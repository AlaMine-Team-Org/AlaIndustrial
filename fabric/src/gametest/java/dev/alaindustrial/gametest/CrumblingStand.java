package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.compat.L3Chunks;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-703 (batch 0): the break cracks reach the frame through a block-entity renderer — the {@code chest_crumbling}
 * frames.
 *
 * <p><b>Why this frame exists.</b> A block drawn by its renderer alone has no block model to crack: the mod's
 * chests carry a particle-only model, so the crumbling overlay a player sees while mining one is drawn by
 * {@code ChestBlockEntityRenderer} from the {@code breakProgress} vanilla hands it. That path is a version seam
 * ({@code compat.client.ModelSubmit.withCrumbling}: 26.3 submits the overlay as its own order, 26.2 passes it to
 * {@code submitModel}), and before this frame no L3 shot ever set a break progress, so neither line's body of the
 * seam was looked at.
 *
 * <p><b>The gate.</b> The server reports a break progress of stage 9 on the chest for a breaker that is no
 * player ({@code ServerLevel.destroyBlockProgress} sends it to every player but the breaker), the frame is taken,
 * and the progress is cleared again. The cracked frame must differ from the clean frame before it by far more
 * than the two clean frames, before and after, differ from each other — the same "with versus without, against
 * the noise floor" shape as {@link RendererStands}, with no committed baseline PNG.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CrumblingStand {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Where the chest stands: between the centrifuge (x 190) and the workstation (x 214) rigs. */
    private static final BlockPos CHEST = new BlockPos(202, 101, 150);

    /** The breaker id the progress is reported for: no entity has it, so every player is sent the overlay. */
    private static final int NO_BREAKER = -1;

    /** The last crack stage (0–9): the most pixels for the gate to measure. */
    private static final int LAST_STAGE = 9;

    private CrumblingStand() {
    }

    /** @implements MOD-703-CR01 — the crumbling overlay of a renderer-only block reaches the captured frame */
    public static void checkChestCrumbling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // Rain and night put moving or dimming pixels into the clean pair; the noise floor has to be driver
        // dithering alone or the gate loses the ability to fail (MOD-232).
        server.runCommand("weather clear");
        server.runCommand("time set day");
        int x = CHEST.getX();
        int y = CHEST.getY();
        int z = CHEST.getZ();
        server.runCommand("fill " + (x - 3) + " " + (y - 1) + " " + (z - 3) + " " + (x + 3) + " " + (y - 1) + " "
                + (z + 3) + " minecraft:smooth_stone");
        server.runCommand("fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 3) + " " + (y + 3) + " "
                + (z + 3) + " minecraft:air");
        server.runCommand("setblock " + x + " " + y + " " + z + " alaindustrial:iron_chest[facing=south]");
        server.runCommand("gamemode spectator @p");
        // Close and a little above, looking down on the lid: the chest owns most of the frame.
        server.runCommand("tp @p " + (x + 0.5) + " " + (y + 0.6) + " " + (z + 2.0) + " 180 30");
        L3Chunks.waitRender(singleplayer);
        context.waitTicks(10);

        Path cleanBefore = takeCleanScreenshot(context, "chest_crumbling_clean_a");

        setProgress(server, LAST_STAGE);
        context.waitTicks(5);
        Path cracked = takeCleanScreenshot(context, "chest_crumbling");
        LOG.info("[GUITEST][CRUMBLING] chest_crumbling -> {}", cracked.toAbsolutePath());

        setProgress(server, -1);
        context.waitTicks(5);
        Path cleanAfter = takeCleanScreenshot(context, "chest_crumbling_clean_b");

        int crackDelta = differingPixels(cracked, cleanBefore);
        int staticNoise = differingPixels(cleanBefore, cleanAfter);
        // 4x the measured floor and at least 400 px, the condenser crystal's shape: the cracks cover the
        // whole visible chest, and a threshold a healthy build cannot clear is a gate that gets deleted.
        int required = Math.max(4 * staticNoise, 400);
        LOG.info("[GUITEST][CRUMBLING] crack pixel gate: delta={} px, static baseline={} px, required>{}",
                crackDelta, staticNoise, required);
        if (crackDelta < required) {
            throw new AssertionError("[GUITEST][CRUMBLING] a stage-" + LAST_STAGE + " break progress changed only "
                    + crackDelta + " px (static baseline " + staticNoise + " px, required > " + required
                    + ") — ChestBlockEntityRenderer's crumbling overlay is not in the captured frame (check "
                    + "compat.client.ModelSubmit.withCrumbling on this line), or the progress never reached the "
                    + "client. " + explainWithDiff(cleanBefore, cracked));
        }
    }

    /** Reports {@code stage} for {@link #CHEST} to every player; {@code -1} clears it. */
    private static void setProgress(TestServerContext server, int stage) {
        server.runOnServer(minecraft -> minecraft.overworld().destroyBlockProgress(NO_BREAKER, CHEST, stage));
    }
}
