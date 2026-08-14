package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for what the electric tool line shares across items (suite TC-ETOOL-001): the membership tags
 * and enchantability of the diamond-tipped upgrades (MOD-389) and of the base tools (MOD-364), plus the
 * audit of the parameter table the shared EU tests are driven from (MOD-364). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/ElectricToolTagScenarios} and the SAME bodies
 * run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}).
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

	/**
	 * @implements TC-ETOOL-001-FUN03 — the BASE chainsaw, shovel and hoe sit in #minecraft:axes /
	 *     #minecraft:shovels / #minecraft:hoes; the shovel's tag membership was asserted by nothing at all
	 *     before, because it is the one tool of the line with no upgrade (MOD-364).
	 */
	@GameTest
	public void tcEtool001Fun03_baseMembershipTags(GameTestHelper helper) {
		ElectricToolTagScenarios.fun03BaseMembershipTags(helper);
	}

	/**
	 * @implements TC-ETOOL-001-FUN04 — the enchanting table's filter accepts each BASE tool for
	 *     efficiency/unbreaking/mending/fortune/silk_touch and rejects protection and looting (MOD-364).
	 */
	@GameTest
	public void tcEtool001Fun04_baseEnchantmentAccepted(GameTestHelper helper) {
		ElectricToolTagScenarios.fun04BaseEnchantmentAccepted(helper);
	}

	/**
	 * @implements TC-ETOOL-001-FUN05 — the ToolCase table driving the eighteen shared EU tests is audited
	 *     against the real items: right tool per suite, numbers agreeing with ItemEnergy and the TOOL
	 *     component, fixtures able to fail, no two cases collapsed onto one tool (MOD-364).
	 */
	@GameTest
	public void tcEtool001Fun05_energyCaseRosterIsHonest(GameTestHelper helper) {
		ElectricToolTagScenarios.fun05EnergyCaseRosterIsHonest(helper);
	}
}
