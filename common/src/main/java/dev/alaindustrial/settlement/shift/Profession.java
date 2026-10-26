package dev.alaindustrial.settlement.shift;

import java.util.Locale;
import java.util.Optional;

/**
 * A shift worker's trade (MOD-888; epic MOD-824, decisions R8, R150, R151): what its workplace makes of it. A worker
 * without a workplace has {@link #NONE} and lives by the town's routine without work (R150). The first wave of trades
 * (R8) comes one building at a time; this build has the storekeeper of the warehouse (R155).
 *
 * <p>Minecraft-free on purpose: the L1 lane reads it ({@code WorkplaceRulesTest}). The saved form is the serialized
 * name, read by {@code ShiftWorkerRecord}; an unknown name reads as {@link #NONE}.
 */
public enum Profession {
	/** No trade: the worker has no workplace. */
	NONE("", 0),
	/** The warehouse's storekeeper (R155): carries materials between the town's storage and the dispatcher. */
	STOREKEEPER("warehouse", 1);

	/** The start of a trade's translation key: {@code shift_worker.alaindustrial.profession.<name>}. */
	public static final String KEY_PREFIX = "shift_worker.alaindustrial.profession.";

	private final String building;
	private final int places;

	Profession(String building, int places) {
		this.building = building;
		this.places = places;
	}

	/** The name in saves and translation keys: {@code storekeeper}; empty for {@link #NONE}. */
	public String serializedName() {
		return this == NONE ? "" : name().toLowerCase(Locale.ROOT);
	}

	/** The translation key of the trade's name. */
	public String langKey() {
		return KEY_PREFIX + (this == NONE ? "none" : serializedName());
	}

	/**
	 * The last path segment of the template of the building this trade works in ({@code warehouse}); empty for
	 * {@link #NONE}.
	 */
	public String building() {
		return building;
	}

	/** How many workers one building of this trade takes (plan 0.1.220 B4: the warehouse 1; later by tier). */
	public int places() {
		return places;
	}

	/** The trade saved as {@code name}, or {@link #NONE} for an empty or unknown name. */
	public static Profession byName(String name) {
		for (Profession profession : values()) {
			if (profession != NONE && profession.serializedName().equals(name)) {
				return profession;
			}
		}
		return NONE;
	}

	/** The trade whose workplace is a building with template name {@code buildingName}, if any. */
	public static Optional<Profession> ofBuilding(String buildingName) {
		for (Profession profession : values()) {
			if (profession != NONE && profession.building.equals(buildingName)) {
				return Optional.of(profession);
			}
		}
		return Optional.empty();
	}
}
