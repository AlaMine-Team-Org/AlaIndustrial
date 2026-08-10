package dev.alaindustrial.gametest.visual;

import dev.alaindustrial.visual.MissingTextureDetector;
import dev.alaindustrial.visual.PixelGrid;
import dev.alaindustrial.visual.PngIo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes every screenshot in the L3 lane, gates it, and records what it was for.
 *
 * <p>This replaces the old {@code takeCleanScreenshot}, which proved exactly one thing: that a PNG
 * bigger than 2 KiB reached the disk. Three things are added here, and each closes a hole the audit
 * behind MOD-362 found:
 *
 * <ul>
 *   <li><b>A missing-texture gate on every frame.</b> A block rendering as the vanilla pink cube used
 *       to pass — the file was large and non-empty. Now any frame carrying the placeholder fails the
 *       run and says where it is.
 *   <li><b>Metadata.</b> Each frame is written into {@code shots-manifest.json} together with the mod
 *       version, the loader, the window geometry, which {@code R-*} rule it serves and what a human
 *       should look at. A screenshot on its own is unreadable a month later.
 *   <li><b>Duplicate-name detection.</b> Frame names were bare string literals; two frames sharing a
 *       name silently overwrote each other's slot in the reviewer's mental model.
 * </ul>
 *
 * <p>State is static because a client gametest is one run in one process, and the alternative —
 * threading a recorder through 60 call sites — would have made the change to the existing suite far
 * larger than the value it adds.
 */
public final class ShotRecorder {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * A real GUI or world frame compresses to several KiB at minimum; a cleared framebuffer lands in the
     * hundreds of bytes. Deliberately generous so headless-driver compression variance never flakes a
     * healthy capture.
     */
    private static final long MIN_SCREENSHOT_BYTES = 2 * 1024L;

    /**
     * Escape hatch for the pink-cube gate: {@code -Dalaindustrial.shots.nomissingtexturegate=1}. Exists
     * so a genuinely magenta future texture can be shipped the same day it is added, without disabling
     * the whole lane — not as a routine switch.
     */
    private static final boolean MISSING_TEXTURE_GATE =
            System.getProperty("alaindustrial.shots.nomissingtexturegate") == null;

    private static ClientGameTestContext context;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Set<String> USED_NAMES = new LinkedHashSet<>();
    private static Path screenshotDir;
    private static String locale = "en_us";

    private ShotRecorder() {
    }

    /** One captured frame, as it will appear in the manifest. */
    private record Entry(int index, String file, String name, String group, String menuId, String locale,
            List<String> rules, String checks, String gate, long bytes) {
    }

    /**
     * Attaches the recorder to a client-gametest class. Call at the start of every {@code runTest}.
     *
     * <p>Deliberately does NOT clear what was already recorded. Fabric runs each registered
     * client-gametest class in the same process, one after another, and they all write into the same
     * screenshots folder with a shared frame counter — so the manifest has to describe the whole run.
     * Clearing here is what made the second lane's manifest silently replace the first lane's, leaving
     * 61 frames documented out of 209 (found in the MOD-362 shakedown run).
     */
    public static void begin(ClientGameTestContext ctx) {
        context = ctx;
        locale = "en_us";
    }

    /**
     * Marks which locale subsequent frames are captured under, so the manifest and the gallery can tell
     * an English frame from its Russian twin.
     */
    public static void locale(String localeCode) {
        locale = localeCode;
    }

    /** A frame with no rule attached — the plain replacement for the old helper. */
    public static Path capture(String name, ShotGroup group) {
        return capture(name, group, null, List.of(), "");
    }

