package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.Profession;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.shift.TownChronicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who works where in an ALA town (MOD-888; epic MOD-824, decisions R150, R151, plan 0.1.220 block B4). The worker's
 * record holds its trade and its workplace ({@link ShiftWorkerRecord#profession()},
 * {@link ShiftWorkerRecord#workplace()}); the rule of who gets which place is {@link WorkplaceRules}.
 *
 * <p><b>A workplace</b> is a building of a trade ({@link Profession#building}: the warehouse) that the town has
 * restored once. The warehouse is restored by its plan, which the town learns like a house plan (MOD-892, R154,
 * {@link HousePlans#needsPlan}); its order waits for the hall like a house's.
 *
 * <p><b>Taken off work</b> by the restorer (the card's button), a worker is marked {@link ShiftWorkerRecord#OFF_WORK}:
 * the town does not give it a place again until the restorer sends it back to work, so the place goes to the next
 * worker by number.
 */
public final class TownWorkplaces {

	/** A lit core gives its town's workplaces out once every this many ticks, a tick after it settles its houses. */
	public static final int ASSIGN_TICKS = TownHousing.SETTLE_TICKS;

	/** The chronicle's line about a worker who took a trade: «became a storekeeper» ({@code …became.<trade>}). */
	public static final String CHRONICLE_BECAME = "settlement.alaindustrial.chronicle.became.";
	/** The chronicle's line about a worker the restorer took off work. */
	public static final String CHRONICLE_OFF_WORK = "settlement.alaindustrial.chronicle.off_work";

	private TownWorkplaces() {
	}

	/** The trade building {@code building} makes of its workers, if it is a workplace by its kind. */
	public static Optional<Profession> tradeOf(TownBuilding building) {
		if (building.withdrawn()) {
			return Optional.empty();
		}
		String path = building.template().getPath();
		return Profession.ofBuilding(path.substring(path.lastIndexOf('/') + 1));
	}

	/** The workplaces of {@code works} now: workplace buildings restored once and not given up. */
	public static List<WorkplaceRules.Place> places(TownWorks works) {
		List<WorkplaceRules.Place> out = new ArrayList<>();
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			Optional<Profession> trade = tradeOf(building);
			if (trade.isPresent() && StorekeeperRules.isStorage(building.everRestored(), building.withdrawn())) {
				out.add(new WorkplaceRules.Place(i, trade.get()));
			}
		}
		return out;
	}

	/** One lit tick of town {@code town}'s core: every {@link #ASSIGN_TICKS} ticks gives the workplaces out. */
	public static void tick(ServerLevel level, int town) {
		if (Math.floorMod(level.getGameTime() - 1, (long) ASSIGN_TICKS) == 0) {
			assign(level, town);
		}
	}

	/** Gives town {@code town}'s workplaces out ({@link WorkplaceRules#assign}) and writes every change down. */
	public static void assign(ServerLevel level, int town) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> works = registry.works().find(town);
		if (works.isEmpty()) {
			return;
		}
		ShiftWorkerBook book = registry.workers();
		List<WorkplaceRules.Worker> workers = new ArrayList<>();
		for (ShiftWorkerRecord record : book.all()) {
			if (record.settlement() != town || record.status() == ShiftWorkerRecord.Status.DEAD
					&& record.workplace() < 0) {
				continue;
			}
			workers.add(new WorkplaceRules.Worker(record.number().toString(), record.number().index(),
					record.status() == ShiftWorkerRecord.Status.ALIVE,
					record.status() == ShiftWorkerRecord.Status.AWAITING_RETURN,
					record.employed() ? record.workplace() : -1, record.workplace() == ShiftWorkerRecord.OFF_WORK));
		}
		for (WorkplaceRules.Change change : WorkplaceRules.assign(places(works.get()), workers)) {
			Optional<ShiftWorkerRecord> found = book.all().stream()
					.filter(record -> record.settlement() == town && record.number().toString().equals(change.key()))
					.findFirst();
			found.ifPresent(record -> apply(level, record, change));
		}
	}

	private static void apply(ServerLevel level, ShiftWorkerRecord record, WorkplaceRules.Change change) {
		boolean offWork = record.workplace() == ShiftWorkerRecord.OFF_WORK;
		ShiftWorkerRecord changed = record.withWork(change.profession(),
				change.hired() ? change.building() : offWork ? ShiftWorkerRecord.OFF_WORK
						: ShiftWorkerRecord.NO_WORKPLACE);
		store(level, changed);
		if (change.hired()) {
			TownChronicle.record(level.getServer(), changed.settlement(), changed,
					CHRONICLE_BECAME + change.profession().serializedName(), level.getGameTime());
		}
		// INFO on purpose (MOD-833): the playtest protocol of the towns reads these lines from the log.
		Industrialization.LOGGER.info("[ala-towns] settlement {}: worker {} {}", changed.settlement(),
				changed.number(), change.hired() ? "works as " + change.profession().serializedName() + " in building "
						+ change.building() : "has no work");
	}

	/** Writes {@code record} and shows it on the worker's loaded body (its patch, R151). */
	private static void store(ServerLevel level, ShiftWorkerRecord record) {
		SettlementRegistry.get(level.getServer()).workers().put(record);
		ShiftWorkers.showOnBody(level, record);
	}

	/**
	 * The restorer takes worker {@code record} off work (the card's button): no trade, and the town gives it no place
	 * by itself; the place goes to the next worker by number on the next pass. Returns whether it changed anything.
	 */
	public static boolean takeOffWork(ServerLevel level, ShiftWorkerRecord record) {
		if (record.status() != ShiftWorkerRecord.Status.ALIVE || record.workplace() == ShiftWorkerRecord.OFF_WORK) {
			return false;
		}
		boolean employed = record.employed();
		ShiftWorkerRecord off = record.withWork(Profession.NONE, ShiftWorkerRecord.OFF_WORK);
		store(level, off);
		if (employed) {
			TownChronicle.record(level.getServer(), off.settlement(), off, CHRONICLE_OFF_WORK, level.getGameTime());
		}
		assign(level, off.settlement());
		return true;
	}

	/**
	 * The restorer sends worker {@code record}, taken off work before, back to work: the town gives it the next free
	 * place, now if there is one. Returns whether it changed anything.
	 */
	public static boolean backToWork(ServerLevel level, ShiftWorkerRecord record) {
		if (record.status() != ShiftWorkerRecord.Status.ALIVE || record.workplace() != ShiftWorkerRecord.OFF_WORK) {
			return false;
		}
		store(level, record.withWork(Profession.NONE, ShiftWorkerRecord.NO_WORKPLACE));
		assign(level, record.settlement());
		return true;
	}

	/**
	 * The restorer takes worker {@code number} off work, or sends a worker taken off work back (the card's button):
	 * only the restorer, only a living worker ({@link ShiftWorkerProfiles#mayChange}); the card is sent again either
	 * way.
	 */
	public static void toggleWork(ServerPlayer player, RecordNumber number) {
		ShiftWorkerBook book = SettlementRegistry.get(player.level().getServer()).workers();
		Optional<ShiftWorkerRecord> found = book.byNumber(number)
				.filter(record -> ShiftWorkerProfiles.mayChange(player, record));
		if (found.isEmpty()) {
			return;
		}
		if (found.get().workplace() == ShiftWorkerRecord.OFF_WORK) {
			backToWork(player.level(), found.get());
		} else {
			takeOffWork(player.level(), found.get());
		}
		book.byNumber(number).ifPresent(record -> ShiftWorkerProfiles.sendCard(player, record, false));
	}

	/** Whether town {@code town} has a free place a worker could take now. */
	public static boolean freePlace(ServerLevel level, int town) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> works = registry.works().find(town);
		if (works.isEmpty()) {
			return false;
		}
		int room = 0;
		for (WorkplaceRules.Place place : places(works.get())) {
			room += place.profession().places();
		}
		for (ShiftWorkerRecord record : registry.workers().all()) {
			if (record.settlement() == town && record.employed() && record.status() != ShiftWorkerRecord.Status.DEAD) {
				room--;
			}
		}
		return room > 0;
	}
}
