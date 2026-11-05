package dev.alaindustrial.core.quarry;

/**
 * The order a quarry works its pit in (MOD-951, stage B of MOD-637; spec {@code docs/blocks/mining/quarry.md},
 * "working order"): layer after layer from the foundations' level down, and in each layer a snake — the first
 * row along the bridge from the start corner, the next one back, and so on. The next layer starts where the last
 * one ended, so the pit is never crossed empty: an odd layer runs the even layer's path backwards.
 *
 * <p>Minecraft-free, so every rule is an L1 target. A cell is named by its <em>step</em> — the layer and the index
 * inside it — and the controller keeps only that pair; the world position is worked out from it here.
 *
 * <p><b>Axes.</b> {@code u} runs along the bridge (the cart's way), {@code t} along the paths (the bridge's way).
 * The start corner is the pit's corner nearest the controller's tower; {@code (uSign, tSign)} point from there
 * into the pit.
 *
 * @param x0        the start corner's X
 * @param z0        the start corner's Z
 * @param topY      the first layer: the bottom foundations' level
 * @param alongX    whether the paths run along X, so the bridge — and {@code u} — run along Z
 * @param spanU     cells along the bridge
 * @param spanT     cells along the paths
 * @param uSign     +1 or −1: the direction of {@code u} in the world
 * @param tSign     +1 or −1: the direction of {@code t} in the world
 * @param layers    how many layers the pit may have: the tier's depth, or until {@code bottomY}
 * @param bottomY   the lowest layer the world allows, inclusive
 */
public record QuarryDigOrder(int x0, int z0, int topY, boolean alongX, int spanU, int spanT, int uSign, int tSign,
		int layers, int bottomY) {

	public QuarryDigOrder {
		if (spanU < 1 || spanT < 1) {
			throw new IllegalArgumentException("a pit needs at least one cell, got " + spanU + " x " + spanT);
		}
		if (Math.abs(uSign) != 1 || Math.abs(tSign) != 1) {
			throw new IllegalArgumentException("signs are +1 or -1, got " + uSign + ", " + tSign);
		}
	}

	/** A cell of the pit and where it sits on the bridge ({@code u}) and the paths ({@code t}). */
	public record Cell(int x, int y, int z, int u, int t) {
	}

	/**
	 * The order for the pit between the towers of a frame, from its outer bounds: the pit is the frame's inside,
	 * one tower's width in from every side, and the start corner is the one at the controller's tower.
	 *
	 * @param minX        the frame's outer bounds, as the scan reports them
	 * @param tower       a tower's edge in cells
	 * @param controllerX the controller's X, to tell which corner is the start
	 * @param controllerZ the controller's Z
	 * @param depth       the tier's depth in layers; 0 or less means "down to {@code bottomY}"
	 */
	public static QuarryDigOrder forFrame(int minX, int minZ, int maxX, int maxZ, int tower, boolean alongX,
			int controllerX, int controllerZ, int topY, int depth, int bottomY) {
		int lowX = minX + tower;
		int highX = maxX - tower;
		int lowZ = minZ + tower;
		int highZ = maxZ - tower;
		boolean westStart = controllerX - minX < maxX - controllerX;
		boolean northStart = controllerZ - minZ < maxZ - controllerZ;
		int startX = westStart ? lowX : highX;
		int startZ = northStart ? lowZ : highZ;
		int signX = westStart ? 1 : -1;
		int signZ = northStart ? 1 : -1;
		int spanX = highX - lowX + 1;
		int spanZ = highZ - lowZ + 1;
		int reachable = topY - bottomY + 1;
		int layers = depth > 0 ? Math.min(depth, reachable) : reachable;
		return alongX
				? new QuarryDigOrder(startX, startZ, topY, true, spanZ, spanX, signZ, signX, layers, bottomY)
				: new QuarryDigOrder(startX, startZ, topY, false, spanX, spanZ, signX, signZ, layers, bottomY);
	}

	/** Cells in one layer. */
	public int perLayer() {
		return spanU * spanT;
	}

	/** Whether {@code (layer, index)} lies past the last cell of the pit. */
	public boolean done(int layer, int index) {
		return layer >= layers || layer < 0 || index < 0 || index >= perLayer();
	}

	/** The cell at step {@code index} of {@code layer}; the step must not be {@link #done done}. */
	public Cell cell(int layer, int index) {
		if (done(layer, index)) {
			throw new IllegalArgumentException("no cell at layer " + layer + ", step " + index);
		}
		int walked = layer % 2 == 0 ? index : perLayer() - 1 - index;
		int row = walked / spanU;
		int along = walked % spanU;
		int u = row % 2 == 0 ? along : spanU - 1 - along;
		int t = row;
		int du = u * uSign;
		int dt = t * tSign;
		int x = alongX ? x0 + dt : x0 + du;
		int z = alongX ? z0 + du : z0 + dt;
		return new Cell(x, topY - layer, z, u, t);
	}

	/**
	 * The step after {@code (layer, index)} as {@code {layer, index}}; past the last cell it is
	 * {@code {layers, 0}}, which {@link #done} recognises.
	 */
	public int[] next(int layer, int index) {
		if (index + 1 < perLayer()) {
			return new int[] {layer, index + 1};
		}
		return new int[] {layer + 1, 0};
	}

	/** How far along its layer step {@code index} is, in per mille, for the screen. */
	public int layerPermille(int index) {
		return (int) Math.min(1000L, 1000L * Math.max(0, index) / perLayer());
	}
}
