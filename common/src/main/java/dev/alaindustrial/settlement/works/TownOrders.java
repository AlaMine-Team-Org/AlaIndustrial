package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownMaintenance;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;

/**
 * The town owner's orders (round 2 of 0.1.219, owner's answers Q1–Q3, Q7): «restore», «build», «resume» or «repair»
 * one building puts it in the town's order list with the jobs it needs — its clearing when there is debris, its road
 * when it is a house off the town's network, its building when a block is missing — and «cancel» calls them all off.
 * The drones take the orders in the owner's order ({@link WorkOrders}); a finished order leaves the list by itself.
 *
 * <p>The owner check is the caller's (R95). What the order may not be placed for is {@link #blocker}: the town hall
 * comes first (R81), a house needs room under the tier's limit (R110) and a plan the town knows (Q4).
 */
public final class TownOrders {

	/** Why a building cannot be ordered: a house whose plan the town has not learned (Q4). */
	public static final String NO_PLAN = "no_house_plan";
	/** Nothing to do: the building stands whole. */
	public static final String NOTHING = "nothing_to_do";

	private TownOrders() {
	}

	/** Whether building {@code index} is an order: it has an open job. */
	public static boolean ordered(TownWorks works, int index) {
		return works.queue().jobs().stream().anyMatch(job -> job.building() == index && job.status().open());
	}

	/** The button building {@code index}'s row shows, or empty for a building that takes none. */
	public static Optional<OrderButton> button(TownWorks works, int index) {
		Optional<TownBuilding> found = works.building(index);
		if (found.isEmpty() || found.get().withdrawn() || found.get().intact().isEmpty()) {
			return Optional.empty();
		}
		if (ordered(works, index)) {
			return Optional.of(OrderButton.CANCEL);
		}
		TownBuilding building = found.get();
		if (building.state() == BuildingState.INTACT || building.state() == BuildingState.UPGRADED) {
			return Optional.empty();
		}
		if (building.everRestored()) {
			return Optional.of(OrderButton.REPAIR);
		}
		boolean calledOff = works.queue().jobs().stream()
				.anyMatch(job -> job.building() == index && job.status() == WorkJobStatus.CANCELLED);
		if (calledOff) {
			return Optional.of(OrderButton.RESUME);
		}
		return Optional.of(building.isLaid() ? OrderButton.BUILD : OrderButton.RESTORE);
	}

	/**
	 * Why building {@code index} of {@code town} cannot be ordered now — a suffix of the dispatcher's «Start» lines
	 * ({@link TownHouses#HALL_FIRST}, {@link TownHouses#LIMIT}) or {@link #NO_PLAN} — or empty when it can.
	 */
	public static Optional<String> blocker(Settlement town, TownWorks works, int index) {
		OptionalInt hall = TownHall.index(works);
		Optional<String> wait = TownHouses.startBlocker(works, index, hall, town.tier().houseLimit());
		if (wait.isPresent()) {
			return wait;
		}
		return HousePlans.missing(works, hall, index) ? Optional.of(NO_PLAN) : Optional.empty();
	}

	/**
	 * Places the order of building {@code index}: raises the jobs it needs and puts it last in the order list.
	 * Returns why not, or empty when it was placed (or was an order already).
	 */
	public static Optional<String> place(ServerLevel level, Settlement town, int index, String who) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		if (ordered(works, index)) {
			return Optional.empty();
		}
		Optional<TownBuilding> building = works.building(index);
		if (building.isEmpty() || building.get().withdrawn() || building.get().intact().isEmpty()) {
			return Optional.of(NOTHING);
		}
		Optional<String> blocked = blocker(town, works, index);
		if (blocked.isPresent()) {
			return blocked;
		}
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town.id(), index);
		boolean clear = plan.map(p -> TownMaintenance.needsClearing(level, p)).orElse(true);
		boolean build = plan.map(p -> TownMaintenance.needsBuilding(level, p)).orElse(true);
		boolean road = TownHouses.isHouse(building.get()) && RoadWork.needsRoad(level, town.id(), index);
		if (!clear && !build && !road) {
			return Optional.of(NOTHING);
		}
		if (clear) {
			TownMaintenance.raiseByOwner(level, town.id(), index, WorkJobType.CLEAR);
		}
		if (road) {
			works.queue().raise(WorkJobType.ROAD, index);
		}
		if (build || road) {
			TownMaintenance.raiseByOwner(level, town.id(), index, WorkJobType.BUILD);
		}
		works.placeOrder(index);
		Industrialization.LOGGER.info("[ala-towns] settlement {}: {} ordered building {} ({})", town.id(), who, index,
				building.get().template());
		return Optional.empty();
	}

	/** Calls off order {@code index}: every open job on the building. The work done so far stays in the world. */
	public static void cancel(TownWorks works, int index) {
		for (WorkJob job : List.copyOf(works.queue().jobs())) {
			if (job.building() == index && job.status().open()) {
				works.queue().cancel(job.id());
			}
		}
	}
}
