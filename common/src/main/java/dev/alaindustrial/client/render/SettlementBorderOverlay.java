package dev.alaindustrial.client.render;

import dev.alaindustrial.block.SettlementCoreBlock;
import dev.alaindustrial.network.SettlementBorderPayload;
import dev.alaindustrial.settlement.SettlementShell;
import dev.alaindustrial.settlement.SettlementTerritory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's border, shown to the one player who pressed "show borders" in the core's screen (MOD-843): one shell
 * of walls and a dome over the town's territory — from outside a translucent shell with a grid, walls, floor and dome;
 * from inside only the grid of the walls and the dome, nothing filled and no floor, so the edge reads through the
 * town (MOD-848; {@link SettlementShell} has the shape and why it is that shape).
 *
 * <p><b>Personal, client-only state.</b> The server answers a press with the town's core and territory
 * ({@link SettlementBorderPayload}) and keeps nothing; which borders this client shows lives here, keyed by the
 * town's core position. Nothing is shown to anyone else, because nobody else's client holds the entry.
 *
 * <p><b>Drawn through {@link FrameGizmos}</b>, the 26.3 frame path (a primitive list submitted from the hook never
 * reaches the screen there, MOD-665), always on top: the floor lies under the ground and the far walls behind
 * hills, and the point of the shell is to see where the town ends through all of it. Every quad is emitted with
 * both windings so a face shows from either side.
 *
 * <p>An entry goes when its button is pressed again, when the town's core is gone from its loaded chunk, on a
 * change of dimension, and when the player leaves the world ({@code ClientDisconnectReset}).
 */
public final class SettlementBorderOverlay {
	/** Mint at a low alpha: reads as a pane of glass, not as fog, even three layers deep. */
	private static final int FILL = 0x1A5FE0B8;
	/** The grid: the same mint, strong, so the edge of the town is a line and not a tint. */
	private static final int EDGE = 0xB45FE0B8;
	private static final float EDGE_WIDTH = 1.5f;
	/** Past this many blocks from a town's territory its shell is not drawn: it would be a speck. */
	private static final double DRAW_DISTANCE = 512.0;

	/** Shown borders of THIS client: town core position → its territory. */
	private static final Map<BlockPos, SettlementTerritory.Area> SHOWN = new HashMap<>();
	@Nullable
	private static ResourceKey<Level> dimension;
	/** The town core of the screen open right now, or null; the button's state is read against it. */
	@Nullable
	private static BlockPos openCore;

	private SettlementBorderOverlay() {
	}

	/**
	 * Handle a border message: a press ({@code toggle}) flips the border of that town, an opened screen only records
	 * which town the screen belongs to. A core of no town brings no territory: nothing to show, and the button stays
	 * off.
	 */
	public static void receive(SettlementBorderPayload payload) {
		if (dimension != null && !dimension.equals(payload.dimension())) {
			SHOWN.clear();
		}
		dimension = payload.dimension();
		if (payload.territory().isEmpty()) {
			openCore = null;
			return;
		}
		BlockPos core = payload.core().immutable();
		openCore = core;
		if (payload.toggle() && SHOWN.remove(core) == null) {
			SHOWN.put(core, payload.territory().get());
		}
	}

	/** Whether the border of the town whose core screen is open is shown. */
	public static boolean isShownForOpenScreen() {
		return openCore != null && SHOWN.containsKey(openCore);
	}

	/** Drop every border (dimension change, leaving the world). */
	public static void clear() {
		SHOWN.clear();
		dimension = null;
		openCore = null;
	}

	/** Per-frame entry: called by each loader's world-render hook next to the network overlay. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		if (SHOWN.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || (dimension != null && !level.dimension().equals(dimension))) {
			clear();
			return;
		}
		Vec3 eye = camera.pos;
		Map<BlockPos, SettlementTerritory.Area> frame = new HashMap<>();
		for (Iterator<Map.Entry<BlockPos, SettlementTerritory.Area>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, SettlementTerritory.Area> entry = it.next();
			BlockPos core = entry.getKey();
			if (level.isLoaded(core) && !(level.getBlockState(core).getBlock() instanceof SettlementCoreBlock)) {
				it.remove(); // the town's core is gone: the border has nothing left to describe
				continue;
			}
			if (eye == null || horizontalDistance(eye, entry.getValue()) <= DRAW_DISTANCE) {
				frame.put(core, entry.getValue());
			}
		}
		if (frame.isEmpty() || eye == null) {
			return;
		}
		FrameGizmos.emit((gizmos, alphaMultiplier) -> {
			for (Map.Entry<BlockPos, SettlementTerritory.Area> entry : frame.entrySet()) {
				SettlementShell.Box box = SettlementShell.box(entry.getValue(), entry.getKey().getY());
				SettlementShell.emit(box, eye.x, eye.y, eye.z, new GizmoSink(gizmos));
			}
		}, true);
	}

	/** How far the eye is from the territory along the farther axis; 0 inside its columns. */
	private static double horizontalDistance(Vec3 eye, SettlementTerritory.Area area) {
		double dx = Math.max(0.0, Math.max(area.minX() - eye.x, eye.x - (area.maxX() + 1.0)));
		double dz = Math.max(0.0, Math.max(area.minZ() - eye.z, eye.z - (area.maxZ() + 1.0)));
		return Math.max(dx, dz);
	}

	/** The shell's quads and lines as gizmos; each quad twice, once per winding, so either side shows. */
	private record GizmoSink(GizmoPrimitives gizmos) implements SettlementShell.Sink {
		@Override
		public void quad(SettlementShell.Point a, SettlementShell.Point b, SettlementShell.Point c,
				SettlementShell.Point d) {
			Vec3 va = vec(a);
			Vec3 vb = vec(b);
			Vec3 vc = vec(c);
			Vec3 vd = vec(d);
			gizmos.addQuad(va, vb, vc, vd, FILL);
			gizmos.addQuad(vd, vc, vb, va, FILL);
		}

		@Override
		public void line(SettlementShell.Point a, SettlementShell.Point b) {
			gizmos.addLine(vec(a), vec(b), EDGE, EDGE_WIDTH);
		}

		private static Vec3 vec(SettlementShell.Point p) {
			return new Vec3(p.x(), p.y(), p.z());
		}
	}
}
