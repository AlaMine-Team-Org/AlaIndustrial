package dev.alaindustrial.visual;

/**
 * The arithmetic behind every visual gate: how many pixels changed, where they changed, and whether a
 * given sprite is present in a frame.
 *
 * <p>This class is the "ruler" the L3 suite measures with. Before MOD-362 the ruler itself was never
 * measured — the comparison logic lived inside the client gametest, could only run inside a booted
 * Minecraft client, and no test asserted that it returns the right numbers. Here it is Minecraft-free
 * and covered by L1 ({@code common/src/test/java/dev/alaindustrial/visual}).
 */
public final class PixelMath {

    private PixelMath() {
    }

    /** Pixels that differ between two same-sized frames. */
    public static int differingPixels(PixelGrid a, PixelGrid b, Tolerance tolerance) {
        requireSameSize(a, b);
        int differing = 0;
        for (int i = 0; i < a.argb().length; i++) {
            if (tolerance.differs(a.argb()[i], b.argb()[i])) {
                differing++;
            }
        }
        return differing;
    }

    /**
     * Pixels that differ inside {@code region} only. This is what makes a gate robust: comparing a GUI
     * window instead of the whole frame ignores the world behind it, which animates on every tick and
     * would otherwise drown the signal in noise.
     */
    public static int differingPixelsIn(PixelGrid a, PixelGrid b, Region region, Tolerance tolerance) {
        requireSameSize(a, b);
        if (!a.contains(region)) {
            throw new IllegalArgumentException(region + " does not fit inside " + a.width() + "x" + a.height());
        }
        int differing = 0;
        for (int y = region.y(); y < region.bottom(); y++) {
            int rowStart = y * a.width();
            for (int x = region.x(); x < region.right(); x++) {
                if (tolerance.differs(a.argb()[rowStart + x], b.argb()[rowStart + x])) {
                    differing++;
                }
            }
        }
        return differing;
    }

    /**
     * The smallest rectangle containing every differing pixel, or {@code null} when the frames match.
     * A failing gate reports this so the reviewer knows where to look instead of hunting a 1280x720 frame.
     */
    public static Region diffBoundingBox(PixelGrid a, PixelGrid b, Tolerance tolerance) {
        requireSameSize(a, b);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < a.height(); y++) {
            int rowStart = y * a.width();
            for (int x = 0; x < a.width(); x++) {
                if (tolerance.differs(a.argb()[rowStart + x], b.argb()[rowStart + x])) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        return maxX < minX ? null : new Region(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Where {@code needle} appears inside {@code haystack}, or {@code null} when it does not.
     *
     * <p>This is the cheap way to assert "the renderer actually drew this sprite" instead of the much
     * weaker "the file is bigger than 2 KiB". Scans row-major and returns the first (top-left-most) hit;
     * a sprite that legitimately repeats is matched at its first occurrence, which is what a presence
     * check wants.
     */
    public static Point findSubImage(PixelGrid haystack, PixelGrid needle, Tolerance tolerance) {
        if (needle.width() > haystack.width() || needle.height() > haystack.height()) {
            return null;
        }
        int lastX = haystack.width() - needle.width();
        int lastY = haystack.height() - needle.height();
        for (int oy = 0; oy <= lastY; oy++) {
            for (int ox = 0; ox <= lastX; ox++) {
                if (matchesAt(haystack, needle, ox, oy, tolerance)) {
                    return new Point(ox, oy);
                }
            }
        }
        return null;
    }

    private static boolean matchesAt(PixelGrid haystack, PixelGrid needle, int ox, int oy, Tolerance tolerance) {
        for (int y = 0; y < needle.height(); y++) {
            int needleRow = y * needle.width();
            int hayRow = (oy + y) * haystack.width() + ox;
            for (int x = 0; x < needle.width(); x++) {
                if (tolerance.differs(haystack.argb()[hayRow + x], needle.argb()[needleRow + x])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Rec. 709 relative luminance, 0.0 (black) to 255.0 (white). */
    public static double luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * The floor a "did the renderer contribute?" gate must clear: {@code max(multiplier * noise, floor)}.
     *
     * <p>Every such gate in the suite hand-rolled this same {@code Math.max(4 * noise, N)} expression.
     * Naming it makes the shared calibration visible — and makes it one edit to change.
     *
     * @param noiseBaseline pixels that differ between two shots of the UNCHANGED scene
     * @param multiplier    how far above the noise the real signal must sit (the suite uses 4)
     * @param absoluteFloor minimum regardless of noise, sized to the object being photographed
     */
    public static int requiredSignal(int noiseBaseline, int multiplier, int absoluteFloor) {
        if (noiseBaseline < 0) {
            throw new IllegalArgumentException("noise baseline cannot be negative: " + noiseBaseline);
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be >= 1, got " + multiplier);
        }
        return Math.max(multiplier * noiseBaseline, absoluteFloor);
    }

    private static void requireSameSize(PixelGrid a, PixelGrid b) {
        if (!a.sameSizeAs(b)) {
            throw new IllegalArgumentException("frame sizes differ: "
                    + a.width() + "x" + a.height() + " vs " + b.width() + "x" + b.height()
                    + " — comparing them pixel-for-pixel is meaningless");
        }
    }
}
