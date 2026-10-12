package dev.alaindustrial.client.render;

import dev.alaindustrial.core.world.ZonePreviewBlock;
import dev.alaindustrial.network.ZonePreviewPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

/**
 * Personal reach preview (MOD-278 for the Mob Repeller, generalised in MOD-802 for the Vacuum Collector):
 * a translucent cube with a bright wireframe marking exactly the zone the block works in, visible ONLY to
 * the player who pressed the preview button in the block's screen. Any {@link ZonePreviewBlock} can use it.
 *
 * <p>Not a {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}: a BER renders for
 * everybody and is culled against the block's own section, whereas this must be per-player and
 * spans up to 49 blocks. The per-frame gizmo path the Network Analyzer already uses gives both for
 * free — the loader adapters hand the same {@link SubmitNodeCollector} at the same frame point, and
 * only this client's map is ever drawn ({@link NetworkOverlayRenderer} class doc has the details).
 *
 * <p>State is a plain position→radius map, keyed like {@code MachineHumClientHook}'s per-position
 * client state. There is no expiry timer: an entry disappears when its block stops being a
 * {@link ZonePreviewBlock} (broken or replaced — an evolved repeller tier is still one), or when the
 * player changes dimension — all checked while drawing, so no tick hook is needed.
 */
public final class ZonePreviewRenderer {
	private ZonePreviewRenderer() {
	}

	/** Fill: soul-fire teal at low alpha — reads as a volume without hiding the world inside it. */
	private static final int FILL_COLOR = 0x2C4FD8DE;
	/** Wireframe: the same teal at near-full alpha, so the boundary cell is unambiguous. */
	private static final int EDGE_COLOR = 0xE64FD8DE;
	private static final float EDGE_WIDTH = 2.5f;

	/** Shown zones of THIS client: block position → reach from the block centre, in blocks. */
	private static final Map<BlockPos, Double> DOMES = new HashMap<>();
	/** Dimension the entries belong to; a change wipes them (positions mean nothing elsewhere). */
	private static ResourceKey<Level> dimension;
	/** The block whose screen is open right now — what the preview button reports its state for. */
	private static BlockPos openScreen;

	/**
	 * Handle a dome message. {@code toggle = true} (the player pressed the button) flips the dome for
	 * that block; {@code toggle = false} (the player opened the screen) only records which block the
	 * open screen belongs to. Called on the client thread by both loaders' receive seams.
	 */
	public static void receive(ZonePreviewPayload payload) {
		if (dimension != null && !dimension.equals(payload.dimension())) {
			DOMES.clear();
		}
		dimension = payload.dimension();
		BlockPos pos = payload.pos();
		openScreen = pos;
		if (payload.toggle() && DOMES.remove(pos) == null) {
			DOMES.put(pos, payload.reach());
		}
	}

	/** Whether the zone of the block whose screen is open is currently shown. */
	public static boolean isShownForOpenScreen() {
		return openScreen != null && DOMES.containsKey(openScreen);
	}

	/** Drop every dome (dimension change / disconnect). */
	public static void clear() {
		DOMES.clear();
		dimension = null;
		openScreen = null;
	}

	/** Per-frame submit, called by the loader adapters with the render-time collector. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		if (DOMES.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			clear();
			return;
		}
		if (dimension != null && !level.dimension().equals(dimension)) {
			clear();
			return;
		}
		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		boolean any = false;
		for (Iterator<Map.Entry<BlockPos, Double>> it = DOMES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Double> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!level.isLoaded(pos)) {
				continue; // chunk out of view: keep the entry, just skip this frame
			}
			if (!(level.getBlockState(pos).getBlock() instanceof ZonePreviewBlock)) {
				it.remove(); // the block is gone — the zone has nothing left to describe
				continue;
			}
			addDome(gizmos, pos, entry.getValue());
			any = true;
		}
		if (any) {
			gizmos.submit(collector, camera, false);
		}
	}

	/** One zone: the cube {@code reach} out from the block centre, drawn as filled quads plus a wireframe. */
	private static void addDome(DrawableGizmoPrimitives gizmos, BlockPos pos, double reach) {
		// The payload states the server's own zone (the repeller's `new AABB(pos).inflate(r)` is r + 0.5 from
		// the centre), so the cube is literally the zone — no cosmetic fudge, or the boundary would lie.
		AABB zone = AABB.ofSize(Vec3.atCenterOf(pos), reach * 2.0, reach * 2.0, reach * 2.0);
		OverlayGeometry.addBoxOutline(gizmos, zone, FILL_COLOR, EDGE_COLOR, EDGE_WIDTH);
	}
}
