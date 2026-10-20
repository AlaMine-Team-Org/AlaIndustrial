package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Verdict;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTier;
import dev.alaindustrial.settlement.ShiftWorkerHost;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the reception capsule (MOD-833, suite TC-CAPS-001), replayed by both loaders through
 * the roster (ADR-038): with its town's core lit and claimed, room under the tier's limit, the EU and the materials
 * in, the capsule releases exactly one real shift worker (MOD-832) standing in it and takes the price exactly once;
 * every refusal — the core dark, away or out of reach, the town unclaimed or full, a material or EU short, a worker
 * that could not be put into the world — takes nothing; a restoration halted by a dark core keeps its progress;
 * with permadeath off a dead worker comes back for double the price; only the town's owner breaks the capsule, and
 * it drops as one item. {@code ala_town_hibernated_costs_nothing} checks the town's hibernation on a worker the
 * capsule released.
 *
 * <p><b>Each case serves its own town.</b> The capsule keeps the town with the nearest core around it, and every rig
 * places its core two blocks from its capsule and ticks the core first — an LV territory is 65 blocks wide and
 * reaches into the neighbouring rigs, so "whichever town holds this block" would not be this case's town. Each
 * case ends by sending its workers out of the world and taking its core away, so its town holds nothing for the
 * cases after it.
 */
