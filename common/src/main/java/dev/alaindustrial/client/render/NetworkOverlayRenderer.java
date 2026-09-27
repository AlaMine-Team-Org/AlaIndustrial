package dev.alaindustrial.client.render;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkTopology;
import dev.alaindustrial.network.NetworkTopology.FlowEdge;
import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loader-neutral world-space highlight for the Network Analyzer item (MOD-016): keeps the last
 * {@link NetworkAnalyzerPayload} received (in {@link NetworkOverlayState}) and draws it every frame —
 * in world space, always through walls and translucent (MOD-665: the "through blocks" switch is gone,
 * the trace lives inside the cables it follows and was invisible with depth testing on). A "PCB trace"
 * look, all via vanilla gizmo primitives ({@link GizmoPrimitives}, emitted through {@link FrameGizmos}):
 *
 * <ul>
 *   <li>a beam along every wire the network really has ({@link NetworkTopology#connectedAdjacency}): a thin
 *       bright core inside a faint sheath, coloured along the path from the configured colour at a producer
 *       to warm yellow at the far end ({@link NetworkOverlayState#pathFraction}). It is drawn one hop per
 *       block, walls only, so a straight line reads as one seamless beam;
 *   <li>a joint — a small core cube in its sheath cube — at every node that needs one: dead ends, turns,
 *       branches ({@link NetworkTopology#jointNodes}); every endpoint (producer/consumer/storage) gets a
 *       bigger cube in its role colour — the network's "socket", always a joint (MOD-059);
 *   <li>sparks travelling along {@link NetworkTopology#flowFromNetwork} — the way the network itself moves EU:
 *       each a small comet of closed cubes, <b>not</b> an {@code addPoint} (vanilla draws points in screen
 *       pixels with no perspective, MOD-060) and not a recoloured stretch of an open tube, which reads as an
 *       empty square frame (MOD-665).
 * </ul>
 *
 * <p>This class is the single implementation for both loaders (MOD-033): Fabric calls it from
 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES}, NeoForge from {@code SubmitCustomGeometryEvent};
 * both hand the payload over through {@link #updatePayload} as it arrives and call {@link #clear} when
 * the player leaves the world.
 *
 * <p>Horizontal anchoring is always the block-cell centre; the vertical anchor of endpoints follows
 * their collision shape (see {@link #nodeCenter}) — a half-block Solar Panel keeps the marker on the
 * slab, not floating mid-cell (MOD-042/MOD-049).
 *
 * <p>The topology is computed once per payload ({@link NetworkOverlayState#update}); a frame only checks
 * which positions are loaded, anchors endpoints (once each per frame) and places the sparks — at most
 * {@link OverlayMath#MAX_FRAME_PULSES} of them.
 */
public final class NetworkOverlayRenderer {
	private NetworkOverlayRenderer() {
	}

	private static final int PRODUCER_COLOR = 0xFF84CC16; // green
	private static final int CONSUMER_COLOR = 0xFFFB923C; // orange
	private static final int STORAGE_COLOR = 0xFF38BDF8;  // light blue — storage sink / bridge node (MOD-047)
	/** The far end of the trace's colour ramp; the near end is the colour chosen in the settings. */
	private static final int RAMP_END_COLOR = 0xFFFACC15; // warm yellow
	/** How far the core and the sparks are lifted from the ramp colour toward white. */
	private static final double CORE_WHITENESS = 0.5;
	private static final double SPARK_WHITENESS = 0.8;

	private static final NetworkOverlayState STATE = new NetworkOverlayState();

	/**
	 * Accepts a payload from the loader's receive seam. An empty payload clears the trace; the same
	 * payload twice recomputes nothing.
	 */
	public static void updatePayload(NetworkAnalyzerPayload payload) {
		STATE.update(payload);
	}

	/** Drops the trace — the player left the world (called by both loaders on disconnect, D1). */
	public static void clear() {
		STATE.clear();
	}

	/**
	 * Builds this frame's overlay geometry and hands it to the loader's render-time collector. Both
	 * loaders call this at the equivalent frame point (see the class comment), so animation phase,
	 * geometry and the always-on-top flag behave identically.
	 */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		if (!AlaClientConfig.networkOverlayEnabled || STATE.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !STATE.retainFor(level.dimension())) {
			return;
		}

		// Built inside a vanilla-collected gizmo: see FrameGizmos for why a direct submit is not drawn on 26.3.
		FrameGizmos.emit((gizmos, alphaMultiplier) -> {
			int alpha = AlaClientConfig.networkOverlayAlpha;
			int start = OverlayMath.withAlpha(AlaClientConfig.networkOverlayColor, 255);
			int sheathAlpha = (int) Math.round(alpha * OverlayGeometry.SHEATH_OPACITY);
			Map<BlockPos, Vec3> centers = new HashMap<>();

			// Sparks are placed first and drawn after the beam.
			record Spark(Vec3 from, Vec3 to, double phase, int color) {
			}
			List<Spark> sparks = new ArrayList<>();
			if (AlaClientConfig.networkOverlayFlowDots) {
				double gameTicks = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				int budget = OverlayMath.MAX_FRAME_PULSES;
				for (FlowEdge flow : STATE.flowEdges()) {
					if (budget <= 0) {
						break;
					}
					if (!level.isLoaded(flow.from()) || !level.isLoaded(flow.to())) {
						continue;
					}
					Vec3 from = center(level, flow.from(), centers);
					Vec3 to = center(level, flow.to(), centers);
					double tFrom = STATE.pathFraction(flow.from());
					double tTo = STATE.pathFraction(flow.to());
					for (int i = 0; i < OverlayGeometry.FLOW_DOTS_PER_EDGE && budget > 0; i++, budget--) {
						double phase = OverlayMath.flowPhase(gameTicks, i, OverlayGeometry.FLOW_DOTS_PER_EDGE,
								OverlayGeometry.FLOW_SPEED_EDGES_PER_SECOND);
						int base = rampColor(start, tFrom + (tTo - tFrom) * phase);
						sparks.add(new Spark(from, to, phase, OverlayMath.lerpColor(base, 0xFFFFFFFF, SPARK_WHITENESS)));
					}
				}
			}

			for (NetworkEdge edge : STATE.edges()) {
				if (!level.isLoaded(edge.a()) || !level.isLoaded(edge.b())) {
					continue; // D13: a line crossing the edge of the loaded world is drawn up to that edge
				}
				Vec3 a = center(level, edge.a(), centers);
				Vec3 b = center(level, edge.b(), centers);
				int base = rampColor(start, (STATE.pathFraction(edge.a()) + STATE.pathFraction(edge.b())) / 2);
				OverlayGeometry.addBeamHop(gizmos, a, b, OverlayGeometry.SHEATH_HALF, retract(edge.a(), false),
						retract(edge.b(), false), OverlayMath.withAlpha(base, sheathAlpha));
				OverlayGeometry.addBeamHop(gizmos, a, b, OverlayGeometry.CORE_HALF, retract(edge.a(), true),
						retract(edge.b(), true), core(base));
			}

			for (BlockPos pos : STATE.cables()) {
				if (level.isLoaded(pos) && STATE.jointNodes().contains(pos)) {
					Vec3 c = center(level, pos, centers);
					int base = rampColor(start, STATE.pathFraction(pos));
					OverlayGeometry.addJointCube(gizmos, c, OverlayGeometry.JOINT_SHEATH_HALF,
							OverlayMath.withAlpha(base, sheathAlpha));
					OverlayGeometry.addJointCube(gizmos, c, OverlayGeometry.JOINT_CORE_HALF, core(base));
				}
			}

			for (Spark spark : sparks) {
				OverlayGeometry.addComet(gizmos, spark.from(), spark.to(), spark.phase(), spark.color());
			}

			addJointCubes(gizmos, STATE.producers(), OverlayMath.withAlpha(PRODUCER_COLOR, alpha), level, centers);
			addJointCubes(gizmos, STATE.consumers(), OverlayMath.withAlpha(CONSUMER_COLOR, alpha), level, centers);
			addJointCubes(gizmos, STATE.storage(), OverlayMath.withAlpha(STORAGE_COLOR, alpha), level, centers);
		}, true);
	}

	/** The trace colour {@code t} of the way from a producer ({@code start}) to the far end (warm yellow). */
	private static int rampColor(int start, double t) {
		return OverlayMath.lerpColor(start, RAMP_END_COLOR, t);
	}

	/**
	 * The core is the sheath's colour lifted toward white — the bright wire inside the glow. It and the
	 * sparks are opaque whatever the opacity setting: translucent gizmo quads that overlap are drawn in no
	 * particular order, so a translucent core and sparks inside the translucent sheath came out cut up
	 * differently from every angle (MOD-665). The setting governs the sheath and the role cubes.
	 */
	private static int core(int base) {
		return OverlayMath.lerpColor(base, 0xFFFFFFFF, CORE_WHITENESS);
	}

	/**
	 * How far a hop pulls back from {@code pos}: to the face of the cube drawn there, or not at all on a
	 * plain straight-through cable, where the next hop carries on wall to wall.
	 */
	private static double retract(BlockPos pos, boolean core) {
		if (!STATE.jointNodes().contains(pos)) {
			return 0.0;
		}
		if (STATE.isEndpoint(pos)) {
			return OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE;
		}
		return core ? OverlayGeometry.JOINT_CORE_HALF : OverlayGeometry.JOINT_SHEATH_HALF;
	}

	/** {@link #nodeCenter}, computed once per position per frame — {@code getShape} is not free (D13). */
	private static Vec3 center(ClientLevel level, BlockPos pos, Map<BlockPos, Vec3> centers) {
		return centers.computeIfAbsent(pos, p -> nodeCenter(level, p));
	}

	/**
	 * Where to anchor a node's tube/marker geometry, in world space. Horizontally this is always the
	 * block-cell centre: a cable's {@link VoxelShape} is a core plus an arm toward each connection (see
	 * {@code CableBlock}), so its {@code bounds()} centre is pulled sideways toward whichever arms it
	 * happens to have. The cell centre is on the block grid, so orthogonally-adjacent cables line up into
	 * perfectly straight axis-aligned tubes.
	 *
	 * <p>Vertically, endpoints follow their collision shape. A full-height machine (shape
	 * {@code maxY == 1.0}) is anchored at its vertical centre, so a tube meets it mid-face. A
	 * <b>half-block</b> endpoint (shape {@code maxY < 1.0}, e.g. a Solar Panel with {@code maxY == 0.5})
	 * is anchored {@link OverlayGeometry#ENDPOINT_JOINT_HALF_SIZE} <i>below</i> its top surface, so the
	 * endpoint joint cube sits flush with the slab's top face instead of poking half a cube above it
	 * (MOD-049), and the incoming tube dips down to the panel like the cable's own {@code arm_low} model
	 * (MOD-042). Cables always use the cell centre.
	 */
	private static Vec3 nodeCenter(ClientLevel level, BlockPos pos) {
		double y = pos.getY() + 0.5;
		if (STATE.isEndpoint(pos)) {
			VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
			if (!shape.isEmpty()) {
				AABB bounds = shape.bounds();
				y = pos.getY() + (bounds.maxY < 1.0
						? bounds.maxY - OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE
						: (bounds.minY + bounds.maxY) / 2.0);
			}
		}
		return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
	}

	private static void addJointCubes(GizmoPrimitives gizmos, List<BlockPos> positions, int color,
			ClientLevel level, Map<BlockPos, Vec3> centers) {
		for (BlockPos pos : positions) {
			if (level.isLoaded(pos) && STATE.jointNodes().contains(pos)) {
				OverlayGeometry.addJointCube(gizmos, center(level, pos, centers),
						OverlayGeometry.ENDPOINT_JOINT_HALF_SIZE, color);
			}
		}
	}
}
