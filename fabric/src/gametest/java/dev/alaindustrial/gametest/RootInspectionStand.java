package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.client.render.RootInspectionState;
import dev.alaindustrial.gametest.visual.VisualWorld;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * MOD-584: real terrain, root snapshots and before/after framebuffer evidence.
 *
 * <p>The rig sits beside {@link WorldBlockStands} at x 22..34 rather than out at x 200: only the
 * spawn chunks are loaded when a client gametest world opens, and {@code setblock} into an unloaded
 * chunk fails silently — the first version of this stand built nothing and then dereferenced the
 * block entity that was never created.
 */
public final class RootInspectionStand {
    /** Short column: one ready tip on dirt over stone. */
    private static final BlockPos SHORT_TIP = new BlockPos(25, 99, 0);
    /** Long column: two segments in sand. */
    private static final BlockPos LONG_TIP = new BlockPos(28, 98, 0);
    private static final BlockPos LONG_UPPER = new BlockPos(28, 99, 0);

    private RootInspectionStand() { }

    public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var server = singleplayer.getServer();
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("time set day");
        server.runCommand("gamerule doWeatherCycle false");
        server.runCommand("weather clear");
        server.runCommand("gamemode creative @p");
        server.runCommand("fill 22 90 -4 34 97 6 minecraft:stone");
        server.runCommand("fill 22 98 -4 34 99 6 minecraft:dirt");
        server.runCommand("fill 22 100 -4 34 106 6 minecraft:air");
        // Short column: stone right under the single segment, so the tip forms one block down.
        server.runCommand("setblock 25 98 0 minecraft:stone");
        server.runCommand("setblock 25 99 0 alaindustrial:kok_sagyz_root[tip=true]");
        server.runCommand("setblock 25 100 0 alaindustrial:kok_sagyz[age=3]");
        // Long column in sand — also the proof that the shell draws the stored ground, not dirt.
        server.runCommand("setblock 28 98 0 alaindustrial:kok_sagyz_root[tip=true]");
        server.runCommand("setblock 28 99 0 alaindustrial:kok_sagyz_root[tip=false]");
        server.runCommand("setblock 28 100 0 alaindustrial:kok_sagyz[age=3]");
        server.runOnServer(mc -> {
            soil(mc.overworld().getBlockEntity(SHORT_TIP), Blocks.DIRT.defaultBlockState(), SHORT_TIP);
            soil(mc.overworld().getBlockEntity(LONG_TIP), Blocks.SAND.defaultBlockState(), LONG_TIP);
            soil(mc.overworld().getBlockEntity(LONG_UPPER), Blocks.SAND.defaultBlockState(), LONG_UPPER);
        });
        server.runCommand("tp @p 26.5 100 4.5 180 15");
        VisualWorld.awaitNoScreen(context);
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(15);

        var off = VisualStandSupport.takeCleanScreenshot(context, "root_inspect_off");
        context.runOnClient(mc -> mc.options.keyShift.setDown(true));
        context.waitFor(mc -> ((RootInspectionState) mc.gameRenderer.gameRenderState().levelRenderState)
                .alaindustrial$roots().plants().size() == 2);
        context.waitTicks(10);
        var on = VisualStandSupport.takeCleanScreenshot(context, "root_inspect_on");
        context.runOnClient(mc -> mc.options.keyShift.setDown(false));
        context.waitTicks(10);
        var released = VisualStandSupport.takeCleanScreenshot(context, "root_inspect_released");

        long changed = VisualStandSupport.differingPixels(off, on);
        long drift = VisualStandSupport.differingPixels(off, released);
        if (changed < 1000 || changed < drift * 2) {
            throw new AssertionError("Root inspection missing from framebuffer: changed="
                    + changed + ", drift=" + drift);
        }
    }

    /** Fail where the block entity is missing rather than one line later on a null dereference. */
    private static void soil(Object blockEntity, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        if (!(blockEntity instanceof KokSagyzRootBlockEntity root)) {
            throw new AssertionError("No kok sagyz root block entity at " + pos
                    + " — the setblock above did not take (unloaded chunk?), got " + blockEntity);
        }
        root.setSoil(state);
    }
}
