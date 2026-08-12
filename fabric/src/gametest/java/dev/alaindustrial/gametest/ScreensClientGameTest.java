package dev.alaindustrial.gametest;

import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.gametest.visual.MenuState;
import dev.alaindustrial.visual.ShotGroup;
import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.menu.MachineMenu;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every machine and chest screen, opened the way a player opens it.
 *
 * <p>Two gaps, both found by the MOD-362 audit, are closed here.
 *
 * <p><b>Coverage.</b> 17 of the 31 menus in {@code ContentManifest.MENUS} had never been photographed —
 * half the mod's screens shipped with no visual check at all. Every menu now has at least one frame,
 * and {@code docs/tools/menu_shot_parity_check.py} keeps it that way.
 *
 * <p><b>Honesty.</b> The existing suite builds its screens by calling {@code MenuScreens.create} and
 * pushing numbers straight into the menu's {@code ContainerData}. That proves the screen can draw
 * "50 %"; it proves nothing about whether right-clicking the block opens that screen, whether the menu
 * is registered for it, or whether the server ever sends those numbers. Here the player walks up and
 * right-clicks a real block, and the first frame of every screen shows what the server actually sent —
 * {@link SCREENS} is captured through the real path (R-GUI-01), and only the extra states after it fall
 * back to injection, because driving a machine to 50 % charge for a screenshot costs a minute of ticks.
 *
 * <p>Frames land in {@code build/run/clientGameTest/screenshots/} with a {@code shots-manifest.json}
 * beside them; see {@link ShotRecorder}.
 *
 */
