package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 server game tests for the ten material ore blocks (tin/silver/nickel/sulfur/uranium ×
 * stone/deepslate) — TC-ORE-001-*.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link OreScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation,
 * so the SAME scenario runs on BOTH loaders (NeoForge registers it in {@code NeoForgeGameTests}).
 *
 * <p>See docs/testing/blocks/materials/ores.md for the case table and covered rules.
 */
public class OreGameTest {

	@GameTest
	public void tcOre001Brk01_dropsItselfWithPickaxe(GameTestHelper helper) {
		OreScenarios.tcOre001Brk01_dropsItselfWithPickaxe(helper);
	}

	@GameTest
	public void tcOre001Brk03_noDropByHandAxeOrShovel(GameTestHelper helper) {
		OreScenarios.tcOre001Brk03_noDropByHandAxeOrShovel(helper);
	}

	@GameTest
	public void tcOre001Brk02_pickaxeTierGate(GameTestHelper helper) {
		OreScenarios.tcOre001Brk02_pickaxeTierGate(helper);
	}

	@GameTest
	public void tcOre001Brk04_fortuneAndSilkTouchNeutral(GameTestHelper helper) {
		OreScenarios.tcOre001Brk04_fortuneAndSilkTouchNeutral(helper);
	}

	@GameTest
	public void tcOre001Brk05_hardnessStoneVsDeepslate(GameTestHelper helper) {
		OreScenarios.tcOre001Brk05_hardnessStoneVsDeepslate(helper);
	}

	@GameTest
	public void tcOre001Phy02_fullCubeHitbox(GameTestHelper helper) {
		OreScenarios.tcOre001Phy02_fullCubeHitbox(helper);
	}

	@GameTest
	public void tcOre001Phy04_nonFlammable(GameTestHelper helper) {
		OreScenarios.tcOre001Phy04_nonFlammable(helper);
	}
}
