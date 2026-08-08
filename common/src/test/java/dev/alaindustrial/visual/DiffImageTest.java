package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests — the failure-triage picture.
 *
 * <p>A diff image is only useful if the marks land exactly on the changed pixels: a picture that
 * highlights the wrong area is worse than no picture, because it sends the reviewer to the wrong place.
 *
 * @implements R-VIS-01 diff rendering (MOD-362)
 * @covers R-VIS-01
 */
class DiffImageTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MARK = 0xFFFF2020;

    @Test
    void marksExactlyTheChangedPixels() {
        PixelGrid before = PixelGrid.filled(10, 10, BLACK);
        PixelGrid after = before.withRegionPainted(new Region(3, 4, 2, 2), WHITE);

        PixelGrid diff = DiffImage.render(before, after, Tolerance.CHANNEL_24);

        assertEquals(MARK, diff.at(3, 4), "changed pixel is marked");
        assertEquals(MARK, diff.at(4, 5), "the far corner of the change is marked too");
        assertNotEquals(MARK, diff.at(2, 4), "the pixel just left of the change is not marked");
        assertNotEquals(MARK, diff.at(5, 5), "the pixel just right of the change is not marked");
    }

    @Test
    void marksNothingWhenTheFramesMatch() {
        PixelGrid grid = PixelGrid.filled(8, 8, WHITE);
        PixelGrid diff = DiffImage.render(grid, grid, Tolerance.EXACT);

        for (int pixel : diff.argb()) {
            assertNotEquals(MARK, pixel, "an identical pair must produce a diff with no marks at all");
        }
    }

    @Test
    void unchangedBackgroundIsDimmedSoTheMarksStandOut() {
        PixelGrid before = PixelGrid.filled(4, 4, WHITE);
        PixelGrid after = before.withRegionPainted(new Region(0, 0, 1, 1), BLACK);

        PixelGrid diff = DiffImage.render(before, after, Tolerance.CHANNEL_24);

        int background = diff.at(3, 3);
        assertTrue(PixelMath.luminance(background) < PixelMath.luminance(WHITE),
                "white background must come out dimmed, not white");
        int r = (background >> 16) & 0xFF;
        int g = (background >> 8) & 0xFF;
        int b = background & 0xFF;
        assertTrue(r == g && g == b, "background must be greyscale so only the marks carry colour");
    }

    @Test
    void refusesToDiffFramesOfDifferentSizes() {
        assertThrows(IllegalArgumentException.class, () -> DiffImage.render(
                PixelGrid.filled(4, 4, BLACK), PixelGrid.filled(5, 5, BLACK), Tolerance.EXACT));
    }

    @Test
    void honoursTheToleranceItIsGiven() {
        // The picture must show what the FAILING GATE saw, so the same pair renders differently under
        // different tolerances.
        PixelGrid dark = PixelGrid.filled(4, 4, 0xFF404040);
        PixelGrid lit = PixelGrid.filled(4, 4, 0xFF808080);   // +64 per channel

        assertEquals(MARK, DiffImage.render(dark, lit, Tolerance.CHANNEL_24).at(0, 0));
        assertNotEquals(MARK, DiffImage.render(dark, lit, Tolerance.LIGHTING_64).at(0, 0));
    }
}
