package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.block.LitMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.client.render.ThermalCentrifugeBlockEntityRenderer;
import dev.alaindustrial.client.render.WindMillRotorBlockEntityRenderer;
import dev.alaindustrial.core.machine.RotorSpin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every stand that proves a {@code BlockEntityRenderer}'s geometry really reaches the captured frame:
 * the water mill's wheel (MOD-024), the three wind mills' rotors (MOD-232), the incubator's floating
 * item (MOD-118), the energy condenser's crystal (MOD-546) and the thermal centrifuge's rotor
 * (MOD-424).
 *
 * <p>Split out of {@code GuiClientGameTest} by MOD-404. They belong together because they are the same
 * test, five times over, and the shape of it is the load-bearing part:
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

    /** Where the condenser stands for {@link #checkEnergyCondenserCrystal}. */
    private static final int ORB_X = 170;
    private static final int ORB_Y = 101;
    private static final int ORB_Z = 150;

    /** Where the thermal centrifuge stands for {@link #checkThermalCentrifugeRotor}. */
    private static final int ROTOR_X = 190;
    private static final int ROTOR_Y = 101;
    private static final int ROTOR_Z = 150;
    /** Position of that centrifuge, for the client-side renderer gate. */
    private static final BlockPos ROTOR_POS = new BlockPos(ROTOR_X, ROTOR_Y, ROTOR_Z);
    /**
     * How far a player's eye sits above the feet a {@code tp} places. Subtracting it is what puts the
     * camera level with the rotor's axis instead of looking down on the machine from head height — the
     * difference between a window that fills the frame and one squinted at across its own top edge.
     */
    private static final double EYE_HEIGHT = 1.62;

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
    // Energy condenser — BER crystal visual (MOD-546)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * MOD-546: proves the condenser's crystal actually reaches the captured frame.
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
    public static void checkEnergyCondenserCrystal(ClientGameTestContext context,
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
        // Top tier (MOD-546): the crystal has three shapes, and this is the largest of them — the one
        // with the halves that pull apart. A smaller stage would hand the pixel gate below less to
        // measure while proving less about the geometry.
        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 4000000L}");

        // Close in: the crystal is four pixels across inside the frame, an order of magnitude smaller
        // than the wind mill's rotor quad, so the camera has to be near enough for it to own real
        // pixels.
        server.runCommand("tp @p " + (ORB_X + 0.5) + " " + (ORB_Y + 0.3) + " " + (ORB_Z + 2.2) + " 180 5");
        server.runCommand("gamemode spectator @p");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(10);

        Path withCrystal = takeCleanScreenshot(context, "condenser_crystal");
        LOG.info("[GUITEST][CONDENSER] condenser_crystal -> {}", withCrystal.toAbsolutePath());

        // One EU, not zero. The comparison has to isolate the CRYSTAL, and an empty bank changes far
        // more than that: LIT goes false, so the block stops emitting light 13 and swaps its frame
        // texture for the dim one — the whole frame differs, and the gate below would pass at full
        // marks with the crystal deleted from the renderer entirely (measured: the diff covered the
        // entire 1280x720 frame). A single EU keeps LIT true and the frame identical while leaving
        // the bank below tier I, where no clot exists and nothing is drawn inside.
        server.runCommand("data merge block " + ORB_X + " " + ORB_Y + " " + ORB_Z + " {Energy: 1L}");
        context.waitTicks(10);
        Path emptyA = takeCleanScreenshot(context, "condenser_crystal_empty_a");
        context.waitTicks(5);
        Path emptyB = takeCleanScreenshot(context, "condenser_crystal_empty_b");

        int crystalDelta = differingPixels(withCrystal, emptyA);
        int staticNoise = differingPixels(emptyA, emptyB);
        // 4x the measured floor, and at least 400 px. Lower than the rotor's 2000 on purpose: the
        // crystal is a much smaller object, and a threshold it cannot clear on a healthy build is a
        // gate that gets deleted rather than fixed.
        int required = Math.max(4 * staticNoise, 400);
        LOG.info("[GUITEST][CONDENSER] crystal pixel gate: delta={} px, static baseline={} px, "
                + "required>{}", crystalDelta, staticNoise, required);
        if (crystalDelta < required) {
            throw new AssertionError("[GUITEST][CONDENSER] dropping below tier I changed only " + crystalDelta
                    + " px (static baseline " + staticNoise + " px, required > " + required + ") — either "
                    + "EnergyCondenserBlockEntityRenderer's geometry is not in the captured frame, or the "
                    + "crystal no longer hides below tier I. " + explainWithDiff(withCrystal, emptyA));
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Thermal centrifuge — BER rotor visual (MOD-424)
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * MOD-424: proves the centrifuge's rotor reaches the captured frame, and that it TURNS.
     *
     * <p><b>The toggle is the machine's state, not the block.</b> Cutting the redstone signal stops the
     * rotor dead — {@code RotorSpin.angle} is a hard zero at zero spin — while leaving the housing, its
     * open window, the floor and everything else in shot untouched. Photographing the block against bare
     * ground instead would compare the whole machine and pass even if the rotor never drew a triangle,
     * which is the failure this exists to catch (MOD-231).
     *
     * <p><b>Why the gate measures motion rather than presence.</b> The rotor is drawn whether or not it
     * is turning — it is hardware, not a readout — so "with vs without" is not available here the way it
     * is for the condenser's crystal. Worse, the rotor carries four vanes per tier and is therefore symmetric
     * every 90&deg;, so comparing a stopped rotor against a spinning one could legitimately land on two
     * near-identical poses and fail on a healthy build. So the measurement is turned around: two frames a
     * few ticks apart while it spins must differ far more than two frames a few ticks apart while it is
     * stopped. That single comparison covers three separate claims — the geometry is in the frame, it is
     * animated, and it really does stop when the signal goes away.
     */
    public static void checkThermalCentrifugeRotor(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        // Rain and night both put moving or dimming pixels into the stopped baseline; that baseline has
        // to be driver dithering alone or the gate loses the ability to fail (MOD-232).
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill " + (ROTOR_X - 4) + " " + ROTOR_Y + " " + (ROTOR_Z - 4) + " "
                + (ROTOR_X + 4) + " " + (ROTOR_Y + 4) + " " + (ROTOR_Z + 4) + " minecraft:air");
        server.runCommand("fill " + (ROTOR_X - 4) + " " + (ROTOR_Y - 1) + " " + (ROTOR_Z - 4) + " "
                + (ROTOR_X + 4) + " " + (ROTOR_Y - 1) + " " + (ROTOR_Z + 4) + " minecraft:smooth_stone");
        // facing=south turns the housing's window towards the camera, which then leaves the far side free
        // for the redstone block holding the motor's start signal — hidden behind the machine's own back
        // wall, so it contributes no pixels to either pair of frames.
        server.runCommand("setblock " + ROTOR_X + " " + ROTOR_Y + " " + ROTOR_Z
                + " alaindustrial:thermal_centrifuge[facing=south]");
        setCentrifugeSignal(server, true);
        // Spin is persisted state, so the ramp is one command instead of 400 ticks of lane time. The
        // value is deliberately far past any spin-up length: loadAdditional clamps it to the configured
        // ceiling, so this reads "fully wound" whatever thermalCentrifugeSpinupTicks happens to be — and
        // the EU is what keeps it there, since a spun-up rotor that cannot pay the idle rate sheds speed.
        server.runCommand("data merge block " + ROTOR_X + " " + ROTOR_Y + " " + ROTOR_Z
                + " {Spin: 100000, Energy: 2000L}");

        server.runCommand("gamemode spectator @p");
        server.runCommand("tp @p " + (ROTOR_X + 0.5) + " " + (ROTOR_Y + 0.5 - EYE_HEIGHT) + " "
                + (ROTOR_Z + 1.8) + " 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        // Only now is the machine's chunk inside the client's view distance — a client-side
        // getBlockEntity before the teleport returns null (MOD-231).
        context.waitTicks(20);

        assertRotorRendererGate(context);

        Path spinningA = takeCleanScreenshot(context, "centrifuge_rotor_spinning_a");
        LOG.info("[GUITEST][CENTRIFUGE] centrifuge_rotor_spinning_a -> {}", spinningA.toAbsolutePath());
        context.waitTicks(5);
        Path spinningB = takeCleanScreenshot(context, "centrifuge_rotor_spinning_b");

        setCentrifugeSignal(server, false);
        // Long enough for the machine to see the neighbour change, zero its spin and push that to the
        // client. The block's neighbourChanged deliberately bypasses the 40-tick idle sleep for exactly
        // this, so ten ticks is generous rather than tight.
        context.waitTicks(10);
        Path stoppedA = takeCleanScreenshot(context, "centrifuge_rotor_stopped_a");
        context.waitTicks(5);
        Path stoppedB = takeCleanScreenshot(context, "centrifuge_rotor_stopped_b");

        int spinDelta = differingPixels(spinningA, spinningB);
        int stoppedNoise = differingPixels(stoppedA, stoppedB);
        // 4x the measured floor, and at least 400 px — the condenser crystal's numbers, and for the same
        // reason: the rotor is seen through an 8x8-pixel window rather than filling a 2x2-block quad like
        // the wind mill's blades, and a threshold a healthy build cannot clear is a gate that gets
        // deleted rather than fixed.
        int required = Math.max(4 * stoppedNoise, 400);
        LOG.info("[GUITEST][CENTRIFUGE] rotor pixel gate: spinning delta={} px, stopped baseline={} px, "
                + "required>{}", spinDelta, stoppedNoise, required);
        if (spinDelta < required) {
            throw new AssertionError("[GUITEST][CENTRIFUGE] five ticks of a spun-up rotor changed only "
                    + spinDelta + " px (stopped baseline " + stoppedNoise + " px, required > " + required
                    + ") — either ThermalCentrifugeBlockEntityRenderer's geometry is not in the captured "
                    + "frame, or the rotor is being drawn frozen (check that spinPermille reaches the "
                    + "client and that RotorSpin.angle is fed the game clock). "
                    + explainWithDiff(spinningA, spinningB));
        }

        // Restore the signal, then walk round to the left face. The housing is open on three sides, and a
        // suite that only ever photographs the front would not notice a side window that framed nothing —
        // the model cuts those openings and the bars back out by hand, and they are the pieces with no
        // shared geometry to fall back on. Logged, not gated: the pixel gate above already proves the
        // renderer reaches a frame, and a second copy of it here would measure the same claim twice.
        setCentrifugeSignal(server, true);
        context.waitTicks(10);
        // Same 1.3 blocks off the block's CENTRE as the front camera — the coordinates here are the
        // block's minimum corner, so the half-block has to be added before the standoff is subtracted.
        server.runCommand("tp @p " + (ROTOR_X + 0.5 - 1.3) + " " + (ROTOR_Y + 0.5 - EYE_HEIGHT) + " "
                + (ROTOR_Z + 0.5) + " 270 0");
        singleplayer.getClientLevel().waitForChunksRender();
        context.waitTicks(5);
        LOG.info("[GUITEST][CENTRIFUGE] centrifuge_rotor_side -> {}",
                takeCleanScreenshot(context, "centrifuge_rotor_side").toAbsolutePath());
    }

    /**
     * Renderer gate: the CLIENT copy of the machine must be in the state the rotor renderer animates in.
     *
     * <p>Reads the same two inputs {@code ThermalCentrifugeBlockEntityRenderer.extractRenderState} reads —
     * the synced spin permille and the {@code LIT} blockstate — and runs them through the very rate
     * function the renderer uses, so a rig that leaves the machine unpowered, unsignalled or spun down
     * fails HERE instead of producing a green screenshot of a motionless rotor.
     */
    private static void assertRotorRendererGate(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            BlockEntity be = mc.level.getBlockEntity(ROTOR_POS);
            if (!(be instanceof ThermalCentrifugeBlockEntity centrifuge)) {
                throw new AssertionError("[GUITEST][CENTRIFUGE] client has no ThermalCentrifugeBlockEntity "
                        + "at " + ROTOR_POS + " (got " + be + ", block state "
                        + mc.level.getBlockState(ROTOR_POS) + ") — the rig is broken, or the camera is "
                        + "still too far away for the client to have loaded that chunk");
            }
            Object renderer = mc.getBlockEntityRenderDispatcher().getRenderer(centrifuge);
            if (!(renderer instanceof ThermalCentrifugeBlockEntityRenderer)) {
                throw new AssertionError("[GUITEST][CENTRIFUGE] the thermal centrifuge has no "
                        + "ThermalCentrifugeBlockEntityRenderer registered (got " + renderer
                        + ") — the rotor cannot reach any frame at all");
            }
            BlockState state = mc.level.getBlockState(ROTOR_POS);
            boolean lit = state.hasProperty(LitMachineBlock.LIT) && state.getValue(LitMachineBlock.LIT);
            int permille = centrifuge.spinPermille();
            float rate = RotorSpin.radiansPerTick(permille, lit);
            LOG.info("[GUITEST][CENTRIFUGE] renderer gate: spin={}permille lit={} rate={} rad/tick",
                    permille, lit, rate);
            if (permille < 900 || rate <= 0.0F) {
                throw new AssertionError("[GUITEST][CENTRIFUGE] the rotor renderer would draw a MOTIONLESS "
                        + "rotor: spin=" + permille + " permille, lit=" + lit + ", rate=" + rate
                        + " rad/tick (expected spin >= 900). The machine only holds its revolutions while "
                        + "it has a redstone signal AND enough EU to pay thermalCentrifugeIdleEuPerTick — "
                        + "check the redstone block behind it and the Energy the rig merged in.");
            }
        });
    }

    /** The held redstone signal that is this machine's motor switch, placed on its hidden back side. */
    private static void setCentrifugeSignal(TestServerContext server, boolean on) {
        server.runCommand("setblock " + ROTOR_X + " " + ROTOR_Y + " " + (ROTOR_Z - 1) + " "
                + (on ? "minecraft:redstone_block" : "minecraft:air"));
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

    // ────────────────────────────────────────────────────────────────────────────────
    // Workstation — BER fans and fold-out screens (MOD-483)
    // ────────────────────────────────────────────────────────────────────────────────

    /** Where the workstation stands for {@link #checkWorkstationScreens}. Clear of every other rig. */
    private static final int WSTATION_X = 214;
    private static final int WSTATION_Y = 101;
    private static final int WSTATION_Z = 150;

    /**
     * Proves the workstation's animation is alive, not merely that its texture changed.
     *
     * <p>The machine has three moving parts — three fans on a loop and the monitor arm folding out —
     * and all of them are driven by the game clock through a blockstate flag, with nothing stored and
     * nothing synced. That design is cheap precisely because there is no packet to notice when it
     * breaks: a renderer that stopped reading the clock, or a block that stopped flipping the flag,
     * would look exactly like a machine standing still, and standing still is also what an unpowered
     * one is supposed to look like.
     *
     * <p>So the gate is the same shape as the centrifuge's: two frames five ticks apart while the
     * station is powered must differ by far more than two frames five ticks apart while it is dark.
     * A screenshot of the lit machine alone would pass with the fans frozen.
     *
     * <p>The dark half is made by REPLACING the machine rather than by draining it: the buffer holds
     * 40 000 EU and upkeep is 6 EU/t, so waiting for it to run down would cost the lane five and a half
     * hours of ticks. A freshly placed pair has an empty buffer and goes dark on its first tick.
     */
    public static void checkWorkstationScreens(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill " + (WSTATION_X - 3) + " " + WSTATION_Y + " " + (WSTATION_Z - 3) + " "
                + (WSTATION_X + 3) + " " + (WSTATION_Y + 4) + " " + (WSTATION_Z + 3) + " minecraft:air");
        server.runCommand("fill " + (WSTATION_X - 3) + " " + (WSTATION_Y - 1) + " " + (WSTATION_Z - 3) + " "
                + (WSTATION_X + 3) + " " + (WSTATION_Y - 1) + " " + (WSTATION_Z + 3) + " minecraft:smooth_stone");

        placeWorkstation(server);
        // Energy through the save format rather than a cable: the frame is about the renderer, and a
        // powered neighbour would put its own block into it.
        server.runCommand("data merge block " + WSTATION_X + " " + WSTATION_Y + " " + WSTATION_Z
                + " {Energy: 20000L}");

        server.runCommand("gamemode spectator @p");
        // Eye level with the monitors (one block up) and far enough back that both halves fit.
        server.runCommand("tp @p " + (WSTATION_X + 0.5) + " " + (WSTATION_Y + 1.5 - EYE_HEIGHT) + " "
                + (WSTATION_Z + 3.2) + " 180 0");
        singleplayer.getClientLevel().waitForChunksRender();
        // Past the one-second fold-out, so the arm is settled and only the fans still move.
        context.waitTicks(30);

        Path litA = takeCleanScreenshot(context, "vis_workstation_lit_a");
        LOG.info("[GUITEST][WORKSTATION] vis_workstation_lit_a -> {}", litA.toAbsolutePath());
        context.waitTicks(5);
        Path litB = takeCleanScreenshot(context, "vis_workstation_lit_b");

        // A brand-new pair: empty buffer, dark on its first tick.
        server.runCommand("fill " + WSTATION_X + " " + WSTATION_Y + " " + WSTATION_Z + " "
                + WSTATION_X + " " + (WSTATION_Y + 1) + " " + WSTATION_Z + " minecraft:air");
        placeWorkstation(server);
        // The fold-away plays for a second; the baseline has to be the settled pose, or it measures the
        // animation it is supposed to be the absence of.
        context.waitTicks(30);

        Path darkA = takeCleanScreenshot(context, "vis_workstation_dark_a");
        context.waitTicks(5);
        Path darkB = takeCleanScreenshot(context, "vis_workstation_dark_b");

        int litDelta = differingPixels(litA, litB);
        int darkNoise = differingPixels(darkA, darkB);
        // Same shape of threshold as the centrifuge, and a lower floor for the same reason in reverse:
        // three fans of a few pixels each move less than a rotor seen through a window. 4x the measured
        // floor keeps it honest; the 120 px absolute keeps it from passing on a still frame when the
        // floor happens to measure zero.
        int required = Math.max(4 * darkNoise, 120);
        LOG.info("[GUITEST][WORKSTATION] fan pixel gate: lit delta={} px, dark baseline={} px, required>{}",
                litDelta, darkNoise, required);
        if (litDelta < required) {
            throw new AssertionError("[GUITEST][WORKSTATION] five ticks of a powered workstation changed "
                    + "only " + litDelta + " px (dark baseline " + darkNoise + " px, required > " + required
                    + ") - either WorkstationBlockEntityRenderer's fans are not in the captured frame, or "
                    + "they are drawn frozen (check that the renderer reads the game clock and that LIT "
                    + "reaches the client). " + explainWithDiff(litA, litB));
        }
    }

    /** Two casings, stacked: the neighbour update assembles them into the machine. */
    private static void placeWorkstation(TestServerContext server) {
        server.runCommand("setblock " + WSTATION_X + " " + WSTATION_Y + " " + WSTATION_Z
                + " alaindustrial:workstation[facing=south]");
        server.runCommand("setblock " + WSTATION_X + " " + (WSTATION_Y + 1) + " " + WSTATION_Z
                + " alaindustrial:workstation[facing=south]");
    }

}
