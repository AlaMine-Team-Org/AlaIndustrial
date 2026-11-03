package dev.alaindustrial.block;

/**
 * The reactor antenna boom's boxes (MOD-941), in model pixels of the mast's own cell with the antenna facing north
 * (the boom points north, the antenna is the cell to the south). Kept free of Minecraft types so an L1 test can
 * hold it against the installed model ({@code models/block/reactor_antenna_mast.json}), which
 * {@code tools/model_sources/reactor_antenna_concepts/install_chosen.py} writes from the chosen design.
 */
public final class ReactorAntennaGeometry {

	private ReactorAntennaGeometry() {
	}

	/**
	 * {@code {x0, y0, z0, x1, y1, z1}} of each element: mount plate, boom, feed box, driven element, two directors —
	 * the mast's own drawing ({@code reactor_antenna_mast.json}). The cable's sleeve and arms are {@link #SLEEVE_BOX}
	 * and {@link #ARM_BOX}, drawn only while a cable joins.
	 */
	public static final double[][] MAST_BOXES = {
		{5, 11, 15, 11, 15, 16},
		{7.5, 12.5, 2, 8.5, 13.5, 15},
		{7, 12, 11, 9, 14, 13},
		{2, 12.25, 11.5, 14, 13.75, 12.5},
		{3, 12.25, 7.5, 13, 13.75, 8.5},
		{4, 12.25, 3.5, 12, 13.75, 4.5},
	};

	/**
	 * The copper sleeve that takes a joining cable into the port in the antenna's face: from the centre of the cell to
	 * the wall (z = 16), a little thicker than the cable (owner's playtest 2026-11-03). North-facing model pixels.
	 */
	public static final double[] SLEEVE_BOX = {4.5, 4.5, 4.5, 11.5, 11.5, 16};

	/** One arm of the sleeve toward a cable, drawn pointing north from the centre and turned like a facing. */
	public static final double[] ARM_BOX = {4.75, 4.75, 0, 11.25, 11.25, 4.5};

	private static final String[] DIRECTIONS = {"DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"};

	/**
	 * Whether a cable may join the boom's cell through {@code face} for an antenna facing {@code facing}: every face
	 * but the antenna's own and the one the model's top (where the Yagi elements stand) turns to — up for a wall
	 * antenna, south for one on a roof, north for one in a ceiling. A sleeve from there would run into the elements.
	 */
	public static boolean isCableFace(Enum<?> facing, Enum<?> face) {
		return !face.name().equals(opposite(facing.name())) && !face.name().equals(modelTop(facing.name()));
	}

	/** Where the model's top lands once the blockstate turns it to {@code facing}. */
	static String modelTop(String facing) {
		return switch (facing) {
			case "UP" -> "SOUTH";
			case "DOWN" -> "NORTH";
			default -> "UP";
		};
	}

	private static String opposite(String direction) {
		for (int i = 0; i < DIRECTIONS.length; i++) {
			if (DIRECTIONS[i].equals(direction)) {
				return DIRECTIONS[i ^ 1];
			}
		}
		throw new IllegalArgumentException("not a direction: " + direction);
	}

	/**
	 * A point of the north-facing model turned to {@code facing}, as the blockstate turns the model: y 90/180/270
	 * for east/south/west, x 270 for up and x 90 for down. Coordinates are model pixels, 0..16.
	 */
	public static double[] rotate(Enum<?> facing, double x, double y, double z) {
		return switch (facing.name()) {
			case "EAST" -> new double[] {16 - z, y, x};
			case "SOUTH" -> new double[] {16 - x, y, 16 - z};
			case "WEST" -> new double[] {z, y, 16 - x};
			case "UP" -> new double[] {x, 16 - z, y};
			case "DOWN" -> new double[] {x, z, 16 - y};
			default -> new double[] {x, y, z};
		};
	}
}
