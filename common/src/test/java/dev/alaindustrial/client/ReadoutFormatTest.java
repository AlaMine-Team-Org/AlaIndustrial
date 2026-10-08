package dev.alaindustrial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ReadoutFormat} — the shared number/duration formatting behind the statistics
 * panel and the dashboard (MOD-125).
 *
 * <p>Worth pinning because the boundaries are where these helpers historically went wrong: the three
 * private copies this class replaced disagreed about when to switch to "k", and one of them rendered a
 * decimal comma on a German client, putting a separator on screen that no other number used.
 *
 * @implements MOD-125 shared readout formatting: compact magnitudes, exact figures, duration parts
 */
class ReadoutFormatTest {

	@Test
	void compactKeepsSmallValuesExact() {
		assertEquals("0", ReadoutFormat.compact(0));
		assertEquals("999", ReadoutFormat.compact(999), "the last value before the k threshold stays exact");
	}

	@Test
	void compactSwitchesUnitsAtEachThousand() {
		assertEquals("1.0k", ReadoutFormat.compact(1000), "the threshold itself abbreviates");
		assertEquals("552.9k", ReadoutFormat.compact(552_900));
		assertEquals("1.0M", ReadoutFormat.compact(1_000_000));
		assertEquals("1.0B", ReadoutFormat.compact(1_000_000_000L));
		assertEquals("12.0B", ReadoutFormat.compact(12_000_000_000L), "past int range still formats");
	}

	/**
	 * A decimal comma would disagree with every other number on the same screen, so the formatter must
	 * not follow the JVM's default locale.
	 */
	@Test
	void compactIgnoresTheDefaultLocale() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			assertEquals("1.2k", ReadoutFormat.compact(1200), "a German default must not produce \"1,2k\"");
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void exactUsesSpacedGroupsRatherThanCommas() {
		assertEquals("1 234 567", ReadoutFormat.exact(1_234_567));
		assertEquals("42", ReadoutFormat.exact(42));
	}

	@Test
	void durationPartsSplitTicksIntoDaysHoursMinutes() {
		// 20 ticks per second: one in-game day of real time is 24 * 3600 * 20 ticks.
		long ticks = (2L * 86_400 + 4L * 3600 + 12L * 60) * 20L;
		long[] parts = ReadoutFormat.durationParts(ticks);
		assertEquals(2, parts[0], "days");
		assertEquals(4, parts[1], "hours");
		assertEquals(12, parts[2], "minutes");
	}

	@Test
	void durationPartsAreZeroForAFreshBlock() {
		long[] parts = ReadoutFormat.durationParts(0);
		assertEquals(0, parts[0]);
		assertEquals(0, parts[1]);
		assertEquals(0, parts[2]);
	}

	/** A block whose placement tick has not been resolved yet must not render a negative uptime. */
	@Test
	void durationPartsClampNegativeSpans() {
		long[] parts = ReadoutFormat.durationParts(-500);
		assertEquals(0, parts[0]);
		assertEquals(0, parts[1]);
		assertEquals(0, parts[2]);
	}

	@Test
	void durationPartsDropSubMinuteRemainder() {
		long[] parts = ReadoutFormat.durationParts(59 * 20L);
		assertEquals(0, parts[2], "59 seconds is not yet a minute");
	}

	@Test
	void clockZeroPadsBothHalves() {
		assertEquals("04:12", ReadoutFormat.clock(4, 12));
		assertEquals("00:00", ReadoutFormat.clock(0, 0));
		assertEquals("23:59", ReadoutFormat.clock(23, 59));
	}

	/**
	 * MOD-722: a window rate under 1 EU/t used to print as 0 on a working machine. The station's real
	 * window — one 8 EU action in 40 ticks — is the first case.
	 *
	 * @implements MOD-722-RATE — fractions below 10, whole numbers from 10, "<0.1" for a tiny non-zero
	 *     amount, "0" only when nothing moved
	 * @covers MOD-722
	 */
	@Test
	void rateKeepsFractionsOfAnEuPerTick() {
		assertEquals("0.2", ReadoutFormat.rate(8, 40), "one drone action in a 40-tick window");
		assertEquals("0.4", ReadoutFormat.rate(16, 40));
		assertEquals("<0.1", ReadoutFormat.rate(1, 40), "1 EU in 40 ticks rounds to 0.0 but is not nothing");
		assertEquals("<0.1", ReadoutFormat.rate(3, 40), "0.075 EU/t");
		assertEquals("0.1", ReadoutFormat.rate(4, 40), "the first value that shows a digit");
		assertEquals("0", ReadoutFormat.rate(0, 40), "zero only when nothing moved");
		assertEquals("0", ReadoutFormat.rate(-5, 40), "a negative amount is clamped, never printed");
	}

	/** @implements MOD-722-RATE — the band edge rounds down, so 9.96 does not print as "10.0" */
	@Test
	void rateSwitchesToWholeNumbersAtTen() {
		assertEquals("8.0", ReadoutFormat.rate(320, 40), "an exact rate below 10 keeps its decimal");
		assertEquals("9.9", ReadoutFormat.rate(399, 40), "9.975 rounds down inside the decimal band");
		assertEquals("10", ReadoutFormat.rate(400, 40), "the threshold itself is a whole number");
		assertEquals("12", ReadoutFormat.rate(499, 40), "12.475 rounds down");
		assertEquals("2048", ReadoutFormat.rate(2048L * 40, 40));
		assertEquals("1000000000000", ReadoutFormat.rate(40_000_000_000_000L, 40), "past int range");
	}

	/** @implements MOD-722-RATE — the decimal point is a point under any default locale */
	@Test
	void rateIgnoresTheDefaultLocale() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			assertEquals("0.4", ReadoutFormat.rate(16, 40));
		} finally {
			Locale.setDefault(previous);
		}
	}
}
