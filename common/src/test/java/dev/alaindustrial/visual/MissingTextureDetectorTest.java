package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests — the missing-texture detector.
 *
 * <p>The detector's whole value is that it fires on a broken model and stays quiet on the mod's own
 * purple art. Both halves are tested here, and the negative cases are the important ones: a detector
 * that fires on everything gets muted within a week, and a detector that fires on nothing is exactly
 * the state MOD-362 found the suite in.
 *
 * @implements R-VIS-04 missing-texture detection (MOD-362)
 * @covers R-VIS-04, R-VIS-05
 */
class MissingTextureDetectorTest {

    private static final int MAGENTA = 0xFFF800F8;
    private static final int BLACK = 0xFF000000;
    private static final int STONE_GREY = 0xFF7A7A7A;

    /**
     * Vanilla's placeholder: a 2x2 checkerboard of magenta and black, drawn at {@code square} pixels per
     * cell so the test can scale it the way distance from the camera does.
     */
    private static PixelGrid checkerboard(int square, int magenta) {
        PixelGrid grid = PixelGrid.filled(square * 2, square * 2, BLACK);
        return grid
                .withRegionPainted(new Region(0, 0, square, square), magenta)
                .withRegionPainted(new Region(square, square, square, square), magenta);
    }

    @Test
    void findsTheVanillaPlaceholder() {
        MissingTextureDetector.Finding hit = MissingTextureDetector.scan(checkerboard(32, MAGENTA));

        assertNotNull(hit, "the vanilla magenta/black checkerboard is exactly what this detector exists for");
        assertTrue(hit.magentaPixels() >= 1024, "a 32x32 magenta square is 1024 px, got " + hit.magentaPixels());
        assertTrue(hit.darkFraction() > 0.2, "the checkerboard is roughly half black, got " + hit.darkFraction());
    }

    @Test
    void findsThePlaceholderWhenBlockLightingDarkensIt() {
        // In-world the placeholder is never full brightness. An exact-colour detector would find nothing
        // outdoors, which is precisely where the pink cubes show up.
        int litMagenta = 0xFF7C007C;   // 50 % brightness: red == blue, green still 0
        MissingTextureDetector.Finding hit = MissingTextureDetector.scan(checkerboard(32, litMagenta));

        assertNotNull(hit, "a darkened placeholder is still a placeholder");
    }

    @Test
    void ignoresACleanFrame() {
        assertNull(MissingTextureDetector.scan(PixelGrid.filled(128, 128, STONE_GREY)));
    }

    @Test
    void ignoresFlatMagentaArt_becauseTheModShipsPurpleTextures() {
        // The key false-positive guard. Without the checkerboard requirement this frame would be
        // reported as a broken texture and the gate would have to be switched off.
        PixelGrid flatArt = PixelGrid.filled(128, 128, STONE_GREY)
                .withRegionPainted(new Region(20, 20, 64, 64), MAGENTA);

        assertNull(MissingTextureDetector.scan(flatArt),
                "solid magenta with no black companion is art, not a missing texture");
    }

    @Test
    void ignoresPinkAndViolet_whichAreNotThePlaceholderColour() {
        // Placeholder magenta has red == blue and green == 0. Pink (green high) and violet (red != blue)
        // are common in real art and must not trip the ratio test.
        int pink = 0xFFF880F8;
        int violet = 0xFF8000F8;

        assertTrue(!MissingTextureDetector.looksLikePlaceholderMagenta(pink),
                "pink has a high green channel");
        assertTrue(!MissingTextureDetector.looksLikePlaceholderMagenta(violet),
                "violet has red far below blue");
        assertTrue(MissingTextureDetector.looksLikePlaceholderMagenta(MAGENTA));
    }

    @Test
    void ignoresASpeckTooSmallToBeABrokenModel() {
        // A handful of magenta pixels is dithering or a UI accent, not a pink cube.
        PixelGrid speck = PixelGrid.filled(128, 128, BLACK)
                .withRegionPainted(new Region(10, 10, 4, 4), MAGENTA);

        assertNull(MissingTextureDetector.scan(speck),
                "16 px is far below the default cluster floor");
    }

    @Test
    void reportsWhereTheProblemIs_soAReviewerDoesNotHuntTheFrame() {
        PixelGrid frame = PixelGrid.filled(200, 200, STONE_GREY);
        // A placeholder patch parked away from the origin: the reported area must point at it.
        frame = frame
                .withRegionPainted(new Region(100, 60, 30, 30), MAGENTA)
                .withRegionPainted(new Region(130, 90, 30, 30), MAGENTA)
                .withRegionPainted(new Region(130, 60, 30, 30), BLACK)
                .withRegionPainted(new Region(100, 90, 30, 30), BLACK);

        MissingTextureDetector.Finding hit = MissingTextureDetector.scan(frame, 400);

        assertNotNull(hit);
        assertTrue(hit.area().x() >= 100 && hit.area().y() >= 60,
                "the reported area must sit on the patch, got " + hit.area());
        assertTrue(hit.area().width() <= 60 && hit.area().height() <= 60,
                "the area must be tight, not the whole frame, got " + hit.area());
    }

    @Test
    void aCallerCanLowerTheFloorForSmallSubjects() {
        // Item icons are 16x16 in GUI space; the block-sized default would never fire on them.
        PixelGrid icon = checkerboard(8, MAGENTA);   // 8x8 squares = 64 px per cluster

        assertNull(MissingTextureDetector.scan(icon), "below the default block-sized floor");
        assertNotNull(MissingTextureDetector.scan(icon, 60), "found once the caller sizes the floor to the subject");
    }
}
