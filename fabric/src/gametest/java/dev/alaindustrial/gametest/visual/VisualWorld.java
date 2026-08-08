package dev.alaindustrial.gametest.visual;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts the world into a state where two runs of the same frame look the same.
 *
 * <p>Determinism is the precondition for every stronger gate. Comparing a frame against a stored
 * reference is only meaningful if the scene does not drift on its own — and before MOD-362 it drifted
 * constantly: the day/night cycle ran, weather could roll in, clouds moved, and time was only pinned
 * inside the handful of checks that happened to care. Everything else was photographed "whenever".
 *
 * <p>Also holds the waiting helpers. The suite had 88 {@code waitTicks(N)} calls — a hope that N ticks
 * is enough rather than a statement of what is being waited for. On a slower machine the hope fails and
 * the frame is captured early; the run stays green and the screenshot is simply wrong.
 */
public final class VisualWorld {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Noon: the sun is up, shadows are short, nothing is mid-transition. */
    public static final int FIXED_TIME = 6000;

    private VisualWorld() {
    }

    /**
     * Freezes everything that would otherwise make the same frame differ between runs, and strips the
     * chrome that has no business in a review screenshot.
     */
    public static void quarantine(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();

        // Chrome: toasts, chat and advancement popups are timing-dependent overlays. A toast that
        // happens to be on screen changes thousands of pixels and has nothing to do with the subject.
        server.runCommand("gamerule announceAdvancements false");
        server.runCommand("gamerule sendCommandFeedback false");
        server.runCommand("gamerule commandBlockOutput false");

        // Nuisance: a mob wandering into frame, or the player taking damage mid-capture, is noise that
        // no amount of tolerance tuning can filter out.
        server.runCommand("gamerule doMobSpawning false");
        server.runCommand("gamerule doFireTick false");
        server.runCommand("gamerule fallDamage false");
        server.runCommand("gamerule doImmediateRespawn true");
        server.runCommand("difficulty peaceful");

        // The two clocks that make an unchanged scene photograph differently: time of day changes every
        // shadow in the frame, weather changes the sky, the light level and the solar panels' readouts.
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("gamerule doWeatherCycle false");
        server.runCommand("time set " + FIXED_TIME);
        server.runCommand("weather clear");

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
        LOG.info("[VISUAL] world quarantined: time={} weather=clear cycles=off", FIXED_TIME);
    }

    /**
     * Waits until a container screen is actually on screen.
     *
     * <p>Replaces {@code waitTicks(5)} after opening a menu: the condition is observable, so wait for
     * the condition. Faster when the screen is ready sooner, and correct when it is ready later.
     */
    public static void awaitContainerScreen(ClientGameTestContext context, String what) {
        try {
            context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        } catch (RuntimeException e) {
            throw new AssertionError("[VISUAL] no container screen appeared for " + what
                    + " — the menu never opened (wrong block, player out of reach, or the screen is not "
                    + "registered for this menu type)", e);
        }
    }

    /** Waits until the client's copy of the world agrees that {@code block} stands at {@code pos}. */
    public static void awaitBlockOnClient(ClientGameTestContext context, BlockPos pos, String blockId) {
        try {
            context.waitFor(mc -> mc.level != null
                    && BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock())
                            .toString().equals(blockId));
        } catch (RuntimeException e) {
            throw new AssertionError("[VISUAL] the client never saw " + blockId + " at " + pos
                    + " — the setblock did not reach the client, or the chunk is not loaded there", e);
        }
    }

    /**
     * Closes an open container the way the player's Escape key does, and waits until it is really gone.
     *
     * <p>Not {@code setScreenAndShow(null)}: that closes the screen on the CLIENT only. The server goes
     * on believing the player has a container open and silently drops the next block interaction — so
     * the following screen never opens and the run dies on a timeout with no hint as to why. Measured
     * behaviour, not theory: it is exactly how the first version of this lane failed after its very
     * first screen.
     *
     * <p>{@link net.minecraft.client.player.LocalPlayer#closeContainer()} sends the close packet and
     * drops back to the inventory menu, which is what makes the next right-click land.
     */
    public static void awaitNoScreen(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreenAndShow(null);
        });
        context.waitFor(mc -> mc.gui.screen() == null
                && mc.player != null && mc.player.containerMenu == mc.player.inventoryMenu);
    }
}
