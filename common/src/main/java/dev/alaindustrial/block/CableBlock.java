package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.registry.ModCriteria;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LV copper cable. Connects (visually + for routing) to adjacent cables and machines via the six
 * {@code north/south/east/west/up/down} connection properties (reused from {@link PipeBlock}),
 * so a multipart blockstate can join segments with arm models. Energy relay lives in
 * {@link CableBlockEntity}.
 *
 * <p>Horizontal arms toward a <b>half-block</b> neighbour (e.g. a Solar Panel, top at Y=8px) drop
 * to the base of the block via a per-direction {@code *_low} flag — see {@link #ARMS_LOW} — so the
 * sleeve hugs the slab's side instead of floating 3px above its surface (MOD-042). The flag is
 * derived generically from the neighbour's collision shape ({@code maxY <= 0.5}), not from any
 * specific block type, so any future half-block machine connects the same way.
 */
public class CableBlock extends AbstractMachineBlock {
	public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

	/** Collision/outline that matches the model: a 6px core plus an arm toward each connection. */
	private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	static {
		ARMS.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
		ARMS.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
		ARMS.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
		ARMS.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
		ARMS.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
		ARMS.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
	}

	/**
	 * Low arms for horizontal connections to a half-block neighbour (e.g. a Solar Panel, top at
	 * Y=8). The normal horizontal arm sits at Y=5..11 (the cable's vertical centre), so its upper
	 * 3px would float in the air above a 0.5-block surface. These drop the sleeve to Y=0..5 so it
	 * hugs the base/side of the slab — same 6px cross-section, lowered. See {@link #LOW_FLAGS}.
	 */
	private static final Map<Direction, VoxelShape> ARMS_LOW = new EnumMap<>(Direction.class);
	static {
		ARMS_LOW.put(Direction.NORTH, Shapes.or(
				Block.box(5, 5, 2, 11, 11, 5),
				Block.box(5, 2, 0, 11, 8, 2)));
		ARMS_LOW.put(Direction.SOUTH, Shapes.or(
				Block.box(5, 5, 11, 11, 11, 14),
				Block.box(5, 2, 14, 11, 8, 16)));
		ARMS_LOW.put(Direction.WEST, Shapes.or(
				Block.box(2, 5, 5, 5, 11, 11),
				Block.box(0, 2, 5, 2, 8, 11)));
		ARMS_LOW.put(Direction.EAST, Shapes.or(
				Block.box(11, 5, 5, 14, 11, 11),
				Block.box(14, 2, 5, 16, 8, 11)));
	}

	/**
	 * Per-horizontal-direction {@code *Low} flag — {@code true} when the neighbour in that direction
	 * is a half-block (collision {@code maxY <= LOW_NEIGHBOUR_THRESHOLD}), so the arm drops to
	 * {@link #ARMS_LOW}. Vertical directions are not flagged: a cable above/below a slab connects
	 * at the cell centre already and is visually fine.
	 */
	private static final Map<Direction, BooleanProperty> LOW_FLAGS = new EnumMap<>(Direction.class);
	static {
		LOW_FLAGS.put(Direction.NORTH, BooleanProperty.create("north_low"));
		LOW_FLAGS.put(Direction.SOUTH, BooleanProperty.create("south_low"));
		LOW_FLAGS.put(Direction.WEST, BooleanProperty.create("west_low"));
		LOW_FLAGS.put(Direction.EAST, BooleanProperty.create("east_low"));
	}

	/** Neighbours at or below this height (in blocks) are "half-blocks" for the low-arm geometry. */
	private static final double LOW_NEIGHBOUR_THRESHOLD = 0.5;

	public CableBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (BooleanProperty prop : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(prop, false);
		}
		for (BooleanProperty prop : LOW_FLAGS.values()) {
			state = state.setValue(prop, false);
		}
		registerDefaultState(state);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
		LOW_FLAGS.values().forEach(builder::add);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		LevelReader level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir));
			BooleanProperty lowFlag = LOW_FLAGS.get(dir);
			if (lowFlag != null) {
				state = state.setValue(lowFlag, isLowNeighbour(level, pos.relative(dir)));
			}
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
			RandomSource random) {
		state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
				connectsTo(level, pos, direction));
		BooleanProperty lowFlag = LOW_FLAGS.get(direction);
		if (lowFlag != null) {
			// Re-read at neighborPos so the shape sees any neighbour-dependent (updateShape) state,
			// not the static default. level is a LevelReader, which extends BlockGetter.
			state = state.setValue(lowFlag, isLowNeighbour(level, neighborPos));
		}
		return state;
	}

	/**
	 * A neighbour block changed next to this cable (e.g. a machine was placed or broken adjacent to
	 * it without touching a cable). Mark the owning {@link dev.alaindustrial.core.EnergyNetwork}
	 * dirty so it re-discovers its producer/consumer endpoints on the next tick — otherwise a newly
	 * placed machine is never picked up (an asleep network stays asleep; an awake one ignores it).
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			NetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	/**
	 * A cable connects to any adjacent energy block of this mod — other cables, generators, machines,
	 * storage — but not to pure-container blocks that happen to inherit {@link AbstractMachineBlock}
	 * for non-energy reasons (e.g. {@link IronChestBlock}, which is a chest, not an energy receiver).
	 * The {@link AbstractMachineBlock#isCableConnectable()} marker carries that distinction while
	 * keeping the check at the Block level (always available), so the connection is correct the
	 * instant the block is placed — no block-entity load race, no visual gaps (MOD-038).
	 */
	private static boolean connectsTo(LevelReader level, BlockPos pos, Direction dir) {
		Block block = level.getBlockState(pos.relative(dir)).getBlock();
		return block instanceof AbstractMachineBlock machine && machine.isCableConnectable();
	}

	/**
	 * Whether the block at {@code neighborPos} is a "low" (half-block) neighbour — collision shape
	 * top at or below {@link #LOW_NEIGHBOUR_THRESHOLD} (0.5 blocks, e.g. a Solar Panel). Drives the
	 * per-direction {@code *_low} flag so the horizontal arm drops to {@link #ARMS_LOW}. This reads
	 * the neighbour's shape generically (no block-type special-casing), so any future half-block
	 * machine connects the same way. {@link LevelReader} extends {@link BlockGetter}, so it satisfies
	 * {@link BlockState#getShape(BlockGetter, BlockPos)}; empty shapes (air, fluids) are not low.
	 */
	private static boolean isLowNeighbour(LevelReader level, BlockPos neighborPos) {
		VoxelShape shape = level.getBlockState(neighborPos).getShape(level, neighborPos);
		return !shape.isEmpty() && shape.bounds().maxY <= LOW_NEIGHBOUR_THRESHOLD;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = CORE;
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			Direction dir = entry.getKey();
			if (state.getValue(entry.getValue())) {
				BooleanProperty lowFlag = LOW_FLAGS.get(dir);
				VoxelShape arm = lowFlag != null && state.getValue(lowFlag) ? ARMS_LOW.get(dir) : ARMS.get(dir);
				shape = Shapes.or(shape, arm);
			}
		}
		return shape;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CableBlockEntity(pos, state);
	}

	/**
	 * Eagerly registers the new cable with {@link NetworkManager} via {@link CableBlockEntity#ensureRegistered()}
	 * (idempotent, and keeps the entity's own {@code registered} flag in sync so it can never leak an
	 * unregistered-on-removal entry — see the MOD-015/016 code review) so the resulting network's
	 * awake state is known synchronously, and fires {@link ModCriteria#NETWORK_ENERGIZED} if placing
	 * this cable just connected a producer to a consumer (MOD-015 "Closed circuit").
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel
				&& serverLevel.getBlockEntity(pos) instanceof CableBlockEntity cable) {
			cable.ensureRegistered();
			if (placer instanceof ServerPlayer player) {
				ModCriteria.tryFireNetworkEnergized(serverLevel, pos, player);
			}
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
