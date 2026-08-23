package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModSounds;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The reactor airlock (MOD-468, stage 1) — the only way into a reactor room, and deliberately not a
 * door you can prop open.
 *
 * <p><b>Why this is not a {@code DoorBlock}.</b> Vanilla's iron door is held open for as long as the
 * signal lasts; the airlock is the opposite contract — a redstone <em>pulse</em> lets the player
 * through and the door shuts itself. Subclassing would also require a {@code BlockSetType}, whose
 * registry is private in 26.2 (no cross-loader way to add one), so the shape, the properties and the
 * two-half bookkeeping are reimplemented here from vanilla's own logic rather than inherited.
 *
 * <p><b>The contract.</b>
 * <ul>
 *   <li>Hands do nothing — {@code useWithoutItem} is left at {@code BlockBehaviour}'s default
 *       {@code PASS}, so there is no "open by clicking" path to disable.</li>
 *   <li>A <em>rising</em> redstone edge opens it and schedules the close, {@link Config#reactorDoorOpenTicks}
 *       later. Holding the lever down does not hold the door: the scheduled close still fires, and only a
 *       fresh edge opens it again. That is what makes the room's seal a matter of seconds, not of whether
 *       someone left a lever on.</li>
 *   <li>The close waits while anyone stands in the doorway, re-scheduling itself. A door that shuts
 *       through the player would be a trap in a room whose whole point is that you must be inside it.</li>
 * </ul>
 *
 * <p>{@code POWERED} is stored for exactly one reason — edge detection. Without it every neighbour
 * update while the lever is on reads as "signal present" and would re-open a door the player just
 * watched close.
 */
public class ReactorDoorBlock extends Block {

	public static final MapCodec<ReactorDoorBlock> CODEC = simpleCodec(ReactorDoorBlock::new);

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/**
	 * Whether the shell this door belongs to currently passes its scan — cosmetic, set by the
	 * controller, and the reason a finished room stops looking like separate crates (see
	 * {@link ReactorShellBlock}).
	 */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/** Vanilla's door slab, 3/16 thick on the hinge side, rotated per facing. */
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(16.0, 13.0, 16.0));

	public ReactorDoorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(HINGE, DoorHingeSide.LEFT)
				.setValue(OPEN, false)
				.setValue(POWERED, false)
				.setValue(FORMED, false));
	}

	@Override
	protected MapCodec<? extends ReactorDoorBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF, FACING, OPEN, HINGE, POWERED, FORMED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		boolean open = state.getValue(OPEN);
		boolean rightHinge = state.getValue(HINGE) == DoorHingeSide.RIGHT;
		Direction shapeFacing = open
				? (rightHinge ? facing.getCounterClockWise() : facing.getClockWise())
				: facing;
		return SHAPES.get(shapeFacing);
	}

	/** An open airlock is a corridor; a closed one is a wall. Mobs must path accordingly. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.AIR ? state.getValue(OPEN) : false;
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null; // no room for the upper half
		}
		// Placed shut, whatever the redstone is doing: an airlock that spawns open because a lever
		// happens to be on nearby would leave the room unsealed the moment it is built.
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection())
				.setValue(HINGE, hingeSide(context))
				.setValue(POWERED, level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above()))
				.setValue(OPEN, false)
				.setValue(HALF, DoubleBlockHalf.LOWER);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState belowState = level.getBlockState(below);
		return state.getValue(HALF) == DoubleBlockHalf.LOWER
				? belowState.isFaceSturdy(level, below, Direction.UP)
				: belowState.is(this);
	}

	/** Keeps the two halves as one object: losing either takes the other with it. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
			RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (directionToNeighbour.getAxis() == Direction.Axis.Y
				&& (half == DoubleBlockHalf.LOWER) == (directionToNeighbour == Direction.UP)) {
			return neighbourState.is(this) && neighbourState.getValue(HALF) != half
					? state
					: Blocks.AIR.defaultBlockState();
		}
		if (half == DoubleBlockHalf.LOWER && directionToNeighbour == Direction.DOWN
				&& !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState,
				random);
	}

	/**
	 * Opens on a rising edge only. The falling edge is deliberately ignored: the door is closed by its
	 * own scheduled tick, so releasing the button neither closes it early nor keeps it shut.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level.isClientSide()) {
			return;
		}
		// Either half may be the one the signal reaches; both halves observe the same two positions so
		// the pair cannot disagree about whether it is powered.
		BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
		if (powered == state.getValue(POWERED)) {
			return;
		}
		setBothHalves(level, lower, h -> h.setValue(POWERED, powered));
		if (powered) {
			openAirlock(level, lower);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		BlockState lowerState = level.getBlockState(lower);
		if (!lowerState.is(this) || !lowerState.getValue(OPEN)) {
			return;
		}
		if (doorwayOccupied(level, lower)) {
			// Someone is still walking through — try again shortly rather than closing on them.
			level.scheduleTick(lower, this, Config.reactorDoorOccupiedRecheckTicks);
			return;
		}
		setBothHalves(level, lower, s -> s.setValue(OPEN, false));
		playDoorSound(level, lower, false);
	}

	/** Opens both halves and books the close. Idempotent: re-opening an open door only defers the close. */
	private void openAirlock(Level level, BlockPos lower) {
		BlockState lowerState = level.getBlockState(lower);
		if (!lowerState.is(this)) {
			return;
		}
		if (!lowerState.getValue(OPEN)) {
			setBothHalves(level, lower, s -> s.setValue(OPEN, true));
			playDoorSound(level, lower, true);
		}
		level.scheduleTick(lower, this, Config.reactorDoorOpenTicks);
	}

	/**
	 * Applies a change to both halves in one go. Flag 10 = {@code UPDATE_CLIENTS | UPDATE_IMMEDIATE},
	 * vanilla's own choice for door toggles: neighbours are not re-notified, which is what keeps this
	 * from bouncing back into {@link #neighborChanged}.
	 */
	private void setBothHalves(Level level, BlockPos lower, java.util.function.UnaryOperator<BlockState> change) {
		BlockState lowerState = level.getBlockState(lower);
		if (lowerState.is(this)) {
			level.setBlock(lower, change.apply(lowerState), 10);
		}
		BlockPos upper = lower.above();
		BlockState upperState = level.getBlockState(upper);
		if (upperState.is(this)) {
			level.setBlock(upper, change.apply(upperState), 10);
		}
	}

	private static boolean doorwayOccupied(Level level, BlockPos lower) {
		AABB doorway = AABB.encapsulatingFullBlocks(lower, lower.above());
		// getEntities, not noCollision: a closed door is itself a collision, so noCollision would report
		// "blocked" for an empty doorway and the airlock would never shut.
		return !level.getEntities((Entity) null, doorway, EntitySelector.NO_SPECTATORS).isEmpty();
	}

	/**
	 * The airlock's own voice (MOD-472). It used to borrow vanilla's iron door, which undersold what
	 * this block is: a pressure seal on a nuclear containment shell, not a hinged panel. The samples are
	 * a steam seal releasing on the way open and a pneumatic lock engaging on the way shut.
	 *
	 * <p>{@code null} as the first argument on purpose — a block entity has no client-side prediction, so
	 * naming a player there is precisely what stops the nearest one from hearing it.
	 */
	private static void playDoorSound(Level level, BlockPos pos, boolean opening) {
		level.playSound(null, pos,
				(opening ? ModSounds.REACTOR_DOOR_OPEN : ModSounds.REACTOR_DOOR_CLOSE).get(),
				SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	/** Prevents the upper half from dropping a second door when the lower one is mined. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state))
				&& state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			BlockState belowState = level.getBlockState(below);
			if (belowState.is(state.getBlock()) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
				BlockState replacement = belowState.getFluidState().is(Fluids.WATER)
						? Blocks.WATER.defaultBlockState()
						: Blocks.AIR.defaultBlockState();
				level.setBlock(below, replacement, 35); // UPDATE_ALL | UPDATE_SUPPRESS_DROPS
				level.levelEvent(player, 2001, below, Block.getId(belowState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return mirror == Mirror.NONE
				? state
				: state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
	}

	/**
	 * Vanilla's hinge rule, reimplemented: prefer the side with an adjacent door or a solid block, and
	 * fall back to which half of the block the player clicked.
	 */
	private DoorHingeSide hingeSide(BlockPlaceContext context) {
		BlockGetter level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction facing = context.getHorizontalDirection();
		BlockPos above = pos.above();
		Direction left = facing.getCounterClockWise();
		BlockPos leftPos = pos.relative(left);
		BlockState leftState = level.getBlockState(leftPos);
		BlockPos leftAbove = above.relative(left);
		BlockState leftAboveState = level.getBlockState(leftAbove);
		Direction right = facing.getClockWise();
		BlockPos rightPos = pos.relative(right);
		BlockState rightState = level.getBlockState(rightPos);
		BlockPos rightAbove = above.relative(right);
		BlockState rightAboveState = level.getBlockState(rightAbove);

		int bias = (leftState.isCollisionShapeFullBlock(level, leftPos) ? -1 : 0)
				+ (leftAboveState.isCollisionShapeFullBlock(level, leftAbove) ? -1 : 0)
				+ (rightState.isCollisionShapeFullBlock(level, rightPos) ? 1 : 0)
				+ (rightAboveState.isCollisionShapeFullBlock(level, rightAbove) ? 1 : 0);

		boolean leftIsDoor = leftState.is(this) && leftState.getValue(HALF) == DoubleBlockHalf.LOWER;
		boolean rightIsDoor = rightState.is(this) && rightState.getValue(HALF) == DoubleBlockHalf.LOWER;
		if ((!leftIsDoor || rightIsDoor) && bias <= 0) {
			if ((!rightIsDoor || leftIsDoor) && bias >= 0) {
				int stepX = facing.getStepX();
				int stepZ = facing.getStepZ();
				double clickX = context.getClickLocation().x - pos.getX();
				double clickZ = context.getClickLocation().z - pos.getZ();
				boolean towardsRight = (stepX >= 0 || clickZ >= 0.5)
						&& (stepX <= 0 || clickZ <= 0.5)
						&& (stepZ >= 0 || clickX <= 0.5)
						&& (stepZ <= 0 || clickX >= 0.5);
				return towardsRight ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			}
			return DoorHingeSide.LEFT;
		}
		return DoorHingeSide.RIGHT;
	}
}
