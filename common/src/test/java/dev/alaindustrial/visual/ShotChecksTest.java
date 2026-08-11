package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The depth ladder decides what the suite is allowed to claim about itself, so it is checked here
 * rather than trusted.
 *
 * <p>Every test has a negative control: an assertion that only passes because the classifier really
 * distinguishes the cases, not because every input happens to land on the same rung.
 */
class ShotChecksTest {

    @Test
    @DisplayName("no checks at all is the weakest rung, not a missing value")
    void emptyIsShallow() {
        assertEquals(ShotChecks.SHALLOW, ShotChecks.depthOf(List.of()));
        assertEquals(ShotChecks.SHALLOW, ShotChecks.depthOf(null));
        // Negative control: something must be able to reach a higher rung, or the line above proves
        // only that the method returns a constant.
        assertEquals(ShotChecks.SMOKE, ShotChecks.depthOf(ShotChecks.baseline(true)));
    }

    @Test
    @DisplayName("the file gates alone do not amount to a rendered-content check")
    void fileGatesAloneAreShallow() {
        assertEquals(ShotChecks.SHALLOW,
                ShotChecks.depthOf(List.of(ShotChecks.FILE_SIZE, ShotChecks.NOT_BLANK)));
    }

    @Test
    @DisplayName("the missing-texture scan lifts a frame to smoke")
    void missingTextureScanIsSmoke() {
        assertEquals(ShotChecks.SMOKE, ShotChecks.depthOf(
                List.of(ShotChecks.FILE_SIZE, ShotChecks.NOT_BLANK, ShotChecks.NO_MISSING_TEXTURE)));
    }

    @Test
    @DisplayName("a sibling-frame comparison outranks the frame-level gates")
    void pixelDiffIsCompared() {
        assertEquals(ShotChecks.COMPARED, ShotChecks.depthOf(
                List.of(ShotChecks.FILE_SIZE, ShotChecks.NO_MISSING_TEXTURE, ShotChecks.PIXEL_DIFF)));
    }

    @Test
    @DisplayName("a stored reference is the strongest claim and wins over a sibling comparison")
    void storedTemplateOutranksPixelDiff() {
        assertEquals(ShotChecks.REFERENCED, ShotChecks.depthOf(
                List.of(ShotChecks.PIXEL_DIFF, ShotChecks.STORED_TEMPLATE)));
        // Order must not decide the verdict: the same set the other way round reads the same.
        assertEquals(ShotChecks.REFERENCED, ShotChecks.depthOf(
                List.of(ShotChecks.STORED_TEMPLATE, ShotChecks.PIXEL_DIFF)));
    }

    @Test
    @DisplayName("a frame captured with the pink-cube gate off does not claim that gate")
    void baselineHonoursTheDisabledGate() {
        Set<String> off = ShotChecks.baseline(false);
        assertFalse(off.contains(ShotChecks.NO_MISSING_TEXTURE));
        assertEquals(ShotChecks.SHALLOW, ShotChecks.depthOf(off));
        // Negative control: with the gate on the very same call must include it.
        assertTrue(ShotChecks.baseline(true).contains(ShotChecks.NO_MISSING_TEXTURE));
    }

    @Test
    @DisplayName("every depth the classifier can return is listed in the reported ladder")
    void ladderCoversEveryReachableDepth() {
        // A report prints DEPTHS in order; a rung the classifier can return but the ladder omits
        // would silently vanish from the distribution.
        assertTrue(ShotChecks.DEPTHS.containsAll(List.of(
                ShotChecks.SHALLOW, ShotChecks.SMOKE, ShotChecks.COMPARED, ShotChecks.REFERENCED)));
        assertEquals(4, ShotChecks.DEPTHS.size());
    }
}
