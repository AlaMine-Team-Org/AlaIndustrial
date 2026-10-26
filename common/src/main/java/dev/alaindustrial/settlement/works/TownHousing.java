package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ChargingBedBlock;
import dev.alaindustrial.block.EngravedLetterPlateBlock;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.HousePowerSource;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.TownChronicle;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Who lives where in an ALA town (MOD-862; epic MOD-824, decisions R40, R101, R102, R103, R110). The worker's record
 * holds its house ({@link ShiftWorkerRecord#home()}); the house keeps its former residents
 * ({@link TownBuilding#formerResidents()}).
 *
 * <p><b>Settling</b> ({@link #settle}), run by the town's lit core now and then: a resident of a house that is no
 * home any more — off the count, past the limit, no longer fit, its inlet broken — moves out and becomes homeless, as
 * does a worker dead for good; then every free home takes the first homeless living worker by number (R102). A
 * worker waiting to come back keeps its house. Old worlds' workers start homeless and are settled the same way (R103).
 *
 * <p><b>The plaque</b> (R40; owner's round 4 of 0.1.219, R4-12): the house's plaque shows its resident's full number —
 * the letter whole, then the three digits — and once the house stands empty, its last resident's number with the
 * letter broken. The number it showed before goes into the house's history — the ruin's former resident on the first
 * move-in. Nothing is engraved here: a town drone flies over and changes the plates ({@link #plaqueToEngrave}), with
 * no word in the chat. A house nobody ever lived in keeps its ruin's plaque; a plate a player took away is put back by
 * the house's repair first, and engraved after. Plates are interchangeable for the comparison
 * ({@link BuildingPlan#matches}), so the engraving never reads as damage.
 */
public final class TownHousing {

	/** A lit core settles its town once every this many ticks. */
	public static final int SETTLE_TICKS = 40;

	private TownHousing() {
	}

	/**
	 * One lit tick of town {@code town}'s core: every {@link #SETTLE_TICKS} ticks calls off the laid houses whose inlet
	 * went before their building started (MOD-877), then settles the town.
	 */
	public static void tick(ServerLevel level, int town) {
		if (level.getGameTime() % SETTLE_TICKS == 0) {
			HousePlacement.tick(level, town);
			settle(level, town);
		}
	}

	/** Settles town {@code town}: moves out residents without a home, then houses the homeless by number. */
	public static void settle(ServerLevel level, int town) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> found = registry.works().find(town);
		Optional<Settlement> settlement = registry.find(town);
		if (found.isEmpty() || settlement.isEmpty()) {
			return;
		}
		TownWorks works = found.get();
		List<Integer> homes = TownHouses.homes(works, settlement.get().tier().houseLimit());
		ShiftWorkerBook book = registry.workers();
		Set<Integer> taken = new HashSet<>();
		for (ShiftWorkerRecord record : List.copyOf(book.all())) {
			if (record.settlement() != town || !record.housed()) {
				continue;
			}
			boolean stays = record.status() != ShiftWorkerRecord.Status.DEAD && homes.contains(record.home())
					&& taken.add(record.home());
			if (!stays) {
				moveOut(works, book, record);
			}
		}
		List<ShiftWorkerRecord> homeless = new ArrayList<>();
		for (ShiftWorkerRecord record : book.all()) {
			if (record.settlement() == town && record.status() == ShiftWorkerRecord.Status.ALIVE
					&& !record.housed()) {
				homeless.add(record);
			}
		}
		homeless.sort(Comparator.comparing(ShiftWorkerRecord::number));
		int next = 0;
		for (int home : homes) {
			if (next >= homeless.size()) {
				break;
			}
			if (!taken.contains(home)) {
				moveIn(level, town, works, book, homeless.get(next++), home);
			}
		}
	}

	/** Towns a game test frees of «no housing»: the capsule suites predate the houses. Empty outside the tests. */
	private static final Set<Integer> HOUSING_EXEMPT = ConcurrentHashMap.newKeySet();

	/** A game test's switch: the reception capsule of town {@code town} no longer asks for a free house. */
	public static void exemptFromHousing(int town) {
		HOUSING_EXEMPT.add(town);
	}

	/** Whether a new worker of town {@code town} has a home to go to (R103): a free home, or a test's exemption. */
	public static boolean roomForNewWorker(ServerLevel level, int town) {
		return HOUSING_EXEMPT.contains(town) || !freeHomes(level, town).isEmpty();
	}

	/**
	 * How many homes town {@code town} has — houses fit to live in, at most its tier's house limit, taken or free —
	 * what the reception capsule counts its workers against (owner's round 1 of 0.1.219); a test's exempt town
	 * ({@link #exemptFromHousing}) has {@link Integer#MAX_VALUE}, housing not being in question there.
	 */
	public static int homeCount(ServerLevel level, int town) {
		if (HOUSING_EXEMPT.contains(town)) {
			return Integer.MAX_VALUE;
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> found = registry.works().find(town);
		Optional<Settlement> settlement = registry.find(town);
		if (found.isEmpty() || settlement.isEmpty()) {
			return 0;
		}
		return TownHouses.homes(found.get(), settlement.get().tier().houseLimit()).size();
	}

	/** The homes of town {@code town} nobody lives in, in the town's order; settle first for a fresh answer. */
	public static List<Integer> freeHomes(ServerLevel level, int town) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> found = registry.works().find(town);
		Optional<Settlement> settlement = registry.find(town);
		if (found.isEmpty() || settlement.isEmpty()) {
			return List.of();
		}
		List<Integer> free = new ArrayList<>(TownHouses.homes(found.get(), settlement.get().tier().houseLimit()));
		for (ShiftWorkerRecord record : registry.workers().all()) {
			if (record.settlement() == town && record.housed() && record.status() != ShiftWorkerRecord.Status.DEAD) {
				free.remove(Integer.valueOf(record.home()));
			}
		}
		return free;
	}

	/**
	 * Moves the living worker {@code record} into free home {@code index} of its town (R102: the «Relocate» button);
	 * returns whether it moved. Its old house, if any, keeps its number in the history.
	 */
	public static boolean relocate(ServerLevel level, ShiftWorkerRecord record, int index) {
		int town = record.settlement();
		if (record.status() != ShiftWorkerRecord.Status.ALIVE || record.home() == index
				|| !freeHomes(level, town).contains(index)) {
			return false;
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		TownWorks works = registry.works().town(town);
		ShiftWorkerRecord moved = record.housed() ? moveOut(works, registry.workers(), record) : record;
		moveIn(level, town, works, registry.workers(), moved, index);
		return true;
	}

	/**
	 * Where the head of the charging bed of house {@code index} of town {@code town} stands, by its intact template:
	 * the cell a worker lies down on, its body along the bed (R4-9).
	 */
	public static Optional<BlockPos> bed(ServerLevel level, int town, int index) {
		return cellOf(level, town, index, state -> state.is(ModContent.CHARGING_BED.get())
				&& state.getValue(ChargingBedBlock.PART) == BedPart.HEAD);
	}

	/**
	 * The inlet of house {@code index} of town {@code town}, standing and loaded (R101): what its charging bed draws
	 * from. Empty when the inlet is gone or its chunk is not loaded.
	 */
	public static Optional<HousePowerSource> inlet(ServerLevel level, int town, int index) {
		return cellOf(level, town, index, state -> state.is(ModContent.HOUSE_INLET.get()))
				.filter(level::isLoaded)
				.map(level::getBlockEntity)
				.filter(HousePowerSource.class::isInstance)
				.map(HousePowerSource.class::cast);
	}

	private static Optional<BlockPos> cellOf(ServerLevel level, int town, int index, Predicate<BlockState> block) {
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town, index);
		if (plan.isEmpty()) {
			return Optional.empty();
		}
		for (int i = 0; i < plan.get().size(); i++) {
			if (block.test(plan.get().expected(i))) {
				return Optional.of(plan.get().pos(i));
			}
		}
		return Optional.empty();
	}

	/** The hall log note of a broken inlet of a lived-in house: «…, resident %s homeless». */
	public static final String JOURNAL_INLET_BROKEN = "settlement.alaindustrial.journal.house_inlet_broken";
	/** The hall log note of a broken inlet of a house nobody lives in. */
	public static final String JOURNAL_INLET_BROKEN_EMPTY =
			"settlement.alaindustrial.journal.house_inlet_broken_empty";

	/**
	 * A pass over house {@code index} of town {@code town} has just found its inlet gone (MOD-876, MOD-884): if the
	 * house was fit to live in before the pass ({@code before}) and its inlet stood then, writes one line to the
	 * hall's log — the house is off the count and its resident, named by number, is homeless. The line is a note
	 * ({@link ShiftWorkerBook.Note}) carrying the resident's number and name the way a line about a death does; the
	 * move out itself is {@link #settle}'s. Returns whether it wrote the line.
	 */
	public static boolean logInletBroken(ServerLevel level, int town, int index, TownBuilding before,
			List<String> missingNow) {
		if (!TownHouses.fitToLive(before) || TownHouses.inletMissing(before)
				|| !missingNow.contains(TownHouses.INLET_KEY)) {
			return false;
		}
		ShiftWorkerBook book = SettlementRegistry.get(level.getServer()).workers();
		Optional<ShiftWorkerRecord> resident = book.all().stream()
				.filter(record -> record.settlement() == town && record.housed() && record.home() == index
						&& record.status() != ShiftWorkerRecord.Status.DEAD)
				.findFirst();
		// The resident's number is also the sentence's argument, so the «Chronicle» tab (MOD-886) words it in full.
		ShiftWorkerBook.Note note = new ShiftWorkerBook.Note(
				resident.isPresent() ? JOURNAL_INLET_BROKEN : JOURNAL_INLET_BROKEN_EMPTY, level.getGameTime(),
				resident.map(record -> List.of(record.number().toString())).orElse(List.of()), 1);
		book.addToLog(new ShiftWorkerBook.LogLine(town, resident.map(ShiftWorkerRecord::number),
				resident.map(ShiftWorkerRecord::name).orElse(""), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.of(note)));
		// INFO on purpose (MOD-833): the playtest protocol of the towns reads these lines from the log.
		Industrialization.LOGGER.info("[ala-towns] settlement {}: the inlet of house {} is broken, resident {}", town,
				index, resident.map(record -> record.number().toString()).orElse("none"));
		return true;
	}

	private static ShiftWorkerRecord moveOut(TownWorks works, ShiftWorkerBook book, ShiftWorkerRecord record) {
		works.addFormerResident(record.home(), record.number().toString());
		ShiftWorkerRecord out = record.withHome(ShiftWorkerRecord.HOMELESS);
		book.put(out);
		Industrialization.LOGGER.info("[ala-towns] settlement {}: worker {} moved out of building {}",
				record.settlement(), record.number(), record.home());
		return out;
	}

	private static void moveIn(ServerLevel level, int town, TownWorks works, ShiftWorkerBook book,
			ShiftWorkerRecord record, int index) {
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town, index);
		if (plan.isPresent() && works.building(index).map(b -> b.formerResidents().isEmpty()).orElse(false)) {
			ruinNumber(level, plan.get()).ifPresent(number -> works.addFormerResident(index, number.toString()));
		}
		ShiftWorkerRecord housed = record.withHome(index);
		book.put(housed);
		works.building(index).ifPresent(house -> TownChronicle.settled(level.getServer(), housed,
				TownHouses.displayName(house), level.getGameTime())); // MOD-886: the town's chronicle
		Industrialization.LOGGER.info("[ala-towns] settlement {}: worker {} moved into building {}", town,
				record.number(), index);
	}

	/** The plate cells of {@code plan}'s plaque in reading order, and the facing they read from. */
	private static List<BlockPos> plaque(BuildingPlan plan, Direction[] facing) {
		List<BlockPos> plates = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (want.is(ModTags.Blocks.TOWN_PLAQUE_PLATES) && want.hasProperty(HorizontalDirectionalBlock.FACING)) {
				plates.add(plan.pos(i));
				facing[0] = want.getValue(HorizontalDirectionalBlock.FACING);
			}
		}
		if (!plates.isEmpty()) {
			AbandonedLabPlacer.sortForReader(plates, facing[0]);
		}
		return plates;
	}

	/** The former resident the ruin's plaque was engraved with at generation (R40), the way the town placed it. */
	private static Optional<RecordNumber> ruinNumber(ServerLevel level, BuildingPlan plan) {
		List<BlockPos> plates = plaque(plan, new Direction[1]);
		if (plates.isEmpty()) {
			return Optional.empty();
		}
		BlockPos anchor = plates.getFirst();
		return Optional.of(RecordNumberLedger.formerResidentAt(level.getSeed(), anchor.getX(), anchor.getY(),
				anchor.getZ()));
	}

	/**
	 * One plate of a house's plaque that does not show what it should (R4-12).
	 *
	 * @param slot   the plate's place on the plaque, in reading order: 0 is the letter
	 * @param pos    the plate's cell
	 * @param wanted the plate it should be, facing as the one there now
	 */
	public record PlaquePlate(int slot, BlockPos pos, BlockState wanted) {
	}

	/**
	 * The plates of house {@code index}'s plaque a drone has to change (R4-12), in reading order; empty when the
	 * plaque reads right, the building is no house, or nobody ever lived in it. A plate cell that holds no plate, or
	 * whose chunk is not loaded, is passed: the repair puts a lost plate back before it is engraved.
	 */
	public static List<PlaquePlate> plaqueToEngrave(ServerLevel level, int town, int index) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownBuilding> building = registry.works().find(town).flatMap(works -> works.building(index));
		if (building.isEmpty() || !TownHouses.isHouse(building.get())) {
			return List.of();
		}
		Optional<RecordNumber> number = Optional.empty();
		boolean broken = false;
		for (ShiftWorkerRecord record : registry.workers().all()) {
			if (record.settlement() == town && record.housed() && record.home() == index
					&& record.status() != ShiftWorkerRecord.Status.DEAD) {
				number = Optional.of(record.number());
			}
		}
		if (number.isEmpty()) {
			List<String> former = building.get().formerResidents();
			number = former.isEmpty() ? Optional.empty() : RecordNumber.parse(former.getLast());
			broken = true;
		}
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town, index);
		if (number.isEmpty() || plan.isEmpty()) {
			return List.of();
		}
		Direction[] facing = new Direction[1];
		List<BlockPos> plates = plaque(plan.get(), facing);
		List<Block> digits = AbandonedLabPlacer.digits(number.get().digits());
		int first = Math.max(0, digits.size() - (plates.size() - 1));
		EngravedLetterPlateBlock letter = (EngravedLetterPlateBlock) ModContent.ENGRAVED_LETTER_PLATE.get();
		List<PlaquePlate> out = new ArrayList<>();
		for (int slot = 0; slot < plates.size(); slot++) {
			BlockPos pos = plates.get(slot);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState world = level.getBlockState(pos);
			if (!world.is(ModTags.Blocks.TOWN_PLAQUE_PLATES)) {
				continue;
			}
			Direction face = world.hasProperty(HorizontalDirectionalBlock.FACING)
					? world.getValue(HorizontalDirectionalBlock.FACING) : facing[0];
			BlockState wanted;
			if (slot == 0) {
				wanted = letter.plate(number.get().letter(), broken, face);
			} else if (first + slot - 1 < digits.size()) {
				wanted = digits.get(first + slot - 1).defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, face);
			} else {
				continue;
			}
			if (!world.equals(wanted)) {
				out.add(new PlaquePlate(slot, pos, wanted));
			}
		}
		return out;
	}
}
