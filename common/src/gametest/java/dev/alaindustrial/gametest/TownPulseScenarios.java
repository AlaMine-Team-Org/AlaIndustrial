package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.network.TownPulsePayload;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ChronicleRules;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.shift.TownChronicle;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.TownNeeds;
import dev.alaindustrial.settlement.works.TownPulse;
import dev.alaindustrial.settlement.works.TownPulseService;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkQueue;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/**
 * L2 suite for the town's pulse and its chronicle (MOD-886, plan 0.1.220 blocks B2 and B3): a build job that waits for
 * materials makes the missing item the town's need; a worker's death writes exactly one line of the chronicle; the
 * 201st event of a town drops its oldest, and a repeat of the newest line is merged into it.
 */
public final class TownPulseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownPulseScenarios::missingMaterialsAreTheTownsNeed,
						"town_pulse_missing_materials_are_the_need").ticks(20),
				RosterEntry.of(TownPulseScenarios::aDeathWritesOneChronicleLine, "town_pulse_death_writes_one_line")
						.ticks(40),
				RosterEntry.of(TownPulseScenarios::theChronicleKeepsItsNewestLines,
						"town_pulse_chronicle_keeps_its_newest_lines").ticks(20));

		private Roster() {}
	}

	private TownPulseScenarios() {}

	private static final String GLASS = "block.minecraft.glass";
	private static final BlockPos STAND = new BlockPos(3, 1, 3);
	/** How many beats the cost of the pulse is measured over. */
	private static final int MEASURED_BEATS = 1000;

	/**
	 * @implements MOD-886-PULSE-MATERIALS — a town whose build job waits for glass needs glass, and says so first; the
	 *     pulse reads the job's reason, not the world. The cost of a beat is written to the log (task.md).
	 */
	public static void missingMaterialsAreTheTownsNeed(GameTestHelper helper) {
		int town = town(helper);
		TownWorks works = SettlementRegistry.get(helper.getLevel().getServer()).works().town(town);
		WorkQueue queue = works.queue();
		WorkJob job = queue.raise(WorkJobType.BUILD, 0).orElseThrow();
		helper.assertTrue(queue.take(job.id(), "test") == WorkQueue.Outcome.OK, "the build job was not taken");
		queue.block(job.id(), "test", BlockReason.noMaterials(List.of(ConstructionRow.Text.counted(6, GLASS))), 0);

		TownPulse.Reading reading = TownPulse.beat(helper.getLevel(), town, false);
		helper.assertTrue(!reading.needs().isEmpty() && reading.needs().getFirst().kind() == TownNeeds.Kind.MATERIALS
				&& reading.needs().getFirst().text().args().contains(ConstructionRow.Text.counted(6, GLASS)),
				"the town's first need must name the missing glass, it reads " + reading.needs());
		helper.assertTrue(TownPulse.latest(town, helper.getLevel().getGameTime()).equals(Optional.of(reading)),
				"the beat is the town's latest reading");

		// The cost on a town with many blocked jobs: the pulse only reads data, so a beat stays far below a tick.
		for (int i = 1; i < 64; i++) {
			WorkJob more = queue.raise(WorkJobType.BUILD, i).orElseThrow();
			queue.take(more.id(), "test");
			queue.block(more.id(), "test", BlockReason.noMaterials(List.of(ConstructionRow.Text.counted(i, GLASS))),
					0);
		}
		long total = 0L;
		for (int i = 0; i < MEASURED_BEATS; i++) {
			total += TownPulse.beat(helper.getLevel(), town, true).nanos();
		}
		// INFO on purpose: MOD-886 reads the pulse's cost from this line.
		Industrialization.LOGGER.info("[ala-towns] MOD-886 town pulse: {} ns a beat on average over {} beats, {} "
				+ "blocked jobs", total / MEASURED_BEATS, MEASURED_BEATS, queue.jobs().size());
		TownPulse.Reading busy = TownPulse.latest(town, helper.getLevel().getGameTime()).orElseThrow();
		helper.assertTrue(busy.needs().getFirst().kind() == TownNeeds.Kind.ENERGY
				&& busy.needs().get(1).kind() == TownNeeds.Kind.MATERIALS,
				"energy comes before materials, the needs read " + busy.needs());
		closeTown(helper, town);
		helper.succeed();
	}

	/**
	 * @implements MOD-886-CHRONICLE-DEATH — a worker's death writes exactly one line of its town's chronicle, the
	 *     newest, reading «died» about that worker.
	 */
	public static void aDeathWritesOneChronicleLine(GameTestHelper helper) {
		int town = town(helper);
		floor(helper);
		ShiftWorker worker = ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(STAND), town).orElseThrow();
		ShiftWorkerBook book = ShiftWorkers.book(helper.getLevel().getServer());
		List<ShiftWorkerBook.LogLine> arrived = book.log(town);
		helper.assertTrue(!arrived.isEmpty() && arrived.getLast().note()
				.map(note -> note.key().equals(TownChronicle.ARRIVED)).orElse(false),
				"the new worker's arrival must be the chronicle's newest line, it reads " + arrived);
		int before = arrived.size();
		worker.kill(helper.getLevel());
		List<ShiftWorkerBook.LogLine> after = book.log(town);
		helper.assertTrue(after.size() == before + 1 && after.getLast().death().isPresent(),
				"the death must add exactly one line, the chronicle went from " + before + " to " + after.size()
						+ ": " + after);
		List<TownPulsePayload.Line> shown = TownPulseService.chronicle(book, town);
		helper.assertTrue(shown.getFirst().text().key().equals(TownChronicle.DIED)
				&& !shown.getFirst().worker().isEmpty() && shown.getFirst().day() >= 1,
				"the tab's newest line must read the death of the worker, it reads " + shown.getFirst());
		closeTown(helper, town);
		helper.succeed();
	}

	/**
	 * @implements MOD-886-CHRONICLE-TRIM — a town keeps its newest {@link ChronicleRules#CAPACITY} lines: the 201st
	 *     event drops the oldest; a repeat of the newest line is merged into it, counted.
	 */
	public static void theChronicleKeepsItsNewestLines(GameTestHelper helper) {
		int town = town(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ShiftWorkerBook book = ShiftWorkers.book(server);
		for (int i = 0; i <= ChronicleRules.CAPACITY; i++) {
			TownChronicle.inletBroken(server, town, "house " + i, i);
		}
		List<ShiftWorkerBook.LogLine> lines = book.log(town);
		helper.assertTrue(lines.size() == ChronicleRules.CAPACITY, "the town keeps " + lines.size() + " lines");
		helper.assertTrue(lines.getFirst().note().map(note -> note.args().equals(List.of("house 1"))).orElse(false),
				"the oldest line went first, the chronicle starts with " + lines.getFirst());
		boolean added = TownChronicle.record(server, town, null, TownChronicle.INLET_BROKEN, 500L,
				"house " + ChronicleRules.CAPACITY);
		List<ShiftWorkerBook.LogLine> merged = book.log(town);
		helper.assertTrue(!added && merged.size() == ChronicleRules.CAPACITY
				&& TownChronicle.repeats(merged.getLast()) == 2 && TownChronicle.gameTime(merged.getLast()) == 500L,
				"a repeat must merge into the newest line, it reads " + merged.getLast());
		closeTown(helper, town);
		helper.succeed();
	}

	private static int town(GameTestHelper helper) {
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = registry.place(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)), 0)
				.settlementId();
		registry.setActive(town, true);
		return town;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Leaves the test's town dark, so its territory holds nothing for the scenarios that run after this one. */
	private static void closeTown(GameTestHelper helper, int town) {
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
	}
}
