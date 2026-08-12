package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.awaitMenuScreen;
import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.menu.StorageModuleMenu;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The storage module: its connected textures in the world (MOD-287) and the warehouse window they
 * open (MOD-287 round 2).
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404.
 */
@SuppressWarnings("UnstableApiUsage")
public final class StorageModuleStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Captured inside {@code runOnClient}: panel top, panel height, and the screen's scaled height. */
    private static int[] storageWindowBox;

    private StorageModuleStands() {
    }

    /**
     * Photographs the five arrangements the storage module's connected textures exist for: one
     * module alone, two side by side, a 2x2 wall, a row of four, and that row with a fifth module
     * pushed against it.
     *
     * <p><b>This stand used to assert nothing about pixels</b>, and the seams shipped visibly broken
     * underneath it: it took the photographs and trusted that somebody would look. It now carries two
     * pixel gates of its own (see {@link #assertStorageSeamsInFrame}) — that the block renders at all,
     * and that joining the modules really changes what the camera sees.
     *
     * <p>What it still does NOT try to decide is whether the joint is pixel-continuous. That question
     * is answered exactly, off-screen, by {@code tools/gen_storage_module_models.py --check}, which
     * composes the shipped models face by face; a screenshot at an angle, with perspective and
     * ambient occlusion in it, could only answer it approximately. The seam flags themselves are gated
     * headlessly on both loaders by {@code StorageClusterScenarios.seamsMatchClusterMembership} and
     * {@code seamsJoinAWallAndNeverADiagonal}. The frames below stay for a human to look at, and the
     * client-side blockstates are logged next to them so a reviewer can tell "the model is wrong" from
     * "the seam flag never reached the client".
     *
     * <p>The fifth module is the interesting frame: the group outgrows the four-module cap, the
     * warehouse it would belong to stops being well-defined, and every seam in the row — including
     * the one three blocks away — has to disappear. A frame where the row still looks welded
     * together is the visuals lying about what the machine does.
     *
     * <p>Platform at (158..180, 99, 164..176).
     */
    public static void checkStorageModuleSeams(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        server.runCommand("fill 158 99 164 180 105 176 minecraft:air");
        server.runCommand("fill 158 99 164 180 99 176 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");

        // Four groups on one line, two empty blocks between them so none of them merges into the next.
        String module = "alaindustrial:storage_module";
        server.runCommand("setblock 160 100 170 " + module);                       // alone
        server.runCommand("fill 163 100 170 164 100 170 " + module);               // pair
        server.runCommand("fill 167 100 170 168 101 170 " + module);               // 2x2 wall
        server.runCommand("fill 171 100 170 174 100 170 " + module);               // row of four

        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);

        String[][] views = {
            {"167.5 103.5 184.5 180 6", "storage_seams_family"},
            {"160.5 100.5 173.5 180 4", "storage_seams_one"},
            {"163.5 100.5 173.5 180 4", "storage_seams_pair"},
            {"167.5 101.5 174.5 180 4", "storage_seams_wall_2x2"},
            {"170.5 103.0 174.5 150 20", "storage_seams_wall_2x2_iso"},
            {"172.5 100.5 176.5 180 4", "storage_seams_row_4"},
            {"177.5 103.0 176.5 150 20", "storage_seams_row_4_iso"},
        };
        for (String[] view : views) {
            server.runCommand("tp @p " + view[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][STORAGE] {} -> {}",
                    view[1], takeCleanScreenshot(context, view[1]).toAbsolutePath());
        }
        logStorageStates(context, 171, 175);

        assertStorageSeamsInFrame(context, singleplayer, server, module);

        checkStorageWindowFits(context);

        // The fifth module. Everything the row was claiming has to be withdrawn, all the way to its
        // far end.
        server.runCommand("setblock 175 100 170 " + module);
        context.waitTicks(5);
        for (String[] view : new String[][] {
            {"172.5 100.5 176.5 180 4", "storage_seams_row_5"},
            {"178.5 103.0 176.5 150 20", "storage_seams_row_5_iso"},
        }) {
            server.runCommand("tp @p " + view[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][STORAGE] {} -> {}",
                    view[1], takeCleanScreenshot(context, view[1]).toAbsolutePath());
        }
        logStorageStates(context, 171, 175);
    }

    /**
     * Two differential pixel gates on the row of four, both without a committed baseline PNG.
     *
     * <ol>
     *   <li><b>The block renders.</b> The storage module's model is nothing but flat quads sitting on
     *       the block surface — after the connected textures were rewritten as a face partition there
     *       is no solid cube element in it at all, and every face is assembled from up to nine pieces
     *       chosen by the multipart. "Does that produce geometry" is a real question and the answer
     *       cannot come from a JSON checker: clearing the row must change the frame by a wide margin.
     *   <li><b>Joining changes the picture.</b> A fifth module hidden directly BEHIND the westmost one
     *       pushes the group past the cap, so every seam in the row is withdrawn while nothing else in
     *       view moves — the camera is level so the hidden block's top face cannot appear either. The
     *       same camera must therefore see a different picture. If the connected textures never
     *       reached the renderer, the two frames are identical and this fails.
     * </ol>
     *
     * <p>Both thresholds are stated against a noise baseline measured on the spot (two frames of the
     * same, unchanged scene), the way the mill gates in {@link RendererStands} do it, so neither can
     * be flipped by driver dithering.
     */
    private static void assertStorageSeamsInFrame(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, TestServerContext server, String module) {
        // Level camera (pitch 0): the hidden fifth module below must stay hidden.
        server.runCommand("tp @p 172.5 100.9 176.5 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        Path merged = takeCleanScreenshot(context, "storage_gate_merged");
        context.waitTicks(5);
        Path mergedAgain = takeCleanScreenshot(context, "storage_gate_merged_again");
        int noise = differingPixels(merged, mergedAgain);

        server.runCommand("fill 171 100 170 174 100 170 minecraft:air");
        context.waitTicks(5);
        Path cleared = takeCleanScreenshot(context, "storage_gate_cleared");
        int drawn = differingPixels(merged, cleared);
        server.runCommand("fill 171 100 170 174 100 170 " + module);
        context.waitTicks(5);

        int requiredDrawn = Math.max(4 * noise, 5000);
        LOG.info("[GUITEST][STORAGE] render gate: row delta={} px, noise={} px, required>{}",
                drawn, noise, requiredDrawn);
        if (drawn < requiredDrawn) {
            throw new AssertionError("[GUITEST][STORAGE] removing four storage modules changed only "
                    + drawn + " px (noise " + noise + ", required > " + requiredDrawn + ") — the "
                    + "block's face partition produced no geometry in the captured frame. Compare "
                    + merged.getFileName() + " with " + cleared.getFileName() + ".");
        }

        // The hidden fifth module: same x and y as the westmost, one block further north, so the row
        // itself occludes it completely at this camera.
        server.runCommand("setblock 171 100 169 " + module);
        context.waitTicks(5);
        Path split = takeCleanScreenshot(context, "storage_gate_split");
        int seamDelta = differingPixels(merged, split);
        server.runCommand("setblock 171 100 169 minecraft:air");
        context.waitTicks(5);

        int requiredSeam = Math.max(4 * noise, 300);
        LOG.info("[GUITEST][STORAGE] seam gate: merged-vs-split delta={} px, noise={} px, required>{}",
                seamDelta, noise, requiredSeam);
        if (seamDelta < requiredSeam) {
            throw new AssertionError("[GUITEST][STORAGE] outgrowing the four-module cap changed only "
                    + seamDelta + " px (noise " + noise + ", required > " + requiredSeam + ") — the "
                    + "seam flags reach the blockstate but the connected models never reach the "
                    + "frame. Compare " + merged.getFileName() + " with " + split.getFileName() + ".");
        }
    }

    /**
     * The biggest warehouse window, photographed and measured: the whole panel, hotbar included, has
     * to be on the screen.
     *
     * <p>This is the defect, stated as an assertion. A four-module warehouse used to open twelve rows
     * — a 328 px panel — and the player photographed it running off the top AND the bottom with the
     * hotbar clipped. The window now shows six rows and scrolls, which is 220 px: the same height as
     * the vanilla double chest.
     *
     * <p>Two claims, because the test window is not the player's:
     * <ul>
     *   <li><b>Absolute.</b> The panel must fit in 240 px. That is the floor the game itself
     *       guarantees: {@code Window.calculateScale} keeps raising the GUI scale while
     *       {@code height / (scale + 1) >= 240}, so at the default "auto" the usable height is never
     *       below 240 — and on 1440p and 4K it is exactly 240. A panel that fits 240 fits everywhere.
     *   <li><b>Here.</b> In this frame the panel must start at or below the top edge and end at or
     *       above the bottom one, which is what {@code topPos} being negative would deny.
     * </ul>
     *
     * <p>The menu is opened client-side and told it stands on a twelve-row warehouse through the same
     * {@code ContainerData} the server would send, so the scrollbar is in its active state and the
     * frame shows what four modules really look like.
     */
    private static void checkStorageWindowFits(ClientGameTestContext context) {
        // Every rung of the capacity ladder, not only the top one: one module opens the three-row
        // window, and two, three and four modules all open the six-row window over a different number
        // of total rows (so the scrollbar is inactive at two and active at three and four). A window
        // that only fits at one of these is a window the player meets broken at another.
        checkOneStorageWindow(context, 1, 3);
        checkOneStorageWindow(context, 2, 6);
        checkOneStorageWindow(context, 3, 9);
        checkOneStorageWindow(context, 4, 12);
    }

    /**
     * One rung: open the window a {@code modules}-module warehouse would open, photograph it, and
     * measure it.
     */
    private static void checkOneStorageWindow(ClientGameTestContext context, int modules, int totalRows) {
        storageWindowBox = null;
        context.runOnClient(mc -> {
            // The two menu types are different generic parameters, so the branch has to be on the
            // call, not inside it — a conditional would have to unify StorageMenu3 with StorageMenu6.
            if (modules == 1) {
                MenuScreens.create(ModContent.STORAGE_MODULE_MENU_3.get(), mc, 0,
                        Component.literal("Warehouse"));
            } else {
                MenuScreens.create(ModContent.STORAGE_MODULE_MENU_6.get(), mc, 0,
                        Component.literal("Warehouse"));
            }
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs) {
                // What the server sends for a cluster of this size.
                acs.getMenu().setData(StorageModuleMenu.DATA_TOTAL_ROWS, totalRows);
                acs.getMenu().setData(StorageModuleMenu.DATA_TOP_ROW, 0);
                var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
                storageWindowBox = new int[] {
                        pos.alaindustrial$getTopPos(),
                        pos.alaindustrial$getImageHeight(),
                        mc.getWindow().getGuiScaledHeight(),
                };
            }
        });
        awaitMenuScreen(context);
        Path frame = takeCleanScreenshot(context, "gui_storage_warehouse_" + modules + "_modules");
        LOG.info("[GUITEST][STORAGE] window frame ({} modules) -> {}", modules, frame.toAbsolutePath());
        context.runOnClient(mc -> mc.setScreenAndShow(null));

        if (storageWindowBox == null) {
            throw new AssertionError("[GUITEST][STORAGE] the window for " + modules + " module(s) "
                    + "did not open — the menu type has no screen registered for it.");
        }
        int topPos = storageWindowBox[0];
        int panelHeight = storageWindowBox[1];
        int screenHeight = storageWindowBox[2];
        LOG.info("[GUITEST][STORAGE] window fit ({} modules, {} rows): panel={}px top={} screen={}px",
                modules, totalRows, panelHeight, topPos, screenHeight);
        if (panelHeight > 240) {
            throw new AssertionError("[GUITEST][STORAGE] the " + modules + "-module warehouse panel is " + panelHeight
                    + " px tall; the game guarantees only 240 px of usable height at its own GUI "
                    + "scale (Window.calculateScale grows the scale while height/(scale+1) >= 240), "
                    + "so this window is cut off on 1440p and 4K. See " + frame.getFileName() + ".");
        }
        if (topPos < 0 || topPos + panelHeight > screenHeight) {
            throw new AssertionError("[GUITEST][STORAGE] the " + modules + "-module warehouse panel "
                    + "runs off this screen: top=" + topPos + ", height=" + panelHeight + ", screen=" + screenHeight
                    + ". See " + frame.getFileName() + ".");
        }
    }

    /** Log the client's copy of each module's blockstate across {@code x0..x1} at (x, 100, 170). */
    private static void logStorageStates(ClientGameTestContext context, int x0, int x1) {
        context.runOnClient(mc -> {
            for (int x = x0; x <= x1; x++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, 100, 170);
                if (mc.level != null) {
                    LOG.info("[GUITEST][STORAGE] client state x={} -> {}", x, mc.level.getBlockState(pos));
                }
            }
        });
    }
}
