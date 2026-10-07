package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.PiezoPlateBlockEntity;
import dev.alaindustrial.block.entity.PiezoPlateGroup;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.energy.EnergyHostRedirect;
import dev.alaindustrial.core.energy.FloorPortLender;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The piezo plate (MOD-764): a pressure plate that also makes a small EU pulse on every NEW press — the step
 * from released to pressed. A plate held down earns nothing, so output follows traffic, not the number of
 * plates; a crowd standing on one plate is one press.
 *
 * <p><b>Behaves as a pressure plate.</b> It extends the vanilla base, sits in {@code #minecraft:pressure_plates}
 * (water does not flow through it), keeps the vanilla redstone signal and timing ({@code getPressedTime}, 20
 * ticks) and is pressed by any entity, like a wooden plate. A living presser gives the full pulse, a lone item,
 * arrow or cart a fifth of it — an item loop on a water stream would otherwise beat a mob farm.
 *
 * <p><b>Why the press check is written here and not inherited.</b> Vanilla's {@code checkPressed} is private
 * and plays the click and the vibration itself; the plate needs the press edge with its presser in hand, and
 * the silent plate needs to play neither. The sequence below mirrors vanilla's order: signal, state, neighbour
 * update, click and game event on the edge, re-check after {@link #getPressedTime()}.
 *
 * <p><b>Energy faces.</b> Out on the four sides — a cable beside the plate draws its arm and pulls — and
 * through the floor: as a {@link FloorPortLender} the plate lends its port to the bottom face of the plain
 * block it rests on, so a cable or receiver under the floor works and the wire stays hidden (ADR-046).
 */
public class PiezoPlateBlock extends BasePressurePlateBlock
		implements EntityBlock, EnergyHostRedirect, FloorPortLender, HasMachineTooltip {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	// 26.2 seam: BlockBehaviour.codec() is abstract here (26.3 removed block codecs); one codec per variant.
	private static final MapCodec<PiezoPlateBlock> LOUD_CODEC = simpleCodec(PiezoPlateBlock::loud);
	private static final MapCodec<PiezoPlateBlock> SILENT_CODEC = simpleCodec(PiezoPlateBlock::silent);

	private final boolean silent;

	public PiezoPlateBlock(Properties properties, boolean silent) {
		super(properties, BlockSetType.STONE);
		this.silent = silent;
		registerDefaultState(stateDefinition.any().setValue(POWERED, Boolean.FALSE));
	}

	public static PiezoPlateBlock loud(Properties properties) {
		return new PiezoPlateBlock(properties, false);
	}

	public static PiezoPlateBlock silent(Properties properties) {
		return new PiezoPlateBlock(properties, true);
	}

	public boolean isSilent() {
		return silent;
	}

	@Override
	protected MapCodec<PiezoPlateBlock> codec() {
		return silent ? SILENT_CODEC : LOUD_CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	// --- pressure plate --------------------------------------------------------------------------

	@Override
	protected int getSignalForState(BlockState state) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected BlockState setSignalForState(BlockState state, int signal) {
		return state.setValue(POWERED, signal > 0);
	}

	/** Any entity presses it, like a wooden plate — entities that ignore block triggers excepted. */
	@Override
	protected int getSignalStrength(Level level, BlockPos pos) {
		return getEntityCount(level, TOUCH_AABB.move(pos), Entity.class) > 0 ? 15 : 0;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effects, boolean intersects) {
		if (!level.isClientSide() && getSignalForState(state) == 0) {
			checkPressed(entity, level, pos, state, 0);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int signal = getSignalForState(state);
		if (signal > 0) {
			checkPressed(null, level, pos, state, signal);
		}
	}

	private void checkPressed(@Nullable Entity entity, Level level, BlockPos pos, BlockState state, int oldSignal) {
		int signal = getSignalStrength(level, pos);
		boolean wasPressed = oldSignal > 0;
		boolean pressed = signal > 0;
		if (oldSignal != signal) {
			BlockState updated = setSignalForState(state, signal);
			level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
			updateNeighbours(level, pos);
			level.setBlocksDirty(pos, state, updated);
		}
		if (!pressed && wasPressed) {
			if (!silent) {
				level.playSound(null, pos, type.pressurePlateClickOff(), SoundSource.BLOCKS);
				level.gameEvent(entity, GameEvent.BLOCK_DEACTIVATE, pos);
			}
		} else if (pressed && !wasPressed) {
			if (!silent) {
				level.playSound(null, pos, type.pressurePlateClickOn(), SoundSource.BLOCKS);
				level.gameEvent(entity, GameEvent.BLOCK_ACTIVATE, pos);
			}
			if (level.getBlockEntity(pos) instanceof PiezoPlateBlockEntity plate) {
				plate.onPressed(livingPresses(level, pos));
			}
		}
		if (pressed) {
			level.scheduleTick(pos, this, getPressedTime());
		}
	}

	/** True when a living entity is among the pressers: it earns the full pulse, an item alone a fifth. */
	private static boolean livingPresses(Level level, BlockPos pos) {
		AABB box = TOUCH_AABB.move(pos);
		return !level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isIgnoringBlockTriggers()).isEmpty();
	}

	// --- block entity ----------------------------------------------------------------------------

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PiezoPlateBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (tickLevel, tickPos, tickState, be) -> {
			if (be instanceof PiezoPlateBlockEntity plate) {
				plate.serverTick(tickLevel, tickPos, tickState);
			}
		};
	}

	// --- energy faces ----------------------------------------------------------------------------

	/** The plate's own port on the sides and the bottom (lent through the floor); nothing on top. */
	@Override
	public @Nullable BlockPos energyHost(BlockState state, BlockPos pos, Direction face) {
		return face == Direction.UP ? null : pos;
	}

	// --- neighbours ------------------------------------------------------------------------------

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
			membershipChanged(serverLevel, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		membershipChanged(level, pos);
	}

	/**
	 * A plate joined or left: neighbouring plates rebuild their group, and a cable two blocks down is told — it
	 * sees the plate through the floor, but no block update reaches it from two blocks away.
	 */
	private static void membershipChanged(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (level.getBlockEntity(pos.relative(dir)) instanceof PiezoPlateBlockEntity neighbour) {
				neighbour.onNeighbourChanged();
			}
		}
		NetworkManager.onNeighbourChanged(level, pos.below(2));
	}

	/**
	 * A neighbour changed — a cable or machine appeared or left beside the plate. A neighbouring plate being
	 * pressed changes nothing about the group, and joins and leaves are handled in {@link #onPlace} and
	 * {@link #affectNeighborsAfterRemoval}, so those are not worth a new walk.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && !(neighborBlock instanceof PiezoPlateBlock)
				&& level.getBlockEntity(pos) instanceof PiezoPlateBlockEntity plate) {
			plate.onNeighbourChanged();
		}
	}

	/** One line above the hotbar on placement: how big the path is and whether it is connected. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || !(placer instanceof Player player)
				|| !(level.getBlockEntity(pos) instanceof PiezoPlateBlockEntity plate)) {
			return;
		}
		PiezoPlateGroup group = PiezoPlateGroup.of(level, plate);
		String key = plate.viewOutlets() > 0 ? "message.alaindustrial.piezo_plate.placed_connected"
				: "message.alaindustrial.piezo_plate.placed_no_outlet";
		player.sendOverlayMessage(Component.translatable(key, group.size()));
	}

	// --- tooltip ---------------------------------------------------------------------------------

	@Override
	public MachineTooltipSpec machineTooltip() {
		List<MachineTooltipSpec.Line> basic = new ArrayList<>();
		basic.add(MachineTooltipSpec.note("tooltip.alaindustrial.piezo_plate_press", MachineTooltipSpec.Tone.GRAY));
		if (silent) {
			basic.add(MachineTooltipSpec.note("tooltip.alaindustrial.silent_piezo_plate_quiet",
					MachineTooltipSpec.Tone.DARK_GRAY));
		}
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV, basic,
				List.of(MachineTooltipSpec.stat("piezo_press", ServerBalance::piezoPlateLivingPressEu,
								ServerBalance::piezoPlateObjectPressEu),
						MachineTooltipSpec.note("tooltip.alaindustrial.piezo_plate_connect",
								MachineTooltipSpec.Tone.DARK_GRAY),
						MachineTooltipSpec.note("tooltip.alaindustrial.piezo_plate_wrench",
								MachineTooltipSpec.Tone.DARK_GRAY)));
	}
}
