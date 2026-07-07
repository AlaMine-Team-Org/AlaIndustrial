package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkTopology;
import dev.alaindustrial.network.NetworkTopology.FlowEdge;
import dev.alaindustrial.network.NetworkTopology.NetworkEdge;
import dev.alaindustrial.network.neoforge.NeoForgeNetworkClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForge Network-Analyzer highlight (MOD-022, the NeoForge counterpart to the Fabric
 * {@code NetworkVisualizationClient}). Draws the last {@link NetworkAnalyzerPayload} received
 * ({@link NeoForgeNetworkClient#latest()}) in world space so it reads through walls across chunks.
 *
 * <p><b>Approximation, by API constraint.</b> The Fabric version builds custom oriented-tube quads and
 * backface-wound joint cubes with a render-time {@code DrawableGizmoPrimitives}/{@code SubmitNodeCollector}
 * — a collector NeoForge's {@code RenderLevelStageEvent} does not expose. This uses the vanilla per-tick
 * gizmo API ({@link Minecraft#collectPerTickGizmos()} + {@link Gizmos}), which offers only high-level
 * shapes, so the topology is drawn as {@link Gizmos#line edge lines} + {@link Gizmos#cuboid node cubes} +
 * {@link Gizmos#point animated flow dots} rather than the Fabric tube look. Functionally equivalent (same
 * cables, endpoints and flow direction); visually simpler. Submitted once per client tick.
 *
 * <p>Topology is recomputed only when a new payload arrives (compared by reference), not every tick.
 */
public final class NeoForgeNetworkVisualization {

	private static final int TUBE_COLOR = 0xFF11577A;     // muted dark teal — edges
	private static final int PRODUCER_COLOR = 0xFF84CC16; // green
	private static final int CONSUMER_COLOR = 0xFFFB923C; // orange
	private static final int FLOW_COLOR = 0xFFFFE9A8;     // warm near-white flow spark

	private static final float EDGE_WIDTH = 4.0f;
	private static final float FLOW_POINT_SIZE = 0.5f;
	private static final float ENDPOINT_HALF = 0.16f;
	private static final int FLOW_DOTS_PER_EDGE = 3;
	private static final double FLOW_SPEED_EDGES_PER_SECOND = 1.0;

	private static NetworkAnalyzerPayload cachedPayload;
	private static ResourceKey<Level> dimension;
	private static List<BlockPos> producers = List.of();
	private static List<BlockPos> consumers = List.of();
	private static List<NetworkEdge> edges = List.of();
	private static List<FlowEdge> flowEdges = List.of();

	private NeoForgeNetworkVisualization() {
	}

	/** Submit the highlight for this client tick from the last received payload, if any. */
	public static void tick() {
		NetworkAnalyzerPayload payload = NeoForgeNetworkClient.latest();
		if (payload == null) {
			return;
		}
		if (payload != cachedPayload) {
			recompute(payload);
		}
		if (edges.isEmpty() && producers.isEmpty() && consumers.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || dimension == null || !level.dimension().equals(dimension)) {
			return;
		}

		try (Gizmos.TemporaryCollection collection = Minecraft.getInstance().collectPerTickGizmos()) {
			for (NetworkEdge edge : edges) {
				if (level.isLoaded(edge.a()) && level.isLoaded(edge.b())) {
					Gizmos.line(center(edge.a()), center(edge.b()), TUBE_COLOR, EDGE_WIDTH).setAlwaysOnTop();
				}
			}
			drawNodes(level, producers, PRODUCER_COLOR);
			drawNodes(level, consumers, CONSUMER_COLOR);

			double timeSeconds = System.currentTimeMillis() / 1000.0;
			for (FlowEdge flow : flowEdges) {
				if (!level.isLoaded(flow.from()) || !level.isLoaded(flow.to())) {
					continue;
				}
				Vec3 from = center(flow.from());
				Vec3 to = center(flow.to());
				for (int i = 0; i < FLOW_DOTS_PER_EDGE; i++) {
					double phase = (timeSeconds * FLOW_SPEED_EDGES_PER_SECOND + (double) i / FLOW_DOTS_PER_EDGE) % 1.0;
					Gizmos.point(from.lerp(to, phase), FLOW_COLOR, FLOW_POINT_SIZE).setAlwaysOnTop();
				}
			}
		}
	}

	private static void recompute(NetworkAnalyzerPayload payload) {
		cachedPayload = payload;
		dimension = payload.dimension();
		List<BlockPos> cables = payload.cables();
		producers = payload.producers();
		consumers = payload.consumers();
		edges = NetworkTopology.fullAdjacency(cables, producers, consumers);
		flowEdges = NetworkTopology.flowDirections(edges, producers);
	}

	private static void drawNodes(ClientLevel level, List<BlockPos> positions, int color) {
		GizmoStyle style = GizmoStyle.strokeAndFill(color, 2.0f, (color & 0x00FFFFFF) | 0x66000000);
		for (BlockPos pos : positions) {
			if (level.isLoaded(pos)) {
				Gizmos.cuboid(box(center(pos), ENDPOINT_HALF), style).setAlwaysOnTop();
			}
		}
	}

	private static Vec3 center(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private static AABB box(Vec3 c, float half) {
		return new AABB(c.x - half, c.y - half, c.z - half, c.x + half, c.y + half, c.z + half);
	}
}
