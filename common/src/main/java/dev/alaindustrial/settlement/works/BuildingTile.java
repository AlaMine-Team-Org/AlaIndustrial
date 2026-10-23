package dev.alaindustrial.settlement.works;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What the core's «Construction» tab draws on a building's card besides its row (owner's round 3 of 0.1.219, R3-3):
 * the section the card goes into, the picture of the building, one status chip, who lives in it, and the open jobs of
 * its order — so a stage and a job read inside the card of their own building, not in a list of their own.
 *
 * <p>The card's words stay in the building's {@link ConstructionRow} (its {@code target} is {@link #index()}); its
 * stage is the row of {@code StageRows} with the same target; its jobs are the queue rows whose targets are
 * {@link #jobs()}.
 *
 * <p>Minecraft-free on purpose: the tab's data.
 *
 * @param index    the building's index in its town — the target of its row
 * @param icon     the picture: {@code <style>/<building>} as {@code tools/gen_town_building_icons.py} draws it, or
 *                 empty for a building it does not know
 * @param group    the section the card goes into
 * @param chip     the one word of the card's status
 * @param resident who lives in a house — «Resident: No. 7» or «Nobody lives here» — or empty for another building
 * @param jobs     the numbers of the open jobs on the building, first first
 */
public record BuildingTile(int index, String icon, Group group, Chip chip, Optional<ConstructionRow.Text> resident,
		List<Integer> jobs) {

	public BuildingTile {
		Objects.requireNonNull(icon, "icon");
		Objects.requireNonNull(group, "group");
		Objects.requireNonNull(chip, "chip");
		Objects.requireNonNull(resident, "resident");
		jobs = List.copyOf(jobs);
	}

	/** The section of the tab a card goes into. */
	public enum Group {
		HALL, HOUSE, OTHER;

		/** The translation key of the section's heading. */
		public String key() {
			return "settlement.alaindustrial.works.section." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** The status word of a card, and the colour the screen gives it. */
	public enum Chip {
		/** A ruin never restored. */
		RUIN,
		/** Restored once, damaged since: a repair is offered. */
		DAMAGED,
		/** A house whose plan the town has not learned. */
		NO_PLAN,
		/** May not be ordered yet: the town hall first, or no room under the house limit. */
		LOCKED,
		/** An order under way. */
		WORKING,
		/** An order that stands still: a stage short of materials or a blocked job. */
		WAITING,
		/** Whole. */
		DONE;

		/** The translation key of its word. */
		public String key() {
			return "settlement.alaindustrial.works.chip." + name().toLowerCase(Locale.ROOT);
		}

		/** The chip with this ordinal, or {@link #RUIN} — what the packet carries. */
		public static Chip byOrdinal(int ordinal) {
			Chip[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : RUIN;
		}
	}

	/** The section heading of the road network and of the plans. */
	public static final String SECTION_ROADS = "settlement.alaindustrial.works.section.roads";
	public static final String SECTION_PLANS = "settlement.alaindustrial.works.section.plans";

	/**
	 * The picture of a building whose template sits at {@code path} — {@code ala_town/plains/intact/capsule_house_2}
	 * reads {@code plains/capsule_house_2} — or empty for a template outside the towns' folder.
	 */
	public static String iconOf(String path) {
		String[] parts = path.split("/");
		if (parts.length < 3 || !parts[0].equals("ala_town")) {
			return "";
		}
		return parts[1] + "/" + parts[parts.length - 1];
	}
}
