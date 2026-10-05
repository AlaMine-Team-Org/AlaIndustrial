package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 suite for the EU rule of the electric mining tools (MOD-707). The world scenarios
 * ({@code ElectricMiningToolContractScenarios}, {@code ElectricToolEnergyScenarios}) pin the rule through
 * the items; this pins its boundaries directly, and the client-side clause they cannot reach.
 */
class ElectricMiningRuleTest {

	@Test
	@DisplayName("a flat tool reports exactly hand speed, so Efficiency cannot revive it")
	void handSpeedIsExactlyOne() {
		assertEquals(1.0f, ElectricMiningRule.HAND_SPEED);
	}

	@Test
	@DisplayName("the tool affords an action at exactly its cost, not one EU below")
	void affordsAtCostNotBelow() {
		assertTrue(ElectricMiningRule.affords(50, 50), "exactly the cost");
		assertTrue(ElectricMiningRule.affords(10_000, 50), "a full buffer");
		assertFalse(ElectricMiningRule.affords(49, 50), "one EU below the cost");
		assertFalse(ElectricMiningRule.affords(0, 20), "flat");
	}

	@Test
	@DisplayName("only a server-side block of non-zero hardness is billed")
	void billsOnlyHardBlocksOnTheServer() {
		assertTrue(ElectricMiningRule.billsBlock(false, 1.5f), "stone on the server");
		assertTrue(ElectricMiningRule.billsBlock(false, 0.1f), "a snow layer is soft, not free");
		assertFalse(ElectricMiningRule.billsBlock(false, 0.0f), "an instant-break block is free");
		assertFalse(ElectricMiningRule.billsBlock(true, 1.5f), "the client never moves the charge");
		assertTrue(ElectricMiningRule.billsBlock(false, -1.0f), "bedrock is not instant-break");
	}
}
