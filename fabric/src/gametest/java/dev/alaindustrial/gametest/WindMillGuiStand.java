package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.differingPixelsInBand;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.screen.HighAltitudeWindMillScreen;
import dev.alaindustrial.client.screen.MachineScreen.EnergyBarSpec;
import dev.alaindustrial.client.screen.StormWindMillScreen;
import dev.alaindustrial.client.screen.WindMillScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Path;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GUI status row of all three wind mills, in every state it can DRAW (MOD-371).
 *
 * <p>Same hole, same shape as the water mill's before MOD-354: {@link RendererStands#checkWindMillRotor}
 * gates the rotor's block-entity renderer out in the world, and {@code ScreensClientGameTest} gives each
 * mill an {@code opened / empty / half / full} frame through the generic menu-state helper — but nothing
 * looks at the row that tells the player why the mill is standing still. A row that fails to draw, draws
 * off-window, or draws the same text for all eight modes is invisible to every server-side L2 test in
 * the suite, because no L2 test renders a screen.
 *
 * <p><b>Why this stand cannot use the water mill's blank-row anchor, and what replaces it.</b> MOD-354
 * measured every label against the state where the row is deliberately EMPTY — a water mill with no
 * wheel, where {@code WaterMillScreen.drawStatusText} returns before drawing. The wind mills have no such
 * state: {@code drawStatusText} in all three screens takes {@code modeLabel(mode)}, falls back to
 * {@code outputLine(rate)} when that is {@code null}, and then calls {@code graphics.text(...)}
 * unconditionally. {@code modeLabel} returns {@code null} for breeze/gale/storm AND for any unrecognised
 * code ({@code default -> null}), so even an invented mode number prints the output line. There is no
 * input that produces a blank row, and manufacturing one would mean deleting the "No rotor" hint from
 * three screens — a product change, not a test change, and not this task's to make.
 *
 * <p>Three things stand in for the anchor, and together they are stronger than it was:
 * <ul>
 *   <li><b>A noise floor from a DUPLICATE of a state that has text.</b> {@code calm} is shot twice and the
 *       two frames must agree to ~0 px. Dithering, if a driver has any, lives on glyph edges, so measuring
 *       it on a frame WITH glyphs is stricter than measuring it on a blank one.
 *   <li><b>Exhaustive pairwise comparison</b> of all eight frames rather than MOD-354's chain of adjacent
 *       states. A chain cannot see two NON-adjacent states collapsing onto one label; every pair can. And
 *       it subsumes what the blank anchor did: if the row stops drawing, every pair drops to 0 px and all
 *       28 gates go red at once, rather than one.
 *   <li><b>A third negative control</b> (see the task log) that moves the band off the row and requires
 *       the gates to go red — the blank anchor's other job was telling "the row is broken" apart from
 *       "the band is not over the row", and this is what restores that.
 * </ul>
 *
 * <p><b>Why the menu is built client-side.</b> Same reason as {@link WaterMillGuiStand}: a wind mill is a
 * generator, a placed one recomputes mode and rate every tick and broadcasts them, so injected state
 * survives at most a tick. MOD-362 recorded this as a known limitation of its own generator frames. A
 * menu opened through {@code MenuScreens.create} has no server behind it, so the state under test is the
 * state photographed.
 */
