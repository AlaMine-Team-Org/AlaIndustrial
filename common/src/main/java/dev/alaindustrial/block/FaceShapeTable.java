package dev.alaindustrial.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The assembled shapes of a block whose geometry is a core plus one arm per connected face — the two
 * pipes and every cable grade — held one per distinct GEOMETRY instead of one per blockstate.
 *
 * <p><b>Why this exists (MOD-562).</b> {@code getShape} is not called once per state: for a block
 * without a dynamic shape, {@code BlockBehaviour.BlockStateBase.initCache()} asks for the shape
 * twenty times — once for the collision shape, eighteen times through
 * {@code SupportType.isSupporting} (six faces times three support types, each reaching
 * {@code getBlockSupportShape} and so {@code getShape}), and once for
 * {@code isCollisionShapeFullBlock}. A pipe that rebuilt its shape inside that call paid a
 * {@code Shapes.or} per arm every time, and {@code Shapes.or} is
 * {@code joinUnoptimized(..).optimize()} — {@code optimize()} reassembling the shape from all of its
 * boxes. MOD-540 took the item pipe from 4 096 states to 38 416 and the fluid pipe to 76 832, and
 * the client then spent 440 s of a 530 s startup inside {@code ItemPipeBlock.getShape}.
 *
 * <p><b>Why the table is so much smaller than the state count.</b> The routing mode a face shows is
 * not geometry: {@code NEUTRAL}, {@code EXTRACT} and {@code INSERT} draw the same arm and differ only
 * in texture. The shape depends on three answers per face — no arm, the ordinary arm, or the dropped
 * one — so 38 416 pipe states collapse onto 324 shapes, and the same 324 serve the fluid pipe (its
 * {@code filled} property is not geometry either) and all eight cable grades (neither is
 * {@code breaker_open}).
 *
 * <p>The table is filled eagerly at class-init: registration visits every state anyway, so deferring
 * saves nothing, and an eager table needs no locking on a pass that may run in parallel.
 */
public final class FaceShapeTable {
	/** This face draws no arm. */
	public static final int NONE = 0;
	/** This face draws its ordinary arm. */
	public static final int ARM = 1;
	/** This face drops its arm to hug a half-block neighbour. */
	public static final int LOW = 2;

	private static final int CODES = 3;
	private static final int FACES = 6;
	private static final int SIZE = CODES * CODES * CODES * CODES * CODES * CODES;

	/** Whether the face at that {@link Direction#ordinal()} is vertical, i.e. has no dropped arm. */
	private static final boolean[] VERTICAL = new boolean[FACES];

	static {
		Direction[] order = Direction.values();
		// index() spells the ordinal order out as a formula, so a mapping change has to fail loudly
		// rather than silently hand every state a neighbour's shape.
		if (order.length != FACES || order[0] != Direction.DOWN || order[1] != Direction.UP
				|| order[2] != Direction.NORTH || order[3] != Direction.SOUTH
				|| order[4] != Direction.WEST || order[5] != Direction.EAST) {
			throw new IllegalStateException("Direction order changed — FaceShapeTable indexes by it");
		}
		for (int face = 0; face < FACES; face++) {
			VERTICAL[face] = order[face].getAxis().isVertical();
		}
	}

	/**
	 * Assembles one combination; the codes are indexed by {@link Direction#ordinal()}.
	 *
	 * <p>Implementations keep the face order their block's own loop used before this table existed.
	 * That is belt and braces, not a requirement: reversing the order was measured to give the same
	 * box split, because {@code Shapes.or} ends in {@code optimize()} and that reassembles the shape
	 * from its boxes. Keeping the order means the one-off proof that the geometry did not change
	 * (MOD-562) does not rest on that observation staying true of a future vanilla.
	 */
	@FunctionalInterface
	public interface Assembler {
		VoxelShape assemble(int[] codes);
	}

	private final VoxelShape[] shapes;

	public FaceShapeTable(Assembler assembler) {
		this.shapes = new VoxelShape[SIZE];
		int[] codes = new int[FACES];
		for (int i = 0; i < SIZE; i++) {
			int rest = i;
			for (int face = FACES - 1; face >= 0; face--) {
				codes[face] = rest % CODES;
				rest /= CODES;
			}
			// A vertical face has no dropped arm, so neither block can ask for one: point those slots
			// at the plain-arm shape they would equal instead of assembling them. That leaves the table
			// without holes to reason about and folds 729 slots onto the 324 shapes that really differ.
			// The canonical index is always below i — LOW is the largest code — so it is already filled.
			int canonical = canonicalIndex(codes);
			this.shapes[i] = canonical == i ? assembler.assemble(codes) : this.shapes[canonical];
		}
	}

	/** The shape for these six face codes, named in {@link Direction#ordinal()} order. */
	public VoxelShape get(int down, int up, int north, int south, int west, int east) {
		return this.shapes[index(down, up, north, south, west, east)];
	}

	private static int index(int down, int up, int north, int south, int west, int east) {
		return ((((down * CODES + up) * CODES + north) * CODES + south) * CODES + west) * CODES + east;
	}

	private static int canonicalIndex(int[] codes) {
		int index = 0;
		for (int face = 0; face < FACES; face++) {
			int code = codes[face];
			index = index * CODES + (code == LOW && VERTICAL[face] ? ARM : code);
		}
		return index;
	}
}
