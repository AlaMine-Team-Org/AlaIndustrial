package dev.alaindustrial.client.render;

import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Primitive world-space geometry builders for {@link NetworkOverlayRenderer}: beam hops (a thin
 * bright core inside a faint sheath, each a square prism of side walls), comet sparks and solid
 * axis-aligned cubes (joint pads + endpoint markers).
 *
 * <p>Extracted from {@code NetworkOverlayRenderer} (540 lines) so that file is the
 * payload + per-frame orchestration + node-anchoring logic, and this file is purely "given points
 * and a colour, emit quads into the gizmo collector". No state — every method is a static builder.
 *
 * <p>Package-private — internal to the network-overlay renderer, not a general geometry API.
 */
final class OverlayGeometry {
	private OverlayGeometry() {
	}

	// Geometry sizing — world-space (metres of a block), not screen pixels, so it stays readable at
	// normal play distance rather than looking reasonable only up close.
	/** Half-thickness of the beam's bright core — a thin wire of light down the middle of the cable. */
	static final float CORE_HALF = 0.035f;
	/** Half-thickness of the soft sheath around the core — the "glow", drawn much fainter than the core. */
	static final float SHEATH_HALF = 0.11f;
	/** How much of the configured opacity the sheath keeps: a veil around the core, not a second tube. */
	static final double SHEATH_OPACITY = 0.35;
	/** Half-size of the core cube at a turn/branch/dead end — covers the open ends of the hops meeting there. */
	static final float JOINT_CORE_HALF = 0.055f;
	/** Half-size of the sheath cube at a turn/branch/dead end — covers the sheath hops meeting there. */
	static final float JOINT_SHEATH_HALF = 0.13f;
	/** Half-size of the joint cube at producer/consumer nodes — bigger than {@link #JOINT_SHEATH_HALF} so the
	 * network's "socket" reads as deliberately larger than a mid-network joint. */
	static final float ENDPOINT_JOINT_HALF_SIZE = 0.15f;

	/** Flow sparks per edge and how many edge-lengths they cross per second (edges are always 1 block). */
	static final int FLOW_DOTS_PER_EDGE = 1;
	static final double FLOW_SPEED_EDGES_PER_SECOND = 1.0;
	/** Half-size of a spark's head, and how far apart and how much smaller its tail boxes are. */
	static final double COMET_HEAD_HALF = 0.06;
	static final double COMET_TAIL_STEP = 0.07;
	static final int COMET_TAIL = 3;
	/** Longest wall quad of a beam hop, see {@link #addWalls}. */
	static final double WALL_PIECE = 0.125;

	/** Unit vectors for axis-aligned edge directions, indexed by dominant axis (0=X, 1=Y, 2=Z). */
	static final Vec3[] AXIS_UNIT = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
	/** Cross-section basis for a beam walking along {@link #AXIS_UNIT}[axis] — two unit vectors
	 * perpendicular to it and to each other. */
	static final Vec3[] SIDE_A_UNIT = {new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(-1, 0, 0)};
	/** {@code AXIS_UNIT[axis].cross(SIDE_A_UNIT[axis])}, precomputed alongside {@link #SIDE_A_UNIT}. */
	static final Vec3[] SIDE_B_UNIT = {new Vec3(0, -1, 0), new Vec3(-1, 0, 0), new Vec3(0, -1, 0)};

