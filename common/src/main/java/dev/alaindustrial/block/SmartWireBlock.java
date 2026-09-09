package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.SmartWireBlockEntity;
import dev.alaindustrial.core.monitor.ContainerScan;
import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
 * The smart wire (MOD-480): a line that carries NUMBERS, not items.
 *
 * <p>It links the containers a player already owns to a monitor core, and nothing it touches ever
 * moves — the whole system only ever looks. That is also why it has no face modes: with no direction
 * of flow there is nothing to switch, so six booleans describe it completely.
 *
 * <p><b>Sixty-four states, and that number is the design.</b> The item pipe carries a seven-value
 * enum per face and pays 38 416 blockstate combinations for it; {@code initCache()} then asks every
 * one of them for its shape twenty times over (ADR-023), which once cost the client 440 s of start-up.
 * Six booleans is 64 states, and the shapes for all of them are assembled once into
 * {@link #SHAPES} at class-init.
 */
public class SmartWireBlock extends BaseEntityBlock {

	public static final MapCodec<SmartWireBlock> CODEC = simpleCodec(SmartWireBlock::new);

	/** A 4px core so the wire reads as thinner and more delicate than the item pipe's 6px body. */
	private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);

	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

	static {
		ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
		ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
		ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
		ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
		ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
		ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
	}

	/**
	 * Every shape this block can have, indexed by the six connection bits. Built once here rather
	 * than assembled inside {@code getShape} — see the class javadoc and ADR-023.
	 */
	private static final VoxelShape[] SHAPES = new VoxelShape[64];

	static {
		for (int mask = 0; mask < SHAPES.length; mask++) {
			VoxelShape shape = CORE;
			for (Direction dir : Direction.values()) {
				if ((mask & (1 << dir.ordinal())) != 0) {
					shape = Shapes.or(shape, ARMS.get(dir));
				}
			}
			SHAPES[mask] = shape;
		}
	}

	public SmartWireBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (BooleanProperty prop : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
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
		builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST,
				PipeBlock.UP, PipeBlock.DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		int mask = 0;
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			if (state.getValue(entry.getValue())) {
				mask |= 1 << entry.getKey().ordinal();
			}
		}
		return SHAPES[mask];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			Direction dir = entry.getKey();
			state = state.setValue(entry.getValue(), connectsTo(level, pos.relative(dir)));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour);
		if (prop == null) {
			return state;
		}
		return state.setValue(prop, connectsTo(level, neighbourPos));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@org.jetbrains.annotations.Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.onNeighbourChanged(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			net.minecraft.world.entity.@org.jetbrains.annotations.Nullable LivingEntity placer,
			net.minecraft.world.item.ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof SmartWireBlockEntity wire) {
			wire.ensureRegistered();
		}
	}

	/**
	 * Whether a wire draws an arm toward {@code target}: another wire, a monitor core, or anything
	 * whose contents this system can read.
	 *
	 * <p>Panels are deliberately absent. They join the network through the core, not through the
	 * wire, so a wire run brushing past a monitoring wall does not silently graft itself onto it.
	 */
	public static boolean connectsTo(LevelReader level, BlockPos target) {
		BlockState state = level.getBlockState(target);
		if (state.getBlock() instanceof SmartWireBlock || state.getBlock() instanceof MonitorCoreBlock) {
			return true;
		}
		return level instanceof ServerLevel serverLevel && ContainerScan.isReadable(serverLevel, target);
	}

	/** Grid connectivity for the network graph: wire to wire, wire to core. */
	public static boolean isNetworkNode(LevelReader level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof SmartWireBlock || state.getBlock() instanceof MonitorCoreBlock;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SmartWireBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof SmartWireBlockEntity wire) {
				wire.serverTick();
			}
		};
	}
}
