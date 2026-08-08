package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests — the "these two pixels differ" rule (pure logic, no Minecraft, no image files).
 *
 * <p>This rule decides every visual verdict in the L3 suite, so its boundary is load-bearing: a
 * tolerance that is off by one silently changes what every gate considers a regression. The tests
 * therefore pin the boundary on BOTH sides (24 is same, 25 differs) rather than asserting the vague
 * "big differences are detected" — a one-sided check would stay green if the comparison flipped to
 * {@code >=}.
 *
 * @implements R-VIS-01 tolerance boundary (MOD-362)
 * @covers R-VIS-01
 */
class ToleranceTest {

    @Test
    void channel24_treatsExactlyTheThresholdAsSame_andOneMoreAsDifferent() {
        // BVA on the shipped default: the historical gates were calibrated against "> 24", so both
        // sides of the boundary are pinned. Only the second assertion fails if someone writes ">=".
        int base = 0xFF808080;
        int plus24 = 0xFF808098;   // blue 0x80 -> 0x98 = +24
        int plus25 = 0xFF808099;   // blue 0x80 -> 0x99 = +25

        assertFalse(Tolerance.CHANNEL_24.differs(base, plus24),
                "a channel delta of exactly 24 is within the shipped tolerance");
        assertTrue(Tolerance.CHANNEL_24.differs(base, plus25),
                "a channel delta of 25 is a real difference");
    }

    @Test
    void exact_reportsAnySingleStepAsDifferent() {
        assertTrue(Tolerance.EXACT.differs(0xFF000000, 0xFF000001),
                "EXACT exists precisely so a one-step change counts");
        assertFalse(Tolerance.EXACT.differs(0xFF123456, 0xFF123456));
    }

    @Test
    void lighting64_isStrictlyMoreForgivingThanChannel24() {
        // Not a tautology: this compares the two tolerances against the SAME pixel pair, so collapsing
        // the ladder back to one shared number turns it red.
        int dark = 0xFF404040;
        int lit = 0xFF808080;   // +64 on every channel

        assertTrue(Tolerance.CHANNEL_24.differs(dark, lit), "a lighting step is a difference at ±24");
        assertFalse(Tolerance.LIGHTING_64.differs(dark, lit), "the same step is tolerated at ±64");
    }

    @Test
    void alphaIsIgnored_becauseScreenshotsAreOpaqueAndDriversDisagreeOnIt() {
        assertFalse(Tolerance.EXACT.differs(0xFF112233, 0x00112233),
                "alpha must not decide a visual verdict — a headless capture may write 0 or 255");
    }

    @Test
    void differenceIsSymmetric() {
        int a = 0xFF204060;
        int b = 0xFF6040FF;
        assertEquals(Tolerance.CHANNEL_24.differs(a, b), Tolerance.CHANNEL_24.differs(b, a));
    }

    @Test
    void constructor_rejectsAThresholdThatCouldNeverFire() {
        assertThrows(IllegalArgumentException.class, () -> new Tolerance("negative", -1));
        assertThrows(IllegalArgumentException.class, () -> new Tolerance("beyond-255", 256),
                "a tolerance above the channel range would make every gate permanently green");
    }
}
