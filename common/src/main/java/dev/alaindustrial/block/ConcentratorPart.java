package dev.alaindustrial.block;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Which cell of an assembled Mirror Concentrator (MOD-603) a block is — or {@link #LOOSE}, meaning
 * it is not part of a structure at all.
 *
 * <p><b>Why a ninth value instead of eight.</b> Same reasoning as {@link WorkstationPart}: the
 * player crafts and places loose sections, so "not yet part of a machine" is a state the world
 * genuinely holds. Naming it makes "a top-back cell with nothing under it" unrepresentable rather
 * than merely unlikely.
 *
 * <p><b>The eight cell values are not written out here — they are checked against the generator.</b>
 * Offsets and shapes come from {@link ConcentratorCellGeometry}, which the asset generator writes
 * from the designer's model; the constants below only give them names. The static block asserts the
 * two agree, so renaming a cell in one place and not the other fails at class load instead of
 * shipping a structure whose blocks quietly point at the wrong eighth of the model.
 *
 * <p><b>Canonical orientation.</b> Offsets are stated for a structure whose core faces north, and
 * are turned into world offsets by {@link #worldOffset}: {@code +x} is the core's right hand,
 * {@code +z} is straight back from its face, {@code +y} is up. Every rotation in this class goes
 * through vanilla's own {@code getClockWise}/{@code getOpposite}, so there is no rotation matrix to
 * get wrong.
 */
public enum ConcentratorPart implements StringRepresentable {
	/** A loose section: a plain block the player crafted, placed and can pick back up. */
	LOOSE("loose"),
	/** The bottom cell the grown panel itself occupies — the only cell that holds energy. */
	CORE("core"),
	/** Bottom, one step to the core's right. */
	RIGHT("right"),
	/** Bottom, one step back from the core's face. */
	BACK("back"),
	/** Bottom, back and to the right — the corner diagonally opposite the core. */
	BACK_RIGHT("back_right"),
	/** Directly above the core. */
	TOP("top"),
	/** Above {@link #RIGHT}. */
	TOP_RIGHT("top_right"),
	/** Above {@link #BACK}. */
	TOP_BACK("top_back"),
	/** Above {@link #BACK_RIGHT}. */
	TOP_BACK_RIGHT("top_back_right");

	/** The eight cell values, in the generator's order — {@link #LOOSE} excluded. */
	public static final ConcentratorPart[] CELLS =
			Arrays.copyOfRange(values(), CORE.ordinal(), values().length);

	/**
	 * Ready-made shapes, {@code [horizontal facing][part]}. Built once at class load rather than in
	 * {@code getShape}, which the engine calls twenty times per state (ADR-023). {@link #LOOSE} has
	 * no row here — its shape does not depend on the facing, so it is a constant of its own.
	 */
	private static final VoxelShape[][] SHAPES = buildShapes();

	static {
		if (CELLS.length != ConcentratorCellGeometry.NAMES.length) {
			throw new IllegalStateException("cell count disagrees with the generated geometry: "
					+ CELLS.length + " here, " + ConcentratorCellGeometry.NAMES.length + " generated");
		}
		for (int i = 0; i < CELLS.length; i++) {
			if (!CELLS[i].name().equals(ConcentratorCellGeometry.NAMES[i])) {
				throw new IllegalStateException("cell " + i + " is " + CELLS[i].name()
						+ " here but " + ConcentratorCellGeometry.NAMES[i] + " in the generated"
						+ " geometry — regenerate with tools/gen_radiant_solar_panel_assets.py");
			}
		}
	}

	private final String serializedName;

	ConcentratorPart(String serializedName) {
		this.serializedName = serializedName;
	}

	private static VoxelShape[][] buildShapes() {
		VoxelShape[][] shapes = new VoxelShape[ConcentratorCellGeometry.BOXES.length][];
		for (int facing = 0; facing < shapes.length; facing++) {
			double[][] row = ConcentratorCellGeometry.BOXES[facing];
			shapes[facing] = new VoxelShape[row.length];
			for (int part = 0; part < row.length; part++) {
				double[] box = row[part];
				shapes[facing][part] = Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
			}
		}
		return shapes;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** Whether this block belongs to an assembled structure (i.e. it has neighbours to lose). */
	public boolean assembled() {
		return this != LOOSE;
	}

	/** Offset from the core in the canonical orientation; {@code null} for a loose section. */
	@Nullable
	public Vec3i canonicalOffset() {
		if (this == LOOSE) {
			return null;
		}
		int[] cell = ConcentratorCellGeometry.OFFSETS[ordinal() - CORE.ordinal()];
		return new Vec3i(cell[0], cell[1], cell[2]);
	}

	/**
	 * Offset from the core in world axes, for a structure whose core carries {@code facing}.
	 *
	 * <p>{@code +x} of the canonical frame is the core's right hand and {@code +z} is straight back
	 * from its face, so the whole rotation is two vanilla direction lookups and no arithmetic of our
	 * own — the same way {@code WindMillClearance} builds the cells in front of a mill, generalised
	 * from six hand-written positions to a table.
	 */
	public Vec3i worldOffset(Direction facing) {
		Vec3i canonical = canonicalOffset();
		if (canonical == null) {
			return Vec3i.ZERO;
		}
		Direction right = facing.getClockWise();
		Direction backward = facing.getOpposite();
		return new Vec3i(
				right.getStepX() * canonical.getX() + backward.getStepX() * canonical.getZ(),
				canonical.getY(),
				right.getStepZ() * canonical.getX() + backward.getStepZ() * canonical.getZ());
	}

	/**
	 * A loose section fills its block: the frame's bars run right to the boundary, so anything less
	 * would let a player stand inside the cage.
	 *
	 * <p>It is a full cube that must NOT occlude — the same shape as the mod's two glass blocks, and
	 * for the same reason: the middle is glass, and a section that occluded would cull away whatever
	 * stands behind it, including the six other sections of a half-built machine. That combination is
	 * what R-PHY-05 waives by name for glass, and this block is listed there with them.
	 */
	private static final VoxelShape LOOSE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	/** Shape of this cell for a structure facing {@code facing}; see {@link #LOOSE_SHAPE}. */
	public VoxelShape shape(Direction facing) {
		if (this == LOOSE) {
			return LOOSE_SHAPE;
		}
		return SHAPES[facing.get2DDataValue()][ordinal() - CORE.ordinal()];
	}

	/** Half the structure's width in blocks — the middle of the CANONICAL box, which is fixed. */
	public static final float CANONICAL_CENTRE = 1.0f;

	/**
	 * A point of the canonical structure, mapped into world space relative to the CORE's origin.
	 *
	 * <p>Both centres are involved and they are NOT the same one, which is the whole difficulty. The
	 * point arrives in canonical coordinates, where the structure always spans {@code 0..2} and its
	 * middle is {@link #CANONICAL_CENTRE}; it has to come out in world coordinates, where the
	 * structure lies on whichever side of the core its facing puts it and its middle is
	 * {@link #structureCentre}. So the turn subtracts the canonical middle and adds the world one.
	 * Using the world middle on both sides cancels only when the two are equal — that is, facing
	 * north — and puts the mirrors a block or two off the machine on the other three.
	 *
	 * @param facing which way the assembled structure faces
	 * @param x      canonical x in blocks, {@code 0..2}
	 * @param z      canonical z in blocks, {@code 0..2}
	 * @return {@code {x, z}} in blocks from the core's origin
	 */
	public static float[] canonicalToWorld(Direction facing, float x, float z) {
		float[] centre = structureCentre(facing);
		float dx = x - CANONICAL_CENTRE;
		float dz = z - CANONICAL_CENTRE;
		// Vanilla's blockstate `y` turns clockwise seen from above: (dx, dz) -> (-dz, dx) per quarter.
		int quarters = (modelYaw(facing) / 90) % 4;
		for (int i = 0; i < quarters; i++) {
			float nx = -dz;
			dz = dx;
			dx = nx;
		}
		return new float[] {centre[0] + dx, centre[1] + dz};
	}

	/**
	 * Middle of the assembled structure, measured from the CORE block's own origin, in blocks.
	 *
	 * <p>It is not a constant, and assuming it was is what sent the mirrors flying. The structure
	 * grows one step along the core's right hand and one step straight back from its face, so which
	 * side of the core it occupies — and therefore where its middle lies — changes with the facing:
	 * {@code (1, 1)} facing north, {@code (0, 1)} east, {@code (0, 0)} south, {@code (1, 0)} west.
	 * Rotating the wings about a hard-coded {@code (1, 1)} put them a block or two off the machine on
	 * three facings out of four, and looked exactly like broken geometry.
	 *
	 * @return {@code {x, z}} of the middle, in blocks from the core's origin
	 */
	public static float[] structureCentre(Direction facing) {
		Direction right = facing.getClockWise();
		Direction backward = facing.getOpposite();
		return new float[] {
				(right.getStepX() + backward.getStepX()) / 2.0f + 0.5f,
				(right.getStepZ() + backward.getStepZ()) / 2.0f + 0.5f};
	}

	/**
	 * The blockstate {@code y} rotation the structure is drawn with, in degrees.
	 *
	 * <p>Exposed here because the generated table is package-private and the wing renderer lives in
	 * the client package. Anything drawing the structure outside the baked models must turn by
	 * exactly this angle, or the wings and the collector they fold over would point different ways.
	 * Vanilla's {@code y} turns clockwise seen from above, so a {@code PoseStack} needs it negated.
	 */
	public static int modelYaw(Direction facing) {
		return ConcentratorCellGeometry.MODEL_YAW[facing.get2DDataValue()];
	}

	/** The cell sitting at {@code offset} in the canonical frame, or {@code null} if outside. */
	@Nullable
	public static ConcentratorPart atCanonical(int dx, int dy, int dz) {
		for (ConcentratorPart part : CELLS) {
			Vec3i cell = part.canonicalOffset();
			if (cell != null && cell.getX() == dx && cell.getY() == dy && cell.getZ() == dz) {
				return part;
			}
		}
		return null;
	}
}
