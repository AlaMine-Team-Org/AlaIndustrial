package dev.alaindustrial.client.render;

/**
 * The incubator's front-panel equaliser — GENERATED, do not edit by hand.
 *
 * <p>Source: {@code tools/model_sources/incubator_concept/base.json}, regenerate with
 * {@code python tools/gen_incubator_assets.py}. These bars are deliberately ABSENT from the
 * block model: a cube in a JSON model has a fixed height, and the panel has to move while the
 * machine runs, so {@link IncubatorBlockEntityRenderer} draws them instead. Keeping a copy in
 * the model would draw every bar twice, fighting for the same pixels.
 *
 * <p>Coordinates are block pixels in the model AS THE DESIGNER AUTHORED IT — front panel on the
 * SOUTH face. {@link #FRONT_FACE_YAW} is the half turn the blockstate adds to put that panel on
 * the block's {@code FACING} side, and the renderer must apply the same turn.
 */
final class IncubatorScreenGeometry {

	private IncubatorScreenGeometry() {
	}

	/** Extra blockstate rotation, because the panel is authored on south and the mod faces north. */
	static final float FRONT_FACE_YAW = 180.0F;

	/** Every bar stands on this line. */
	static final float BASE_Y = 8F;
	/** The well's ceiling: a bar drawn past this pokes through the panel. */
	static final float WELL_TOP = 10.4F;
	/** Where the bars sit in depth, just proud of the well's floor. */
	static final float FRONT_Z = 15.7F;

	/** Palette texel the bars take their colour from, as a fraction of the sprite. */
	static final float INDICATOR_U = 0.632812F;
	static final float INDICATOR_V = 0.007812F;

	/** One row per bar: {@code {x0, x1, idleTop}} — the height the designer drew it at rest. */
	static final float[][] BARS = {
			{5.2F, 5.7F, 8.6F},
			{6.2F, 6.7F, 9.3F},
			{7.2F, 7.7F, 9.8F},
			{8.2F, 8.7F, 9.1F},
			{9.2F, 9.7F, 8.8F},
	};
}
