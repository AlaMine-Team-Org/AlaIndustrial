package dev.alaindustrial.visual;

/** A rectangle in image space. Origin is top-left, as in every image API this package touches. */
public record Region(int x, int y, int width, int height) {

    public Region {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Region must be non-empty, got " + width + "x" + height);
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int area() {
        return width * height;
    }

    /** A region centred on (cx, cy). Odd sizes centre exactly; even sizes lean top-left. */
    public static Region centredOn(int cx, int cy, int width, int height) {
        return new Region(cx - width / 2, cy - height / 2, width, height);
    }

    @Override
    public String toString() {
        return width + "x" + height + "@(" + x + "," + y + ")";
    }
}