    /**
     * Captures, gates and records one frame.
     *
     * @param name   file stem; must be unique across the run
     * @param group  what is being photographed
     * @param menuId registry id of the menu on screen, or {@code null} for world/item frames — this is
     *               what the menu-to-shot parity gate reads
     * @param rules  {@code R-*} ids from docs/testing/RULES.md that this frame serves
     * @param checks one sentence telling a human reviewer what to look at
     */
    public static Path capture(String name, ShotGroup group, String menuId, List<String> rules, String checks) {
        if (context == null) {
            throw new IllegalStateException("ShotRecorder.begin(context) was never called — the run would "
                    + "produce screenshots with no manifest behind them");
        }
        if (!USED_NAMES.add(name)) {
            throw new AssertionError("[SHOT] duplicate frame name '" + name + "'. Two frames writing the "
                    + "same name make the run unreviewable: the second silently replaces the first in "
                    + "every report that keys on names.");
        }

        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.waitTicks(1);
        Path path = context.takeScreenshot(name);

        long bytes = gateFileIsARealFrame(name, path);
        PixelGrid frame = decode(name, path);
        gateFrameIsNotBlank(name, frame);
        String gate = gateNoMissingTexture(name, group, frame, path) ? "missing-texture" : "blank";

        if (screenshotDir == null) {
            screenshotDir = path.getParent();
        }
        ENTRIES.add(new Entry(ENTRIES.size(), path.getFileName().toString(), name, group.id(), menuId,
                locale, List.copyOf(rules), checks, gate, bytes));
        LOG.info("[SHOT] {} ({}) {} bytes -> {}", name, group.id(), bytes, path.getFileName());
        return path;
    }

    /** Convenience for a machine screen: the menu id doubles as the frame-name prefix. */
    public static Path captureScreen(String menuId, String stateName, List<String> rules, String checks) {
        return capture("gui_" + menuId + "_" + stateName, ShotGroup.GUI, menuId, rules, checks);
    }

    private static long gateFileIsARealFrame(String name, Path path) {
        if (!Files.exists(path)) {
            throw new AssertionError("[SHOT] '" + name + "' was not written to " + path
                    + " — the capture path is broken (renderer threw, screen never opened, or the headless "
                    + "framebuffer is misconfigured).");
        }
        try {
            long size = Files.size(path);
            if (size < MIN_SCREENSHOT_BYTES) {
                throw new AssertionError("[SHOT] '" + name + "' is only " + size + " bytes (< "
                        + MIN_SCREENSHOT_BYTES + ") — likely a blank framebuffer, not a rendered frame. "
                        + "Capture path: " + path);
            }
            return size;
        } catch (IOException e) {
            throw new AssertionError("[SHOT] could not stat '" + name + "' at " + path, e);
        }
    }

    private static PixelGrid decode(String name, Path path) {
        try {
            return PngIo.read(path);
        } catch (RuntimeException e) {
            throw new AssertionError("[SHOT] '" + name + "' could not be decoded: " + e.getMessage(), e);
        }
    }

    /**
     * Fails the run when the frame is a single flat colour.
     *
     * <p>The byte-size gate above does NOT cover this, which was the whole point of measuring instead of
     * assuming: a solid black 1280x720 PNG is 2760 bytes and a solid grey one is 4320 — both sail past
     * the 2 KiB floor that was supposed to catch exactly this. "The renderer drew nothing" was therefore
     * still a passing frame for most of the suite.
     *
     * <p>Counting distinct colours is nearly free here because the frame is already decoded for the
     * missing-texture gate. The bar is deliberately low: any real frame — a GUI, a world view, even a
     * dark cave — carries hundreds of distinct colours from text, shading and dithering.
     */
    private static void gateFrameIsNotBlank(String name, PixelGrid frame) {
        final int MIN_DISTINCT_COLOURS = 16;
        Set<Integer> seen = new LinkedHashSet<>();
        for (int argb : frame.argb()) {
            if (seen.add(argb) && seen.size() >= MIN_DISTINCT_COLOURS) {
                return;
            }
        }
        throw new AssertionError("[SHOT] '" + name + "' is a flat image: only " + seen.size()
                + " distinct colour(s) in " + frame.width() + "x" + frame.height() + " px (need "
                + MIN_DISTINCT_COLOURS + "). The renderer drew nothing — a blank framebuffer, a screen "
                + "that never opened, or a camera pointed into the void. The file-size gate cannot catch "
                + "this: a solid black frame of this size is still ~2.7 KB.");
    }

    /**
     * Fails the run when the frame contains Minecraft's missing-texture placeholder.
     *
     * @return {@code true} when the gate actually ran, so the manifest can record how strongly the frame
     *         was checked rather than implying a check that was skipped
     */
    private static boolean gateNoMissingTexture(String name, ShotGroup group, PixelGrid frame, Path path) {
        if (!MISSING_TEXTURE_GATE) {
            return false;
        }
        // Item frames photograph a 16x16 icon scaled into the GUI, so a broken icon covers far fewer
        // pixels than a broken block does. Sizing the floor to the subject keeps the gate meaningful for
        // both instead of blind to the smaller one.
        int floor = group == ShotGroup.ITEM ? 60 : MissingTextureDetector.DEFAULT_MIN_CLUSTER;
        MissingTextureDetector.Finding finding = MissingTextureDetector.scan(frame, floor);
        if (finding != null) {
            throw new AssertionError("[SHOT] '" + name + "' contains Minecraft's missing-texture "
                    + "placeholder at " + finding.area() + " (" + finding.magentaPixels() + " magenta px). "
                    + "A model, blockstate or texture referenced by this scene does not resolve. Open "
                    + path.getFileName() + " and look at that rectangle.");
        }
        return true;
    }

