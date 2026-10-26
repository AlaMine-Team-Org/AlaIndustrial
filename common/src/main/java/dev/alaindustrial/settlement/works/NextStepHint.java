package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.settlement.drone.ConstructionDroneWork;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The one line at the top of the core's «Construction» tab that says what to do next (owner's round 3 of 0.1.219,
 * R3-6): worked out on the server from the town's state, first unmet step first — find the hall's plan sheets, put the
 * plan together in the dispatcher, set a nest with a drone, order the hall; then feed the stage that waits for
 * materials, order a house, look for house plans — until the town stands whole.
 */
public final class NextStepHint {

	private static final String KEY = "settlement.alaindustrial.works.next.";
	/** «Find the town hall's plan sheets: %s of 3 found…». */
	public static final String SHEETS = KEY + "sheets";
	/** No working dispatcher. */
	public static final String DISPATCHER = KEY + "dispatcher";
	/** The three sheets found, the hall's plan not in the dispatcher's plan slot. */
	public static final String ASSEMBLE = KEY + "assemble";
	/** No nest with a drone. */
	public static final String NEST = KEY + "nest";
	/** Everything ready: order the hall. */
	public static final String ORDER_HALL = KEY + "order_hall";
	/** An order short of materials: «…put into the dispatcher's buffer: %s» (joined). */
	public static final String MATERIALS = KEY + "materials";
	/** An order whose job is blocked: «Work on %s has stopped…». */
	public static final String STOPPED = KEY + "stopped";
	/** An order under way: «The drones are building %s…». */
	public static final String BUILDING = KEY + "building";
	/** A house may be ordered. */
	public static final String ORDER_HOUSE = KEY + "order_house";
	/** A building restored once and damaged since. */
	public static final String REPAIR = KEY + "repair";
	/** Houses whose plans the town does not know. */
	public static final String HOUSE_PLANS = KEY + "house_plans";
	/** The warehouse, whose plan the town does not know (MOD-892). */
	public static final String WAREHOUSE_PLAN = KEY + "warehouse_plan";
	/** No room under the tier's house limit. */
	public static final String LIMIT = KEY + "limit";
	/** Nothing left to do. */
	public static final String DONE = KEY + "done";

	/** Every key this class words. */
	public static final List<String> KEYS = List.of(SHEETS, DISPATCHER, ASSEMBLE, NEST, ORDER_HALL, MATERIALS, STOPPED,
			BUILDING, ORDER_HOUSE, REPAIR, HOUSE_PLANS, WAREHOUSE_PLAN, LIMIT, DONE);

	private NextStepHint() {
	}

	/** The next step of {@code context}'s town, or empty for a town that knows no building yet. */
	public static Optional<ConstructionRow.Text> of(ConstructionRows.Context context) {
		TownWorks works = context.works();
		if (!works.buildingsKnown() || works.buildings().isEmpty()) {
			return Optional.empty();
		}
		OptionalInt hall = TownHall.index(works);
		if (hall.isPresent() && !works.buildings().get(hall.getAsInt()).everRestored()
				&& !TownOrders.ordered(works, hall.getAsInt())) {
			return Optional.of(hallSteps(context, hall.getAsInt()));
		}
		for (int index : works.orders()) {
			Optional<ConstructionRow.Text> order = order(context, index);
			if (order.isPresent()) {
				return order;
			}
		}
		return Optional.of(afterHall(context));
	}

