package dev.alaindustrial.block;

import java.util.List;

/**
 * Geometry of the assembled Mirror Concentrator — GENERATED, do not edit by hand.
 *
 * <p>Source: {@code tools/model_sources/solar_concentrator_concept/solar_concentrator_folding.bbmodel},
 * regenerate with {@code python tools/gen_radiant_solar_panel_assets.py}. The structure is the
 * one-block model at double scale, cut into eight cells; each row here is one cell in the
 * CANONICAL orientation (the core facing north). Rotating the structure is the caller's job.
 *
 * <p>A cell's box is measured from every cube that INTERSECTS the cell, not from the cubes
 * assigned to it for drawing: the chassis slab spans the whole structure and is drawn by a
 * single cell, so an ownership-based box would declare its three neighbours hollow and let
 * a player walk through the housing.
 */
final class ConcentratorCellGeometry {

	private ConcentratorCellGeometry() {
	}

	/** Cell offsets from the core, in the canonical orientation: {@code {dx, dy, dz}}. */
	static final int[][] OFFSETS = {
			{0, 0, 0},  // CORE
			{1, 0, 0},  // RIGHT
			{0, 0, 1},  // BACK
			{1, 0, 1},  // BACK_RIGHT
			{0, 1, 0},  // TOP
			{1, 1, 0},  // TOP_RIGHT
			{0, 1, 1},  // TOP_BACK
			{1, 1, 1},  // TOP_BACK_RIGHT
	};

	/**
	 * Per-cell shape in block pixels, {@code [horizontal facing][part]} ->
	 * {@code {x0, y0, z0, x1, y1, z1}}. The first index is vanilla's
	 * {@code Direction.get2DDataValue()}, so a caller indexes it with the facing it
	 * already has and never rotates a box itself — the rotation is baked here, by the
	 * same table that writes the blockstate, so shape and picture cannot disagree.
	 */
	static final double[][][] BOXES = {
		{  // south
			{0, 0, 0, 12, 16, 12},  // CORE
			{4, 0, 0, 16, 16, 12},  // RIGHT
			{0, 0, 4, 12, 16, 16},  // BACK
			{4, 0, 4, 16, 16, 16},  // BACK_RIGHT
			{0, 0, 0, 11, 5.6, 14},  // TOP
			{5, 0, 0, 16, 5.6, 14},  // TOP_RIGHT
			{0, 0, 2, 11, 5.6, 16},  // TOP_BACK
			{5, 0, 2, 16, 5.6, 16},  // TOP_BACK_RIGHT
		},
		{  // west
			{4, 0, 0, 16, 16, 12},  // CORE
			{4, 0, 4, 16, 16, 16},  // RIGHT
			{0, 0, 0, 12, 16, 12},  // BACK
			{0, 0, 4, 12, 16, 16},  // BACK_RIGHT
			{2, 0, 0, 16, 5.6, 11},  // TOP
			{2, 0, 5, 16, 5.6, 16},  // TOP_RIGHT
			{0, 0, 0, 14, 5.6, 11},  // TOP_BACK
			{0, 0, 5, 14, 5.6, 16},  // TOP_BACK_RIGHT
		},
		{  // north
			{4, 0, 4, 16, 16, 16},  // CORE
			{0, 0, 4, 12, 16, 16},  // RIGHT
			{4, 0, 0, 16, 16, 12},  // BACK
			{0, 0, 0, 12, 16, 12},  // BACK_RIGHT
			{5, 0, 2, 16, 5.6, 16},  // TOP
			{0, 0, 2, 11, 5.6, 16},  // TOP_RIGHT
			{5, 0, 0, 16, 5.6, 14},  // TOP_BACK
			{0, 0, 0, 11, 5.6, 14},  // TOP_BACK_RIGHT
		},
		{  // east
			{0, 0, 4, 12, 16, 16},  // CORE
			{0, 0, 0, 12, 16, 12},  // RIGHT
			{4, 0, 4, 16, 16, 16},  // BACK
			{4, 0, 0, 16, 16, 12},  // BACK_RIGHT
			{0, 0, 5, 14, 5.6, 16},  // TOP
			{0, 0, 0, 14, 5.6, 11},  // TOP_RIGHT
			{2, 0, 5, 16, 5.6, 16},  // TOP_BACK
			{2, 0, 0, 16, 5.6, 11},  // TOP_BACK_RIGHT
		},
	};

	/**
	 * How a cable's dropped arm continues past the cell edge into a bottom-tier cell, in block
	 * pixels (MOD-609): bands stacked from the sleeve's bottom up, each carried to the surface
	 * in front of it — the base step, the housing — and not a hair further. Measured from the
	 * model. One depth for the whole sleeve would have pushed its sides through the step, and
	 * two faces in one plane facing the same way flicker; the generator refuses any such pair
	 * that can be seen.
	 */
	static final List<CableArmReach.Band> CABLE_ARM_BANDS = List.of(
			new CableArmReach.Band(2f, 3f, 5f),
			new CableArmReach.Band(3f, 8f, 6f));

	/**
	 * Blockstate {@code y} rotation of the structure, indexed by
	 * {@code Direction.get2DDataValue()}. Anything drawing the structure outside the
	 * baked models — the wing renderer — must turn by the SAME angle the blockstate
	 * turns the static pieces by, and this is that one number, written by the one
	 * table that also writes the blockstate. Vanilla's {@code y} turns clockwise seen
	 * from above, so a {@code PoseStack} needs it negated.
	 */
	static final int[] MODEL_YAW = {180, 270, 0, 90};

	/** Names of the parts, in the order of {@link #OFFSETS}. */
	static final String[] NAMES = {"CORE", "RIGHT", "BACK", "BACK_RIGHT", "TOP", "TOP_RIGHT", "TOP_BACK", "TOP_BACK_RIGHT"};
}
