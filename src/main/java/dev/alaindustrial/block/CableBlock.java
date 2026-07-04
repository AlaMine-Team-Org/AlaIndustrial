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

	public CableBlock(Properties properties) {
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
		super.createBlockStateDefinition(builder);
		PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		LevelReader level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
			RandomSource random) {
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
				connectsTo(level, pos, direction));
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
	 * A cable connects to any adjacent Industrialization block — other cables, generators, machines,
	 * storage. Checks the neighbour's block type (always available), not its block entity, so the
	 * connection is correct the instant the block is placed (no block-entity load race -> no gaps).
	 */
	private static boolean connectsTo(LevelReader level, BlockPos pos, Direction dir) {
		return level.getBlockState(pos.relative(dir)).getBlock() instanceof AbstractMachineBlock;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = CORE;
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			if (state.getValue(entry.getValue())) {
				shape = Shapes.or(shape, ARMS.get(entry.getKey()));
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
	 * this cable just connected a producer to a consumer (MOD-015 "Closed Circuit").
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
