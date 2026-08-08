package dev.alaindustrial.visual;

/**
 * Renders "what changed between these two frames" as a picture.
 *
 * <p>When a pixel gate fails it used to print a sentence — "removing the wheel changed only 812 px,
 * compare A.png with B.png" — and leave the reviewer to open two 1280x720 screenshots and spot the
 * difference by eye. A diff image answers the same question in one glance, which is the difference
 * between triaging a red run in seconds and putting it off.
 */
public final class DiffImage {

    private DiffImage() {
    }

    /** Opaque red; the mod's own art never reaches this saturation in the GUI, so marks stay readable. */
    private static final int MARK_COLOUR = 0xFFFF2020;

    /** How much of the original brightness the unchanged background keeps. */
    private static final double BACKGROUND_DIM = 0.35;

    /**
     * The base frame dimmed to grey with every differing pixel painted red.
     *
     * @param before    the frame that was expected
     * @param after     the frame that was captured
     * @param tolerance the same rule the failing gate used, so the picture shows exactly what the gate saw
     */
    public static PixelGrid render(PixelGrid before, PixelGrid after, Tolerance tolerance) {
        if (!before.sameSizeAs(after)) {
            throw new IllegalArgumentException("cannot diff frames of different sizes: "
                    + before.width() + "x" + before.height() + " vs " + after.width() + "x" + after.height());
        }
        int[] out = new int[before.argb().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = tolerance.differs(before.argb()[i], after.argb()[i])
                    ? MARK_COLOUR
                    : dimToGrey(after.argb()[i]);
        }
        return new PixelGrid(before.width(), before.height(), out);
    }

    /** Greyscale at {@link #BACKGROUND_DIM} brightness, alpha forced opaque. */
    private static int dimToGrey(int argb) {
        int grey = (int) Math.round(PixelMath.luminance(argb) * BACKGROUND_DIM);
        grey = Math.max(0, Math.min(255, grey));
        return 0xFF000000 | (grey << 16) | (grey << 8) | grey;
    }
}
