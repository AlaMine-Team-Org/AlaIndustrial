package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;

/**
 * The house plans a town knows (round 2 of 0.1.219, owner's answers Q4 and Q5): the plans its restorer let it learn —
 * a house plan put into the dispatcher's plan slot or onto the core is used up and its type is known for good — and,
 * once its town hall has been restored, the plainest house of its style, type 1, as the hall's reward. A house of a
 * known type may be ordered, a ruin or a new one; a house plan is never a «current target».
 *
 * <p>The hall's reward is worked out from the hall itself rather than stored, so a town restored before 0.1.219 knows
 * it too.
 */
public final class HousePlans {

	/** The house type the restored hall teaches (Q5): the plainest house of the style. */
	public static final int HALL_REWARD_TYPE = 1;

	private HousePlans() {
	}

	/** Every house plan town {@code works} knows, by intact template id, the hall's reward included. */
	public static Set<String> known(TownWorks works, OptionalInt hall) {
		Set<String> out = new TreeSet<>(works.learnedHousePlans());
		hallReward(works, hall).ifPresent(id -> out.add(id.toString()));
		return out;
	}

	/** Whether town {@code works} knows the plan of the house whose intact template is {@code intact}. */
	public static boolean knows(TownWorks works, OptionalInt hall, Identifier intact) {
		return known(works, hall).contains(intact.toString());
	}

	/** Whether building {@code index} is a house whose plan the town does not know: it may not be ordered yet. */
	public static boolean missing(TownWorks works, OptionalInt hall, int index) {
		Optional<TownBuilding> building = works.building(index);
		return building.isPresent() && TownHouses.isHouse(building.get()) && !building.get().everRestored()
				&& building.get().intact().map(id -> !knows(works, hall, id)).orElse(true);
	}

	/** The intact template of the house the restored hall teaches, if the hall has been restored. */
	public static Optional<Identifier> hallReward(TownWorks works, OptionalInt hall) {
		if (hall.isEmpty()) {
			return Optional.empty();
		}
		TownBuilding building = works.buildings().get(hall.getAsInt());
		if (!building.everRestored() || building.intact().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(sibling(building.intact().get(), PlanSheetRules.house(HALL_REWARD_TYPE)));
	}

	/** The template next to {@code template} named {@code name}: the same style's folder, another building. */
	public static Identifier sibling(Identifier template, String name) {
		String path = template.getPath();
		return Identifier.fromNamespaceAndPath(template.getNamespace(), path.substring(0, path.lastIndexOf('/') + 1)
				+ name);
	}
}
