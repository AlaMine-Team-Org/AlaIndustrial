package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.command.demo.energy.EnergyStand;
import dev.alaindustrial.command.demo.energy.EnergyStandCheck;
import dev.alaindustrial.command.demo.energy.EnergyStandReport;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * MOD-799: the energy test stand of {@code /ala demo energy}, built and checked headless with the very code the
 * command runs — {@link EnergyStand#build} and {@link EnergyStandCheck#start} over real server ticks.
 *
 * <ol>
 *   <li><b>Every bay keeps its rule</b> on the branch's energy core, and the build writes no cell twice — except
 *       the bays with a known finding ({@link EnergyStand#knownFinding}), which must still FAIL: a core change
 *       that fixes one turns this red until its entry is removed.</li>
 *   <li><b>The checker can fail.</b> Three faults are planted after the reset: a cable cut out of the control
 *       bay's bus (its machine goes dark), the cable under the last machine of the "machine on every cable"
 *       bay removed, and the near box of the storage-only line filled to the brim (the far box charges first).
 *       Exactly those three bays must come out FAIL, each on the rule the fault breaks.</li>
 * </ol>
 *
 * <p>The stand sits in the demo stand's 90×14×52 structure ({@code demo_stand_area}) with sky, at a 1-block
 * margin; the check's warm-up and window are the command's own.
 */
public final class EnergyStandScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EnergyStandScenarios::everyBayPasses, "mod799_energy_stand_every_bay_passes")
						.ticks(320).sky().structure(Industrialization.id("demo_stand_area")),
				RosterEntry.of(EnergyStandScenarios::plantedFaultsFail, "mod799_energy_stand_planted_faults_fail")
						.ticks(320).sky().structure(Industrialization.id("demo_stand_area")));

		private Roster() {}
	}

	private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
	/** Ticks after the start by which the check has reported; the roster's 320-tick limit leaves 50 more. */
	private static final int DONE = EnergyStandCheck.WARMUP + EnergyStandCheck.WINDOW + 10;

	private EnergyStandScenarios() {
	}

	private static AtomicReference<EnergyStandReport> buildAndCheck(GameTestHelper helper, Runnable plant) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		EnergyStand.build(helper.getLevel(), origin);
		if (!EnergyStand.lastBuildProblems().isEmpty()) {
			helper.fail("the energy stand writes a cell twice or outside its bay: " + EnergyStand.lastBuildProblems());
		}
		AtomicReference<EnergyStandReport> report = new AtomicReference<>();
		EnergyStandCheck.start(helper.getLevel(), origin, EnergyStandCheck.WARMUP, EnergyStandCheck.WINDOW,
				report::set);
		plant.run();
		return report;
	}

	public static void everyBayPasses(GameTestHelper helper) {
		AtomicReference<EnergyStandReport> report = buildAndCheck(helper, () -> { });
		helper.runAfterDelay(DONE, () -> {
			EnergyStandReport r = report.get();
			if (r == null) {
				helper.fail("the energy stand check did not report within " + DONE + " ticks");
				return;
			}
			Industrialization.LOGGER.info("energy stand gametest report:\n{}", r.text());
			StringBuilder failed = new StringBuilder();
			for (EnergyStandReport.BayResult bay : r.bays()) {
				boolean known = EnergyStand.knownFinding(bay.id()) != null;
				if (!bay.pass() && !known) {
					failed.append("\n").append(bay.id()).append(' ').append(bay.firstFailure());
				} else if (bay.pass() && known) {
					failed.append("\n").append(bay.id()).append(" now PASSES - remove its known finding: ")
							.append(EnergyStand.knownFinding(bay.id()));
				}
			}
			if (!failed.isEmpty()) {
				helper.fail("energy stand bays FAIL:" + failed);
				return;
			}
			helper.succeed();
		});
	}

	public static void plantedFaultsFail(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		ServerLevel level = helper.getLevel();
		AtomicReference<EnergyStandReport> report = buildAndCheck(helper, () -> {
			level.setBlockAndUpdate(EnergyStand.cell(origin, "E01", 2, 1, 2), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(EnergyStand.cell(origin, "E29", 4, 1, 2), Blocks.AIR.defaultBlockState());
			if (level.getBlockEntity(EnergyStand.cell(origin, "E15", 1, 1, 3)) instanceof EnergyBlockEntity box) {
				box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
			}
		});
		helper.runAfterDelay(DONE, () -> {
			EnergyStandReport r = report.get();
			if (r == null) {
				helper.fail("the energy stand check did not report within " + DONE + " ticks");
				return;
			}
			expectFail(helper, r, "E01", "M1 fed");
			expectFail(helper, r, "E29", "every machine fed");
			expectFail(helper, r, "E15", "B1 charges before B2");
			helper.succeed();
		});
	}

	private static void expectFail(GameTestHelper helper, EnergyStandReport r, String bay, String rule) {
		EnergyStandReport.BayResult result = r.bay(bay);
		if (result == null || result.pass() || !result.firstFailure().startsWith(rule)) {
			helper.fail("planted fault in " + bay + " should fail '" + rule + "', got "
					+ (result == null ? "no bay" : result.pass() ? "PASS" : result.firstFailure()));
		}
	}
}
