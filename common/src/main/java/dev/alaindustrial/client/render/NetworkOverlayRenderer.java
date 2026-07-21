package dev.alaindustrial.client.render;

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
import dev.alaindustrial.client.AlaClientConfig;

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
 *       spike; see {@link NetworkTopology#fullAdjacency}, {@link OverlayGeometry#addTube};
 *   <li>animated sparks travelling along {@link NetworkTopology#flowDirections} to show which way the
 *       energy flows. Each pulse is a short bright stretch of the tube itself — the wall quads are
 *       split and recoloured at the pulse position ({@link OverlayGeometry#FLOW_PULSE_HALF_LENGTH}) — <b>not</b> a
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

	// Geometry sizing + flow constants live in OverlayGeometry (extracted from this file). This class
	// keeps only the per-overlay colour palette and the static payload/topology state below.

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
				for (int i = 0; i < OverlayGeometry.FLOW_DOTS_PER_EDGE; i++) {
					double phase = (timeSeconds * OverlayGeometry.FLOW_SPEED_EDGES_PER_SECOND + (double) i / OverlayGeometry.FLOW_DOTS_PER_EDGE) % 1.0;
					pulses.add(from.lerp(to, phase));
				}
			}
		}

		for (BlockPos pos : cables) {
			if (level.isLoaded(pos) && jointNodes.contains(pos)) {
				OverlayGeometry.addJointCube(gizmos, nodeCenter(level, pos), OverlayGeometry.JOINT_HALF_SIZE, tubeColor);
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
	 * with {@code maxY == 0.5}) is anchored {@link #OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE} <i>below</i> its top
	 * surface, so the endpoint joint cube (built {@code ±halfSize} around the anchor) sits flush with
	 * the slab's top face instead of poking half a cube above it (MOD-049) — and the incoming tube
	 * (half-thickness {@link #OverlayGeometry.TUBE_HALF_THICKNESS} &lt; that offset) dips down to the panel like the
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
						? bounds.maxY - OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE
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
	 * lerp along, so tube and sparks agree again. The two pieces meet seamlessly: {@link OverlayGeometry#addTube} builds
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
			OverlayGeometry.addTube(gizmos, from, mainFrom, color, OverlayGeometry.TUBE_HALF_THICKNESS, true, false, pulses, FLOW_COLOR);
		}
		OverlayGeometry.addTube(gizmos, mainFrom, mainTo, color, OverlayGeometry.TUBE_HALF_THICKNESS, !dropFrom, !dropTo, pulses, FLOW_COLOR);
		if (dropTo) {
			OverlayGeometry.addTube(gizmos, mainTo, to, color, OverlayGeometry.TUBE_HALF_THICKNESS, false, true, pulses, FLOW_COLOR);
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
				OverlayGeometry.addJointCube(gizmos, nodeCenter(level, pos), OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE, color);
			}
		}
	}

	// The geometry primitives (addTube, addJointCube, addBox, addFace + the sizing/cross-section
	// constants) live in OverlayGeometry — extracted from this file. This class is the payload +
	// per-frame orchestration + node anchoring; geometry emission is delegated.
}
