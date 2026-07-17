package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.ItemLookup;
import dev.alaindustrial.core.ItemNetworkManager;
import dev.alaindustrial.core.PipeFaceMode;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.alaindustrial.registry.ModContent;

/** Passive multipart item pipe. Routing state lives in {@link ItemPipeBlockEntity}, not blockstate. */
public final class ItemPipeBlock extends BaseEntityBlock {
	public static final MapCodec<ItemPipeBlock> CODEC = simpleCodec(ItemPipeBlock::new);
	// Item pipes are intentionally slimmer than the 6 px copper cable so the two transport systems
	// remain recognisable in a dense factory line.
	private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	private static final Map<Direction, EnumProperty<PipeFaceMode>> FACE_MODES = new EnumMap<>(Direction.class);
	static {
		ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
		ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
		ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
		ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
		ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
		ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
		FACE_MODES.put(Direction.DOWN, EnumProperty.create("down_mode", PipeFaceMode.class));
		FACE_MODES.put(Direction.UP, EnumProperty.create("up_mode", PipeFaceMode.class));
		FACE_MODES.put(Direction.NORTH, EnumProperty.create("north_mode", PipeFaceMode.class));
		FACE_MODES.put(Direction.SOUTH, EnumProperty.create("south_mode", PipeFaceMode.class));
		FACE_MODES.put(Direction.WEST, EnumProperty.create("west_mode", PipeFaceMode.class));
		FACE_MODES.put(Direction.EAST, EnumProperty.create("east_mode", PipeFaceMode.class));
	}

	public ItemPipeBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (EnumProperty<PipeFaceMode> property : FACE_MODES.values()) {
			state = state.setValue(property, PipeFaceMode.DISABLED);
		}
		registerDefaultState(state);
	}

	@Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
	@Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		FACE_MODES.values().forEach(builder::add);
	}

	@Override public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for (Direction dir : Direction.values()) {
			state = state.setValue(FACE_MODES.get(dir), visibleMode(context.getLevel(), context.getClickedPos(), dir));
		}
		return state;
	}

	@Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(FACE_MODES.get(direction), visibleMode(level, pos, direction));
	}

	@Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel server) ItemNetworkManager.onNeighbourChanged(server, pos);
	}

	/** Connection contract shared by state refresh and the graph manager. */
	public static boolean shouldConnectTo(LevelReader level, BlockPos pos, Direction direction) {
		if (faceMode(level, pos, direction) == PipeFaceMode.DISABLED) return false;
		if (!hasEndpointCandidate(level, pos, direction)) return false;
		BlockPos neighbour = pos.relative(direction);
		return !(level.getBlockState(neighbour).getBlock() instanceof ItemPipeBlock)
				|| faceMode(level, neighbour, direction.getOpposite()) != PipeFaceMode.DISABLED;
	}

	/**
	 * True when a face touches a configurable inventory or pipe, independent of its current mode.
	 * The wrench uses this to restore a disabled, visually hidden endpoint from a click on the
	 * exposed side of the pipe.
	 */
	public static boolean hasEndpointCandidate(LevelReader level, BlockPos pos, Direction direction) {
		BlockPos neighbour = pos.relative(direction);
		if (level.getBlockState(neighbour).getBlock() instanceof ItemPipeBlock) return true;
		return level instanceof Level world && ItemLookup.get().find(world, neighbour, direction.getOpposite()) != null;
	}

	private static PipeFaceMode faceMode(LevelReader level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe
				? pipe.faceMode(direction) : PipeFaceMode.NEUTRAL;
	}

	/**
	 * Model state for one face. A missing neighbour intentionally maps to {@link PipeFaceMode#DISABLED}
	 * without overwriting the player's persistent BE configuration, so reconnecting a chest restores
	 * its selected mode while an unconnected pipe stays visually compact.
	 */
	private static PipeFaceMode visibleMode(LevelReader level, BlockPos pos, Direction direction) {
		return shouldConnectTo(level, pos, direction) ? faceMode(level, pos, direction) : PipeFaceMode.DISABLED;
	}

	/** Recompute the six baked model faces without recursively dirtying every neighbour network. */
	public static void refreshConnections(Level level, BlockPos pos) {
		BlockState current = level.getBlockState(pos);
		if (!(current.getBlock() instanceof ItemPipeBlock)) return;
		BlockState updated = current;
		for (Direction dir : Direction.values()) {
			updated = updated.setValue(FACE_MODES.get(dir), visibleMode(level, pos, dir));
		}
		if (updated != current) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	@Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape result = CORE;
		for (Map.Entry<Direction, EnumProperty<PipeFaceMode>> entry : FACE_MODES.entrySet()) {
			if (state.getValue(entry.getValue()) != PipeFaceMode.DISABLED) result = Shapes.or(result, ARMS.get(entry.getKey()));
		}
		return result;
	}

	@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ItemPipeBlockEntity(pos, state); }
	@Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) pipe.ensureRegistered();
	}
	@Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModContent.ITEM_PIPE_BE.get()) return null;
		return (world, pos, blockState, entity) -> ((ItemPipeBlockEntity) entity).serverTick(world, pos, blockState);
	}
}
