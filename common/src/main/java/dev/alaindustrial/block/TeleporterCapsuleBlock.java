package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
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

	public static final MapCodec<TeleporterCapsuleBlock> CODEC = simpleCodec(TeleporterCapsuleBlock::new);

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

	/**
	 * {@code tan(22.5°)}: half a facet of a regular octagon, per pixel of its apothem. Declared before the
	 * silhouettes below, which read it during class-init — a field further down would still be zero then.
	 */
	private static final double OCTAGON_HALF_FACET = Math.tan(Math.PI / 8);
	/** Apothem of the station's ring and of the crown's lower ring, in pixels — read off the models. */
	public static final double RING_APOTHEM = 7.7;
	/** Apothem of the glass barrel between them, outer surface. */
	private static final double BARREL_APOTHEM = 6.7;
	/** Apothem of the crown's upper ring, the one under the cap. */
	private static final double CROWN_TOP_APOTHEM = 6.55;

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
	/*
	 * The top cell as the crosshair meets it (MOD-803): the same walls under a crown that follows the model
	 * — an octagonal ring, a narrower ring above it and the cap — instead of the square box the player
	 * bumps into. That box stood out past the ring's diagonals and put a flat lid over a cap that narrows
	 * to two pixels at the top.
	 */
	private static final Map<Direction, VoxelShape> TOP_CLOSED_OUTLINE =
			Shapes.rotateHorizontal(Shapes.or(sidesAndBack(8), front(8), crownOutline(0)));
	private static final Map<Direction, VoxelShape> TOP_OPEN_OUTLINE =
			Shapes.rotateHorizontal(Shapes.or(sidesAndBack(8), crownOutline(0)));
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

	/**
	 * The crown as the model draws it, {@code dy} pixels up from the top cell's floor: the ring from 8 to
	 * 14, the narrower ring to 15.2 and the stepped cap to the top of the block.
	 */
	private static VoxelShape crownOutline(double dy) {
		return Shapes.or(
				octagon(RING_APOTHEM, dy + 8, dy + 14),
				octagon(CROWN_TOP_APOTHEM, dy + 14, dy + 15.2),
				Block.box(6, dy + 14, 6, 10, dy + 15.5, 10),
				Block.box(7, dy + 15.5, 7, 9, dy + 16, 9));
	}

	/**
	 * A regular octagon prism centred on the block, from {@code y0} to {@code y1} pixels: three boxes — two
	 * crossing bars and the square whose corners touch the diagonal facets. It lies inside the octagon and
	 * misses only the four slivers along the diagonals, so an outline drawn from it never stands out past
	 * the model, and it is the same for every facing.
	 */
	public static VoxelShape octagon(double apothem, double y0, double y1) {
		double facet = apothem * OCTAGON_HALF_FACET;
		double corner = apothem * (1 + OCTAGON_HALF_FACET) / 2;
		return Shapes.or(
				Block.box(8 - apothem, y0, 8 - facet, 8 + apothem, y1, 8 + facet),
				Block.box(8 - facet, y0, 8 - apothem, 8 + facet, y1, 8 + apothem),
				Block.box(8 - corner, y0, 8 - corner, 8 + corner, y1, 8 + corner));
	}

	/**
	 * The whole assembled capsule as one solid silhouette, in the station's pixels, facing north (MOD-803):
	 * the station's ring, the glass barrel with its console, spine and rod, and the crown. The hover outline
	 * ({@link TeleporterCapsuleOutline}) draws it whichever of the three blocks the player aims at.
	 */
	static VoxelShape capsuleSilhouette() {
		return Shapes.or(
				octagon(RING_APOTHEM, 0, 10),
				octagon(BARREL_APOTHEM, 10, 40),
				// The side console, the back spine and the rod beside the console.
				Block.box(13.5, 22, 5, 15.8, 29, 9),
				Block.box(6, 10, 14.4, 10, 40, 15.8),
				Block.box(14.8, 10, 6.5, 15.6, 40, 7.5),
				crownOutline(32));
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
	protected MapCodec<? extends TeleporterCapsuleBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, FACING, OPEN, GLASS);
	}

	/** What the crosshair hits and the outline traces: the top cell's crown follows the model (MOD-803). */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		boolean open = state.getValue(OPEN);
		return state.getValue(PART) == Part.MIDDLE
				? (open ? MIDDLE_OPEN : MIDDLE_CLOSED).get(facing)
				: (open ? TOP_OPEN_OUTLINE : TOP_CLOSED_OUTLINE).get(facing);
	}

	/** What a player bumps into and stands on: the square crown, as it always was. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
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
