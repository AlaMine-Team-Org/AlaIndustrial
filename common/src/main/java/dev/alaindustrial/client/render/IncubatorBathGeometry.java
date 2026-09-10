package dev.alaindustrial.client.render;

/**
 * The incubator's nutrient bath, as a column of water — GENERATED, do not edit by hand.
 *
 * <p>Source: {@code tools/model_sources/incubator_concept/dome.json}, regenerate with
 * {@code python tools/gen_incubator_assets.py}. Cut from the SAME silhouette the chamber's
 * collision uses, so the water narrows with the glass shoulders instead of standing as one box
 * inside a stepped room — which left it empty under the lid and short of the floor.
 *
 * <p>Rows are {@code {x0, z0, x1, z1, y0, y1}} in the DOME's own pixels; the dome sits one block
 * above the block entity that draws it. Sides are sunk {@code 0.6 px} into each step, which is where
 * the corner posts stand — they, not the thin wall panes, are what bounds the chamber. The
 * bottom step borrows the barrel's footprint because the chamber has no floor plate at all:
 * under the glass there is only a rim around the edge.
 */
final class IncubatorBathGeometry {

	private IncubatorBathGeometry() {
	}

	/** The water stands on the machine's top plate — the chamber's real floor. */
	static final float FLOOR = 0F;
	/** A full tank reaches here — under the lid's metal cap, where water would not be seen. */
	static final float CEILING = 14F;

	static final float[][] STEPS = {
			{2.15F, 2.15F, 13.85F, 13.85F, 0F, 10F},
			{2.6F, 2.6F, 13.4F, 13.4F, 10F, 12F},
			{3.6F, 3.6F, 12.4F, 12.4F, 12F, 14F},
	};
}
