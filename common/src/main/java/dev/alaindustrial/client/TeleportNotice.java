package dev.alaindustrial.client;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The last refusal the server sent, for the remote's screen to show (MOD-093).
 *
 * <p>A static holder rather than something handed to the screen, because the receiver has no way to
 * reach the open screen instance on either loader — the same shape the mod's other client-side
 * payload state uses.
 *
 * <p>It expires on its own after {@link #SHOW_MS}. That is deliberate: a line explaining why a jump
 * was refused must not still be sitting there once the player has fixed the problem, and nothing has
 * to remember to clear it.
 */
public final class TeleportNotice {

	/** How long a refusal stays on screen — long enough to read, short enough not to lie. */
	private static final long SHOW_MS = 5000;

	@Nullable
	private static volatile Component message;
	private static volatile long stampMs;

	private TeleportNotice() {
	}

	/** A refusal from the server. Called on the client main thread by each loader's receiver. */
	public static void receive(Component reason) {
		message = reason;
		stampMs = System.currentTimeMillis();
	}

	/** The line to draw, or null if there is nothing to say (or it has gone stale). */
	@Nullable
	public static Component current() {
		Component current = message;
		if (current == null || System.currentTimeMillis() - stampMs > SHOW_MS) {
			return null;
		}
		return current;
	}

	/** Drop it — used when the screen opens, so a stale refusal never greets a fresh screen. */
	public static void clear() {
		message = null;
		stampMs = 0L;
	}
}
