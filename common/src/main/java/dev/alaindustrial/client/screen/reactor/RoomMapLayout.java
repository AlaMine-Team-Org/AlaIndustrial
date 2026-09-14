package dev.alaindustrial.client.screen.reactor;

/**
 * Where the «Room» tab's top-down map puts its cells (MOD-619).
 *
 * <p>A cell is one block column seen from above, north up and east right, counted in blocks from the
 * controller — the offsets the menu's channels carry. The map shows the shell's footprint when the scan has
 * measured it, the controller, and every problem cell; the cell size is the largest that fits all of them
 * into the map's box, up to {@link #MAX_CELL}, so a 5×5 shell is not drawn as a postage stamp and a 15×15 one
 * still fits.
 *
 * <p>Minecraft-free so the fitting can be tested: a map that clipped the far wall of the largest room, or
 * lost the controller off its edge, is a layout bug a screenshot shows only in the one state it happens to
 * photograph.
 *
 * @param minX   west-most cell shown, in blocks from the controller
 * @param minZ   north-most cell shown
 * @param cols   cells across
 * @param rows   cells down
 * @param cell   side of one cell, in GUI pixels
 * @param left   pixels from the map box's left edge to the first cell
 * @param top    pixels from the map box's top edge to the first cell
 */
public record RoomMapLayout(int minX, int minZ, int cols, int rows, int cell, int left, int top) {

	/** The largest cell drawn, in GUI pixels: a small room gets room to breathe, not a giant grid. */
	public static final int MAX_CELL = 12;

	/**
	 * Cells shown around the controller when no box was measured: enough to see which way the problem lies,
	 * not so few that one cell fills the map.
	 */
	public static final int UNMEASURED_SPAN = 5;

	/**
	 * Lays the map out.
	 *
	 * @param boxMeasured whether the shell's footprint is known
	 * @param boxWest     interior's west edge, in blocks from the controller (ignored when not measured)
	 * @param boxNorth    interior's north edge
	 * @param sizeX       interior extent east-west, in blocks
	 * @param sizeZ       interior extent north-south
	 * @param problems    the problem cells as {@code x, z} pairs, east and south of the controller
	 * @param width       map box width, in GUI pixels
	 * @param height      map box height
	 */
	public static RoomMapLayout of(boolean boxMeasured, int boxWest, int boxNorth, int sizeX, int sizeZ,
			int[] problems, int width, int height) {
		int minX = 0;
		int maxX = 0;
		int minZ = 0;
		int maxZ = 0;
		if (boxMeasured) {
			minX = Math.min(minX, boxWest - 1);
			maxX = Math.max(maxX, boxWest + sizeX);
			minZ = Math.min(minZ, boxNorth - 1);
			maxZ = Math.max(maxZ, boxNorth + sizeZ);
		}
		for (int i = 0; i + 1 < problems.length; i += 2) {
			minX = Math.min(minX, problems[i]);
			maxX = Math.max(maxX, problems[i]);
			minZ = Math.min(minZ, problems[i + 1]);
			maxZ = Math.max(maxZ, problems[i + 1]);
		}
		if (!boxMeasured) {
			// Centre the controller in a small field, grown only if a problem lies outside it.
			int half = UNMEASURED_SPAN / 2;
			minX = Math.min(minX, -half);
			maxX = Math.max(maxX, half);
			minZ = Math.min(minZ, -half);
			maxZ = Math.max(maxZ, half);
		}
		int cols = maxX - minX + 1;
		int rows = maxZ - minZ + 1;
		int cell = Math.max(1, Math.min(MAX_CELL, Math.min(width / cols, height / rows)));
		return new RoomMapLayout(minX, minZ, cols, rows, cell, (width - cols * cell) / 2, (height - rows * cell) / 2);
	}

	/** Pixels from the map box's left edge to the cell {@code x} blocks east of the controller. */
	public int cellLeft(int x) {
		return left + (x - minX) * cell;
	}

	/** Pixels from the map box's top edge to the cell {@code z} blocks south of the controller. */
	public int cellTop(int z) {
		return top + (z - minZ) * cell;
	}

	/** Whether a cell is part of the shell's footprint: the ring one block outside the interior. */
	public static boolean isShell(int x, int z, int boxWest, int boxNorth, int sizeX, int sizeZ) {
		boolean insideRing = x >= boxWest - 1 && x <= boxWest + sizeX && z >= boxNorth - 1 && z <= boxNorth + sizeZ;
		return insideRing && !isInterior(x, z, boxWest, boxNorth, sizeX, sizeZ);
	}

	/** Whether a cell lies over the room's interior. */
	public static boolean isInterior(int x, int z, int boxWest, int boxNorth, int sizeX, int sizeZ) {
		return x >= boxWest && x < boxWest + sizeX && z >= boxNorth && z < boxNorth + sizeZ;
	}
}
