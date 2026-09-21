package dev.alaindustrial.block;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The two glass cells of an assembled teleporter capsule (MOD-112): the barrel above the station and
 * the crown above that.
 *
 * <p>The player never holds this block. Two blocks of vanilla glass on a station become it
 * ({@link TeleporterBlock#tryAssemble}); taking the capsule apart turns each cell back into the glass it
 * was built from. It therefore has no item and no creative-tab entry.
 *
 * <p><b>Everything lives in the block state</b> — no block entity. Which part a cell is, which way the
 * door faces, whether it is open, and which glass the cell was made of: all four are needed to draw the
 * capsule or to hand the glass back, and a state reaches the client and survives a reload for nothing.
 * The loot table reads {@link #GLASS}, which is why every way of destroying a cell — a pickaxe, an
 * explosion — returns exactly the glass that went in.
 *
 * <p><b>The door.</b> Its three front facets sink into the floor, drawn by
 * {@code TeleporterCapsuleDoorRenderer} on the station. A click on either cell opens it; it shuts itself
 * after {@link Config#teleporterCapsuleDoorOpenTicks}, but never while someone stands in the doorway.
 * Only the middle cell keeps the schedule, so there is one timer per capsule.
 */
public class TeleporterCapsuleBlock extends Block {

	/** Which cell of the capsule this is. */
	public enum Part implements StringRepresentable {
		MIDDLE("middle"),
		TOP("top");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** The glass this very cell was built from — the one its loot table hands back. */
	public static final EnumProperty<CapsuleGlass> GLASS = EnumProperty.create("glass", CapsuleGlass.class);

	/**
	 * How far the doorway reaches in from the front edge, in pixels. A player standing in the middle of
	 * the capsule starts at 3.2, so they are inside rather than in the doorway and the door may close
	 * behind them — which is what an arrival looks like.
	 */
	private static final double DOORWAY_DEPTH = 3.0;
	/** Where the door panels start, measured from the middle cell's floor: the capsule floor at 9/16. */
	private static final double DOORWAY_BELOW_MIDDLE = 7.0 / 16.0;
	/** Where they end: the crown, half a block into the top cell. */
	private static final double DOORWAY_ABOVE_MIDDLE = 1.5;

	/*
	 * Silhouettes, assembled once at class-init (ADR-023). The glass barrel is an octagon; collision is
	 * its axis-aligned stand-in, one pixel of wall with the diagonals filled in at the corners. The
	 * interior between the walls is 12 pixels across, and a player is 9.6.
	 */
	private static final Map<Direction, VoxelShape> MIDDLE_CLOSED =
			Shapes.rotateHorizontal(Shapes.or(sidesAndBack(16), front(16)));
	private static final Map<Direction, VoxelShape> MIDDLE_OPEN = Shapes.rotateHorizontal(sidesAndBack(16));
	private static final Map<Direction, VoxelShape> TOP_CLOSED =
			Shapes.rotateHorizontal(Shapes.or(sidesAndBack(8), front(8), crown()));
	private static final Map<Direction, VoxelShape> TOP_OPEN =
			Shapes.rotateHorizontal(Shapes.or(sidesAndBack(8), crown()));
	/** The doorway, as a box inside the middle cell's footprint, for each facing. */
	private static final Map<Direction, VoxelShape> DOORWAY =
			Shapes.rotateHorizontal(Block.box(1, 0, 0, 15, 16, DOORWAY_DEPTH));

	private static VoxelShape sidesAndBack(double top) {
		return Shapes.or(
				Block.box(1, 0, 3, 2, top, 13),
				// The side console and its stand reach out to x=15.8.
				Block.box(14, 0, 3, 16, top, 13),
				// The back spine reaches out to z=15.8.
				Block.box(3, 0, 14, 13, top, 16),
				Block.box(1, 0, 13, 3, top, 15),
				Block.box(13, 0, 13, 15, top, 15));
	}

	private static VoxelShape front(double top) {
		return Shapes.or(
				Block.box(3, 0, 1, 13, top, 2),
				Block.box(1, 0, 1, 3, top, 3),
				Block.box(13, 0, 1, 15, top, 3));
	}

	private static VoxelShape crown() {
		return Block.box(0.5, 8, 0.5, 15.5, 16, 15.5);
	}

	public TeleporterCapsuleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(PART, Part.MIDDLE)
				.setValue(FACING, Direction.NORTH)
				.setValue(OPEN, false)
				.setValue(GLASS, CapsuleGlass.CLEAR));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, FACING, OPEN, GLASS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		boolean open = state.getValue(OPEN);
		return state.getValue(PART) == Part.MIDDLE
				? (open ? MIDDLE_OPEN : MIDDLE_CLOSED).get(facing)
				: (open ? TOP_OPEN : TOP_CLOSED).get(facing);
	}

	/** The middle cell of the capsule {@code state} at {@code pos} belongs to. */
	public static BlockPos middlePos(BlockState state, BlockPos pos) {
		return state.getValue(PART) == Part.TOP ? pos.below() : pos;
	}

	/**
	 * Where the station of a capsule cell stands, or {@code pos} itself for any other block — so that a
	 * remote or a chip used on the glass reaches the station the player obviously meant.
	 */
	public static BlockPos stationPos(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TeleporterCapsuleBlock)) {
			return pos;
		}
		return middlePos(state, pos).below();
	}

	/** Whether the capsule standing on the station at {@code station} has its door open. */
	public static boolean isDoorOpen(BlockGetter level, BlockPos station) {
		BlockState middle = level.getBlockState(station.above());
		return middle.getBlock() instanceof TeleporterCapsuleBlock && middle.getValue(OPEN);
	}

	/**
	 * A click on either cell works the door: shut, it opens; open, it closes — unless someone is in the
	 * doorway, in which case the click does nothing rather than shut the door on them.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockPos middle = middlePos(state, pos);
			BlockState middleState = level.getBlockState(middle);
			// A click while the panels are still travelling does nothing: rapid clicking would otherwise
			// reverse the door every few ticks and it would never finish a slide.
			if (level.getBlockEntity(middle.below()) instanceof TeleporterBlockEntity station
					&& !station.doorMayToggle(level.getGameTime())) {
				return InteractionResult.SUCCESS;
			}
			if (middleState.getBlock() instanceof TeleporterCapsuleBlock) {
				if (!middleState.getValue(OPEN)) {
					setDoor(level, middle, true);
				} else if (!doorwayOccupied(level, middle, middleState.getValue(FACING))) {
					setDoor(level, middle, false);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** The door's own close, booked when it opened. Re-books itself while the doorway is in use. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PART) != Part.MIDDLE || !state.getValue(OPEN)) {
			return;
		}
		if (doorwayOccupied(level, pos, state.getValue(FACING))) {
			level.scheduleTick(pos, this, Config.teleporterCapsuleDoorRecheckTicks);
			return;
		}
		setDoor(level, pos, false);
	}

	/**
	 * Opens or shuts the door of the capsule whose middle cell is at {@code middle}. Public for the game
	 * tests, which cannot click.
	 */
	public static void setDoor(Level level, BlockPos middle, boolean open) {
		BlockState middleState = level.getBlockState(middle);
		if (!(middleState.getBlock() instanceof TeleporterCapsuleBlock capsule)
				|| middleState.getValue(PART) != Part.MIDDLE || middleState.getValue(OPEN) == open) {
			return;
		}
		level.setBlock(middle, middleState.setValue(OPEN, open), Block.UPDATE_ALL);
		BlockState topState = level.getBlockState(middle.above());
		if (topState.getBlock() instanceof TeleporterCapsuleBlock && topState.getValue(PART) == Part.TOP) {
			level.setBlock(middle.above(), topState.setValue(OPEN, open), Block.UPDATE_ALL);
		}
		if (open) {
			level.scheduleTick(middle, capsule, Config.teleporterCapsuleDoorOpenTicks);
		}
		if (level.getBlockEntity(middle.below()) instanceof TeleporterBlockEntity station) {
			station.markDoorToggled(level.getGameTime());
		}
		// The airlock's own voice, pitched up: this is a glass canopy, not a pressure bulkhead. Null
		// player so that everyone nearby hears it, the one who clicked included.
		level.playSound(null, middle,
				(open ? ModSounds.REACTOR_DOOR_OPEN : ModSounds.REACTOR_DOOR_CLOSE).get(),
				SoundSource.BLOCKS, 0.8f, 1.2f + level.getRandom().nextFloat() * 0.1f);
	}

	/**
	 * Whether anything stands where the door panels would close, the whole height from the capsule floor
	 * up to the crown.
	 *
	 * <p>{@code getEntities}, not {@code noCollision}: a shut door is itself a collider, so a collision
	 * test would read an empty doorway as blocked and the door would never close — the reactor airlock
	 * learned the same thing.
	 */
	public static boolean doorwayOccupied(Level level, BlockPos middle, Direction facing) {
		AABB band = DOORWAY.get(facing).bounds();
		AABB doorway = new AABB(
				middle.getX() + band.minX, middle.getY() - DOORWAY_BELOW_MIDDLE, middle.getZ() + band.minZ,
				middle.getX() + band.maxX, middle.getY() + DOORWAY_ABOVE_MIDDLE, middle.getZ() + band.maxZ);
		return !level.getEntities((Entity) null, doorway, EntitySelector.NO_SPECTATORS).isEmpty();
	}

	/**
	 * A cell that has lost its partner or its station turns back into its glass. This is the whole
	 * disassembly story: a pickaxe, an explosion, a command and a lost station all end in a shape update,
	 * and each cell then returns what it was made of — the one broken by the loot table, the others by
	 * becoming that glass again on the spot.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		boolean middle = state.getValue(PART) == Part.MIDDLE;
		boolean lost = middle
				? (directionToNeighbour == Direction.UP && !isPart(neighbourState, Part.TOP))
						|| (directionToNeighbour == Direction.DOWN && !TeleporterBlock.isFormed(neighbourState))
				: directionToNeighbour == Direction.DOWN && !isPart(neighbourState, Part.MIDDLE);
		if (lost) {
			return state.getValue(GLASS).block().defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	private static boolean isPart(BlockState state, Part part) {
		return state.getBlock() instanceof TeleporterCapsuleBlock && state.getValue(PART) == part;
	}
}
