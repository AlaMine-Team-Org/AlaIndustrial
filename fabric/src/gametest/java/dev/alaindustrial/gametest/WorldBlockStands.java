package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The blocks themselves, in the world: the model rig, the six-face survey, idle-vs-active textures,
 * the cable's multipart arms and the worn Energy Pack.
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. Everything here photographs geometry that the
 * chunk builder produces — no block entity renderer is involved, which is what separates these stands
 * from {@link RendererStands} next door.
 *
 * <p>Coverage map (RULES.md → method):
 * <ul>
 *   <li>R-VIS-04 — {@link #checkSixFaceSurvey}      — all 6 block faces visible and correct
 *   <li>R-VIS-01 — {@link #checkActiveIdleTextures} — idle vs active texture change
 *   <li>R-CON-03 — {@link #checkCableConnectivity}  — cable model updates per neighbour
 *   <li>MOD-065  — {@link #checkEnergyPackWorn}     — the data-driven worn model reaches the frame
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class WorldBlockStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private WorldBlockStands() {
    }

    /**
     * Place the machine + cable blocks on a small platform and screenshot them in-world, so the
     * block models / textures (directional fronts, the thin cable, etc.) can be verified visually —
     * not just the GUIs. Uses server commands to build the rig and pose the camera.
     */
    public static void renderBlocksInWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
    public static void checkSixFaceSurvey(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
    public static void checkActiveIdleTextures(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
    public static void checkCableConnectivity(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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

    /**
     * MOD-065: photographs the player wearing the Energy Pack, from behind and from the front. The
     * worn model is data-driven (the item's EQUIPPABLE component points at
     * {@code assets/alaindustrial/equipment/energy_pack.json}, whose layers name the humanoid
     * textures) — nothing in code renders it, so a typo in either file shows up as a player with a
     * bare chest and nothing else. These two frames are the only place that would catch it.
     */
    public static void checkEnergyPackWorn(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
}
