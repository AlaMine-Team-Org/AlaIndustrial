package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.network.PayloadBudget;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-665: the network analyzer's overlay reaches the frame.
 *
 * <p>A generator, a line of copper cable and an electric furnace, scanned exactly as a right-click
 * does it (the same traverse, the same payload, sent to the player through the same dispatcher), then
 * photographed from above with and without the trace. The pixel gate is the claim: the frame with the
 * trace must differ from the frame without it by far more than two frames without it differ from each
 * other — a trace that is built but never drawn gives two identical frames.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NetworkOverlayStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    private static BlockPos cableStart;

    private NetworkOverlayStand() {
    }

    public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        server.runCommand("weather clear");
        server.runCommand("gamemode spectator @p");
        // Built where the player stands, so the camera is guaranteed to be above it.
        java.util.concurrent.atomic.AtomicReference<BlockPos> at = new java.util.concurrent.atomic.AtomicReference<>();
        server.runOnServer(mc -> at.set(mc.getPlayerList().getPlayers().get(0).blockPosition()));
        BlockPos p = at.get();
        int x = p.getX();
        int y = p.getY() - 3;
        int z = p.getZ() - 3;
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:smooth_stone", x - 7, y - 1, z - 4, x + 7, y - 1, z + 4));
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 7, y, z - 4, x + 7, y + 3, z + 4));
        server.runCommand(String.format("setblock %d %d %d alaindustrial:generator[facing=west]", x - 5, y, z));
        server.runCommand(String.format("item replace block %d %d %d container.0 with minecraft:coal 64", x - 5, y, z));
        server.runCommand(String.format("fill %d %d %d %d %d %d alaindustrial:copper_cable", x - 4, y, z, x + 4, y, z));
        server.runCommand(String.format("setblock %d %d %d alaindustrial:electric_furnace[facing=east]", x + 5, y, z));
        cableStart = new BlockPos(x - 4, y, z);
        server.runCommand(String.format("tp @p %d.5 %d %d.5 180 45", x, p.getY(), p.getZ()));
        VisualWorld.awaitNoScreen(context);
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(40);

        clearTrace(server);
        context.waitTicks(5);
        Path offA = VisualStandSupport.takeCleanScreenshot(context, "network_overlay_off_a");
        context.waitTicks(5);
        Path offB = VisualStandSupport.takeCleanScreenshot(context, "network_overlay_off_b");

        server.runOnServer(mc -> {
            ServerLevel level = mc.overworld();
            ServerPlayer player = mc.getPlayerList().getPlayers().get(0);
            EnergyNetwork net = NetworkManager.networkAt(level, cableStart);
            if (net == null) {
                LOG.error("[GUITEST][OVERLAY] no energy network at {}", cableStart);
                return;
            }
            NetworkTraverser.TraversalResult result = NetworkTraverser.traverse(level, net, AnalyzerMode.TRAVERSE,
                    Config.networkAnalyzerMaxTraversedNetworks);
            NetworkAnalyzerPayload payload = NetworkAnalyzerPayload.of(level.dimension(), result,
                    AnalyzerMode.TRAVERSE, cableStart, PayloadBudget.MAX_POSITIONS);
            LOG.info("[GUITEST][OVERLAY] scan cables={} producers={} consumers={} storage={}",
                    result.cableCount(), result.producerList().size(), result.consumerList().size(),
                    result.storageList().size());
            NetworkDispatcher.get().sendToPlayer(player, payload);
        });
        context.waitTicks(10);
        Path on = VisualStandSupport.takeCleanScreenshot(context, "network_overlay_on");
        // A low, close, slanted view along the line — where overlapping translucent parts showed
        // (sparks cut by the sheath at some angles).
        server.runCommand(String.format("tp @p %d.5 %d.6 %d.5 -60 20", x - 3, y + 1, z - 2));
        context.waitTicks(5);
        VisualStandSupport.takeCleanScreenshot(context, "network_overlay_close");
        server.runCommand(String.format("tp @p %d.5 %d %d.5 180 45", x, p.getY(), p.getZ()));

        int noise = VisualStandSupport.differingPixels(offA, offB);
        int signal = VisualStandSupport.differingPixels(offA, on);
        LOG.info("[GUITEST][OVERLAY] noise={} signal={}", noise, signal);
        clearTrace(server);
        if (signal <= Math.max(200, noise * 10)) {
            throw new AssertionError("MOD-665 the analyzer overlay did not reach the frame: " + signal
                    + " pixels changed with the trace against " + noise + " between two frames without it");
        }
    }

    private static void clearTrace(TestServerContext server) {
        server.runOnServer(mc -> {
            ServerPlayer player = mc.getPlayerList().getPlayers().get(0);
            NetworkDispatcher.get().sendToPlayer(player,
                    NetworkAnalyzerPayload.empty(mc.overworld().dimension()));
        });
    }
}
