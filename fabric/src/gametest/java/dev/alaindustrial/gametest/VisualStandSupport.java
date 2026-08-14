package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.visual.DiffImage;
import dev.alaindustrial.visual.PixelGrid;
import dev.alaindustrial.visual.PngIo;
import dev.alaindustrial.visual.Tolerance;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The handful of operations every visual stand in this package shares: take a frame, wait for a
 * screen, count the pixels two frames differ by (MOD-404).
 *
 * <p>These used to be private methods of {@code GuiClientGameTest}, which is why the file was 3108
 * lines long: a stand could not be moved out of it without taking a copy of the pixel comparison
 * with it, and a copied threshold is two independent numbers that happen to agree — exactly the
 * defect MOD-362 removed from {@link #differsAt} / {@link #differsFrom}. One home, static-imported
 * by the stands, keeps the split honest.
 *
 * <p>Static-import these rather than qualifying them, the way {@code EnergyScenarioSupport} is used
 * in {@code common/src/gametest}: the call sites then read exactly as they did inside the monolith.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VisualStandSupport {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private VisualStandSupport() {
    }

    /**
     * Captures one frame through the shared recorder (MOD-362).
     *
     * <p>This used to hold its own copy of the "the file exists and is bigger than 2 KiB" gate and
     * nothing else. Routing every frame through {@link ShotRecorder} gives all 148 of them three things
     * they did not have: the missing-texture gate (a machine rendering as a pink cube used to pass),
     * an entry in {@code shots-manifest.json}, and a duplicate-name check.
     *
     * <p>Round 3 added the fourth: the frame's group, its {@code R-*} rule and its reviewer caption now
     * come from {@link dev.alaindustrial.visual.ShotFamilies}, keyed on the name. Until then every frame
     * captured through here went in with {@code null, List.of(), ""} — 148 of the 236 frames in the
     * 2026-08-11 nightly had no rule and no caption, which is a picture, not a check.
     */
    public static Path takeCleanScreenshot(ClientGameTestContext context, String name) {
        return ShotRecorder.capture(name);
    }

    /**
     * Waits until the menu screen just created is actually on screen and has had a tick to lay itself
     * out (MOD-362).
     *
     * <p>Replaces the {@code waitTicks(5)} that used to follow every {@code MenuScreens.create}: five
     * ticks was a guess, and on a slower machine — a CI runner on a software rasteriser, say — the
     * capture could happen before the screen finished drawing. The result would not be a failure; it
     * would be a screenshot of the wrong thing, in a green run. Waiting on the condition is both
     * correct and faster, because a ready screen no longer costs the full five ticks.
     */
    public static void awaitMenuScreen(ClientGameTestContext context) {
        context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        // One tick after the screen exists: menu data injected in the same runOnClient block is applied
        // to the widgets on the next layout pass, and the frame is only meaningful after that.
        context.waitTicks(1);
    }

    /**
     * Pixels whose colour differs by more than 24/255 in any channel. Shared by every pixel gate.
     *
     * <p>Registers the comparison against BOTH frames (MOD-362 round 3). Doing it here rather than at
     * the ~25 call sites is what keeps the manifest honest by construction: a pixel gate added later
     * is recorded without anybody remembering to record it, and the alternative — a marker call per
     * site — is a line that gets copied without its neighbour and quietly understates the suite.
     */
    public static int differingPixels(Path first, Path second) {
        ShotRecorder.markComparedFiles(first, second);
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST] could not decode " + first + " / " + second);
            }
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                throw new AssertionError("[GUITEST] screenshot sizes differ: "
                        + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight());
            }
            int differing = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    if (differsAt(a, b, x, y)) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST] could not read screenshots for the pixel gate", e);
        }
    }

    /**
     * Pixels that differ inside ONE band of a machine window, and nowhere else (MOD-371).
     *
     * <p>Cropping is what makes a "these two frames differ" gate mean anything: a machine screen has
     * several elements that legitimately change between the states under test — the energy bar, a slot's
     * contents, a progress sprite — and any of them left inside the band could satisfy the gate on its
     * own while the element actually under test was broken. The band therefore has to be argued from
     * geometry at the call site, which is why the bounds are parameters here rather than constants.
     *
     * <p>Lives here rather than in each stand because a copied comparison is a copied tolerance, and a
     * copied tolerance is two independent numbers that happen to agree — the defect MOD-362 removed from
     * {@link #differsAt}. {@link ShotRecorder#markComparedFiles} is called here for the same reason it is
     * called inside {@link #differingPixels}: registering the comparison at the ~30 call sites instead
     * means one of them eventually forgets, and the manifest then understates how deeply the suite checked.
     *
     * @param windowBox {@code {leftPos, topPos, imageWidth, guiScaledWidth}} in GUI-scaled units, as
     *                  measured inside {@code runOnClient} when the frame was taken
     * @param x0        band's left edge, as an offset inside the window (GUI units)
     * @param x1        band's right edge, exclusive, as an offset inside the window (GUI units)
     * @param bandY     band's top edge, as an offset inside the window (GUI units)
     * @param bandH     band height (GUI units)
     */
    public static int differingPixelsInBand(Path first, Path second, int[] windowBox,
                                            int x0, int x1, int bandY, int bandH) {
        ShotRecorder.markComparedFiles(first, second);
        if (windowBox == null) {
            throw new AssertionError("[GUITEST] the window was never measured — the band cannot be placed");
        }
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST] could not decode " + first + " / " + second);
            }
            // Screenshots are in physical pixels, the box and the band in GUI-scaled units.
            int scale = Math.max(1, a.getWidth() / windowBox[3]);
            int px0 = Math.max(0, (windowBox[0] + x0) * scale);
            int py0 = Math.max(0, (windowBox[1] + bandY) * scale);
            int px1 = Math.min(a.getWidth(), (windowBox[0] + x1) * scale);
            int py1 = Math.min(a.getHeight(), (windowBox[1] + bandY + bandH) * scale);
            int differing = 0;
            for (int y = py0; y < py1; y++) {
                for (int x = px0; x < px1; x++) {
                    if (differsAt(a, b, x, y)) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST] could not read screenshots for the band gate", e);
        }
    }

    /**
     * The band a gate measured, spelled out for its failure message.
     *
     * <p>Built from the same four numbers {@link #differingPixelsInBand} was handed, on purpose. A gate
     * that describes its band with its own literals drifts away from the band it actually measures, and
     * then sends whoever is fixing it to the wrong rows — MOD-354 shipped exactly that (a diagnostic
     * reading {@code y 44..56} over a band of {@code y 43..55}) and had to fix it in review.
     */
    public static String describeBand(int x0, int x1, int bandY, int bandH) {
        return "x " + x0 + ".." + x1 + ", y " + bandY + ".." + (bandY + bandH) + " inside the window";
    }

    /**
     * The single "these two pixels differ" rule every pixel gate in this suite shares.
     *
     * <p>Delegates to {@link Tolerance#CHANNEL_24} instead of carrying its own copy of the literal 24
     * (MOD-362). The threshold used to be written out here AND in {@link #differsFrom}, so "the shared
     * rule" was in fact two independent numbers that happened to agree — changing the strictness of the
     * suite meant finding every copy. Now there is one, and it is unit-tested on both sides of its
     * boundary in {@code common/src/test/java/dev/alaindustrial/visual/ToleranceTest.java}.
     */
    public static boolean differsAt(BufferedImage a, BufferedImage b, int x, int y) {
        return Tolerance.CHANNEL_24.differs(a.getRGB(x, y), b.getRGB(x, y));
    }

    /** True when the framebuffer pixel is a different colour than {@code expected} (same slack as
     * {@link #differsAt}, so driver dithering never registers as product). */
    public static boolean differsFrom(BufferedImage image, int x, int y, int expected) {
        return Tolerance.CHANNEL_24.differs(image.getRGB(x, y), expected);
    }

    /**
     * Writes a diff picture for a failing pixel gate and returns the sentence that points at it.
     *
     * <p>A failing gate used to say "compare A.png with B.png" and leave the reviewer to spot the
     * difference between two 1280x720 screenshots by eye. The diff marks the changed pixels in red on a
     * dimmed background, so the answer is visible at a glance (MOD-362).
     */
    public static String explainWithDiff(Path expected, Path actual) {
        try {
            PixelGrid before = PngIo.read(expected);
            PixelGrid after = PngIo.read(actual);
            Path diff = PngIo.writeDiffBeside(actual, DiffImage.render(before, after, Tolerance.CHANNEL_24));
            if (diff != null) {
                return "Compare " + expected.getFileName() + " with " + actual.getFileName()
                        + "; the marked-up difference is in " + diff.getFileName() + ".";
            }
        } catch (RuntimeException e) {
            // The diff is triage material. If it cannot be produced, the real assertion must still be
            // the thing that gets reported — never mask a gate failure with an I/O failure.
            LOG.warn("[GUITEST] could not render the diff image for {} vs {}", expected, actual, e);
        }
        return "Compare " + expected.getFileName() + " with " + actual.getFileName() + ".";
    }
}
