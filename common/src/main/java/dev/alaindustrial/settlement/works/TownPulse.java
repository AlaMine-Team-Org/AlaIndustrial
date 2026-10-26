package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.ConstructionDroneWork;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * The town's pulse (MOD-886, plan 0.1.220 block B2): every {@code townPulseIntervalTicks} ticks a lit core gathers the
 * town's facts and works out what the town needs ({@link TownNeeds}). It reads the town's data only — the registry's
 * buildings, work queue and workers, and the dispatcher's plan slot — never the world's blocks: comparing the buildings
 * with the world is {@link TownWorksScanner}'s, and the pulse reads what that left in the buildings' records.
 *
 * <p><b>What it reads.</b> Energy: the core says whether it owes upkeep or holds less than its reserve. Materials: the
 * «no materials» reasons of the town's blocked jobs — what the dispatcher's buffer lacked when a drone last asked (a
 * drone asks again on its own, so a filled buffer clears the need at its next try). A home: living workers with
 * none, or no free home for the next one while the tier allows more. Restoration: a building the town may order now —
 * a house of a known type under the house limit, the hall with its plan in the dispatcher, or the warehouse once the
 * hall stands and the town knows its plan (MOD-888, MOD-892). A workplace: never — a worker without work lives by
 * the routine (R150, {@link #withoutWorkplace}).
 *
 * <p>The latest result is kept per town for the core's screen ({@link #latest}); it is the server's runtime state, not
 * saved — the next beat works it out again. Server thread only.
 */
public final class TownPulse {

	/**
	 * What one beat worked out.
	 *
	 * @param gameTime when
	 * @param needs    the town's needs, most pressing first
	 * @param nanos    what the beat cost, in nanoseconds (MOD-886 measures the pulse)
	 */
	public record Reading(long gameTime, List<TownNeeds.Need> needs, long nanos) {

		public Reading {
			needs = List.copyOf(needs);
		}
	}

	private static final Map<Integer, Reading> LATEST = new ConcurrentHashMap<>();

	private TownPulse() {
	}

	/**
	 * One lit tick of town {@code town}'s core: beats once every {@code townPulseIntervalTicks} ticks. The towns
	 * beat on different ticks (offset by their number), so many towns never beat together.
	 *
	 * @param energyShort whether the core owes upkeep or holds less than its reserve
	 */
	public static void tick(ServerLevel level, int town, boolean energyShort) {
		int every = Math.max(1, SettlementConfig.townPulseIntervalTicks);
		if (Math.floorMod(level.getGameTime() + town, every) == 0) {
			beat(level, town, energyShort);
		}
	}

	/** Works out town {@code town}'s needs now and keeps them as its latest reading. */
	public static Reading beat(ServerLevel level, int town, boolean energyShort) {
		long started = System.nanoTime();
		List<TownNeeds.Need> needs = TownNeeds.of(facts(level, town, energyShort));
		Reading reading = new Reading(level.getGameTime(), needs, System.nanoTime() - started);
		LATEST.put(town, reading);
		return reading;
	}

	/**
	 * Town {@code town}'s latest reading, if one is fresh: worked out at most two intervals before {@code now}, and not
	 * after it (a reading of another world's town of the same number, in a server started again, is not fresh).
	 */
	public static Optional<Reading> latest(int town, long now) {
		Reading reading = LATEST.get(town);
		long window = 2L * Math.max(1, SettlementConfig.townPulseIntervalTicks);
		if (reading == null || reading.gameTime() > now || now - reading.gameTime() > window) {
			return Optional.empty();
		}
		return Optional.of(reading);
	}

	/** The facts of town {@code town} the needs are worked out from. */
	public static TownNeeds.Facts facts(ServerLevel level, int town, boolean energyShort) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<Settlement> settlement = registry.find(town);
		Optional<TownWorks> found = registry.works().find(town);
		if (settlement.isEmpty()) {
			return TownNeeds.Facts.NONE;
		}
		int living = 0;
		int homeless = 0;
		for (ShiftWorkerRecord record : registry.workers().all()) {
			if (record.settlement() == town && record.status() == ShiftWorkerRecord.Status.ALIVE) {
				living++;
				if (!record.housed()) {
					homeless++;
				}
			}
		}
		boolean noHomeForNext = homeless == 0 && living < settlement.get().tier().workerLimit()
				&& !TownHousing.roomForNewWorker(level, town);
		List<List<String>> missing = new ArrayList<>();
		List<String> restorable = new ArrayList<>();
		List<String> repairable = new ArrayList<>();
		if (found.isPresent()) {
			TownWorks works = found.get();
			for (WorkJob job : works.queue().jobs()) {
				if (job.status() == WorkJobStatus.BLOCKED && job.reason().isPresent()
						&& BlockReason.NO_MATERIALS.equals(job.reason().get().key())) {
					missing.add(job.reason().get().args());
				}
			}
			orderable(level, settlement.get(), works, restorable, repairable);
		}
		return new TownNeeds.Facts(energyShort, missing, homeless, noHomeForNext, restorable, repairable,
				withoutWorkplace(level, town));
	}

	/** The buildings of {@code works} the town may order now, by name key: restored or built, or mended. */
	private static void orderable(ServerLevel level, Settlement town, TownWorks works, List<String> restorable,
			List<String> repairable) {
		Optional<TownDispatcherBlockEntity> dispatcher = Optional.empty();
		boolean dispatcherAsked = false;
		for (int i = 0; i < works.buildings().size(); i++) {
			Optional<OrderButton> button = TownOrders.button(works, i);
			if (button.isEmpty() || button.get() == OrderButton.CANCEL
					|| TownOrders.blocker(town, works, i).isPresent()) {
				continue;
			}
			TownBuilding building = works.buildings().get(i);
			if (!TownHouses.isHouse(building) && !building.everRestored()) {
				// The hall (or another building) is restored by the plan in the dispatcher's slot (R84).
				if (!dispatcherAsked) {
					dispatcher = TownDispatcherBlockEntity.of(level, town.id());
					dispatcherAsked = true;
				}
				if (dispatcher.isEmpty() || !ConstructionDroneWork.planKnown(works, i, dispatcher.get().plan())) {
					continue;
				}
			}
			(button.get() == OrderButton.REPAIR ? repairable : restorable).add(TownHouses.displayName(building));
		}
	}

	/**
	 * Living workers of town {@code town} who miss a workplace — always none (MOD-888). A worker without work just
	 * lives by the routine (R150): that is no need of the town. A workplace that could be restored already shows as a
	 * restoration need ({@link #orderable}), and a full workplace makes no new one to want, so the line is never
	 * raised; the hook and its translation stay for a trade that will want its place filled.
	 */
	private static int withoutWorkplace(ServerLevel level, int town) {
		return 0;
	}
}
