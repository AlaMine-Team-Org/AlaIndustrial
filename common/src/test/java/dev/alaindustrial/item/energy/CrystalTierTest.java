package dev.alaindustrial.item.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link CrystalTier} — the Minecraft-free catalogue behind the EU crystals (MOD-504).
 *
 * <p>Two things here are load-bearing and would fail silently in game rather than throw:
 *
 * <ul>
 *   <li><b>The blank id is derived, not typed.</b> Both loaders register {@code <id>_blank} from this
 *       method; a tier whose blank id stopped matching its item would simply fail to register.</li>
 *   <li><b>Config is read live, not captured.</b> The suppliers exist so that a config reload moves the
 *       ladder. Replace them with fields initialised once and the tooltip keeps quoting the old price
 *       until the next restart — which is exactly the kind of drift no gate can see.</li>
 * </ul>
 */
class CrystalTierTest {

	private int energyBuffer;
	private int energyRate;

	@AfterEach
	void restoreConfig() {
		// Config is static and shared across the suite: leaving a probe value behind would make an
		// unrelated balance test read a number nobody put in gradle.properties.
		if (energyBuffer != 0) {
			Config.energyCrystalBuffer = energyBuffer;
		}
		if (energyRate != 0) {
			Config.energyCrystalInputRate = energyRate;
		}
	}

	@Test
	void blankIdIsTheItemIdPlusSuffix() {
		for (CrystalTier tier : CrystalTier.values()) {
			assertEquals(tier.id() + "_blank", tier.blankId(),
					"the loaders register the blank by this id");
		}
	}

	@Test
	void everyTierHasItsOwnIds() {
		Set<String> ids = new HashSet<>();
		for (CrystalTier tier : CrystalTier.values()) {
			assertTrue(ids.add(tier.id()), "duplicate crystal id: " + tier.id());
			assertTrue(ids.add(tier.blankId()), "duplicate blank id: " + tier.blankId());
		}
		assertEquals(CrystalTier.values().length * 2, ids.size());
	}

	@Test
	void capacityAndIntakeFollowConfigAfterAReload() {
		energyBuffer = Config.energyCrystalBuffer;
		energyRate = Config.energyCrystalInputRate;

		Config.energyCrystalBuffer = 123_000;
		Config.energyCrystalInputRate = 77;
		assertEquals(123_000L, CrystalTier.ENERGY.capacity(),
				"capacity must read Config live, not a value captured at class-init");
		assertEquals(77L, CrystalTier.ENERGY.inputRate(),
				"intake must read Config live, not a value captured at class-init");

		// A second move proves it is a live read rather than a one-time copy taken on first call.
		Config.energyCrystalBuffer = 456_000;
		assertEquals(456_000L, CrystalTier.ENERGY.capacity());
	}

	@Test
	void theLadderGrowsWithEachRung() {
		assertTrue(CrystalTier.ENERGY.capacity() < CrystalTier.LAPOTRON.capacity(),
				"the lapotron must cost more than the energy crystal");
		assertTrue(CrystalTier.LAPOTRON.capacity() < CrystalTier.RESONANT.capacity(),
				"the resonant crystal must cost more than the lapotron");
		assertNotEquals(0L, CrystalTier.ENERGY.capacity(),
				"a zero-price blank would finish the instant it is crafted");
	}
}
