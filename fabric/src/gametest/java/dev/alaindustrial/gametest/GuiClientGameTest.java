package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.visual.ShotRecorder;
import dev.alaindustrial.gametest.visual.VisualWorld;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Visual regression suite for AlaIndustrial. Runs a real Minecraft client (headless, no display
 * window) and takes screenshots that a human reviewer checks after each build.
 *
 * <p><b>This class is the lane, not the stands.</b> It builds one world, configures the client,
 * opens and closes the recorder, and calls the stands in order. Every stand lives in its own file
 * (MOD-404) — the monolith this used to be was 3108 lines, so one failing GUI check meant opening
 * all of them, and two worktrees touching the suite conflicted almost by definition.
 *
 * <p><b>Why the stands are not client-gametest entrypoints of their own.</b> Each registered
 * {@code FabricClientGameTest} builds its own world, and this setup carries two worlds per client
 * process: the third reliably dies with "Timeout loading world" (measured — see
 * {@code ScreensClientGameTest.captureItemRenderContexts}). The two entrypoints are spoken for, so
 * splitting the file means plain static holders called from here, not new registrations in
 * {@code fabric.mod.json}.
 *
 * <p>Coverage map (RULES.md → stand):
 * <ul>
 *   <li>R-GUI-01 — {@link MachineGuiStands#shootGuiScreenshots}        — all GUIs open without crash
 *   <li>R-GUI-04 — {@link AdvancementScreenStand#shootAdvancementScreens} — advancement-tab icons render
 *       (not white/offset) under gui context
 *   <li>R-VIS-04 — {@link WorldBlockStands#checkSixFaceSurvey}         — all 6 block faces visible and correct
 *   <li>R-VIS-01 — {@link WorldBlockStands#checkActiveIdleTextures}    — idle vs active texture change
 *   <li>R-CON-03 — {@link WorldBlockStands#checkCableConnectivity}     — cable model updates per neighbour
 *   <li>R-PHY-10 — hitboxes: {@code mc.debugHitboxes} removed in MC 26.2; re-enable when an API is found
 *   <li>MOD-024  — {@link RendererStands#checkWaterMillWheel}          — the water wheel BER draws into the frame
 *   <li>MOD-232  — {@link RendererStands#checkWindMillRotor}           — the wind mill rotor BER draws into the frame
 *   <li>MOD-118  — {@link RendererStands#checkIncubatorDome}           — the incubator's floating item reaches the frame
 *   <li>MOD-546  — {@link RendererStands#checkEnergyCondenserCrystal}  — the condenser crystal draws, and hides below tier I
 *   <li>MOD-424  — {@link RendererStands#checkThermalCentrifugeRotor}  — the centrifuge rotor draws AND turns, and stops
 *       dead when the redstone signal goes away
 *   <li>MOD-354  — {@link WaterMillGuiStand#checkWaterMillStatusRow}   — the water mill's GUI status row draws, and draws
 *       a different label per state
 *   <li>MOD-371  — {@link WindMillGuiStand#checkWindMillStatusRows}   — the same row on all three wind mills; no blank
 *       state exists there, so the anchor is a duplicate frame plus an exhaustive pairwise comparison
 *   <li>MOD-275  — {@link AssemblerGuiStands}                          — the Assembler window: tabs, queue, blueprint preview
 *   <li>MOD-275  — {@link BlueprintIconStands}                         — a recorded blueprint's own icon carries its product
 *   <li>MOD-287  — {@link StorageModuleStands#checkStorageModuleSeams} — storage-module connected textures
 * </ul>
 *
 * <p>Screenshots land in {@code build/run/clientGameTest/screenshots/}.
 * Filenames are prefixed with a sequential index so they sort in test order.
 *
 * <p><b>A screenshot on its own asserts nothing.</b> {@code VisualStandSupport.takeCleanScreenshot}
 * only proves a non-empty PNG was written — a frame missing a whole renderer still passes. Any stand
 * that claims to cover rendering must additionally assert the thing it photographs, the way
 * {@link RendererStands} does; otherwise name it for what it is, a screenshot for a human to look at.
 * The same applies to a GUI: {@link WaterMillGuiStand} shows how to assert a text row — compare it
 * against the state where it is deliberately blank, and against the other states it is supposed to
 * distinguish.
 */
@SuppressWarnings("UnstableApiUsage")
public class GuiClientGameTest implements FabricClientGameTest {

    /** Pass {@code -Pguionly} to Gradle to skip world-building tests and only shoot GUI screens. */
    private static final boolean GUI_ONLY = System.getProperty("alaindustrial.guionly") != null;

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
                WorldBlockStands.renderBlocksInWorld(context, singleplayer);

                // ── Visual checks (RULES.md) ─────────────────────────────────────────
                WorldBlockStands.checkSixFaceSurvey(context, singleplayer);      // R-VIS-04
                WorldBlockStands.checkActiveIdleTextures(context, singleplayer); // R-VIS-01
                WorldBlockStands.checkCableConnectivity(context, singleplayer);  // R-CON-03
                WorldBlockStands.checkEnergyPackWorn(context, singleplayer);     // MOD-065 worn model
                RendererStands.checkWaterMillWheel(context, singleplayer);       // MOD-024 BER visual regression
                RendererStands.checkWindMillRotor(context, singleplayer);        // MOD-232 BER visual regression
                RendererStands.checkIncubatorDome(context, singleplayer);        // MOD-118 BER visual regression
                RendererStands.checkEnergyCondenserCrystal(context, singleplayer);   // MOD-393 BER visual regression
                RendererStands.checkThermalCentrifugeRotor(context, singleplayer); // MOD-424 BER visual regression
                StorageModuleStands.checkStorageModuleSeams(context, singleplayer); // MOD-287 connected textures
                // R-PHY-10: mc.debugHitboxes removed in MC 26.2; re-enable when API is found.
            }

            // ── Advancement-tab icon regression (needs the in-world player + server) ───
            AdvancementScreenStand.shootAdvancementScreens(context, singleplayer);

            // ── GUI screenshots (always runs) ─────────────────────────────────────────
            MachineGuiStands.shootGuiScreenshots(context);

            // ── MOD-053: the same catalogue once in Arabic — R-GUI-15, the RTL layout audit ──
            // Runs after the English frames and restores en_us when done: a lane that left ar_sa
            // on would quietly shoot every later frame (this class's tail and ScreensClientGameTest)
            // in Arabic too.
            RtlGuiStands.shootArabicScreens(context);

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

    private static void configureVisualTestClient(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // GameRules API, not command strings — 26.2 renamed the rule ids, so the camelCase literals
        // this used to dispatch failed to parse and configured nothing (see VisualWorld.quarantine,
        // unified there first in MOD-294).
        server.runOnServer(mc -> {
            GameRules rules = mc.overworld().getGameRules();
            rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, mc);
            rules.set(GameRules.SEND_COMMAND_FEEDBACK, false, mc);
            rules.set(GameRules.COMMAND_BLOCK_OUTPUT, false, mc);
            rules.set(GameRules.FALL_DAMAGE, false, mc);
            rules.set(GameRules.IMMEDIATE_RESPAWN, true, mc);
        });

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
}
