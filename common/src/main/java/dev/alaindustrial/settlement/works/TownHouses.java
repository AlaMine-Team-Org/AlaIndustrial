package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Which of a town's buildings are houses, which count against the tier's house limit, and which a shift worker may
 * live in (MOD-862; epic MOD-824, decisions R5, R81, R86, R100, R110). Reads the town's records only — the world is
 * read by the scanner that writes them ({@link TownWorksScanner}).
 *
 * <p><b>A house</b> is a capsule house: its template's last path segment starts with {@value #HOUSE_PREFIX}.
 *
 * <p><b>Fit to live in</b> (R86, R100, R110): the last full pass found the house matching its intact template to the
 * share «restored» needs, with every key point in place — the door, the roof, the light, the plaque, the charging bed
 * and the house inlet — and the inlet wired to the core (R112, MOD-861: the scanner's «connection to the core»).
 * Whoever built it: the drone, or a player by hand.
 *
 * <p><b>Counted against the limit</b> (R110): a house with work open on it, or one restored once whose inlet still
 * stands. A ruin nobody touched does not count; a house whose inlet was broken is taken off the count. Of the counted
 * houses only the first {@code limit} in the town's order are lived in — a house a player finished by hand past the
 * limit stays empty.
 */
public final class TownHouses {

	/** The start of a capsule house's template name ({@code capsule_house_1} … {@code capsule_house_4}). */
	public static final String HOUSE_PREFIX = "capsule_house";
	/** The key point of the house inlet (R100, R110): its loss takes the house off the count. */
	public static final String INLET_KEY = "settlement.alaindustrial.building.key.inlet";

	private TownHouses() {
	}

	/** Whether {@code building} is a capsule house. */
	public static boolean isHouse(TownBuilding building) {
		if (building.withdrawn()) {
			return false; // a laid house called off counts as nothing (MOD-877)
		}
		String path = building.template().getPath();
		return path.substring(path.lastIndexOf('/') + 1).startsWith(HOUSE_PREFIX);
	}

	/** The start of the translation key of a house's name with its type (owner's round 2 of 0.1.219, R2-6). */
	public static final String TYPED_NAME_PREFIX = "settlement.alaindustrial.building.capsule_house.type_";

	/** The house type of {@code building} (1 to 4), or 0 for a building that is no capsule house. */
	public static int type(TownBuilding building) {
		if (!isHouse(building)) {
			return 0;
		}
		String path = building.intact().orElse(building.template()).getPath();
		return PlanSheetRules.houseType(path.substring(path.lastIndexOf('/') + 1));
	}

	/**
	 * The translation key a player reads for {@code building}: a house names its type — «Capsule house (type 3)» —
	 * so two houses of a town read apart (R2-6); any other building its own name.
	 */
	public static String displayName(TownBuilding building) {
		int type = type(building);
		if (type > 0) {
			return TYPED_NAME_PREFIX + type;
		}
		return building.name().isEmpty() ? ConstructionRows.UNNAMED : building.name();
	}

	/** Whether the last pass found {@code building} a house fit to live in (R86, R100). */
	public static boolean fitToLive(TownBuilding building) {
		return isHouse(building) && building.intact().isPresent() && building.measured()
				&& BuildingStateRules.meetsRestoration(new BuildingStateRules.Measure(building.matched(),
						building.total(), building.missingKeys()), BuildingStateRules.DEFAULT_RESTORED_AT);
	}

	/** Whether {@code building}'s inlet was found missing by the last pass. */
	public static boolean inletMissing(TownBuilding building) {
		return building.missingKeys().contains(INLET_KEY);
	}

	/** Whether house {@code index} of {@code works} counts against the house limit (R110). */
	public static boolean counted(TownWorks works, int index) {
		TownBuilding building = works.buildings().get(index);
		if (!isHouse(building)) {
			return false;
		}
		boolean jobs = works.queue().open(WorkJobType.CLEAR, index).isPresent()
				|| works.queue().open(WorkJobType.BUILD, index).isPresent()
				|| works.queue().open(WorkJobType.ROAD, index).isPresent(); // MOD-861: its road is under way
		return jobs || building.everRestored() && !inletMissing(building) || fitToLive(building);
	}

	/** How many houses of {@code works} count against the limit. */
	public static int countedHouses(TownWorks works) {
		int count = 0;
		for (int i = 0; i < works.buildings().size(); i++) {
			if (counted(works, i)) {
				count++;
			}
		}
		return count;
	}

	/** The houses a worker may live in: counted, within the first {@code limit} counted, and fit to live. */
	public static List<Integer> homes(TownWorks works, int limit) {
		List<Integer> out = new ArrayList<>();
		int counted = 0;
		for (int i = 0; i < works.buildings().size() && counted < limit; i++) {
			if (counted(works, i)) {
				counted++;
				if (fitToLive(works.buildings().get(i))) {
					out.add(i);
				}
			}
		}
		return out;
	}

	/**
	 * Why house {@code index} may not be put to work now, or empty when it may (R81, R110): the town hall goes first
	 * ({@link #HALL_FIRST}), and a house not counted yet needs room under the limit ({@link #LIMIT}).
	 */
	public static Optional<String> startBlocker(TownWorks works, int index, OptionalInt hall, int limit) {
		if (hall.isPresent() && hall.getAsInt() != index && !works.buildings().get(hall.getAsInt()).everRestored()) {
			return Optional.of(HALL_FIRST);
		}
		if (isHouse(works.buildings().get(index)) && !counted(works, index) && countedHouses(works) >= limit) {
			return Optional.of(LIMIT);
		}
		return Optional.empty();
	}

	/** The town hall is not restored yet (R81): the suffix of its line in the dispatcher and the core's tab. */
	public static final String HALL_FIRST = "hall_first";
	/** No room under the tier's house limit (R5, R110). */
	public static final String LIMIT = "house_limit";
}
