package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.differsAt;

import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.client.screen.WaterMillScreen;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The water mill's GUI status row, in every state it can be in (MOD-354).
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. The wheel this screen belongs to — the
 * BlockEntityRenderer half of the same machine — is gated in {@link RendererStands}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class WaterMillGuiStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /**
     * The water mill window as {@code {leftPos, topPos, imageWidth, guiScaledWidth}} in GUI-scaled
     * units, captured inside {@code runOnClient} by {@link #shootWaterMill}.
     */
    private static int[] waterMillWindowBox;

    /**
     * Height of the cropped band, in GUI-scaled units. The vanilla font is 9 units tall; the extra
     * three units are margin, so a row that shifts by a pixel or grows a descender stays inside the
     * measured area instead of silently falling out of it and reading as "nothing drew".
     */
    private static final int WATER_MILL_ROW_H = 12;

    /**
     * Where the band starts inside the window, in GUI-scaled units. Not zero, because the energy bar
     * ({@code EnergyBarSpec.LEFT_WINDMILL}) spans x 17..26 and y 32..75 — it crosses this row, and its
     * fill height is a function of the stored EU. Every gate below asks "do these two frames differ", so a
     * bar left inside the band could answer that question by itself and let a broken row pass. Starting at
     * 28 clears the bar with a two-unit margin and still leaves 148 units for the text: the longest label
     * ("Wheel interference") is about 96 units wide and, being centred, starts around x 40.
     */
    private static final int WATER_MILL_ROW_X = 28;

    /**
     * Two different labels are two different pictures: the mill's shortest label is "No water", which
     * lights well over a hundred physical pixels at GUI scale 2. A floor this high cannot be met by
     * driver dithering, and cannot be met at all by a row that did not draw.
     */
    private static final int MIN_ROW_DELTA = 100;

    /**
     * Floor for the one comparison where the two rows differ by a single glyph ("Output: 4 EU/t" vs
     * "Output: 1 EU/t"). Minecraft's digits are fixed-width, so the string does not re-centre and the
     * only pixels that can move are the digit itself — roughly 40 at GUI scale 2. Kept well under that,
     * and still an order of magnitude above the dithering floor {@code differsAt} already absorbs.
     */
    private static final int MIN_GLYPH_DELTA = 20;

    private WaterMillGuiStand() {
    }

    /**
     * Every state the water mill's status row can be in, photographed and then measured (MOD-354).
     *
     * <p>The screen was the only one in the generator family with no frame at all: when MOD-348 added the
     * output row to it, the only way to see whether the row drew, fit, or collided with the wheel slot was
     * to launch a client by hand. That is the class of defect this stand exists for — a row that renders
     * off-window, renders empty, or renders the same text for every state is invisible to every server-side
     * L2 test in the suite.
     *
     * <p><b>Why the menu is built client-side.</b> Every frame here opens through {@code MenuScreens.create}
     * and injects into the client's own {@code ContainerData}, deliberately, rather than right-clicking a
     * placed mill. The water mill is a <em>generator</em>: a real one recomputes its state every tick and
     * broadcasts it, so an injected value survives at most one tick before the server overwrites it and the
     * frame shows whatever the world happened to produce. A client-only menu has no server behind it, so the
     * state under test is the state photographed.
     *
     * <p><b>Why this is a test and not a photo album.</b> A screenshot on its own asserts nothing beyond
     * "a file appeared" (see {@code VisualStandSupport.takeCleanScreenshot}). Six frames are compared inside
     * the status row and nowhere else, against a noise floor measured from two identical frames:
     * <ul>
     *   <li>each label differs from the <b>blank</b> row — proves the row draws at all;
     *   <li>running differs from idle, and the three idle reasons differ from each other — proves the
     *       screen switches on {@code getMode()} instead of showing one hardcoded string;
     *   <li>4 EU/t differs from 1 EU/t — proves the number is the menu's rate channel and not a constant.
     * </ul>
     * Cropping to the row is not a way of hiding an inconvenient signal: it is the only band the row can
     * draw in, and every gate here asks whether two frames <em>differ</em>, so anything else that can
     * legitimately differ between these states has to be outside the band or it could satisfy a gate on
     * its own while the row was broken. Excluded on purpose: the wheel slot (y 23..38), the status gear
     * (y 22..37) and the energy bar — which is the subtle one. {@code LEFT_WINDMILL.barBottom()} is 76 and
     * the fill grows <em>upward</em> from it, so the bar occupies y 32..75 and genuinely overlaps this row;
     * it is kept out by starting the band to the right of it, not by the row's y range.
     */
    public static void checkWaterMillStatusRow(ClientGameTestContext context) {
        final int CAP = dev.alaindustrial.Config.waterMillBuffer;
        // One energy level for every frame. The bar is cropped out horizontally, so this is not what makes
        // the gates sound — it keeps the shots comparable by eye, which is what a human reviewing the
        // gallery actually does.
        final int E = CAP / 2;
        final int OK = WaterMillBlockEntity.MODE_OK;

        // Wheel installed, all four drive cells carrying a current — the mill at its maximum, 4 EU/t at
        // the default rate multiplier. The exact number is not what is under test; that it reaches the
        // row, and that a different number draws differently, is.
        Path running = shootWaterMill(context, "running",
                "Running at maximum: the status row reads \"Output: 4 EU/t\", centred, clear of the wheel "
                        + "slot above it, and the status gear beside the slot is lit",
                E, 4, OK, 4, true);
        // Same running state at one drive cell: only the digit in the row may change.
        Path oneSide = shootWaterMill(context, "running_one_side",
                "Running at one drive cell: the same row now reads 1 EU/t - only the digit differs from the "
                        + "frame above, because Minecraft's digits are fixed-width",
                E, 1, OK, 1, true);
        // Wheel installed and free, but no current reaches it.
        Path noWater = shootWaterMill(context, "no_water",
                "Wheel installed and free, but no current reaches it: the row reads \"No water\" in the dim "
                        + "colour, and the status gear is dark",
                E, 0, WaterMillBlockEntity.MODE_NO_WATER, 0, true);
        // A neighbouring mill's wheel overlaps ours; both stall.
        Path interference = shootWaterMill(context, "interference",
                "A neighbouring wheel overlaps ours: the row reads \"Wheel interference\" - the longest of "
                        + "the four labels, so this is the frame a HUMAN should check for the label running "
                        + "past the window edge. The gates below cannot see that: the label is centred, so "
                        + "an overlong one spills outside the measured band on both sides",
                E, 0, WaterMillBlockEntity.MODE_INTERFERENCE, 0, true);
        // A solid block sits in the swept area.
        Path obstructed = shootWaterMill(context, "obstructed",
                "A solid block sits in the swept area: the row reads \"Blocked\"",
                E, 0, WaterMillBlockEntity.MODE_OBSTRUCTED, 0, true);
        // No wheel: the block entity reports MODE_OK because a bare mill has nothing to clash with, and the
        // screen deliberately leaves the row EMPTY rather than claiming an output of 0. That blank row is the
        // reference every other frame is measured against.
        Path noWheel = shootWaterMill(context, "no_wheel",
                "No wheel: the row is deliberately EMPTY - the empty slot is the message, and a mill with no "
                        + "wheel must not claim an output of 0",
                E, 0, OK, 0, false);
        Path noWheelB = shootWaterMill(context, "no_wheel_again",
                "The blank row shot a second time: the noise floor every threshold below is measured against",
                E, 0, OK, 0, false);

        // Nothing in this band animates, so the honest expectation is zero. Measuring it anyway is what
        // turns every threshold below into a claim about the row instead of a claim about the driver.
        int noise = differingPixelsInStatusRow(noWheel, noWheelB);
        LOG.info("[GUITEST][MOD-354] status-row noise floor: {} px", noise);

        assertStatusRowDiffers("running label vs blank row", running, noWheel, noise, MIN_ROW_DELTA);
        assertStatusRowDiffers("\"no water\" vs blank row", noWater, noWheel, noise, MIN_ROW_DELTA);
        assertStatusRowDiffers("\"interference\" vs blank row", interference, noWheel, noise, MIN_ROW_DELTA);
        assertStatusRowDiffers("\"obstructed\" vs blank row", obstructed, noWheel, noise, MIN_ROW_DELTA);

        assertStatusRowDiffers("running label vs \"no water\"", running, noWater, noise, MIN_ROW_DELTA);
        assertStatusRowDiffers("\"no water\" vs \"interference\"", noWater, interference, noise, MIN_ROW_DELTA);
        assertStatusRowDiffers("\"interference\" vs \"obstructed\"", interference, obstructed, noise, MIN_ROW_DELTA);

        assertStatusRowDiffers("4 EU/t vs 1 EU/t", running, oneSide, noise, MIN_GLYPH_DELTA);
    }

    /**
     * One water mill frame: opens the screen, fills the five sync channels the machine owns and puts (or
     * leaves out) the wheel, then photographs it.
     *
     * @param sides drive cells carrying a current (0..4) — channel 2, the wheel renderer's spin input and
     *              the screen's gate for lighting the status gear
     * @param mode  one of {@code WaterMillBlockEntity.MODE_*} — carried by the {@code maxProgress} channel
     * @param rate  effective generation in EU/t — channel 4 (MOD-348), after the global rate multiplier
     *              since MOD-356; injected straight into the channel here, so what the screen prints is
     *              exactly this number and the gates never depend on how the machine would compute it
     * @param wheel whether the component slot holds a water wheel; an empty slot blanks the row
     */
    private static Path shootWaterMill(ClientGameTestContext context, String state, String checks,
                                       int energy, int sides, int mode, int rate, boolean wheel) {
        LOG.info("[GUITEST][MOD-354] opening water_mill/{} (E={} sides={} mode={} rate={} wheel={})",
                state, energy, sides, mode, rate, wheel);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.WATER_MILL_MENU.get(), mc, 0, Component.literal("Water Mill"));
            // No silent fallback. Every gate below asks whether two frames DIFFER, and a frame shot with
            // no state injected differs from the others just as readily as a correct one — so a quiet
            // no-op here would keep the suite green while measuring nothing.
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> acs)
                    || !(acs.getMenu() instanceof MachineMenu menu)) {
                throw new AssertionError("[GUITEST][MOD-354] MenuScreens.create did not open a machine "
                        + "screen for water_mill/" + state + " — the screen binding in MenuScreenManifest "
                        + "is missing, so there is nothing to photograph.");
            }
            menu.injectTestData(energy, dev.alaindustrial.Config.waterMillBuffer, sides, mode);
            menu.injectTestChannel(4, rate);
            // Straight into the client-side SimpleContainer behind slot 0, the same way the solar panel
            // stand injects its chip: the screen reads the slot synchronously, so no server is involved.
            menu.getSlot(0).container.setItem(0, wheel
                    ? new ItemStack(ModContent.WATER_MILL_WHEEL.get())
                    : ItemStack.EMPTY);
            var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
            waterMillWindowBox = new int[] {
                    pos.alaindustrial$getLeftPos(),
                    pos.alaindustrial$getTopPos(),
                    pos.alaindustrial$getImageWidth(),
                    mc.getWindow().getGuiScaledWidth(),
            };
        });
        // Wait for the screen to actually be up, not for a guessed number of ticks (MOD-362): a fixed
        // waitTicks that runs out early photographs whatever was on screen before, and this suite's gates
        // would accept that frame — it differs from the others exactly like a correct one does.
        awaitMenuScreen(context);
        Path path = ShotRecorder.captureScreen("water_mill", state,
                ShotRecorder.rules("R-GUI-01", "R-GUI-03"), checks);
        LOG.info("[GUITEST][MOD-354] screenshot water_mill/{} -> {}", state, path.toAbsolutePath());
        return path;
    }

    /** Pixels that differ inside the water mill's status row, and nowhere else. */
    private static int differingPixelsInStatusRow(Path first, Path second) {
        ShotRecorder.markComparedFiles(first, second);
        if (waterMillWindowBox == null) {
            throw new AssertionError("[GUITEST][MOD-354] the water mill window was never measured");
        }
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST][MOD-354] could not decode " + first + " / " + second);
            }
            // Screenshots are in physical pixels, the box in GUI-scaled units.
            int scale = Math.max(1, a.getWidth() / waterMillWindowBox[3]);
            int x0 = (waterMillWindowBox[0] + WATER_MILL_ROW_X) * scale;
            int y0 = (waterMillWindowBox[1] + WaterMillScreen.STATUS_TEXT_Y - 1) * scale;
            int x1 = Math.min(a.getWidth(), (waterMillWindowBox[0] + waterMillWindowBox[2]) * scale);
            int y1 = Math.min(a.getHeight(), y0 + WATER_MILL_ROW_H * scale);
            int differing = 0;
            for (int y = Math.max(0, y0); y < y1; y++) {
                for (int x = Math.max(0, x0); x < x1; x++) {
                    if (differsAt(a, b, x, y)) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-354] could not read screenshots for the status-row gate", e);
        }
    }

    /** Requires two status rows to be visibly different pictures, above the measured noise floor. */
    private static void assertStatusRowDiffers(String what, Path first, Path second, int noise, int floor) {
        int delta = differingPixelsInStatusRow(first, second);
        int required = Math.max(4 * noise, floor);
        LOG.info("[GUITEST][MOD-354] {}: delta={} px, noise={} px, required>{}", what, delta, noise, required);
        if (delta < required) {
            throw new AssertionError("[GUITEST][MOD-354] " + what + " changed only " + delta
                    + " px in the status row (noise floor " + noise + " px, required > " + required
                    + ") — the two states draw the same row, or the row does not draw at all. The band is "
                    + "x " + WATER_MILL_ROW_X + "..176, y " + (WaterMillScreen.STATUS_TEXT_Y - 1) + ".."
                    + (WaterMillScreen.STATUS_TEXT_Y - 1 + WATER_MILL_ROW_H) + " inside the window; if the "
                    + "layout moved, move STATUS_TEXT_Y with it rather than widening this gate. Compare "
                    + first.getFileName() + " with " + second.getFileName() + ".");
        }
    }
}
