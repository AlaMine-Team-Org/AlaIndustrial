package dev.alaindustrial.client.render;

import java.util.List;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.world.phys.Vec3;

/**
 * Primitive world-space geometry builders for {@link NetworkOverlayRenderer}: tubes (a hollow
 * square cross-section "wire" that survives at distance, with flow pulses painted as recoloured
 * stretches of its own walls) and solid axis-aligned cubes (joint pads + endpoint markers).
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
	/** Half-thickness of the wire's cross-section quads — full width ~0.18 blocks, a proper visible wire. */
	static final float TUBE_HALF_THICKNESS = 0.09f;
	/** Half-size of the joint cube at turn/branch nodes — larger than {@link #TUBE_HALF_THICKNESS} so it
	 * covers the tube's cross-section from any angle, hiding the seam between independently-oriented segments. */
	static final float JOINT_HALF_SIZE = 0.11f;
	/** Half-size of the joint cube at producer/consumer nodes — bigger than {@link #JOINT_HALF_SIZE} so the
	 * network's "socket" reads as deliberately larger than a mid-network joint. */
	static final float ENDPOINT_JOINT_HALF_SIZE = 0.15f;
	/** How far each tube segment is pulled back from its endpoints so it terminates flush against the joint
	 * cube (or the producer/consumer marker) instead of poking a spike through it. */
	static final float RETRACT = 0.09f;

	/** Flow pulses per edge and how many edge-lengths they cross per second (edges are always 1 block). */
	static final int FLOW_DOTS_PER_EDGE = 1;
	static final double FLOW_SPEED_EDGES_PER_SECOND = 1.0;
	/** Half-length of a flow pulse along the tube. A pulse is a recoloured stretch of the tube wall —
	 * see {@link #addTube} for why no overlay-geometry approach survives distance. */
	static final double FLOW_PULSE_HALF_LENGTH = 0.05;
	/** A pulse position is matched to a tube segment by projecting onto its axis; this is the maximum
	 * allowed perpendicular distance (squared) — generous for float noise, far below the 1-block spacing
	 * of parallel neighbouring cables. */
	static final double FLOW_PULSE_MATCH_EPS_SQ = 0.03 * 0.03;

	/** Unit vectors for axis-aligned edge directions, indexed by dominant axis (0=X, 1=Y, 2=Z). */
	static final Vec3[] AXIS_UNIT = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
	/** Cross-section basis for a tube segment walking along {@link #AXIS_UNIT}[axis] in its positive
	 * direction — {@code AXIS_UNIT[axis].cross(Y_UP)} (with the X=1,0,0 fallback for the Y axis). */
	static final Vec3[] SIDE_A_UNIT = {new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(-1, 0, 0)};
	/** {@code AXIS_UNIT[axis].cross(SIDE_A_UNIT[axis])}, precomputed alongside {@link #SIDE_A_UNIT}. */
	static final Vec3[] SIDE_B_UNIT = {new Vec3(0, -1, 0), new Vec3(-1, 0, 0), new Vec3(0, -1, 0)};

	/**
	 * Draws a chunky "wire" as a hollow square tube (four side walls, no end caps — the joint cube at
	 * either end covers those). {@code rawFrom}-to-{@code rawTo} is nominally one block apart (see
	 * {@link NetworkTopology#fullAdjacency}; {@code nodeCenter} can perturb that slightly), so direction
	 * is computed from the actual delta rather than assumed. Each end is pulled back by {@link #RETRACT}
	 * only if that end lands on a joint node — a plain straight-through hop needs no gap, so it renders
	 * flush into its neighbour instead of a seam every block.
	 *
	 * <p>The cross-section basis ({@link #SIDE_A_UNIT}, {@link #SIDE_B_UNIT}) is a fixed lookup by
	 * dominant axis, not a {@code dir.cross(UP)} computed fresh per call. Two collinear segments in a
	 * straight run can have opposite {@code dir} signs — the segment is normalized to always walk in
	 * that axis's positive direction so the basis stays bit-identical for every segment sharing an axis.
	 *
	 * <p>Flow pulses are painted as recoloured stretches of the tube wall (see {@link #FLOW_PULSE_HALF_LENGTH}
	 * for why no overlay-geometry approach survives distance): project every pulse onto this segment,
	 * keep the ones actually lying on it, and emit each wall as alternating base/bright sub-quads.
	 */
	static void addTube(DrawableGizmoPrimitives gizmos, Vec3 rawFrom, Vec3 rawTo, int color,
			float halfThickness, boolean retractFrom, boolean retractTo, List<Vec3> pulses, int flowColor) {
		Vec3 delta = rawTo.subtract(rawFrom);
		double adx = Math.abs(delta.x);
		double ady = Math.abs(delta.y);
		double adz = Math.abs(delta.z);
		if (adx < 1.0e-6 && ady < 1.0e-6 && adz < 1.0e-6) {
			return;
		}

		int axis;
		boolean negative;
		if (adx >= ady && adx >= adz) {
			axis = 0;
			negative = delta.x < 0;
		} else if (ady >= adx && ady >= adz) {
			axis = 1;
			negative = delta.y < 0;
		} else {
			axis = 2;
			negative = delta.z < 0;
		}

		Vec3 from = negative ? rawTo : rawFrom;
		Vec3 to = negative ? rawFrom : rawTo;
		boolean retractLow = negative ? retractTo : retractFrom;
		boolean retractHigh = negative ? retractFrom : retractTo;

		Vec3 canonicalDir = AXIS_UNIT[axis];
		if (retractLow) {
			from = from.add(canonicalDir.scale(RETRACT));
		}
		if (retractHigh) {
			to = to.subtract(canonicalDir.scale(RETRACT));
		}

		Vec3 sideA = SIDE_A_UNIT[axis].scale(halfThickness);
		Vec3 sideB = SIDE_B_UNIT[axis].scale(halfThickness);

		Vec3 c1 = sideA.scale(-1).add(sideB.scale(-1));
		Vec3 c2 = sideA.add(sideB.scale(-1));
		Vec3 c3 = sideA.add(sideB);
		Vec3 c4 = sideA.scale(-1).add(sideB);

		Vec3 seg = to.subtract(from);
		double length = seg.length();
		if (length < 1.0e-6) {
			return;
		}
		Vec3 unit = seg.scale(1.0 / length);

		// Collect pulse interval boundaries on this segment, then walk them in order, merging overlaps.
		double[] marks = null;
		int markCount = 0;
		if (!pulses.isEmpty()) {
			for (Vec3 pulse : pulses) {
				Vec3 rel = pulse.subtract(from);
				double t = rel.dot(unit);
				if (t < -FLOW_PULSE_HALF_LENGTH || t > length + FLOW_PULSE_HALF_LENGTH) {
					continue;
				}
				if (rel.subtract(unit.scale(t)).lengthSqr() > FLOW_PULSE_MATCH_EPS_SQ) {
					continue;
				}
				double lo = Math.max(0.0, t - FLOW_PULSE_HALF_LENGTH);
				double hi = Math.min(length, t + FLOW_PULSE_HALF_LENGTH);
				if (hi - lo < 1.0e-6) {
					continue;
				}
				if (marks == null) {
					marks = new double[8];
				} else if (markCount == marks.length) {
					marks = java.util.Arrays.copyOf(marks, marks.length * 2);
				}
				marks[markCount++] = lo;
				marks[markCount++] = hi;
			}
		}
		if (markCount == 0) {
			addTubeSection(gizmos, from, to, c1, c2, c3, c4, color);
			return;
		}

		double[][] intervals = new double[markCount / 2][2];
		for (int i = 0; i < intervals.length; i++) {
			intervals[i][0] = marks[i * 2];
			intervals[i][1] = marks[i * 2 + 1];
		}
		java.util.Arrays.sort(intervals, (a, b) -> Double.compare(a[0], b[0]));
		double cursor = 0.0;
		Vec3 cursorPoint = from;
		for (double[] interval : intervals) {
			double lo = Math.max(interval[0], cursor);
			double hi = Math.max(interval[1], cursor);
			if (hi - lo < 1.0e-6) {
				continue;
			}
			Vec3 loPoint = from.add(unit.scale(lo));
			Vec3 hiPoint = from.add(unit.scale(hi));
			if (lo - cursor > 1.0e-6) {
				addTubeSection(gizmos, cursorPoint, loPoint, c1, c2, c3, c4, color);
			}
			addTubeSection(gizmos, loPoint, hiPoint, c1, c2, c3, c4, flowColor);
			cursor = hi;
			cursorPoint = hiPoint;
		}
		if (length - cursor > 1.0e-6) {
			addTubeSection(gizmos, cursorPoint, to, c1, c2, c3, c4, color);
		}
	}

	/** Emits the four wall quads of one tube stretch between {@code from} and {@code to} with the
	 * shared cross-section corners {@code c1..c4} (see {@link #addTube} for the winding). */
	private static void addTubeSection(DrawableGizmoPrimitives gizmos, Vec3 from, Vec3 to,
			Vec3 c1, Vec3 c2, Vec3 c3, Vec3 c4, int color) {
		gizmos.addQuad(from.add(c1), from.add(c2), to.add(c2), to.add(c1), color);
		gizmos.addQuad(from.add(c2), from.add(c3), to.add(c3), to.add(c2), color);
		gizmos.addQuad(from.add(c3), from.add(c4), to.add(c4), to.add(c3), color);
		gizmos.addQuad(from.add(c4), from.add(c1), to.add(c1), to.add(c4), color);
	}

	/**
	 * Draws a small axis-aligned solid cube centred on a network node — the "PCB pad" that hides the
	 * seam where independently-oriented tube segments meet. Each face is wound so
	 * {@code (v2-v1)×(v4-v1)} points outward; only three faces reach the screen per viewing angle.
	 */
	static void addJointCube(DrawableGizmoPrimitives gizmos, Vec3 center, float halfSize, int color) {
		addBox(gizmos, center, halfSize, halfSize, halfSize, color);
	}

	/** {@link #addJointCube} generalized to per-axis half-extents — same outward winding. */
	private static void addBox(DrawableGizmoPrimitives gizmos, Vec3 center, double halfX, double halfY,
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

	private static void addFace(DrawableGizmoPrimitives gizmos, Vec3 faceCenter, Vec3 axisA, Vec3 axisB, int color) {
		gizmos.addQuad(faceCenter.subtract(axisA).subtract(axisB), faceCenter.add(axisA).subtract(axisB),
				faceCenter.add(axisA).add(axisB), faceCenter.subtract(axisA).add(axisB), color);
	}
}
