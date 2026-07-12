package dev.alaindustrial.client;

import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkTopology;
import dev.alaindustrial.network.NetworkTopology.FlowEdge;
import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import dev.alaindustrial.network.NetworkTopology.TubeRun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loader-neutral world-space highlight for the Network Analyzer item (MOD-016): keeps the last
 * {@link NetworkAnalyzerPayload} received and draws it every frame — in world space so it reads
 * through walls across several chunks. A "PCB trace" look, all via the vanilla
 * {@link DrawableGizmoPrimitives}:
 *
 * <ul>
 *   <li>a small solid cube — a "joint pad" — at every node that actually needs one: dead ends, turns,
 *       branches ({@link NetworkTopology#jointNodes}) and every endpoint (producer/consumer/storage —
 *       an endpoint is the network's "socket" and is always a joint, even when it sits straight-through
 *       between two collinear cables, MOD-059). A plain straight-through cable hop gets no joint and no
 *       retraction, so a long straight run of cable renders as one seamless tube instead of a little
 *       pinch at every block;
 *   <li>a chunky wire — a hollow square tube built from quads, not a thin {@code addLine} — drawn one
 *       piece per {@link NetworkTopology#tubeRuns straight run} between joint nodes rather than one
 *       piece per block, so a long straight stretch is a single seamless quad instead of many
 *       individually-correct-but-still-visibly-seamed ones; retracted at both ends so it terminates
 *       flush against the joint cube's flat square face there instead of overlapping into a jagged
 *       spike; see {@link NetworkTopology#fullAdjacency}, {@link #addTube};
 *   <li>animated sparks travelling along {@link NetworkTopology#flowDirections} to show which way the
 *       energy flows. Each pulse is a short bright stretch of the tube itself — the wall quads are
 *       split and recoloured at the pulse position ({@link #FLOW_PULSE_HALF_LENGTH}) — <b>not</b> a
 *       {@code addPoint} — vanilla renders points through the {@code DEBUG_POINTS} pipeline whose
 *       vertex shader sets {@code gl_PointSize} in <i>screen pixels</i> with no perspective scaling
 *       (verified against the 26.2 {@code debug_point.vsh}), so a point neither grows when approached
 *       nor guarantees a minimum rasterized size — the old 0.5f point was half a pixel and effectively
 *       invisible (MOD-060). A cube is world-space geometry: it scales with distance like the tube it
 *       rides on, and renders identically on both loaders.
 * </ul>
 *
 * <p>This class is the single implementation for both loaders (MOD-033): the geometry only needs a
 * render-time {@link SubmitNodeCollector} + {@link CameraRenderState}, which Fabric exposes through
 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES} ({@code LevelRenderContext}) and NeoForge
 * through {@code SubmitCustomGeometryEvent} — both fire at the same point of the frame, right where
 * vanilla submits its own gizmo primitives. Loader classes are thin adapters: they receive/poll the
 * payload and forward their collector to {@link #submitFrame}.
 *
 * <p>Horizontal anchoring is always the block-cell centre; the vertical anchor of endpoints follows
 * their collision shape (see {@link #nodeCenter}) — a half-block Solar Panel keeps the marker on the
 * slab, not floating mid-cell (MOD-042/MOD-049).
 *
 * <p>All sizes are world-space (metres of a block), not screen pixels, so they stay readable at
 * normal play distance rather than looking reasonable only up close — a hairline {@code addLine} is
 * essentially invisible past a few blocks. The tube/pad colour is a muted dark teal so the flow
 * sparks (warm near-white) read clearly against it instead of blending in.
 *
 * <p>Producer/consumer endpoints get the same joint-cube treatment as cable nodes (bigger, and
 * tinted green/orange) rather than a flat camera-facing dot — a billboard dot cannot reliably cap
 * a 3D tube cross-section from every angle the way real geometry does. No pulsing, no text — the
 * flow animation alone communicates direction.
 *
 * <p>There is no separate expiry timer: a position simply stops being drawn once its chunk unloads
 * client-side ({@link ClientLevel#isLoaded}), and the highlight is replaced or cleared server-side
 * the next time the item is used (see {@code NetworkAnalyzerItem}).
 *
 * <p>Topology ({@link #edges}, {@link #flowEdges}, {@link #jointNodes}, {@link #tubeRuns}) is computed
 * once, when a payload arrives ({@link #updatePayload} compares by reference), not on every render
 * call — it never changes between payloads, so re-deriving it on every one of the 60+ frames rendered
 * while a highlight is visible would be pure waste. Only per-position {@link ClientLevel#isLoaded}
 * checks, {@link #nodeCenter} lookups and the flow-spark time offset — the things that genuinely
 * change frame to frame — run in {@link #submitFrame}.
 */
public final class NetworkOverlayRenderer {
	private NetworkOverlayRenderer() {
	}

	private static final int PRODUCER_COLOR = 0xFF84CC16; // green
	private static final int CONSUMER_COLOR = 0xFFFB923C; // orange
	private static final int STORAGE_COLOR = 0xFF38BDF8;  // light blue — storage sink / bridge node (MOD-047)
	private static final int FLOW_COLOR = 0xFFFFE9A8;     // warm near-white spark — pops against the dark tube

	/** Half-length of a flow pulse along the tube. A pulse is a <b>recoloured stretch of the tube
	 * itself</b> — {@link #addTube} splits every wall quad at the pulse boundaries and paints the
	 * pulse stretch {@link #FLOW_COLOR} — not geometry layered on top. Every overlay shape tried
	 * first (cubes, a thin ring, flat wall dashes; four feedback rounds in MOD-060) lost to the tube
	 * somewhere: their depth margin over the wall (0.005–0.025) drops below depth-buffer resolution
	 * with distance, so pulses visibly vanished on far stretches while near ones stayed. Recolouring
	 * the wall has zero geometric overlap — nothing to lose, at any distance, from any angle. */
	private static final double FLOW_PULSE_HALF_LENGTH = 0.05;
	/** A pulse position is matched to a tube segment by projecting onto its axis; this is the maximum
	 * allowed perpendicular distance (squared) — generous enough for float noise, far below the
	 * 1-block spacing of parallel neighbouring cables. */
	private static final double FLOW_PULSE_MATCH_EPS_SQ = 0.03 * 0.03;
	/** Half-thickness of the wire's cross-section quads — full width ~0.18 blocks, a proper visible wire
	 * instead of a hairline. */
	private static final float TUBE_HALF_THICKNESS = 0.09f;
	/** Half-size of the joint cube drawn at every turn/branch node — slightly larger than
	 * {@link #TUBE_HALF_THICKNESS} so it fully covers the tube's cross-section from any angle, hiding the
	 * seam where two independently oriented segments would otherwise meet as a jagged spike. */
	private static final float JOINT_HALF_SIZE = 0.11f;
	/** Half-size of the joint cube at producer/consumer nodes — a little larger than
	 * {@link #JOINT_HALF_SIZE} so the "socket" where the network ends reads as deliberately bigger than a
	 * plain mid-network joint. */
	private static final float ENDPOINT_JOINT_HALF_SIZE = 0.15f;
	/** How far each tube segment is pulled back from its endpoints so it terminates flush against the joint
	 * cube (or the producer/consumer marker) instead of poking a spike through it. */
	private static final float RETRACT = 0.09f;

	/** Unit vectors for the axis-aligned edge directions, indexed by dominant axis (0=X, 1=Y, 2=Z), always
	 * the axis's own positive direction — see {@link #addTube} for why segments are normalized to this
	 * canonical direction rather than using whichever way a given edge happens to be ordered. */
	private static final Vec3[] AXIS_UNIT = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
	/** Cross-section basis for a tube segment walking along {@link #AXIS_UNIT}[axis] in its positive
	 * direction — equal to {@code AXIS_UNIT[axis].cross(Y_UP)} (with the X=1,0,0 fallback for the Y axis,
	 * where that cross product would be zero), precomputed so no per-call {@code cross()} is needed. */
	private static final Vec3[] SIDE_A_UNIT = {new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(-1, 0, 0)};
	/** {@code AXIS_UNIT[axis].cross(SIDE_A_UNIT[axis])}, precomputed alongside {@link #SIDE_A_UNIT}. */
	private static final Vec3[] SIDE_B_UNIT = {new Vec3(0, -1, 0), new Vec3(-1, 0, 0), new Vec3(0, -1, 0)};

	/** Flow pulses per edge and how many edge-lengths they cross per second (edges are always exactly 1 block, see
	 * {@link NetworkTopology#fullAdjacency}). One pulse per block edge — three read as visual noise on long
	 * runs (player feedback, MOD-060). */
	private static final int FLOW_DOTS_PER_EDGE = 1;
	private static final double FLOW_SPEED_EDGES_PER_SECOND = 1.0;

	private static NetworkAnalyzerPayload cachedPayload;
	private static ResourceKey<Level> dimension;
	private static List<BlockPos> cables = List.of();
	private static List<BlockPos> producers = List.of();
	private static List<BlockPos> consumers = List.of();
	private static List<BlockPos> storage = List.of();
	/** Full topology (cables + producer/consumer legs), recomputed only in {@link #updatePayload}. */
	private static List<NetworkEdge> edges = List.of();
	/** Subset of {@link #edges} with a well-defined flow direction, recomputed alongside {@link #edges}. */
	private static List<FlowEdge> flowEdges = List.of();
	/** Nodes that need a visible joint cube — dead ends, turns, branches (see
	 * {@link NetworkTopology#jointNodes}) plus every endpoint: an endpoint sitting straight-through
	 * between two collinear cables is still the network's "socket", so it always gets a marker and
	 * always breaks the tube run (MOD-059). Recomputed alongside {@link #edges}. */
	private static Set<BlockPos> jointNodes = Set.of();
	/** Merged straight stretches of tube between joint nodes, see {@link NetworkTopology#tubeRuns} — the
	 * static tube outline is drawn from this, not directly from {@link #edges}, so a long straight run
	 * is one seamless quad instead of one per block. Recomputed alongside {@link #edges}. */
	private static List<TubeRun> tubeRuns = List.of();
	/** Producer + consumer + storage positions, for {@link #nodeCenter} to tell "endpoint" (shape-aware
	 * vertical placement so a half-height Solar Panel marker sits on the model) from "cable" (always the
	 * cell centre — a cable's connection arms skew its {@link VoxelShape} bounds off-axis). */
	private static Set<BlockPos> endpointPositions = Set.of();

	/**
	 * Accepts the most recent payload (or {@code null} when none has arrived yet) and recomputes the
	 * topology if it actually changed — payloads are immutable and replaced wholesale on the receive
	 * seam of each loader, so reference identity is the change test. Fabric calls this once from its
	 * payload receiver; NeoForge polls its stored payload every frame, which this makes equally cheap.
	 */
	public static void updatePayload(NetworkAnalyzerPayload payload) {
		if (payload == null || payload == cachedPayload) {
			return;
		}
		cachedPayload = payload;
		dimension = payload.dimension();
		cables = payload.cables();
		producers = payload.producers();
		consumers = payload.consumers();
		storage = payload.storage();
		// Storage sinks bridge two cable segments and behave as endpoints for shaping (MOD-047):
		// a BatteryBox is a full cube, but joining the endpoint set keeps nodeCenter consistent and
		// lets fullAdjacency/tubeRuns route tubes through them like any other endpoint.
		List<BlockPos> allEndpoints = new ArrayList<>(producers.size() + consumers.size() + storage.size());
		allEndpoints.addAll(producers);
		allEndpoints.addAll(consumers);
		allEndpoints.addAll(storage);
		edges = NetworkTopology.fullAdjacency(cables, allEndpoints, List.of());
		flowEdges = NetworkTopology.flowDirections(edges, producers);
		List<BlockPos> allNodes = new ArrayList<>(cables.size() + allEndpoints.size());
		allNodes.addAll(cables);
		allNodes.addAll(allEndpoints);
		endpointPositions = new HashSet<>(allEndpoints);
		// Endpoints are always joints (MOD-059): without this union a producer/consumer squeezed
		// between two collinear cables counts as a plain straight-through hop — no marker, and the
		// tube run would pass level through a half-block panel's cell instead of dropping to it.
		Set<BlockPos> joints = new HashSet<>(NetworkTopology.jointNodes(allNodes, edges));
		joints.addAll(endpointPositions);
		jointNodes = joints;
		tubeRuns = NetworkTopology.tubeRuns(allNodes, edges, jointNodes);
	}

	/**
	 * Builds this frame's overlay geometry and hands it to the loader's render-time collector. Both
	 * loaders call this at the equivalent frame point (see the class comment), so animation phase,
	 * geometry and the always-on-top flag behave identically.
	 */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		if (!AlaClientConfig.networkOverlayEnabled) {
			return;
		}
		if (cables.isEmpty() && producers.isEmpty() && consumers.isEmpty() && storage.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || dimension == null || !level.dimension().equals(dimension)) {
			return;
		}

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		int tubeColor = AlaClientConfig.networkOverlayColor;

		// Current world positions of every flow pulse — computed before the tubes are drawn, because
		// the pulses are rendered AS bright segments of the tube walls themselves (see addTube), not
		// as separate geometry layered on top.
		List<Vec3> pulses = List.of();
		if (AlaClientConfig.networkOverlayFlowDots && !flowEdges.isEmpty()) {
			pulses = new ArrayList<>();
			double timeSeconds = System.currentTimeMillis() / 1000.0;
			for (FlowEdge flow : flowEdges) {
				if (!level.isLoaded(flow.from()) || !level.isLoaded(flow.to())) {
					continue;
				}
				Vec3 from = nodeCenter(level, flow.from());
				Vec3 to = nodeCenter(level, flow.to());
				for (int i = 0; i < FLOW_DOTS_PER_EDGE; i++) {
					double phase = (timeSeconds * FLOW_SPEED_EDGES_PER_SECOND + (double) i / FLOW_DOTS_PER_EDGE) % 1.0;
					pulses.add(from.lerp(to, phase));
				}
			}
		}

		for (BlockPos pos : cables) {
			if (level.isLoaded(pos) && jointNodes.contains(pos)) {
				addJointCube(gizmos, nodeCenter(level, pos), JOINT_HALF_SIZE, tubeColor);
			}
		}
		for (TubeRun run : tubeRuns) {
			if (allLoaded(level, run.positions())) {
				addRun(gizmos, level, run, tubeColor, pulses);
			}
		}

		addJointCubes(gizmos, producers, PRODUCER_COLOR, level);
		addJointCubes(gizmos, consumers, CONSUMER_COLOR, level);
		addJointCubes(gizmos, storage, STORAGE_COLOR, level);
		gizmos.submit(collector, cameraRenderState, AlaClientConfig.networkOverlayThroughBlocks);
	}

	/**
	 * Where to anchor a node's tube/marker geometry, in world space. Horizontally this is always the
	 * block-cell centre: a cable's {@link VoxelShape} is a core plus an arm toward each connection (see
	 * {@code CableBlock}), so its {@code bounds()} centre is pulled sideways toward whichever arms it
	 * happens to have — using that skewed centre put the tube off to one side of the cable and, worse,
	 * bent straight runs into diagonals because adjacent cables with different arm sets landed at
	 * different offsets. The cell centre is on the block grid, so orthogonally-adjacent cables line up
	 * into perfectly straight axis-aligned tubes, matching how the cable blocks themselves are laid out.
	 *
	 * <p>Vertically, endpoints (producers/consumers) follow their collision shape. A full-height
	 * machine (shape {@code maxY == 1.0}) is anchored at its vertical centre (cell centre), so a tube
	 * meets it mid-face. A <b>half-block</b> endpoint (shape {@code maxY < 1.0}, e.g. a Solar Panel
	 * with {@code maxY == 0.5}) is anchored {@link #ENDPOINT_JOINT_HALF_SIZE} <i>below</i> its top
	 * surface, so the endpoint joint cube (built {@code ±halfSize} around the anchor) sits flush with
	 * the slab's top face instead of poking half a cube above it (MOD-049) — and the incoming tube
	 * (half-thickness {@link #TUBE_HALF_THICKNESS} &lt; that offset) dips down to the panel like the
	 * cable's own {@code arm_low} model, staying inside the slab's height. Anchoring at the slab's
	 * vertical centre (0.25) instead would make tubes appear to plunge into the panel (MOD-042).
	 * Cables always use the cell centre (their arms skew the vertical bounds just as badly as the
	 * horizontal ones, so the shape can't be trusted for them on any axis).
	 */
	private static Vec3 nodeCenter(ClientLevel level, BlockPos pos) {
		double y = pos.getY() + 0.5;
		if (endpointPositions.contains(pos)) {
			VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
			if (!shape.isEmpty()) {
				AABB bounds = shape.bounds();
				// Half-block endpoints (e.g. Solar Panel, maxY=0.5) tuck the anchor below the top
				// surface so the marker cube's top is flush with the slab (MOD-049); full cubes
				// anchor at the vertical centre. Keeps full machines mid-face, no regression.
				y = pos.getY() + (bounds.maxY < 1.0
						? bounds.maxY - ENDPOINT_JOINT_HALF_SIZE
						: (bounds.minY + bounds.maxY) / 2.0);
			}
		}
		return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
	}

	/**
	 * Draws one {@link TubeRun} as a tube, keeping a half-block endpoint's vertical "drop" local to the
	 * run's last hop (MOD-049). A run merges a whole straight stretch into a single segment — drawn
	 * naively from anchor to anchor, a run ending on a Solar Panel (anchor below the cable level, see
	 * {@link #nodeCenter}) would tilt the entire multi-block tube into one long shallow diagonal, and
	 * the flow sparks (animated per block edge, level along the middle hops) would visibly float off it.
	 * Instead the run is split like the cable's own {@code arm_low} model: the main tube stays level at
	 * the cable height up to the node adjacent to the anchored endpoint, then a short one-hop "drop"
	 * segment descends to the endpoint anchor — which is exactly the path the last flow edge's sparks
	 * lerp along, so tube and sparks agree again. The two pieces meet seamlessly: {@link #addTube} builds
	 * both cross-sections from the same shared point with the same per-axis basis, so no joint cube is
	 * needed at the split. Runs whose ends sit at the cell centre (full machines, cables) take the
	 * single-segment path unchanged; a run of one hop is already local, so it never splits.
	 */
	private static void addRun(DrawableGizmoPrimitives gizmos, ClientLevel level, TubeRun run, int color,
			List<Vec3> pulses) {
		List<BlockPos> positions = run.positions();
		Vec3 from = nodeCenter(level, run.from());
		Vec3 to = nodeCenter(level, run.to());
		boolean dropFrom = positions.size() > 2 && Math.abs(from.y - (run.from().getY() + 0.5)) > 1.0e-6;
		boolean dropTo = positions.size() > 2 && Math.abs(to.y - (run.to().getY() + 0.5)) > 1.0e-6;

		Vec3 mainFrom = dropFrom ? nodeCenter(level, positions.get(1)) : from;
		Vec3 mainTo = dropTo ? nodeCenter(level, positions.get(positions.size() - 2)) : to;

		if (dropFrom) {
			addTube(gizmos, from, mainFrom, color, TUBE_HALF_THICKNESS, true, false, pulses);
		}
		addTube(gizmos, mainFrom, mainTo, color, TUBE_HALF_THICKNESS, !dropFrom, !dropTo, pulses);
		if (dropTo) {
			addTube(gizmos, mainTo, to, color, TUBE_HALF_THICKNESS, false, true, pulses);
		}
	}

	private static boolean allLoaded(ClientLevel level, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (!level.isLoaded(pos)) {
				return false;
			}
		}
		return true;
	}

	private static void addJointCubes(DrawableGizmoPrimitives gizmos, List<BlockPos> positions, int color,
			ClientLevel level) {
		for (BlockPos pos : positions) {
			if (level.isLoaded(pos) && jointNodes.contains(pos)) {
				addJointCube(gizmos, nodeCenter(level, pos), ENDPOINT_JOINT_HALF_SIZE, color);
			}
		}
	}

	/**
	 * Draws a chunky "wire" as a hollow square tube (four side walls, no end caps — the joint cube at
	 * either end covers those) — a bare {@code addLine} reads as an invisible hairline at normal play
	 * distance, so the topology is real geometry instead. A square cross-section, not a "+"-shaped
	 * cross of two planes, so it meets a {@link #addJointCube joint cube}'s flat square face flush:
	 * the "+" shape has no material along its diagonals, which showed up as a notch cut into the joint
	 * right where the tube met it. {@code rawFrom}-to-{@code rawTo} is nominally one block apart (see
	 * {@link NetworkTopology#fullAdjacency}; {@link #nodeCenter} can perturb that slightly for
	 * non-full-cube shapes), so direction is computed from the actual delta rather than assumed. Each
	 * end is pulled back by {@link #RETRACT} only if that end lands on a {@link #jointNodes joint node}
	 * — a plain straight-through hop needs no gap, so it renders flush into its neighbour instead of a
	 * seam every block.
	 *
	 * <p>Single-sided with the same outward-normal winding derivation as {@link #addJointCube}: walking
	 * the four cross-section corners in a consistent order and building each side quad from matching
	 * corners at {@code from} and {@code to} gives an outward-facing normal automatically, so normal
	 * backface culling shows only the two or three walls actually facing the camera.
	 *
	 * <p>The cross-section basis ({@link #SIDE_A_UNIT}, {@link #SIDE_B_UNIT}) is a fixed lookup by
	 * dominant axis, not a {@code dir.cross(UP)} computed fresh per call. Two collinear segments in a
	 * straight run can have opposite {@code dir} signs — the edge's canonical ordering doesn't track
	 * which way the path was walked — and although the resulting corner set is geometrically the same
	 * either way, computing it via {@code cross()}/{@code normalize()} from two different (if
	 * geometrically equivalent) inputs is not guaranteed to produce bit-identical floating-point
	 * vertices: a sub-millimetre mismatch at the shared joint between segments, invisible in the numbers
	 * but visible on screen as a thin diagonal crease. To get a fixed per-axis constant without breaking
	 * the outward-normal winding for the axis's *negative* direction, the segment is first normalized to
	 * always walk in that axis's positive direction (swapping {@code from}/{@code to} if needed) — so the
	 * basis stays bit-identical for every segment sharing an axis, and correct winding is preserved
	 * because the geometry always "sees" the same canonical direction the basis was derived for.
	 */
	private static void addTube(DrawableGizmoPrimitives gizmos, Vec3 rawFrom, Vec3 rawTo, int color,
			float halfThickness, boolean retractFrom, boolean retractTo, List<Vec3> pulses) {
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

		// Flow pulses are painted as recoloured stretches of the tube itself (see
		// FLOW_PULSE_HALF_LENGTH for why no overlay-geometry approach survives distance): project
		// every pulse onto this segment, keep the ones actually lying on it, and emit each wall as
		// alternating base/bright sub-quads. Boundary points are computed once and shared by the
		// sub-quads on both sides, so the split introduces no cracks. No pulses -> the single
		// full-length quad per wall, exactly as before.
		Vec3 seg = to.subtract(from);
		double length = seg.length();
		if (length < 1.0e-6) {
			return;
		}
		Vec3 unit = seg.scale(1.0 / length);

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

		// Sort pulse intervals by start and walk the segment, merging overlaps as we go.
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
			addTubeSection(gizmos, loPoint, hiPoint, c1, c2, c3, c4, FLOW_COLOR);
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
	 * seam where independently oriented tube segments meet, instead of a jagged spike. Each face is
	 * wound so {@code (v2-v1)×(v4-v1)} points outward (standard CCW-front convention): for the "positive"
	 * faces that's {@code axisA × axisB} with {@code axisA} then {@code axisB} taken in
	 * x→y→z→x cyclic order (e.g. +Y uses {@code axisA=z, axisB=x}), and the "negative" faces swap the
	 * two axes to flip the cross product. Only three faces should ever reach the screen per viewing
	 * angle — the other three point away from the camera and get discarded by normal backface culling.
	 */
	private static void addJointCube(DrawableGizmoPrimitives gizmos, Vec3 center, float halfSize, int color) {
		addBox(gizmos, center, halfSize, halfSize, halfSize, color);
	}

	/** {@link #addJointCube} generalized to per-axis half-extents — used for the flow-spark bands,
	 * which are short along their travel axis and tube-hugging across it. Same outward winding. */
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
