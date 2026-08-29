package dev.alaindustrial.advancement;

import org.jspecify.annotations.Nullable;

/**
 * The reactor steps the advancement branch marks (MOD-473).
 *
 * <p>Minecraft-free on purpose, so the name↔constant mapping the datapack depends on can be unit
 * tested on the L1 lane, which has no game on its classpath. The trigger that carries these values
 * lives next door in {@link ReactorMilestoneTrigger}.
 *
 * <p>The serialized names are datapack surface: they appear verbatim in
 * {@code data/alaindustrial/advancement/*.json}, so renaming one breaks every world that has not
 * earned that advancement yet.
 */
public enum ReactorMilestone {
	/** A controller's scan turned a shell into a sealed room for the first time. */
	ROOM_SEALED("room_sealed"),
	/** A reactor put its first EU into its own buffer. */
	POWER("power"),
	/** The coolant loop boiled its first water into steam. */
	STEAM("steam");

	private final String id;

	ReactorMilestone(String id) {
		this.id = id;
	}

	/** The name written in the datapack. */
	public String id() {
		return id;
	}

	/** The milestone with this datapack name, or {@code null} for a name no version ever wrote. */
	@Nullable
	public static ReactorMilestone byId(String id) {
		for (ReactorMilestone milestone : values()) {
			if (milestone.id.equals(id)) {
				return milestone;
			}
		}
		return null;
	}
}