	/**
	 * One hop of the beam between two node anchors: the four side walls of a square prism of
	 * {@code halfThickness}, with no end caps. The hops of a straight line meet wall to wall, so a long
	 * run reads as one seamless beam — a cap at every block would show as a ring through a translucent
	 * trace (the "holey" look the first 26.3 frames had). Where hops meet at an angle or end, the node's
	 * joint cube covers the open ends; {@code retractFrom}/{@code retractTo} pull an end back to that
	 * cube's face so two translucent layers do not stack inside it.
	 *
	 * <p>The cross-section is chosen by the dominant axis and the hop is walked in that axis's positive
	 * direction, so every hop along one axis has bit-identical walls whichever way it was handed in.
	 */
	static void addBeamHop(GizmoPrimitives gizmos, Vec3 rawFrom, Vec3 rawTo, float halfThickness,
			double retractFrom, double retractTo, int color) {
		Vec3 delta = rawTo.subtract(rawFrom);
		int axis = dominantAxis(delta);
		if (axis < 0) {
			return;
		}
		boolean negative = component(delta, axis) < 0;
		Vec3 from = negative ? rawTo : rawFrom;
		Vec3 to = negative ? rawFrom : rawTo;
		double retractLow = negative ? retractTo : retractFrom;
		double retractHigh = negative ? retractFrom : retractTo;
		Vec3 unit = to.subtract(from).normalize();
		from = from.add(unit.scale(retractLow));
		to = to.subtract(unit.scale(retractHigh));
		if (to.subtract(from).dot(unit) <= 1.0e-6) {
			return;
		}
		Vec3 a = SIDE_A_UNIT[axis].scale(halfThickness);
		Vec3 b = SIDE_B_UNIT[axis].scale(halfThickness);
		Vec3 c1 = a.scale(-1).subtract(b);
		Vec3 c2 = a.subtract(b);
		Vec3 c3 = a.add(b);
		Vec3 c4 = b.subtract(a);
		addWalls(gizmos, from, to, c1, c2, c3, c4, color);
	}

	/**
	 * The walls between {@code from} and {@code to}, cut into pieces no longer than {@link #WALL_PIECE}.
	 * The gizmo pass orders quads by distance rather than testing depth per pixel, so one block-long wall
	 * quad could be ordered in front of a small spark it runs through and hide it, differently from every
	 * angle (MOD-665); short pieces sort next to the spark they touch.
	 */
	private static void addWalls(GizmoPrimitives gizmos, Vec3 from, Vec3 to, Vec3 c1, Vec3 c2, Vec3 c3, Vec3 c4,
			int color) {
		double length = to.subtract(from).length();
		int pieces = Math.max(1, (int) Math.ceil(length / WALL_PIECE));
		Vec3 step = to.subtract(from).scale(1.0 / pieces);
		for (int i = 0; i < pieces; i++) {
			addWallPiece(gizmos, from.add(step.scale(i)), from.add(step.scale(i + 1)), c1, c2, c3, c4, color);
		}
	}

	private static void addWallPiece(GizmoPrimitives gizmos, Vec3 from, Vec3 to, Vec3 c1, Vec3 c2, Vec3 c3, Vec3 c4,
			int color) {
		gizmos.addQuad(from.add(c1), from.add(c2), to.add(c2), to.add(c1), color);
		gizmos.addQuad(from.add(c2), from.add(c3), to.add(c3), to.add(c2), color);
		gizmos.addQuad(from.add(c3), from.add(c4), to.add(c4), to.add(c3), color);
		gizmos.addQuad(from.add(c4), from.add(c1), to.add(c1), to.add(c4), color);
	}

	/**
	 * A spark travelling from {@code from} to {@code to}, {@code phase} (0..1) of the way along: a closed
	 * cube for the head and {@link #COMET_TAIL} smaller cubes trailing behind it, all in {@code color}.
	 * Tail boxes that would fall behind {@code from} are dropped rather than bent round a corner. Opaque;
	 * the sheath and core are cut into short pieces ({@link #addWalls}) so the gizmo pass orders them
	 * against the spark correctly from every angle (MOD-665).
	 */
	static void addComet(GizmoPrimitives gizmos, Vec3 from, Vec3 to, double phase, int color) {
		Vec3 hop = to.subtract(from);
		double length = hop.length();
		if (length < 1.0e-6) {
			return;
		}
		Vec3 unit = hop.scale(1.0 / length);
		double along = phase * length;
		for (int k = COMET_TAIL; k >= 0; k--) {
			double at = along - k * COMET_TAIL_STEP;
			if (at < 0) {
				continue;
			}
			double half = COMET_HEAD_HALF * (1.0 - 0.22 * k);
			addBox(gizmos, from.add(unit.scale(at)), half, half, half, color);
		}
	}

	/** 0/1/2 for the axis {@code delta} mostly runs along, or -1 for a zero-length delta. */
	private static int dominantAxis(Vec3 delta) {
		double x = Math.abs(delta.x);
		double y = Math.abs(delta.y);
		double z = Math.abs(delta.z);
		if (x < 1.0e-6 && y < 1.0e-6 && z < 1.0e-6) {
			return -1;
		}
		if (x >= y && x >= z) {
			return 0;
		}
		return y >= z ? 1 : 2;
	}

