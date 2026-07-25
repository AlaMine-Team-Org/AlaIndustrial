package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Forge Hammer (MOD-078). Thin Fabric wrappers: the bodies are
 * loader-neutral in {@code common/.../gametest/HammerScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}), so both loaders exercise the identical
 * craft-remainder mechanic (the hammer stays in the grid, one durability per plate, breaks on its last
 * point) through {@code CraftingRecipe.defaultCraftingReminder}.
 */
public class HammerGameTest {

	/** @implements TC-HAMMER-001-FUN01 — a fresh hammer stays in the grid and comes back with 1 damage. */
	@GameTest
	public void fun01_hammerStaysWithOneDamage(GameTestHelper helper) {
		HammerScenarios.fun01HammerStaysWithOneDamage(helper);
	}

	/** @implements TC-HAMMER-001-PRF01 — one craft raises the hammer's damage by exactly 1 (mid-life). */
	@GameTest
	public void prf01_exactlyOneDamagePerPlate(GameTestHelper helper) {
		HammerScenarios.prf01ExactlyOneDamagePerPlate(helper);
	}

	/** @implements TC-HAMMER-001-NEG01 — on its last point the hammer breaks: plate made, grid slot empty. */
	@GameTest
	public void neg01_lastDurabilityBreaks(GameTestHelper helper) {
		HammerScenarios.neg01LastDurabilityBreaks(helper);
	}

	/** @implements TC-HAMMER-001-CON01 — the Forge Hammer's durability is 128 (PERFORMANCE.md). */
	@GameTest
	public void con01_durability128(GameTestHelper helper) {
		HammerScenarios.con01Durability128(helper);
	}
}
