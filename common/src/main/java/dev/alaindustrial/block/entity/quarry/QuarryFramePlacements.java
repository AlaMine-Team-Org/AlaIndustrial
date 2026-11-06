package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.core.structure.QuarryFrameScan;
import java.util.ArrayList;
import java.util.List;

/**
 * Where each part of the designer's model stands on a formed quarry frame (MOD-950) — one list for the renderer that
 * draws them and for {@link QuarryFrameShapes}, which cuts the same parts into collision, so what the player sees and
 * what the player bumps into cannot drift apart.
 *
 * <p>A part is canonical in its own frame (the generator's, {@code tools/gen_quarry_assets.py}); a placement puts the
 * part's {@link Part#localX local centre} on a world point and turns it about Y by {@code yaw}: a tower so its head's
 * beam reaches out along its path towards the tower across, a path block and the cart with the path, a saddle so the
 * bridge leaves it towards the other path. A right-handed turn of +90 about Y takes +Z to +X.
 */
public final class QuarryFramePlacements {

	private static final int T = QuarryFrameScan.TOWER;
	/** Path blocks the head's beam already covers at each end of a path, in blocks. */
	private static final int UNDER_THE_BEAM = 2;
	/** How far a saddle reaches from its path's centre line towards the other path, in blocks (32 px). */
	private static final float SADDLE_REACH = 2.0F;

	private QuarryFramePlacements() {
	}

	/** The parts of the model, with their local centre in their own frame (pixels). */
	public enum Part {
		TOWER(24.0F, 24.0F),
		PATH(24.0F, 8.0F),
		SADDLE(0.0F, 8.0F),
		BRIDGE(8.0F, 8.0F),
		CART(0.0F, 0.0F),
		/**
		 * Where the drill hangs from the cart (the cart's frame): its mount, pipe and head are drawn here, lowered and
		 * turning (MOD-952), and only while a drill bit is in the controller's slot.
		 */
		DRILL(0.0F, 0.0F);

		private final float localX;
		private final float localZ;

		Part(float localX, float localZ) {
			this.localX = localX;
			this.localZ = localZ;
		}

		public float localX() {
			return localX;
		}

		public float localZ() {
			return localZ;
		}
	}

	/**
	 * One part where it stands.
	 *
	 * @param x      the world X its local centre lands on, in blocks
	 * @param z      the world Z its local centre lands on, in blocks
	 * @param yaw    the turn about Y, degrees
	 * @param lightY the height of the cell whose light it takes: the open air just above the blocks it hides, which
	 *               holds the light outside whether or not the client has relit the frame's own cells yet
	 */
	public record Placement(Part part, float x, float z, float yaw, int lightY) {
	}

	/**
	 * Every part of the frame with the bridge and the cart where the player set them. The model's own y is kept: 0 is
	 * the bottom of the foundations.
	 */
	public static List<Placement> of(QuarryFrameLayout layout) {
		return of(layout, layout.bridgeAt() + 0.5F, layout.cartAt() + 0.5F);
	}

	/**
	 * Every part of the frame with the bridge at {@code bridge} along the paths and the cart at {@code cart} along the
	 * bridge, both in world blocks (MOD-952: they move as the quarry digs).
	 */
	public static List<Placement> of(QuarryFrameLayout layout, float bridge, float cart) {
		List<Placement> out = new ArrayList<>();
		boolean alongX = layout.alongX();
		int aboveHeads = layout.headY() + QuarryFrameScan.HEAD_LAYERS;
		int abovePaths = layout.headY() + QuarryFrameScan.PATH_LAYERS;
		for (int[] tower : layout.towers()) {
			boolean lowSide = alongX ? tower[0] == layout.minX() : tower[1] == layout.minZ();
			float yaw = alongX ? (lowSide ? 90.0F : 270.0F) : (lowSide ? 0.0F : 180.0F);
			out.add(new Placement(Part.TOWER, tower[0] + T / 2.0F, tower[1] + T / 2.0F, yaw, aboveHeads));
		}
		for (int row : new int[] {layout.nearPath(), layout.farPath()}) {
			for (int t = layout.pathLow() + UNDER_THE_BEAM; t <= layout.pathHigh() - UNDER_THE_BEAM; t++) {
				out.add(at(Part.PATH, alongX, t + 0.5F, row + T / 2.0F, alongX ? 90.0F : 0.0F, abovePaths));
			}
		}
		float nearCentre = layout.nearPath() + T / 2.0F;
		float farCentre = layout.farPath() + T / 2.0F;
		out.add(at(Part.SADDLE, alongX, bridge, nearCentre, alongX ? 90.0F : 180.0F, abovePaths));
		out.add(at(Part.SADDLE, alongX, bridge, farCentre, alongX ? 270.0F : 0.0F, abovePaths));
		// Bridge blocks from where the near saddle ends to where the far one begins, so no two overlap.
		for (float across = nearCentre + SADDLE_REACH + 0.5F; across < farCentre - SADDLE_REACH; across += 1.0F) {
			out.add(at(Part.BRIDGE, alongX, bridge, across, alongX ? 90.0F : 0.0F, abovePaths));
		}
		out.add(at(Part.CART, alongX, bridge, cart, alongX ? 90.0F : 0.0F, aboveHeads));
		out.add(at(Part.DRILL, alongX, bridge, cart, alongX ? 90.0F : 0.0F, aboveHeads));
		return out;
	}

	/** The drill head's lowest point at rest, above the bottom of the foundations, in blocks. */
	public static float drillRestingTip() {
		return QuarryFrameBoxes.DRILL_TIP / 16.0F;
	}

	/** How tall the drill's head is, cage included, in blocks. */
	public static float drillHeadHeight() {
		return (QuarryFrameBoxes.DRILL_CAGE_TOP - QuarryFrameBoxes.DRILL_TIP) / 16.0F;
	}

	/** A placement given along and across the paths rather than in X and Z. */
	private static Placement at(Part part, boolean alongX, float along, float across, float yaw, int lightY) {
		return new Placement(part, alongX ? along : across, alongX ? across : along, yaw, lightY);
	}
}
