package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every stand that proves a {@code BlockEntityRenderer}'s geometry really reaches the captured frame:
 * the water mill's wheel (MOD-024), the three wind mills' rotors (MOD-232), the incubator's floating
 * item (MOD-118) and the energy condenser's orb (MOD-393).
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. They belong together because they are the same
 * test, four times over, and the shape of it is the load-bearing part:
 * <ul>
 *   <li><b>Renderer gate</b> — the CLIENT block entity must be in the exact state the renderer draws
 *       in. Every one of these renderers returns early on some state, so a rig that walls the thing in
 *       yields a perfectly valid, perfectly empty screenshot.
 *   <li><b>Pixel gate</b> — the frame with the thing installed must differ from the frame without it by
 *       far more than two consecutive without-it frames differ from each other. No committed baseline
 *       PNG, so nothing goes stale across drivers.
 * </ul>
 *
 * <p><b>A screenshot on its own asserts nothing.</b> {@code takeCleanScreenshot} only proves a non-empty
 * PNG was written — a frame missing a whole renderer still passes. Any stand that claims to cover
 * rendering must additionally assert the thing it photographs, the way these do; otherwise name it for
 * what it is, a screenshot for a human to look at.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RendererStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** Position of the water mill in {@link #checkWaterMillWheel}'s rig. */
    private static final BlockPos WMILL_POS = new BlockPos(120, 102, 121);

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

    /** Where the condenser stands for {@link #checkEnergyCondenserOrb}. */
    private static final int ORB_X = 170;
    private static final int ORB_Y = 101;
    private static final int ORB_Z = 150;

    private RendererStands() {
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
    public static void checkWaterMillWheel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
                    + ") — the BlockEntityRenderer's geometry is NOT in the captured frame. "
                    + explainWithDiff(withWheel, noWheelA));
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Energy condenser — BER orb visual (MOD-393)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * MOD-393: proves the condenser's orb actually reaches the captured frame.
     *
     * <p>The toggle is the bank itself, not the block. {@code EnergyCondenserBlockEntityRenderer}
     * draws nothing at zero — a deliberate rule, because a glowing orb inside an empty condenser
     * claims the machine is working — so filling and emptying the bank turns the renderer on and off
     * while leaving the block, its frame model and every pixel around it untouched. Photographing the
     * block against bare ground instead would compare the whole machine and pass even if the orb never
     * drew a single triangle, which is the failure this exists to catch (MOD-231).
     *
     * <p>That makes one gate cover two things: the orb renders, AND it obeys the empty-bank rule.
     */
    public static void checkEnergyCondenserOrb(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // Rain and night both put moving or dimming pixels into the baseline pair; the noise floor has
        // to be driver dithering alone or the gate loses the ability to fail (MOD-232).
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill " + (ORB_X - 3) + " " + (ORB_Y - 1) + " " + (ORB_Z - 3) + " "
                + (ORB_X + 3) + " " + (ORB_Y - 1) + " " + (ORB_Z + 3) + " minecraft:smooth_stone");
        server.runCommand("fill " + (ORB_X - 3) + " " + ORB_Y + " " + (ORB_Z - 3) + " "
                + (ORB_X + 3) + " " + (ORB_Y + 3) + " " + (ORB_Z + 3) + " minecraft:air");
        server.runCommand("setblock " + ORB_X + " " + ORB_Y + " " + ORB_Z
                + " alaindustrial:energy_condenser");
        // Banked past tier I, so the orb is at a healthy size and brightness rather than its dimmest.
        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 700000L}");

        // Close in: the orb is a 0.6-block ball inside the frame, an order of magnitude smaller than
        // the wind mill's rotor quad, so the camera has to be near enough for it to own real pixels.
        server.runCommand("tp @p " + (ORB_X + 0.5) + " " + (ORB_Y + 0.3) + " " + (ORB_Z + 2.2) + " 180 5");
        server.runCommand("gamemode spectator @p");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);

        Path withOrb = takeCleanScreenshot(context, "condenser_orb");
        LOG.info("[GUITEST][CONDENSER] condenser_orb -> {}", withOrb.toAbsolutePath());

        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 0L}");
        context.waitTicks(10);
        Path emptyA = takeCleanScreenshot(context, "condenser_orb_empty_a");
        context.waitTicks(5);
        Path emptyB = takeCleanScreenshot(context, "condenser_orb_empty_b");

        int orbDelta = differingPixels(withOrb, emptyA);
        int staticNoise = differingPixels(emptyA, emptyB);
        // 4x the measured floor, and at least 400 px. Lower than the rotor's 2000 on purpose: the orb
        // is a much smaller object, and a threshold it cannot clear on a healthy build is a gate that
        // gets deleted rather than fixed.
        int required = Math.max(4 * staticNoise, 400);
        LOG.info("[GUITEST][CONDENSER] orb pixel gate: delta={} px, static baseline={} px, required>{}",
                orbDelta, staticNoise, required);
        if (orbDelta < required) {
            throw new AssertionError("[GUITEST][CONDENSER] emptying the bank changed only " + orbDelta
                    + " px (static baseline " + staticNoise + " px, required > " + required + ") — either "
                    + "EnergyCondenserBlockEntityRenderer's geometry is not in the captured frame, or the "
                    + "orb no longer hides on an empty bank. " + explainWithDiff(withOrb, emptyA));
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Wind mill — BER rotor visual (blades present and turning on all three family mills)
    // ────────────────────────────────────────────────────────────────────────────────

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
    public static void checkWindMillRotor(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
                    + "is NOT in the captured frame. " + explainWithDiff(withRotor, noRotorA));
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

    /** Install or clear the rotor in slot 0 — {@code ROTOR_SLOT}, shared by all three wind mills. */
    private static void setRotor(TestServerContext server, String x, boolean install) {
        server.runCommand("item replace block " + x + " " + WIND_MILL_Y + " " + WIND_MILL_Z
                + " container.0 with " + (install ? "alaindustrial:windmill_rotor" : "minecraft:air"));
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Incubator — the dome, its tint and the floating item (MOD-118)
    // ────────────────────────────────────────────────────────────────────────────────

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
    public static void checkIncubatorDome(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
}