    /**
     * The frame names this run actually wrote to disk.
     *
     * <p>Exists so a lane can assert it photographed everything it set out to photograph (MOD-388).
     * The recorder is the only place that knows what was really captured: a catalogue entry states an
     * intention, and an intention is what silently went unmet when the run died two screens in.
     *
     * <p>Names, not menu ids, and that distinction is load-bearing. Every lane in the process shares
     * this recorder, and several menus are photographed by more than one of them — {@code water_mill}
     * carries seven frames from the wind/water lane before this one ever opens it. Asking "does any
     * frame carry this menu id" would therefore answer yes for a screen this lane never reached, which
     * is precisely the failure being guarded against.
     */
    public static Set<String> capturedNames() {
        return Set.copyOf(USED_NAMES);
    }

    /**
     * Writes {@code shots-manifest.json} beside the frames. Safe to call when nothing was captured — it
     * says so rather than writing an empty manifest that would read as a successful empty run.
     */
    public static void finish() {
        if (ENTRIES.isEmpty() || screenshotDir == null) {
            LOG.warn("[SHOT] no frames were captured — no manifest written");
            return;
        }
        String json = renderManifest();
        Path target = screenshotDir.resolve("shots-manifest.json");
        try {
            Files.writeString(target, json, StandardCharsets.UTF_8);
            LOG.info("[SHOT] manifest for {} frames -> {}", ENTRIES.size(), target.toAbsolutePath());
        } catch (IOException e) {
            // The manifest is review material, not a gate: losing it must not turn a good run red.
            LOG.error("[SHOT] could not write the manifest to {}", target, e);
        }
    }

    private static String renderManifest() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n  \"schema\": 1,\n  \"run\": {\n");
        sb.append("    \"modVersion\": ").append(quote(modVersion())).append(",\n");
        sb.append("    \"minecraftVersion\": ").append(quote(minecraftVersion())).append(",\n");
        sb.append("    \"loader\": \"fabric\",\n");
        sb.append("    \"commit\": ").append(quote(System.getProperty("alaindustrial.commit", "unknown")))
                .append(",\n");
        int[] window = windowGeometry();
        sb.append("    \"windowWidth\": ").append(window[0]).append(",\n");
        sb.append("    \"windowHeight\": ").append(window[1]).append(",\n");
        sb.append("    \"guiScale\": ").append(window[2]).append(",\n");
        sb.append("    \"shotCount\": ").append(ENTRIES.size()).append("\n  },\n  \"shots\": [\n");
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            sb.append("    {")
                    .append("\"index\": ").append(e.index())
                    .append(", \"file\": ").append(quote(e.file()))
                    .append(", \"name\": ").append(quote(e.name()))
                    .append(", \"group\": ").append(quote(e.group()))
                    .append(", \"menu\": ").append(e.menuId() == null ? "null" : quote(e.menuId()))
                    .append(", \"locale\": ").append(quote(e.locale()))
                    .append(", \"rules\": ").append(quoteList(e.rules()))
                    .append(", \"checks\": ").append(quote(e.checks()))
                    .append(", \"gate\": ").append(quote(e.gate()))
                    .append(", \"bytes\": ").append(e.bytes())
                    .append("}").append(i + 1 < ENTRIES.size() ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static int[] windowGeometry() {
        return context.computeOnClient(mc -> new int[] {
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), (int) mc.getWindow().getGuiScale()});
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("alaindustrial")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String quoteList(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            sb.append(quote(values.get(i)));
            if (i + 1 < values.size()) {
                sb.append(", ");
            }
        }
        return sb.append(']').toString();
    }

    /** Minimal JSON string escaping — the manifest is machine-read by the gallery and the diff tool. */
    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Shorthand for the common "this frame serves these rules" argument. */
    public static List<String> rules(String... ids) {
        return Arrays.asList(ids);
    }
}
