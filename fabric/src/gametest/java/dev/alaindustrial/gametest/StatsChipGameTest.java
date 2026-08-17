package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the statistics chip (MOD-125).
 *
 * <p>The scenario bodies live in {@link StatsChipScenarios} ({@code common/src/gametest}); what stays
 * here is the Fabric wiring only, so the SAME scenario runs on BOTH loaders (NeoForge registers it in
 * {@code NeoForgeGameTests}).
 */
public class StatsChipGameTest {

	@GameTest
	public void statsChip_withoutChipNothingIsMeasured(GameTestHelper helper) {
		StatsChipScenarios.statsChip_withoutChipNothingIsMeasured(helper);
	}

	@GameTest
	public void statsChip_withChipTheCountersRun(GameTestHelper helper) {
		StatsChipScenarios.statsChip_withChipTheCountersRun(helper);
	}

	@GameTest
	public void statsChip_countingStartsWhenTheChipIsFitted(GameTestHelper helper) {
		StatsChipScenarios.statsChip_countingStartsWhenTheChipIsFitted(helper);
	}

	@GameTest
	public void statsChip_removingTheChipKeepsWhatWasCounted(GameTestHelper helper) {
		StatsChipScenarios.statsChip_removingTheChipKeepsWhatWasCounted(helper);
	}

	/** MOD-440: a chip must also count on a machine that owns its tick loop (alloy smelter). */
	@GameTest(maxTicks = 400)
	public void statsChip_countersRunOnHandRolledMachine(GameTestHelper helper) {
		StatsChipScenarios.statsChip_countersRunOnHandRolledMachine(helper);
	}
}
