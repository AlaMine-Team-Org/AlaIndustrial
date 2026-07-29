package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * L1 unit tests — the cable ladder (pure logic, no Minecraft world).
 *
 * <p>The point of {@link CableType} is that the three shipped cables are genuinely different. Before
 * MOD-219 they shared one hardcoded set of numbers, so a "cable" test could pass while every grade
 * behaved identically. These tests therefore assert the grades differ <b>from each other</b>, not
 * just that each returns something — and they pin the canonical values as literals rather than
 * re-deriving them from {@link Config} (a {@code f(x) == f(x)} check would stay green even if the
 * ladder collapsed back to one shared number).
 *
 * @implements common-NRG cable ladder (MOD-219)
 */
class CableTypeTest {

	@Test
	void throughput_isTheSegmentBuffer_andStrictlyAscendsAcrossTheLadder() {
		// The buffer IS the real throughput (MOD-070), so this ordering is what a player feels as
		// "thicker wire". Gold must beat copper here or its whole niche is fiction.
		assertEquals(8L, CableType.TIN.segmentBuffer(), "tin carries 8 EU/t");
		assertEquals(12L, CableType.COPPER.segmentBuffer(), "copper keeps its shipped 12 EU/t");
		assertEquals(48L, CableType.GOLD.segmentBuffer(), "gold carries 48 EU/t = 4× copper");
		assertTrue(CableType.TIN.segmentBuffer() < CableType.COPPER.segmentBuffer());
		assertTrue(CableType.COPPER.segmentBuffer() < CableType.GOLD.segmentBuffer());
	}

	@Test
	void packetCap_ascends_andTinSitsBelowTheLvTierCeiling() {
		assertEquals(8L, CableType.TIN.packetCap(), "tin is capped below LV on purpose");
		assertEquals(32L, CableType.COPPER.packetCap(), "copper = the LV tier voltage");
		assertEquals(128L, CableType.GOLD.packetCap(), "gold = the MV tier voltage");
		assertTrue(CableType.TIN.packetCap() < EnergyTier.LV.maxVoltage(),
				"tin must stay under the LV ceiling, otherwise it is just a cheaper copper");
	}

	@Test
	void loss_tinIsGentlest_andGoldIsWorstOnPurpose() {
		assertEquals(0.006, CableType.TIN.lossPerBlock(), 1e-9);
		assertEquals(0.003, CableType.INSULATED_TIN.lossPerBlock(), 1e-9);
		assertEquals(0.02, CableType.COPPER.lossPerBlock(), 1e-9);
		assertEquals(0.01, CableType.INSULATED_COPPER.lossPerBlock(), 1e-9);
		assertEquals(0.03, CableType.GOLD.lossPerBlock(), 1e-9);
		assertEquals(0.015, CableType.INSULATED_GOLD.lossPerBlock(), 1e-9);
		assertTrue(CableType.INSULATED_TIN.lossPerBlock() < CableType.TIN.lossPerBlock());
		assertTrue(CableType.INSULATED_COPPER.lossPerBlock() < CableType.COPPER.lossPerBlock());
		assertTrue(CableType.INSULATED_GOLD.lossPerBlock() < CableType.GOLD.lossPerBlock());
		assertTrue(CableType.TIN.lossPerBlock() < CableType.COPPER.lossPerBlock(),
				"tin's low loss is its entire reason to exist");
		assertTrue(CableType.GOLD.lossPerBlock() > CableType.COPPER.lossPerBlock(),
				"gold trades distance for throughput — a strictly-better gold would flatten the choice");
	}

