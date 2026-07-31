package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Passive multipart fluid pipe (MOD-151). Routing state and the segment buffer live in
 * {@link FluidPipeBlockEntity}; the blockstate carries only what the model needs.
 *
 * <p><b>Why {@link #FILLED} is a blockstate and the fluid's colour is not.</b> Whether a segment holds
 * anything is a two-value fact, so it rides along in the blockstate for free — vanilla already syncs
 * and batches those into the chunk mesh. The fluid TYPE cannot: the set of fluids is open and other
 * mods register into it at runtime, so it is read from the block entity by a tint source instead. That
 * split is what keeps a line of hundreds of pipes off the per-frame renderer path entirely.
 */
public final class FluidPipeBlock extends BaseEntityBlock {
	public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);

	/** True while the segment holds fluid — drives the visible core, not the colour. */
	public static final BooleanProperty FILLED = BooleanProperty.create("filled");

	// Same slim profile as the item pipe: the two transport systems read as siblings, and are told
	// apart by texture and by the fluid colour showing through, not by size.
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

	public FluidPipeBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any().setValue(FILLED, false);
		for (EnumProperty<PipeFaceMode> property : FACE_MODES.values()) {
			state = state.setValue(property, PipeFaceMode.DISABLED);
		}
		registerDefaultState(state);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		FACE_MODES.values().forEach(builder::add);
		builder.add(FILLED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for (Direction dir : Direction.values()) {
			state = state.setValue(FACE_MODES.get(dir),
					visibleMode(context.getLevel(), context.getClickedPos(), dir));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(FACE_MODES.get(direction), visibleMode(level, pos, direction));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel server) {
			FluidNetworkManager.onNeighbourChanged(server, pos);
		}
	}

	/** Connection contract shared by state refresh and the graph manager. */
	public static boolean shouldConnectTo(LevelReader level, BlockPos pos, Direction direction) {
		if (faceMode(level, pos, direction) == PipeFaceMode.DISABLED) {
			return false;
		}
		if (!hasEndpointCandidate(level, pos, direction)) {
			return false;
		}
		BlockPos neighbour = pos.relative(direction);
		return !(level.getBlockState(neighbour).getBlock() instanceof FluidPipeBlock)
				|| faceMode(level, neighbour, direction.getOpposite()) != PipeFaceMode.DISABLED;
	}

	/**
	 * True when a face touches another pipe or a real fluid port, independent of its current mode. The
	 * wrench uses this to restore a disabled, visually hidden endpoint. Refusing a face that offers no
	 * port keeps the geometry honest — a pipe that cannot move anything must not look connected
	 * (the item pipe learned this the hard way in MOD-234).
	 */
	public static boolean hasEndpointCandidate(LevelReader level, BlockPos pos, Direction direction) {
		BlockPos neighbour = pos.relative(direction);
		if (level.getBlockState(neighbour).getBlock() instanceof FluidPipeBlock) {
			return true;
		}
		return level instanceof Level world
				&& FluidLookup.get().find(world, neighbour, direction.getOpposite()) != null;
	}

	private static PipeFaceMode faceMode(LevelReader level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe
				? pipe.faceMode(direction) : PipeFaceMode.NEUTRAL;
	}

	/**
	 * Model state for one face. A missing neighbour maps to {@link PipeFaceMode#DISABLED} without
	 * overwriting the player's stored configuration, so reconnecting a tank restores the chosen mode.
	 */
	private static PipeFaceMode visibleMode(LevelReader level, BlockPos pos, Direction direction) {
		return shouldConnectTo(level, pos, direction) ? faceMode(level, pos, direction) : PipeFaceMode.DISABLED;
	}

	/** Recompute the six baked model faces without recursively dirtying neighbouring networks. */
	public static void refreshConnections(Level level, BlockPos pos) {
		BlockState current = level.getBlockState(pos);
		if (!(current.getBlock() instanceof FluidPipeBlock)) {
			return;
		}
		BlockState updated = current;
		for (Direction dir : Direction.values()) {
			updated = updated.setValue(FACE_MODES.get(dir), visibleMode(level, pos, dir));
		}
		if (updated != current) {
			level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
		}
	}

	/** Flip the visible core on or off. Called only when the segment crosses empty/non-empty. */
	public static void refreshFilled(Level level, BlockPos pos, boolean filled) {
		BlockState current = level.getBlockState(pos);
		if (!(current.getBlock() instanceof FluidPipeBlock) || current.getValue(FILLED) == filled) {
			return;
		}
		level.setBlock(pos, current.setValue(FILLED, filled), Block.UPDATE_CLIENTS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape result = CORE;
		for (Map.Entry<Direction, EnumProperty<PipeFaceMode>> entry : FACE_MODES.entrySet()) {
			if (state.getValue(entry.getValue()) != PipeFaceMode.DISABLED) {
				result = Shapes.or(result, ARMS.get(entry.getKey()));
			}
		}
		return result;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidPipeBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
			pipe.ensureRegistered();
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModContent.FLUID_PIPE_BE.get()) {
			return null;
		}
		return (world, pos, blockState, entity) ->
				((FluidPipeBlockEntity) entity).serverTick(world, pos, blockState);
	}
}
