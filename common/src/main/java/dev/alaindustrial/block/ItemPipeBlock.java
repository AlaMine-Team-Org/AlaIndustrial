package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.core.item.PipeFaceRender;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.WorldlyContainer;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.alaindustrial.registry.ModContent;

/**
 * Passive multipart item pipe. Routing state lives in {@link ItemPipeBlockEntity}, not blockstate;
 * the blockstate carries only what the model needs — per face, what to draw ({@link PipeFaceRender}).
 *
 * <p>A horizontal face touching a half-block neighbour draws a dropped arm so the sleeve hugs the
 * neighbour's side instead of floating above it (MOD-540) — the same fix cables got in MOD-042, with
 * the "low" answer folded into the face value rather than added as a fifth property; the reasoning,
 * and its price in blockstates, is on {@link PipeFaceRender}.
 */
public final class ItemPipeBlock extends BaseEntityBlock {
	public static final MapCodec<ItemPipeBlock> CODEC = simpleCodec(ItemPipeBlock::new);
	private static final Map<Direction, EnumProperty<PipeFaceRender>> FACE_MODES = new EnumMap<>(Direction.class);
	static {
		FACE_MODES.put(Direction.DOWN,
				EnumProperty.create("down_mode", PipeFaceRender.class, PipeFaceRender.VERTICAL));
		FACE_MODES.put(Direction.UP,
				EnumProperty.create("up_mode", PipeFaceRender.class, PipeFaceRender.VERTICAL));
		FACE_MODES.put(Direction.NORTH, EnumProperty.create("north_mode", PipeFaceRender.class));
		FACE_MODES.put(Direction.SOUTH, EnumProperty.create("south_mode", PipeFaceRender.class));
		FACE_MODES.put(Direction.WEST, EnumProperty.create("west_mode", PipeFaceRender.class));
		FACE_MODES.put(Direction.EAST, EnumProperty.create("east_mode", PipeFaceRender.class));
	}

	public ItemPipeBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (EnumProperty<PipeFaceRender> property : FACE_MODES.values()) {
			state = state.setValue(property, PipeFaceRender.DISABLED);
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
			state = state.setValue(FACE_MODES.get(dir), visibleRender(context.getLevel(), context.getClickedPos(), dir));
		}
		return state;
	}

	@Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(FACE_MODES.get(direction), visibleRender(level, pos, direction));
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
	 *
	 * <p>A face that offers no slots is <b>not</b> a candidate (MOD-234). Every machine in the mod keeps
	 * its front face inert to automation, but the inventory behind it is still a valid port — so a pipe
	 * laid against that face connected, rendered its terminal, registered as an endpoint, and then moved
	 * nothing for ever with no hint anywhere. A player wiring a machine's front to a chest sees a
	 * correct-looking build that does nothing (owner report, 2026-07-26). Refusing the connection makes
	 * the geometry tell the truth: the pipe does not join that face, so the mistake is visible the
	 * moment it is made. Both the rendered connection and the transfer endpoints resolve through here,
	 * so the two cannot disagree.
	 */
	public static boolean hasEndpointCandidate(LevelReader level, BlockPos pos, Direction direction) {
		BlockPos neighbour = pos.relative(direction);
		if (level.getBlockState(neighbour).getBlock() instanceof ItemPipeBlock) return true;
		Direction faceTowardsPipe = direction.getOpposite();
		if (level.getBlockEntity(neighbour) instanceof WorldlyContainer sided
				&& sided.getSlotsForFace(faceTowardsPipe).length == 0) {
			return false;
		}
		return level instanceof Level world
				&& ItemLookup.get().find(world, neighbour, faceTowardsPipe) != null;
	}

	private static PipeFaceMode faceMode(LevelReader level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe
				? pipe.faceMode(direction) : PipeFaceMode.NEUTRAL;
	}

	/**
	 * What one face draws. A missing neighbour intentionally maps to {@link PipeFaceRender#DISABLED}
	 * without overwriting the player's persistent BE configuration, so reconnecting a chest restores
	 * its selected mode while an unconnected pipe stays visually compact.
	 *
	 * <p>The half-block answer rides along here rather than in a property of its own (MOD-540), which
	 * is why the low arm needs no plumbing beyond this line: every path that keeps a face current —
	 * placement, {@code updateShape}, {@link #refreshConnections} and the once-per-load re-derive in
	 * {@link ItemPipeBlockEntity} — already goes through this method.
	 */
	private static PipeFaceRender visibleRender(LevelReader level, BlockPos pos, Direction direction) {
		if (!shouldConnectTo(level, pos, direction)) {
			return PipeFaceRender.DISABLED;
		}
		boolean low = direction.getAxis().isHorizontal()
				&& HalfBlockNeighbour.isLow(level, pos.relative(direction));
		return PipeFaceRender.of(faceMode(level, pos, direction), low);
	}

	/**
	 * What the given face of this state draws. The blockstate property itself stays private — this is
	 * the read the gametests and any future tooling use, so the six properties keep exactly one owner.
	 */
	public static PipeFaceRender renderAt(BlockState state, Direction direction) {
		return state.getValue(FACE_MODES.get(direction));
	}

	/**
	 * The same state with one face set to {@code render}. Paired with {@link #renderAt} so the six
	 * properties keep a single owner even where a caller needs to write one — today that is the
	 * gametest covering the low-arm branch a future half-block fluid port would take.
	 */
	public static BlockState withRender(BlockState state, Direction direction, PipeFaceRender render) {
		return state.setValue(FACE_MODES.get(direction), render);
	}

	/** Recompute the six baked model faces without recursively dirtying every neighbour network. */
	public static void refreshConnections(Level level, BlockPos pos) {
		BlockState current = level.getBlockState(pos);
		if (!(current.getBlock() instanceof ItemPipeBlock)) return;
		BlockState updated = current;
		for (Direction dir : Direction.values()) {
			updated = updated.setValue(FACE_MODES.get(dir), visibleRender(level, pos, dir));
		}
		if (updated != current) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	@Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PipeShapes.of(renderAt(state, Direction.DOWN), renderAt(state, Direction.UP),
				renderAt(state, Direction.NORTH), renderAt(state, Direction.SOUTH),
				renderAt(state, Direction.WEST), renderAt(state, Direction.EAST));
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
