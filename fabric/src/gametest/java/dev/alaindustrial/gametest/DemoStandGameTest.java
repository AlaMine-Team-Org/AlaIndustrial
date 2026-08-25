package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * MOD-058/MOD-294 fabric wrapper: the stand scenarios live in {@link DemoStandScenarios} (common)
 * so the NeoForge lane runs the identical checks — this class only pins the fabric-side test
 * configuration. Runs in a custom 44×14×28 empty structure ({@code demo_stand_area.snbt}) because
 * the stand does not fit the default 8×8×8 envelope; sky access keeps the solar panels honest,
 * though their output is deliberately not asserted (test-world time of day is not fixed here).
 */
public class DemoStandGameTest {

	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 300, skyAccess = true)
	public void demoStandBuildsCoversAndRuns(GameTestHelper helper) {
		DemoStandScenarios.demoStandBuildsCoversAndRuns(helper);
	}

	/** {@code clear} removes every stand block and entity above the restored floor. */
	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 100, skyAccess = true)
	public void demoStandClearLeavesNoBlocks(GameTestHelper helper) {
		DemoStandScenarios.demoStandClearLeavesNoBlocks(helper);
	}

	/** MOD-294 rebuild×2: identical block multiset, no drops, frames exactly once per item. */
	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 100, skyAccess = true)
	public void demoStandRebuildIsIdempotent(GameTestHelper helper) {
		DemoStandScenarios.demoStandRebuildIsIdempotent(helper);
	}

	/** MOD-294 item showcase: every non-block registry item hangs in a glow frame. */
	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 100, skyAccess = true)
	public void demoStandShowcaseCoversItems(GameTestHelper helper) {
		DemoStandScenarios.demoStandShowcaseCoversItems(helper);
	}
}
