package dev.alaindustrial.block;

/**
 * Silhouette of the incubator's glass chamber — GENERATED, do not edit by hand.
 *
 * <p>Source: {@code tools/model_sources/incubator_concept/dome.json}, regenerate with
 * {@code python tools/gen_incubator_assets.py}. The chamber steps inward four times on its
 * way up, and the straight {@code 1..15} box it used to carry left an invisible corner above
 * the lid for the player to walk into.
 *
 * <p>Rows are {@code {x0, y0, z0, x1, y1, z1}} in block pixels, measured off the model in two
 * pixel bands and merged where neighbouring bands agree.
 */
final class IncubatorDomeGeometry {

	private IncubatorDomeGeometry() {
	}

	static final double[][] STEPS = {
			{1, 0, 1, 15, 2, 15},
			{1.55, 2, 1.55, 14.45, 10, 14.45},
			{2, 10, 2, 14, 12, 14},
			{3, 12, 3, 13, 14, 13},
			{5, 14, 5, 11, 16, 11},
	};
}