	@Test
	void insulationChangesOnlyLoss_andUsesTheCanonicalHalfMultiplier() {
		assertEquals(0.5, Config.insulationLossMultiplier, 1e-9);
		assertSameConductor(CableType.TIN, CableType.INSULATED_TIN);
		assertSameConductor(CableType.COPPER, CableType.INSULATED_COPPER);
		assertSameConductor(CableType.GOLD, CableType.INSULATED_GOLD);
		assertFalse(CableType.TIN.isInsulated());
		assertFalse(CableType.COPPER.isInsulated());
		assertFalse(CableType.GOLD.isInsulated());
		assertTrue(CableType.INSULATED_TIN.isInsulated());
		assertTrue(CableType.INSULATED_COPPER.isInsulated());
		assertTrue(CableType.INSULATED_GOLD.isInsulated());
		assertEquals(CableType.TIN.lossPerBlock() * 0.5,
				CableType.INSULATED_TIN.lossPerBlock(), 1e-9);
		assertEquals(CableType.COPPER.lossPerBlock() * 0.5,
				CableType.INSULATED_COPPER.lossPerBlock(), 1e-9);
		assertEquals(CableType.GOLD.lossPerBlock() * 0.5,
				CableType.INSULATED_GOLD.lossPerBlock(), 1e-9);
	}

	@Test
	void insulationGoldenDelivery_matchesMod253AttenuationAtFiftyAndOneHundredBlocks() {
		assertDelivery(8, CableType.TIN, 50, 6);
		assertDelivery(8, CableType.INSULATED_TIN, 50, 7);
		assertDelivery(12, CableType.COPPER, 50, 5);
		assertDelivery(12, CableType.INSULATED_COPPER, 50, 8);

		assertDelivery(8, CableType.TIN, 100, 5);
		assertDelivery(8, CableType.INSULATED_TIN, 100, 6);
		assertDelivery(12, CableType.COPPER, 100, 2);
		assertDelivery(12, CableType.INSULATED_COPPER, 100, 5);

		assertDelivery(8, CableType.GOLD, 50, 2);
		assertDelivery(8, CableType.INSULATED_GOLD, 50, 4);
		assertDelivery(8, CableType.GOLD, 100, 1);
		assertDelivery(8, CableType.INSULATED_GOLD, 100, 2);
	}

	@Test
	void tinRetainsMoreOfTheSameFlowThanCopperAndGold() {
		// MOD-253 guarantees that the last EU survives on every grade, so compare a representative packet
		// instead of using a 1-EU trickle (which must now be loss-free on copper too).
		long gross = 48;
		int distance = 100;
		long tinLoss = EnergyShare.cableLoss(gross, CableType.TIN.lossPerBlock(), distance);
		long copperLoss = EnergyShare.cableLoss(gross, CableType.COPPER.lossPerBlock(), distance);
		long goldLoss = EnergyShare.cableLoss(gross, CableType.GOLD.lossPerBlock(), distance);
		assertEquals(21L, tinLoss);
		assertEquals(41L, copperLoss);
		assertEquals(45L, goldLoss);
		assertTrue(tinLoss < copperLoss, "tin remains the long-distance grade");
		assertTrue(copperLoss < goldLoss, "gold still pays the highest resistive loss");
	}

	@Test
	void gradesAreNotInterchangeable() {
		// The regression guard for the "recoloured copper" bug class: if a future refactor pointed two
		// grades at the same Config knobs, this fails even though every other test still passes.
		for (CableType a : CableType.values()) {
			for (CableType b : CableType.values()) {
				if (a == b) {
					continue;
				}
				boolean sameNumbers = a.segmentBuffer() == b.segmentBuffer()
						&& a.packetCap() == b.packetCap()
						&& a.lossPerBlock() == b.lossPerBlock();
				assertFalse(sameNumbers, a + " and " + b + " have identical balance numbers");
			}
		}
	}

	@Test
	void strongerThan_ranksByPacketCap_andIsIrreflexive() {
		assertTrue(CableType.GOLD.strongerThan(CableType.COPPER));
		assertTrue(CableType.COPPER.strongerThan(CableType.TIN));
		assertTrue(CableType.GOLD.strongerThan(CableType.TIN));
		assertFalse(CableType.TIN.strongerThan(CableType.COPPER));
		assertFalse(CableType.COPPER.strongerThan(CableType.GOLD));
		for (CableType t : CableType.values()) {
			assertFalse(t.strongerThan(t), t + " is not stronger than itself");
		}
	}