@SuppressWarnings("UnstableApiUsage")
public final class WindMillGuiStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * The window under test as {@code {leftPos, topPos, imageWidth, guiScaledWidth}} in GUI-scaled units,
     * measured inside {@code runOnClient} by {@link #shootWindMill}.
     */
    private static int[] windowBox;

    /**
     * Left edge of the measured band, as an offset inside the window — one GUI pixel past the right edge
     * of the energy bar, and <b>derived from the bar's own spec</b> rather than copied from it.
     *
     * <p>Not zero, because {@code EnergyBarSpec.LEFT_WINDMILL} is {@code (17, 76, 176, 48)} and 76 is the
     * bar's BOTTOM: {@code renderEnergyBar} draws upward from it, so the bar occupies x 17..26, y 32..75
     * and passes straight through this row. Every gate below asks whether two frames differ, so a bar left
     * inside the band could answer that question by itself while the row was broken. MOD-354 shipped that
     * mistake once and it was invisible — the gates were green only because the energy was pinned to the
     * same value in every frame.
     *
     * <p>The margin between the bar and this band is exactly one GUI pixel — the bar ends at x 26 and the
     * band starts at 28 — which is why the number is computed instead of written down. Against the old
     * literal {@code 28}, shifting {@code LEFT_WINDMILL.barX()} by one pixel eats the whole margin and
     * shifting it by two puts the bar back inside the measured band; either is a plausible GUI tweak, and
     * neither would fail anything. Every gate here asks only "do these two frames differ", so a bar inside
     * the band goes on answering yes while the row is broken. {@code STATUS_TEXT_Y} was made public in this
     * task for exactly the same reason; the x bounds were the half that stayed literals.
     */
    private static final int BAND_X0 = EnergyBarSpec.LEFT_WINDMILL.barX() + EnergyBarSpec.WIDTH + 1;

    /**
     * Right edge of the band (exclusive), as an offset inside the window.
     *
     * <p>Stops short of the window's 176 because the T1 mill's chip slot sits at {@code (149, 39)} in
     * {@code WindMillMenu} and an item in it covers y 39..54 — overlapping this band's y 49..60. The slot
     * is empty in every frame here, so today it contributes nothing; that is discipline, and the band is
     * structure. The two T2 mills have no chip slot at all, but they share the bound so the three mills
     * share one gate. Everything the row can print fits: the widest label is "Grid is not drawing" at
     * about 93 GUI units, centred, so it spans x 41..134.
     *
     * <p>Unlike {@link #BAND_X0} this one stays a literal, and deliberately: there is nothing to derive it
     * from. {@code WindMillMenu.addMachineSlots} builds the chip slot with its coordinates inline
     * ({@code new Slot(machine, CHIP_SLOT, 149, 39)}) and exports no constant for them, so a computed
     * bound here would only move the duplicated 149 one file over. Exporting one is a change to production
     * code for a test's benefit and belongs to whoever next moves that slot.
     */
    private static final int BAND_X1 = 148;

    /**
     * Band height in GUI-scaled units. The vanilla font is 9 units tall; the extra three are margin, so a
     * row that shifts a pixel or grows a descender stays inside the measured area instead of falling out
     * of it and reading as "nothing drew".
     */
    private static final int BAND_H = 12;

    /**
     * Floor for "these two rows are different pictures", in physical pixels at GUI scale 2.
     *
     * <p>Derived from the cost of a GLYPH, not from what a run happened to produce — a floor fitted under
     * the smallest observed delta stops asking "are these distinguishable" and starts asserting "today
     * they differed by this much". MOD-354 measured one fixed-width digit swapping for another at 84 px
     * on this same screen scale, i.e. roughly 40 px of ink per glyph. The shortest label a wind mill can
     * print is "Calm", four glyphs, about 170 px of ink; requiring 100 means a pair still fails the gate
     * even if it somehow shares half of the shorter label's ink. It is also far above anything dithering
     * could reach — {@code differsAt} already absorbs differences up to 24/255 per channel before a pixel
     * counts at all — and unreachable for a row that did not draw.
     */
    private static final int MIN_ROW_DELTA = 100;

    /**
     * Floor for the one pair that differs by a single glyph: "Output: 4 EU/t" against "Output: 1 EU/t".
     * Minecraft's digits are fixed width, so the string does not re-centre and the only pixels that can
     * move are the digit itself — MOD-354 measured 84 px for exactly this comparison. Kept well under
     * that, and still far above the dithering floor. This gate is the only one that proves the number on
     * the row comes from the menu's rate channel instead of being a constant.
     */
    private static final int MIN_GLYPH_DELTA = 20;

    // ── The eight distinguishable pictures the row can show, in the order they are shot ────────────
    private static final String[] STATES = {
            "no_rotor", "roofed", "obstructed", "interference", "calm", "running", "running_low", "buffer_full",
    };
    /** Index into {@link #STATES} of the pair that differs by one digit and nothing else. */
    private static final int RUNNING = 5;
    private static final int RUNNING_LOW = 6;

    private WindMillGuiStand() {
    }

    /**
     * Every state the three wind mills' status rows can be in, photographed and then measured.
     *
     * <p><b>Which states, and why these.</b> All eight mode codes in {@code WindMillBlockEntity} are
     * reachable in a real world — no rotor (empty slot), roofed (no open sky), obstructed
     * ({@code WindMillClearance}), interference (MOD-051, with L2 coverage in {@code WindMillGameTest}),
     * calm (a rate of zero at sea level or above the dead height) and the three weather modes. But
     * reachability is not what bounds this set: DISTINGUISHABILITY is. {@code modeLabel} returns
     * {@code null} for breeze, gale AND storm, so all three fall through to the same {@code outputLine}
     * and, at equal rate and buffer, draw a byte-identical row. Shooting them separately would add a gate
     * that is REQUIRED to be red — so the difference between them is left where it lives and is already
     * tested, on the server side in {@code WindMillGameTest}. What IS added over the mode list is the
     * branch {@code MachineScreen.outputLine} takes on a full buffer ("Grid is not drawing"): a picture of
     * the row that no mode code produces, and one the water mill stand never covered.
     *
     * <p><b>Why every frame with a number keeps energy below capacity.</b> That same
     * {@code outputLine} branch fires on {@code energy >= capacity}. Left unpinned, five frames would
     * silently collapse into "Grid is not drawing" and the pairwise gates would go red for a reason that
     * is not a defect.
     */
    public static void checkWindMillStatusRows(ClientGameTestContext context) {
        checkMill(context, "wind_mill", ModContent.WIND_MILL_MENU.get(), "Wind Mill",
                WindMillScreen.class, Config.windMillBuffer, WindMillScreen.STATUS_TEXT_Y,
                WindMillBlockEntity.RATE_CHANNEL);
        checkMill(context, "high_altitude_wind_mill", ModContent.HIGH_ALTITUDE_WIND_MILL_MENU.get(),
                "High Altitude Wind Mill", HighAltitudeWindMillScreen.class, Config.t2WindMillBuffer,
                HighAltitudeWindMillScreen.STATUS_TEXT_Y, HighAltitudeWindMillBlockEntity.RATE_CHANNEL);
        checkMill(context, "storm_wind_mill", ModContent.STORM_WIND_MILL_MENU.get(), "Storm Wind Mill",
                StormWindMillScreen.class, Config.t2WindMillBuffer, StormWindMillScreen.STATUS_TEXT_Y,
                StormWindMillBlockEntity.RATE_CHANNEL);
    }

    /**
     * One mill: nine frames, a noise floor and 28 gates.
     *
     * @param screenClass the screen this mill's menu type MUST open — see {@link #shootWindMill}, which
     *                    fails the run when something else is in front
     * @param statusTextY the screen's OWN {@code STATUS_TEXT_Y}, read from the screen class rather than
     *                    copied — a copy survives a layout change and leaves the gate measuring an empty
     *                    band, green, forever
     * @param rateChannel the mill's own effective-rate channel: 6 on the T1 mill (seven-wide data), 4 on
     *                    both T2 mills (five-wide). Hardcoding either would make the other gate measure a
     *                    channel the screen does not read.
     */
    private static void checkMill(ClientGameTestContext context, String menuId, MenuType<?> type,
                                  String displayName, Class<?> screenClass, int capacity, int statusTextY,
                                  int rateChannel) {
        // Half a buffer in every frame but buffer_full: the bar is cropped out horizontally so this is not
        // what makes the gates sound, but it keeps energy under capacity (see outputLine above) and keeps
        // the frames comparable by eye, which is what a human reviewing the gallery actually does.
        final int E = capacity / 2;
        final int RATE = 4;
        final int RATE_LOW = 1;

        windowBox = null;
        Path[] frames = new Path[STATES.length];

        frames[0] = shootWindMill(context, menuId, type, displayName, screenClass, "no_rotor",
                "Rotor slot empty: the row reads \"No rotor\" in the dim colour and the status gear beside "
                        + "the slot is dark - the mill is telling the player what to do about it",
                capacity, E, WindMillBlockEntity.MODE_NO_ROTOR, 0, rateChannel, false);
        frames[1] = shootWindMill(context, menuId, type, displayName, screenClass, "roofed",
                "A block above the mill cuts it off from the sky: the row reads \"Roofed\"",
                capacity, E, WindMillBlockEntity.MODE_ROOFED, 0, rateChannel, true);
        frames[2] = shootWindMill(context, menuId, type, displayName, screenClass, "obstructed",
                "A solid block stands inside the rotor disc: the row reads \"Blocked\"",
                capacity, E, WindMillBlockEntity.MODE_OBSTRUCTED, 0, rateChannel, true);
        frames[3] = shootWindMill(context, menuId, type, displayName, screenClass, "interference",
                "A neighbouring mill's rotor disc overlaps this one (MOD-051): the row reads "
                        + "\"Interference\" - the longest of the five mode labels, so this is the frame a "
                        + "HUMAN should check for the label running past the window edge. The gates below "
                        + "cannot see that: the label is centred, so an overlong one spills outside the "
                        + "measured band on both sides",
                capacity, E, WindMillBlockEntity.MODE_INTERFERENCE, 0, rateChannel, true);
        frames[4] = shootWindMill(context, menuId, type, displayName, screenClass, "calm",
                "Clear sky, rotor free, but the wind carries nothing here: the row reads \"Calm\" - the "
                        + "SHORTEST label the row can print, which is what the pixel floor is sized against",
                capacity, E, WindMillBlockEntity.MODE_CALM, 0, rateChannel, true);
        frames[RUNNING] = shootWindMill(context, menuId, type, displayName, screenClass, "running",
                "Generating in a breeze with room in the buffer: the row switches to the bright colour and "
                        + "reads \"Output: 4 EU/t\", and the status gear beside the rotor slot is lit",
                capacity, E, WindMillBlockEntity.MODE_BREEZE, RATE, rateChannel, true);
        frames[RUNNING_LOW] = shootWindMill(context, menuId, type, displayName, screenClass, "running_low",
                "The same running state at 1 EU/t: only the digit differs from the frame above, because "
                        + "Minecraft's digits are fixed-width and the row does not re-centre",
                capacity, E, WindMillBlockEntity.MODE_BREEZE, RATE_LOW, rateChannel, true);
        frames[7] = shootWindMill(context, menuId, type, displayName, screenClass, "buffer_full",
                "Generating, but the buffer is full: the row must NOT keep advertising a rate it is not "
                        + "delivering - it reads \"Grid is not drawing\" instead (MachineScreen.outputLine)",
                capacity, capacity, WindMillBlockEntity.MODE_BREEZE, RATE, rateChannel, true);
        Path calmAgain = shootWindMill(context, menuId, type, displayName, screenClass, "calm_again",
                "The \"Calm\" row shot a second time: the noise floor every threshold below is measured "
                        + "against. Taken on a frame that HAS text on purpose - dithering, where a driver "
                        + "has any, lives on the edges of glyphs",
                capacity, E, WindMillBlockEntity.MODE_CALM, 0, rateChannel, true);

        // Nothing in this band animates, so the honest expectation is zero. Measuring it anyway is what
        // turns every threshold below into a claim about the row instead of a claim about the driver.
        int noise = differingPixelsInBand(frames[4], calmAgain, windowBox,
                BAND_X0, BAND_X1, statusTextY - 1, BAND_H);
        LOG.info("[GUITEST][MOD-371] {} status-row noise floor: {} px (band {})", menuId, noise,
                VisualStandSupport.describeBand(BAND_X0, BAND_X1, statusTextY - 1, BAND_H));

        // Every pair, not a chain of neighbours. A chain would miss two NON-adjacent states collapsing
        // onto one label, and there is no blank anchor here to catch that from the other side.
        for (int i = 0; i < frames.length; i++) {
            for (int j = i + 1; j < frames.length; j++) {
                boolean digitsOnly = i == RUNNING && j == RUNNING_LOW;
                assertRowDiffers(menuId + ": \"" + STATES[i] + "\" vs \"" + STATES[j] + "\"",
                        frames[i], frames[j], noise, digitsOnly ? MIN_GLYPH_DELTA : MIN_ROW_DELTA,
                        statusTextY);
            }
        }
    }

    /**
     * One wind mill frame: opens the screen client-side, fills the channels the row reads, puts (or leaves
     * out) the rotor, and photographs it.
     *
     * @param mode        one of {@code WindMillBlockEntity.MODE_*}. Carried by the {@code maxProgress}
     *                    channel, which is what all three menus return from {@code getMode()}. Use the
     *                    constants and nothing else: the javadoc on the three menus still describes the
     *                    pre-MOD-051 ladder ("2 calm"), while the real code has 2 as OBSTRUCTED and 3 as
     *                    CALM, so a number copied out of that comment photographs the wrong label.
     * @param rate        effective EU/t, injected straight into {@code rateChannel}, so what the screen
     *                    prints is exactly this number and the gates never depend on how a real mill
     *                    would compute it
     * @param rotor       whether the rotor slot holds a rotor. Outside the measured band (the slot is at
     *                    y 23..38), so this is for the human looking at the frame, not for the gate.
     * @param screenClass the screen that must end up in front. Checking the TYPE, not merely that some
     *                    container screen is up, is what makes the three mills three subjects instead of
     *                    one: nothing closes the previous mill's screen between shots, so a
     *                    {@code MenuScreens.create} that quietly did nothing — an unbound menu type — would
     *                    leave the PREVIOUS mill on screen, take the injection, and photograph it under the
     *                    next mill's name. Every gate would pass, the deltas would look perfect, and the
     *                    suite would be measuring one screen three times.
     */
    private static Path shootWindMill(ClientGameTestContext context, String menuId, MenuType<?> type,
                                      String displayName, Class<?> screenClass, String state, String checks,
                                      int capacity, int energy, int mode, int rate, int rateChannel,
                                      boolean rotor) {
        LOG.info("[GUITEST][MOD-371] opening {}/{} (E={}/{} mode={} rate={} channel={} rotor={})",
                menuId, state, energy, capacity, mode, rate, rateChannel, rotor);
        context.runOnClient(mc -> {
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            // No silent fallback. Every gate below asks whether two frames DIFFER, and a frame shot with
            // no state injected differs from the others just as readily as a correct one — so a quiet
            // no-op here would keep the suite green while measuring nothing.
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)
                    || !(acs.getMenu() instanceof MachineMenu menu)) {
                throw new AssertionError("[GUITEST][MOD-371] MenuScreens.create did not open a machine "
                        + "screen for " + menuId + "/" + state + " — the screen binding in "
                        + "MenuScreenManifest is missing, so there is nothing to photograph.");
            }
            // ...and it has to be THIS mill's screen. See the parameter doc: the previous mill's screen is
            // still up when this runs, and it would satisfy the check above perfectly well.
            if (!screenClass.isInstance(acs)) {
                throw new AssertionError("[GUITEST][MOD-371] " + menuId + "/" + state + " expected "
                        + screenClass.getSimpleName() + " but " + acs.getClass().getSimpleName()
                        + " is in front. MenuScreens.create left the previous screen up, which means this "
                        + "menu type has no screen bound to it — the frame would carry another mill's "
                        + "layout under this mill's name.");
            }
            // progress (channel 2) stays 0: on these mills it is the MECHANICAL rate that drives the
            // rotor renderer's spin, not the number the row prints. maxProgress (channel 3) is the mode.
            menu.injectTestData(energy, capacity, 0, mode);
            menu.injectTestChannel(rateChannel, rate);
            // Straight into the client-side SimpleContainer behind slot 0, the same way the water mill
            // stand injects its wheel: the screen reads the slot synchronously, so no server is involved.
            menu.getSlot(0).container.setItem(0, rotor
                    ? new ItemStack(ModContent.WINDMILL_ROTOR.get())
                    : ItemStack.EMPTY);
            var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
            int[] box = new int[] {
                    pos.alaindustrial$getLeftPos(),
                    pos.alaindustrial$getTopPos(),
                    pos.alaindustrial$getImageWidth(),
                    mc.getWindow().getGuiScaledWidth(),
            };
            // The band is placed from ONE box but applied to every frame of this mill, so the box has to
            // be the same one every time. A screen that laid itself out differently would silently shift
            // the band off the row, and the gates would then measure background against background.
            if (windowBox != null && !Arrays.equals(windowBox, box)) {
                throw new AssertionError("[GUITEST][MOD-371] the " + menuId + " window moved between "
                        + "frames: " + Arrays.toString(windowBox) + " -> " + Arrays.toString(box)
                        + ". The band is placed once per mill, so every frame of it must share a layout.");
            }
            windowBox = box;
        });
        // Wait for the screen to actually be up, not for a guessed number of ticks (MOD-362): a fixed
        // waitTicks that runs out early photographs whatever was on screen before, and this suite's gates
        // would accept that frame — it differs from the others exactly like a correct one does.
        awaitMenuScreen(context);
        Path path = ShotRecorder.captureScreen(menuId, state,
                ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
        LOG.info("[GUITEST][MOD-371] screenshot {}/{} -> {}", menuId, state, path.toAbsolutePath());
        return path;
    }

    /** Requires two status rows to be visibly different pictures, above the measured noise floor. */
    private static void assertRowDiffers(String what, Path first, Path second, int noise, int floor,
                                         int statusTextY) {
        int delta = differingPixelsInBand(first, second, windowBox, BAND_X0, BAND_X1,
                statusTextY - 1, BAND_H);
        int required = Math.max(4 * noise, floor);
        LOG.info("[GUITEST][MOD-371] {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
        if (delta < required) {
            throw new AssertionError("[GUITEST][MOD-371] " + what + " changed only " + delta
                    + " px in the status row (noise floor " + noise + " px, required > " + required
                    + ") — the two states draw the same row, or the row does not draw at all. The band is "
                    + VisualStandSupport.describeBand(BAND_X0, BAND_X1, statusTextY - 1, BAND_H)
                    + "; if the layout moved, move STATUS_TEXT_Y with it rather than widening this gate. "
                    + "Compare " + first.getFileName() + " with " + second.getFileName() + ".");
        }
    }
}
