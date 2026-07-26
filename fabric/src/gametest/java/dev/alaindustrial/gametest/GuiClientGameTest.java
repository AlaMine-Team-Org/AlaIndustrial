package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
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
 * </ul>
 *
 * <p>Screenshots land in {@code build/run/clientGameTest/screenshots/}.
 * Filenames are prefixed with a sequential index so they sort in test order.
 *
 * <p><b>A screenshot on its own asserts nothing.</b> {@link #takeCleanScreenshot} only proves a
 * non-empty PNG was written — a frame missing a whole renderer still passes. Any stand that claims to
 * cover rendering must additionally assert the thing it photographs, the way
 * {@link #checkWaterMillWheel} and {@link #checkWindMillRotor} do; otherwise name it for what it is,
 * a screenshot for a human to look at.
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
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            configureVisualTestClient(context, singleplayer);
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
                // R-PHY-10: mc.debugHitboxes removed in MC 26.2; re-enable when API is found.
            }

            // ── Advancement-tab icon regression (needs the in-world player + server) ───
            shootAdvancementScreens(context, singleplayer);

            // ── GUI screenshots (always runs) ─────────────────────────────────────────
            shootGuiScreenshots(context);
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
                    + ") — the BlockEntityRenderer's geometry is NOT in the captured frame. Compare "
                    + withWheel.getFileName() + " with " + noWheelA.getFileName() + ".");
        }
    }

    /** Pixels whose colour differs by more than 24/255 in any channel. Shared by every pixel gate. */
    private static int differingPixels(Path first, Path second) {
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
                    int pa = a.getRGB(x, y);
                    int pb = b.getRGB(x, y);
                    int dr = Math.abs(((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF));
                    int dg = Math.abs(((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF));
                    int db = Math.abs((pa & 0xFF) - (pb & 0xFF));
                    if (Math.max(dr, Math.max(dg, db)) > 24) {
                        differing++;
                    }
                }
            }
            return differing;
        } catch (IOException e) {
            throw new AssertionError("[GUITEST] could not read screenshots for the pixel gate", e);
        }
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
                    + "is NOT in the captured frame. Compare " + withRotor.getFileName() + " with "
                    + noRotorA.getFileName() + ".");
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
                dev.alaindustrial.item.ItemEnergy.set(pouch, dev.alaindustrial.Config.lvPouchBuffer / 2);
                menu.getSlot(0).container.setItem(0, pouch);
            }
        });
        context.waitTicks(5);
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
                dev.alaindustrial.item.ItemEnergy.set(pack, dev.alaindustrial.Config.energyPackBuffer / 2);
                menu.getSlot(0).container.setItem(0, pack);
            }
        });
        context.waitTicks(5);
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
        context.waitTicks(5);
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
        context.waitTicks(5);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
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
        context.waitTicks(5);
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
                    if (s instanceof MachineMenu.UpgradeSlot up && !up.isLocked()) {
                        s.set(new ItemStack(dev.alaindustrial.registry.ModContent.MUTE_CHIP.get()));
                        break;
                    }
                }
            }
        });
        context.waitTicks(5);
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
        context.waitTicks(5);
        java.nio.file.Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
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

    private static java.nio.file.Path takeCleanScreenshot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> mc.gui.toastManager().clear());
        context.waitTicks(1);
        java.nio.file.Path path = context.takeScreenshot(name);
        // Regression guard: previously this method returned a path that nothing ever asserted on, so
        // the whole L3 suite stayed green even if the screenshot file was never written or came out as
        // a 0-byte / all-black frame (e.g. MenuScreens.create silently no-op'd, the screen closed before
        // capture, the renderer threw inside the framebuffer). Asserting existence + a minimum byte size
        // catches every "screenshot not produced" and "blank frame" failure mode without requiring a
        // pixel-diff baseline. A non-trivial PNG of a real Minecraft frame is a few KB at minimum; a
        // cleared/empty framebuffer compresses to a few hundred bytes. The threshold is deliberately
        // generous so headless-driver variance never flakes a healthy capture.
        if (!java.nio.file.Files.exists(path)) {
            throw new AssertionError("[GUITEST] screenshot '" + name + "' was not written to " + path
                    + " — the capture path is broken (renderer threw, screen never opened, or the headless "
                    + "framebuffer is misconfigured). This used to pass silently; now it fails the L3 suite.");
        }
        try {
            long size = java.nio.file.Files.size(path);
            // 2 KiB: a real GUI/world frame is ≥ several KB compressed; an empty framebuffer PNG is a
            // few hundred bytes. Keeps the gate robust against headless-driver compression variance.
            final long MIN_SCREENSHOT_BYTES = 2 * 1024L;
            if (size < MIN_SCREENSHOT_BYTES) {
                throw new AssertionError("[GUITEST] screenshot '" + name + "' is only " + size
                        + " bytes (< " + MIN_SCREENSHOT_BYTES + ") — likely a blank/cleared framebuffer, "
                        + "not a rendered frame. Capture path: " + path);
            }
            LOG.info("[GUITEST] screenshot {} ({} bytes) OK", name, size);
        } catch (java.io.IOException e) {
            throw new AssertionError("[GUITEST] could not stat screenshot '" + name + "' at " + path, e);
        }
        return path;
    }
}
