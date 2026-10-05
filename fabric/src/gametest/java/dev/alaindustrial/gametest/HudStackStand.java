package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.hud.TeleportFadeHud;
import dev.alaindustrial.compat.L3Chunks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-706 batch 0 — the stack of the mod's HUD layers on Fabric, photographed with two of them on at
 * once: the teleport fade and the Energy Pack readout.
 *
 * <p>The layers are registered by hand today ({@code HudElementRegistry.addLast} in
 * {@code IndustrializationClient}, in the order root inspection, concentrator assembly, teleport fade,
 * energy pack, electric drill). MOD-706 moves that list into {@code ClientContentManifest.HUD_LAYERS};
 * this frame is the before-and-after witness that the order did not change: the readout, registered
 * after the fade, has to stay legible ON TOP of the darkened world. NeoForge has no client lane, so its
 * order ({@code registerAboveAll}, same sequence) is pinned by the task's research.md instead.
 *
 * <p>The fade is fed straight into the loader-neutral {@link TeleportFadeHud} — the same call both
 * loaders' receivers make — rather than through a real warmup, which would need a teleporter rig and a
 * second dimension of timing. It believes a level for 150 ms and then fades out over about five ticks,
 * so it is fed right before the capture, which itself waits only one tick before it shoots.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HudStackStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Mid-darkness: dark enough to see the fade, light enough to see the world under it. */
    private static final float FADE = 0.6f;

    private HudStackStand() {
    }

    /** Teleport fade + Energy Pack readout in one first-person frame. */
    public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        server.runCommand("fill 4 100 3 14 100 13 minecraft:smooth_stone");
        server.runCommand("gamemode survival @p");
        server.runCommand("item replace entity @p armor.chest with alaindustrial:energy_pack");
        server.runCommand("tp @p 9 101 8 180 0");
        L3Chunks.waitRender(singleplayer);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(10);

        context.runOnClient(mc -> TeleportFadeHud.receive(FADE));
        LOG.info("[GUITEST] HUD stack (teleport fade + energy pack) -> {}",
                takeCleanScreenshot(context, "hud_stack_teleport_fade_energy_pack").toAbsolutePath());

        context.runOnClient(mc -> TeleportFadeHud.reset());
        server.runCommand("item replace entity @p armor.chest with minecraft:air");
        context.waitTicks(5);
    }
}
