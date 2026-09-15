package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.menu.TeleporterStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import dev.alaindustrial.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Teleporter station (spec: alaindustrial:teleporter) — the HV anchor a Teleporter Remote jumps to
 * (MOD-091). This task ships the station only: it accepts HV on its five working faces, banks EU in
 * an oversized buffer, and remembers its owner and privacy flag. The jump itself (MOD-092) and the
 * GUI (MOD-093) come later, so the station has no menu and no slots yet.
 *
 <p>It stayed hidden from the creative tab and un-craftable until MOD-093 finished the feature —
 * a station that banks EU with no way to spend it is not something to ship. The tab entry, the
 * recipe and its unlock advancement all arrived together.
 */
public class TeleporterBlock extends HorizontalMachineBlock {
	public static final MapCodec<TeleporterBlock> CODEC = simpleCodec(TeleporterBlock::new);

	/**
	 * Whether a Random Jump Chip is fitted (MOD-116) — a purely VISUAL mirror of
	 * {@link TeleporterBlockEntity#hasRtpModule()}.
	 *
	 * <p>The block entity stays the single source of truth for behaviour; this property exists only so
	 * the upgrade can be seen. Kept as blockstate rather than read from the block entity at render
	 * time because a blockstate is synchronised to the client for free — the station has no block
	 * entity sync of its own, and adding one for a texture swap would be a lot of machinery for a
	 * picture. The two can in principle disagree, and if they ever do the consequence is a wrong
	 * texture, never a jump that should not have happened.
	 *
	 * <p>Default {@code false}, so every station saved before MOD-116 loads unchanged.
	 */
	public static final BooleanProperty UPGRADED = BooleanProperty.create("upgraded");

	/**
	 * Whether two glass blocks on this station have become its capsule (MOD-112) — and so whether a jump
	 * may land here.
	 *
	 * <p>A block state rather than a block entity field, because two readers need it and both are
	 * better served by a state: the model (a state reaches the client for free) and the jump rule (a
	 * state is saved with the chunk, so a reload cannot disagree with what the player sees). The capsule
	 * cells above hold the rest of the structure in their own states; see {@link TeleporterCapsuleBlock}.
	 *
	 * <p>Default {@code false}: a station saved before MOD-112 loads unassembled, as it truly is.
	 */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/**
	 * Height a player stands at inside the capsule, in blocks above the station's own floor.
	 *
	 * <p>9/16 rather than the design's deck at 10/16: a player steps up 0.6 of a block, and a floor at
	 * 0.625 would make the capsule a thing to jump into rather than walk into.
	 */
	public static final double CAPSULE_FLOOR = 9.0 / 16.0;

	/** The assembled station is the capsule's base: its outline stops at the deck. */
	private static final VoxelShape FORMED_OUTLINE = Block.box(0.5, 0, 0.5, 15.5, 10, 15.5);
	/** …and the floor underfoot stops at {@link #CAPSULE_FLOOR}. */
	private static final VoxelShape FORMED_COLLISION = Block.box(0.5, 0, 0.5, 15.5, 9, 15.5);

	/**
	 * How often a station with one glass on it looks for the second.
	 *
	 * <p>Polled, because vanilla does not tell a block about a change two cells above it: a glass placed
	 * on top of the first notifies that first glass, which is vanilla and does nothing with it. The poll
	 * exists only while exactly one glass sits on an unassembled station, and it stops the moment the
	 * capsule forms or the glass goes.
	 */
	private static final int ASSEMBLY_POLL_TICKS = 4;

	public TeleporterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(UPGRADED, false).setValue(FORMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(UPGRADED, FORMED);
	}

	/** Whether {@code state} is a station whose capsule stands. */
	public static boolean isFormed(BlockState state) {
		return state.getBlock() instanceof TeleporterBlock && state.getValue(FORMED);
	}

	/**
	 * Light the marker on a station that has just been upgraded.
	 *
	 * <p>Changing only a property of the SAME block leaves the block entity in place (vanilla replaces
	 * it only when the block itself changes), so the fitted module and the banked EU survive this call.
	 */
	public static void showUpgraded(Level level, BlockPos pos, BlockState state) {
		if (state.hasProperty(UPGRADED) && !state.getValue(UPGRADED)) {
			level.setBlock(pos, state.setValue(UPGRADED, true), Block.UPDATE_ALL);
		}
	}

