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
		assertEquals(0.02, CableType.COPPER.lossPerBlock(), 1e-9);
		assertEquals(0.03, CableType.GOLD.lossPerBlock(), 1e-9);
		assertTrue(CableType.TIN.lossPerBlock() < CableType.COPPER.lossPerBlock(),
				"tin's low loss is its entire reason to exist");
		assertTrue(CableType.GOLD.lossPerBlock() > CableType.COPPER.lossPerBlock(),
				"gold trades distance for throughput — a strictly-better gold would flatten the choice");
	}

	@Test
	void tinCarriesSolarTrickleWithZeroLoss_whereCopperWouldLeak() {
		// Tin's niche, expressed as the game actually computes it: one solar panel is 1 EU/t.
		long solarFlow = Config.solarEuPerTick;
		assertEquals(0L, EnergyShare.cableLoss(solarFlow, CableType.TIN.lossPerBlock(), 100),
				"a 1 EU/t panel trickle floors to zero loss on tin over 100 blocks");
		// Same trickle on copper over the same run does lose EU — this contrast is the design.
		assertTrue(EnergyShare.cableLoss(solarFlow, CableType.COPPER.lossPerBlock(), 100) > 0,
				"copper does leak on the same trickle, which is why tin has a niche");
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
	void tier_goldIsMv_andTheLvPairIsLv() {
		assertEquals(EnergyTier.LV, CableType.TIN.tier());
		assertEquals(EnergyTier.LV, CableType.COPPER.tier());
		assertEquals(EnergyTier.MV, CableType.GOLD.tier(),
				"gold is the mod's first MV wire — the tooltip and network read this");
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
}
