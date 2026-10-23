package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * The town's watch over orders that ended too early (round 5 of 0.1.219, the owner's «no dead ends»): a building whose
 * order came to an end — its last job done, not called off — while the building is still not restored, and that has
 * no open job, gets its order placed again by the town itself ({@link TownOrders#place}), and the log says so with
 * {@code [ala-towns]}. Without it such a building waited for a «Start» that did nothing visible.
 *
 * <p>A building is looked at again only after it has stood so for {@link #RESTORE_AFTER} ticks (the job's last
 * measure has settled), and is restored at most once per {@link #BACKOFF} ticks, so a building no drone can finish
 * never makes the queue spin. When a new order raises nothing, the dispatcher's «Task» tab says why
 * ({@link DispatcherViews}).
 *
 * <p>Held by the town's working dispatcher; not saved — a reload waits {@link #RESTORE_AFTER} again.
 */
public final class StalledOrders {

	/** Ticks a building stands unfinished with no job before the town places its order again. */
	public static final int RESTORE_AFTER = 200;
	/** Ticks before the same building is restored again. */
	public static final int BACKOFF = 6000;
	/** Who places the restored order, in the log. */
	public static final String WHO = "the town (work queue restored)";

	private final Map<Integer, Long> stalledSince = new HashMap<>();
	private final Map<Integer, Long> nextTry = new HashMap<>();

	/** One look over the buildings of {@code town} at game time {@code now}. */
	public void check(ServerLevel level, Settlement town, long now) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		for (int i = 0; i < works.buildings().size(); i++) {
			if (!stalled(works, i)) {
				stalledSince.remove(i);
				continue;
			}
			long since = stalledSince.computeIfAbsent(i, k -> now);
			if (now - since < RESTORE_AFTER || now < nextTry.getOrDefault(i, Long.MIN_VALUE)) {
				continue;
			}
			stalledSince.remove(i);
			nextTry.put(i, now + BACKOFF);
			Optional<String> refused = TownOrders.place(level, town, i, WHO);
			if (refused.isEmpty() && TownOrders.ordered(works, i)) {
				Industrialization.LOGGER.info("[ala-towns] settlement {}: building {} was left unfinished with no job"
						+ " open; the work queue was restored", town.id(), i);
			}
		}
	}

	/**
	 * Whether building {@code index} of {@code works} is an order that ended too early: it can be restored, has not
	 * been yet, has no open job, and its newest job was finished — not called off by the owner, not a repair.
	 */
	public static boolean stalled(TownWorks works, int index) {
		Optional<TownBuilding> found = works.building(index);
		if (found.isEmpty()) {
			return false;
		}
		TownBuilding building = found.get();
		if (building.withdrawn() || building.intact().isEmpty() || building.everRestored()
				|| building.state() == BuildingState.INTACT || building.state() == BuildingState.UPGRADED) {
			return false;
		}
		WorkJob newest = null;
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() != index) {
				continue;
			}
			if (job.status().open()) {
				return false;
			}
			newest = job;
		}
		return newest != null && newest.status() == WorkJobStatus.DONE && !newest.repair();
	}
}
