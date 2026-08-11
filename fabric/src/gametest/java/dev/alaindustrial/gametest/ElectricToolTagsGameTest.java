package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the membership tags of the diamond-tipped electric tools (MOD-389, suite TC-ETOOL-001).
 * Thin Fabric wrappers: the bodies are loader-neutral in
 * {@code common/.../gametest/ElectricToolTagScenarios} and the SAME bodies run on the NeoForge
 * {@code gameTestServer} lane ({@code NeoForgeGameTests}).
 */
public class ElectricToolTagsGameTest {

	/**
	 * @implements TC-ETOOL-001-FUN01 — each diamond-tipped upgrade sits in the same membership tags as its
	 *     base tool (pickaxes / axes / hoes, plus the drill's cluster-harvest and c:mining_tool entries).
	 */
	@GameTest
	public void tcEtool001Fun01_tipMembershipTags(GameTestHelper helper) {
		ElectricToolTagScenarios.fun01TipMembershipTags(helper);
	}

	/**
	 * @implements TC-ETOOL-001-FUN02 — the enchanting table's filter accepts each upgrade for its base
	 *     tool's enchantments, and rejects the ones from another domain (negative control).
	 */
	@GameTest
	public void tcEtool001Fun02_tipEnchantmentAccepted(GameTestHelper helper) {
		ElectricToolTagScenarios.fun02TipEnchantmentAccepted(helper);
	}
}
