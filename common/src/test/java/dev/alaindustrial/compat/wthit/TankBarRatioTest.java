package dev.alaindustrial.compat.wthit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * L1 pin for {@link TankBarRatio} (MOD-938): whatever a non-empty tank holds, WTHIT's sprite bar must
 * get a scissor at least 1 px wide. The bar math below is WTHIT 20.0.0's {@code SpriteBarComponent}:
 * fill {@code (int) (100 * ratio)}, drawn only when above 0, clipped to {@code fill - 2}.
 */
class TankBarRatioTest {
	private static final int WTHIT_BAR_WIDTH = 100;

	private static int wthitScissorWidth(float ratio) {
		int fill = (int) (WTHIT_BAR_WIDTH * ratio);
		return fill > 0 ? fill - 2 : Integer.MAX_VALUE; // a zero fill is never drawn, so never clipped
	}

	/** The player's crash: a 10 000 mB distillation tank holding 100–299 mB. */
	@ParameterizedTest
	@ValueSource(longs = {1_000, 4_000, 8_000, 10_000, 16_000, 64_000})
	void everyNonEmptyLevelGetsAVisibleScissor(long capacity) {
		for (long amount = 1; amount <= capacity; amount++) {
			int width = wthitScissorWidth(TankBarRatio.of(amount, capacity));
			assertTrue(width >= 1, amount + "/" + capacity + " mB gives a " + width + " px scissor");
		}
	}

	/** Without the floor the same band crashes — proves the sweep above can fail. */
	@Test
	void rawRatioInTheBandWouldCrash() {
		assertTrue(wthitScissorWidth(150F / 10_000) < 1);
		assertTrue(wthitScissorWidth(299F / 10_000) < 1);
	}

	@Test
	void emptyAndBrokenTanksDrawNothing() {
		assertEquals(0F, TankBarRatio.of(0, 10_000));
		assertEquals(0F, TankBarRatio.of(-5, 10_000));
		assertEquals(0F, TankBarRatio.of(500, 0));
	}

	@Test
	void outsideTheBandTheBarIsTheTruth() {
		assertEquals(0.5F, TankBarRatio.of(5_000, 10_000));
		assertEquals(0.03F, TankBarRatio.of(300, 10_000));
		assertEquals(1F, TankBarRatio.of(10_000, 10_000));
		assertEquals(1F, TankBarRatio.of(12_000, 10_000));
		assertEquals(TankBarRatio.MIN_VISIBLE, TankBarRatio.of(1, 10_000));
	}
}
