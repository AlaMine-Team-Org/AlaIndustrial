package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

	/** An empty town: no building known, nothing queued. */
	public TownWorks() {
		this(List.of(), false, new WorkQueue(), 0);
	}

	TownWorks(List<TownBuilding> saved, boolean known, WorkQueue queue, int foundSheets) {
		this.buildings.addAll(saved);
		this.foundSheets = foundSheets & PlanSheetRules.ALL_FOUND;
		this.buildingsKnown = known || !saved.isEmpty();
		this.queue = queue;
		this.queue.onChange(() -> {
			refreshStates();
			onChange.run();
		});
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
		return !buildingsKnown && queue.isEmpty() && foundSheets == 0;
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

	/** Replaces the building at {@code index}; no-op for an index out of range or an equal record. */
	void put(int index, TownBuilding building) {
		if (index >= 0 && index < buildings.size() && !buildings.get(index).equals(building)) {
			buildings.set(index, building);
			onChange.run();
		}
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
