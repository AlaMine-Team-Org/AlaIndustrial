package dev.alaindustrial.gametest;

import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.registry.ModItems;
import dev.alaindustrial.registry.ModMenus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visual regression test suite for AlaIndustrial. Runs a real Minecraft client (headless, no
 * display window) and takes screenshots that a human reviewer checks after each build.
 *
 * <p>Coverage map (RULES.md → test method):
 * <ul>
 *   <li>R-GUI-01   — {@link #shootGuiScreenshots}         — all GUIs open without crash
 *   <li>R-VIS-04   — {@link #checkSixFaceSurvey}          — all 6 block faces visible and correct
 *   <li>R-VIS-01   — {@link #checkActiveIdleTextures}     — idle vs active texture change
 *   <li>R-CON-03   — {@link #checkCableConnectivity}      — cable model updates per neighbour
 *   <li>R-PHY-10   — {@link #checkHitboxes}               — hitbox shape matches block model
 * </ul>
 *
 * <p>Screenshots land in {@code build/run/clientGameTest/screenshots/}.
 * Filenames are prefixed with a sequential index so they sort in test order.
 */
@SuppressWarnings("UnstableApiUsage")
public class GuiClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Pass {@code -Pguionly} to Gradle to skip world-building tests and only shoot GUI screens. */
    private static final boolean GUI_ONLY = System.getProperty("alaindustrial.guionly") != null;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            if (!GUI_ONLY) {
                // ── World render rig ─────────────────────────────────────────────────
                renderBlocksInWorld(context, singleplayer);

                // ── Visual checks (RULES.md) ─────────────────────────────────────────
                checkSixFaceSurvey(context, singleplayer);      // R-VIS-04
                checkActiveIdleTextures(context, singleplayer); // R-VIS-01
                checkCableConnectivity(context, singleplayer);  // R-CON-03
                // R-PHY-10: mc.debugHitboxes removed in MC 26.2; re-enable when API is found.
            }

            // ── GUI screenshots (always runs) ─────────────────────────────────────────
            shootGuiScreenshots(context);
        }
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
            LOG.info("[GUITEST][R-VIS-04] {} -> {}", v[1], context.takeScreenshot(v[1]).toAbsolutePath());
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
            LOG.info("[GUITEST][R-VIS-04] {} -> {}", v[1], context.takeScreenshot(v[1]).toAbsolutePath());
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
        LOG.info("[GUITEST][R-VIS-01] gen_idle -> {}", context.takeScreenshot("vis_gen_idle").toAbsolutePath());

        // Insert 64 coal into slot 0 (fuel slot)
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:coal 64");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] gen_active -> {}", context.takeScreenshot("vis_gen_active").toAbsolutePath());

        // ── Macerator (needs energy + raw ore) ───────────────────────────────────────
        server.runCommand("setblock 80 100 80 alaindustrial:macerator[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][R-VIS-01] mac_idle -> {}", context.takeScreenshot("vis_mac_idle").toAbsolutePath());

        // Inject energy (Team Reborn Energy stores as "energy" Long in NBT) + ore input
        server.runCommand("data merge block 80 100 80 {energy:4000L}");
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:raw_iron 8");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] mac_active -> {}", context.takeScreenshot("vis_mac_active").toAbsolutePath());

        // ── Electric Furnace ──────────────────────────────────────────────────────────
        server.runCommand("setblock 80 100 80 alaindustrial:electric_furnace[facing=south]");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][R-VIS-01] furnace_idle -> {}", context.takeScreenshot("vis_furnace_idle").toAbsolutePath());

        server.runCommand("data merge block 80 100 80 {energy:4000L}");
        server.runCommand("item replace block 80 100 80 container.0 with minecraft:raw_iron 4");
        context.waitTicks(20);
        LOG.info("[GUITEST][R-VIS-01] furnace_active -> {}", context.takeScreenshot("vis_furnace_active").toAbsolutePath());
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
        LOG.info("[GUITEST][R-CON-03] cable_alone -> {}", context.takeScreenshot("con_cable_alone").toAbsolutePath());

        // Add east neighbour (+X)
        server.runCommand("setblock 101 100 100 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_east_arm -> {}", context.takeScreenshot("con_cable_east_arm").toAbsolutePath());

        // Add south neighbour (+Z) → L-corner
        server.runCommand("setblock 100 100 101 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_corner -> {}", context.takeScreenshot("con_cable_corner").toAbsolutePath());

        // Remove east → only south arm remains
        server.runCommand("setblock 101 100 100 minecraft:air");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_south_only -> {}", context.takeScreenshot("con_cable_south_only").toAbsolutePath());

        // Add vertical arm (above)
        server.runCommand("setblock 100 101 100 alaindustrial:copper_cable");
        context.waitTicks(3);
        LOG.info("[GUITEST][R-CON-03] cable_vertical -> {}", context.takeScreenshot("con_cable_vertical").toAbsolutePath());

        // Cleanup — remove helpers so later rigs don't see stray cables
        server.runCommand("setblock 100 100 101 minecraft:air");
        server.runCommand("setblock 100 101 100 minecraft:air");
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Existing: world render rig + GUI screenshots
    // ────────────────────────────────────────────────────────────────────────────────

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
        LOG.info("[GUITEST] world screenshot -> {}", context.takeScreenshot("world_blocks").toAbsolutePath());

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
        LOG.info("[GUITEST] cable screenshot -> {}", context.takeScreenshot("world_cables").toAbsolutePath());

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
        LOG.info("[GUITEST] panel-neighbour -> {}", context.takeScreenshot("panel_neighbour").toAbsolutePath());
        // Top-down on the same seam.
        server.runCommand("tp @p 43 103 42 180 80");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);
        LOG.info("[GUITEST] panel-neighbour top -> {}", context.takeScreenshot("panel_neighbour_top").toAbsolutePath());
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
        shootMenu(context, "gui_moonlit_solar_panel", ModMenus.MOONLIT_SOLAR_PANEL, "Moonlit Solar Panel");

        // ── Electric Furnace — three states ──────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_electric_furnace_empty",
                ModMenus.ELECTRIC_FURNACE, "Electric Furnace",
                0, CAP, 0, BURN);

        // State 2: smelting — 50 % through, energy at 75 %
        shootMenuWithState(context, "gui_electric_furnace_smelting",
                ModMenus.ELECTRIC_FURNACE, "Electric Furnace",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: full — full energy, arrow at max
        shootMenuWithState(context, "gui_electric_furnace_full",
                ModMenus.ELECTRIC_FURNACE, "Electric Furnace",
                CAP, CAP, BURN, BURN);

        // ── BatteryBox — three states ─────────────────────────────────────────────────────
        shootMenuWithState(context, "gui_battery_box_empty",
                ModMenus.BATTERY_BOX, "BatteryBox",
                0, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_half",
                ModMenus.BATTERY_BOX, "BatteryBox",
                CAP / 2, CAP, 0, 0);
        shootMenuWithState(context, "gui_battery_box_full",
                ModMenus.BATTERY_BOX, "BatteryBox",
                CAP, CAP, 0, 0);

        // ── LV Generator — three states ──────────────────────────────────────────────
        // State 1: empty — no fuel, no energy
        shootMenuWithState(context, "gui_generator_empty",
                ModMenus.GENERATOR, "LV Generator",
                0, CAP, 0, BURN);

        // State 2: burning — 50 % fuel remaining, energy building to 50 %
        shootMenuWithState(context, "gui_generator_burning",
                ModMenus.GENERATOR, "LV Generator",
                CAP / 2, CAP, BURN / 2, BURN);

        // State 3: full energy — buffer saturated, no active burn
        shootMenuWithState(context, "gui_generator_full",
                ModMenus.GENERATOR, "LV Generator",
                CAP, CAP, 0, BURN);

        // ── Geothermal Generator — three states ──────────────────────────────────────
        // State 1: empty — no lava in tank, no energy
        shootMenuWithState(context, "gui_geothermal_empty",
                ModMenus.GEOTHERMAL_GENERATOR, "Geothermal Generator",
                0, CAP, 0, TANK);

        // State 2: lava tank ~70 %, energy building (~40 %)
        shootMenuWithState(context, "gui_geothermal_mid",
                ModMenus.GEOTHERMAL_GENERATOR, "Geothermal Generator",
                CAP * 2 / 5, CAP, TANK * 7 / 10, TANK);

        // State 3: full lava tank, full energy buffer
        shootMenuWithState(context, "gui_geothermal_full",
                ModMenus.GEOTHERMAL_GENERATOR, "Geothermal Generator",
                CAP, CAP, TANK, TANK);

        // ── Macerator — three states ─────────────────────────────────────────────────
        // State 1: empty — no input, no energy
        shootMenuWithState(context, "gui_macerator_empty",
                ModMenus.MACERATOR, "Macerator",
                0, CAP, 0, BURN);

        // State 2: processing — 60 % through, moderate energy
        shootMenuWithState(context, "gui_macerator_processing",
                ModMenus.MACERATOR, "Macerator",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, arrow at max
        shootMenuWithState(context, "gui_macerator_full",
                ModMenus.MACERATOR, "Macerator",
                CAP, CAP, BURN, BURN);

        // ── Extractor — three states ─────────────────────────────────────────────
        // State 1: empty — no input, no energy, chevrons dark
        shootMenuWithState(context, "gui_extractor_empty",
                ModMenus.EXTRACTOR, "Extractor",
                0, CAP, 0, BURN);

        // State 2: extracting — 60 % through, energy at 75 % (cyan chevrons mid-fill)
        shootMenuWithState(context, "gui_extractor_processing",
                ModMenus.EXTRACTOR, "Extractor",
                CAP * 3 / 4, CAP, BURN * 3 / 5, BURN);

        // State 3: done — full energy, chevrons fully lit
        shootMenuWithState(context, "gui_extractor_full",
                ModMenus.EXTRACTOR, "Extractor",
                CAP, CAP, BURN, BURN);

        // ── Compressor — three states ────────────────────────────────────────────
        // State 1: idle — no energy, no active compression
        shootMenuWithState(context, "gui_compressor_idle",
                ModMenus.COMPRESSOR, "Compressor",
                0, CAP, 0, BURN);

        // State 2: compressing — 50 % through, energy at 75 % (arrows mid-way toward center)
        shootMenuWithState(context, "gui_compressor_mid",
                ModMenus.COMPRESSOR, "Compressor",
                CAP * 3 / 4, CAP, BURN / 2, BURN);

        // State 3: done — full energy, arrows fully converged at center
        shootMenuWithState(context, "gui_compressor_done",
                ModMenus.COMPRESSOR, "Compressor",
                CAP, CAP, BURN, BURN);
    }

    /**
     * Opens the screen, then immediately injects ContainerData so the GUI renders the requested
     * state (energy fill, flame height, arrow width). Works only for screens backed by
     * {@link MachineMenu}; for other types falls back to the plain empty shot.
     */
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
        java.nio.file.Path path = context.takeScreenshot(name);
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
        java.nio.file.Path path = context.takeScreenshot(name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
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
            MenuScreens.create(ModMenus.SOLAR_PANEL, mc, 0, Component.literal("Solar Panel"));
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
        java.nio.file.Path path = context.takeScreenshot(name);
        LOG.info("[GUITEST] screenshot {} -> {}", name, path.toAbsolutePath());
    }
}
