package dev.alaindustrial.settlement.works;

import java.util.Locale;
import java.util.Optional;

/**
 * One stage of putting a building back (MOD-860, epic decision R84): the building's cells split into sets that close
 * as a whole, like the bundles of a farm game — the foundation, then the walls, the roof, the windows and doors, the
 * fittings. A builder works on one stage at a time, the first one with a cell still missing, and starts it only when
 * the dispatcher's buffer holds every item the stage still needs ({@link BuildStageRules}).
 *
 * <p>The order of the constants is the order of the work: what holds comes before what hangs on it — a wall torch
 * needs its wall, a carpet its floor. A save names a stage by {@link #serializedName()}, so a constant is never
 * renamed.
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it.
 */
public enum BuildStage {
	/** The bottom layer of the building: what everything else stands on. */
	FOUNDATION,
	/** The walls, the pillars and the inner floors: everything not sorted into another stage. */
	WALLS,
	/** The layers from the last full-width layer up. */
	ROOF,
	/** Doors, trapdoors, gates and glass. */
	OPENINGS,
	/** Lights, carpets, chests and the rest of the fittings. */
	INTERIOR;

	/** The name in data and in translation keys: {@code foundation}, {@code walls}, … */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The translation key of the stage's name. */
	public String key() {
		return "settlement.alaindustrial.works.stage." + serializedName();
	}

	/** The stage named {@code name}, if this build knows it. */
	public static Optional<BuildStage> byName(String name) {
		for (BuildStage stage : values()) {
			if (stage.serializedName().equals(name)) {
				return Optional.of(stage);
			}
		}
		return Optional.empty();
	}
}
