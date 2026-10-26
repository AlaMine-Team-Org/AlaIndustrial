package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>The warehouse</b> (MOD-892, R154 of MOD-824) is learned the same way: its plan lies in the warehouse ruin or
 * is crafted, it is a {@link dev.alaindustrial.item.misc.HousePlanItem} stack naming the warehouse's intact template,
 * and it goes into the same library. Every building that needs a learned plan is {@link #needsPlan}.
 *
 * <p>The hall's reward is worked out from the hall itself rather than stored, so a town restored before 0.1.219 knows
 * it too. A warehouse ordered or restored by a build that let it go without a plan (MOD-888, never released) is
 * taught at load instead ({@link #grandfathered}), so its order goes on.
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

	/**
	 * Whether {@code building} may be restored only once the town knows its plan: a capsule house or the warehouse
	 * (MOD-892, {@link PlanSheetRules#learned}). The town hall's plan lies in the dispatcher's slot instead.
	 */
	public static boolean needsPlan(TownBuilding building) {
		return TownHouses.isHouse(building)
				|| !building.withdrawn() && PlanSheetRules.WAREHOUSE.equals(lastSegment(building.template()));
	}

	/**
	 * Whether building {@code index} is a house or the warehouse whose plan the town does not know: it may not be
	 * ordered yet.
	 */
	public static boolean missing(TownWorks works, OptionalInt hall, int index) {
		Optional<TownBuilding> building = works.building(index);
		return building.isPresent() && needsPlan(building.get()) && !building.get().everRestored()
				&& building.get().intact().map(id -> !knows(works, hall, id)).orElse(true);
	}

	/**
	 * The plans a town of an older save is taught at load (MOD-892): under MOD-888 the warehouse needed no plan, so a
	 * warehouse with a job on it (open, done or called off) or restored once keeps going as if its plan were learned.
	 * A warehouse nobody touched needs its plan like a new one. Houses are never taught here: they always needed one.
	 * Today no warehouse gets a job without its plan, so for a current save this teaches nothing new.
	 *
	 * @param buildings the town's buildings, by index
	 * @param worked    the indexes of the buildings that have any job in the town's queue
	 */
	public static List<String> grandfathered(List<TownBuilding> buildings, Set<Integer> worked) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < buildings.size(); i++) {
			TownBuilding building = buildings.get(i);
			if (!TownHouses.isHouse(building) && needsPlan(building) && building.intact().isPresent()
					&& (building.everRestored() || worked.contains(i))) {
				out.add(building.intact().get().toString());
			}
		}
		return out;
	}

	private static String lastSegment(Identifier template) {
		String path = template.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
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
