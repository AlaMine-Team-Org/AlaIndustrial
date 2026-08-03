package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * MOD-085 targeted L2 tests for the Enriched Uranium Torch — the two feature guarantees the parametric
 * {@link AlaCommonGameTest} carve-outs deliberately do not check: the light level and the wall variant's
 * drop. (Vanilla-behaviour torch invariants — occlusion, placement — are covered by the common suite.)
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link EnrichedUraniumTorchScenarios} ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a
 * delegation. That is what makes the SAME scenario run on BOTH loaders — NeoForge registers
 * it in {@code NeoForgeGameTests} — instead of only on whichever loader it happened to be
 * written in.
 */
public class EnrichedUraniumTorchGameTest {

	@GameTest
	public void torchesEmitVanillaTorchLight(GameTestHelper helper) {
		EnrichedUraniumTorchScenarios.torchesEmitVanillaTorchLight(helper);
	}

	@GameTest
	public void wallTorchDropsStandingTorch(GameTestHelper helper) {
		EnrichedUraniumTorchScenarios.wallTorchDropsStandingTorch(helper);
	}
}
