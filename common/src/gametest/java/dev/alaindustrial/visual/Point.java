package dev.alaindustrial.visual;

/** A pixel coordinate. Origin is top-left. */
public record Point(int x, int y) {

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
