package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.core.drone.DroneFlightPath;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.misc.ConstructionDroneItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModSounds;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A builder drone's nest (MOD-858; epic MOD-824, decisions R91, R94; the owner's choice «grate on legs»): a grate plate
 * on four legs, copper contact strips under the drone's skids, four teal lights at the corners. It goes anywhere over
 * the town hall's footprint; the yellow carpets of the drone pad only suggest a spot. The drone docked on it is the
 * block entity's renderer's geometry, not the block's: the block's shape is the plate alone, three pixels high, so the
 * drone never gets in the way of a walking player. The work is in {@link DroneNestBlockEntity}.
 *
 * <p>The nest is a platform: it holds no drone until a builder drone item is put in with a right-click, and a sneaking
 * right-click by an empty hand takes it out with its charge (owner's round 1). A plain right-click shows over the
 * hotbar what the nest is doing and where the charge comes from. In a woken town only its restorer breaks it, docks
 * or takes a drone; a stranger to the town gets nothing from it, not even the status line, only a chat line that the
 * town does not know them (R98). No cable reaches it: the core charges a docked drone (R94).
 */
public class DroneNestBlock extends AbstractMachineBlock implements HasMachineTooltip, MachineHumProvider {

	/** The plate's top, in pixels: the shape and the drone's landing height. */
	public static final double PLATE_TOP_PX = 3.0;

	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, PLATE_TOP_PX, 16);

	public DroneNestBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DroneNestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	// ---------------------------------------------------------------- flight sound (owner's round 2)

	/** The garden drone's rotor loop: one drone sound for the mod. */
	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.GARDEN_DRONE_FLY;
	}

	/** As quiet as the garden drone, and for its reason: the drone comes to the player, over their head. */
	@Override
	public float humVolume() {
		return 0.22f;
	}

	/** «Working» is airborne: a leg in the air. A docked drone is silent. */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest && nest.droneTarget() != null;
	}

	/** The drone's own place on its route, so the loop travels with it; the renderer's rounding and lean left out. */
	@Override
	public @Nullable Vec3 humPosition(Level level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest) || nest.droneTarget() == null) {
			return null;
		}
		int[] corners = nest.routeCorners();
		float[] xyz = new float[corners.length];
		for (int i = 0; i < corners.length; i++) {
			xyz[i] = corners[i] + 0.5F;
		}
		float eased = DroneFlight.accelerate(nest.flightProgress(level.getGameTime()));
		float[] at = new float[3];
		new DroneFlightPath(xyz).point(nest.isReturning() ? 1.0F - eased : eased, at);
		return new Vec3(at[0], at[1], at[2]);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public boolean isCableConnectable() {
		return false;
	}

	/**
	 * A builder drone in hand docks it in the empty nest (owner's round 1); its charge comes along. Anything else in
	 * hand falls through to the empty-hand use.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModContent.CONSTRUCTION_DRONE.get())
				|| !(level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide() || !nest.admitOrTell(player)) {
			return InteractionResult.SUCCESS;
		}
		if (!nest.mayBeBrokenBy(player)) {
			player.sendOverlayMessage(Component.translatable("settlement.alaindustrial.dispatcher.owner_only"));
			return InteractionResult.SUCCESS;
		}
		if (!nest.insertDrone(ConstructionDroneItem.charge(stack))) {
			player.sendOverlayMessage(Component.translatable("settlement.alaindustrial.drone_nest.occupied"));
			return InteractionResult.SUCCESS;
		}
		stack.consume(1, player);
		player.sendOverlayMessage(nest.statusLine());
		return InteractionResult.SUCCESS;
	}

	/**
	 * An empty hand: a sneaking click takes the drone out as an item with its charge; a plain click shows over the
	 * hotbar what the nest is doing and where the drone's charge comes from.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !nest.admitOrTell(player)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive() && nest.hasDrone()) {
			if (!nest.mayBeBrokenBy(player)) {
				player.sendOverlayMessage(Component.translatable("settlement.alaindustrial.dispatcher.owner_only"));
				return InteractionResult.SUCCESS;
			}
			long charge = nest.removeDrone();
			if (charge < 0) {
				player.sendOverlayMessage(Component.translatable("settlement.alaindustrial.drone_nest.in_flight"));
				return InteractionResult.SUCCESS;
			}
			ItemStack drone = ConstructionDroneItem.withCharge(charge);
			if (!player.getInventory().add(drone)) {
				Block.popResource(level, pos.above(), drone);
			}
			player.sendOverlayMessage(Component.translatable("settlement.alaindustrial.drone_nest.taken_out"));
			return InteractionResult.SUCCESS;
		}
		player.sendOverlayMessage(nest.statusLine());
		return InteractionResult.SUCCESS;
	}

	/** In a town with an owner, nothing for anybody else; otherwise as any machine. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest && !nest.mayBeBrokenBy(player)) {
			return 0.0F;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	/** Hover tooltip of the item (MOD-716, ADR-040): what it is for. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		return new MachineTooltipSpec(MachineTooltipSpec.Tier.LV,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.drone_nest.purpose",
						MachineTooltipSpec.Tone.GRAY)),
				List.of());
	}

	/** Its lines are whole sentences: broken to the width of a vanilla description (R4-3). */
	@Override
	public boolean wrapsTooltipLines() {
		return true;
	}
}
