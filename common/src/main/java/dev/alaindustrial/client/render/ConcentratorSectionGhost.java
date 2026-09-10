package dev.alaindustrial.client.render;

/**
 * Boxes of a Concentrator Section, for the assembly schematic — GENERATED, do not edit.
 *
 * <p>Source: {@code tools/gen_concentrator_section_assets.py}, the same function that writes
 * the block's model. The schematic is drawn on the gizmo path, which knows nothing about
 * block models, so the shape has to reach it as numbers; taking them from the model's own
 * builder is what stops the ghost promising a block different from the one that lands.
 *
 * <p>Coordinates are BLOCK FRACTIONS (0..1), not model pixels: every caller here works in
 * world space, and dividing once at generation costs nothing at runtime.
 */
final class ConcentratorSectionGhost {

	private ConcentratorSectionGhost() {
	}

	/** The metal cage: eight corners and twelve edge bars. */
	static final double[][] FRAME = {
			{0, 0, 0, 0.125, 0.125, 0.125},
			{0, 0, 0.875, 0.125, 0.125, 1},
			{0, 0.875, 0, 0.125, 1, 0.125},
			{0, 0.875, 0.875, 0.125, 1, 1},
			{0.875, 0, 0, 1, 0.125, 0.125},
			{0.875, 0, 0.875, 1, 0.125, 1},
			{0.875, 0.875, 0, 1, 1, 0.125},
			{0.875, 0.875, 0.875, 1, 1, 1},
			{0.125, 0, 0, 0.875, 0.125, 0.125},
			{0.125, 0, 0.875, 0.875, 0.125, 1},
			{0.125, 0.875, 0, 0.875, 1, 0.125},
			{0.125, 0.875, 0.875, 0.875, 1, 1},
			{0, 0.125, 0, 0.125, 0.875, 0.125},
			{0, 0.125, 0.875, 0.125, 0.875, 1},
			{0.875, 0.125, 0, 1, 0.875, 0.125},
			{0.875, 0.125, 0.875, 1, 0.875, 1},
			{0, 0, 0.125, 0.125, 0.125, 0.875},
			{0, 0.875, 0.125, 0.125, 1, 0.875},
			{0.875, 0, 0.125, 1, 0.125, 0.875},
			{0.875, 0.875, 0.125, 1, 1, 0.875},
	};

	/** The glass core, drawn fainter than the cage. */
	static final double[] GLASS = {0.0625, 0.0625, 0.0625, 0.9375, 0.9375, 0.9375};
}
