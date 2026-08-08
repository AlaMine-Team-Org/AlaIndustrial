package dev.alaindustrial.visual;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Finds Minecraft's "missing texture" placeholder in a captured frame.
 *
 * <p>A broken blockstate, a model pointing at a texture that was renamed, or an item without its
 * {@code items/&lt;name&gt;.json} definition all render as the same thing: the vanilla magenta-and-black
 * checkerboard. Before MOD-362 nothing in the suite looked for it — a whole machine could render as a
 * pink cube and all 148 frames would still pass, because the only gate was "the PNG is bigger than
 * 2 KiB". Spotting it was left to a human scrolling a folder.
 *
 * <p><b>Why this is not just "count magenta pixels".</b> The mod legitimately ships purple and magenta
 * art, and in-world the placeholder is darkened by block lighting, so an exact colour match finds
 * nothing outdoors and a loose one fires on real textures. The detector therefore requires BOTH
 * signals the placeholder always has:
 * <ul>
 *   <li>the magenta half — red and blue nearly equal and clearly brighter than green, at any brightness,
 *       because scaling by a light level keeps green at zero and keeps red == blue;
 *   <li>the black half — the placeholder is a 2x2 checkerboard, so a genuine hit always has dark
 *       pixels interleaved with the magenta ones inside the same area.
 * </ul>
 * A flat magenta texture with no dark companion is treated as art, not as a defect.
 */
public final class MissingTextureDetector {

    private MissingTextureDetector() {
    }

    /** Vanilla's placeholder magenta, full brightness. */
    public static final int PLACEHOLDER_MAGENTA = 0xFFF800F8;

    /**
     * Smallest cluster worth reporting, in pixels. A missing texture on a block one metre from the
     * camera covers thousands of pixels; a stray magenta pixel from art does not. Callers photographing
     * something small (an item icon) pass their own floor.
     */
    public static final int DEFAULT_MIN_CLUSTER = 400;

    /** A darker pixel than this (Rec. 709 luminance) counts as the checkerboard's black half. */
    private static final double DARK_LUMINANCE = 48.0;

    /**
     * Fraction of the hit area that must be dark for the cluster to read as a checkerboard rather than
     * as flat magenta art. The placeholder is half black by construction; perspective, mipmaps and
     * partial occlusion move that a long way from 0.5, so the window is deliberately wide — it exists
     * to reject FLAT magenta, not to measure the pattern.
     */
    private static final double MIN_DARK_FRACTION = 0.08;

    /** A hit: where the placeholder is and how much of the frame it covers. */
    public record Finding(Region area, int magentaPixels, double darkFraction) {

        @Override
        public String toString() {
            return "missing-texture " + area + " (" + magentaPixels + " magenta px, "
                    + Math.round(darkFraction * 100) + "% dark)";
        }
    }

    /**
     * True for a pixel that could be the magenta half of the placeholder at any light level.
     *
     * <p>The placeholder is {@code #F800F8}: red == blue, green == 0. Multiplying by a light level
     * preserves both properties, so the test is a ratio test, not a colour-equality test.
     */
    public static boolean looksLikePlaceholderMagenta(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (r < 40 || b < 40) {
            return false;
        }
        int lower = Math.min(r, b);
        // Red and blue must track each other: allow 12/255 of absolute slack plus 8% of the level, which
        // covers texture filtering and the renderer's colour quantisation without admitting pink or violet.
        int allowedSpread = Math.max(12, (int) (lower * 0.08));
        if (Math.abs(r - b) > allowedSpread) {
            return false;
        }
        return g <= lower * 0.35;
    }

