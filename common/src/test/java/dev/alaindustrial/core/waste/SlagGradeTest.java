package dev.alaindustrial.core.waste;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Recycler's grading rule (MOD-145) — the one piece of the machine that decides whether sorting
 * waste is worth doing, and the only piece that can be tested without Minecraft.
 */
class SlagGradeTest {

	@Test
	@DisplayName("a single fraction pays ballast, however much of it there is")
	void monoBatchIsPoor() {
		assertEquals(SlagGrade.POOR, SlagGrade.of(64, 0, 0));
		assertEquals(SlagGrade.POOR, SlagGrade.of(0, 8, 0));
		assertEquals(SlagGrade.POOR, SlagGrade.of(0, 0, 1000));
	}

	@Test
	@DisplayName("a trace of a second fraction does not rescue a lopsided batch")
	void dominantFractionStaysPoor() {
		// 80 % is the threshold: at exactly that share the batch is still ballast.
		assertEquals(SlagGrade.POOR, SlagGrade.of(80, 20, 0));
		assertEquals(SlagGrade.POOR, SlagGrade.of(90, 5, 5));
	}

	@Test
	@DisplayName("two fractions in workable proportion pay the everyday grade")
	void twoFractionsAreCommon() {
		assertEquals(SlagGrade.COMMON, SlagGrade.of(50, 50, 0));
		assertEquals(SlagGrade.COMMON, SlagGrade.of(60, 40, 0));
	}

	@Test
	@DisplayName("all three present and none over half pays rich")
	void balancedBatchIsRich() {
		assertEquals(SlagGrade.RICH, SlagGrade.of(20, 20, 20));
		assertEquals(SlagGrade.RICH, SlagGrade.of(32, 16, 16));
	}

	@Test
	@DisplayName("three fractions with one over half is not rich")
	void thirdFractionAloneIsNotEnough() {
		// Present in all three, but 60 % of the batch is mineral: variety has to be real, not token.
		assertEquals(SlagGrade.COMMON, SlagGrade.of(60, 20, 20));
	}

	@Test
	@DisplayName("a batch of nothing but unknown junk is ballast")
	void emptyGradedMassIsPoor() {
		// OTHER never reaches this method — the block entity keeps it out of the arithmetic — so an
		// all-unknown batch arrives here as three zeroes and must not crash or flatter the player.
		assertEquals(SlagGrade.POOR, SlagGrade.of(0, 0, 0));
	}
}
