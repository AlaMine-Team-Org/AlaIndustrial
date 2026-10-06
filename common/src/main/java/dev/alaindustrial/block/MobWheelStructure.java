package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.core.environment.MobWheelLayout;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Assembly, disassembly and geometry of the 3×3×3 mob wheel (MOD-763, decision D1).
 *
 * <p><b>Canonical frame.</b> Cells are {@code (x, y, z)} with each axis {@code 0..2}; the wheel's axle runs
 * along {@code z}, the gate side is {@code +z} ("the front") and the drive stands in the back right corner,
 * {@code (2, 0, 0)}. A structure with facing {@code F} turns canonical {@code +z} onto {@code F} and
 * canonical {@code +x} onto {@code F.getCounterClockWise()} — a rotation, never a mirror, so a mirrored
 * build simply does not match. Facing {@code SOUTH} is the canonical frame itself, which is also what the
 * generated render geometry ({@code client/render/MobWheelGeometry}) assumes: its yaw is 0 for {@code SOUTH}.
 *
 * <p><b>What the player places and what assembly adds.</b> Fourteen blocks: the drive, eleven frame posts
 * (the four corner columns, three high, minus the drive's own cell), the running wheel in the middle and the
 * gate in the middle of the front. The remaining thirteen cells must be free — air, or a plant or cover
 * (grass, flowers, a snow layer; {@link #freeCell}) that holds no fluid (owner review 2); assembly
 * breaks such plants with their drops, fills the cells with invisible {@code mob_wheel_cell} blocks and flips
 * every part to {@code formed}. Water or lava in a cell refuses the build. The drive's block entity then draws
 * the whole machine. The running wheel may stand in any facing.
 *
 * <p><b>Taking it apart.</b> Any member that disappears — mined, blown up, {@code /setblock} — reaches
 * {@link #onMemberRemoved} through its block's {@code affectNeighborsAfterRemoval}, which vanilla calls only
 * when the block itself changes (a gate swinging open keeps its block and does not count). The cells go,
 * the parts go back to their loose form, the occupant is let go. Nothing is dropped by this class.
 */
public final class MobWheelStructure {
	private MobWheelStructure() {
	}

	/** Assembled or not; carried by every placeable part. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/** Edge of the structure in blocks. */
	public static final int SIZE = 3;

	public static final Vec3i CONTROLLER = new Vec3i(2, 0, 0);
	public static final Vec3i ROTOR = new Vec3i(1, 1, 1);
	public static final Vec3i GATE = new Vec3i(1, 0, 2);
	/** The running deck: the cell the occupant stands in. */
	public static final Vec3i DECK = new Vec3i(1, 0, 1);
	/** Head room above the gate. */
	public static final Vec3i DOORWAY = new Vec3i(1, 1, 2);

	/**
	 * Height of the running surface above the structure's floor, in blocks: the inner face of the lowest
	 * running plank of the drawn wheel ({@link MobWheelLayout#ROTOR_FLOOR_PX}). The {@code floor} cell's
	 * collision top and the occupant's feet both sit here, so the mob stands on the planks, not in them.
	 */
	public static final double DECK_HEIGHT = MobWheelLayout.ROTOR_FLOOR_PX / 16.0;
	/** Lift over the surface the occupant is held at, so it never starts a tick inside the floor's box. */
	private static final double DECK_EPSILON = 1.0E-3;
	/**
	 * Inner face of the closed gate's panel, in blocks from the gate cell's inner edge: a mob reaching into
	 * the gate cell up to here is inside the wheel; anything beyond the panel is outside.
	 */
	private static final double GATE_PANEL_INNER = 6.0 / 16.0;

	/** Facings tried when assembling, in a fixed order so the result never depends on timing. */
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

	/** What stands at a canonical cell. */
	public enum Slot { CONTROLLER, FRAME, ROTOR, GATE, CELL }

	/** The slot at canonical {@code (x, y, z)}; each coordinate {@code 0..2}. */
	public static Slot slotAt(int x, int y, int z) {
		if (x == CONTROLLER.getX() && y == CONTROLLER.getY() && z == CONTROLLER.getZ()) {
			return Slot.CONTROLLER;
		}
		if (x == ROTOR.getX() && y == ROTOR.getY() && z == ROTOR.getZ()) {
			return Slot.ROTOR;
		}
		if (x == GATE.getX() && y == GATE.getY() && z == GATE.getZ()) {
			return Slot.GATE;
		}
		if (x != 1 && z != 1) {
			return Slot.FRAME;
		}
		return Slot.CELL;
	}

	/** Which kind of invisible cell assembly puts at a canonical {@link Slot#CELL}. */
	public static MobWheelCellShape cellShapeAt(int x, int y, int z) {
		if (x == DECK.getX() && y == DECK.getY() && z == DECK.getZ()) {
			return MobWheelCellShape.FLOOR;
		}
		if (x == DOORWAY.getX() && z >= 1 && y >= 1) {
			// The doorway, and the crown and lintel above the deck and the doorway: the deck is raised to the
			// running surface, so a zombie or villager (1.95 tall) standing on it reaches into the top row.
			return MobWheelCellShape.OPEN;
		}
		return MobWheelCellShape.SOLID;
	}

	/** Canonical {@code (x, y, z)} turned into a world offset for a structure facing {@code facing}. */
	public static Vec3i worldOffset(Direction facing, int x, int y, int z) {
		Direction right = facing.getCounterClockWise();
		return new Vec3i(right.getStepX() * x + facing.getStepX() * z, y,
				right.getStepZ() * x + facing.getStepZ() * z);
	}

	/** World position of the canonical corner {@code (0, 0, 0)} of the structure driven from {@code controller}. */
	public static BlockPos origin(BlockPos controller, Direction facing) {
		return controller.subtract(worldOffset(facing, CONTROLLER.getX(), CONTROLLER.getY(), CONTROLLER.getZ()));
	}

	/** World position of canonical cell {@code (x, y, z)}. */
	public static BlockPos at(BlockPos controller, Direction facing, int x, int y, int z) {
		return origin(controller, facing).offset(worldOffset(facing, x, y, z));
	}

	/** World position of canonical cell {@code cell}. */
	public static BlockPos at(BlockPos controller, Direction facing, Vec3i cell) {
		return at(controller, facing, cell.getX(), cell.getY(), cell.getZ());
	}

	/** Canonical cell of {@code pos}, or {@code null} when it lies outside the structure. */
	@Nullable
	public static Vec3i canonicalOf(BlockPos controller, Direction facing, BlockPos pos) {
		BlockPos d = pos.subtract(origin(controller, facing));
		Direction right = facing.getCounterClockWise();
		int x = d.getX() * right.getStepX() + d.getZ() * right.getStepZ();
		int z = d.getX() * facing.getStepX() + d.getZ() * facing.getStepZ();
		int y = d.getY();
		if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE) {
			return null;
		}
		return new Vec3i(x, y, z);
	}

	/** Where the occupant is held: the middle of the running deck, its feet on the running surface. */
	public static Vec3 anchor(BlockPos controller, Direction facing) {
		BlockPos deck = at(controller, facing, DECK);
		return new Vec3(deck.getX() + 0.5, deck.getY() + DECK_HEIGHT + DECK_EPSILON, deck.getZ() + 0.5);
	}

	/**
	 * Where a mob counts as inside the wheel: the deck's column (deck, rotor) and the gate cell up to the
	 * closed panel's inner face. A mob whose box touches it while the gate is closed is caught; one standing
	 * outside a closed gate cannot reach it, the panel's collision is in the way.
	 */
	public static AABB passage(BlockPos controller, Direction facing) {
		return canonicalBox(controller, facing, DECK.getX(), DECK.getY(), DECK.getZ(), DECK.getX() + 1.0,
				DECK.getY() + 2.0, GATE.getZ() + GATE_PANEL_INNER);
	}

	/**
	 * A box given in canonical block coordinates ({@code [x, x + 1]} is cell {@code x}) turned into world
	 * blocks for a structure facing {@code facing}.
	 */
	private static AABB canonicalBox(BlockPos controller, Direction facing, double x0, double y0, double z0,
			double x1, double y1, double z1) {
		Vec3 a = canonicalPoint(controller, facing, x0, y0, z0);
		Vec3 b = canonicalPoint(controller, facing, x1, y1, z1);
		return new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x),
				Math.max(a.y, b.y), Math.max(a.z, b.z));
	}

	/** A canonical point in block units turned into a world point (cell {@code (0,0,0)} spans {@code [0, 1]}). */
	private static Vec3 canonicalPoint(BlockPos controller, Direction facing, double x, double y, double z) {
		BlockPos o = origin(controller, facing);
		Direction right = facing.getCounterClockWise();
		double wx = o.getX() + 0.5 + right.getStepX() * (x - 0.5) + facing.getStepX() * (z - 0.5);
		double wz = o.getZ() + 0.5 + right.getStepZ() * (x - 0.5) + facing.getStepZ() * (z - 0.5);
		return new Vec3(wx, o.getY() + y, wz);
	}

	/** The whole 3×3×3 box in world blocks. */
	public static AABB bounds(BlockPos controller, Direction facing) {
		BlockPos a = at(controller, facing, 0, 0, 0);
		BlockPos b = at(controller, facing, SIZE - 1, SIZE - 1, SIZE - 1);
		return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
				Math.max(a.getX(), b.getX()) + 1.0, b.getY() + 1.0, Math.max(a.getZ(), b.getZ()) + 1.0);
	}

	/** The way a mob runs on the deck: across the axle, towards canonical {@code +x}. */
	public static Direction runDirection(Direction facing) {
		return facing.getCounterClockWise();
	}

	/** The yaw a mob faces when it runs along {@link #runDirection}. */
	public static float runYaw(Direction facing) {
		return runDirection(facing).toYRot();
	}

	// --- state queries ---

	public static boolean isController(BlockState state) {
		return state.is(ModContent.MOB_WHEEL_CONTROLLER.get());
	}

	/** True for an assembled member of any wheel: a formed part or a cell. */
	public static boolean isFormedMember(BlockState state) {
		if (state.is(ModContent.MOB_WHEEL_CELL.get())) {
			return true;
		}
		return isPart(state) && state.getValue(FORMED);
	}

	/** True for the four placeable parts, formed or loose. */
	public static boolean isPart(BlockState state) {
		return state.is(ModContent.MOB_WHEEL_CONTROLLER.get()) || state.is(ModContent.MOB_WHEEL_FRAME.get())
				|| state.is(ModContent.MOB_WHEEL_ROTOR.get()) || state.is(ModContent.MOB_WHEEL_GATE.get());
	}

	/** The block a slot wants, or {@code null} for a cell (which wants a {@link #freeCell}). */
	@Nullable
	private static Block partFor(Slot slot) {
		return switch (slot) {
			case CONTROLLER -> ModContent.MOB_WHEEL_CONTROLLER.get();
			case FRAME -> ModContent.MOB_WHEEL_FRAME.get();
			case ROTOR -> ModContent.MOB_WHEEL_ROTOR.get();
			case GATE -> ModContent.MOB_WHEEL_GATE.get();
			case CELL -> null;
		};
	}

	/** Whether {@code state} is ready to become {@code slot} of a new structure. */
	private static boolean fits(Slot slot, BlockState state) {
		Block want = partFor(slot);
		if (want == null) {
			return freeCell(state);
		}
		return state.is(want) && !state.getValue(FORMED);
	}

	/**
	 * Whether a cell holding {@code state} can take an invisible cell: air, or a plant or cover that holds no
	 * fluid — anything a placed block replaces ({@code canBeReplaced}: short grass, ferns, a snow layer), and
	 * the small plants vanilla lets a growing tree replace ({@code #replaceable_by_trees}: flowers, tall
	 * flowers, leaf litter) apart from leaves. Water and lava, source or flowing, and anything waterlogged are
	 * refused rather than destroyed unasked.
	 */
	public static boolean freeCell(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		boolean plant = state.canBeReplaced()
				|| state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.LEAVES);
		return plant && state.getFluidState().isEmpty();
	}

	// --- assembly ---

	/**
	 * Try to assemble the wheel driven from {@code controllerPos}, in each of the four facings. {@code public
	 * static} because a game test places blocks straight into the world, which never calls
	 * {@code setPlacedBy}. Starts only from a loose drive, so the neighbour updates assembly itself sets off
	 * cannot recurse into it.
	 *
	 * @return whether a structure was assembled
	 */
	public static boolean tryAssemble(Level level, BlockPos controllerPos) {
		if (level.isClientSide()) {
			return false;
		}
		BlockState state = level.getBlockState(controllerPos);
		if (!isController(state) || state.getValue(FORMED)) {
			return false;
		}
		for (Direction facing : FACINGS) {
			if (missing(level, controllerPos, facing) == 0) {
				assemble(level, controllerPos, facing);
				return true;
			}
		}
		return false;
	}

	/** A part changed near {@code pos}: try every loose drive close enough to share a structure with it. */
	public static void tryAssembleNear(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(probe);
			if (isController(state) && !state.getValue(FORMED) && tryAssemble(level, probe.immutable())) {
				return;
			}
		}
	}

	/** How many cells of the structure in this facing are not ready. */
	private static int missing(Level level, BlockPos controller, Direction facing) {
		int missing = 0;
		for (int x = 0; x < SIZE; x++) {
			for (int y = 0; y < SIZE; y++) {
				for (int z = 0; z < SIZE; z++) {
					if (!fits(slotAt(x, y, z), level.getBlockState(at(controller, facing, x, y, z)))) {
						missing++;
					}
				}
			}
		}
		return missing;
	}

	private static void assemble(Level level, BlockPos controller, Direction facing) {
		// The drive goes first: once it is formed, the neighbour updates the rest of this method causes find no
		// loose drive to start another assembly from.
		BlockState driveState = level.getBlockState(controller);
		level.setBlock(controller, driveState.setValue(FORMED, Boolean.TRUE)
				.setValue(MobWheelControllerBlock.FACING, facing), Block.UPDATE_ALL);
		for (int x = 0; x < SIZE; x++) {
			for (int y = 0; y < SIZE; y++) {
				for (int z = 0; z < SIZE; z++) {
					Slot slot = slotAt(x, y, z);
					BlockPos pos = at(controller, facing, x, y, z);
					BlockState state = level.getBlockState(pos);
					switch (slot) {
						case CONTROLLER -> { }
						case GATE -> level.setBlock(pos, state.setValue(FORMED, Boolean.TRUE)
								.setValue(MobWheelGateBlock.FACING, facing), Block.UPDATE_ALL);
						case FRAME, ROTOR ->
								level.setBlock(pos, state.setValue(FORMED, Boolean.TRUE), Block.UPDATE_ALL);
						case CELL -> {
							if (!level.getBlockState(pos).isAir()) { // a plant or snow layer: break it, drops and all
								level.destroyBlock(pos, true);
							}
							level.setBlock(pos, ModContent.MOB_WHEEL_CELL.get().defaultBlockState()
									.setValue(MobWheelCellBlock.SHAPE, cellShapeAt(x, y, z)), Block.UPDATE_ALL);
						}
					}
				}
			}
		}
		level.playSound(null, controller, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
		if (level.getBlockEntity(controller) instanceof MobWheelBlockEntity drive) {
			drive.onFormed();
		}
	}

	// --- disassembly ---

	/**
	 * Take apart the structure driven from {@code controller}: the drive first (so the cascade of cell
	 * removals finds no formed drive to start again from), then the parts, then the cells.
	 */
	public static void disassemble(Level level, BlockPos controller, Direction facing) {
		BlockState drive = level.getBlockState(controller);
		if (isController(drive) && drive.getValue(FORMED)) {
			if (level.getBlockEntity(controller) instanceof MobWheelBlockEntity be) {
				be.onUnformed();
			}
			level.setBlock(controller, drive.setValue(FORMED, Boolean.FALSE), Block.UPDATE_ALL);
		}
		for (int x = 0; x < SIZE; x++) {
			for (int y = 0; y < SIZE; y++) {
				for (int z = 0; z < SIZE; z++) {
					BlockPos pos = at(controller, facing, x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(ModContent.MOB_WHEEL_CELL.get())) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					} else if (isPart(state) && !isController(state) && state.getValue(FORMED)) {
						level.setBlock(pos, state.setValue(FORMED, Boolean.FALSE), Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/**
	 * A formed member at {@code pos} has just been replaced by another block — called from
	 * {@code affectNeighborsAfterRemoval}, i.e. after the block (and any block entity) is gone.
	 */
	public static void onMemberRemoved(ServerLevel level, BlockPos pos, BlockState oldState) {
		if (isController(oldState)) {
			if (oldState.getValue(FORMED)) {
				disassemble(level, pos, oldState.getValue(MobWheelControllerBlock.FACING));
			}
			return;
		}
		if (!isFormedMember(oldState)) {
			return;
		}
		BlockPos controller = findFormedController(level, pos);
		if (controller != null) {
			disassemble(level, controller, level.getBlockState(controller).getValue(MobWheelControllerBlock.FACING));
		}
	}

	/** The formed drive whose structure contains {@code pos}, or {@code null}. */
	@Nullable
	public static BlockPos findFormedController(Level level, BlockPos pos) {
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(probe);
			if (isController(state) && state.getValue(FORMED)
					&& canonicalOf(probe, state.getValue(MobWheelControllerBlock.FACING), pos) != null) {
				return probe.immutable();
			}
		}
		return null;
	}

	// --- diagnosis ---

	/**
	 * The actionbar line for an empty-hand click on a loose drive: the first thing missing in the facing that
	 * is closest to complete. Never {@code null}: a drive that would assemble now says nothing is missing.
	 */
	public static Component diagnose(Level level, BlockPos controller) {
		Direction best = FACINGS[0];
		int bestMissing = Integer.MAX_VALUE;
		for (Direction facing : FACINGS) {
			int missing = missing(level, controller, facing);
			if (missing < bestMissing) {
				bestMissing = missing;
				best = facing;
			}
		}
		if (bestMissing == 0) {
			return Component.translatable("message.alaindustrial.mob_wheel.ready");
		}
		for (int y = 0; y < SIZE; y++) {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					Slot slot = slotAt(x, y, z);
					BlockPos pos = at(controller, best, x, y, z);
					BlockState state = level.getBlockState(pos);
					if (fits(slot, state)) {
						continue;
					}
					String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
					Block want = partFor(slot);
					if (want == null) {
						String key = state.getFluidState().isEmpty() ? "message.alaindustrial.mob_wheel.blocked"
								: "message.alaindustrial.mob_wheel.fluid";
						return Component.translatable(key, bestMissing, state.getBlock().getName(), where);
					}
					return Component.translatable("message.alaindustrial.mob_wheel.missing", bestMissing,
							want.getName(), where);
				}
			}
		}
		return Component.translatable("message.alaindustrial.mob_wheel.ready");
	}
}