	/**
	 * Carry the marker across a re-place.
	 *
	 * <p>The module rides the dropped item as a data component, so by the time this runs the block
	 * entity has already been handed it back — but the blockstate was created fresh and knows nothing.
	 * Without this, moving an upgraded station would silently drop its marker while keeping the
	 * upgrade, which is the one disagreement between the two that a player would actually notice.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof TeleporterBlockEntity station && station.hasRtpModule()) {
			showUpgraded(level, pos, state);
		}
		// Glass already waiting above — a player who built the capsule's glass first.
		tryAssemble(level, pos);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TeleporterBlockEntity(pos, state);
	}

	// Ownership is set generically by AbstractMachineBlock#setPlacedBy (MOD-133): the placer becomes
	// the owner on every place, and the name snapshot is taken there — the station no longer needs its
	// own setPlacedBy override. Owner deliberately does NOT ride the dropped item (it is re-assigned on
	// place), unlike the EU buffer and privacy flag, which do (see collectImplicitComponents).

	/**
	 * Right-click opens the station's screen (MOD-093).
	 *
	 * <p>Opened here rather than through {@code MenuProvider} on the block entity: the base machine
	 * class hands four upgrade slots to every menu-bearing BE ({@code MachineBlockEntity:76}), and a
	 * station is a fund, not a machine — it must stay slotless, or hoppers gain somewhere to push
	 * items nobody can see.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity station)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new TeleporterStationMenu(syncId, inventory, station,
							ContainerLevelAccess.create(level, pos)),
					station.menuTitle()));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * No server ticker on purpose. The station has nothing to do per tick: energy delivery is driven by
	 * the network ({@code EnergyNetwork#tick} pushes straight into the buffer through the face port),
	 * not by the consumer's own tick, so registering a ticker would only spin an empty
	 * {@code onServerTick} 20×/s for every loaded station — exactly what the idle-sleep gate (R-29)
	 * exists to avoid.
	 *
	 * <p>The client does tick an assembled station (MOD-112), and only to keep the capsule door's travel
	 * clock: a door nobody is looking at must still know when it last moved, or it would play its slide
	 * the moment it came into view.
	 */
	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (!level.isClientSide() || !state.getValue(FORMED)) {
			return null;
		}
		return (tickLevel, tickPos, tickState, blockEntity) -> {
			if (blockEntity instanceof TeleporterBlockEntity station) {
				station.clientTick(tickLevel);
			}
		};
	}

	// --- the capsule (MOD-112) -------------------------------------------------------------------

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FORMED) ? FORMED_OUTLINE : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return state.getValue(FORMED) ? FORMED_COLLISION : Shapes.block();
	}

	/**
	 * An assembled station hides nothing of its neighbours: its base is inset from the block edge, and a
	 * neighbour that stopped drawing its face toward it would show a hole straight through itself. The
	 * loose station is still the full cube it always was, and keeps occluding like one.
	 */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(FORMED) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		tryAssemble(level, pos);
	}

	/**
	 * The cell above is the capsule's middle or the capsule is gone. Losing it drops {@link #FORMED}
	 * — the jump rule and the model follow — and glass left standing there starts the poll again.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == Direction.UP) {
			boolean middle = neighbourState.getBlock() instanceof TeleporterCapsuleBlock
					&& neighbourState.getValue(TeleporterCapsuleBlock.PART) == TeleporterCapsuleBlock.Part.MIDDLE;
			if (state.getValue(FORMED) && !middle) {
				state = state.setValue(FORMED, false);
			}
			if (!state.getValue(FORMED) && CapsuleGlass.of(neighbourState).isPresent()) {
				ticks.scheduleTick(pos, this, ASSEMBLY_POLL_TICKS);
			}
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/** The poll booked by {@link #tryAssemble} while one glass waits for the second. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		tryAssemble(level, pos);
	}

	/**
	 * Turn two blocks of glass on this station into its capsule.
	 *
	 * <p>{@code public static} on purpose: a game test places blocks straight into the world, and a
	 * programmatic placement never calls {@code setPlacedBy} (MOD-015).
	 *
	 * <p><b>The order of the three writes matters.</b> The station goes first, so that when the middle
	 * cell appears above it the station's own shape update already sees a formed station and keeps it;
	 * the middle cell goes before the top one, so that the top cell's arrival is what the middle cell
	 * checks its partner against. Written the other way round, a cell would read a neighbour that was not
	 * a capsule yet and turn straight back into glass.
	 */
	public static void tryAssemble(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState station = level.getBlockState(pos);
		if (!(station.getBlock() instanceof TeleporterBlock) || station.getValue(FORMED)) {
			return;
		}
		Optional<CapsuleGlass> lower = CapsuleGlass.of(level.getBlockState(pos.above()));
		if (lower.isEmpty()) {
			return;
		}
		Optional<CapsuleGlass> upper = CapsuleGlass.of(level.getBlockState(pos.above(2)));
		if (upper.isEmpty()) {
			level.scheduleTick(pos, station.getBlock(), ASSEMBLY_POLL_TICKS);
			return;
		}
		BlockState cell = ModContent.TELEPORTER_CAPSULE.get().defaultBlockState()
				.setValue(TeleporterCapsuleBlock.FACING, station.getValue(FACING))
				.setValue(TeleporterCapsuleBlock.OPEN, false);
		level.setBlock(pos, station.setValue(FORMED, true), Block.UPDATE_ALL);
		level.setBlock(pos.above(), cell.setValue(TeleporterCapsuleBlock.PART, TeleporterCapsuleBlock.Part.MIDDLE)
				.setValue(TeleporterCapsuleBlock.GLASS, lower.get()), Block.UPDATE_ALL);
		level.setBlock(pos.above(2), cell.setValue(TeleporterCapsuleBlock.PART, TeleporterCapsuleBlock.Part.TOP)
				.setValue(TeleporterCapsuleBlock.GLASS, upper.get()), Block.UPDATE_ALL);
		// The remote's "locked in" chime: the capsule is now somewhere a jump can land.
		level.playSound(null, pos.above(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.7f, 1.3f);
	}
}
