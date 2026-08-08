package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Diamond-Tipped Electric Chainsaw (MOD-374, suite TC-CHAINSAW-001). Thin
 * Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/ElectricChainsawScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical logic.
 */
public class ElectricChainsawGameTest {

	/**
	 * @implements TC-CHAINSAW-001-FUN01 — the diamond-tipped upgrade (MOD-374) cuts at 10.5 on logs and
	 *     on leaves, strictly faster than the base chainsaw, keeps the axe tier and still drops to hand
	 *     speed when flat.
	 */
	@GameTest
	public void tcChainsaw001Fun01_diamondTipSpeedAndTier(GameTestHelper helper) {
		ElectricChainsawScenarios.fun01DiamondTipSpeedAndTier(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN02 — sneak + right-click toggles the upgrade's Silk Touch mode, and
	 *     the mode changes the real leaf loot-table drop both ways (leaf block ↔ no leaf block); a plain
	 *     click is inert.
	 */
	@GameTest
	public void tcChainsaw001Fun02_diamondTipSilkToggleOnLeaves(GameTestHelper helper) {
		ElectricChainsawScenarios.fun02DiamondTipSilkToggleOnLeaves(helper);
	}

	/**
	 * @implements TC-CHAINSAW-001-FUN03 — the BASE chainsaw has no Silk Touch mode: sneak-clicking never
	 *     enchants it and its leaf drops never include the leaf block.
	 */
	@GameTest
	public void tcChainsaw001Fun03_baseChainsawHasNoSilkMode(GameTestHelper helper) {
		ElectricChainsawScenarios.fun03BaseChainsawHasNoSilkMode(helper);
	}
}
