package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the enchantability of the whole item roster (suite TC-ENCH-001, MOD-565). Thin Fabric
 * wrapper: the body is loader-neutral in {@code common/.../gametest/EnchantableRosterScenarios} and the
 * SAME body runs on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}).
 */
public class EnchantableRosterGameTest {

	/**
	 * @implements TC-ENCH-001-FUN01 — every item that declares itself enchantable accepts at least one
	 *     enchantment, so the enchanting table never shows levels above a blank line.
	 */
	@GameTest
	public void tcEnch001Fun01_everyEnchantableItemHasCandidates(GameTestHelper helper) {
		EnchantableRosterScenarios.fun01EveryEnchantableItemHasCandidates(helper);
	}
}
