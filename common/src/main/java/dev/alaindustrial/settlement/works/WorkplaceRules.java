package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.shift.Profession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who works where in an ALA town (MOD-888; epic MOD-824, decisions R150, R151, plan 0.1.220 block B4): the town gives
 * its workplaces out by itself, the way it gives its houses (R102). A workplace is a building of a trade
 * ({@link Profession#building}) restored once; it takes {@link Profession#places} workers. Every pass:
 * <ol>
 *   <li>a worker that is not alive and waiting, or whose workplace is no workplace any more (withdrawn, never
 *       restored, its trade gone), or over its building's places, loses its work — a worker waiting to be brought back
 *       keeps it, as it keeps its house;</li>
 *   <li>every free place takes the first living worker without work by number — the oldest record first (R102). A
 *       worker the restorer took off work ({@code offWork}) is never given a place by the town.</li>
 * </ol>
 *
 * <p>A restored workplace that is damaged later stays a workplace: the town's maintenance mends it, its worker keeps
 * the job. Minecraft-free on purpose: the L1 lane checks the whole table ({@code WorkplaceRulesTest}).
 */
public final class WorkplaceRules {

	private WorkplaceRules() {
	}

	/**
	 * One workplace of the town.
	 *
	 * @param building   its index among the town's buildings
	 * @param profession the trade it makes of its workers
	 */
	public record Place(int building, Profession profession) {
	}

	/**
	 * One worker as the rule sees it.
	 *
	 * @param key       anything that tells the workers apart (the record number's text); the order of {@code rank}
	 * @param rank      the order of seniority: lower is older (the record number's index)
	 * @param living    alive in the world
	 * @param waiting   dead and waiting to be brought back: keeps its work
	 * @param workplace the building it works in now, or a negative value: none
	 * @param offWork   the restorer took it off work: the town gives it no place
	 */
	public record Worker(String key, long rank, boolean living, boolean waiting, int workplace, boolean offWork) {
	}

	/**
	 * One change the pass makes.
	 *
	 * @param key        the worker's {@link Worker#key}
	 * @param profession its new trade, {@link Profession#NONE} when it loses its work
	 * @param building   its new workplace, or -1
	 */
	public record Change(String key, Profession profession, int building) {

		/** Whether the change gives the worker work. */
		public boolean hired() {
			return profession != Profession.NONE && building >= 0;
		}
	}

	/** The changes one pass makes over {@code workers} for {@code places}, the losses first, then the new hires. */
	public static List<Change> assign(List<Place> places, List<Worker> workers) {
		Map<Integer, Place> byBuilding = new LinkedHashMap<>();
		for (Place place : places) {
			byBuilding.put(place.building(), place);
		}
		List<Worker> byRank = new ArrayList<>(workers);
		byRank.sort(Comparator.comparingLong(Worker::rank).thenComparing(Worker::key));
		Map<Integer, Integer> taken = new HashMap<>();
		List<Change> out = new ArrayList<>();
		List<Worker> free = new ArrayList<>();
		for (Worker worker : byRank) {
			if (worker.workplace() < 0) {
				if (worker.living() && !worker.offWork()) {
					free.add(worker);
				}
				continue;
			}
			Place place = byBuilding.get(worker.workplace());
			boolean keeps = (worker.living() || worker.waiting()) && place != null
					&& taken.getOrDefault(place.building(), 0) < place.profession().places();
			if (keeps) {
				taken.merge(place.building(), 1, Integer::sum);
			} else {
				out.add(new Change(worker.key(), Profession.NONE, -1));
				if (worker.living()) {
					free.add(worker);
				}
			}
		}
		free.sort(Comparator.comparingLong(Worker::rank).thenComparing(Worker::key));
		int next = 0;
		for (Place place : byBuilding.values()) {
			int room = place.profession().places() - taken.getOrDefault(place.building(), 0);
			for (int i = 0; i < room && next < free.size(); i++) {
				out.add(new Change(free.get(next++).key(), place.profession(), place.building()));
			}
		}
		return collapse(out);
	}

	/** A worker that lost its work and got another in one pass shows one change: the last one. */
	private static List<Change> collapse(List<Change> changes) {
		Map<String, Change> last = new LinkedHashMap<>();
		for (Change change : changes) {
			last.put(change.key(), change);
		}
		List<Change> out = new ArrayList<>();
		for (Change change : changes) {
			if (last.get(change.key()) == change) {
				out.add(change);
			}
		}
		return out;
	}
}
