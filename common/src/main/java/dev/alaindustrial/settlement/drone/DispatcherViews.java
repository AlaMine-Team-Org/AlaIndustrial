package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.ConstructionRow.Text;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHouses;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Works out a dispatcher's {@link DispatcherView} (MOD-858, MOD-860; owner's round 1; round 2 of 0.1.219, Q1–Q3): the
 * «Orders» tab's list — every building with work open on it, in the owner's order, each with one line on where it
 * stands — and, for the order the player picked there ({@link #target}), the «Task» tab's checklist with the notes of
 * its last step and the «Materials» tab's stage header and bill against what the buffer holds.
 *
 * <p><b>No silent wait.</b> Every order's line says why it does not move when it does not: a stop's reason, the road
 * it waits for, a plan the town lacks, or a drone it waits for.
 */
public final class DispatcherViews {

	private static final String PREFIX = "settlement.alaindustrial.dispatcher.";
	public static final String NO_JOB = PREFIX + "task.no_job";
	public static final String NO_STAGE = PREFIX + "materials.none";
	/** «%1$s: stage «Road», %2$s road blocks to go» — the road before a house, as the first stage (R1-5). */
	public static final String ROAD_STAGE = "settlement.alaindustrial.works.road_stage_line";
	/** «Waits for its road: the house is built after it» — a building job held back by its road job (R1-5). */
	public static final String WAITS_FOR_ROAD = "settlement.alaindustrial.works.wait.road";

	/** «No orders: pick a building in the core's «Construction» tab…» — nothing ordered, nothing left (Q1). */
	public static final String NO_ORDERS = PREFIX + "no_orders";
	/** «This house needs the house plan of type %s» — the plan step under a house whose plan the town lacks. */
	public static final String NEEDS_HOUSE_PLAN = PREFIX + "step.plan.house";
	/** «The town has learned this house's plan» — the plan step under a house of a known type (Q4). */
	public static final String HOUSE_PLAN_KNOWN = PREFIX + "step.plan.house_known";
	/** «%1$s: the town has not learned the house plan of type %2$s yet» — the «Materials» tab of such a house. */
	public static final String MATERIALS_NO_HOUSE_PLAN = PREFIX + "materials.no_house_plan";
	/** «The town hall's plan is not in the plan slot» — an order on the hall without its plan. */
	public static final String ORDER_NO_HALL_PLAN = PREFIX + "order.no_hall_plan";
	/** «%s: a drone is at work» — an order a drone holds. */
	public static final String ORDER_TAKEN = PREFIX + "order.taken";
	/** «%s: next for a free drone» — an order a drone takes on its next look. */
	public static final String ORDER_QUEUED = PREFIX + "order.queued";
	/** «%s: waits for a free drone — every drone is busy» — more orders than drones. */
	public static final String ORDER_WAITS_DRONE = PREFIX + "order.waits_drone";
	/** «%s: no drone — put a nest with a drone over the town hall» — no nest works. */
	public static final String ORDER_NO_DRONE = PREFIX + "order.no_drone";

	/** «Repair: %s» — an order that mends a building restored before (owner's round 3 of 0.1.219, R3-1). */
	public static final String ORDER_REPAIR = PREFIX + "order.repair";

	/**
	 * «%1$s stands at %3$s where the plan wants %2$s» — the hall is not restored, yet a start would raise nothing: the
	 * drones pass that block by (round 5 of 0.1.219). Drawn in red with its hint, as a job's stop is.
	 */
	public static final String PASSED_BY = "settlement.alaindustrial.works.reason.passed_by";
	/**
	 * «The order was called off. Press Start to go on» — the hall's order the owner cancelled: never placed again by
	 * the town (the player's intent), «Start» takes it up again (round 5 of 0.1.219).
	 */
	public static final String ORDER_CALLED_OFF = PREFIX + "task.called_off";

	/** Every key this class words, for the language gate's sake. */
	public static final List<String> KEYS = List.of(NO_JOB, NO_STAGE, ROAD_STAGE, WAITS_FOR_ROAD, NO_ORDERS,
			NEEDS_HOUSE_PLAN, HOUSE_PLAN_KNOWN, MATERIALS_NO_HOUSE_PLAN, ORDER_NO_HALL_PLAN, ORDER_TAKEN, ORDER_QUEUED,
			ORDER_WAITS_DRONE, ORDER_NO_DRONE, ORDER_REPAIR, PASSED_BY, PASSED_BY + BlockReason.HINT, ORDER_CALLED_OFF);

	private DispatcherViews() {
	}

	/**
	 * The building the «Task» and «Materials» tabs are about: order {@code selected} while it is one, else the first
	 * order, else — nothing ordered — the town hall. Empty for a town that knows no hall and has no order.
	 */
	public static OptionalInt target(TownWorks works, int selected) {
		List<Integer> orders = works.orders();
		if (orders.contains(selected)) {
			return OptionalInt.of(selected);
		}
		if (!orders.isEmpty()) {
			return OptionalInt.of(orders.getFirst());
		}
		return TownHall.index(works);
	}

	/** The view of {@code dispatcher}, serving {@code town} or none. */
	public static DispatcherView of(ServerLevel level, TownDispatcherBlockEntity dispatcher,
			Optional<Settlement> town) {
		if (town.isEmpty()) {
			return DispatcherView.EMPTY;
		}
		int id = town.get().id();
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(id);
		OptionalInt target = target(works, dispatcher.selected());
		if (target.isEmpty()) {
			return DispatcherView.EMPTY;
		}
		int index = target.getAsInt();
		TownBuilding building = works.buildings().get(index);
		boolean hall = TownHall.index(works).equals(OptionalInt.of(index));
		// Restored once is enough (owner's round 4): from then on the drones look after it by themselves, so «Start»
		// has nothing left to start.
		boolean restored = building.everRestored() || building.state() == BuildingState.INTACT
				|| building.state() == BuildingState.UPGRADED;
		List<Text> task = new ArrayList<>();
		boolean stalled = taskOf(works, index, task);
		boolean jobs = !task.isEmpty();
		boolean noOrders = works.orders().isEmpty();
		if (!jobs) {
			task.add(Text.of(hall && !restored ? calledOff(works, index) ? ORDER_CALLED_OFF : NO_JOB : NO_ORDERS));
		}
		boolean startable = hall && !restored && building.intact().isPresent()
				&& works.queue().open(WorkJobType.CLEAR, index).isEmpty()
				&& works.queue().open(WorkJobType.BUILD, index).isEmpty();
		String template = building.intact().map(Object::toString).orElse("");
		boolean house = TownHouses.isHouse(building);
		boolean planKnown = house && ConstructionDroneWork.planKnown(works, index, Optional.empty());
		String name = TownHouses.displayName(building);
		List<Text> materials = new ArrayList<>();
		List<DispatcherView.BillLine> bill = new ArrayList<>();
		List<Text> planNote = new ArrayList<>();
		if (house && !restored) {
			planNote.add(Text.of(planKnown ? HOUSE_PLAN_KNOWN : NEEDS_HOUSE_PLAN,
					String.valueOf(TownHouses.type(building))));
		}
		if (!jobs && (restored || noOrders && !hall)) {
			materials.add(Text.of(NO_ORDERS));
		} else if (house && !restored && !planKnown) {
			materials.add(Text.of(MATERIALS_NO_HOUSE_PLAN, name, String.valueOf(TownHouses.type(building))));
		} else {
			if (!restored && roadPending(level, works, id, index)) {
				roadOf(level, id, index, name, dispatcher.buffer(), materials, bill);
			}
			if (materials.isEmpty()) {
				materialsOf(level, id, index, name, dispatcher.buffer(), materials, bill);
			}
			foundationOf(level, id, index, house && !restored, dispatcher.buffer(), bill);
		}
		String wait = restored || jobs ? "" : waitFor(level, town.get(), works, index, startable, task);
		return new DispatcherView(task, materials,
				new DispatcherView.Checklist(true, restored, !bill.isEmpty(), jobs, startable, template, wait,
						stalled, planKnown),
				bill, planNote, orders(works, index, dispatcher));
	}

	/**
	 * What building {@code index} waits for before it may be started, a {@link StartReadiness} blocker's suffix, or
	 * empty: the town hall first, room under the house limit — or, for a {@code startable} building, nothing left
	 * for the drones (round 5 of 0.1.219: no «all ticked, Start does nothing»), whose reason goes into {@code task}.
	 */
	private static String waitFor(ServerLevel level, Settlement town, TownWorks works, int index, boolean startable,
			List<Text> task) {
		String wait = TownHouses.startBlocker(works, index, TownHall.index(works), town.tier().houseLimit()).orElse("");
		Optional<Text> passed = startable && wait.isEmpty() ? passedBy(level, town.id(), index) : Optional.empty();
		if (passed.isEmpty()) {
			return wait;
		}
		task.add(passed.get());
		task.add(Text.of(PASSED_BY + BlockReason.HINT));
		return StartReadiness.Blocker.NOTHING_LEFT.suffix();
	}

	/**
	 * When a start of building {@code index} would raise no job — nothing to clear, nothing a drone can build — while
	 * a block the drones never touch stands where the plan wants another: the line naming the first such block. Empty
	 * while there is work for the drones, or when no such block is found (the building's measure then catches up).
	 */
	private static Optional<Text> passedBy(ServerLevel level, int town, int index) {
		Optional<BuildingPlan> found = TownWorksScanner.plan(level, town, index);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		BuildingPlan plan = found.get();
		if (TownMaintenance.needsClearing(level, plan) || TownMaintenance.needsBuilding(level, plan)) {
			return Optional.empty();
		}
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState world = level.getBlockState(pos);
			DroneCellRules.Cell cell = DroneCellSurvey.survey(plan, i, world);
			if (!cell.matches() && cell.worldProtected()) {
				return Optional.of(Text.of(PASSED_BY, world.getBlock().getDescriptionId(),
						plan.expected(i).getBlock().getDescriptionId(),
						pos.getX() + " " + pos.getY() + " " + pos.getZ()));
			}
		}
		return Optional.empty();
	}

	/** Whether building {@code index}'s newest job was called off by the owner. */
	public static boolean calledOff(TownWorks works, int index) {
		WorkJob newest = null;
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() == index) {
				newest = job;
			}
		}
		return newest != null && newest.status() == WorkJobStatus.CANCELLED;
	}

	/** The «Orders» tab: every order, in the owner's order, with the line of where it stands. */
	private static List<DispatcherView.OrderLine> orders(TownWorks works, int shown,
			TownDispatcherBlockEntity dispatcher) {
		List<DispatcherView.OrderLine> out = new ArrayList<>();
		Optional<RestorationPlan> slot = dispatcher.plan();
		for (int index : works.orders()) {
			Optional<TownBuilding> building = works.building(index);
			if (building.isEmpty()) {
				continue;
			}
			Optional<WorkJob> current = current(works, index);
			if (current.isEmpty()) {
				continue;
			}
			WorkJob job = current.get();
			String type = ConstructionRows.JOB_TYPE + job.type().serializedName();
			boolean blocked = job.status() == WorkJobStatus.BLOCKED && job.reason().isPresent();
			Text step;
			if (blocked) {
				step = Text.of(job.reason().get());
			} else if (job.status() == WorkJobStatus.TAKEN) {
				step = Text.of(ORDER_TAKEN, type);
			} else if (!ConstructionDroneWork.planKnown(works, index, slot)) {
				blocked = true;
				step = TownHouses.isHouse(building.get())
						? Text.of(NEEDS_HOUSE_PLAN, String.valueOf(TownHouses.type(building.get())))
						: Text.of(ORDER_NO_HALL_PLAN);
			} else if (job.type() == WorkJobType.BUILD && works.queue().open(WorkJobType.ROAD, index).isPresent()) {
				step = Text.of(WAITS_FOR_ROAD);
			} else if (dispatcher.readyNests() <= 0) {
				blocked = true;
				step = Text.of(ORDER_NO_DRONE, type);
			} else if (dispatcher.busyNests() >= dispatcher.readyNests()) {
				step = Text.of(ORDER_WAITS_DRONE, type);
			} else {
				step = Text.of(ORDER_QUEUED, type);
			}
			String name = TownHouses.displayName(building.get());
			Text title = job.repair() ? Text.of(ORDER_REPAIR, name) : Text.of(name);
			out.add(new DispatcherView.OrderLine(index, title, step, blocked, job.status() == WorkJobStatus.TAKEN,
					index == shown));
		}
		return out;
	}

	/** The job an order is at: the one a drone holds, else its first open job in the order clear, road, build. */
	private static Optional<WorkJob> current(TownWorks works, int index) {
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() == index && job.status() == WorkJobStatus.TAKEN) {
				return Optional.of(job);
			}
		}
		for (WorkJobType type : List.of(WorkJobType.CLEAR, WorkJobType.ROAD, WorkJobType.BUILD)) {
			Optional<WorkJob> open = works.queue().open(type, index);
			if (open.isPresent()) {
				return open;
			}
		}
		return Optional.empty();
	}

	/**
	 * The lines of building {@code index}'s open jobs into {@code task}: each job, and under a stopped one its reason
	 * and what the player does about it (owner's round 1 of 0.1.219, R1-5); a building held back by its road says so.
	 * Whether a job stands stopped.
	 */
	private static boolean taskOf(TownWorks works, int index, List<Text> task) {
		boolean stalled = false;
		boolean roadOpen = works.queue().open(WorkJobType.ROAD, index).isPresent();
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() != index || !job.status().open()) {
				continue;
			}
			task.add(Text.of(ConstructionRows.JOB_LINE, ConstructionRows.JOB_TYPE + job.type().serializedName(),
					ConstructionRows.STATUS + job.status().serializedName()));
			if (job.reason().isPresent()) {
				stalled = true;
				task.add(Text.of(job.reason().get()));
				task.add(Text.of(job.reason().get().key() + BlockReason.HINT));
			} else if (job.type() == WorkJobType.BUILD && job.status() == WorkJobStatus.QUEUED && roadOpen) {
				task.add(Text.of(WAITS_FOR_ROAD));
			}
		}
		return stalled;
	}

	/**
	 * Whether building {@code index} still has its road to come before it is built (R1-5): an open road job, or a
	 * building job — or none yet — that will raise one.
	 */
	private static boolean roadPending(ServerLevel level, TownWorks works, int town, int index) {
		if (works.queue().open(WorkJobType.ROAD, index).isPresent()) {
			return true;
		}
		if (!TownHouses.isHouse(works.buildings().get(index))) {
			return false;
		}
		boolean started = works.queue().open(WorkJobType.CLEAR, index).isPresent()
				|| works.queue().open(WorkJobType.BUILD, index).isPresent();
		boolean before = !started && works.queue().jobs().stream().noneMatch(job -> job.building() == index);
		return (started || before) && RoadWork.needsRoad(level, town, index);
	}

	/**
	 * The road as the «Materials» tab's first stage (owner's round 1 of 0.1.219, R1-5): «House: stage Road, N
	 * road blocks to go», and its bill — the road blocks, and the earth or stone a raised stretch stands on.
	 * Nothing when the road has no course or takes nothing more.
	 */
	private static void roadOf(ServerLevel level, int town, int index, String name, DispatcherBuffer buffer,
			List<Text> header, List<DispatcherView.BillLine> bill) {
		RoadWork.Course course = RoadWork.course(level, town, index);
		if (course.problem().isPresent()) {
			return;
		}
		RoadWork.Bill left = RoadWork.bill(level, course.cells());
		if (left.empty()) {
			return;
		}
		header.add(Text.of(ROAD_STAGE, name, String.valueOf(left.roads())));
		if (left.roads() > 0) {
			bill.add(new DispatcherView.BillLine(RoadWork.ROAD_ITEM, left.roads(), buffer.count(RoadWork.ROAD_ITEM)));
		}
		if (left.fills() > 0) {
			bill.add(new DispatcherView.BillLine(RoadWork.FILL_ITEM, left.fills(), RoadWork.fillHeld(buffer)));
		}
	}

	/**
	 * The earth the pit under a house's floor still takes (owner's round 3 of 0.1.219, R3-5), on the bill's one
	 * «earth» line with the road's fill: any earth or stone of the fill tag answers it. Nothing unless {@code house}
	 * names a house still to restore.
	 */
	private static void foundationOf(ServerLevel level, int town, int index, boolean house, DispatcherBuffer buffer,
			List<DispatcherView.BillLine> bill) {
		int wanted = !house ? 0 : TownWorksScanner.plan(level, town, index)
				.map(plan -> DroneCellSurvey.foundationWanted(level, plan)).orElse(0);
		if (wanted <= 0) {
			return;
		}
		for (int i = 0; i < bill.size(); i++) {
			DispatcherView.BillLine line = bill.get(i);
			if (line.item().equals(RoadWork.FILL_ITEM)) {
				bill.set(i, new DispatcherView.BillLine(line.item(), line.need() + wanted, line.have()));
				return;
			}
		}
		bill.add(new DispatcherView.BillLine(RoadWork.FILL_ITEM, wanted, RoadWork.fillHeld(buffer)));
	}

	private static void materialsOf(ServerLevel level, int town, int building, String name, DispatcherBuffer buffer,
			List<Text> header, List<DispatcherView.BillLine> bill) {
		Optional<BuildStageRules.Progress> progress = BuildMaterials.forBuilding(level, town, building)
				.flatMap(staged -> staged.progress(level));
		Optional<BuildStageRules.StageProgress> current = progress.flatMap(BuildStageRules.Progress::current);
		if (current.isEmpty()) {
			header.add(Text.of(NO_STAGE));
			return;
		}
		header.add(Text.of("settlement.alaindustrial.works.stage_line", name,
				String.valueOf(progress.get().currentNumber()), String.valueOf(progress.get().count()),
				current.get().stage().key()));
		for (BuildStageRules.Need need : current.get().needed()) {
			bill.add(new DispatcherView.BillLine(need.item(), need.count(), buffer.count(need.item())));
		}
	}
}
