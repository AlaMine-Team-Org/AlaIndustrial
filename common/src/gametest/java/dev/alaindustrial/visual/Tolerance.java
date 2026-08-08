package dev.alaindustrial.visual;

/**
 * The named "these two pixels differ" rules every visual gate shares.
 *
 * <p>Before MOD-362 the rule was a bare {@code > 24} buried in a private helper, repeated in a second
 * helper with a copy of the same literal, and every gate that wanted a different strictness had no way
 * to say so. A threshold with a name can be reasoned about in review; a literal cannot.
 *
 * @param name            short label, printed in failure messages so a red gate says WHICH rule it used
 * @param maxChannelDelta the largest per-channel difference (0..255) still counted as "same"
 */
public record Tolerance(String name, int maxChannelDelta) {

    public Tolerance {
        if (maxChannelDelta < 0 || maxChannelDelta > 255) {
            throw new IllegalArgumentException("maxChannelDelta must be 0..255, got " + maxChannelDelta);
        }
    }

    /**
     * Byte-for-byte equality. For synthetic fixtures and GUI sprites drawn without lighting, where any
     * difference at all is a real difference.
     */
    public static final Tolerance EXACT = new Tolerance("exact", 0);

    /**
     * The historical project default: a channel may drift by 24/255 before the pixel counts as changed.
     * Wide enough that headless-driver dithering never flips a verdict, narrow enough that a moved
     * sprite does. Every pre-MOD-362 gate used this value, so keeping it named preserves their calibration.
     */
    public static final Tolerance CHANNEL_24 = new Tolerance("channel-24", 24);

    /**
     * For frames where the same geometry is lit differently between shots (world captures at a moving
     * time of day, blocks under a torch). Only a change bigger than a lighting step counts.
     */
    public static final Tolerance LIGHTING_64 = new Tolerance("lighting-64", 64);

    /** {@code true} when the two colours differ by more than this tolerance in any RGB channel. */
    public boolean differs(int argbA, int argbB) {
        int dr = Math.abs(((argbA >> 16) & 0xFF) - ((argbB >> 16) & 0xFF));
        int dg = Math.abs(((argbA >> 8) & 0xFF) - ((argbB >> 8) & 0xFF));
        int db = Math.abs((argbA & 0xFF) - (argbB & 0xFF));
        return Math.max(dr, Math.max(dg, db)) > maxChannelDelta;
    }

    @Override
    public String toString() {
        return name + "(±" + maxChannelDelta + ")";
    }
}
