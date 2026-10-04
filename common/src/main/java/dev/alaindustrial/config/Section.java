package dev.alaindustrial.config;

/**
 * Thematic group a tunable belongs to — and, on disk, the JSON object its key lives inside (MOD-402).
 *
 * <p>The section is declared on the knob's own {@link Knob} annotation, right next to its doc string.
 * That is the point: a second "key → section" table would be a parallel list to keep in sync by hand,
 * which is the exact drift class this repo has already had to build gates against.
 *
 * <p>Declaration order below is the order the sections render in the file.
 */
public enum Section {
	GLOBAL("global", "Server-wide multipliers applied on top of everything else."),
	GENERATORS("generators", "Generator output and the environment that scales it (sky, height, weather),"
			+ " plus rotor/wheel consumables and generator EU buffers."),
	MACHINES("machines", "Processing machines: EU/t, operation durations, internal buffers, overclocker"
			+ " chips, the incubator mutation table, the condenser and the drone station."),
	STORAGE("storage", "EU stores: battery box, reinforced storage, charging station, and the channel"
			+ " that feeds a non-cascade sink from a store."),
	CABLES("cables", "Cable grades: per-segment buffer (which is also the segment throughput), packet"
			+ " caps and per-block loss."),
	SAFETY("safety", "Bare-cable shock hazard and the insulating stands that soften it."),
	NETWORK("network", "Voltage tiers, buffer capacities per tier, and per-tick network budgets."),
	TOOLS("tools", "Powered items the player carries or wears: buffers, charge rates and running costs."),
	LOGISTICS("logistics", "Moving things around: item and fluid pipes, the pump, portable tanks,"
			+ " the teleporter and the stock display frame."),
	PLAYER("player", "Mod XP, the player profile curve, and what each Workstation skill is worth."),
	WORLD("world", "World behaviour: bonus chest injection, burning oil, crop growth.");

	/** Json key of the section object. */
	public final String id;
	/** One-line note written as {@code _comment_<id>} above the section. */
	public final String doc;

	Section(String id, String doc) {
		this.id = id;
		this.doc = doc;
	}
}
