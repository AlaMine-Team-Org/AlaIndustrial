package dev.alaindustrial.client.blueprint;

import java.util.HashSet;
import java.util.Set;

/**
 * Which blueprints this player has opened (MOD-797) — drives the "NEW" stamp in the tooltip. First
 * delivery keeps it for the session only; the server-side record that survives restarts and feeds the
 * advancement is the task's third delivery.
 */
public final class BlueprintProgress {

	private static final Set<String> VIEWED = new HashSet<>();

	private BlueprintProgress() {
	}

	/** Record that the blueprint {@code id} was opened. */
	public static void markViewed(String id) {
		VIEWED.add(id);
	}

	/** Whether the blueprint {@code id} has been opened. */
	public static boolean isViewed(String id) {
		return VIEWED.contains(id);
	}
}