	@Test
	void strongerThan_equalCapBareCableWinsDeterministically() {
		assertTrue(CableType.TIN.strongerThan(CableType.INSULATED_TIN));
		assertFalse(CableType.INSULATED_TIN.strongerThan(CableType.TIN));
		assertTrue(CableType.COPPER.strongerThan(CableType.INSULATED_COPPER));
		assertFalse(CableType.INSULATED_COPPER.strongerThan(CableType.COPPER));
		assertTrue(CableType.GOLD.strongerThan(CableType.INSULATED_GOLD));
		assertFalse(CableType.INSULATED_GOLD.strongerThan(CableType.GOLD));
	}

	@Test
	void tier_goldIsMv_andTheLvPairIsLv() {
		assertEquals(EnergyTier.LV, CableType.TIN.tier());
		assertEquals(EnergyTier.LV, CableType.INSULATED_TIN.tier());
		assertEquals(EnergyTier.LV, CableType.COPPER.tier());
		assertEquals(EnergyTier.LV, CableType.INSULATED_COPPER.tier());
		assertEquals(EnergyTier.MV, CableType.GOLD.tier(),
				"gold is the mod's first MV wire — the tooltip and network read this");
		assertEquals(EnergyTier.MV, CableType.INSULATED_GOLD.tier());
	}

	@Test
	void shockDamage_scalesByTier_andReadsConfigLive() {
		assertEquals(2.0f, CableType.TIN.shockDamage());
		assertEquals(2.0f, CableType.COPPER.shockDamage());
		assertEquals(6.0f, CableType.GOLD.shockDamage());
		float original = Config.bareCableShockLvDamage;
		try {
			Config.bareCableShockLvDamage = 3.5f;
			assertEquals(3.5f, CableType.COPPER.shockDamage());
		} finally {
			Config.bareCableShockLvDamage = original;
		}
	}

	@ParameterizedTest
	@EnumSource(CableType.class)
	void everyGradeIsUsable(CableType type) {
		assertTrue(type.segmentBuffer() > 0, "a zero buffer would carry nothing");
		assertTrue(type.packetCap() > 0);
		assertTrue(type.lossPerBlock() >= 0.0, "negative loss would create EU");
		assertNotEquals("", type.serializedName(), "the codec needs a name");
	}

	@Test
	void numbersReadLiveFromConfig() {
		// Same contract as EnergyTier: a server operator retunes cables from the config file, so the
		// enum must not cache a compile-time copy.
		int original = Config.goldCableBuffer;
		try {
			Config.goldCableBuffer = 999;
			assertEquals(999L, CableType.GOLD.segmentBuffer(), "buffer is read live, not cached");
		} finally {
			Config.goldCableBuffer = original;
		}
	}

	@Test
	void insulationMultiplierIsReadLiveFromConfig() {
		double original = Config.insulationLossMultiplier;
		try {
			Config.insulationLossMultiplier = 0.25;
			assertEquals(Config.tinCableLossPerBlock * 0.25,
					CableType.INSULATED_TIN.lossPerBlock(), 1e-9);
			assertEquals(Config.copperCableLossPerBlock * 0.25,
					CableType.INSULATED_COPPER.lossPerBlock(), 1e-9);
			assertEquals(Config.goldCableLossPerBlock * 0.25,
					CableType.INSULATED_GOLD.lossPerBlock(), 1e-9);
		} finally {
			Config.insulationLossMultiplier = original;
		}
	}

	private static void assertSameConductor(CableType bare, CableType insulated) {
		assertEquals(bare.tier(), insulated.tier());
		assertEquals(bare.packetCap(), insulated.packetCap());
		assertEquals(bare.segmentBuffer(), insulated.segmentBuffer());
	}

	private static void assertDelivery(long gross, CableType type, int distance, long expected) {
		long delivered = gross - EnergyShare.cableLoss(gross, type.lossPerBlock(), distance);
		assertEquals(expected, delivered, type + " delivery at " + distance + " blocks");
	}
}
