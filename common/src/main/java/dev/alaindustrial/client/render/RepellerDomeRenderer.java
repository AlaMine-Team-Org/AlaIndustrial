package dev.alaindustrial.client.render;

import dev.alaindustrial.block.AbstractMobRepellerBlock;
import dev.alaindustrial.network.RepellerDomePayload;
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
 * Personal radius dome for the Mob Repeller (MOD-278): a translucent cube with a bright wireframe
 * marking exactly which cells the field covers, visible ONLY to the player who pressed the dome
 * button in the block's screen.
 *
 * <p>Not a {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}: a BER renders for
 * everybody and is culled against the block's own section, whereas this must be per-player and
 * spans up to 49 blocks. The per-frame gizmo path the Network Analyzer already uses gives both for
 * free — the loader adapters hand the same {@link SubmitNodeCollector} at the same frame point, and
 * only this client's map is ever drawn ({@link NetworkOverlayRenderer} class doc has the details).
 *
 * <p>State is a plain position→radius map, keyed like {@code MachineHumClientHook}'s per-position
 * client state. There is no expiry timer: an entry disappears when its block stops being a repeller
 * (broken, or evolved into a tier the player then re-checks), when its chunk unloads, or when the
 * player changes dimension — all checked while drawing, so no tick hook is needed.
 */
public final class RepellerDomeRenderer {
	private RepellerDomeRenderer() {
	}

	/** Fill: soul-fire teal at low alpha — reads as a volume without hiding the world inside it. */
	private static final int FILL_COLOR = 0x2C4FD8DE;
	/** Wireframe: the same teal at near-full alpha, so the boundary cell is unambiguous. */
	private static final int EDGE_COLOR = 0xE64FD8DE;
	private static final float EDGE_WIDTH = 2.5f;

	/** Shown domes of THIS client: block position → field radius in blocks. */
	private static final Map<BlockPos, Integer> DOMES = new HashMap<>();
	/** Dimension the entries belong to; a change wipes them (positions mean nothing elsewhere). */
	private static ResourceKey<Level> dimension;
	/** The repeller whose screen is open right now — what the dome button reports its state for. */
	private static BlockPos openScreen;

	/**
	 * Handle a dome message. {@code toggle = true} (the player pressed the button) flips the dome for
	 * that block; {@code toggle = false} (the player opened the screen) only records which block the
	 * open screen belongs to. Called on the client thread by both loaders' receive seams.
	 */
	public static void receive(RepellerDomePayload payload) {
		if (dimension != null && !dimension.equals(payload.dimension())) {
			DOMES.clear();
		}
		dimension = payload.dimension();
		BlockPos pos = payload.pos();
		openScreen = pos;
		if (payload.toggle() && DOMES.remove(pos) == null) {
			DOMES.put(pos, payload.radius());
		}
	}

	/** Whether this client currently shows a dome for {@code pos} — the screen's button state. */
	public static boolean isShown(BlockPos pos) {
		return DOMES.containsKey(pos);
	}

	/** Whether the dome of the repeller whose screen is open is currently shown. */
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
		for (Iterator<Map.Entry<BlockPos, Integer>> it = DOMES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Integer> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!level.isLoaded(pos)) {
				continue; // chunk out of view: keep the entry, just skip this frame
			}
			if (!(level.getBlockState(pos).getBlock() instanceof AbstractMobRepellerBlock)) {
				it.remove(); // the block is gone — the dome has nothing left to describe
				continue;
			}
			addDome(gizmos, pos, entry.getValue());
			any = true;
		}
		if (any) {
			gizmos.submit(collector, camera, false);
		}
	}

	/** One dome: the field cube, drawn as filled quads plus a wireframe on the same bounds. */
	private static void addDome(DrawableGizmoPrimitives gizmos, BlockPos pos, int radius) {
		// The field is `new AABB(pos).inflate(radius)` on the server, so the dome is literally the
		// zone — no cosmetic fudge, or the boundary would lie about where mobs are allowed.
		AABB zone = new AABB(pos).inflate(radius);
		OverlayGeometry.addBoxOutline(gizmos, zone, FILL_COLOR, EDGE_COLOR, EDGE_WIDTH);
	}

	/** The dome's centre in world space — exposed for tests/debug overlays. */
	public static Vec3 centerOf(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}
}
