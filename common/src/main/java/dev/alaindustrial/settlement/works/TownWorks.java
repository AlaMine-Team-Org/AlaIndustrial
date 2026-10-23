package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The buildings and the work queue of one ALA town (MOD-857, epic decision R79), kept by its core's record in
 * {@link SettlementWorks}. The building list is the town's own memory of its structure — written once, when the town
 * is registered or, for a town of an older save, when its core first finds the structure — and every building's state
 * is the last finding of the town's scanner ({@link TownWorksScanner}); the queue is what the town's executors work
 * on.
 *
 * <p><b>The queue moves the states.</b> Every change of the queue re-derives the buildings' states that only depend on
 * their jobs ({@link BuildingStateRules#betweenPasses}): raising a clearing job shows the ruin as being cleared at
 * once, not after the next pass.
 *
 * <p>Server thread only. Every change calls the hook the book installs, which marks the towns' saved data dirty.
 */
public final class TownWorks {

	private final List<TownBuilding> buildings = new ArrayList<>();
	/** Whether the building list was ever filled — a town no structure built stays without one. */
	private boolean buildingsKnown;
	private final WorkQueue queue;
	/**
	 * The town hall plan sheets of the town's style the town has seen in its dispatcher (MOD-859): one bit per sheet
	 * number, bit 0 for sheet 1. Only grows.
	 */
	private int foundSheets;
	/**
	 * The town's road network (round 2 of 0.1.219, R2-7; R128–R132): one tree of road cells from the hall's inlet to
	 * every building's, each marked laid or not. Saved, so a road half laid keeps its course over a reload, and a laid
	 * stretch is never traced again.
	 */
	private final TownRoadPlan roadPlan;
	/**
	 * The owner's order of the town's orders (round 2 of 0.1.219, Q3), by building index; what is no order any more is
	 * dropped on every change of the queue ({@link WorkOrders#effective}). Saved.
	 */
	private final List<Integer> orderList = new ArrayList<>();
	/**
	 * The house plans the town has learned (round 2 of 0.1.219, Q4): the intact templates, by id. A sheet put into the
	 * dispatcher or onto the core is used up and its type stays known. Only grows. Saved.
	 */
	private final Set<String> housePlans = new TreeSet<>();
	private Runnable onChange = () -> { };

	// --- per-load state of the scanner and its plans; never saved ---
	/** The plans of the buildings, by index; an absent key — not built yet, a present empty — nothing to measure. */
	private final Map<Integer, Optional<BuildingPlan>> plans = new HashMap<>();
	/** Whether the core already looked for the town's structure on this load. */
	private boolean structureAsked;
	/** The building the scanner measures, and how far its pass got. */
	int scanBuilding;
	int scanCursor;
	int scanMatched;
	int scanFailedKeys;
	/** The game time before which a road network that left buildings out is not grown again; 0 — any time. */
	private long roadRetryAt;

	/** An empty town: no building known, nothing queued. */
	public TownWorks() {
		this(List.of(), false, new WorkQueue(), 0);
	}

	TownWorks(List<TownBuilding> saved, boolean known, WorkQueue queue, int foundSheets) {
		this(saved, known, queue, foundSheets, new TownRoadPlan());
	}

	TownWorks(List<TownBuilding> saved, boolean known, WorkQueue queue, int foundSheets, TownRoadPlan roads) {
		this(saved, known, queue, foundSheets, roads, List.of(), List.of());
	}

	TownWorks(List<TownBuilding> saved, boolean known, WorkQueue queue, int foundSheets, TownRoadPlan roads,
			List<Integer> orders, List<String> plans) {
		this.buildings.addAll(saved);
		this.roadPlan = roads;
		this.roadPlan.onChange(() -> onChange.run());
		this.foundSheets = foundSheets & PlanSheetRules.ALL_FOUND;
		this.buildingsKnown = known || !saved.isEmpty();
		this.queue = queue;
		this.orderList.addAll(WorkOrders.effective(orders, queue.jobs()).stream().filter(orders::contains).toList());
		this.housePlans.addAll(plans);
		this.queue.onChange(() -> {
			refreshStates();
			pruneOrders();
			onChange.run();
		});
	}

	// --- orders (round 2 of 0.1.219, Q1–Q3) ---

	/** The town's orders, first first: every building with an open job, in the owner's order. */
	public List<Integer> orders() {
		return WorkOrders.effective(orderList, queue.jobs());
	}

	/** Where building {@code building} stands among the orders, lower first; {@link WorkOrders#NO_ORDER} if none. */
	public int orderRank(int building) {
		return WorkOrders.rank(orders(), building);
	}

	/** Puts building {@code index} last among the orders, unless it is one already; its jobs are the caller's. */
	public void placeOrder(int index) {
		List<Integer> now = orders();
		if (!orderList.contains(index)) {
			orderList.clear();
			orderList.addAll(now);
			orderList.remove(Integer.valueOf(index));
			orderList.add(index);
			onChange.run();
		}
	}

	/** Moves order {@code index} {@code delta} places, negative — earlier. */
	public void moveOrder(int index, int delta) {
		List<Integer> moved = WorkOrders.move(orders(), index, delta);
		if (!moved.equals(orderList)) {
			orderList.clear();
			orderList.addAll(moved);
			onChange.run();
		}
	}

	/** The owner's order as saved. */
	List<Integer> orderList() {
		return Collections.unmodifiableList(orderList);
	}

	private void pruneOrders() {
		List<Integer> open = orders();
		orderList.removeIf(index -> !open.contains(index));
	}

	// --- the house plan library (round 2 of 0.1.219, Q4) ---

	/** The house plans the town learned, by intact template id. Without the one the restored hall gives (Q5). */
	public Set<String> learnedHousePlans() {
		return Collections.unmodifiableSet(housePlans);
	}

	/** The town learns the house plan of intact template {@code template}; whether it was new. */
	public boolean learnHousePlan(String template) {
		if (!housePlans.add(template)) {
			return false;
		}
		onChange.run();
		return true;
	}

	void onChange(Runnable hook) {
		this.onChange = hook;
	}

	/** Whether the town knows its buildings: its structure registered them, or its core found them. */
	public boolean buildingsKnown() {
		return buildingsKnown;
	}

	/** Whether the town's core already looked for its structure on this load (a town of an older save). */
	public boolean structureAsked() {
		return structureAsked;
	}

	/** The core looked for the town's structure: once per load is enough, found or not. */
	public void markStructureAsked() {
		structureAsked = true;
	}

	/** The town's buildings, in the order its structure laid them out (the town hall first). */
	public List<TownBuilding> buildings() {
		return Collections.unmodifiableList(buildings);
	}

	/** The building at {@code index}. */
	public Optional<TownBuilding> building(int index) {
		return index >= 0 && index < buildings.size() ? Optional.of(buildings.get(index)) : Optional.empty();
	}

	/** The town's work queue: executors take jobs from it, the owner raises and calls them off. */
	public WorkQueue queue() {
		return queue;
	}

	/** Whether the town holds nothing worth saving. */
	public boolean isEmpty() {
		return !buildingsKnown && queue.isEmpty() && foundSheets == 0 && roadPlan.isEmpty() && housePlans.isEmpty();
	}

	/**
	 * The plan sheets the town has found (MOD-859): one bit per sheet number, bit 0 for sheet 1. A sheet is found once
	 * it lay in the town's working dispatcher, alone or inside the assembled plan; the core never looks into any other
	 * container for it (ADR-010).
	 */
	public int foundSheets() {
		return foundSheets;
	}

	/** Adds the sheets of {@code mask} to the found ones; returns whether any was new. A found sheet stays found. */
	public boolean markSheetsFound(int mask) {
		int next = foundSheets | (mask & PlanSheetRules.ALL_FOUND);
		if (next == foundSheets) {
			return false;
		}
		foundSheets = next;
		onChange.run();
		return true;
	}

	/**
	 * The town learns its buildings — once: a town that already knows them keeps them, with their states. Returns
	 * whether the list was taken.
	 */
	public boolean learnBuildings(List<TownBuilding> found) {
		if (buildingsKnown) {
			return false;
		}
		buildings.clear();
		buildings.addAll(found);
		buildingsKnown = true;
		plans.clear();
		onChange.run();
		return true;
	}

	/**
	 * Adds a house a player laid (MOD-877, R108) and returns its index: the slot of a withdrawn house if there is one,
	 * else a new one at the end. The town knows its buildings from then on.
	 */
	public int lay(TownBuilding house) {
		int index = buildings.size();
		for (int i = 0; i < buildings.size(); i++) {
			if (buildings.get(i).withdrawn()) {
				index = i;
				break;
			}
		}
		if (index == buildings.size()) {
			buildings.add(house);
		} else {
			buildings.set(index, house);
		}
		buildingsKnown = true;
		plans.remove(index);
		roadPlan.detach(index);
		roadRetryAt = 0;
		onChange.run();
		return index;
	}

	/** Calls off the laid house {@code index} (MOD-877): its open jobs go, its slot waits for the next house laid. */
	public void withdraw(int index) {
		Optional<TownBuilding> found = building(index);
		if (found.isEmpty() || !found.get().isLaid() || found.get().withdrawn()) {
			return;
		}
		for (WorkJob job : List.copyOf(queue.jobs())) {
			if (job.building() == index && job.status().open()) {
				queue.cancel(job.id());
			}
		}
		buildings.set(index, found.get().asWithdrawn());
		plans.remove(index);
		roadPlan.detach(index);
		onChange.run();
	}

	/** The town's road network (R128): the tree of road cells from the hall's inlet to every building's. */
	public TownRoadPlan roadPlan() {
		return roadPlan;
	}

	/** Whether the road network may be grown again at game time {@code now}: no recent attempt left buildings out. */
	public boolean roadGrowDue(long now) {
		return now >= roadRetryAt;
	}

	/** An attempt to grow the road network left buildings out: the next waits until {@code at}. Never saved. */
	public void deferRoadGrowth(long at) {
		roadRetryAt = at;
	}

	/** The next look at the road network grows it at once — the ground changed (a test built it). */
	public void retryRoadsNow() {
		roadRetryAt = 0;
	}

	/** Replaces the building at {@code index}; no-op for an index out of range or an equal record. */
	void put(int index, TownBuilding building) {
		if (index >= 0 && index < buildings.size() && !buildings.get(index).equals(building)) {
			buildings.set(index, building);
			onChange.run();
		}
	}

	/** Adds {@code number} to the former residents of building {@code index} (MOD-862, R40); once per number. */
	public void addFormerResident(int index, String number) {
		building(index).ifPresent(building -> put(index, building.withFormerResident(number)));
	}

	/** The cached plan of building {@code index}, if built. */
	Optional<Optional<BuildingPlan>> cachedPlan(int index) {
		return Optional.ofNullable(plans.get(index));
	}

	void cachePlan(int index, Optional<BuildingPlan> plan) {
		plans.put(index, plan);
	}

	/** Forgets the plans — a building's definition changed, or the templates were reloaded. */
	public void forgetPlans() {
		plans.clear();
		scanCursor = 0;
		scanMatched = 0;
		scanFailedKeys = 0;
	}

	/**
	 * Takes a finished pass over building {@code index}: its state, numbers and key points from the rules. Returns the
	 * verdict, whose {@link BuildingStateRules.Verdict#justRestored()} tells the caller to log the restoration.
	 */
	public Optional<BuildingStateRules.Verdict> applyPass(int index, BuildingStateRules.Measure measure,
			double restoredAt) {
		Optional<TownBuilding> found = building(index);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		BuildingStateRules.Verdict verdict = BuildingStateRules.afterPass(found.get().before(), measure, restoredAt,
				queue.open(WorkJobType.CLEAR, index).isPresent(), queue.open(WorkJobType.BUILD, index).isPresent());
		put(index, found.get().withVerdict(measure, verdict));
		return Optional.of(verdict);
	}

	/** Re-derives the states that follow from the jobs alone. */
	private void refreshStates() {
		for (int i = 0; i < buildings.size(); i++) {
			TownBuilding building = buildings.get(i);
			BuildingState state = BuildingStateRules.betweenPasses(building.state(), building.before(),
					queue.open(WorkJobType.CLEAR, i).isPresent(), queue.open(WorkJobType.BUILD, i).isPresent());
			if (state != building.state()) {
				buildings.set(i, building.withState(state));
			}
		}
	}
}
