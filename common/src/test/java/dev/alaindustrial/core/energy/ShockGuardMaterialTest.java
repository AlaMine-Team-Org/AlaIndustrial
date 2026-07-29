package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import org.junit.jupiter.api.Test;

/** MOD-279: the insulating-stand ladder, its NBT ids, and the untouched no-stand path. */
class ShockGuardMaterialTest {
	@Test
	void noneAlwaysHitsSoTheBareCableRuleIsUnchanged() {
		// 1.0 is what keeps MOD-260 intact: a bare segment must shock on every eligible contact. The
		// caller additionally short-circuits on NONE without rolling, so this value is belt-and-braces.
		assertEquals(1.0, ShockGuardMaterial.NONE.hitChance());
		assertFalse(ShockGuardMaterial.NONE.isPresent());
	}

	@Test
	void everyRealMaterialIsPresentAndProtects() {
		for (ShockGuardMaterial material : ShockGuardMaterial.values()) {
			if (material == ShockGuardMaterial.NONE) {
				continue;
			}
			assertTrue(material.isPresent(), material + " should count as an installed stand");
			assertTrue(material.hitChance() < 1.0,
					material + " must reduce the hit chance, otherwise the stand is decorative");
			assertTrue(material.hitChance() > 0.0,
					material + " must not be a total immunity — that is what insulated cable is for");
		}
	}

	@Test
	void glassBeatsWoodBeatsWool() {
		// The shipped ladder: glass is the furnace-gated best, wool the cheapest and weakest. Asserted as
		// an ordering rather than three literals so retuning the balance does not churn this test.
		assertTrue(ShockGuardMaterial.GLASS.hitChance() < ShockGuardMaterial.WOOD.hitChance());
		assertTrue(ShockGuardMaterial.WOOD.hitChance() < ShockGuardMaterial.WOOL.hitChance());
	}

	@Test
	void hitChanceFollowsConfigLive() {
		double before = Config.shockGuardWoodHitChance;
		try {
			Config.shockGuardWoodHitChance = 0.125;
			assertEquals(0.125, ShockGuardMaterial.WOOD.hitChance(),
					"the material must read Config live, not snapshot it at class-init");
		} finally {
			Config.shockGuardWoodHitChance = before;
		}
	}

	@Test
	void serializedNamesRoundTrip() {
		for (ShockGuardMaterial material : ShockGuardMaterial.values()) {
			assertSame(material, ShockGuardMaterial.byName(material.serializedName()));
		}
	}

	@Test
	void serializedNamesAreDistinct() {
		ShockGuardMaterial[] values = ShockGuardMaterial.values();
		for (int i = 0; i < values.length; i++) {
			for (int j = i + 1; j < values.length; j++) {
				assertNotEquals(values[i].serializedName(), values[j].serializedName(),
						"two materials sharing an NBT id would silently alias on load");
			}
		}
	}

	@Test
	void unknownNameLoadsAsNoStandRatherThanThrowing() {
		// A world saved by a future version, or a hand-edited chunk, must load as "no stand" instead of
		// failing chunk load.
		assertSame(ShockGuardMaterial.NONE, ShockGuardMaterial.byName("diamond"));
		assertSame(ShockGuardMaterial.NONE, ShockGuardMaterial.byName(""));
	}
}
