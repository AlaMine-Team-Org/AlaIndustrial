package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.core.structure.QuarryFrameScan;

/**
 * Where the parts of one formed quarry frame stand (MOD-950): what the controller paints, remembers and hands its
 * renderer. Taken from a {@link QuarryFrameScan.Result#formed() formed} scan; kept in the controller's data, so it
 * can unpaint the frame after a chunk reload and the client can draw the frame from the update tag alone.
 *
 * @param minX     the lower X of the frame's outer bounds
 * @param minZ     the lower Z of the frame's outer bounds
 * @param maxX     the upper X of the frame's outer bounds
 * @param maxZ     the upper Z of the frame's outer bounds
 * @param headY    the bottom head layer, the controller's; the bottom foundation layer is
 *                 {@link QuarryFrameScan#HEAD_HEIGHT} below
 * @param alongX   whether the paths run along X (the bridge then lies along Z)
 * @param bridgeAt the bridge row's coordinate along the paths
 * @param cartAt   the cart's coordinate across the paths
 */
public record QuarryFrameLayout(int minX, int minZ, int maxX, int maxZ, int headY, boolean alongX, int bridgeAt,
		int cartAt) {

	private static final int T = QuarryFrameScan.TOWER;

	/** The layout of a formed scan whose heads stand at {@code headY}. */
	public static QuarryFrameLayout of(QuarryFrameScan.Result formed, int headY) {
		return new QuarryFrameLayout(formed.minX(), formed.minZ(), formed.maxX(), formed.maxZ(), headY,
				formed.pathsAlongX(), formed.bridgeAt(), formed.cartAt());
	}

	/** The bottom foundation layer. */
	public int baseY() {
		return headY - QuarryFrameScan.HEAD_HEIGHT;
	}

	/** The four towers' north-west corners as {@code {x, z}}. */
	public int[][] towers() {
		return new int[][] {{minX, minZ}, {maxX - T + 1, minZ}, {minX, maxZ - T + 1}, {maxX - T + 1, maxZ - T + 1}};
	}

	/** The first and last cell along the paths between the towers. */
	public int pathLow() {
		return (alongX ? minX : minZ) + T;
	}

	public int pathHigh() {
		return (alongX ? maxX : maxZ) - T;
	}

	/** The first row across of the near and of the far path. */
	public int nearPath() {
		return alongX ? minZ : minX;
	}

	public int farPath() {
		return (alongX ? maxZ : maxX) - T + 1;
	}

	/**
	 * Every cell of the frame: the towers from foundation to head, both paths, the bridge row and the cart.
	 */
	public void forEachCell(CellVisitor cell) {
		for (int[] tower : towers()) {
			for (int y = baseY(); y < headY + QuarryFrameScan.HEAD_LAYERS; y++) {
				for (int dx = 0; dx < T; dx++) {
					for (int dz = 0; dz < T; dz++) {
						cell.visit(tower[0] + dx, y, tower[1] + dz);
					}
				}
			}
		}
		int top = headY + QuarryFrameScan.PATH_LAYERS - 1;
		for (int y = headY; y <= top; y++) {
			for (int row : new int[] {nearPath(), farPath()}) {
				for (int t = pathLow(); t <= pathHigh(); t++) {
					for (int across = row; across < row + T; across++) {
						cell.visit(alongX ? t : across, y, alongX ? across : t);
					}
				}
			}
		}
		for (int across = nearPath() + T; across < farPath(); across++) {
			cell.visit(alongX ? bridgeAt : across, top, alongX ? across : bridgeAt);
		}
		// The saddles: over each path and one cell into the pit, a layer above the bridge.
		for (int across = nearPath(); across < farPath() + T; across++) {
			if (across > nearPath() + T && across < farPath() - 1) {
				continue;
			}
			cell.visit(alongX ? bridgeAt : across, top + 1, alongX ? across : bridgeAt);
		}
		cell.visit(alongX ? bridgeAt : cartAt, top + 1, alongX ? cartAt : bridgeAt);
	}

	/** Receives one cell of {@link #forEachCell}. */
	@FunctionalInterface
	public interface CellVisitor {
		void visit(int x, int y, int z);
	}

	/** The layout as the ints the controller keeps. */
	public int[] toArray() {
		return new int[] {minX, minZ, maxX, maxZ, headY, alongX ? 1 : 0, bridgeAt, cartAt};
	}

	/** A layout read back from {@link #toArray}, or {@code null} for anything else (none kept, an old format). */
	public static QuarryFrameLayout fromArray(int[] data) {
		if (data == null || data.length != 8) {
			return null;
		}
		return new QuarryFrameLayout(data[0], data[1], data[2], data[3], data[4], data[5] != 0, data[6], data[7]);
	}
}
