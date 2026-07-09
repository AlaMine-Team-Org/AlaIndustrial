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
// MOD-022 Phase 3: migrate onto the common client-networking abstraction. ClientPlayNetworking
// (payload receiver registration) is Fabric-only; NeoForge registers the client handler through
// RegisterPayloadHandlersEvent. The LevelRenderEvents world-render hook is a separate Fabric client
// seam with a NeoForge counterpart in net.neoforged.neoforge.client.event.RenderLevelStageEvent.
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client-side highlight for the Network Analyzer item (MOD-016): stores the last
 * {@link NetworkAnalyzerPayload} received and draws it every frame — in world space so it reads
 * through walls across several chunks. A "PCB trace" look, all via {@link DrawableGizmoPrimitives}:
 *
 * <ul>
 *   <li>a small solid cube — a "joint pad" — at every node that actually needs one: dead ends, turns
 *       and branches ({@link NetworkTopology#jointNodes}). A plain straight-through hop (two
 *       neighbours, exactly opposite directions) gets no joint and no retraction, so a long straight
 *       run of cable renders as one seamless tube instead of a little pinch at every block;
 *   <li>a chunky wire — a hollow square tube built from quads, not a thin {@code addLine} — drawn one
 *       piece per {@link NetworkTopology#tubeRuns straight run} between joint nodes rather than one
 *       piece per block, so a long straight stretch is a single seamless quad instead of many
 *       individually-correct-but-still-visibly-seamed ones; retracted at both ends so it terminates
 *       flush against the joint cube's flat square face there instead of overlapping into a jagged
 *       spike; see {@link NetworkTopology#fullAdjacency}, {@link #addTube};
 *   <li>animated dots travelling along {@link NetworkTopology#flowDirections} to show which way
 *       energy is actually moving, from generator to consumer.
 * </ul>
 *
 * The joint cube is drawn single-sided with each face wound so its vertex order gives an
 * outward-facing normal (see {@link #addJointCube}), relying on the renderer's normal backface
 * culling to discard the three faces pointing away from the camera. An earlier version emitted
 * every face twice (both windings) as a defensive hedge against unknown culling behaviour — with
 * depth testing off (needed so the highlight reads through walls), that means the far faces draw
 * right on top of the near ones with no depth sort between them, and their overlapping silhouettes
 * cut a jagged notch out of what should read as one solid cube. Trusting normal culling with correct
 * winding avoids that: only the true near faces should ever reach the screen.
 *
 * <p>Every position is centred on the block's actual {@link VoxelShape} bounds ({@link #nodeCenter})
 * rather than the raw block-cell centre — cables and (especially) half-height blocks like the Solar
 * Panel have a collision shape well off the cell's geometric centre, and {@code Vec3.atCenterOf}
 * placed the marker floating above the model instead of on it.
 *
 * <p>All sizes are world-space (metres of a block), not screen pixels, so they were tuned to stay
 * readable at normal play distance rather than to look reasonable only up close — a hairline
 * {@code addLine} is essentially invisible past a few blocks. The tube/pad colour is a muted
 * dark teal so the flow dots (warm near-white) read clearly against it instead of blending in.
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
 * once, when a payload arrives, not on every render call — it never changes between payloads, so
 * re-deriving it on every one of the 60+ frames rendered while a highlight is visible would be pure
 * waste. Only per-position {@link ClientLevel#isLoaded} checks, {@link #nodeCenter} lookups and the
 * flow-dot time offset — the things that genuinely change frame to frame — run in {@link #render}.
 */
public final class NetworkVisualizationClient {
	private NetworkVisualizationClient() {
	}

	private static final int TUBE_COLOR = 0xFF11577A;     // muted dark teal — tube body + joint cubes
	private static final int PRODUCER_COLOR = 0xFF84CC16; // green
	private static final int CONSUMER_COLOR = 0xFFFB923C; // orange
	private static final int FLOW_COLOR = 0xFFFFE9A8;     // warm near-white spark — pops against the dark tube

	private static final float FLOW_POINT_SIZE = 0.50f;
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

	/** Flow dots per edge and how many edge-lengths they cross per second (edges are always exactly 1 block, see
	 * {@link NetworkTopology#fullAdjacency}), tuned for a readable "current" without crowding dense networks. */
	private static final int FLOW_DOTS_PER_EDGE = 3;
	private static final double FLOW_SPEED_EDGES_PER_SECOND = 1.0;

	private static ResourceKey<Level> dimension;
	private static List<BlockPos> cables = List.of();
	private static List<BlockPos> producers = List.of();
	private static List<BlockPos> consumers = List.of();
	/** Full topology (cables + producer/consumer legs), recomputed only in {@link #init}'s payload receiver. */
	private static List<NetworkEdge> edges = List.of();
	/** Subset of {@link #edges} with a well-defined flow direction, recomputed alongside {@link #edges}. */
	private static List<FlowEdge> flowEdges = List.of();
	/** Nodes that need a visible joint cube — dead ends, turns, branches — see
	 * {@link NetworkTopology#jointNodes}. Recomputed alongside {@link #edges}. */
	private static Set<BlockPos> jointNodes = Set.of();
	/** Merged straight stretches of tube between joint nodes, see {@link NetworkTopology#tubeRuns} — the
	 * static tube outline is drawn from this, not directly from {@link #edges}, so a long straight run
	 * is one seamless quad instead of one per block. Recomputed alongside {@link #edges}. */
	private static List<TubeRun> tubeRuns = List.of();
	/** Producer + consumer positions, for {@link #nodeCenter} to tell "endpoint" (shape-aware vertical
	 * placement so a half-height Solar Panel marker sits on the model) from "cable" (always the cell
	 * centre — a cable's connection arms skew its {@link VoxelShape} bounds off-axis). */
	private static Set<BlockPos> endpointPositions = Set.of();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(NetworkAnalyzerPayload.TYPE, (payload, context) -> {
			dimension = payload.dimension();
			cables = payload.cables();
			producers = payload.producers();
			consumers = payload.consumers();
			edges = NetworkTopology.fullAdjacency(cables, producers, consumers);
			flowEdges = NetworkTopology.flowDirections(edges, producers);
			List<BlockPos> allNodes = new ArrayList<>(cables.size() + producers.size() + consumers.size());
			allNodes.addAll(cables);
			allNodes.addAll(producers);
			allNodes.addAll(consumers);
			jointNodes = NetworkTopology.jointNodes(allNodes, edges);
			tubeRuns = NetworkTopology.tubeRuns(allNodes, edges, jointNodes);
			Set<BlockPos> endpoints = new HashSet<>(producers);
			endpoints.addAll(consumers);
			endpointPositions = endpoints;
		});
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(NetworkVisualizationClient::render);
	}

	private static void render(LevelRenderContext context) {
		if (!AlaClientConfig.networkOverlayEnabled) {
			return;
		}
		if (cables.isEmpty() && producers.isEmpty() && consumers.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || dimension == null || !level.dimension().equals(dimension)) {
			return;
		}

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		int tubeColor = AlaClientConfig.networkOverlayColor;

		for (BlockPos pos : cables) {
			if (level.isLoaded(pos) && jointNodes.contains(pos)) {
				addJointCube(gizmos, nodeCenter(level, pos), JOINT_HALF_SIZE, tubeColor);
			}
		}
		for (TubeRun run : tubeRuns) {
			if (allLoaded(level, run.positions())) {
				addTube(gizmos, nodeCenter(level, run.from()), nodeCenter(level, run.to()), tubeColor,
						TUBE_HALF_THICKNESS, true, true);
			}
		}

		if (AlaClientConfig.networkOverlayFlowDots) {
			double timeSeconds = System.currentTimeMillis() / 1000.0;
			for (FlowEdge flow : flowEdges) {
				if (!level.isLoaded(flow.from()) || !level.isLoaded(flow.to())) {
					continue;
				}
				Vec3 from = nodeCenter(level, flow.from());
				Vec3 to = nodeCenter(level, flow.to());
				for (int i = 0; i < FLOW_DOTS_PER_EDGE; i++) {
					double phase = (timeSeconds * FLOW_SPEED_EDGES_PER_SECOND + (double) i / FLOW_DOTS_PER_EDGE) % 1.0;
					gizmos.addPoint(from.lerp(to, phase), FLOW_COLOR, FLOW_POINT_SIZE);
				}
			}
		}

		addJointCubes(gizmos, producers, PRODUCER_COLOR, level);
		addJointCubes(gizmos, consumers, CONSUMER_COLOR, level);
		gizmos.submit(context.submitNodeCollector(), context.levelState().cameraRenderState,
				AlaClientConfig.networkOverlayThroughBlocks);
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
	 * with {@code maxY == 0.5}) is anchored on its <i>top surface</i> — otherwise the marker would sit
	 * at the slab's vertical centre (0.25) and a tube from an adjacent cable would appear to plunge
	 * into the panel rather than land on it (MOD-042). Cables always use the cell centre (their arms
	 * skew the vertical bounds just as badly as the horizontal ones, so the shape can't be trusted
	 * for them on any axis).
	 */
	private static Vec3 nodeCenter(ClientLevel level, BlockPos pos) {
		double y = pos.getY() + 0.5;
		if (endpointPositions.contains(pos)) {
			VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
			if (!shape.isEmpty()) {
				AABB bounds = shape.bounds();
				// Half-block endpoints (e.g. Solar Panel, maxY=0.5) anchor on their top surface; full
				// cubes anchor at the vertical centre. Keeps full machines mid-face, no regression.
				y = pos.getY() + (bounds.maxY < 1.0 ? bounds.maxY : (bounds.minY + bounds.maxY) / 2.0);
			}
		}
		return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
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
			float halfThickness, boolean retractFrom, boolean retractTo) {
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
		Vec3 x = new Vec3(halfSize, 0, 0);
		Vec3 y = new Vec3(0, halfSize, 0);
		Vec3 z = new Vec3(0, 0, halfSize);

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
