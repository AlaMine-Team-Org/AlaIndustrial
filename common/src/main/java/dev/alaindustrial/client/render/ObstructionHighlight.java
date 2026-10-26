package dev.alaindustrial.client.render;

import dev.alaindustrial.network.TownObstructionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The block in the way of the town's work, blinking for a few seconds after «show it» in the core's «Construction»
 * tab (MOD-890): a red box with a bright edge around the cell the server named ({@link TownObstructionPayload}), seen
 * through walls, so the player finds what to take away — as «Find» shows a worker (R142).
 *
 * <p><b>Drawn through {@link FrameGizmos}</b>, always on top: the 26.3 frame path the piezo plates' x-ray and the
 * town's border take (a primitive list submitted straight from the hook never reaches the screen there, MOD-665). One
 * cell at a time: a new press moves the outline. It goes when its time is up, on a change of dimension, and when the
 * player leaves the world ({@code ClientDisconnectReset}).
 */
public final class ObstructionHighlight {

	/** Red: «this is what stops the work». */
	private static final int FILL = 0x40E0413A;
	private static final int EDGE = 0xF0FF5A50;
	private static final float EDGE_WIDTH = 3.0f;
	/** A blink: lit, then dim, this many milliseconds each. */
	private static final long BLINK_MILLIS = 400L;
	/** Dim half of a blink, as a share of the lit one. */
	private static final float DIM = 0.25f;
	/** The box stands a hair off the block's faces, so it is never flush with them. */
	private static final double INFLATE = 0.02;

	@Nullable
	private static BlockPos target;
	@Nullable
	private static ResourceKey<Level> dimension;
	private static long shownAt;
	private static long until;

	private ObstructionHighlight() {
	}

	/** The server's answer to «show it»: outline {@code payload.pos()} from now on. */
	public static void receive(TownObstructionPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		target = payload.pos().immutable();
		dimension = level.dimension();
		shownAt = level.getGameTime();
		until = shownAt + Math.max(1, payload.ticks());
	}

	/** Forgets the outline: the player left the world. */
	public static void clear() {
		target = null;
		dimension = null;
	}

	/** Per-frame entry: called by each loader's world-render hook next to the network overlay. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		BlockPos cell = target;
		ClientLevel level = Minecraft.getInstance().level;
		if (cell == null) {
			return;
		}
		if (level == null || !level.dimension().equals(dimension) || level.getGameTime() >= until
				|| level.getGameTime() < shownAt) {
			clear();
			return;
		}
		boolean lit = (System.currentTimeMillis() / BLINK_MILLIS) % 2 == 0;
		float alpha = lit ? 1.0f : DIM;
		AABB box = new AABB(cell).inflate(INFLATE);
		FrameGizmos.emit((gizmos, alphaMultiplier) -> OverlayGeometry.addBoxOutline(gizmos, box,
				faded(FILL, alpha), faded(EDGE, alpha), EDGE_WIDTH), true);
	}

	/** The same colour at {@code fade} of its alpha. */
	private static int faded(int argb, float fade) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * fade);
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
