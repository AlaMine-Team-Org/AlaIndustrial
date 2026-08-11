package dev.alaindustrial.visual;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What was actually verified about one frame, and how deep that verification goes.
 *
 * <p><b>The hole this closes.</b> Until now a frame carried a single {@code gate} field, and every
 * frame carried the same value in it — the missing-texture scan runs on all of them. The gallery read
 * that field to decide whether a frame was "checked by a machine" or "checked by human eyes only", so
 * it reported 4 frames out of 159 as eyes-only. Measured against the suite as it really is:
 *
 * <ul>
 *   <li>236 frames pass "the file is a real, non-blank frame with no missing-texture placeholder";
 *   <li>~48 are additionally compared pixel-for-pixel against ANOTHER frame of the same run;
 *   <li><b>1</b> is compared against a STORED reference.
 * </ul>
 *
 * <p>The old field was not false — the gate really ran — it just answered "did anything render?" while
 * being read as "did the RIGHT thing render?". Those are different questions and the gap between them
 * is 235 frames wide. Recording the checks that actually ran, and deriving a depth from them, makes the
 * difference countable instead of implied.
 *
 * <p>Deliberately Minecraft-free and in {@code common}: the classification is pure data, so it is
 * covered by L1 tests that need no client. A rule that decides how strongly the suite is checked must
 * itself be checked.
 */
public final class ShotChecks {

    /** The file exists on disk and is bigger than a cleared framebuffer. */
    public static final String FILE_SIZE = "file-size";

    /** The frame carries more than a handful of distinct colours, so the renderer drew something. */
    public static final String NOT_BLANK = "not-blank";

    /** No cluster of Minecraft's magenta/black missing-texture placeholder in the frame. */
    public static final String NO_MISSING_TEXTURE = "no-missing-texture";

    /** Compared pixel-for-pixel against another frame of the SAME run (a before/after pair). */
    public static final String PIXEL_DIFF = "pixel-diff";

    /** Compared against a reference committed to the repository, not against a sibling frame. */
    public static final String STORED_TEMPLATE = "stored-template";

    /**
     * Depth names, weakest first. Kept as an ordered list so a report can print the ladder in a fixed
     * order and a reader can see which rung the bulk of the suite sits on.
     */
    public static final List<String> DEPTHS = List.of("shallow", "smoke", "compared", "referenced");

    /** Only the file-level checks ran — the pink-cube scan was switched off for this frame. */
    public static final String SHALLOW = "shallow";

    /** Every frame-level gate ran, but nothing was compared: "something plausible rendered". */
    public static final String SMOKE = "smoke";

    /** Compared against another frame of the same run: "this changed / did not change". */
    public static final String COMPARED = "compared";

    /** Compared against a stored reference: "the thing that should be there, is there". */
    public static final String REFERENCED = "referenced";

    private ShotChecks() {
    }

    /**
     * Classifies a frame by the strongest check applied to it.
     *
     * <p>Strongest, not "the last one added": a frame compared against both a sibling and a stored
     * reference is {@code referenced}, because that is the strongest statement that can be made about
     * it. Reporting the weakest would understate the suite; reporting the most recent would make the
     * verdict depend on call order.
     */
    public static String depthOf(Collection<String> checks) {
        if (checks == null || checks.isEmpty()) {
            return SHALLOW;
        }
        if (checks.contains(STORED_TEMPLATE)) {
            return REFERENCED;
        }
        if (checks.contains(PIXEL_DIFF)) {
            return COMPARED;
        }
        if (checks.contains(NO_MISSING_TEXTURE)) {
            return SMOKE;
        }
        return SHALLOW;
    }

    /**
     * The checks every captured frame goes through, in the order the recorder applies them.
     *
     * @param missingTextureGate whether the pink-cube scan ran — it can be switched off for a genuinely
     *                           magenta texture, and a frame captured with it off must not claim it
     */
    public static Set<String> baseline(boolean missingTextureGate) {
        Set<String> checks = new LinkedHashSet<>(List.of(FILE_SIZE, NOT_BLANK));
        if (missingTextureGate) {
            checks.add(NO_MISSING_TEXTURE);
        }
        return checks;
    }
}