    /**
     * Scans a frame and returns the largest placeholder cluster, or {@code null} when the frame is clean.
     *
     * @param minClusterPixels magenta pixels a cluster needs before it is reported; see
     *                         {@link #DEFAULT_MIN_CLUSTER}
     */
    public static Finding scan(PixelGrid frame, int minClusterPixels) {
        if (minClusterPixels < 1) {
            throw new IllegalArgumentException("minClusterPixels must be >= 1, got " + minClusterPixels);
        }
        boolean[] visited = new boolean[frame.width() * frame.height()];
        Finding best = null;

        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                int index = y * frame.width() + x;
                if (visited[index] || !looksLikePlaceholderMagenta(frame.argb()[index])) {
                    continue;
                }
                Finding candidate = growCluster(frame, visited, x, y, minClusterPixels);
                if (candidate != null && (best == null || candidate.magentaPixels() > best.magentaPixels())) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** Convenience overload using {@link #DEFAULT_MIN_CLUSTER}. */
    public static Finding scan(PixelGrid frame) {
        return scan(frame, DEFAULT_MIN_CLUSTER);
    }

    /**
     * Flood-fills one connected magenta blob (4-neighbourhood, iterative — a recursive fill would blow
     * the stack on a full-screen placeholder), then decides whether it is a checkerboard or flat art.
     */
    private static Finding growCluster(PixelGrid frame, boolean[] visited, int startX, int startY,
            int minClusterPixels) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {startX, startY});
        visited[startY * frame.width() + startX] = true;

        int count = 0;
        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;

        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int cx = cell[0];
            int cy = cell[1];
            count++;
            if (cx < minX) {
                minX = cx;
            }
            if (cx > maxX) {
                maxX = cx;
            }
            if (cy < minY) {
                minY = cy;
            }
            if (cy > maxY) {
                maxY = cy;
            }

            enqueueIfMagenta(frame, visited, queue, cx + 1, cy);
            enqueueIfMagenta(frame, visited, queue, cx - 1, cy);
            enqueueIfMagenta(frame, visited, queue, cx, cy + 1);
            enqueueIfMagenta(frame, visited, queue, cx, cy - 1);
        }

        if (count < minClusterPixels) {
            return null;
        }

        // The checkerboard's two magenta squares touch only at their corners, so a 4-neighbour fill
        // captures ONE square and its bounding box is solid magenta — measuring darkness inside it would
        // report 0% and reject every genuine placeholder. Look at a margin around the blob instead: that
        // is where the black squares live.
        Region area = new Region(minX, minY, maxX - minX + 1, maxY - minY + 1);
        double darkFraction = darkFractionIn(frame, expanded(area, frame));
        if (darkFraction < MIN_DARK_FRACTION) {
            return null;
        }
        return new Finding(area, count, darkFraction);
    }

    private static void enqueueIfMagenta(PixelGrid frame, boolean[] visited, Deque<int[]> queue, int x, int y) {
        if (x < 0 || y < 0 || x >= frame.width() || y >= frame.height()) {
            return;
        }
        int index = y * frame.width() + x;
        if (visited[index] || !looksLikePlaceholderMagenta(frame.argb()[index])) {
            return;
        }
        visited[index] = true;
        queue.add(new int[] {x, y});
    }

    /**
     * The blob grown by half its own size in every direction, clipped to the frame. Sized relatively
     * because the placeholder's squares scale with how close the camera is: a fixed margin would miss
     * the neighbouring black square on a block filling the screen.
     */
    private static Region expanded(Region area, PixelGrid frame) {
        int margin = Math.max(2, Math.min(area.width(), area.height()) / 2);
        int x0 = Math.max(0, area.x() - margin);
        int y0 = Math.max(0, area.y() - margin);
        int x1 = Math.min(frame.width(), area.right() + margin);
        int y1 = Math.min(frame.height(), area.bottom() + margin);
        return new Region(x0, y0, x1 - x0, y1 - y0);
    }

    private static double darkFractionIn(PixelGrid frame, Region area) {
        int dark = 0;
        for (int y = area.y(); y < area.bottom(); y++) {
            int rowStart = y * frame.width();
            for (int x = area.x(); x < area.right(); x++) {
                if (PixelMath.luminance(frame.argb()[rowStart + x]) <= DARK_LUMINANCE) {
                    dark++;
                }
            }
        }
        return (double) dark / area.area();
    }
}
