package dev.alaindustrial.visual;

/**
 * A decoded image as plain ARGB ints — the currency every visual gate in this package speaks.
 *
 * <p>Deliberately Minecraft-free and AWT-free: the maths that decides "did the renderer draw this?"
 * must be unit-testable from L1 without booting a client or reading a file. {@link PngIo} is the only
 * class in the package that touches the filesystem, and {@code NativeImage}/{@code BufferedImage} are
 * converted at the edges.
 *
 * @param width  image width in pixels, > 0
 * @param height image height in pixels, > 0
 * @param argb   row-major pixel data, {@code width * height} entries, 0xAARRGGBB
 */
public record PixelGrid(int width, int height, int[] argb) {

    public PixelGrid {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("PixelGrid must be non-empty, got " + width + "x" + height);
        }
        long expected = (long) width * height;
        if (argb.length != expected) {
            throw new IllegalArgumentException("PixelGrid " + width + "x" + height + " needs "
                    + expected + " pixels, got " + argb.length);
        }
    }

    /** Colour at (x, y). Throws rather than wrapping — an out-of-range read is a bug in the caller. */
    public int at(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") outside " + width + "x" + height);
        }
        return argb[y * width + x];
    }

    /** {@code true} when both grids can be compared pixel-for-pixel. */
    public boolean sameSizeAs(PixelGrid other) {
        return other != null && other.width == width && other.height == height;
    }

    /** {@code true} when the region lies fully inside this grid. */
    public boolean contains(Region region) {
        return region.x() >= 0 && region.y() >= 0
                && region.x() + region.width() <= width
                && region.y() + region.height() <= height;
    }

    /**
     * A copy of the given region. Used to compare only the GUI window instead of the whole frame —
     * the world behind a menu animates (water, clouds, particles) and would drown any real difference
     * in noise.
     */
    public PixelGrid crop(Region region) {
        if (!contains(region)) {
            throw new IllegalArgumentException(region + " does not fit inside " + width + "x" + height);
        }
        int[] out = new int[region.width() * region.height()];
        for (int row = 0; row < region.height(); row++) {
            System.arraycopy(argb, (region.y() + row) * width + region.x(),
                    out, row * region.width(), region.width());
        }
        return new PixelGrid(region.width(), region.height(), out);
    }

    /** A uniform grid — the base for synthetic fixtures in L1 tests. */
    public static PixelGrid filled(int width, int height, int colour) {
        int[] data = new int[width * height];
        java.util.Arrays.fill(data, colour);
        return new PixelGrid(width, height, data);
    }

    /** This grid with {@code colour} painted over {@code region}; the receiver is left untouched. */
    public PixelGrid withRegionPainted(Region region, int colour) {
        if (!contains(region)) {
            throw new IllegalArgumentException(region + " does not fit inside " + width + "x" + height);
        }
        int[] copy = argb.clone();
        for (int row = 0; row < region.height(); row++) {
            int start = (region.y() + row) * width + region.x();
            java.util.Arrays.fill(copy, start, start + region.width(), colour);
        }
        return new PixelGrid(width, height, copy);
    }

    /** Whole-image region — the default comparison window. */
    public Region bounds() {
        return new Region(0, 0, width, height);
    }
}