public final class ReceptionCapsuleScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun01ReleasesExactlyOneWorker,
						"reception_capsule_releases_exactly_one_worker").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun02RefusalsTakeNothing,
						"reception_capsule_refusals_take_nothing").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun03NoCoreOrOutsideTakesNothing,
						"reception_capsule_no_core_or_outside_takes_nothing").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun04SilentArchiveTakesNothing,
						"reception_capsule_silent_archive_takes_nothing").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun05DarkCoreKeepsTheProgress,
						"reception_capsule_dark_core_keeps_the_progress").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Fun06DeadComesBackForDouble,
						"reception_capsule_dead_comes_back_for_double").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Brk01OnlyTheOwnerBreaksIntoOneItem,
						"reception_capsule_only_owner_breaks_into_one_item").ticks(40),
				RosterEntry.of(ReceptionCapsuleScenarios::tcCaps001Hib01HibernatedTownCostsNothing,
						"ala_town_hibernated_costs_nothing").ticks(160));

		private Roster() {}
	}

	private ReceptionCapsuleScenarios() {}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	private static final BlockPos CAPSULE = new BlockPos(3, 2, 1);
	private static final UUID OWNER = UUID.nameUUIDFromBytes("capsule-owner".getBytes(StandardCharsets.UTF_8));

	/** A host that makes nothing: the worker cannot be put into the world (FUN04). */
	private static final class RefusingHost implements ShiftWorkerHost {
		int asked;

		@Override
		public int livingWorkers(MinecraftServer server, int settlementId) {
			return ShiftWorkers.livingWorkers(server, settlementId);
		}

		@Override
		public List<RecordNumber> awaitingReturn(MinecraftServer server, int settlementId) {
			return List.of();
		}

		@Override
		public Optional<RecordNumber> releaseNew(ServerLevel level, Vec3 standAt, float yaw, Settlement town) {
			asked++;
			return Optional.empty();
		}

		@Override
		public boolean bringBack(ServerLevel level, Vec3 standAt, float yaw, RecordNumber number, Settlement town) {
			asked++;
			return false;
		}
	}

	/** A rig: a stone floor, a core two blocks west of a whole capsule, the core registered. */
	private record Rig(SettlementCoreBlockEntity core, ReceptionCapsuleBlockEntity capsule) {
		int town() {
			return core.settlementId();
		}
	}

	private static Rig rig(GameTestHelper helper, boolean lit, boolean claimed) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		SettlementCoreBlockEntity core = AlaGameTestHelper.place(helper, CORE, ModContent.SETTLEMENT_CORE.get(),
				SettlementCoreBlockEntity.class);
		if (lit) {
			chargeCore(core, core.getEnergyStorage().getCapacity());
		}
		AlaGameTestHelper.drive(core, helper, 1);
		if (claimed && core.claim(OWNER, "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			helper.fail("control: the lit core could not be claimed");
		}
		ReceptionCapsuleBlock.placeWhole(helper.getLevel(), helper.absolutePos(CAPSULE), Direction.NORTH,
				ModContent.RECEPTION_CAPSULE.get());
		return new Rig(core, helper.getBlockEntity(CAPSULE, ReceptionCapsuleBlockEntity.class));
	}

	/** The town's workers leave the world and its core is taken away: the town holds nothing any more. */
	private static void finish(GameTestHelper helper, int town) {
		ServerLevel level = helper.getLevel();
		for (ShiftWorkerRecord record : ShiftWorkers.book(level.getServer()).all()) {
			if (record.settlement() == town && level.getEntity(record.entity()) instanceof ShiftWorker worker) {
				worker.discard();
			}
		}
		helper.setBlock(CORE, Blocks.AIR);
	}

	private static void chargeCore(SettlementCoreBlockEntity core, long amount) {
		core.getEnergyStorage().setAmountUntracked(amount);
		core.wake();
	}

	/** The capsule's buffer at {@code amount} and {@code workers} workers' worth of every material in its slots. */
	private static void stock(ReceptionCapsuleBlockEntity capsule, long amount, int workers) {
		capsule.getEnergyStorage().setAmountUntracked(amount);
		for (Material material : Material.values()) {
			int count = material.perWorker() * workers;
			capsule.setItem(ReceptionCapsuleBlockEntity.slotOf(material),
					count > 0 ? new ItemStack(ReceptionCapsuleBlockEntity.itemOf(material), count) : ItemStack.EMPTY);
		}
		capsule.wake();
	}

	/** EU and every slot's count, to compare before and after a refusal. */
	private static String contents(ReceptionCapsuleBlockEntity capsule) {
		StringBuilder out = new StringBuilder("eu=").append(capsule.getEnergyStorage().getAmount());
		for (Material material : Material.values()) {
			out.append(' ').append(material).append('=')
					.append(capsule.getItem(ReceptionCapsuleBlockEntity.slotOf(material)).getCount());
		}
		return out.toString();
	}

	/** The living workers of {@code town}, as the shift workers' service counts them. */
	private static int living(GameTestHelper helper, int town) {
		return ShiftWorkers.livingWorkers(helper.getLevel().getServer(), town);
	}

	/**
	 * What keeps the worker from standing whole inside the capsule at {@code lower}, or {@code null} (MOD-850): the
	 * column its body takes — its hitbox's footprint, from its feet up to the highest its model reaches
	 * ({@link ReceptionCapsuleBlock#WORKER_REACH_PX}, taller than the hitbox) — meets none of the capsule's cells, and
	 * the capsule's roof is above it. In the two-block flask the helmet came through the roof.
	 */
	private static String bodyUnderTheRoof(ServerLevel level, BlockPos lower, ShiftWorker worker) {
		AABB body = worker.getBoundingBox().setMaxY(worker.getY() + ReceptionCapsuleBlock.WORKER_REACH_PX / 16.0);
		boolean roofed = false;
		for (ReceptionCapsuleBlock.Part part : ReceptionCapsuleBlock.Part.values()) {
			BlockPos cell = lower.above(part.aboveLower());
			for (AABB piece : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
				AABB placed = piece.move(cell);
				if (placed.intersects(body)) {
					return "the worker's body " + body + " runs into the capsule's " + part.getSerializedName()
							+ " cell at " + placed;
				}
				roofed |= placed.minX < body.maxX && placed.maxX > body.minX && placed.minZ < body.maxZ
						&& placed.maxZ > body.minZ && placed.minY >= body.maxY;
			}
		}
		return roofed ? null : "no roof of the capsule over the worker's body " + body;
	}

	/** The loaded worker whose record holds {@code number}, or null. */
	private static ShiftWorker worker(GameTestHelper helper, RecordNumber number) {
		Optional<ShiftWorkerRecord> record = ShiftWorkers.book(helper.getLevel().getServer()).byNumber(number);
		Entity entity = record.map(r -> helper.getLevel().getEntity(r.entity())).orElse(null);
		return entity instanceof ShiftWorker worker ? worker : null;
	}

	/** The numbers of the town's living workers, in the order they were made. */
	private static List<RecordNumber> livingNumbers(GameTestHelper helper, int town) {
		List<RecordNumber> out = new ArrayList<>();
		for (ShiftWorkerRecord record : ShiftWorkers.book(helper.getLevel().getServer()).all()) {
			if (record.settlement() == town && record.status() == ShiftWorkerRecord.Status.ALIVE) {
				out.add(record.number());
			}
		}
		return out;
	}

	/** Drives the capsule for a whole restoration and a little more. */
	private static void driveRestoration(ReceptionCapsuleBlockEntity capsule, GameTestHelper helper) {
		AlaGameTestHelper.drive(capsule, helper, ReceptionCapsuleRules.restoreTicks() + 5);
	}

	/**
	 * Drives a capsule that must refuse with {@code expected} for a whole restoration and fails unless no worker came
	 * out and nothing was taken.
	 */
	private static boolean refusesTakingNothing(GameTestHelper helper, Rig rig, Verdict expected, String phase) {
		String before = contents(rig.capsule());
		int living = living(helper, rig.town());
		driveRestoration(rig.capsule(), helper);
		String after = contents(rig.capsule());
		if (rig.capsule().verdict() != expected) {
			helper.fail(phase + ": the capsule should refuse with " + expected + ", says " + rig.capsule().verdict());
			return false;
		}
		if (living(helper, rig.town()) != living || !before.equals(after)) {
			helper.fail(phase + ": a refusal took something or released a worker — before " + before + ", after "
					+ after + ", workers " + living + " -> " + living(helper, rig.town()));
			return false;
		}
		return true;
	}

	/**
	 * @implements TC-CAPS-001-FUN01 — a lit, claimed town with room, the price in: one restoration releases exactly
	 *     one shift worker of the town, standing on the capsule's floor and facing out of it, with a living number in
	 *     the ledger; it takes exactly one price of EU and of every material; the next restoration does not finish
	 *     early. From its first tick the menu shows at least one percent of it (MOD-847).
	 */
	public static void tcCaps001Fun01ReleasesExactlyOneWorker(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		long price = ReceptionCapsuleRules.euPrice(false);
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 2);
		AlaGameTestHelper.drive(capsule, helper, 1);
		int shown = capsule.getDataAccess().get(ReceptionCapsuleBlockEntity.Channel.PROGRESS.ordinal());
		int scale = capsule.getDataAccess().get(ReceptionCapsuleBlockEntity.Channel.MAX_PROGRESS.ordinal());
		if (scale != ReceptionCapsuleRules.SHOWN_SCALE || shown < ReceptionCapsuleRules.SHOWN_FLOOR) {
			helper.fail("MOD-847: a started restoration must show at least 1 % in the menu channel, shows " + shown
					+ " of " + scale);
			return;
		}
		AlaGameTestHelper.drive(capsule, helper, ReceptionCapsuleRules.restoreTicks() - 2);
		if (living(helper, rig.town()) != 0) {
			helper.fail("a worker came out before the restoration finished");
			return;
		}
		AlaGameTestHelper.drive(capsule, helper, 1);
		List<RecordNumber> numbers = livingNumbers(helper, rig.town());
		if (numbers.size() != 1) {
			helper.fail("one restoration must release exactly one worker of the town, released " + numbers
					+ " (verdict " + capsule.verdict() + ")");
			return;
		}
		long expectedEu = capsule.getEnergyStorage().getCapacity() - price;
		if (capsule.getEnergyStorage().getAmount() != expectedEu) {
			helper.fail("one worker must cost exactly " + price + " EU: buffer "
					+ capsule.getEnergyStorage().getAmount() + ", expected " + expectedEu);
			return;
		}
		for (Material material : Material.values()) {
			int left = capsule.getItem(ReceptionCapsuleBlockEntity.slotOf(material)).getCount();
			if (left != material.perWorker()) {
				helper.fail("one worker must cost exactly " + material.perWorker() + " " + material + ", "
						+ left + " left of " + 2 * material.perWorker());
				return;
			}
		}
		RecordNumber number = numbers.get(0);
		if (RecordNumberLedger.get(helper.getLevel().getServer()).statusOf(number) != RecordNumber.Status.LIVE) {
			helper.fail("the released worker's number " + number + " must be a living one in the ledger");
			return;
		}
		ShiftWorker worker = worker(helper, number);
		BlockPos at = helper.absolutePos(CAPSULE);
		Vec3 floor = new Vec3(at.getX() + 0.5, at.getY() + 1.0 / 16.0, at.getZ() + 0.5);
		if (worker == null || worker.position().distanceTo(floor) > 1e-6
				|| Math.abs(worker.getYRot() - Direction.NORTH.toYRot()) > 1e-3) {
			helper.fail("the worker must stand on the capsule's floor facing out of it: "
					+ (worker == null ? "no worker" : worker.position() + " yaw " + worker.getYRot()));
			return;
		}
		String cramped = bodyUnderTheRoof(helper.getLevel(), at, worker);
		if (cramped != null) {
			helper.fail("MOD-850: " + cramped);
			return;
		}
		AlaGameTestHelper.drive(capsule, helper, ReceptionCapsuleRules.restoreTicks() - 1);
		if (living(helper, rig.town()) != 1) {
			helper.fail("the second restoration finished early: " + livingNumbers(helper, rig.town()));
			return;
		}
		finish(helper, rig.town());
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-FUN02 — the same capsule, full of EU and materials, refuses and takes nothing while the
	 *     core is dark, the town unclaimed, a material short, the EU short or the town at its tier's limit — each with
	 *     its own reason; with permadeath on a dead worker does not wait to come back; one below the limit it releases.
	 */
	public static void tcCaps001Fun02RefusalsTakeNothing(GameTestHelper helper) {
		Rig rig = rig(helper, false, false);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		if (!refusesTakingNothing(helper, rig, Verdict.DARK, "dark core")) {
			return;
		}
		chargeCore(rig.core(), rig.core().getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(rig.core(), helper, 1);
		if (!refusesTakingNothing(helper, rig, Verdict.UNCLAIMED, "unclaimed town")) {
			return;
		}
		if (rig.core().claim(OWNER, "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			helper.fail("control: the lit core could not be claimed");
			return;
		}
		capsule.setItem(ReceptionCapsuleBlockEntity.RUBBER_SLOT,
				new ItemStack(ModContent.RUBBER.get(), Material.RUBBER.perWorker() - 1));
		if (!refusesTakingNothing(helper, rig, Verdict.NEED_MATERIALS, "one rubber short")) {
			return;
		}
		stock(capsule, ReceptionCapsuleRules.euPrice(false) - 1, 1);
		if (!refusesTakingNothing(helper, rig, Verdict.NEED_ENERGY, "one EU short")) {
			return;
		}
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		int limit = SettlementTier.LV.workerLimit();
		List<ShiftWorker> made = new ArrayList<>();
		for (int i = 0; i < limit; i++) {
			made.add(ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(1 + i, 2, 5)), rig.town())
					.orElseThrow(() -> helper.assertionException("control: no worker could be made")));
		}
		if (!refusesTakingNothing(helper, rig, Verdict.AT_LIMIT, "town at its limit of " + limit)) {
			return;
		}
		made.get(0).kill(helper.getLevel());
		capsule.nextChoice();
		if (capsule.returning().isPresent()) {
			helper.fail("with permadeath on, a dead worker must not wait to come back: " + capsule.returning());
			return;
		}
		capsule.wake();
		driveRestoration(capsule, helper);
		if (living(helper, rig.town()) != limit) {
			helper.fail("one below the limit, with everything in, the capsule must release a worker; verdict "
					+ capsule.verdict() + ", living " + living(helper, rig.town()));
			return;
		}
		finish(helper, rig.town());
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-FUN03 — a capsule whose town's core is taken away refuses as "no core", and one left
	 *     outside its town's territory when the core moved refuses as "outside"; neither takes anything.
	 */
	public static void tcCaps001Fun03NoCoreOrOutsideTakesNothing(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		AlaGameTestHelper.drive(capsule, helper, 1);
		int town = capsule.settlementId();
		if (town != rig.town()) {
			helper.fail("control: the capsule must serve the town of the core beside it, serves " + town);
			return;
		}
		helper.setBlock(CORE, Blocks.AIR);
		if (!refusesTakingNothing(helper, rig, Verdict.CORE_AWAY, "core taken away")) {
			return;
		}
		ServerLevel level = helper.getLevel();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		BlockPos moved = helper.absolutePos(CORE).offset(-SettlementTier.LV.radius(), 0, 0);
		if (!registry.place(level.dimension(), moved, town).home()) {
			helper.fail("control: the town's core could not be placed back inside its territory");
			return;
		}
		registry.setActive(town, true);
		if (!refusesTakingNothing(helper, rig, Verdict.OUTSIDE, "capsule outside the moved territory")) {
			return;
		}
		registry.coreRemoved(level.dimension(), moved);
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-FUN04 — when the worker cannot be put into the world, the restoration takes nothing,
	 *     says the archive does not answer, and starts over.
	 */
	public static void tcCaps001Fun04SilentArchiveTakesNothing(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		RefusingHost host = new RefusingHost();
		capsule.useHost(host);
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		String before = contents(capsule);
		AlaGameTestHelper.drive(capsule, helper, ReceptionCapsuleRules.restoreTicks());
		if (host.asked != 1 || capsule.verdict() != Verdict.ARCHIVE_SILENT || !before.equals(contents(capsule))) {
			helper.fail("a worker that could not be put into the world must take nothing: asked " + host.asked
					+ ", verdict " + capsule.verdict() + ", before " + before + ", after " + contents(capsule));
			return;
		}
		if (capsule.restoreProgress() != 0) {
			helper.fail("the failed restoration must start over, progress " + capsule.restoreProgress());
			return;
		}
		finish(helper, rig.town());
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-FUN05 — a restoration halted by the core going dark keeps its progress and takes
	 *     nothing; once the core is lit again it goes on from there and releases one worker.
	 */
	public static void tcCaps001Fun05DarkCoreKeepsTheProgress(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		int half = ReceptionCapsuleRules.restoreTicks() / 2;
		AlaGameTestHelper.drive(capsule, helper, half);
		chargeCore(rig.core(), 0);
		AlaGameTestHelper.drive(rig.core(), helper, SettlementConfig.settlementCoreGraceTicks);
		if (rig.core().isActive()) {
			helper.fail("control: the core did not go dark");
			return;
		}
		String before = contents(capsule);
		AlaGameTestHelper.drive(capsule, helper, half + 10);
		if (capsule.verdict() != Verdict.DARK || capsule.restoreProgress() != half
				|| !before.equals(contents(capsule)) || living(helper, rig.town()) != 0) {
			helper.fail("a dark core must halt the restoration where it was: verdict " + capsule.verdict()
					+ ", progress " + capsule.restoreProgress() + " of " + half + ", contents " + contents(capsule));
			return;
		}
		chargeCore(rig.core(), rig.core().getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(rig.core(), helper, 1);
		// A halted capsule sleeps and looks again within IDLE_SLEEP_TICKS; the core lighting does not wake it, so
		// the case wakes it to count the remaining ticks exactly.
		capsule.wake();
		AlaGameTestHelper.drive(capsule, helper, ReceptionCapsuleRules.restoreTicks() - half);
		if (living(helper, rig.town()) != 1) {
			helper.fail("the lit core must let the restoration finish from where it stopped; workers "
					+ livingNumbers(helper, rig.town()) + ", progress " + capsule.restoreProgress());
			return;
		}
		finish(helper, rig.town());
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-FUN06 — with permadeath off (epic decision R39) a worker who died waits; the capsule's
	 *     choice moves to it and back to "new"; brought back, the worker has the same number and look in a new body,
	 *     alive in the ledger, and the capsule took exactly twice the price of a new worker.
	 */
	public static void tcCaps001Fun06DeadComesBackForDouble(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		driveRestoration(capsule, helper);
		List<RecordNumber> numbers = livingNumbers(helper, rig.town());
		if (numbers.size() != 1) {
			helper.fail("control: the capsule released no worker: " + capsule.verdict());
			return;
		}
		RecordNumber number = numbers.get(0);
		ShiftWorker first = worker(helper, number);
		ShiftWorkerRecord before = ShiftWorkers.book(helper.getLevel().getServer()).byNumber(number).orElseThrow();
		try (ConfigOverrides overrides = ConfigOverrides.sync()) {
			overrides.set("shiftWorkerPermadeath", false);
			first.kill(helper.getLevel());
		}
		capsule.nextChoice();
		if (!capsule.returning().equals(Optional.of(number))) {
			helper.fail("the choice must move to the dead worker " + number + ", it is " + capsule.returning());
			return;
		}
		capsule.nextChoice();
		if (capsule.returning().isPresent()) {
			helper.fail("past the last of the dead the choice must come back to a new worker: " + capsule.returning());
			return;
		}
		capsule.nextChoice();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 2);
		driveRestoration(capsule, helper);
		ShiftWorkerRecord after = ShiftWorkers.book(helper.getLevel().getServer()).byNumber(number).orElseThrow();
		ShiftWorker back = worker(helper, number);
		if (after.status() != ShiftWorkerRecord.Status.ALIVE || back == null || back == first
				|| !after.look().equals(before.look()) || after.entity().equals(before.entity())) {
			helper.fail("the dead worker must come back alive in a new body with its number and look: " + after);
			return;
		}
		if (RecordNumberLedger.get(helper.getLevel().getServer()).statusOf(number) != RecordNumber.Status.LIVE) {
			helper.fail("the worker brought back must keep a living number");
			return;
		}
		long left = capsule.getEnergyStorage().getCapacity() - ReceptionCapsuleRules.euPrice(true);
		if (capsule.getEnergyStorage().getAmount() != left || !capsule.isEmpty()) {
			helper.fail("bringing a worker back must cost exactly twice a new one: " + contents(capsule)
					+ ", expected eu=" + left + " and empty slots");
			return;
		}
		if (living(helper, rig.town()) != 1) {
			helper.fail("bringing a worker back must not add a second one: " + livingNumbers(helper, rig.town()));
			return;
		}
		finish(helper, rig.town());
		helper.succeed();
	}

	/**
	 * @implements TC-CAPS-001-BRK01 — the capsule is unbreakable by hardness; a stranger with a pickaxe and the owner
	 *     bare-handed make no progress on any of its three cells; the town's owner breaks the roof with a pickaxe, all
	 *     three cells go, exactly one capsule item drops and the materials inside come out with it.
	 */
	public static void tcCaps001Brk01OnlyTheOwnerBreaksIntoOneItem(GameTestHelper helper) {
		SettlementCoreBlockEntity core = AlaGameTestHelper.place(helper, CORE, ModContent.SETTLEMENT_CORE.get(),
				SettlementCoreBlockEntity.class);
		chargeCore(core, core.getEnergyStorage().getCapacity());
		AlaGameTestHelper.drive(core, helper, 1);
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.mockPlayerInLevel(helper);
		if (core.claim(owner.getUUID(), "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			helper.fail("control: the lit core could not be claimed");
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(CAPSULE);
		BlockPos upper = lower.above(ReceptionCapsuleBlock.Part.UPPER.aboveLower());
		List<BlockPos> cells = List.of(lower, lower.above(ReceptionCapsuleBlock.Part.MIDDLE.aboveLower()), upper);
		ReceptionCapsuleBlock.placeWhole(level, lower, Direction.NORTH, ModContent.RECEPTION_CAPSULE.get());
		ReceptionCapsuleBlockEntity capsule = helper.getBlockEntity(CAPSULE, ReceptionCapsuleBlockEntity.class);
		capsule.setItem(ReceptionCapsuleBlockEntity.RUBBER_SLOT, new ItemStack(ModContent.RUBBER.get(), 3));
		AlaGameTestHelper.drive(capsule, helper, 1);
		if (level.getBlockState(lower).getDestroySpeed(level, lower) >= 0) {
			helper.fail("the capsule must be unbreakable by hardness, so tools that skip such blocks skip it");
			return;
		}
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
		for (BlockPos cell : cells) {
			BlockState state = level.getBlockState(cell);
			if (state.getDestroyProgress(stranger, level, cell) != 0.0F
					|| state.getDestroyProgress(owner, level, cell) != 0.0F) {
				helper.fail("a stranger with a pickaxe or the owner bare-handed made progress on " + cell);
				return;
			}
		}
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
		for (BlockPos cell : cells) {
			if (level.getBlockState(cell).getDestroyProgress(owner, level, cell) <= 0.0F) {
				helper.fail("the owner with a wooden pickaxe must be able to break " + cell);
				return;
			}
		}
		if (!owner.gameMode.destroyBlock(upper)) {
			helper.fail("the owner could not break the roof");
			return;
		}
		for (BlockPos cell : cells) {
			if (!level.getBlockState(cell).isAir()) {
				helper.fail("all three cells must go with the roof: " + cell + " is " + level.getBlockState(cell));
				return;
			}
		}
		AABB around = new AABB(lower).inflate(2);
		int capsules = level.getEntitiesOfClass(ItemEntity.class, around,
				drop -> drop.getItem().is(ModContent.RECEPTION_CAPSULE_ITEM.get())).stream()
				.mapToInt(drop -> drop.getItem().getCount()).sum();
		int rubber = level.getEntitiesOfClass(ItemEntity.class, around,
				drop -> drop.getItem().is(ModContent.RUBBER.get())).stream()
				.mapToInt(drop -> drop.getItem().getCount()).sum();
		if (capsules != 1 || rubber != 3) {
			helper.fail("breaking the capsule must drop exactly one capsule and its 3 rubber: capsules " + capsules
					+ ", rubber " + rubber);
			return;
		}
		helper.setBlock(CORE, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * Structural, not by timing (ADR-050 point 10): a worker the capsule released goes into hibernation when its
	 * town's core goes dark — frozen, so the game runs none of its goals, its navigation or its planner — and stands
	 * exactly where it stood, not falling, not turning, not hurt, for forty ticks; when the core lights again the town
	 * wakes it.
	 *
	 * @implements TC-CAPS-001-HIB01 — the hibernated town costs nothing (ala_town_hibernated_costs_nothing)
	 */
	public static void tcCaps001Hib01HibernatedTownCostsNothing(GameTestHelper helper) {
		Rig rig = rig(helper, true, true);
		ReceptionCapsuleBlockEntity capsule = rig.capsule();
		stock(capsule, capsule.getEnergyStorage().getCapacity(), 1);
		driveRestoration(capsule, helper);
		List<RecordNumber> numbers = livingNumbers(helper, rig.town());
		ShiftWorker worker = numbers.size() == 1 ? worker(helper, numbers.get(0)) : null;
		if (worker == null) {
			helper.fail("control: the capsule released no worker: " + capsule.verdict());
			return;
		}
		chargeCore(rig.core(), 0);
		AlaGameTestHelper.drive(rig.core(), helper, SettlementConfig.settlementCoreGraceTicks);
		if (rig.core().isActive()) {
			helper.fail("control: the core did not go dark");
			return;
		}
		// MOD-854, R68: a dark core's worker works on its battery and sleeps only once it is empty.
		ShiftWorkerRecord charged = ShiftWorkers.book(helper.getLevel().getServer()).byEntity(worker.getUUID())
				.orElseThrow(() -> helper.assertionException("control: the released worker has no record"));
		ShiftWorkers.book(helper.getLevel().getServer()).put(charged.withProfile(charged.profile().withCharge(0)));
		Vec3[] stood = new Vec3[1];
		float[] look = new float[2];
		float[] health = new float[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(worker.isNoAi(), "a dark town's worker still runs its goals"))
				.thenExecute(() -> {
					stood[0] = worker.position();
					look[0] = worker.getYRot();
					look[1] = worker.getYHeadRot();
					health[0] = worker.getHealth();
				})
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(worker.isNoAi() && !worker.isEffectiveAi(),
							"a hibernating worker must run no goals at all");
					helper.assertTrue(worker.getNavigation().isDone(), "a hibernating worker still has a path");
					helper.assertTrue(worker.position().equals(stood[0]),
							"a hibernating worker moved: " + stood[0] + " -> " + worker.position());
					helper.assertTrue(worker.getYRot() == look[0] && worker.getYHeadRot() == look[1],
							"a hibernating worker turned its body or head");
					helper.assertTrue(worker.fallDistance == 0 && worker.getHealth() == health[0],
							"a hibernating worker fell or was hurt: fall " + worker.fallDistance + ", health "
									+ worker.getHealth());
					chargeCore(rig.core(), rig.core().getEnergyStorage().getCapacity());
					AlaGameTestHelper.drive(rig.core(), helper, 1);
					helper.assertTrue(!worker.isNoAi(), "the lit core did not wake its worker");
					finish(helper, rig.town());
					helper.succeed();
				});
	}
}