	private static double component(Vec3 v, int axis) {
		return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
	}

	/**
	 * Draws a small axis-aligned solid cube centred on a network node — the "PCB pad" that hides the
	 * seam where independently-oriented tube segments meet. Each face is wound so
	 * {@code (v2-v1)×(v4-v1)} points outward; only three faces reach the screen per viewing angle.
	 */
	static void addJointCube(GizmoPrimitives gizmos, Vec3 center, float halfSize, int color) {
		addBox(gizmos, center, halfSize, halfSize, halfSize, color);
	}

	/**
	 * A translucent filled box plus a bright wireframe on the same bounds — the shape a "zone of
	 * effect" wants (MOD-278's repeller dome; the Fabric cable-placement preview draws the same pair
	 * by hand). Fill and edge are separate colours because a single translucent volume reads as fog
	 * at 24 blocks: the wireframe is what actually tells the player which cell is the boundary.
	 *
	 * <p>Any colour with {@code alpha < 255} is routed to vanilla's translucent gizmo group
	 * automatically, so the fill needs no extra setup here.
	 */
	/**
	 * Just the six faces of a box, with no wireframe.
	 *
	 * <p>For ghosts built out of MANY small boxes — the concentrator's assembly schematic draws a
	 * whole section's cage per cell — where outlining every one of them would turn the shape into a
	 * thicket of lines and hide the very silhouette it is meant to show.
	 */
	static void addBoxFill(GizmoPrimitives gizmos, AABB box, int color) {
		addBox(gizmos, box.getCenter(), box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2,
				color);
	}

	public static void addBoxOutline(GizmoPrimitives gizmos, AABB box, int fillColor,
			int edgeColor, float edgeWidth) {
		Vec3 center = box.getCenter();
		addBox(gizmos, center, box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2, fillColor);

		Vec3 min = new Vec3(box.minX, box.minY, box.minZ);
		Vec3 max = new Vec3(box.maxX, box.maxY, box.maxZ);
		// Four verticals + two horizontal rings; every edge of the cube exactly once.
		for (int i = 0; i < 4; i++) {
			double x = (i == 0 || i == 3) ? min.x : max.x;
			double z = (i < 2) ? min.z : max.z;
			gizmos.addLine(new Vec3(x, min.y, z), new Vec3(x, max.y, z), edgeColor, edgeWidth);
		}
		for (double y : new double[] {min.y, max.y}) {
			gizmos.addLine(new Vec3(min.x, y, min.z), new Vec3(max.x, y, min.z), edgeColor, edgeWidth);
			gizmos.addLine(new Vec3(max.x, y, min.z), new Vec3(max.x, y, max.z), edgeColor, edgeWidth);
			gizmos.addLine(new Vec3(max.x, y, max.z), new Vec3(min.x, y, max.z), edgeColor, edgeWidth);
			gizmos.addLine(new Vec3(min.x, y, max.z), new Vec3(min.x, y, min.z), edgeColor, edgeWidth);
		}
	}

	/** {@link #addJointCube} generalized to per-axis half-extents — same outward winding. */
	private static void addBox(GizmoPrimitives gizmos, Vec3 center, double halfX, double halfY,
			double halfZ, int color) {
		Vec3 x = new Vec3(halfX, 0, 0);
		Vec3 y = new Vec3(0, halfY, 0);
		Vec3 z = new Vec3(0, 0, halfZ);

		addFace(gizmos, center.add(x), y, z, color); // +X: y×z = +x
		addFace(gizmos, center.subtract(x), z, y, color); // -X: z×y = -x
		addFace(gizmos, center.add(y), z, x, color); // +Y: z×x = +y
		addFace(gizmos, center.subtract(y), x, z, color); // -Y: x×z = -y
		addFace(gizmos, center.add(z), x, y, color); // +Z: x×y = +z
		addFace(gizmos, center.subtract(z), y, x, color); // -Z: y×x = -z
	}

	private static void addFace(GizmoPrimitives gizmos, Vec3 faceCenter, Vec3 axisA, Vec3 axisB, int color) {
		gizmos.addQuad(faceCenter.subtract(axisA).subtract(axisB), faceCenter.add(axisA).subtract(axisB),
				faceCenter.add(axisA).add(axisB), faceCenter.subtract(axisA).add(axisB), color);
	}
}
