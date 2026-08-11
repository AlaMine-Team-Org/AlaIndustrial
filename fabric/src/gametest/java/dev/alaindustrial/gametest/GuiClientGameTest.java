package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.visual.ShotGroup;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.visual.DiffImage;
import dev.alaindustrial.visual.PixelGrid;
import dev.alaindustrial.visual.PixelMath;
import dev.alaindustrial.visual.PngIo;
import dev.alaindustrial.visual.Tolerance;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.client.screen.WaterMillScreen;
import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.registry.ModItems;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.client.CameraType;
import dev.alaindustrial.menu.StorageModuleMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visual regression test suite for AlaIndustrial. Runs a real Minecraft client (headless, no
 * display window) and takes screenshots that a human reviewer checks after each build.
 *
 * <p>Coverage map (RULES.md → test method):
 * <ul>
 *   <li>R-GUI-01   — {@link #shootGuiScreenshots}         — all GUIs open without crash
 *   <li>R-GUI-04   — {@link #shootAdvancementScreens}     — advancement-tab icons render (not white/offset) under gui context
 *   <li>R-VIS-04   — {@link #checkSixFaceSurvey}          — all 6 block faces visible and correct
 *   <li>R-VIS-01   — {@link #checkActiveIdleTextures}     — idle vs active texture change
 *   <li>R-CON-03   — {@link #checkCableConnectivity}      — cable model updates per neighbour
 *   <li>R-PHY-10   — {@link #checkHitboxes}               — hitbox shape matches block model
 *   <li>MOD-024    — {@link #checkWaterMillWheel}       — the water wheel BlockEntityRenderer draws into the frame
 *   <li>MOD-232    — {@link #checkWindMillRotor}       — the wind mill rotor BlockEntityRenderer draws into the frame
 *   <li>MOD-354    — {@link #checkWaterMillStatusRow} — the water mill's GUI status row draws, and draws a different label per state
 *   <li>MOD-275    — {@link #assertBlueprintPreviewInFrame} — the assembler's read-only blueprint preview reaches the frame
 *   <li>MOD-275    — {@link #assertBlueprintIconShowsProduct} — a recorded blueprint's own icon carries its product
 *   <li>MOD-287    — {@link #checkStorageModuleSeams}  — storage-module connected textures (frames for review)
 * </ul>
 *
 * <p>Screenshots land in {@code build/run/clientGameTest/screenshots/}.
 * Filenames are prefixed with a sequential index so they sort in test order.
 *
 * <p><b>A screenshot on its own asserts nothing.</b> {@link #takeCleanScreenshot} only proves a
 * non-empty PNG was written — a frame missing a whole renderer still passes. Any stand that claims to
 * cover rendering must additionally assert the thing it photographs, the way
 * {@link #checkWaterMillWheel} and {@link #checkWindMillRotor} do; otherwise name it for what it is,
 * a screenshot for a human to look at. The same applies to a GUI: {@link #checkWaterMillStatusRow}
 * shows how to assert a text row — compare it against the state where it is deliberately blank, and
 * against the other states it is supposed to distinguish.
 */
@SuppressWarnings("UnstableApiUsage")
public class GuiClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Pass {@code -Pguionly} to Gradle to skip world-building tests and only shoot GUI screens. */
    private static final boolean GUI_ONLY = System.getProperty("alaindustrial.guionly") != null;

    /** Position of the water mill in {@link #checkWaterMillWheel}'s rig. */
    private static final BlockPos WMILL_POS = new BlockPos(120, 102, 121);

    @Override
    public void runTest(ClientGameTestContext context) {
        // MOD-362 — consistent world settings: the same seed and generator on every machine, so a frame
        // can eventually be compared against something other than itself.
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            configureVisualTestClient(context, singleplayer);
            ShotRecorder.begin(context);
            singleplayer.getClientLevel().waitForChunksRender();

            if (!GUI_ONLY) {
                // ── World render rig ─────────────────────────────────────────────────
                renderBlocksInWorld(context, singleplayer);

                // ── Visual checks (RULES.md) ─────────────────────────────────────────
                checkSixFaceSurvey(context, singleplayer);      // R-VIS-04
                checkActiveIdleTextures(context, singleplayer); // R-VIS-01
                checkCableConnectivity(context, singleplayer);  // R-CON-03
                checkEnergyPackWorn(context, singleplayer);     // MOD-065 worn model
                checkWaterMillWheel(context, singleplayer);     // MOD-024 BER visual regression
                checkWindMillRotor(context, singleplayer);      // MOD-232 BER visual regression
                checkIncubatorDome(context, singleplayer);      // MOD-118 BER visual regression
                checkEnergyCondenserOrb(context, singleplayer); // MOD-393 BER visual regression
                checkStorageModuleSeams(context, singleplayer); // MOD-287 connected textures
                // R-PHY-10: mc.debugHitboxes removed in MC 26.2; re-enable when API is found.
            }

            // ── Advancement-tab icon regression (needs the in-world player + server) ───
            shootAdvancementScreens(context, singleplayer);

            // ── GUI screenshots (always runs) ─────────────────────────────────────────
            shootGuiScreenshots(context);

            // Leave the world the way a player leaves it: with no container open. The last frame of this
            // lane is a GUI, and closing the world while the server still believes a container is open
            // hung the client on the handover to the next client-gametest class (observed in the MOD-362
            // shakedown: the log stopped dead right after this lane's last frame).
            VisualWorld.awaitNoScreen(context);

            // Writes shots-manifest.json beside the frames: what each one is, which rule it serves and
            // which build produced it. Without it an archived run is 148 anonymous PNGs.
            ShotRecorder.finish();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // R-GUI-04 — Advancement-tab icons render correctly
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * R-GUI-04: Opens the real {@link AdvancementsScreen} and photographs our advancement tab, a
     * vanilla tab, and the return to our tab. The tab icons (raw_tin on ore_hunter, solar_panel
     * on root/solar_power, copper_cable on first_wire/energized_network, battery_box on
     * first_storage, electronic_circuit on first_circuit) are rendered by {@code AdvancementWidget} in
     * {@code ItemDisplayContext.GUI} —
     * the exact path the {@code minecraft:select} gui-icon fix targets. A white square or an
     * off-centre icon here is the regression this suite exists to catch.
     *
     * <p>Also shoots the hotbar with all seven fixed block-items ({@code solar_panel},
     * {@code daylight/moonlit_solar_panel}, and the four cables) so every fixed icon is verified in
     * one frame, not just the three that happen to be advancement icons.
     *
     * <p>The whole tree is granted first so icons render at full brightness (ungranted icons still
     * render, only dimmed).
     */
    private static void shootAdvancementScreens(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Grant our root (ore_hunter) + all descendants so every tab icon renders bright.
        server.runCommand("advancement grant @p from alaindustrial:ore_hunter");
        context.waitTicks(5);

        // ── Our tab ──────────────────────────────────────────────────────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            mc.setScreenAndShow(new AdvancementsScreen(adv));
            AdvancementHolder root = adv.get(Industrialization.id("ore_hunter"));
            if (root != null) {
                adv.setSelectedTab(root, false);
                LOG.info("[GUITEST][R-GUI-04] selected tab {}", root.id());
            } else {
                LOG.warn("[GUITEST][R-GUI-04] alaindustrial:ore_hunter (root) advancement missing on client!");
            }
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_alaindustrial_root -> {}",
                takeCleanScreenshot(context, "adv_alaindustrial_root").toAbsolutePath());

        // ── Transition: switch to a vanilla tab (proves tab navigation) ────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            AdvancementHolder story = adv.get(Identifier.fromNamespaceAndPath("minecraft", "story/root"));
            if (story != null) adv.setSelectedTab(story, false);
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_vanilla_story -> {}",
                takeCleanScreenshot(context, "adv_vanilla_story").toAbsolutePath());

        // ── Back to our tab (icons must survive re-select) ─────────────────────────────
        context.runOnClient(mc -> {
            var adv = mc.getConnection().getAdvancements();
            AdvancementHolder root = adv.get(Industrialization.id("ore_hunter"));
            if (root != null) adv.setSelectedTab(root, false);
        });
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] adv_alaindustrial_return -> {}",
                takeCleanScreenshot(context, "adv_alaindustrial_return").toAbsolutePath());

        // Close the advancements screen.
        context.runOnClient(mc -> mc.setScreenAndShow(null));
        context.waitTicks(3);

        // ── All 7 fixed block-item icons in the hotbar (same gui render context) ───────
        server.runCommand("fill 6 99 17 12 99 23 minecraft:smooth_stone");
        server.runCommand("gamemode creative @p");
        server.runCommand("clear @p");
        server.runCommand("tp @p 9 100 20 180 0");
        String[] fixedItems = {
            "solar_panel", "daylight_solar_panel", "moonlit_solar_panel",
            "copper_cable", "tin_cable", "insulated_copper_cable", "insulated_tin_cable"
        };
        for (String it : fixedItems) server.runCommand("give @p alaindustrial:" + it);
        context.waitTicks(5);
        LOG.info("[GUITEST][R-GUI-04] hud_fixed_item_icons -> {}",
                takeCleanScreenshot(context, "hud_fixed_item_icons").toAbsolutePath());
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // R-VIS-04 — All 6 faces of key blocks
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * R-VIS-04: Photographs every visible face of the LV Generator (directional, full-cube) and
     * the Solar Panel (non-directional, thin top-slab). Confirms: textures are on the right sides,
     * no face is black/missing, front ≠ back ≠ side textures where they should differ.
     *
     * <p>Platform centred at (60, 99, 60) — isolated from other rigs.
     *
     * @implements R-VIS-04 - all six block faces are visible and distinguishable
     * @covers R-VIS-04
     */
    private static void checkSixFaceSurvey(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Clear previous rigs (renderBlocksInWorld row + panel-neighbour rig) so they don't
        // appear in the background of six-face screenshots.
        server.runCommand("fill -5 99 -5 22 102 10 minecraft:air");
        server.runCommand("fill 38 99 38 48 102 48 minecraft:air");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        // Clean platform for this rig
        server.runCommand("fill 56 99 56 64 99 64 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");

        // ── Generator (facing=south → front face points toward +Z) ──────────────────
        server.runCommand("setblock 60 100 60 alaindustrial:generator[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);

        // tp format: "x y z yaw pitch"
        // Y=100 (eye≈101.6). 1 blk from each face → block fills ~70% frame width.
        // pitch=37° centres the block vertically (arctan(1.12 / 1.5) where 1.5 = dist to centre)
        String[][] genViews = {
            {"60 100 62 180 37", "gen_face_front"},  // 1 blk S of south face (Z=61)
            {"60 100 59 0 37",   "gen_face_back"},   // 1 blk N of north face (Z=60)
            {"62 100 60 90 37",  "gen_face_east"},   // 1 blk E of east  face (X=61)
            {"59 100 60 -90 37", "gen_face_west"},   // 1 blk W of west  face (X=60)
            {"60 101 60 0 90",   "gen_face_top"},    // eye≈102.6, 1.6 blk above top → 44% fill
            {"62 101 62 135 45", "gen_face_iso"},    // close SE iso, steep pitch centres block
        };
        for (String[] v : genViews) {
            server.runCommand("tp @p " + v[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][R-VIS-04] {} -> {}", v[1], takeCleanScreenshot(context, v[1]).toAbsolutePath());
        }

        // ── Solar Panel (thin top-slab model) ────────────────────────────────────────
        server.runCommand("setblock 60 100 60 alaindustrial:solar_panel");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);

        String[][] solarViews = {
            // top face ≈ Y=100.19; feet Y=100 → eye≈101.6, dist≈1.4 → ~50% frame fill
            {"60 100 60 0 90",   "solar_face_top"},
            // side: 2 blk from south face; pitch=37 centres thin slab (arctan(1.53/2.0))
            {"60 100 63 180 37", "solar_face_side"},
            {"62 101 62 135 40", "solar_face_iso"},
        };
        for (String[] v : solarViews) {
            server.runCommand("tp @p " + v[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][R-VIS-04] {} -> {}", v[1], takeCleanScreenshot(context, v[1]).toAbsolutePath());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // R-VIS-01 — Idle vs active texture
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * R-VIS-01: Captures each machine twice — once with empty slots (idle) and once with fuel/input
     * inserted (active). A visual diff of the two screenshots confirms the active-state texture or
     * animation turns on.
     *
     * <p>Item injection uses {@code /item replace block … container.N}, which is agnostic to the
     * block entity's internal NBT layout.
     * Energy injection uses {@code /data merge block … {energy:…L}} (Team Reborn convention).
     * Platform centred at (80, 99, 80).
     *
     * @implements R-VIS-01 - the texture switches between the working and idle states
     * @covers R-VIS-01
     */
    private static void checkActiveIdleTextures(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Clear six-face survey rig so it doesn't bleed into background.
        server.runCommand("fill 54 99 54 66 102 66 minecraft:air");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        server.runCommand("fill 76 99 76 84 99 84 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        // ── LV Generator ─────────────────────────────────────────────────────────────
        server.runCommand("setblock 80 100 80 alaindustrial:generator[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        // Camera: 1 blk from south face (Z=81), pitch=37 centres block vertically.
        server.runCommand("tp @p 80 100 82 180 37");
        context.waitTicks(5);
        LOG.info("[GUITEST][R-VIS-01] gen_idle -> {}", takeCleanScreenshot(context, "vis_gen_idle").toAbsolutePath());

        // Insert 64 coal into slot 0 (fuel slot)
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:coal 64");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] gen_active -> {}", takeCleanScreenshot(context, "vis_gen_active").toAbsolutePath());

        // ── Macerator (needs energy + raw ore) ───────────────────────────────────────
        server.runCommand("setblock 80 100 80 alaindustrial:macerator[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][R-VIS-01] mac_idle -> {}", takeCleanScreenshot(context, "vis_mac_idle").toAbsolutePath());

        // Inject energy (Team Reborn Energy stores as "energy" Long in NBT) + ore input
        server.runCommand("data merge block 80 100 80 {energy:4000L}");
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:raw_iron 8");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] mac_active -> {}", takeCleanScreenshot(context, "vis_mac_active").toAbsolutePath());

        // ── Electric Furnace ──────────────────────────────────────────────────────────
        server.runCommand("setblock 80 100 80 alaindustrial:electric_furnace[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][R-VIS-01] furnace_idle -> {}", takeCleanScreenshot(context, "vis_furnace_idle").toAbsolutePath());

        server.runCommand("data merge block 80 100 80 {energy:4000L}");
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:raw_iron 4");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] furnace_active -> {}", takeCleanScreenshot(context, "vis_furnace_active").toAbsolutePath());
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // R-CON-03 — Cable model updates on neighbour changes
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * R-CON-03: Places a single copper cable, then adds and removes adjacent cables one by one.
     * Each state gets a screenshot. Confirms: the multipart arm appears when a neighbour is added
     * and disappears when it is removed — no stale/frozen arms.
     *
     * <p>States captured:
     * <ol>
     *   <li>Alone — centre blob, no arms
     *   <li>East arm added   (+X cable)
     *   <li>South arm added  (+Z cable) — L-shape corner
     *   <li>East arm removed — south arm only
     *   <li>Vertical arm added (above) — T-shape with vertical
     * </ol>
     *
     * Platform centred at (100, 99, 100).
     *
     * @implements R-VIS-12 - the cable's multipart model assembles from its neighbours
     * @covers R-VIS-12, R-CON-03
     */
    private static void checkCableConnectivity(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Clear active-idle rig before shooting cable connectivity.
        server.runCommand("fill 74 99 74 86 102 86 minecraft:air");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        server.runCommand("fill 96 99 96 104 99 104 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");

        // Centre cable at (100, 100, 100). Camera: SE iso, 1 blk diagonal, pitch=30 to centre.
        server.runCommand("setblock 100 100 100 alaindustrial:copper_cable");
        singleplayer.getClientLevel().waitForChunksRender();
        server.runCommand("tp @p 101 100 101 135 30");
        context.waitTicks(5);
        LOG.info("[GUITEST][R-CON-03] cable_alone -> {}", takeCleanScreenshot(context, "con_cable_alone").toAbsolutePath());

        // Add east neighbour (+X)
        server.runCommand("setblock 101 100 100 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_east_arm -> {}", takeCleanScreenshot(context, "con_cable_east_arm").toAbsolutePath());

        // Add south neighbour (+Z) → L-corner
        server.runCommand("setblock 100 100 101 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_corner -> {}", takeCleanScreenshot(context, "con_cable_corner").toAbsolutePath());

        // Remove east → only south arm remains
        server.runCommand("setblock 101 100 100 minecraft:air");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_south_only -> {}", takeCleanScreenshot(context, "con_cable_south_only").toAbsolutePath());

        // Add vertical arm (above)
        server.runCommand("setblock 100 101 100 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_vertical -> {}", takeCleanScreenshot(context, "con_cable_vertical").toAbsolutePath());

        // Cleanup — remove helpers so later rigs don't see stray cables
        server.runCommand("setblock 100 100 101 minecraft:air");
        server.runCommand("setblock 100 101 100 minecraft:air");
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Water mill — BER wheel visual (silhouette, bucket depth, lighting)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Photographs the MOD-024 wheel from three gameplay angles plus a night-lighting view, and then
     * proves the wheel geometry actually reaches the captured frame.
     *
     * <p>Two gates, because the screenshots alone assert nothing about the renderer:
     * <ul>
     *   <li><b>Renderer gate</b> — the CLIENT block entity must be in the state
     *       {@code WaterMillWheelBlockEntityRenderer.submit} draws in: wheel installed, mode
     *       {@code MODE_OK}, production &gt; 0. The renderer returns early on
     *       {@code MODE_INTERFERENCE}/{@code MODE_OBSTRUCTED}, so a rig that walls the wheel in
     *       yields a perfectly valid, perfectly wheel-less screenshot.
     *   <li><b>Pixel gate</b> — the frame with the wheel installed must differ from the frame with it
     *       removed by far more than two consecutive wheel-less frames differ from each other (the
     *       flowing-water animation baseline). That comparison, and only that, proves BER output
     *       lands in {@code takeScreenshot}'s framebuffer — with no committed baseline PNG to go
     *       stale across drivers.
     * </ul>
     */
    private static void checkWaterMillWheel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        server.runCommand("fill 96 99 96 104 103 104 minecraft:air");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        server.runCommand("fill 114 99 114 128 99 128 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");
        server.runCommand("fill 117 100 120 124 100 125 minecraft:smooth_stone");
        server.runCommand("fill 118 100 121 123 100 124 minecraft:water");

        server.runCommand("setblock 120 100 118 minecraft:smooth_stone");
        server.runCommand("setblock 120 101 118 minecraft:smooth_stone");
        server.runCommand("setblock 120 101 119 minecraft:smooth_stone");
        server.runCommand("setblock 120 102 119 minecraft:smooth_stone");
        server.runCommand("setblock 120 102 121 alaindustrial:water_mill[facing=south]");
        server.runCommand("item replace block 120 102 121 container.0 with alaindustrial:water_mill_wheel");
        server.runCommand("setblock 120 101 121 minecraft:smooth_stone");
        server.runCommand("setblock 120 100 121 minecraft:smooth_stone");

        // Water inlet on the mill's west face. Two rules constrain this little channel, and the
        // original layout broke both:
        //   1. WaterMillClearance treats the wheel's front cell (120,102,122), the cell above it and
        //      its two side neighbours (119,102,122) / (121,102,122) as the swept area. A block in any
        //      of them makes the mill report MODE_OBSTRUCTED and the renderer draws NO wheel at all —
        //      which is exactly what a smooth_stone at (119,102,122) used to do here.
        //   2. waterSides() counts only FLOWING water (MOD-188), so a source block placed straight
        //      against the mill produces nothing. The source therefore sits one cell further west and
        //      (119,102,121) is left empty for it to flow into.
        // The overflow spills south through (119,102,122) into the basin — water is replaceable, so
        // the spillway keeps the clearance cell legal.
        server.runCommand("setblock 118 101 121 minecraft:smooth_stone");
        server.runCommand("setblock 119 101 121 minecraft:smooth_stone");
        server.runCommand("setblock 117 102 121 minecraft:smooth_stone");
        server.runCommand("setblock 118 102 120 minecraft:smooth_stone");
        server.runCommand("setblock 118 102 122 minecraft:smooth_stone");
        server.runCommand("setblock 119 102 120 minecraft:smooth_stone");
        server.runCommand("setblock 118 102 121 minecraft:water");

        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(20);

        String[][] millViews = {
            {"120.5 101 128.5 180 8", "wmill_front"},
            {"124.5 102 126.5 135 18", "wmill_iso"},
            {"126.0 101.5 122.5 90 8", "wmill_side"},
        };
        for (String[] view : millViews) {
            server.runCommand("tp @p " + view[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][WMILL] {} -> {}",
                    view[1], takeCleanScreenshot(context, view[1]).toAbsolutePath());
        }

        assertWheelPixelsInFrame(context, singleplayer, server);

        server.runCommand("time set midnight");
        context.waitTicks(10);
        server.runCommand("tp @p 120.5 101 128.5 180 8");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][WMILL] wmill_night -> {}",
                takeCleanScreenshot(context, "wmill_night").toAbsolutePath());
        server.runCommand("time set day");
        context.waitTicks(5);
    }

    /**
     * Renderer gate: the client-side mill must be in the exact state the wheel renderer draws in.
     * Reads the same three inputs {@code WaterMillWheelBlockEntityRenderer.extractRenderState} reads —
     * the wheel item in {@code WHEEL_SLOT}, the mode channel ({@code dataAccess} slot 3) and the
     * production channel (slot 2) — so a rig that silently stalls or hides the wheel fails here
     * instead of producing a green, wheel-less screenshot.
     */
    private static void assertWheelRendererGate(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            BlockEntity be = mc.level.getBlockEntity(WMILL_POS);
            if (!(be instanceof WaterMillBlockEntity mill)) {
                throw new AssertionError("[GUITEST][WMILL] client has no WaterMillBlockEntity at "
                        + WMILL_POS + " (got " + be + ", block state "
                        + mc.level.getBlockState(WMILL_POS) + ") — the rig is broken, or the camera "
                        + "is still too far away for the client to have loaded that chunk");
            }
            if (mc.getBlockEntityRenderDispatcher().getRenderer(mill) == null) {
                throw new AssertionError("[GUITEST][WMILL] no BlockEntityRenderer registered for the water mill");
            }
            boolean installed = !mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).isEmpty();
            int production = mill.getDataAccess().get(2);
            int mode = mill.getDataAccess().get(3);
            LOG.info("[GUITEST][WMILL] renderer gate: installed={} production={} mode={}",
                    installed, production, mode);
            if (!installed || mode != WaterMillBlockEntity.MODE_OK || production <= 0) {
                throw new AssertionError("[GUITEST][WMILL] the wheel renderer would draw NOTHING: "
                        + "installed=" + installed + " production=" + production + " mode=" + mode
                        + " (expected installed=true production>0 mode="
                        + WaterMillBlockEntity.MODE_OK + "). Modes: 0=OK 1=INTERFERENCE 2=OBSTRUCTED "
                        + "3=NO_WATER. An OBSTRUCTED mill means a rig block sits in the "
                        + "wheel's clearance cells (front cell of FACING, its top and its two side "
                        + "neighbours — see WaterMillClearance).");
            }
        });
    }

    /**
     * Pixel gate: shoot the same camera with the wheel installed, then twice with it removed. The
     * wheel-vs-no-wheel delta must dwarf the no-wheel-vs-no-wheel delta (which measures only the
     * flowing-water texture animation), otherwise the BER contributed nothing to the captured frame.
     */
    private static void assertWheelPixelsInFrame(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, TestServerContext server) {
        server.runCommand("tp @p 120.5 101 128.5 180 8");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        // Only now is the mill's chunk inside the client's view distance — while the player stands at
        // the previous rig 160 blocks away the client has no block entity there at all.
        assertWheelRendererGate(context);
        Path withWheel = takeCleanScreenshot(context, "wmill_ber_wheel");

        server.runCommand("item replace block 120 102 121 container.0 with minecraft:air");
        context.waitTicks(5);
        Path noWheelA = takeCleanScreenshot(context, "wmill_ber_nowheel_a");
        context.waitTicks(5);
        Path noWheelB = takeCleanScreenshot(context, "wmill_ber_nowheel_b");

        server.runCommand("item replace block 120 102 121 container.0 with alaindustrial:water_mill_wheel");
        context.waitTicks(5);

        int wheelDelta = differingPixels(withWheel, noWheelA);
        int animationNoise = differingPixels(noWheelA, noWheelB);
        // 4x the animation baseline, and at least 2000 px: the wheel covers tens of thousands of
        // pixels at this camera distance, so the margin is wide enough that driver dithering or a
        // stray water frame cannot flip the verdict either way.
        int required = Math.max(4 * animationNoise, 2000);
        LOG.info("[GUITEST][WMILL] pixel gate: wheel delta={} px, animation baseline={} px, required>{}",
                wheelDelta, animationNoise, required);
        if (wheelDelta < required) {
            throw new AssertionError("[GUITEST][WMILL] removing the wheel changed only " + wheelDelta
                    + " px (animation baseline " + animationNoise + " px, required > " + required
                    + ") — the BlockEntityRenderer's geometry is NOT in the captured frame. "
                    + explainWithDiff(withWheel, noWheelA));
        }
    }

    /**
     * Writes a diff picture for a failing pixel gate and returns the sentence that points at it.
     *
     * <p>A failing gate used to say "compare A.png with B.png" and leave the reviewer to spot the
     * difference between two 1280x720 screenshots by eye. The diff marks the changed pixels in red on a
     * dimmed background, so the answer is visible at a glance (MOD-362).
     */
    private static String explainWithDiff(Path expected, Path actual) {
        try {
            PixelGrid before = PngIo.read(expected);
            PixelGrid after = PngIo.read(actual);
            Path diff = PngIo.writeDiffBeside(actual, DiffImage.render(before, after, Tolerance.CHANNEL_24));
            if (diff != null) {
                return "Compare " + expected.getFileName() + " with " + actual.getFileName()
                        + "; the marked-up difference is in " + diff.getFileName() + ".";
            }
        } catch (RuntimeException e) {
            // The diff is triage material. If it cannot be produced, the real assertion must still be
            // the thing that gets reported — never mask a gate failure with an I/O failure.
            LOG.warn("[GUITEST] could not render the diff image for {} vs {}", expected, actual, e);
        }
        return "Compare " + expected.getFileName() + " with " + actual.getFileName() + ".";
    }

    /**
     * Pixels whose colour differs by more than 24/255 in any channel. Shared by every pixel gate.
     *
     * <p>Registers the comparison against BOTH frames (MOD-362 round 3). Doing it here rather than at
     * the ~25 call sites is what keeps the manifest honest by construction: a pixel gate added later
     * is recorded without anybody remembering to record it, and the alternative — a marker call per
     * site — is a line that gets copied without its neighbour and quietly understates the suite.
     */
    private static int differingPixels(Path first, Path second) {
        ShotRecorder.markComparedFiles(first, second);
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST] could not decode " + first + " / " + second);
            }
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                throw new AssertionError("[GUITEST] screenshot sizes differ: "
                        + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight());
            }
            int differing = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    if (differsAt(a, b, x, y)) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST] could not read screenshots for the pixel gate", e);
        }
    }

    /**
     * The single "these two pixels differ" rule every pixel gate in this suite shares.
     *
     * <p>Delegates to {@link Tolerance#CHANNEL_24} instead of carrying its own copy of the literal 24
     * (MOD-362). The threshold used to be written out here AND in {@link #differsFrom}, so "the shared
     * rule" was in fact two independent numbers that happened to agree — changing the strictness of the
     * suite meant finding every copy. Now there is one, and it is unit-tested on both sides of its
     * boundary in {@code common/src/test/java/dev/alaindustrial/visual/ToleranceTest.java}.
     */
    private static boolean differsAt(BufferedImage a, BufferedImage b, int x, int y) {
        return Tolerance.CHANNEL_24.differs(a.getRGB(x, y), b.getRGB(x, y));
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Wind mill — BER rotor visual (blades present and turning on all three family mills)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * The three mills {@link WindMillRotorBlockEntityRenderer} is bound to, west to east — one row
     * of {@code block id | x | close-camera tp arguments | screenshot prefix}.
     *
     * <p>They stand 4 blocks apart on purpose. {@code WindMillInterference} stalls <b>both</b> mills
     * whose 2×2 rotor discs overlap, and the renderer hides the blades of an interfered mill, so a
     * tighter row would photograph three bare poles and the pixel gate would have nothing to measure.
     */
    private static final String[][] WIND_MILLS = {
        {"wind_mill",               "146", "146.5 101 155.5 180 2", "windmill_t1"},
        {"high_altitude_wind_mill", "150", "150.5 101 155.5 180 2", "windmill_t2_high_altitude"},
        {"storm_wind_mill",         "154", "154.5 101 155.5 180 2", "windmill_t2_storm"},
    };

    /** Y and Z shared by every mill in the rig (the X of each is in {@link #WIND_MILLS}). */
    private static final int WIND_MILL_Y = 102;
    private static final int WIND_MILL_Z = 150;


    /** Where the condenser stands for {@link #checkEnergyCondenserOrb}. */
    private static final int ORB_X = 170;
    private static final int ORB_Y = 101;
    private static final int ORB_Z = 150;

    /**
     * MOD-393: proves the condenser's orb actually reaches the captured frame.
     *
     * <p>The toggle is the bank itself, not the block. {@code EnergyCondenserBlockEntityRenderer}
     * draws nothing at zero — a deliberate rule, because a glowing orb inside an empty condenser
     * claims the machine is working — so filling and emptying the bank turns the renderer on and off
     * while leaving the block, its frame model and every pixel around it untouched. Photographing the
     * block against bare ground instead would compare the whole machine and pass even if the orb never
     * drew a single triangle, which is the failure this exists to catch (MOD-231).
     *
     * <p>That makes one gate cover two things: the orb renders, AND it obeys the empty-bank rule.
     */
    private static void checkEnergyCondenserOrb(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // Rain and night both put moving or dimming pixels into the baseline pair; the noise floor has
        // to be driver dithering alone or the gate loses the ability to fail (MOD-232).
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill " + (ORB_X - 3) + " " + (ORB_Y - 1) + " " + (ORB_Z - 3) + " "
                + (ORB_X + 3) + " " + (ORB_Y - 1) + " " + (ORB_Z + 3) + " minecraft:smooth_stone");
        server.runCommand("fill " + (ORB_X - 3) + " " + ORB_Y + " " + (ORB_Z - 3) + " "
                + (ORB_X + 3) + " " + (ORB_Y + 3) + " " + (ORB_Z + 3) + " minecraft:air");
        server.runCommand("setblock " + ORB_X + " " + ORB_Y + " " + ORB_Z
                + " alaindustrial:energy_condenser");
        // Banked past tier I, so the orb is at a healthy size and brightness rather than its dimmest.
        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 700000L}");

        // Close in: the orb is a 0.6-block ball inside the frame, an order of magnitude smaller than
        // the wind mill's rotor quad, so the camera has to be near enough for it to own real pixels.
        server.runCommand("tp @p " + (ORB_X + 0.5) + " " + (ORB_Y + 0.3) + " " + (ORB_Z + 2.2) + " 180 5");
        server.runCommand("gamemode spectator @p");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);

        Path withOrb = takeCleanScreenshot(context, "condenser_orb");
        LOG.info("[GUITEST][CONDENSER] condenser_orb -> {}", withOrb.toAbsolutePath());

        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 0L}");
        context.waitTicks(10);
        Path emptyA = takeCleanScreenshot(context, "condenser_orb_empty_a");
        context.waitTicks(5);
        Path emptyB = takeCleanScreenshot(context, "condenser_orb_empty_b");

        int orbDelta = differingPixels(withOrb, emptyA);
        int staticNoise = differingPixels(emptyA, emptyB);
        // 4x the measured floor, and at least 400 px. Lower than the rotor's 2000 on purpose: the orb
        // is a much smaller object, and a threshold it cannot clear on a healthy build is a gate that
        // gets deleted rather than fixed.
        int required = Math.max(4 * staticNoise, 400);
        LOG.info("[GUITEST][CONDENSER] orb pixel gate: delta={} px, static baseline={} px, required>{}",
                orbDelta, staticNoise, required);
        if (orbDelta < required) {
            throw new AssertionError("[GUITEST][CONDENSER] emptying the bank changed only " + orbDelta
                    + " px (static baseline " + staticNoise + " px, required > " + required + ") — either "
                    + "EnergyCondenserBlockEntityRenderer's geometry is not in the captured frame, or the "
                    + "orb no longer hides on an empty bank. " + explainWithDiff(withOrb, emptyA));
        }
    }

    /**
     * MOD-232: photographs the rotor of all three wind mills and proves the rotor geometry actually
     * reaches the captured frame — the coverage {@code WindMillRotorBlockEntityRenderer} had none of.
     *
     * <p>Same two gates as {@link #checkWaterMillWheel}, because a screenshot alone asserts nothing:
     * <ul>
     *   <li><b>Renderer gate</b> — per mill, the CLIENT block entity must be in the state
     *       {@code WindMillRotorBlockEntityRenderer.submit} draws in. The renderer returns on
     *       {@code !state.visible}, and {@code visible = !interfered && hasRotor}, so a rig that
     *       forgets the rotor or lets two discs overlap yields a perfectly valid, perfectly
     *       rotor-less screenshot. Production is gated too: at {@code production <= 0} the blade
     *       angle is pinned to 0 and the "spinning rotor" this stand claims to shoot is a still one.
     *   <li><b>Pixel gate</b> — per mill, the frame with its rotor installed must differ from the
     *       frame without it by far more than two consecutive rotor-less frames differ from each
     *       other. No committed baseline PNG to go stale across drivers.
     * </ul>
     */
    private static void checkWindMillRotor(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Clear the water mill rig — in two passes, and the second one is not optional. Deleting that
        // rig's platform releases its basin, which becomes a water column falling through the void at
        // roughly one block per tick, and flowing water is an ANIMATED texture: it sits on the western
        // horizon of this rig's frames and puts noise into the pixel gate's supposedly static baseline
        // until it has drained out of shot (measured: 1 263 px of noise in the first mill's baseline,
        // 0 px by the third). Five ticks is enough for the whole column to leave y > 95 and not nearly
        // enough for its head to reach the bottom of the world, so one fill catches all of it. The
        // first box is far wider than the water mill's footprint because its source spills several
        // cells past the basin before falling.
        server.runCommand("fill 108 96 108 139 108 139 minecraft:air");
        context.waitTicks(5);
        server.runCommand("fill 116 -64 116 126 95 126 minecraft:air");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(3);

        // Clear weather is not cosmetic: rain flips the mills to MODE_GALE/MODE_STORM and draws a
        // moving particle layer over every frame, which would inflate the pixel gate's noise baseline.
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill 140 99 140 160 99 160 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");

        // Rig rules, every one of them load-bearing:
        //   * the support pillars sit UNDER the mill (z=150), never in front of it. WindMillClearance
        //     treats the front cell along FACING (z=151), its two side neighbours and the three-cell
        //     pit beneath them as the blade sweep; one non-replaceable block there makes the mill
        //     report MODE_OBSTRUCTED, which zeroes production and freezes the blades — the exact trap
        //     the water mill rig fell into (MOD-231).
        //   * nothing above y=102 in the mills' columns: openSky must classify CLEAR, or the mill is
        //     MODE_ROOFED and dead regardless of height.
        //   * the platform is at y=99, two blocks below the lowest swept cell (y=101).
        for (String[] mill : WIND_MILLS) {
            String x = mill[1];
            server.runCommand("setblock " + x + " 100 " + WIND_MILL_Z + " minecraft:smooth_stone");
            server.runCommand("setblock " + x + " 101 " + WIND_MILL_Z + " minecraft:smooth_stone");
            server.runCommand("setblock " + x + " " + WIND_MILL_Y + " " + WIND_MILL_Z
                    + " alaindustrial:" + mill[0] + "[facing=south]");
            setRotor(server, x, true);
        }

        // Family portrait: all three rotors in one frame for the human reviewer.
        server.runCommand("tp @p 150.5 101 161.5 180 2");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(20);
        LOG.info("[GUITEST][WINDMILL] windmill_family -> {}",
                takeCleanScreenshot(context, "windmill_family").toAbsolutePath());

        // Strip every rotor before the per-mill gates. Each mill is then measured alone: the two
        // "no rotor" baseline frames show a completely static rig, so the noise floor they establish
        // is driver dithering only — a neighbour's spinning blades would otherwise land in the same
        // frame and inflate the baseline until the gate could no longer fail.
        for (String[] mill : WIND_MILLS) {
            setRotor(server, mill[1], false);
        }
        context.waitTicks(10);
        for (String[] mill : WIND_MILLS) {
            checkOneWindMillRotor(context, singleplayer, server, mill);
        }

        // Night frame: the rotor quad is submitted at LightCoordsUtil.FULL_BRIGHT, so the blades must
        // stay fully lit after dark. This is the one frame that would catch that decision regressing.
        setRotor(server, WIND_MILLS[0][1], true);
        server.runCommand("time set midnight");
        server.runCommand("tp @p " + WIND_MILLS[0][2]);
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST][WINDMILL] windmill_night -> {}",
                takeCleanScreenshot(context, "windmill_night").toAbsolutePath());
        server.runCommand("time set day");
        context.waitTicks(5);
    }

    /**
     * MOD-118 visual regression for the incubator multiblock: the two-tier silhouette, the dome that
     * replaced the player's glass, and the lit/unlit faces of the base. Powered for real by a fuel
     * generator pushing into its side, and shot by day and at midnight.
     *
     * <p>The floating item is proved to be in the frame the same way the water mill proves its wheel:
     * shoot the dome with an item in the input slot, then twice without one, and require the
     * with-vs-without delta to dwarf the without-vs-without baseline. An earlier version of this
     * comment claimed the harness could not capture renderer output at all — that was wrong, and
     * MOD-231's gate is what disproved it.
     *
     * <p>The tint <i>is</i> captured, because it belongs to the block model rather than to the
     * renderer. Three domes stand in a row for it — lime, red and plain glass — so a single frame
     * separates "the tint is wired" from "the dome happens to be green".
     */
    private static void checkIncubatorDome(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        server.runCommand("fill 138 99 138 148 104 148 minecraft:air");
        server.runCommand("fill 138 99 138 148 99 148 minecraft:smooth_stone");
        server.runCommand("gamemode spectator @p");

        // The base, its dome (lime glass, so the tint is visible) and a generator feeding it.
        server.runCommand("setblock 143 100 143 alaindustrial:incubator[facing=south]");
        server.runCommand("setblock 143 101 143 minecraft:lime_stained_glass");
        server.runCommand("setblock 142 100 143 alaindustrial:generator[facing=east]");
        server.runCommand("item replace block 142 100 143 container.0 with minecraft:coal 64");

        // Two more domes east of it, unpowered — they exist purely so one frame carries the whole
        // claim: two different dyes tint differently, and plain glass stays colourless. Without the
        // third the frame proves "the dome is green", not "the dome takes the glass's colour".
        server.runCommand("setblock 145 100 143 alaindustrial:incubator[facing=south]");
        server.runCommand("setblock 145 101 143 minecraft:red_stained_glass");
        server.runCommand("setblock 147 100 143 alaindustrial:incubator[facing=south]");
        server.runCommand("setblock 147 101 143 minecraft:glass");

        // Chip picks the mode, uranium is the charge, lapis is the item that will be floating.
        server.runCommand("item replace block 143 100 143 container.0 with alaindustrial:mutation_chip_transform");
        server.runCommand("item replace block 143 100 143 container.1 with alaindustrial:uranium_ingot 8");
        server.runCommand("item replace block 143 100 143 container.2 with minecraft:lapis_lazuli 16");

        singleplayer.getClientLevel().waitForChunksRender();
        // Long enough for the generator to fill the buffer and the cycle to start (LIT turns on).
        context.waitTicks(120);

        // Logged, not asserted: the renderer draws from the CLIENT copy of the block entity, and a
        // silent desync there (an unsynced inventory, a formed flag that never crossed) is invisible
        // in a screenshot. These lines are what tell a reviewer whether an empty-looking dome means
        // "the renderer is broken" or "the client never got the item".
        context.runOnClient(mc -> {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(143, 100, 143);
            if (mc.level != null
                    && mc.level.getBlockEntity(pos)
                            instanceof dev.alaindustrial.block.entity.IncubatorBlockEntity incubator) {
                LOG.info("[GUITEST][INCU] client state={} formed={} shown={} renderer={}",
                        mc.level.getBlockState(pos), incubator.isFormed(), incubator.displayedStack(),
                        mc.getBlockEntityRenderDispatcher().getRenderer(incubator));
            }
            // The tint the chunk builder will multiply into the dome's glass faces, read back through
            // the same path the renderer uses. A screenshot says "green"; this says which ARGB, and
            // whether the client actually received the glass the dome was built from.
            for (int x : new int[] {143, 145, 147}) {
                net.minecraft.core.BlockPos base = new net.minecraft.core.BlockPos(x, 100, 143);
                if (mc.level != null
                        && mc.level.getBlockEntity(base)
                                instanceof dev.alaindustrial.block.entity.IncubatorBlockEntity incubator) {
                    LOG.info("[GUITEST][INCU] dome x={} source={} tint=#{}", x, incubator.domeSource(),
                            Integer.toHexString(
                                    dev.alaindustrial.client.render.IncubatorDomeTint.colorOf(
                                            incubator.domeSource())));
                }
            }
        });

        // Pulled back and centred on the middle dome of the three: the old single-dome framing put
        // the camera one block from where the red one now stands.
        String[][] views = {
            {"145.5 101.5 150.5 180 3", "incubator_front"},
            {"149.5 103 149.5 135 18", "incubator_iso"},
            {"145.5 105.5 148.5 180 30", "incubator_top"},
        };
        for (String[] view : views) {
            server.runCommand("tp @p " + view[0]);
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitTicks(5);
            LOG.info("[GUITEST][INCU] {} -> {}",
                    view[1], takeCleanScreenshot(context, view[1]).toAbsolutePath());
        }

        assertIncubatorItemInFrame(context, singleplayer, server);

        server.runCommand("time set midnight");
        context.waitTicks(10);
        server.runCommand("tp @p 148.5 102.5 148.5 135 12");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][INCU] incubator_night -> {}",
                takeCleanScreenshot(context, "incubator_night").toAbsolutePath());
        server.runCommand("time set day");
        context.waitTicks(5);
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Storage module — connected textures (MOD-287 visuals)
    // ────────────────────────────────────────────────────────────────────────────────

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
    private static void checkStorageModuleSeams(ClientGameTestContext context,
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
     * same, unchanged scene), the way the mill gates in this file do it, so neither can be flipped by
     * driver dithering.
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

    /** Captured inside {@code runOnClient}: panel top, panel height, and the screen's scaled height. */
    private static int[] storageWindowBox;

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

    /** One mill: install its rotor, gate the renderer's inputs, then gate its pixels. Leaves it bare. */
    private static void checkOneWindMillRotor(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, TestServerContext server, String[] mill) {
        String x = mill[1];
        String prefix = mill[3];

        setRotor(server, x, true);
        server.runCommand("tp @p " + mill[2]);
        singleplayer.getClientLevel().waitForChunksRender();
        // Only now is the mill's chunk inside the client's view distance — a client-side
        // getBlockEntity before the teleport returns null (MOD-231). The wait also covers the mode /
        // production channels: they refresh on the sampling cadence, and re-installing the rotor
        // resets the sample counter so the next tick resamples and syncs.
        context.waitTicks(10);
        assertRotorRendererGate(context, x, mill[0]);

        Path withRotor = takeCleanScreenshot(context, prefix + "_rotor");
        LOG.info("[GUITEST][WINDMILL] {}_rotor -> {}", prefix, withRotor.toAbsolutePath());

        setRotor(server, x, false);
        context.waitTicks(10);
        Path noRotorA = takeCleanScreenshot(context, prefix + "_norotor_a");
        context.waitTicks(5);
        Path noRotorB = takeCleanScreenshot(context, prefix + "_norotor_b");

        int rotorDelta = differingPixels(withRotor, noRotorA);
        int staticNoise = differingPixels(noRotorA, noRotorB);
        // 4x the measured noise floor, and at least 2000 px: the rotor covers a 2x2-block quad at this
        // camera distance, so the margin is wide enough that driver dithering cannot flip the verdict.
        int required = Math.max(4 * staticNoise, 2000);
        LOG.info("[GUITEST][WINDMILL] {} pixel gate: rotor delta={} px, static baseline={} px, required>{}",
                mill[0], rotorDelta, staticNoise, required);
        if (rotorDelta < required) {
            throw new AssertionError("[GUITEST][WINDMILL] removing the rotor from alaindustrial:"
                    + mill[0] + " changed only " + rotorDelta + " px (static baseline " + staticNoise
                    + " px, required > " + required + ") — WindMillRotorBlockEntityRenderer's geometry "
                    + "is NOT in the captured frame. " + explainWithDiff(withRotor, noRotorA));
        }
    }

    /**
     * Renderer gate: the client-side mill must be in the exact state the rotor renderer draws in.
     * Reads the same three inputs {@code WindMillRotorBlockEntityRenderer.extractRenderState} reads —
     * the rotor in {@code ROTOR_SLOT} (slot 0 on all three wind mills), the mode channel
     * ({@code dataAccess} slot 3, which decides {@code visible}) and the production channel (slot 2,
     * which decides the blade angle) — so a rig that hides or freezes the blades fails here instead
     * of producing a green, rotor-less screenshot.
     */
    private static void assertRotorRendererGate(ClientGameTestContext context, String x, String blockId) {
        BlockPos pos = new BlockPos(Integer.parseInt(x), WIND_MILL_Y, WIND_MILL_Z);
        context.runOnClient(mc -> {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (!(be instanceof MachineBlockEntity mill)) {
                throw new AssertionError("[GUITEST][WINDMILL] client has no machine block entity at "
                        + pos + " for alaindustrial:" + blockId + " (got " + be + ", block state "
                        + mc.level.getBlockState(pos) + ") — the rig is broken, or the camera is still "
                        + "too far away for the client to have loaded that chunk");
            }
            Object renderer = mc.getBlockEntityRenderDispatcher().getRenderer(mill);
            if (!(renderer instanceof WindMillRotorBlockEntityRenderer<?>)) {
                throw new AssertionError("[GUITEST][WINDMILL] alaindustrial:" + blockId
                        + " has no WindMillRotorBlockEntityRenderer registered (got " + renderer
                        + ") — the rotor cannot reach any frame at all");
            }
            boolean installed = !mill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty();
            int production = mill.getDataAccess().get(2);
            int mode = mill.getDataAccess().get(3);
            LOG.info("[GUITEST][WINDMILL] {} renderer gate: installed={} production={} mode={}",
                    blockId, installed, production, mode);
            if (!installed || mode == WindMillBlockEntity.MODE_INTERFERENCE || production <= 0) {
                throw new AssertionError("[GUITEST][WINDMILL] the rotor renderer would draw NOTHING (or "
                        + "a frozen rotor) on alaindustrial:" + blockId + ": installed=" + installed
                        + " production=" + production + " mode=" + mode + " (expected installed=true "
                        + "production>0 mode!=" + WindMillBlockEntity.MODE_INTERFERENCE + "). Modes: "
                        + "0=NO_ROTOR 1=ROOFED 2=OBSTRUCTED 3=CALM 4=BREEZE 5=GALE 6=STORM "
                        + "7=INTERFERENCE. OBSTRUCTED means a rig block sits in the blade sweep (the "
                        + "front cell along FACING, its two side neighbours and the three-cell pit "
                        + "below them — see WindMillClearance); ROOFED means the column above the mill "
                        + "is not open sky; INTERFERENCE means a neighbouring rotor disc overlaps this "
                        + "one and the renderer hides both.");
            }
        });
    }

    /**
     * Pixel gate for the incubator's renderer: the floating item either reaches the frame or it does
     * not, and a screenshot alone cannot tell those apart from an empty dome.
     *
     * <p>The unpowered dome is the one used, deliberately. On the powered one, removing the input
     * also stops the machine and swaps its lit textures, so the frames would differ for a reason that
     * has nothing to do with the renderer and the gate would pass while proving nothing.
     */
    private static void assertIncubatorItemInFrame(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, TestServerContext server) {
        final String slot = "item replace block 147 100 143 container.2 with ";
        server.runCommand(slot + "minecraft:lapis_lazuli 16");
        // The teleport sets the feet; the eye sits about 1.6 above them. Standing at 100 puts the
        // camera level with the chamber at 101.5, which is the difference between the item filling a
        // few hundred pixels and being a speck at the bottom edge of the frame.
        server.runCommand("tp @p 147.5 100.0 144.9 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);

        context.runOnClient(mc -> {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(147, 100, 143);
            if (!(mc.level != null && mc.level.getBlockEntity(pos)
                    instanceof dev.alaindustrial.block.entity.IncubatorBlockEntity incubator)) {
                throw new AssertionError("[GUITEST][INCU] client has no IncubatorBlockEntity at " + pos);
            } else if (mc.getBlockEntityRenderDispatcher().getRenderer(incubator) == null) {
                throw new AssertionError("[GUITEST][INCU] no BlockEntityRenderer registered for the incubator");
            } else if (!incubator.isFormed() || incubator.displayedStack().isEmpty()) {
                throw new AssertionError("[GUITEST][INCU] the renderer would draw NOTHING: formed="
                        + incubator.isFormed() + " shown=" + incubator.displayedStack());
            }
        });

        Path withItem = takeCleanScreenshot(context, "incubator_ber_item");
        server.runCommand(slot + "minecraft:air");
        context.waitTicks(5);
        Path emptyA = takeCleanScreenshot(context, "incubator_ber_empty_a");
        context.waitTicks(5);
        Path emptyB = takeCleanScreenshot(context, "incubator_ber_empty_b");
        server.runCommand(slot + "minecraft:lapis_lazuli 16");
        context.waitTicks(5);

        int itemDelta = differingPixels(withItem, emptyA);
        int baseline = differingPixels(emptyA, emptyB);
        // The item is small — a scaled-down lapis a block away — so the margin is set lower than the
        // wheel's: 4x the baseline, floor 300 px rather than 2000.
        int required = Math.max(4 * baseline, 300);
        LOG.info("[GUITEST][INCU] pixel gate: item delta={} px, baseline={} px, required>{}",
                itemDelta, baseline, required);
        if (itemDelta < required) {
            throw new AssertionError("[GUITEST][INCU] removing the input changed only " + itemDelta
                    + " px (baseline " + baseline + " px, required > " + required + ") — the floating "
                    + "item drawn by IncubatorBlockEntityRenderer is NOT in the captured frame. Compare "
                    + withItem.getFileName() + " with " + emptyA.getFileName() + ".");
        }
    }

    /** Install or clear the rotor in slot 0 — {@code ROTOR_SLOT}, shared by all three wind mills. */
    private static void setRotor(TestServerContext server, String x, boolean install) {
        server.runCommand("item replace block " + x + " " + WIND_MILL_Y + " " + WIND_MILL_Z
                + " container.0 with " + (install ? "alaindustrial:windmill_rotor" : "minecraft:air"));
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Existing: world render rig + GUI screenshots
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * MOD-065: photographs the player wearing the Energy Pack, from behind and from the front. The
     * worn model is data-driven (the item's EQUIPPABLE component points at
     * {@code assets/alaindustrial/equipment/energy_pack.json}, whose layers name the humanoid
     * textures) — nothing in code renders it, so a typo in either file shows up as a player with a
     * bare chest and nothing else. These two frames are the only place that would catch it.
     */
    private static void checkEnergyPackWorn(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        // Own little stage: the player must stand on solid ground and be fully settled, otherwise the
        // third-person camera catches them mid-fall and the body never renders into the frame.
        server.runCommand("fill 4 100 3 14 100 13 minecraft:smooth_stone");
        server.runCommand("gamemode survival @p");
        server.runCommand("item replace entity @p armor.chest with alaindustrial:energy_pack");
        server.runCommand("tp @p 9 101 8 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(20);

        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(10);
        LOG.info("[GUITEST] worn pack (back) -> {}",
                takeCleanScreenshot(context, "worn_energy_pack_back").toAbsolutePath());

        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        LOG.info("[GUITEST] worn pack (front) -> {}",
                takeCleanScreenshot(context, "worn_energy_pack_front").toAbsolutePath());

        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("item replace entity @p armor.chest with minecraft:air");
        context.waitTicks(5);
    }

    /**
     * Place the machine + cable blocks on a small platform and screenshot them in-world, so the
     * block models / textures (directional fronts, the thin cable, etc.) can be verified visually —
     * not just the GUIs. Uses server commands to build the rig and pose the camera.
     */
    private static void renderBlocksInWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        server.runCommand("gamerule doWeatherCycle false");
        server.runCommand("weather clear");
        server.runCommand("fill -3 99 -3 19 99 8 minecraft:smooth_stone");

        // Row 1 (z=0): generators + panels + machines, facing south (toward camera).
        server.runCommand("setblock 0 100 0 alaindustrial:solar_panel");
        server.runCommand("setblock 2 100 0 alaindustrial:daylight_solar_panel");
        server.runCommand("setblock 4 100 0 alaindustrial:moonlit_solar_panel");
        server.runCommand("setblock 6 100 0 alaindustrial:generator[facing=south]");
        server.runCommand("setblock 8 100 0 alaindustrial:geothermal_generator[facing=south]");
        server.runCommand("setblock 10 100 0 alaindustrial:battery_box[facing=south]");
        server.runCommand("setblock 12 100 0 alaindustrial:electric_furnace[facing=south]");
        server.runCommand("setblock 14 100 0 alaindustrial:extractor");
        server.runCommand("setblock 16 100 0 alaindustrial:compressor[facing=south]");
        server.runCommand("setblock 18 100 0 alaindustrial:macerator[facing=south]");
        // Row 2 (z=2): all four cable types.
        server.runCommand("setblock 6 100 2 alaindustrial:copper_cable");
        server.runCommand("setblock 8 100 2 alaindustrial:tin_cable");
        server.runCommand("setblock 10 100 2 alaindustrial:insulated_copper_cable");
        server.runCommand("setblock 12 100 2 alaindustrial:insulated_tin_cable");
        server.runCommand("tp @p 9 103 8 180 25");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST] world screenshot -> {}", takeCleanScreenshot(context, "world_blocks").toAbsolutePath());

        // Cable network at eye level (y=101) for a clean close-up: a straight machine->cable->machine
        // run along X, plus a junction with south/up branches, so every arm direction is exercised.
        server.runCommand("setblock 3 101 4 alaindustrial:generator[facing=south]");
        server.runCommand("setblock 4 101 4 alaindustrial:copper_cable");
        server.runCommand("setblock 5 101 4 alaindustrial:copper_cable");
        server.runCommand("setblock 6 101 4 alaindustrial:copper_cable");
        server.runCommand("setblock 7 101 4 alaindustrial:battery_box[facing=south]");
        server.runCommand("setblock 5 101 5 alaindustrial:copper_cable");
        server.runCommand("setblock 5 102 4 alaindustrial:copper_cable");
        server.runCommand("tp @p 5 101 8 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST] cable screenshot -> {}", takeCleanScreenshot(context, "world_cables").toAbsolutePath());

        // Isolated panel-vs-neighbour culling check, far from other blocks. Row at z=42 (generator,
        // solar panel, stone) on dirt; camera SOUTH at z=46 looking NORTH (yaw 180) at the seam —
        // to see if the generator/ground faces next to the panel vanish (the reported X-ray).
        server.runCommand("gamemode spectator @p");
        server.runCommand("fill 40 99 40 46 99 46 minecraft:dirt");
        server.runCommand("setblock 41 100 42 minecraft:stone");
        server.runCommand("setblock 42 100 42 alaindustrial:generator[facing=south]");
        server.runCommand("setblock 43 100 42 alaindustrial:solar_panel");
        server.runCommand("setblock 44 100 42 alaindustrial:moonlit_solar_panel");
        server.runCommand("tp @p 43 101 46 180 8");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST] panel-neighbour -> {}", takeCleanScreenshot(context, "panel_neighbour").toAbsolutePath());
        // Top-down on the same seam.
        server.runCommand("tp @p 43 103 42 180 80");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST] panel-neighbour top -> {}", takeCleanScreenshot(context, "panel_neighbour_top").toAbsolutePath());
    }

    /**
     * R-GUI-01: Every machine GUI opens without crash; custom screens (Generator, Geothermal,
     * Macerator) are captured in three states — empty, mid-fill, and full — to verify bar/flame
     * positioning at all fill levels.
     *
     * <p>Capacity=4000 EU, maxProgress=200 ticks mirrors the default balance config values.
     *
     * <p>When adding a new machine: add one {@code shootMenu} line here. That's all.
     *
     * @implements R-GUI-03 - the energy and progress bars match the values behind them
     * @covers R-GUI-01, R-GUI-03
     */
    private static void shootGuiScreenshots(ClientGameTestContext context) {
        final int CAP  = 4000;
        final int BURN = 200;   // maxProgress for fuel-burning machines
        final int TANK = 10000; // maxProgress for geothermal (lavaTicks tank capacity)

        // ── Solar Panel — sun modes + energy fill levels (R-GUI-01, R-GUI-03) ──────
        // State 1: night, empty battery — no sun dot, no energy fill, no evo bar
        shootSolarPanel(context, "gui_solar_panel_night_empty",  0,    8000, 0, 0, 0, 33600);
        // State 2: day direct sun, empty battery — sun dot ON, energy bar empty
        shootSolarPanel(context, "gui_solar_panel_day_empty",    0,    8000, 1, 1, 0, 33600);
        // State 3: day direct sun, 25 % battery
        shootSolarPanel(context, "gui_solar_panel_day_25pct",    2000, 8000, 1, 1, 0, 33600);
        // State 4: day direct sun, 50 % battery
        shootSolarPanel(context, "gui_solar_panel_day_half",     4000, 8000, 1, 1, 0, 33600);
        // State 5: day direct sun, 75 % battery
        shootSolarPanel(context, "gui_solar_panel_day_75pct",    6000, 8000, 1, 1, 0, 33600);
        // State 6: rainy day, full battery — sun dot OFF, 100 % energy
        shootSolarPanel(context, "gui_solar_panel_rain_full",    8000, 8000, 0, 2, 0, 33600);
        // State 7: partial shade, quarter battery — sun dot ON, 25 % energy
        shootSolarPanel(context, "gui_solar_panel_partial",      2000, 8000, 1, 3, 0, 33600);

        // ── Solar Panel — day chip (yellow evo bar) ───────────────────────────────
        // State 8: day, day chip at 50 % evolution — yellow bar half-filled
        shootSolarPanel(context, "gui_solar_panel_evo_day_50pct",  4000, 8000, 1, 1, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);
        // State 9: day, day chip at 100 % evolution — yellow bar full
        shootSolarPanel(context, "gui_solar_panel_evo_day_full",   8000, 8000, 1, 1, 33600, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);

        // ── Solar Panel — night chip (blue evo bar) ───────────────────────────────
        // State 10: night, night chip at 50 % evolution — blue bar half-filled
        shootSolarPanel(context, "gui_solar_panel_evo_night_50pct", 2000, 8000, 0, 0, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 11: night, night chip at 75 % evolution
        shootSolarPanel(context, "gui_solar_panel_evo_night_75pct", 2000, 8000, 0, 0, 25200, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 12: night, night chip evolution complete — full blue bar
        shootSolarPanel(context, "gui_solar_panel_evo_night_full",  2000, 8000, 0, 0, 33600, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 13: day with night chip mid-evolution — sun active + blue bar (cross-mode check)
        shootSolarPanel(context, "gui_solar_panel_evo_night_day",   6000, 8000, 1, 1, 16800, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);
        // State 14: evolution just started (1 tick) — bar must show a minimum 1px so the player gets
        // immediate feedback instead of a blank track for the first ~22 s (proportional floor = 0px).
        shootSolarPanel(context, "gui_solar_panel_evo_day_start",   4000, 8000, 1, 1, 1, 33600,
                ModItems.ALIGNMENT_CHIP_DAY);
        // State 15: same first-tick minimum for the night branch (blue bar, 1px).
        shootSolarPanel(context, "gui_solar_panel_evo_night_start", 2000, 8000, 0, 0, 1, 33600,
                ModItems.ALIGNMENT_CHIP_NIGHT);

        // ── Water Mill — the status row in every state it can show (MOD-354) ─────────
        checkWaterMillStatusRow(context);

        // ── Machines without custom screens (one shot each) ──────────────────────────
        shootMenu(context, "gui_moonlit_solar_panel", ModContent.MOONLIT_SOLAR_PANEL_MENU.get(), "Moonlit Solar Panel");

        // ── MOD-080: upgrade panel open — gear tab + cross panel + mute chip in the active slot ──
        shootMenuWithPanelOpen(context, "gui_macerator_upgrades_open", ModContent.MACERATOR_MENU.get(), "Macerator", CAP, CAP, 0, 0);
        // Dragged over the GUI: proves the panel is a top overlay (GUI slots/text do not bleed over it).
        shootMenuWithPanelOpen(context, "gui_macerator_upgrades_dragged", ModContent.MACERATOR_MENU.get(), "Macerator", CAP, CAP, -120, 60);

        // ── Electric Furnace — three states ──────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_electric_furnace_empty",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                0, CAP, 0, BURN);

        // State 2: smelting — 50 % through, energy at 75 %
        shootMenuWithState(context, "gui_electric_furnace_smelting",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: full — full energy, arrow at max
        shootMenuWithState(context, "gui_electric_furnace_full",
                ModContent.ELECTRIC_FURNACE_MENU.get(), "Electric Furnace",
                CAP, CAP, BURN, BURN);

        // ── Sawmill (MOD-215) — three states ─────────────────────────────────────────
        // The machine has its own atlas, its own saw-blade progress sprite and a row of mode buttons
        // under the slots, so it is the one GUI in the mod where the layout itself is the feature —
        // these shots are the regression guard for it (the buttons are drawn in code, not the atlas).
        final int SAW = 80;   // Config.sawmillDuration — one cut at 1.0 speed
        shootMenuWithState(context, "gui_sawmill_empty",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                0, CAP, 0, SAW);
        shootMenuWithState(context, "gui_sawmill_sawing",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                CAP * 3 / 4, CAP, SAW / 2, SAW);
        shootMenuWithState(context, "gui_sawmill_full",
                ModContent.SAWMILL_MENU.get(), "Sawmill",
                CAP, CAP, SAW, SAW);

        // ── Assembler (MOD-275) — three states ───────────────────────────────────────
        // The one screen in the mod with ghost cells: nine slots that show items nobody owns, plus a
        // result preview, a Write button drawn in code and a status line. None of that is in the
        // atlas, so these shots are the only visual guard on it.
        final int ASM = 40;   // Config.assemblerDuration — one operation at 1.0 speed
        shootAssembler(context, "gui_assembler_empty", 0, CAP, 0, ASM, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_BLUEPRINT, false);
        shootAssembler(context, "gui_assembler_working", CAP * 3 / 4, CAP, ASM / 2, ASM, 0,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.READY, true);
        shootAssembler(context, "gui_assembler_output_full", CAP, CAP, 0, ASM, 2,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.OUTPUT_FULL, true);
        // Two more states, and they are the reason this stand exists after MOD-275's playtest: a queue
        // of recorded blueprints has to be readable WITHOUT touching anything. State 4 is the machine
        // idle — no layout of the player's own, so the window shows the queued blueprint's, dimmed and
        // blue-framed, with the blue ring naming which slot it came from. State 5 is the same window
        // while the machine works: the ring on the working slot turns MV orange. The blueprints in the
        // queue carry their own products in their icons (sticks vs planks — two different products in
        // one frame, so the icon cannot be a fixed picture).
        shootAssemblerQueue(context, "gui_assembler_blueprint_preview", CAP, CAP, 0, ASM, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, true);
        shootAssemblerQueue(context, "gui_assembler_blueprint_active", CAP * 3 / 4, CAP, ASM / 2, ASM, 1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.READY, true);
        assertBlueprintPreviewInFrame(context, ASM);
        // MOD-275, the tab split: one window, two tabs, and the two jobs must not share screen space.
        // Both tabs are photographed, and the gate proves the hidden one's contents are GONE rather
        // than merely covered — in both directions.
        assertHiddenTabIsGone(context);
        // The icon itself (the player's actual complaint): a recorded blueprint has to be readable
        // wherever the item is, not only inside this window. Shot in plain player-inventory slots,
        // which no mod code draws over, so what these frames show is the item model and nothing else.
        assertBlueprintIconShowsProduct(context);

        // ── Incubator (MOD-118) — three states ───────────────────────────────────────
        // Its screen carries two things no other machine draws: the charge pips under the fuel slot
        // and a status line that explains why nothing is happening. The energy bar sits on the right
        // here — the chip slot occupies the top-left corner the default bar would paint over.
        final int MUT = 300;  // Config.mutationDurationTransform — one transform at 1.0 speed
        shootIncubator(context, "gui_incubator_no_dome", 0, CAP, 0, MUT, -1, 0, 0);
        shootIncubator(context, "gui_incubator_no_chip", CAP, CAP, 0, MUT, -1, 0, 1);
        shootIncubator(context, "gui_incubator_running", CAP * 3 / 4, CAP, MUT / 2, MUT, 0, 2, 1);

        // ── BatteryBox — three states ─────────────────────────────────────────────────────
        shootMenuWithState(context, "gui_battery_box_empty",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                0, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_half",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                CAP / 2, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_full",
                ModContent.BATTERY_BOX_MENU.get(), "BatteryBox",
                CAP, CAP, 0, 0);
        // MOD-052: fourth state — a half-charged Battery Pouch sitting in the new charge slot, so the
        // reviewer sees the slot niche, the pouch icon and its EU item bar together.
        shootBatteryBoxWithPouch(context, "gui_battery_box_pouch", CAP / 2, CAP);
        shootBatteryBoxWithPack(context, "gui_battery_box_energy_pack", CAP / 2, CAP);

        // ── LV Generator — three states ──────────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_generator_empty",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                0, CAP, 0, BURN);

        // State 2: burning — 50 % fuel remaining, energy building to 50 %
        shootMenuWithState(context, "gui_generator_burning",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                CAP / 2, CAP, BURN / 2, BURN);

        // State 3: full energy — buffer saturated, no active burn
        shootMenuWithState(context, "gui_generator_full",
                ModContent.GENERATOR_MENU.get(), "LV Generator",
                CAP, CAP, 0, BURN);

        // ── Geothermal Generator — three states ──────────────────────────────────────
        // State 1: empty — no lava in tank, no energy
        shootMenuWithState(context, "gui_geothermal_empty",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                0, CAP, 0, TANK);

        // State 2: lava tank ~70 %, energy building (~40 %)
        shootMenuWithState(context, "gui_geothermal_mid",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                CAP * 2 / 5, CAP, TANK * 7 / 10, TANK);

        // State 3: full lava tank, full energy buffer
        shootMenuWithState(context, "gui_geothermal_full",
                ModContent.GEOTHERMAL_GENERATOR_MENU.get(), "Geothermal Generator",
                CAP, CAP, TANK, TANK);

        // ── Macerator — three states ─────────────────────────────────────────────────
        // State 1: empty — no input, no energy
        shootMenuWithState(context, "gui_macerator_empty",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                0, CAP, 0, BURN);

        // State 2: processing — 60 % through, moderate energy
        shootMenuWithState(context, "gui_macerator_processing",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, arrow at max
        shootMenuWithState(context, "gui_macerator_full",
                ModContent.MACERATOR_MENU.get(), "Macerator",
                CAP, CAP, BURN, BURN);

        // ── Extractor — three states ─────────────────────────────────────────────
        // State 1: empty — no input, no energy, chevrons dark
        shootMenuWithState(context, "gui_extractor_empty",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                0, CAP, 0, BURN);

        // State 2: extracting — 60 % through, energy at 75 % (cyan chevrons mid-fill)
        shootMenuWithState(context, "gui_extractor_processing",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, chevrons fully lit
        shootMenuWithState(context, "gui_extractor_full",
                ModContent.EXTRACTOR_MENU.get(), "Extractor",
                CAP, CAP, BURN, BURN);

        // ── Compressor — three states ────────────────────────────────────────────
        // State 1: idle — no energy, no active compression
        shootMenuWithState(context, "gui_compressor_idle",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                0, CAP, 0, BURN);

        // State 2: compressing — 50 % through, energy at 75 % (arrows mid-way toward center)
        shootMenuWithState(context, "gui_compressor_mid",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: done — full energy, arrows fully converged at center
        shootMenuWithState(context, "gui_compressor_done",
                ModContent.COMPRESSOR_MENU.get(), "Compressor",
                CAP, CAP, BURN, BURN);
    }

    /**
     * Opens the screen, then immediately injects ContainerData so the GUI renders the requested
     * state (energy fill, flame height, arrow width). Works only for screens backed by
     * {@link MachineMenu}; for other types falls back to the plain empty shot.
     */
    /** BatteryBox screen with a half-charged Battery Pouch injected into the charge slot (MOD-052). */
    private static void shootBatteryBoxWithPouch(ClientGameTestContext context, String name,
                                                 int energy, int capacity) {
        LOG.info("[GUITEST] opening {} (pouch in charge slot)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.BATTERY_BOX_MENU.get(), mc, 0, Component.literal("BatteryBox"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                ItemStack pouch = new ItemStack(ModItems.BATTERY_POUCH);
                dev.alaindustrial.item.energy.ItemEnergy.set(pouch, dev.alaindustrial.Config.lvPouchBuffer / 2);
                menu.getSlot(0).container.setItem(0, pouch);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * BatteryBox screen with a half-charged Energy Pack in the charge slot (MOD-065). Proves in one
     * frame that the slot accepts the pack at all (the filter is "any item with an EU buffer", not
     * "pouch only") and that the pack's icon + charge bar render in a GUI slot.
     */
    private static void shootBatteryBoxWithPack(ClientGameTestContext context, String name,
                                                int energy, int capacity) {
        LOG.info("[GUITEST] opening {} (energy pack in charge slot)", name);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.BATTERY_BOX_MENU.get(), mc, 0, Component.literal("BatteryBox"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                ItemStack pack = new ItemStack(ModItems.ENERGY_PACK);
                dev.alaindustrial.item.energy.ItemEnergy.set(pack, dev.alaindustrial.Config.energyPackBuffer / 2);
                menu.getSlot(0).container.setItem(0, pack);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    private static void shootMenuWithState(ClientGameTestContext context, String name,
                                           MenuType<?> type, String displayName,
                                           int energy, int capacity, int progress, int maxProgress) {
        LOG.info("[GUITEST] opening {} (E={}/{} P={}/{})", name, energy, capacity, progress, maxProgress);
        context.runOnClient(mc -> {
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            // In MC 26.2 the active screen lives in mc.gui.screen(), not mc.screen
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Incubator variant of {@link #shootMenuWithState} (MOD-118): also fills the three channels the
     * machine adds — the mode ordinal ({@code -1} = no chip inserted), the irradiation charge left on
     * the loaded ingot, and whether the dome is in place. Those three drive the charge pips and the
     * status line, which is the whole point of photographing this screen.
     */
    private static void shootIncubator(ClientGameTestContext context, String name,
                                       int energy, int capacity, int progress, int maxProgress,
                                       int mode, int charge, int formed) {
        LOG.info("[GUITEST] opening {} (E={}/{} P={}/{} mode={} charge={} formed={})",
                name, energy, capacity, progress, maxProgress, mode, charge, formed);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.INCUBATOR_MENU.get(), mc, 0, Component.literal("Incubator"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, mode);
                menu.injectTestChannel(5, charge);
                menu.injectTestChannel(6, formed);
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Assembler variant of {@link #shootMenuWithState} (MOD-275): also fills the two channels this
     * machine adds — the queue slot it is working from ({@code -1} = the queue is empty) and the idle
     * reason — and, when {@code withPattern}, lays a real recipe into the ghost grid so the shot shows
     * the cells, the result preview and an enabled Write button rather than an empty frame.
     *
     * <p>The pattern is written straight into the client-side container behind the ghost slots, the
     * same way {@link #shootSolarPanel} injects its chip: the screen reads slot contents
     * synchronously, so no server round-trip is needed to photograph the state.
     */
    private static void shootAssembler(ClientGameTestContext context, String name,
                                       int energy, int capacity, int progress, int maxProgress,
                                       int activeSlot,
                                       dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus status,
                                       boolean withPattern) {
        LOG.info("[GUITEST][MOD-275] opening {} (E={}/{} P={}/{} active={} status={} pattern={})",
                name, energy, capacity, progress, maxProgress, activeSlot, status, withPattern);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, activeSlot);
                menu.injectTestChannel(5, status.ordinal());
                selectTab(menu, dev.alaindustrial.menu.AssemblerMenu.TAB_WORK);
                if (withPattern) {
                    // A stick: two planks stacked in the left column — an off-centre layout on purpose,
                    // because that is the case CraftingInput trimming turns into a 1x2 grid. It reaches
                    // the Work tab through the QUEUE, which is what that tab draws its read-only view
                    // from; the editable grid it used to be injected into now lives on the Record tab.
                    menu.slots.get(0).set(blueprint(new int[] {0, 3},
                            net.minecraft.world.item.Items.OAK_PLANKS,
                            new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Assembler window with a queue of <b>recorded</b> blueprints and an untouched authoring grid — the
     * state MOD-275's playtest found unreadable, where the machine visibly worked and the window showed
     * nothing about what it was making.
     *
     * <p>Two different products on purpose (4 sticks in slot 0, 4 planks in slot 1): an icon that showed
     * the same picture for both would be a lie dressed as a feature, and one frame with two unlike icons
     * is what rules it out. {@code recorded=false} shoots the identical rig with blank blueprints, which
     * is the baseline the pixel gate measures against.
     */
    private static Path shootAssemblerQueue(ClientGameTestContext context, String name,
                                            int energy, int capacity, int progress, int maxProgress,
                                            int activeSlot,
                                            dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus status,
                                            boolean recorded) {
        LOG.info("[GUITEST][MOD-275] opening {} (active={} status={} recorded={})",
                name, activeSlot, status, recorded);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, progress, maxProgress);
                menu.injectTestChannel(4, activeSlot);
                menu.injectTestChannel(5, status.ordinal());
                selectTab(menu, dev.alaindustrial.menu.AssemblerMenu.TAB_WORK);
                // Slot 0: two planks in the left column -> 4 sticks. Slot 1: one log -> 4 planks.
                menu.slots.get(0).set(recorded
                        ? blueprint(new int[] {0, 3}, net.minecraft.world.item.Items.OAK_PLANKS,
                                new ItemStack(net.minecraft.world.item.Items.STICK, 4))
                        : new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
                menu.slots.get(1).set(recorded
                        ? blueprint(new int[] {0}, net.minecraft.world.item.Items.OAK_LOG,
                                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4))
                        : new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
            }
        });
        awaitMenuScreen(context);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * The Assembler window as {@code {leftPos, topPos, imageWidth, imageHeight, guiScaledWidth}}, in
     * GUI-scaled units, captured inside {@code runOnClient} by {@link #shootAssemblerTab}.
     */
    private static int[] assemblerWindowBox;

    /** Put the Assembler window on {@code tab}; a no-op for any other machine menu. */
    private static void selectTab(MachineMenu menu, int tab) {
        if (menu instanceof dev.alaindustrial.menu.AssemblerMenu assembler) {
            assembler.setActiveTab(tab);
        }
    }

    /**
     * One Assembler window on one tab, with each group of content independently switchable (MOD-275).
     *
     * <p>Everything else is pinned, so a pair of shots from this method differs by exactly the groups
     * that were toggled between them — which is what makes the tab gate below able to say "this content
     * is not in this frame" rather than merely "the two frames look different".
     *
     * @param tab       {@code TAB_WORK} or {@code TAB_RECORD}
     * @param fillGrid  lay a recipe into the authoring grid and its result preview (Record-tab content)
     * @param fillBlank put a blank blueprint in the Record tab's slot (Record-tab content)
     * @param fillQueue put two recorded blueprints in the queue (Work-tab content)
     */
    private static Path shootAssemblerTab(ClientGameTestContext context, String name, int tab,
                                          boolean fillGrid, boolean fillBlank, boolean fillQueue) {
        final int CAP = 12000;   // Config.assemblerBuffer
        final int ASM = 40;      // Config.assemblerDuration
        LOG.info("[GUITEST][MOD-275] opening {} (tab={} grid={} blank={} queue={})",
                name, tab, fillGrid, fillBlank, fillQueue);
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(CAP * 3 / 4, CAP, ASM / 2, ASM);
                menu.injectTestChannel(4, fillQueue ? 0 : -1);
                menu.injectTestChannel(5, dev.alaindustrial.block.entity.AssemblerBlockEntity
                        .AssemblerStatus.READY.ordinal());
                selectTab(menu, tab);
                var box = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
                assemblerWindowBox = new int[] {
                        box.alaindustrial$getLeftPos(), box.alaindustrial$getTopPos(),
                        box.alaindustrial$getImageWidth(), box.alaindustrial$getImageHeight(),
                        mc.getWindow().getGuiScaledWidth(),
                };
                if (fillGrid) {
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.GRID_SLOT_START)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.GRID_SLOT_START + 4)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    menu.slots.get(dev.alaindustrial.menu.AssemblerMenu.RESULT_SLOT_INDEX)
                            .set(new ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK));
                }
                if (fillBlank) {
                    menu.slots.get(dev.alaindustrial.block.entity.AssemblerBlockEntity.BLANK_SLOT)
                            .set(new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()));
                }
                if (fillQueue) {
                    menu.slots.get(0).set(blueprint(new int[] {0, 3},
                            net.minecraft.world.item.Items.OAK_PLANKS,
                            new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
                    menu.slots.get(1).set(blueprint(new int[] {0},
                            net.minecraft.world.item.Items.OAK_LOG,
                            new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4)));
                }
            }
        });
        context.waitTicks(5);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * The gate that says the hidden tab's contents are <b>gone</b>, not merely covered (MOD-275).
     *
     * <p>Hiding a slot in Minecraft is one boolean — {@code Slot#isActive} — and getting it wrong looks
     * exactly like getting it right in a single screenshot: the frame is drawn from a different atlas
     * either way, and a slot that is still painted underneath, or an item drawn over the wrong band, is
     * a couple of hundred pixels nobody notices. So this measures each tab's content twice, once with it
     * present and once absent, and asserts <em>both</em> directions:
     *
     * <ul>
     *   <li>on its own tab that content moves pixels (otherwise the injection is doing nothing and the
     *       zero below would be vacuous — this is the control);</li>
     *   <li>on the other tab the identical injection moves <b>no</b> pixels beyond the frame-to-frame
     *       noise floor.</li>
     * </ul>
     *
     * <p>Run for both tabs, so neither one is trusted on the strength of the other.
     */
    private static void assertHiddenTabIsGone(ClientGameTestContext context) {
        int work = dev.alaindustrial.menu.AssemblerMenu.TAB_WORK;
        int record = dev.alaindustrial.menu.AssemblerMenu.TAB_RECORD;

        // Noise floor: the same state, twice.
        Path noiseA = shootAssemblerTab(context, "gui_assembler_tab_noise_a", work, false, false, false);
        Path noiseB = shootAssemblerTab(context, "gui_assembler_tab_noise_b", work, false, false, false);
        int noise = differingPixelsInWindow(noiseA, noiseB);

        // Record-tab content: visible on Record, absent from Work.
        Path recOn = shootAssemblerTab(context, "gui_assembler_tab_record_filled", record, true, true, false);
        Path recOff = shootAssemblerTab(context, "gui_assembler_tab_record_empty", record, false, false, false);
        Path workRecOn = shootAssemblerTab(context, "gui_assembler_tab_work_recdata", work, true, true, false);
        Path workRecOff = shootAssemblerTab(context, "gui_assembler_tab_work_nodata", work, false, false, false);

        // Work-tab content: visible on Work, absent from Record.
        Path workOn = shootAssemblerTab(context, "gui_assembler_tab_work_filled", work, false, false, true);
        Path workOff = workRecOff;
        Path recWorkOn = shootAssemblerTab(context, "gui_assembler_tab_record_workdata", record, false, false, true);
        Path recWorkOff = recOff;

        assertTabIsolation("Record", differingPixelsInWindow(recOn, recOff),
                differingPixelsInWindow(workRecOn, workRecOff), noise, recOn, workRecOn);
        assertTabIsolation("Work", differingPixelsInWindow(workOn, workOff),
                differingPixelsInWindow(recWorkOn, recWorkOff), noise, workOn, recWorkOn);
    }

    /**
     * Pixels that differ inside the Assembler window, and nowhere else.
     *
     * <p>The whole screen is the wrong canvas for this measurement, and measuring it taught that the
     * hard way: the recipe viewer's item panel down the right-hand edge re-renders its item models every
     * frame and drifts by a few dozen pixels between two identical shots. Cropping to the window is not
     * a way of hiding an inconvenient signal — it is the only region either tab can draw in, so a
     * difference outside it cannot be the thing under test, and a noise floor big enough to absorb the
     * viewer would also absorb a genuinely leaking slot.
     */
    private static int differingPixelsInWindow(Path first, Path second) {
        ShotRecorder.markComparedFiles(first, second);
        if (assemblerWindowBox == null) {
            throw new AssertionError("[GUITEST][MOD-275] the Assembler window was never measured");
        }
        try {
            BufferedImage a = ImageIO.read(first.toFile());
            BufferedImage b = ImageIO.read(second.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST][MOD-275] could not decode " + first + " / " + second);
            }
            // Screenshots are in physical pixels, the box in GUI-scaled units.
            int scale = Math.max(1, a.getWidth() / assemblerWindowBox[4]);
            int x0 = assemblerWindowBox[0] * scale;
            int y0 = assemblerWindowBox[1] * scale;
            int x1 = Math.min(a.getWidth(), x0 + assemblerWindowBox[2] * scale);
            int y1 = Math.min(a.getHeight(), y0 + assemblerWindowBox[3] * scale);
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
            throw new AssertionError("[GUITEST][MOD-275] could not read screenshots for the tab gate", e);
        }
    }

    /** One direction of {@link #assertHiddenTabIsGone}: seen on its own tab, unseen on the other. */
    private static void assertTabIsolation(String owner,
                                           int onOwnTab, int onOtherTab, int noise,
                                           Path ownShot, Path otherShot) {
        LOG.info("[GUITEST][MOD-275] {} content: {} px on its own tab, {} px on the other (noise {})",
                owner, onOwnTab, onOtherTab, noise);
        int required = Math.max(4 * noise, 200);
        if (onOwnTab < required) {
            throw new AssertionError("[GUITEST][MOD-275] the " + owner + " tab's own content changed only "
                    + onOwnTab + " px (noise " + noise + ", required > " + required + ") — the shots are "
                    + "not injecting anything, so the isolation check below would be vacuous. See "
                    + ownShot.getFileName() + ".");
        }
        // Zero, not "small": inside the window there is nothing to be noisy about, and the noise floor
        // measured above is the evidence for that rather than a budget to spend.
        int tolerated = noise;
        if (onOtherTab > tolerated) {
            throw new AssertionError("[GUITEST][MOD-275] " + owner + "-tab content moved " + onOtherTab
                    + " px on the OTHER tab (noise floor " + tolerated + ") — a hidden slot is still being "
                    + "drawn, or its item is bleeding into the visible band. See " + otherShot.getFileName()
                    + ".");
        }
    }

    /** A recorded blueprint: {@code item} in each of {@code cells}, carrying {@code result} as its product. */
    private static ItemStack blueprint(int[] cells, net.minecraft.world.item.Item item, ItemStack result) {
        java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(
                dev.alaindustrial.item.assembler.BlueprintPattern.GRID_SIZE, ItemStack.EMPTY));
        for (int cell : cells) {
            grid.set(cell, new ItemStack(item));
        }
        return dev.alaindustrial.item.assembler.AssemblyBlueprintItem.record(
                new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()),
                dev.alaindustrial.item.assembler.BlueprintPattern.of(grid), result);
    }

    /**
     * Pixel gate for MOD-275's read-only preview: a screenshot of a window is not evidence that anything
     * was drawn in it.
     *
     * <p>Same window twice — once with recorded blueprints in the queue, once with blanks. Everything
     * else is identical, so the only thing that can move a pixel is the new drawing: the borrowed layout
     * in the ghost cells, the blue frame around it, the result preview and the two icons. A run where
     * the preview silently stopped rendering shows a delta of nearly zero here and fails, instead of
     * producing a perfectly valid, perfectly empty frame for a reviewer to sign off.
     */
    private static void assertBlueprintPreviewInFrame(ClientGameTestContext context, int maxProgress) {
        final int CAP = 12000;   // Config.assemblerBuffer
        Path recorded = shootAssemblerQueue(context, "gui_assembler_bp_gate_recorded", CAP, CAP, 0,
                maxProgress, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, true);
        Path blankA = shootAssemblerQueue(context, "gui_assembler_bp_gate_blank_a", CAP, CAP, 0,
                maxProgress, -1,
                dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus.NO_MATERIALS, false);
        context.waitTicks(3);
        Path blankB = takeCleanScreenshot(context, "gui_assembler_bp_gate_blank_b");

        int previewDelta = differingPixels(recorded, blankA);
        int noise = differingPixels(blankA, blankB);
        // A static GUI has essentially no frame-to-frame noise, so the floor is what matters: the two
        // layout items, the result preview, the frame and the icons cover well over 400 px even at the
        // smallest GUI scale this harness runs at.
        int required = Math.max(4 * noise, 400);
        LOG.info("[GUITEST][MOD-275] preview pixel gate: delta={} px, noise={} px, required>{}",
                previewDelta, noise, required);
        if (previewDelta < required) {
            throw new AssertionError("[GUITEST][MOD-275] recording the queued blueprints changed only "
                    + previewDelta + " px (noise " + noise + ", required > " + required + ") — the "
                    + "read-only layout preview and the blueprint icons are NOT in the captured frame. "
                    + "Compare " + recorded.getFileName() + " with " + blankA.getFileName() + ".");
        }
    }

    /**
     * Photographs three blueprint icons side by side in ordinary player-inventory slots and gates them
     * on pixels (MOD-275).
     *
     * <p>The player's complaint was about the <b>item</b>, not the machine window: a recorded blueprint
     * looked exactly like a blank one in the hotbar, on the ground and in a chest. The fix replaces the
     * grid motif in the middle of the icon with the recipe's real product, through a custom item-model
     * type ({@code alaindustrial:blueprint_result}). Player-inventory slots are used on purpose — the
     * Assembler draws its own decoration over its queue slots, so a shot taken there could pass on the
     * strength of that decoration alone.
     *
     * <p>Two gates, because "something changed" and "the right thing changed" are different claims:
     * <ul>
     *   <li><b>Drawn at all</b> — three recorded blueprints against three blanks. A registration that
     *       silently fell back to the plain model, or a composite that rendered nothing, shows a delta
     *       of roughly zero here and fails instead of producing a valid, empty-looking frame.
     *   <li><b>Reflects the recipe</b> — three sticks-blueprints against three planks-blueprints. Same
     *       component, same model, different product: a static "recorded" texture pretending to be
     *       dynamic passes the first gate and dies on this one.
     * </ul>
     */
    private static void assertBlueprintIconShowsProduct(ClientGameTestContext context) {
        ItemStack blank = new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get());
        // Two planks in the left column -> 4 sticks (a flat, mostly transparent icon), and one log ->
        // 4 planks (a full cube). Two very different shapes, so the second gate cannot be satisfied by
        // a tint difference.
        ItemStack sticks = blueprint(new int[] {0, 3}, net.minecraft.world.item.Items.OAK_PLANKS,
                new ItemStack(net.minecraft.world.item.Items.STICK, 4));
        ItemStack planks = blueprint(new int[] {0}, net.minecraft.world.item.Items.OAK_LOG,
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));

        // The reviewer's frame: blank, sticks and planks in one row, so "these are three different
        // pieces of paper" is a claim a human can check in one glance.
        shootBlueprintIcons(context, "gui_blueprint_icon_states", blank, sticks, planks);

        Path recorded = shootBlueprintIcons(context, "gui_blueprint_icon_gate_sticks",
                sticks, sticks, sticks);
        Path blankA = shootBlueprintIcons(context, "gui_blueprint_icon_gate_blank_a",
                blank, blank, blank);
        context.waitTicks(3);
        Path blankB = takeCleanScreenshot(context, "gui_blueprint_icon_gate_blank_b");
        Path otherProduct = shootBlueprintIcons(context, "gui_blueprint_icon_gate_planks",
                planks, planks, planks);
        // The lighting stand: the same block, once composed into a blueprint and once as itself, in two
        // adjacent slots of the same frame — so the comparison below is free of every other variable.
        Path lighting = shootBlueprintIcons(context, "gui_blueprint_icon_gate_lighting",
                planks, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), blank);
        // Leave the slots as we found them: every later shot in this suite renders the same inventory.
        Path cleared = shootBlueprintIcons(context, "gui_blueprint_icon_cleared",
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        // Out of the GUI entirely. The item-model definition carries no display-context switch, so the
        // product should follow the item into the hand — unlike vanilla's bundle, which restricts its
        // composed item to "when": "gui". Photographed rather than assumed.
        // Gated on two *products*, not on recorded-vs-blank. Blank and recorded blueprints also differ
        // by their sheet texture (the grid motif is erased on a recorded one), so a recorded-vs-blank
        // delta out here stays large even when the product is drawn in the GUI only — measured, not
        // assumed: restricting the product to "when": "gui" left that comparison at 52 000 px. Sticks
        // against planks shares the sheet, so anything this gate sees is the product.
        shootBlueprintInHand(context, "world_blueprint_in_hand_blank", blank);
        Path handSticks = shootBlueprintInHand(context, "world_blueprint_in_hand_sticks", sticks);
        Path handPlanks = shootBlueprintInHand(context, "world_blueprint_in_hand_planks", planks);
        // The baseline goes through a full swap cycle (sticks -> empty -> sticks), not two consecutive
        // frames: a world frame drifts between shots (sky, equip animation), and a baseline that skipped
        // the swap would understate the noise and make the gate vacuous.
        shootBlueprintInHand(context, "world_blueprint_in_hand_empty", ItemStack.EMPTY);
        Path handSticksAgain = shootBlueprintInHand(context, "world_blueprint_in_hand_sticks_again", sticks);
        shootBlueprintInHand(context, "world_blueprint_in_hand_cleared", ItemStack.EMPTY);

        int drawnDelta = differingPixels(recorded, blankA);
        int noise = differingPixels(blankA, blankB);
        int productDelta = differingPixels(recorded, otherProduct);
        int handDelta = differingPixels(handSticks, handPlanks);
        int handNoise = differingPixels(handSticks, handSticksAgain);
        // Three icons at 16x16 GUI px, gui scale 2, with the product filling the middle 10x10 of each:
        // ~1200 px of the frame belong to the three products. The floors below are a quarter of that,
        // so a partial render still fails while driver dithering never flakes a healthy one.
        int drawnRequired = Math.max(4 * noise, 300);
        int productRequired = Math.max(4 * noise, 200);
        LOG.info("[GUITEST][MOD-275] icon pixel gates: recorded-vs-blank={} px (>{}), "
                + "sticks-vs-planks={} px (>{}), noise={} px",
                drawnDelta, drawnRequired, productDelta, productRequired, noise);
        if (drawnDelta < drawnRequired) {
            throw new AssertionError("[GUITEST][MOD-275] recording a blueprint changed only "
                    + drawnDelta + " px of its icon (noise " + noise + ", required > " + drawnRequired
                    + ") — the product is NOT drawn into the item model. Either the "
                    + "alaindustrial:blueprint_result model type is not registered on this loader, or "
                    + "items/assembly_blueprint.json no longer selects it. Compare "
                    + recorded.getFileName() + " with " + blankA.getFileName() + ".");
        }
        if (productDelta < productRequired) {
            throw new AssertionError("[GUITEST][MOD-275] a sticks blueprint and a planks blueprint "
                    + "render " + productDelta + " px apart (noise " + noise + ", required > "
                    + productRequired + ") — the icon does not follow the recorded recipe. Compare "
                    + recorded.getFileName() + " with " + otherProduct.getFileName() + ".");
        }
        // "Inside the frame" is an acceptance criterion, not a matter of taste: the sheet's dark border
        // ring and its highlight row have to survive, or the icon stops reading as a blueprint at all.
        // Every pixel the recording changed must therefore land inside the sheet's interior.
        assertDiffStaysInsideIconInteriors(recorded, blankA);
        // Inside the frame is not the same claim as in the MIDDLE of it: everything above stays green
        // with the product shoved into a corner of the field. Both a flat product and a block one are
        // measured against the sheet's own texture (MOD-275 follow-up).
        assertProductCentredInIcon(recorded, "sticks");
        assertProductCentredInIcon(otherProduct, "planks");
        // Placement is not the same claim as LIGHTING: every gate above stayed green for a release while
        // the composed block rendered dark grey, because they all count pixels or measure where they are.
        assertProductLitLikeTheItemItself(lighting, cleared);
        // The held item is a fraction of the frame and the sky moves behind it, so this gate is
        // measured against its own noise baseline rather than a fixed floor.
        int handRequired = Math.max(4 * handNoise, 4000);
        LOG.info("[GUITEST][MOD-275] in-hand pixel gate: delta={} px (>{}), noise={} px",
                handDelta, handRequired, handNoise);
        if (handDelta < handRequired) {
            throw new AssertionError("[GUITEST][MOD-275] a sticks blueprint and a planks blueprint look "
                    + handDelta + " px apart in the player's hand (noise " + handNoise + ", required > "
                    + handRequired + ") — the product reaches the icon in a GUI slot but not in the "
                    + "world, so the item definition has picked up a display-context restriction. "
                    + "Compare " + handSticks.getFileName() + " with " + handPlanks.getFileName() + ".");
        }
    }

    /**
     * The three icon slots of the last {@link #shootBlueprintIcons} call, in GUI coordinates
     * ({@code x, y} of the 16×16 icon), plus the GUI-scaled screen width the shot was taken at.
     * Written inside the client task, read by {@link #assertDiffStaysInsideIconInteriors}.
     */
    private static int[][] iconSlotBoxes;
    private static int iconGuiScaledWidth;

    /**
     * Fails unless every pixel that differs between the two frames lies inside one of the three icons'
     * <em>interiors</em> — the 12×12 field the sheet's border ring encloses.
     *
     * <p>The pixel-count gates above prove something was drawn and that it follows the recipe; neither
     * would notice a product drawn at full size, spilling over the frame and turning the blueprint into
     * "an item with a blue outline". This is the check for the size and placement, and it is exact:
     * the border ring (icon column/row 1) and the highlight and shadow rows (2 and 13) must come out
     * byte-identical to the blank sheet's.
     */
    private static void assertDiffStaysInsideIconInteriors(Path recorded, Path blank) {
        if (iconSlotBoxes == null) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        try {
            BufferedImage a = ImageIO.read(recorded.toFile());
            BufferedImage b = ImageIO.read(blank.toFile());
            if (a == null || b == null) {
                throw new AssertionError("[GUITEST][MOD-275] could not decode " + recorded + " / " + blank);
            }
            // The screenshot is the raw framebuffer; the GUI is drawn at an integer scale over it.
            int scale = a.getWidth() / iconGuiScaledWidth;
            // Icon interior: columns/rows 2..13 of the 16×16 sprite. 1 is the border ring, 0 is the
            // one-pixel margin outside it.
            final int INSET = 2;
            final int INTERIOR = 12;
            // Scan the icons and their immediate surroundings only. The rest of the frame carries the
            // world behind the window, whose few drifting pixels have nothing to say about whether the
            // product stayed inside its frame — and a whole-frame scan would fail on them.
            final int MARGIN = 8;
            int scanX0 = Integer.MAX_VALUE;
            int scanY0 = Integer.MAX_VALUE;
            int scanX1 = Integer.MIN_VALUE;
            int scanY1 = Integer.MIN_VALUE;
            for (int[] box : iconSlotBoxes) {
                scanX0 = Math.min(scanX0, (box[0] - MARGIN) * scale);
                scanY0 = Math.min(scanY0, (box[1] - MARGIN) * scale);
                scanX1 = Math.max(scanX1, (box[0] + 16 + MARGIN) * scale);
                scanY1 = Math.max(scanY1, (box[1] + 16 + MARGIN) * scale);
            }
            scanX0 = Math.max(0, scanX0);
            scanY0 = Math.max(0, scanY0);
            scanX1 = Math.min(a.getWidth(), scanX1);
            scanY1 = Math.min(a.getHeight(), scanY1);
            int outside = 0;
            int firstX = -1;
            int firstY = -1;
            for (int y = scanY0; y < scanY1; y++) {
                for (int x = scanX0; x < scanX1; x++) {
                    if (!differsAt(a, b, x, y)) {
                        continue;
                    }
                    boolean inside = false;
                    for (int[] box : iconSlotBoxes) {
                        int x0 = (box[0] + INSET) * scale;
                        int y0 = (box[1] + INSET) * scale;
                        if (x >= x0 && x < x0 + INTERIOR * scale && y >= y0 && y < y0 + INTERIOR * scale) {
                            inside = true;
                            break;
                        }
                    }
                    if (!inside) {
                        if (firstX < 0) {
                            firstX = x;
                            firstY = y;
                        }
                        outside++;
                    }
                }
            }
            LOG.info("[GUITEST][MOD-275] icon containment gate: {} px changed outside the sheet interiors",
                    outside);
            if (outside > 0) {
                throw new AssertionError("[GUITEST][MOD-275] recording a blueprint changed " + outside
                        + " px outside the sheets' interiors (first at " + firstX + "," + firstY
                        + ") — the product overflows the frame instead of sitting inside it. Check the "
                        + "scale in the minecraft:composite transformation of items/assembly_blueprint.json. "
                        + "Compare " + recorded.getFileName() + " with " + blank.getFileName() + ".");
            }
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read the icon screenshots", e);
        }
    }

    /**
     * The recorded sheet the icon is drawn on. Read straight off the mod's own resources, so the gate
     * below re-measures itself if the art ever changes instead of pinning colours as constants.
     */
    private static final String RECORDED_SHEET_TEXTURE =
            "/assets/alaindustrial/textures/item/assembly_blueprint_recorded.png";

    /**
     * How far the product's centre may sit from the icon's centre, in sprite texels (MOD-275).
     *
     * <p>Three quarters of a texel. The residual it has to tolerate is the product sprite's own
     * off-centre artwork — a vanilla stick's opaque pixels run 2..14 of its 16×16 sheet, so its visual
     * middle is 8.5, not 8.0, and shrunk to 10/16 that puts its centre of mass 0.38 texels low and
     * right; an oak-planks cube measures 0.01. A placement error, by contrast, moves in whole texels:
     * the smallest wrong translation the JSON can carry (1/16 off) displaces the product by a full
     * texel and fails.
     */
    private static final double ICON_CENTRE_TOLERANCE_TEXELS = 0.75;

    /** The recorded sheet, read off the mod's own resources so the gates re-measure instead of pinning. */
    private static BufferedImage readRecordedSheet() {
        BufferedImage sheet;
        try (java.io.InputStream in =
                     GuiClientGameTest.class.getResourceAsStream(RECORDED_SHEET_TEXTURE)) {
            if (in == null) {
                throw new AssertionError("[GUITEST][MOD-275] " + RECORDED_SHEET_TEXTURE
                        + " is not on the classpath — the sheet texture moved or was renamed.");
            }
            sheet = ImageIO.read(in);
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + RECORDED_SHEET_TEXTURE, e);
        }
        if (sheet == null || sheet.getWidth() != 16 || sheet.getHeight() != 16) {
            throw new AssertionError("[GUITEST][MOD-275] the recorded sheet is expected to be a 16×16 "
                    + "sprite; the gates' texel arithmetic does not hold otherwise.");
        }
        return sheet;
    }

    /**
     * Fails unless the product sits in the MIDDLE of the sheet, not merely somewhere inside it
     * (MOD-275 follow-up — the playtest complaint was "it is not centred", which every earlier gate
     * passed happily).
     *
     * <p>Method: the recorded blueprint is a flat 16×16 sprite drawn at an integer GUI scale, so every
     * pixel of the icon that is <em>not</em> the sheet's own texel is a pixel of the product. Compared
     * against the texture rather than against a blank blueprint on purpose — a blank carries a
     * different sheet (the 3×3 grid motif), so a recorded-vs-blank diff is contaminated by the erased
     * grid, which is itself centred and would drag any centre-of-mass measurement back towards the
     * middle no matter where the product went.
     *
     * <p>Two measurements, because they fail on different mistakes. The bounding box says the product
     * stays inside the frame — a centre test alone passes a product that is centred but overflows.
     * The <em>centre of mass</em> says it sits in the middle — a box test alone passes a product
     * shoved off to one side, and the box's own centre is the wrong instrument for that: an isometric
     * block's silhouette narrows to a single vertex top and bottom, and whether that sliver lands on a
     * sample point shifts the box by half a texel for reasons that have nothing to do with placement.
     * The centre of mass of the same shape is stable to a hundredth of a texel.
     */
    private static void assertProductCentredInIcon(Path frame, String label) {
        if (iconSlotBoxes == null) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        BufferedImage sheet = readRecordedSheet();
        BufferedImage shot;
        try {
            shot = ImageIO.read(frame.toFile());
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + frame, e);
        }
        if (shot == null) {
            throw new AssertionError("[GUITEST][MOD-275] could not decode " + frame);
        }
        int scale = shot.getWidth() / iconGuiScaledWidth;
        for (int slot = 0; slot < iconSlotBoxes.length; slot++) {
            int[] box = iconSlotBoxes[slot];
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            int count = 0;
            double sumX = 0.0;
            double sumY = 0.0;
            for (int ty = 0; ty < 16; ty++) {
                for (int tx = 0; tx < 16; tx++) {
                    int expected = sheet.getRGB(tx, ty);
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            int x = (box[0] + tx) * scale + sx;
                            int y = (box[1] + ty) * scale + sy;
                            if (!differsFrom(shot, x, y, expected)) {
                                continue;
                            }
                            count++;
                            // Sub-texel bounds: the pixel covers [u, u + 1/scale) of the sprite.
                            double u = tx + (double) sx / scale;
                            double v = ty + (double) sy / scale;
                            minX = Math.min(minX, u);
                            minY = Math.min(minY, v);
                            maxX = Math.max(maxX, u + 1.0 / scale);
                            maxY = Math.max(maxY, v + 1.0 / scale);
                            sumX += u + 0.5 / scale;
                            sumY += v + 0.5 / scale;
                        }
                    }
                }
            }
            // A stick is the thinnest product this suite photographs and still covers ~55 pixels at
            // scale 2; a floor of 16 says "something is drawn" without pinning either product's shape.
            if (count < 16) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " carries "
                        + count + " px that differ from the bare sheet — the product is not drawn at "
                        + "all. Compare " + frame.getFileName() + " with " + RECORDED_SHEET_TEXTURE + ".");
            }
            double centreX = sumX / count;
            double centreY = sumY / count;
            LOG.info("[GUITEST][MOD-275] icon {} of {}: {} px, box x[{}, {}] y[{}, {}], "
                            + "centre of mass ({}, {})",
                    slot, label, count, minX, maxX, minY, maxY, centreX, centreY);
            // Columns/rows 2..13 are the field the border ring encloses; the product must not reach the
            // ring itself. Same acceptance criterion as the containment gate, restated per product so
            // this method is a complete statement about placement on its own.
            if (minX < 2.0 || minY < 2.0 || maxX > 14.0 || maxY > 14.0) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " draws the "
                        + "product over texels x[" + minX + ", " + maxX + "] y[" + minY + ", " + maxY
                        + "] — outside the sheet's 2..14 interior. Check the scale in the "
                        + "minecraft:composite transformation of items/assembly_blueprint.json.");
            }
            double offX = centreX - 8.0;
            double offY = centreY - 8.0;
            if (Math.abs(offX) > ICON_CENTRE_TOLERANCE_TEXELS
                    || Math.abs(offY) > ICON_CENTRE_TOLERANCE_TEXELS) {
                throw new AssertionError("[GUITEST][MOD-275] icon " + slot + " of " + label + " puts the "
                        + "product's centre of mass at (" + centreX + ", " + centreY + ") texels instead "
                        + "of (8, 8) — off by (" + offX + ", " + offY + "), tolerance "
                        + ICON_CENTRE_TOLERANCE_TEXELS + ". The sheet's frame is symmetric, so the blue "
                        + "field's centre IS the icon's centre; a layer scaled by s is centred only when "
                        + "the minecraft:composite translates it by (1 - s) / 2 on every axis. Check the "
                        + "translation in items/assembly_blueprint.json against " + frame.getFileName() + ".");
            }
        }
    }

    /**
     * How far the composed product's top-to-side shading contrast may drift from the same item's own.
     *
     * <p>Mean brightness is NOT the instrument here, and that is worth recording: measured on the two
     * rigs, the composed cube's mean luminance came out 106 under the wrong rig and 88 under the right
     * one — <em>brighter</em> when broken. The flat-sprite rig does not dim a block, it relights it, and
     * what the playtest saw as "dark grey" was the top face going dark while a side face went white. So
     * the gate compares the shape of the shading, not its level: top band against bottom band. The two
     * rigs put that ratio 45 % apart (1.24 against 1.82), and two sizes of the same cube agree to a few
     * percent, so a fifth of the way between them separates them with room on both sides.
     */
    private static final double ICON_SHADING_TOLERANCE = 0.20;

    /**
     * How far the composed product's aspect ratio may drift from the same item's own (MOD-275 follow-up).
     *
     * <p>This is the gate for "cropped", and it exists because none of the placement gates could see it.
     * The bounding box only says the product stays inside the frame — a cut-down product is smaller, so
     * it passes more easily. The centre of mass only says it sits in the middle — the sheet bisects the
     * product symmetrically, so a bisected cube is still dead centre. Both stayed green while the icon
     * showed a flat-topped stump. Shape is the thing neither measures: the icon draws the product with a
     * uniform scale, so its silhouette must have the same proportions as the item drawn on its own.
     * Measured, the bisected cube came out 18×17 device px against a whole one's 18×20 — 0.94 against
     * 1.11, 15 % apart — while the two whole shapes agree to a few percent across a 10-vs-16 texel size
     * difference.
     */
    private static final double ICON_SILHOUETTE_TOLERANCE = 0.08;

    /**
     * Fails unless a block product composed into a blueprint is as brightly lit as the same block drawn
     * as an ordinary item one slot to its right (MOD-275 follow-up).
     *
     * <p><b>Why this gate had to be added.</b> Every other icon gate in this file — pixel deltas,
     * containment, bounding box, centre of mass — is a statement about <em>where</em> pixels are. All of
     * them stayed green through an entire release in which the product rendered dark grey, because a
     * dark cube occupies exactly the same texels as a lit one. Brightness needed its own instrument.
     *
     * <p><b>Method.</b> One frame, two adjacent slots: a planks blueprint in slot 0 and a plain oak
     * planks stack in slot 1. Slot 0's product is every pixel that differs from the bare sheet texture
     * (the same identification {@link #assertProductCentredInIcon} uses); slot 1's item is every pixel
     * that differs from the same slot in the all-empty frame. Each shape is then split into a top band
     * (its cube's upward face) and a bottom band (its side faces), and the two <em>contrasts</em> are
     * compared. Measuring the reference in the SAME frame is the point — it cancels the GUI scale, the
     * window background and any driver gamma, so the only thing left between the two numbers is the rig.
     */
    private static void assertProductLitLikeTheItemItself(Path frame, Path emptyFrame) {
        if (iconSlotBoxes == null || iconSlotBoxes.length < 2) {
            throw new AssertionError("[GUITEST][MOD-275] no icon slot geometry was captured — "
                    + "shootBlueprintIcons must run before this gate.");
        }
        BufferedImage sheet = readRecordedSheet();
        BufferedImage shot;
        BufferedImage empty;
        try {
            shot = ImageIO.read(frame.toFile());
            empty = ImageIO.read(emptyFrame.toFile());
        } catch (IOException e) {
            throw new AssertionError("[GUITEST][MOD-275] could not read " + frame + " / " + emptyFrame, e);
        }
        if (shot == null || empty == null) {
            throw new AssertionError("[GUITEST][MOD-275] could not decode " + frame + " / " + emptyFrame);
        }
        int scale = shot.getWidth() / iconGuiScaledWidth;
        IconShape composedShape = measureShape(shot, iconSlotBoxes[0], scale, sheet, null,
                "composed product");
        IconShape plainShape = measureShape(shot, iconSlotBoxes[1], scale, null, empty, "plain item");
        double aspectDrift = composedShape.aspect() / plainShape.aspect() - 1.0;
        LOG.info("[GUITEST][MOD-275] icon silhouette gate: composed {}x{} (aspect {}), "
                        + "plain {}x{} (aspect {}), drift={} (|.| < {})",
                composedShape.width(), composedShape.height(), composedShape.aspect(),
                plainShape.width(), plainShape.height(), plainShape.aspect(),
                aspectDrift, ICON_SILHOUETTE_TOLERANCE);
        if (Math.abs(aspectDrift) > ICON_SILHOUETTE_TOLERANCE) {
            throw new AssertionError("[GUITEST][MOD-275] the composed product's silhouette is "
                    + composedShape.width() + "x" + composedShape.height() + " px (aspect "
                    + composedShape.aspect() + ") while the same item drawn on its own is "
                    + plainShape.width() + "x" + plainShape.height() + " (aspect " + plainShape.aspect()
                    + ", drift " + aspectDrift + ", tolerance " + ICON_SILHOUETTE_TOLERANCE + ") — the "
                    + "product is not the whole shape. A composite scale cannot do this: it is uniform. "
                    + "What can is the sheet, if the product intersects it — everything behind the "
                    + "paper's plane is occluded and the silhouette comes back cut off. See "
                    + "BlueprintResultItemModel#onTheSheet. Compare " + frame.getFileName() + ".");
        }
        double composed = composedShape.contrast();
        double plain = plainShape.contrast();
        double drift = composed / plain - 1.0;
        LOG.info("[GUITEST][MOD-275] icon lighting gate: top/bottom contrast composed={}, plain={}, "
                + "drift={} (|.| < {})", composed, plain, drift, ICON_SHADING_TOLERANCE);
        if (Math.abs(drift) > ICON_SHADING_TOLERANCE) {
            throw new AssertionError("[GUITEST][MOD-275] a block product composed into a blueprint is "
                    + "shaded with a top-to-side contrast of " + composed + " while the same block as a "
                    + "plain item measures " + plain + " in the same frame (drift " + drift
                    + ", tolerance " + ICON_SHADING_TOLERANCE + ") — the GUI is lighting the whole icon "
                    + "with the wrong rig. ItemStackRenderState.usesBlockLight() answers for layer 0 "
                    + "only, so the paper decides the rig for the product; see BlueprintResultItemModel. "
                    + "Compare " + frame.getFileName() + " with " + emptyFrame.getFileName() + ".");
        }
    }

    /**
     * A drawn shape's silhouette in device pixels plus its top-to-bottom shading contrast.
     *
     * <p>Both numbers describe the same set of pixels and are measured in one pass, because both gates
     * ask about the same shape: is it whole, and is it lit like the item it is a copy of.
     */
    private record IconShape(int width, int height, double contrast) {
        double aspect() {
            return (double) height / width;
        }
    }

    /**
     * Measures the shape a slot draws: its bounding box, and the mean luminance of its top third over
     * that of its bottom half.
     *
     * <p>The shape is whatever differs from the reference: a 16×16 sprite passed as {@code sheet}, or
     * the same slot of another frame passed as {@code background}. Bands are taken as fractions of the
     * shape's own bounding box, so the measurement does not care how large the shape is — which is the
     * whole point, since the two shapes compared here are 10 and 16 texels across.
     */
    private static IconShape measureShape(BufferedImage shot, int[] box, int scale,
                                          BufferedImage sheet, BufferedImage background, String label) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int x = (box[0] + tx) * scale + sx;
                        int y = (box[1] + ty) * scale + sy;
                        int expected = sheet != null ? sheet.getRGB(tx, ty) : background.getRGB(x, y);
                        if (!differsFrom(shot, x, y, expected)) {
                            continue;
                        }
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        count++;
                    }
                }
            }
        }
        if (count < 32) {
            throw new AssertionError("[GUITEST][MOD-275] the lighting stand drew " + count + " px of "
                    + label + " — that slot is empty, so the shading comparison would be meaningless.");
        }
        double height = maxY - minY + 1;
        double topEdge = minY + 0.35 * height;
        double bottomEdge = minY + 0.55 * height;
        double topSum = 0.0;
        double bottomSum = 0.0;
        int topCount = 0;
        int bottomCount = 0;
        for (int ty = 0; ty < 16; ty++) {
            for (int tx = 0; tx < 16; tx++) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int x = (box[0] + tx) * scale + sx;
                        int y = (box[1] + ty) * scale + sy;
                        int expected = sheet != null ? sheet.getRGB(tx, ty) : background.getRGB(x, y);
                        if (!differsFrom(shot, x, y, expected)) {
                            continue;
                        }
                        if (y < topEdge) {
                            topSum += luminance(shot.getRGB(x, y));
                            topCount++;
                        } else if (y >= bottomEdge) {
                            bottomSum += luminance(shot.getRGB(x, y));
                            bottomCount++;
                        }
                    }
                }
            }
        }
        if (topCount == 0 || bottomCount == 0 || bottomSum <= 0.0) {
            throw new AssertionError("[GUITEST][MOD-275] could not band " + label + " into a top and a "
                    + "bottom (" + topCount + " / " + bottomCount + " px) — the shape is degenerate.");
        }
        return new IconShape(maxX - minX + 1, maxY - minY + 1,
                (topSum / topCount) / (bottomSum / bottomCount));
    }

    /** Rec. 709 luminance of an ARGB pixel. */
    private static double luminance(int argb) {
        return PixelMath.luminance(argb);
    }

    /** True when the framebuffer pixel is a different colour than {@code expected} (same slack as
     * {@link #differsAt}, so driver dithering never registers as product). */
    private static boolean differsFrom(BufferedImage image, int x, int y, int expected) {
        return Tolerance.CHANNEL_24.differs(image.getRGB(x, y), expected);
    }

    /** Closes any open screen, puts {@code held} in the selected hotbar slot and shoots the world. */
    private static Path shootBlueprintInHand(ClientGameTestContext context, String name,
                                             ItemStack held) {
        LOG.info("[GUITEST][MOD-275] in hand {} ({})", name, held.getItem());
        context.runOnClient(mc -> {
            mc.setScreenAndShow(null);
            mc.player.getInventory().setSelectedSlot(0);
            mc.player.getInventory().setItem(0, held.copy());
        });
        // Swapping the held item plays the equip animation; wait it out or the frames differ for a
        // reason that has nothing to do with the icon.
        context.waitTicks(20);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * Puts three stacks into the first three player-inventory slots of an open Assembler window and
     * shoots it. The slots are found by their container rather than by index: the Assembler appends its
     * ghost-grid slots after the base menu's groups, so the player rows do not sit at a fixed offset.
     */
    private static Path shootBlueprintIcons(ClientGameTestContext context, String name,
                                            ItemStack left, ItemStack middle, ItemStack right) {
        LOG.info("[GUITEST][MOD-275] icon row {} ({} | {} | {})", name,
                left.getItem(), middle.getItem(), right.getItem());
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.ASSEMBLER_MENU.get(), mc, 0, Component.literal("Assembler"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(0, 12000, 0, 40);
                menu.injectTestChannel(4, -1);
                menu.injectTestChannel(5, dev.alaindustrial.block.entity.AssemblerBlockEntity
                        .AssemblerStatus.NO_BLUEPRINT.ordinal());
                java.util.List<net.minecraft.world.inventory.Slot> player = menu.slots.stream()
                        .filter(s -> s.container instanceof net.minecraft.world.entity.player.Inventory)
                        .toList();
                if (player.size() < 3) {
                    throw new AssertionError("[GUITEST][MOD-275] the Assembler menu exposes only "
                            + player.size() + " player-inventory slots — the icon stand needs three.");
                }
                player.get(0).set(left.copy());
                player.get(1).set(middle.copy());
                player.get(2).set(right.copy());
                // Remember where the three icons landed, so a gate can say "inside the frame" in pixels.
                // leftPos/topPos are protected in 26.2 — read through the accessor the mod already has.
                var pos = (dev.alaindustrial.mixin.client.AbstractContainerScreenAccessor) acs;
                int guiLeft = pos.alaindustrial$getLeftPos();
                int guiTop = pos.alaindustrial$getTopPos();
                iconSlotBoxes = new int[][] {
                        {guiLeft + player.get(0).x, guiTop + player.get(0).y},
                        {guiLeft + player.get(1).x, guiTop + player.get(1).y},
                        {guiLeft + player.get(2).x, guiTop + player.get(2).y},
                };
                iconGuiScaledWidth = mc.getWindow().getGuiScaledWidth();
            }
        });
        context.waitTicks(5);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-275] screenshot {} -> {}", name, path.toAbsolutePath());
        return path;
    }

    /**
     * Opens the screen bound to {@code type} in {@link MenuScreens} (same code path as the real
     * game) and takes a screenshot. If no screen is registered for this type, {@link
     * MenuScreens#create} does nothing and the screenshot shows an empty world — a clear visual
     * signal that the binding in {@link dev.alaindustrial.IndustrializationClient} is missing.
     */
    private static void shootMenu(ClientGameTestContext context, String name,
                                  MenuType<?> type, String displayName) {
        LOG.info("[GUITEST] opening {}", name);
        context.runOnClient(mc -> MenuScreens.create(type, mc, 0, Component.literal(displayName)));
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * MOD-080: a machine screen with the upgrade panel expanded (gear tab clicked) and a mute chip in
     * the active slot. Confirms in one frame that the gear tab draws, the cross panel blits beside the
     * GUI, the locked slots read as dimmed, and a chip renders in the active slot.
     */
    private static void shootMenuWithPanelOpen(ClientGameTestContext context, String name,
                                               MenuType<?> type, String displayName, int energy, int capacity,
                                               int dragDX, int dragDY) {
        LOG.info("[GUITEST][MOD-080] opening {} (upgrade panel open, drag {},{})", name, dragDX, dragDY);
        context.runOnClient(mc -> {
            // The screen reads the docked/dragged offset from AlaClientConfig in init(); set it before
            // opening, then reset so it does not leak into later shots.
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDX = dragDX;
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDY = dragDY;
            MenuScreens.create(type, mc, 0, Component.literal(displayName));
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDX = 0;
            dev.alaindustrial.client.AlaClientConfig.upgradePanelDY = 0;
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                menu.injectTestData(energy, capacity, 0, 0);
                menu.togglePanel();
                for (net.minecraft.world.inventory.Slot s : menu.slots) {
                    if (s instanceof MachineMenu.UpgradeSlot) {
                        s.set(new ItemStack(dev.alaindustrial.registry.ModContent.MUTE_CHIP.get()));
                        break;
                    }
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-080] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    /**
     * Opens the Solar Panel screen and injects all six ContainerData channels so the screenshot
     * shows the requested visual state. Covers: energy bar fill, mode square colour (yellow/blue),
     * sun-active dot, and evolution bar.
     *
     * @param energy         stored EU (0..capacity)
     * @param capacity       max EU buffer (use 8000 for LV solar)
     * @param production     EU/t being produced (shown in the production-rate channel)
     * @param mode           sky mode: 0=night, 1=day, 2=weather, 3=partial
     * @param evolveProgress chip ticks accumulated (0..evolveMax)
     * @param evolveMax      chip ticks needed to evolve (Config.solarEvolveTicks, 33600 default)
     */
    private static void shootSolarPanel(ClientGameTestContext context, String name,
                                        int energy, int capacity, int production, int mode,
                                        int evolveProgress, int evolveMax) {
        shootSolarPanel(context, name, energy, capacity, production, mode, evolveProgress, evolveMax, null);
    }

    /**
     * Variant of {@link #shootSolarPanel} that also places {@code chipItem} into the evolution-chip
     * slot (slot 0). Pass {@link ModItems#ALIGNMENT_CHIP_DAY} for a yellow evo bar or
     * {@link ModItems#ALIGNMENT_CHIP_NIGHT} for a blue evo bar. Pass {@code null} to leave the slot
     * empty (same as the no-chip overload).
     *
     * <p>The chip is injected directly into the client-side {@code SimpleContainer} that backs the
     * slot — no server round-trip needed; the rendering reads the slot item synchronously.
     */
    private static void shootSolarPanel(ClientGameTestContext context, String name,
                                        int energy, int capacity, int production, int mode,
                                        int evolveProgress, int evolveMax, Item chipItem) {
        LOG.info("[GUITEST] solar_panel {} (E={}/{} prod={} mode={} evo={}/{} chip={})",
                name, energy, capacity, production, mode, evolveProgress, evolveMax,
                chipItem != null ? chipItem.getDescriptionId() : "none");
        context.runOnClient(mc -> {
            MenuScreens.create(ModContent.SOLAR_PANEL_MENU.get(), mc, 0, Component.literal("Solar Panel"));
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof SolarPanelMenu menu) {
                menu.injectSolarTestData(energy, capacity, production, mode, evolveProgress, evolveMax);
                if (chipItem != null) {
                    // Directly mutate the client-side SimpleContainer that backs slot 0.
                    menu.getSlot(0).container.setItem(0, new ItemStack(chipItem));
                }
            }
        });
        awaitMenuScreen(context);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Water mill — the GUI status row (MOD-354)
    // ────────────────────────────────────────────────────────────────────────────────

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
     * and still an order of magnitude above the dithering floor {@link #differsAt} already absorbs.
     */
    private static final int MIN_GLYPH_DELTA = 20;

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
     * "a file appeared" (see {@link #takeCleanScreenshot}). Six frames are compared inside the status row
     * and nowhere else, against a noise floor measured from two identical frames:
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
    private static void checkWaterMillStatusRow(ClientGameTestContext context) {
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

    private static void configureVisualTestClient(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule announceAdvancements false");
        server.runCommand("gamerule sendCommandFeedback false");
        server.runCommand("gamerule commandBlockOutput false");
        server.runCommand("gamerule fallDamage false");
        server.runCommand("gamerule doImmediateRespawn true");

        context.runOnClient(mc -> {
            mc.options.inGameNotification().set(false);
            mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
            mc.options.guiScale().set(2);
            mc.getWindow().setWindowed(1280, 720);
            mc.getWindow().setGuiScale(mc.getWindow().calculateScale(2, mc.isEnforceUnicode()));
            mc.resizeGui();
            mc.gui.toastManager().clear();
        });
        context.waitTicks(3);
    }

    /**
     * Waits until the menu screen just created is actually on screen and has had a tick to lay itself
     * out (MOD-362).
     *
     * <p>Replaces the {@code waitTicks(5)} that used to follow every {@code MenuScreens.create}: five
     * ticks was a guess, and on a slower machine — a CI runner on a software rasteriser, say — the
     * capture could happen before the screen finished drawing. The result would not be a failure; it
     * would be a screenshot of the wrong thing, in a green run. Waiting on the condition is both
     * correct and faster, because a ready screen no longer costs the full five ticks.
     */
    private static void awaitMenuScreen(ClientGameTestContext context) {
        context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        // One tick after the screen exists: menu data injected in the same runOnClient block is applied
        // to the widgets on the next layout pass, and the frame is only meaningful after that.
        context.waitTicks(1);
    }

    /**
     * Captures one frame through the shared recorder (MOD-362).
     *
     * <p>This used to hold its own copy of the "the file exists and is bigger than 2 KiB" gate and
     * nothing else. Routing every frame through {@link ShotRecorder} gives all 148 of them three things
     * they did not have: the missing-texture gate (a machine rendering as a pink cube used to pass),
     * an entry in {@code shots-manifest.json}, and a duplicate-name check.
     *
     * <p>Round 3 added the fourth: the frame's group, its {@code R-*} rule and its reviewer caption now
     * come from {@link dev.alaindustrial.visual.ShotFamilies}, keyed on the name. Until then every frame
     * captured through here went in with {@code null, List.of(), ""} — 148 of the 236 frames in the
     * 2026-08-11 nightly had no rule and no caption, which is a picture, not a check.
     */
    private static java.nio.file.Path takeCleanScreenshot(ClientGameTestContext context, String name) {
        return ShotRecorder.capture(name);
    }
}
