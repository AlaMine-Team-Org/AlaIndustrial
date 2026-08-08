package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests — the arithmetic every L3 pixel gate measures with.
 *
 * <p>Until MOD-362 this maths lived inside the client gametest and could only run inside a booted
 * Minecraft client, so nothing ever asserted that it returns the right numbers. A gate is only as
 * trustworthy as its ruler: if {@code differingPixels} silently returned 0 the whole suite would go
 * green and stay green. Every test here uses synthetic grids with a KNOWN answer, so the expected
 * value is arithmetic, not a re-derivation of the implementation.
 *
 * @implements R-VIS-01 pixel-gate arithmetic (MOD-362)
 * @covers R-VIS-01, R-VIS-04
 */
class PixelMathTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF0000;

    @Test
    void differingPixels_countsExactlyThePaintedArea() {
        PixelGrid before = PixelGrid.filled(10, 10, BLACK);
        PixelGrid after = before.withRegionPainted(new Region(2, 3, 4, 5), WHITE);

        assertEquals(20, PixelMath.differingPixels(before, after, Tolerance.CHANNEL_24),
                "4x5 painted pixels = 20 differing, counted by hand not by the implementation");
    }

    @Test
    void differingPixels_isZeroForIdenticalFrames_andNonZeroOtherwise() {
        // The negative control that matters: a comparison stuck at 0 would make every gate vacuous.
        PixelGrid grid = PixelGrid.filled(8, 8, BLACK);
        assertEquals(0, PixelMath.differingPixels(grid, grid, Tolerance.EXACT));

        PixelGrid changed = grid.withRegionPainted(new Region(0, 0, 1, 1), WHITE);
        assertTrue(PixelMath.differingPixels(grid, changed, Tolerance.EXACT) > 0,
                "a one-pixel change must be visible to the ruler, otherwise gates cannot fail");
    }

    @Test
    void differingPixelsIn_ignoresChangesOutsideTheRegion() {
        // This is the property that makes GUI gates robust against the animating world behind them.
        PixelGrid before = PixelGrid.filled(20, 20, BLACK);
        PixelGrid after = before
                .withRegionPainted(new Region(0, 0, 5, 5), WHITE)      // outside the window
                .withRegionPainted(new Region(10, 10, 2, 2), WHITE);   // inside the window

        Region window = new Region(8, 8, 8, 8);
        assertEquals(4, PixelMath.differingPixelsIn(before, after, window, Tolerance.CHANNEL_24),
                "only the 2x2 change inside the window counts");
        assertEquals(29, PixelMath.differingPixels(before, after, Tolerance.CHANNEL_24),
                "whole-frame comparison still sees both changes (25 + 4)");
    }

    @Test
    void diffBoundingBox_tightlyWrapsTheChange_andIsNullWhenNothingChanged() {
        PixelGrid before = PixelGrid.filled(16, 16, BLACK);
        PixelGrid after = before.withRegionPainted(new Region(4, 6, 3, 2), RED);

        Region box = PixelMath.diffBoundingBox(before, after, Tolerance.CHANNEL_24);
        assertNotNull(box);
        assertEquals(new Region(4, 6, 3, 2), box, "the box must be tight, not the whole frame");

        assertNull(PixelMath.diffBoundingBox(before, before, Tolerance.CHANNEL_24),
                "identical frames have no bounding box");
    }

    @Test
    void findSubImage_locatesASpriteAndReportsItsTopLeftCorner() {
        PixelGrid frame = PixelGrid.filled(32, 32, BLACK).withRegionPainted(new Region(7, 11, 4, 4), RED);
        PixelGrid sprite = PixelGrid.filled(4, 4, RED);

        assertEquals(new Point(7, 11), PixelMath.findSubImage(frame, sprite, Tolerance.EXACT));
    }

    @Test
    void findSubImage_returnsNullWhenTheSpriteIsAbsent() {
        // Without this the "is the icon drawn?" assertion could never fail.
        PixelGrid frame = PixelGrid.filled(32, 32, BLACK);
        PixelGrid sprite = PixelGrid.filled(4, 4, RED);

        assertNull(PixelMath.findSubImage(frame, sprite, Tolerance.EXACT));
    }

    @Test
    void findSubImage_returnsNullWhenTheSpriteIsLargerThanTheFrame() {
        assertNull(PixelMath.findSubImage(PixelGrid.filled(4, 4, BLACK), PixelGrid.filled(8, 8, BLACK),
                Tolerance.EXACT));
    }

    @Test
    void luminance_ordersBlackBelowGreyBelowWhite_andWeightsGreenHeaviest() {
        assertEquals(0.0, PixelMath.luminance(BLACK), 0.001);
        assertEquals(255.0, PixelMath.luminance(WHITE), 0.001);
        assertTrue(PixelMath.luminance(0xFF00FF00) > PixelMath.luminance(0xFFFF0000),
                "Rec. 709 weights green above red — a greyscale gate depends on this");
        assertTrue(PixelMath.luminance(0xFFFF0000) > PixelMath.luminance(0xFF0000FF));
    }

    @Test
    void requiredSignal_takesWhicheverBoundIsHigher() {
        assertEquals(2000, PixelMath.requiredSignal(100, 4, 2000), "the floor wins when noise is low");
        assertEquals(4000, PixelMath.requiredSignal(1000, 4, 2000), "the noise multiple wins when noise is high");
        assertEquals(2000, PixelMath.requiredSignal(500, 4, 2000), "exactly at the crossover");
    }

    @Test
    void comparingDifferentSizedFrames_failsLoudlyInsteadOfReturningANumber() {
        // A silent 0 here would read as "nothing changed" — the worst possible failure mode for a gate.
        PixelGrid small = PixelGrid.filled(4, 4, BLACK);
        PixelGrid large = PixelGrid.filled(8, 8, BLACK);

        assertThrows(IllegalArgumentException.class,
                () -> PixelMath.differingPixels(small, large, Tolerance.EXACT));
    }
}