	/** The hall is neither restored nor ordered: what it still takes. */
	private static ConstructionRow.Text hallSteps(ConstructionRows.Context context, int hall) {
		TownWorks works = context.works();
		Optional<TownDispatcherBlockEntity> dispatcher = TownDispatcherBlockEntity.of(context.level(),
				context.town().id());
		boolean planInSlot = dispatcher.isPresent()
				&& ConstructionDroneWork.planKnown(works, hall, dispatcher.get().plan());
		if (!planInSlot && !context.town().style().isEmpty() && works.foundSheets() != PlanSheetRules.ALL_FOUND) {
			return ConstructionRow.Text.of(SHEETS, String.valueOf(Integer.bitCount(works.foundSheets())));
		}
		if (dispatcher.isEmpty()) {
			return ConstructionRow.Text.of(DISPATCHER);
		}
		if (!planInSlot) {
			return ConstructionRow.Text.of(ASSEMBLE);
		}
		if (dispatcher.get().readyNests() <= 0) {
			return ConstructionRow.Text.of(NEST);
		}
		return ConstructionRow.Text.of(ORDER_HALL);
	}

	/** What order {@code index} needs, if it stands still; else that it goes on. */
	private static Optional<ConstructionRow.Text> order(ConstructionRows.Context context, int index) {
		Optional<TownBuilding> building = context.works().building(index);
		if (building.isEmpty()) {
			return Optional.empty();
		}
		String name = TownHouses.displayName(building.get());
		Optional<TownDispatcherBlockEntity> dispatcher = TownDispatcherBlockEntity.of(context.level(),
				context.town().id());
		if (dispatcher.isEmpty()) {
			return Optional.of(ConstructionRow.Text.of(DISPATCHER));
		}
		if (!ConstructionDroneWork.planKnown(context.works(), index, dispatcher.get().plan())) {
			return Optional.of(ConstructionRow.Text.of(TownHouses.isHouse(building.get()) ? HOUSE_PLANS
					: HousePlans.needsPlan(building.get()) ? WAREHOUSE_PLAN : ASSEMBLE));
		}
		if (dispatcher.get().readyNests() <= 0) {
			return Optional.of(ConstructionRow.Text.of(NEST));
		}
		List<BuildStageRules.Need> missing = BuildingTiles.shortage(context, index);
		if (!missing.isEmpty()) {
			BlockReason reason = BuildMaterials.reason(missing);
			return Optional.of(new ConstructionRow.Text(MATERIALS, reason.args(), true));
		}
		boolean blocked = context.works().queue().jobs().stream()
				.anyMatch(job -> job.building() == index && job.status() == WorkJobStatus.BLOCKED);
		return Optional.of(ConstructionRow.Text.of(blocked ? STOPPED : BUILDING, name));
	}

	/** The hall stands (or the town has none): the houses and the repairs. */
	private static ConstructionRow.Text afterHall(ConstructionRows.Context context) {
		TownWorks works = context.works();
		List<String> waits = new ArrayList<>();
		boolean house = false;
		boolean repair = false;
		boolean warehousePlan = false;
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			Optional<OrderButton> button = TownOrders.button(works, i);
			if (building.withdrawn() || button.isEmpty() || button.get() == OrderButton.CANCEL) {
				continue;
			}
			Optional<String> wait = TownOrders.blocker(context.town(), works, i);
			if (wait.isPresent() && TownOrders.NO_PLAN.equals(wait.get()) && !TownHouses.isHouse(building)) {
				warehousePlan = true; // MOD-892: the warehouse's plan, not a house's
			} else if (wait.isPresent()) {
				waits.add(wait.get());
			} else if (button.get() == OrderButton.REPAIR) {
				repair = true;
			} else {
				house = true;
			}
		}
		if (repair) {
			return ConstructionRow.Text.of(REPAIR);
		}
		if (house) {
			return ConstructionRow.Text.of(ORDER_HOUSE);
		}
		if (waits.contains(TownOrders.NO_PLAN)) {
			return ConstructionRow.Text.of(HOUSE_PLANS);
		}
		if (warehousePlan) {
			return ConstructionRow.Text.of(WAREHOUSE_PLAN);
		}
		if (waits.contains(TownHouses.LIMIT)) {
			return ConstructionRow.Text.of(LIMIT);
		}
		return ConstructionRow.Text.of(DONE);
	}
}