public class ScreensClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Platform floor. The rig is a straight line so one camera height works for every block. */
    private static final int FLOOR_Y = 99;
    private static final int BLOCK_Y = FLOOR_Y + 1;
    private static final int RIG_Z = 2;
    private static final int STAND_Z = RIG_Z + 2;
    private static final int FIRST_X = 4;
    /** Blocks stand 3 apart: far enough that the wind mills' 2x2 rotor discs never overlap. */
    private static final int STEP_X = 3;

    /**
     * One screen to photograph: the menu's registry id (what the parity gate reads), the block that
     * opens it, and the display name used in log lines.
     *
     * <p>The order is the order of {@code ContentManifest.MENUS}, so a reviewer comparing the two lists
     * reads them top to bottom.
     */
    private record Screen(String menuId, String blockId, String label) {
    }

    /**
     * The 17 menus that had no frame before MOD-362, plus {@code teleporter_station} whose block is
     * registered under a different id than its menu ({@code teleporter}).
     *
     * <p>{@code teleporter_remote} is deliberately absent: it is opened by holding an item, not by a
     * block, and it owns no slots — it is covered separately by {@link #captureRemoteScreen}.
     */
    private static final List<Screen> SCREENS = List.of(
            new Screen("polymerizer", "polymerizer", "Polymerizer"),
            // MOD-251/MOD-388: a multiblock. Only the FORMED base opens the menu, so the rig raises
            // the whole tower — see placeScreenBlock.
            new Screen("distillation_column", "distillation_column", "Distillation Column"),
            new Screen("vulcanizer", "vulcanizer", "Vulcanizer"),
            new Screen("canning_machine", "canning_machine", "Canning Machine"),
            new Screen("alloy_smelter", "alloy_smelter", "Alloy Smelter"),
            new Screen("galvanic_bath", "galvanic_bath", "Galvanic Bath"),
            new Screen("cesu", "cesu", "CESU"),
            // MOD-393: the condenser's readout IS its mechanic (which tier now, how far to the
            // next), so the frame is what guards those two lines against silently vanishing.
            new Screen("energy_condenser", "energy_condenser", "Energy Condenser"),
            new Screen("teleporter_station", "teleporter", "Teleporter Station"),
            // Already had frames before MOD-362, but only under the historical name gui_geothermal_*
            // and only through data injection. Listed here so it is opened the real way like the rest.
            new Screen("geothermal_generator", "geothermal_generator", "Geothermal Generator"),
            new Screen("daylight_solar_panel", "daylight_solar_panel", "Daylight Solar Panel"),
            new Screen("pump", "pump", "Pump"),
            new Screen("garden_drone_station", "garden_drone_station", "Garden Drone Station"),
            new Screen("water_mill", "water_mill", "Water Mill"),
            new Screen("wind_mill", "wind_mill", "Wind Mill"),
            new Screen("high_altitude_wind_mill", "high_altitude_wind_mill", "High Altitude Wind Mill"),
            new Screen("storm_wind_mill", "storm_wind_mill", "Storm Wind Mill"),
            new Screen("iron_chest", "iron_chest", "Iron Chest"),
            new Screen("silver_chest", "silver_chest", "Silver Chest"),
            new Screen("gold_chest", "gold_chest", "Gold Chest"),
            // MOD-391: the double chest's shared 6-row scrolling window. The stand is a PAIR of iron
            // chests (see placeScreenBlock) and the click lands on the right half — the menu joins
            // both, so the frame shows the scrollbar and the per-tier "Double Iron Chest" title.
            new Screen("double_chest", "iron_chest", "Double Iron Chest"));

    /**
     * Screens re-shot under a long locale. Russian labels run noticeably longer than English ones, so a
     * panel that only ever fits in {@code en_us} looks fine in every existing frame and clips in the
     * language half the player base reads.
     */
    private static final List<String> LOCALE_SWEEP =
            List.of("alloy_smelter", "garden_drone_station", "teleporter_station", "gold_chest");

    private static final String LONG_LOCALE = "ru_ru";

    /**
     * Vanilla key used to detect that a language switch has really landed. "Done" / its translation differ in
     * every locale the mod ships, and it is vanilla, so it cannot disappear when our own lang files
     * are edited.
     */
    private static final String LOCALE_PROBE_KEY = "gui.done";

    /**
     * @implements R-GUI-01 - every registered menu opens through a real interaction with its block
     * @covers R-GUI-01, R-GUI-03, R-VIS-05
     */
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                // Same generator settings on every machine — the precondition for comparing a frame
                // against anything other than itself.
                .setUseConsistentSettings(true)
                .create()) {

            VisualWorld.quarantine(context, singleplayer);
            ShotRecorder.begin(context);
            singleplayer.getClientLevel().waitForChunksRender();

            buildRig(context, singleplayer);
            captureEveryScreen(context, singleplayer);
            captureRemoteScreen(context, singleplayer);
            captureLocaleSweep(context, singleplayer);
            captureItemRenderContexts(context, singleplayer);

            ShotRecorder.finish();
            assertEveryScreenPhotographed();
        }
    }

    /**
     * Every catalogued screen must have left a frame behind — checked against what the recorder really
     * captured, not against the catalogue itself (MOD-388).
     *
     * <p>The gap this closes is not "a menu has no catalogue entry" — {@code menu_shot_parity_check.py}
     * already fails the build on that, and it fails it from a source file, without a client. The gap is
     * the other half: a catalogue entry is a promise to photograph something, and nothing checked the
     * promise was kept. When the Distillation Column stopped opening, the run died on screen 2 of 18 and
     * the other 16 went unphotographed — the declaration gate stayed green throughout, because every
     * frame was still declared.
     *
     * <p>Runs AFTER {@code finish()} on purpose: a partial run's manifest is exactly the triage material
     * somebody needs, and it must be on disk before this throws.
     *
     * <p>What is looked for is THIS lane's own frame — {@code gui_<menu>_opened}, the one
     * {@link #captureEveryScreen} takes right after the block interaction — not "any frame carrying
     * this menu id". Several of these menus are also photographed by the wind/water lane earlier in the
     * same process ({@code water_mill} has seven such frames), so the looser question would answer
     * "photographed" for a screen this lane never got to.
     */
    private static void assertEveryScreenPhotographed() {
        Set<String> captured = ShotRecorder.capturedNames();
        List<String> missing = SCREENS.stream()
                .map(Screen::menuId)
                .filter(menuId -> !captured.contains("gui_" + menuId + "_opened"))
                .toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("[SCREENS] " + missing.size() + " of " + SCREENS.size()
                    + " catalogued screens were never photographed: " + String.join(", ", missing)
                    + ". The catalogue promises a frame per entry; these entries did not deliver one — "
                    + "either the loop stopped early, or a capture was removed without its catalogue "
                    + "entry. Frames that WERE taken are in build/run/clientGameTest/screenshots/.");
        }
        LOG.info("[SCREENS] all {} catalogued screens photographed", SCREENS.size());
    }

    /** Clears the arena and puts one block per screen in a straight, evenly spaced row. */
    private static void buildRig(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        int lastX = FIRST_X + STEP_X * (SCREENS.size() - 1);

        server.runCommand("gamemode creative @p");
        // An empty hand is a precondition, not tidiness: right-clicking a block while holding an item
        // runs the ITEM's use handler first, so a stray stack would place a block instead of opening
        // the screen and the wait would time out on a scene that looks fine in the log.
        server.runCommand("clear @p");
        server.runCommand("fill " + (FIRST_X - 2) + " " + FLOOR_Y + " " + (RIG_Z - 2) + " "
                + (lastX + 2) + " " + FLOOR_Y + " " + (STAND_Z + 2) + " minecraft:smooth_stone");
        server.runCommand("fill " + (FIRST_X - 2) + " " + BLOCK_Y + " " + (RIG_Z - 2) + " "
                + (lastX + 2) + " " + (BLOCK_Y + 4) + " " + (STAND_Z + 2) + " minecraft:air");

        for (int i = 0; i < SCREENS.size(); i++) {
            placeScreenBlock(server, SCREENS.get(i), xOf(i));
        }
        server.runCommand("tp @p " + xOf(0) + ".5 " + BLOCK_Y + " " + STAND_Z + ".5 180 0");
        singleplayer.getClientLevel().waitForChunksRender();

        // The one block in the rig that can stand there looking finished and still be the wrong
        // thing: a Distillation Column blank is the same block id as the tower's base, so waiting on
        // the id alone (as captureEveryScreen does) cannot tell them apart. The segment above it only
        // exists when the tower is really assembled, which is why THAT is what is checked — a failure
        // here names the cause instead of surfacing as a menu timeout two steps later (MOD-388).
        VisualWorld.awaitBlockOnClient(context,
                new BlockPos(xOf(indexOf("distillation_column")), BLOCK_Y + 2, RIG_Z),
                "alaindustrial:distillation_column_top");

        LOG.info("[SCREENS] rig built: {} blocks from x={} to x={}", SCREENS.size(), FIRST_X, lastX);
    }

    /**
     * Puts the block that opens one screen at {@code x} — the whole multiblock where the screen
     * belongs to one.
     *
     * <p><b>Why this is not a plain {@code setblock} for every entry (MOD-388).</b> The Distillation
     * Column is a hand-built multiblock (MOD-251 round 3): a lone {@code distillation_column} is an
     * inert <i>blank</i> ({@code formed=false}) that deliberately opens no menu — it shows a
     * "stack three of these" hint instead. Its tower self-assembles in {@code setPlacedBy}, which
     * {@code /setblock} never calls, so the rig used to stand a permanent blank here and the run died
     * on a 10-second timeout at the SECOND screen of eighteen — every screen after it went
     * unphotographed. The tower is raised through the block's own {@code placeTower} helper (the one
     * the demo stand and the server gametests use) rather than three hand-written {@code setblock}s,
     * so a change to the tower's layout cannot silently desync this rig again.
     */
    private static void placeScreenBlock(TestServerContext server, Screen screen, int x) {
        if (screen.blockId().equals("distillation_column")) {
            server.runOnServer(mc ->
                    DistillationColumnBlock.placeTower(mc.overworld(), new BlockPos(x, BLOCK_Y, RIG_Z)));
            return;
        }
        if (screen.menuId().equals("double_chest")) {
            // MOD-391: the double window only exists over a PAIR — the clicked (right) half at x, its
            // LEFT partner one block east. Explicit halves, because /setblock never runs placement.
            server.runCommand("setblock " + x + " " + BLOCK_Y + " " + RIG_Z
                    + " alaindustrial:iron_chest[facing=south,type=right]");
            server.runCommand("setblock " + (x + 1) + " " + BLOCK_Y + " " + RIG_Z
                    + " alaindustrial:iron_chest[facing=south,type=left]");
            return;
        }
        server.runCommand("setblock " + x + " " + BLOCK_Y + " " + RIG_Z
                + " alaindustrial:" + screen.blockId());
    }

    /**
     * For every screen: walk up, right-click, wait for the screen to actually appear, photograph it,
     * then photograph the extra states.
     */
    private static void captureEveryScreen(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        for (int i = 0; i < SCREENS.size(); i++) {
            Screen screen = SCREENS.get(i);
            BlockPos pos = new BlockPos(xOf(i), BLOCK_Y, RIG_Z);

            server.runCommand("tp @p " + xOf(i) + ".5 " + BLOCK_Y + " " + STAND_Z + ".5 180 0");
            singleplayer.getClientLevel().waitForChunksRender();
            VisualWorld.awaitBlockOnClient(context, pos, "alaindustrial:" + screen.blockId());

            openByRightClick(context, pos, screen);

            ShotRecorder.captureScreen(screen.menuId(), "opened",
                    ShotRecorder.rules("R-GUI-01", "R-GUI-03"),
                    "Opened by a real block interaction: title, slots and readouts came from the server - "
                            + "check that the whole window is in frame and no label is clipped");

            captureMachineStates(context, screen);
            VisualWorld.awaitNoScreen(context);
        }
    }

    /**
     * Right-clicks the block and waits for its screen.
     *
     * <p>{@code lookAt} + {@code pressMouse(1)} is the player's own path: it exercises block
     * interaction, the server's menu-open packet and the client's screen registration in one go. None
     * of that is touched when a test calls {@code MenuScreens.create} directly.
     */
    private static void openByRightClick(ClientGameTestContext context, BlockPos pos, Screen screen) {
        // Aim first: the interaction below is dispatched at a specific block, but a correctly aimed
        // camera is also what makes the resulting frame show the block the screen belongs to.
        context.getInput().lookAt(pos);
        context.waitTicks(2);

        // Why not TestInput.pressMouse(1): measured, it opens the FIRST screen and then silently does
        // nothing for every screen after it — the run dies on a timeout with the rig visibly correct.
        // Rather than keep guessing at GLFW-level state, the interaction is dispatched one layer down,
        // where Minecraft itself dispatches it when the right mouse button is released. This is still
        // the honest path: useItemOn sends the ServerboundUseItemOnPacket, the SERVER decides to open a
        // menu, and the client receives a real menu-open packet with real synced data. What is skipped
        // is only the mouse-button plumbing, which is Minecraft's code, not ours.
        context.runOnClient(mc -> {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        });
        VisualWorld.awaitContainerScreen(context, screen.label() + " (" + screen.menuId() + ")");
        LOG.info("[SCREENS] {} opened by block interaction at {}", screen.menuId(), pos);
    }

    /**
     * Photographs the charge/progress states a player actually sees.
     *
     * <p>Only for menus that extend {@link MachineMenu}: chests and the teleporter station carry no
     * energy or progress to inject, so their single opened frame is the whole story.
     */
    private static void captureMachineStates(ClientGameTestContext context, Screen screen) {
        boolean isMachine = context.computeOnClient(mc ->
                mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                        && acs.getMenu() instanceof MachineMenu);
        if (!isMachine) {
            return;
        }

        // 4000 EU is the shipped machine buffer; 200 ticks is a representative job length. Exact values
        // do not matter here — what matters is that empty, half and full render differently.
        final int capacity = 4000;
        final int duration = 200;

        captureInjectedState(context, screen, "empty", MenuState.empty(capacity),
                "Idle machine: the energy bar is empty with no artefacts, the progress bar has not started");
        captureInjectedState(context, screen, "half", MenuState.working(capacity, duration, 0.5),
                "Half charge, half progress: both bars sit around the middle of their tracks");
        captureInjectedState(context, screen, "full", MenuState.full(capacity, duration),
                "Everything at maximum: bars are completely full and do NOT overflow their track");
    }

    /**
     * Injects one state, checks the screen really ADOPTED it, and photographs what is truly on screen.
     *
     * <p><b>The defect this closes (MOD-362 round 3).</b> {@code injectTestData} writes into the
     * CLIENT's copy of the menu data. For a machine that is standing still that value survives — the
     * server only sends an update when its own value changes. For a machine that produces on its own —
     * a solar panel at noon, the three wind mills — the server sends an update every tick and
     * overwrites the injection before the shutter opens. The frame was still captured, still named
     * {@code gui_wind_mill_empty}, and still filed in the manifest exactly like an honest one. A
     * reviewer comparing it against yesterday's would be comparing two different unknown states.
     *
     * <p>So the state is now verified rather than assumed. If the menu holds the requested values, the
     * frame says {@code injected}. If the machine overwrites them, that is not an error — it is a live
     * machine, which is a perfectly good thing to photograph — but the frame says {@code server-driven}
     * and carries the numbers that were actually on screen. Nothing is passed off as something it is not.
     */
    private static void captureInjectedState(ClientGameTestContext context, Screen screen,
            String stateName, MenuState requested, String checks) {
        context.runOnClient(mc -> {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                requested.applyTo(menu);
            }
        });
        // One tick for the widgets to lay out against the new numbers, which is what the frame shows.
        context.waitTicks(2);

        int[] actual = readEnergy(context);
        boolean held = actual[0] == requested.energy() && actual[1] == requested.capacity();
        String note = held
                ? checks
                : checks + " [server-driven: this machine produces on its own, so the requested "
                        + requested.energy() + "/" + requested.capacity() + " EU did not survive - the "
                        + "frame shows the real " + actual[0] + "/" + actual[1] + " EU]";

        ShotRecorder.captureScreen(screen.menuId(), stateName, ShotRecorder.rules("R-GUI-03"), note);
        ShotRecorder.state("gui_" + screen.menuId() + "_" + stateName, held ? "injected" : "server-driven");
        if (!held) {
            LOG.info("[SCREENS] {} {}: injection overwritten by the server, captured the real state "
                    + "{}/{} EU instead of {}/{}", screen.menuId(), stateName, actual[0], actual[1],
                    requested.energy(), requested.capacity());
        }
    }

    /** The energy/capacity pair the open machine screen is really showing, or {@code -1/-1}. */
    private static int[] readEnergy(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof MachineMenu menu) {
                return new int[] {menu.getEnergy(), menu.getCapacity()};
            }
            return new int[] {-1, -1};
        });
    }

    /**
     * The teleporter remote: the one menu with no block behind it. Opened by holding the item and
     * right-clicking, which is exactly how a player opens it.
     */
    private static void captureRemoteScreen(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        VisualWorld.awaitNoScreen(context);

        server.runCommand("clear @p");
        server.runCommand("give @p alaindustrial:teleporter_remote");
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        context.waitTicks(5);

        // Same reasoning as openByRightClick: dispatch where Minecraft dispatches a right-click with an
        // item in hand. useItem sends ServerboundUseItemPacket; the item's own use handler opens the screen.
        context.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        try {
            context.waitFor(mc -> mc.gui.screen() != null);
        } catch (RuntimeException e) {
            throw new AssertionError("[SCREENS] the teleporter remote did not open its screen on "
                    + "right-click — the item's use handler or its screen registration is broken", e);
        }
        ShotRecorder.capture("gui_teleporter_remote_opened", ShotGroup.GUI, "teleporter_remote",
                ShotRecorder.rules("R-GUI-01"),
                "Remote opened from the hand: the (empty) point list, the buttons and the name field are present");
        VisualWorld.awaitNoScreen(context);
    }

    /**
     * Re-shoots a few screens under a long locale.
     *
     * <p>Switching language reloads the resource packs, which is slow — so this is a sample, not a
     * sweep of all 31 screens. The four chosen carry the longest labels in the mod: two status lines,
     * a multi-slot station and the tallest chest.
     */
    private static void captureLocaleSweep(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // The remote-screen capture above left the teleporter remote in hand; holding it would make
        // every right-click below open the remote instead of the block's own screen.
        server.runCommand("clear @p");
        String original = context.computeOnClient(mc -> mc.getLanguageManager().getSelected());

        setLocale(context, LONG_LOCALE);
        ShotRecorder.locale(LONG_LOCALE);

        for (String menuId : LOCALE_SWEEP) {
            int index = indexOf(menuId);
            BlockPos pos = new BlockPos(xOf(index), BLOCK_Y, RIG_Z);
            server.runCommand("tp @p " + xOf(index) + ".5 " + BLOCK_Y + " " + STAND_Z + ".5 180 0");
            singleplayer.getClientLevel().waitForChunksRender();

            openByRightClick(context, pos, SCREENS.get(index));
            ShotRecorder.capture("locale_" + LONG_LOCALE + "_" + menuId, ShotGroup.LOCALE, menuId,
                    ShotRecorder.rules("R-GUI-01"),
                    "The same screen under a long locale: labels are NOT clipped, do not overlap the slots and do not "
                            + "spill outside the window frame");
            VisualWorld.awaitNoScreen(context);
        }

        setLocale(context, original);
        ShotRecorder.locale(original);
    }

    /**
     * Switches the client language and waits until translations are actually live.
     *
     * <p><b>Never {@code join()} the reload future here.</b> Measured the hard way: {@code runOnClient}
     * runs on the render thread, and the resource reload needs that same thread to make progress — so
     * joining it deadlocks the client outright (black window, "not responding", log frozen mid
     * font-load). The reload is kicked off and the thread is released; readiness is then observed the
     * only way that is really true — a probe key that differs between the two languages actually comes
     * back translated.
     */
    private static void setLocale(ClientGameTestContext context, String code) {
        String before = context.computeOnClient(mc -> Language.getInstance().getOrDefault(LOCALE_PROBE_KEY));
        context.runOnClient(mc -> {
            mc.getLanguageManager().setSelected(code);
            mc.options.languageCode = code;
            mc.reloadResourcePacks();
        });
        try {
            context.waitFor(mc -> code.equals(mc.getLanguageManager().getSelected())
                    && !Language.getInstance().getOrDefault(LOCALE_PROBE_KEY).equals(before));
        } catch (RuntimeException e) {
            throw new AssertionError("[SCREENS] switching the client language to " + code
                    + " never took effect — the resource reload did not finish, or " + LOCALE_PROBE_KEY
                    + " reads the same in both languages and can no longer serve as a probe", e);
        }
        LOG.info("[SCREENS] locale switched to {} (probe: {})", code,
                context.computeOnClient(mc -> Language.getInstance().getOrDefault(LOCALE_PROBE_KEY)));
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Item render contexts (R-VIS-05)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * One item per model family, not one per registry entry: the point is to cover the ways a model can
     * be built, and 169 near-identical icons would only cost minutes.
     */
    private static final List<String[]> ITEM_SAMPLES = List.of(
            new String[] {"electric_drill", "handheld tool: held by the grip, not by the bit"},
            new String[] {"forge_hammer", "handheld hammer: head up, handle in the fist"},
            new String[] {"solar_panel", "block item: a 3D block in hand and on the ground, not a flat card"},
            new String[] {"rubber", "flat item: the simplest model there is, catches atlas breakage"});

    /**
     * The same item, photographed in every context Minecraft renders it in.
     *
     * <p>An item model is not one picture: vanilla draws it through separate display transforms — the
     * flat icon in a container, the held model in first and third person, and the entity on the ground —
     * and each can break on its own. The classic case in this mod is the {@code item/handheld} grip: an
     * item that looks perfect in the inventory can be held by the wrong end, and the inventory frame
     * that "covers" it proves nothing about that. Before MOD-362 items were photographed almost only as
     * GUI icons.
     *
     * <p>Runs inside THIS lane's world on purpose. It started as its own client-gametest class and the
     * third world of a run reliably died with "Timeout loading world" — two worlds per client process is
     * what this setup carries.
     */
    private static void captureItemRenderContexts(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        int standX = FIRST_X;
        int standZ = STAND_Z + 6;

        server.runCommand("fill " + (standX - 3) + " " + FLOOR_Y + " " + (standZ - 3) + " "
                + (standX + 3) + " " + FLOOR_Y + " " + (standZ + 3) + " minecraft:smooth_stone");
        server.runCommand("fill " + (standX - 3) + " " + BLOCK_Y + " " + (standZ - 3) + " "
                + (standX + 3) + " " + (BLOCK_Y + 3) + " " + (standZ + 3) + " minecraft:air");
        server.runCommand("tp @p " + standX + ".5 " + BLOCK_Y + " " + standZ + ".5 0 20");
        singleplayer.getClientLevel().waitForChunksRender();

        for (String[] sample : ITEM_SAMPLES) {
            captureOneItem(context, singleplayer, sample[0], sample[1], standX, standZ);
        }
    }

    private static void captureOneItem(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, String itemId, String why, int standX, int standZ) {
        TestServerContext server = singleplayer.getServer();
        String qualified = "alaindustrial:" + itemId;

        server.runCommand("clear @p");
        server.runCommand("kill @e[type=minecraft:item]");
        server.runCommand("give @p " + qualified);
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        context.waitTicks(5);

        // 1. The flat card in a container slot.
        //
        // Opened with the player's own inventory key, not by constructing InventoryScreen directly:
        // the rig runs in creative, where that key opens CreativeModeInventoryScreen instead, so a
        // `waitFor(screen instanceof InventoryScreen)` waits forever on a screen that will never appear
        // (measured — it is what failed this lane's first run). Survival for the duration of the shot
        // both gets the plain inventory back and makes the frame match what a player actually sees.
        server.runCommand("gamemode survival @p");
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyInventory);
        VisualWorld.awaitContainerScreen(context, "inventory (" + itemId + ")");
        ShotRecorder.capture("item_" + itemId + "_icon", ShotGroup.ITEM, null,
                ShotRecorder.rules("R-VIS-05"),
                "Inventory icon: " + why + " - centred in its slot, not clipped");
        assertIconMatchesTemplate(context, itemId);
        VisualWorld.awaitNoScreen(context);
        server.runCommand("gamemode creative @p");

        // 2. First person: the model as the player sees it while playing.
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(10);
        ShotRecorder.capture("item_" + itemId + "_first_person", ShotGroup.ITEM, null,
                ShotRecorder.rules("R-VIS-05"), "In hand, first person: " + why);

        // 3. Third person: the same model on the body, a different transform entirely.
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(10);
        ShotRecorder.capture("item_" + itemId + "_third_person", ShotGroup.ITEM, null,
                ShotRecorder.rules("R-VIS-05"), "In hand, third person: " + why);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(5);

        // 4. On the ground: the item entity, which is what a player sees after breaking a block. A model
        //    that only ever looked right in the GUI fails visibly here.
        server.runCommand("clear @p");
        server.runCommand("summon minecraft:item " + standX + ".5 " + (BLOCK_Y + 1) + " " + (standZ - 2)
                + ".5 {Item:{id:\"" + qualified + "\",count:1},PickupDelay:32767}");
        context.getInput().lookAt(new BlockPos(standX, BLOCK_Y + 1, standZ - 2));
        context.waitTicks(20);
        ShotRecorder.capture("item_" + itemId + "_ground", ShotGroup.ITEM, null,
                ShotRecorder.rules("R-VIS-05"),
                "Dropped on the ground: " + why + " - the entity must be visible, not invisible or a "
                        + "flat sliver");
        server.runCommand("kill @e[type=minecraft:item]");
        context.waitTicks(3);
        LOG.info("[ITEMS] {} captured in four render contexts", itemId);
    }

    /**
     * Items whose inventory icon is checked against a stored reference sprite, keyed by item id.
     *
     * <p>The reference lives at {@code fabric/src/gametest/resources/templates/<name>.png} and is cut
     * from a real frame by {@code tools/extract_shot_template.py} — cutting one by hand in an image
     * editor is a one-pixel mistake away from a permanently red gate.
     *
     * <p>Deliberately one entry for now. A reference sprite is a commitment: every intentional art
     * change has to re-cut it, so they earn their keep one at a time rather than being generated
     * wholesale for 169 items nobody will maintain.
     */
    // The explicit <String[]> is load-bearing: with a SINGLE array argument, List.of treats it as the
    // varargs array itself and infers List<String>, which does not compile here. It works by accident
    // for the multi-entry lists above and would break them the moment they drop to one entry.
    private static final List<String[]> ICON_TEMPLATES = List.<String[]>of(
            new String[] {"solar_panel", "solar_panel_inventory_icon"});

    /**
     * Asserts the item's icon is really on screen, pixel for pixel.
     *
     * <p>This is the first gate in the suite that compares against a STORED reference rather than
     * against another frame of the same run. Everything else can only answer "did something change
     * between these two shots"; this answers "is the thing that should be there, there" — the question
     * a screenshot was never able to answer on its own.
     *
     * <p>Uses the API's own {@code assertScreenshotContains}, which searches the current frame for the
     * template and returns where it found it. No-op for items without a reference.
     */
    private static void assertIconMatchesTemplate(ClientGameTestContext context, String itemId) {
        for (String[] entry : ICON_TEMPLATES) {
            if (!entry[0].equals(itemId)) {
                continue;
            }
            try {
                Vector2i at = context.assertScreenshotContains(
                        TestScreenshotComparisonOptions.of(entry[1]));
                // The strongest check the suite can make, and the manifest has to say so: this is the
                // only frame in a 236-frame run compared against a reference that outlives the run.
                ShotRecorder.markReferenced("item_" + itemId + "_icon");
                LOG.info("[ITEMS] {} icon matched template {} at {},{}", itemId, entry[1], at.x, at.y);
            } catch (RuntimeException e) {
                throw new AssertionError("[ITEMS] the inventory icon of '" + itemId + "' no longer "
                        + "matches the stored reference '" + entry[1] + "'. Either the item's art or its "
                        + "model changed, or the icon stopped being drawn at all. If the change is "
                        + "intentional, re-cut the reference:\n"
                        + "  python tools/extract_shot_template.py --frame <the item_" + itemId
                        + "_icon frame> --region X Y W H --name " + entry[1], e);
            }
        }
    }

    private static int indexOf(String menuId) {
        for (int i = 0; i < SCREENS.size(); i++) {
            if (SCREENS.get(i).menuId().equals(menuId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no screen '" + menuId + "' in the catalogue — the locale "
                + "sweep names a menu that is not in SCREENS");
    }

    private static int xOf(int index) {
        return FIRST_X + STEP_X * index;
    }
}
