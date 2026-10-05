package dev.alaindustrial.gametest;

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
 * MOD-716 batch 0 (characterization of CLI-9): the block-entity renderers that write their own quads and are
 * not photographed by {@link RendererStands} or {@link TeleporterCapsuleStand} — the fluid surface of a tank,
 * the reactor airlock panel (closed and open), the insulating stand under a bare cable and the stock display
 * frame with its item.
 *
 * <p>Each of them builds vertices with a private {@code vertex}/{@code quad}/{@code face} helper; MOD-716
 * batch 13 moves them onto one shared emitter. These frames are the baseline that move is compared against
 * on the build server: the same scene, the same camera, the same pixels. They assert nothing by themselves —
 * the comparison against the frames of the run before the batch is the check — but every block entity's
 * client state is logged, so an empty-looking frame can be told apart from a desynced block entity.
 *
 * <p>The water mill wheel, the wind mill rotor and the incubator are already framed by {@link RendererStands},
 * the capsule door by {@link TeleporterCapsuleStand}; they are not repeated.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BerQuadStands {

    private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

    /** The row the four subjects stand in: x 238..250 every three blocks, at this y and z. */
    private static final int ROW_X = 238;
    private static final int ROW_Y = 101;
    private static final int ROW_Z = 150;
    private static final int STEP = 3;

    private static final BlockPos TANK = new BlockPos(ROW_X, ROW_Y, ROW_Z);
    private static final BlockPos DOOR = new BlockPos(ROW_X + STEP, ROW_Y, ROW_Z);
    private static final BlockPos CABLE = new BlockPos(ROW_X + 2 * STEP, ROW_Y, ROW_Z);
    private static final BlockPos FRAME_WALL = new BlockPos(ROW_X + 3 * STEP, ROW_Y, ROW_Z - 1);

    private BerQuadStands() {
    }

    /**
     * Builds the row, photographs it from three views, then opens the airlock and photographs it again.
     *
     * @covers R-VIS-04
     */
    public static void shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        TestServerContext server = singleplayer.getServer();
        server.runCommand("weather clear");
        server.runCommand("time set day");
        server.runCommand("fill " + (ROW_X - 3) + " " + ROW_Y + " " + (ROW_Z - 4) + " "
                + (ROW_X + 4 * STEP + 3) + " " + (ROW_Y + 4) + " " + (ROW_Z + 6) + " minecraft:air");
        server.runCommand("fill " + (ROW_X - 3) + " " + (ROW_Y - 1) + " " + (ROW_Z - 4) + " "
                + (ROW_X + 4 * STEP + 3) + " " + (ROW_Y - 1) + " " + (ROW_Z + 6) + " minecraft:smooth_stone");

        // A tank three-eighths full of water: the surface quad sits mid-height, clear of both caps.
        server.runCommand("setblock " + at(TANK) + " alaindustrial:fluid_tank");
        server.runCommand("data merge block " + at(TANK)
                + " {FluidTankMb: 6000L, FluidTankFluid: \"minecraft:water\"}");

        // The airlock: both halves, closed. Its block model is empty — the panel is all renderer.
        placeDoor(server, false);

        // A bare copper cable on a glass stand (the stand is drawn by the cable's renderer).
        server.runCommand("setblock " + at(CABLE) + " alaindustrial:copper_cable");
        server.runCommand("data merge block " + at(CABLE) + " {ShockGuard: \"minecraft:glass\"}");

        // A stock display frame hanging on a stone wall, facing south, with an ingot in it.
        server.runCommand("setblock " + at(FRAME_WALL) + " minecraft:stone");
        server.runCommand("summon alaindustrial:stock_display_frame " + at(FRAME_WALL.south())
                + " {Facing: 3b, Item: {id: \"minecraft:iron_ingot\", count: 1}}");

        server.runCommand("gamemode spectator @p");
        L3Chunks.waitRender(singleplayer);
        context.waitTicks(20);
        logClientState(context);

        double centreX = ROW_X + 1.5 * STEP + 0.5;
        String[][] views = {
            {centreX + " " + (ROW_Y + 0.4) + " " + (ROW_Z + 6.5) + " 180 6", "ber_quads_row_front"},
            {(ROW_X + 4 * STEP + 2.5) + " " + (ROW_Y + 2.5) + " " + (ROW_Z + 4.5) + " 135 25", "ber_quads_row_iso"},
            {centreX + " " + (ROW_Y + 4.0) + " " + (ROW_Z + 3.5) + " 180 50", "ber_quads_row_top"},
        };
        for (String[] view : views) {
            shot(context, singleplayer, server, view[0], view[1]);
        }

        // Open the airlock and let the panel finish sliding before the frame.
        placeDoor(server, true);
        context.waitTicks(40);
        shot(context, singleplayer, server, (DOOR.getX() + 0.5) + " " + (ROW_Y + 0.4) + " " + (ROW_Z + 4.5)
                + " 180 6", "ber_quads_reactor_door_open");
    }

    private static void placeDoor(TestServerContext server, boolean open) {
        String state = "[facing=south,hinge=left,open=" + open + ",powered=false";
        server.runCommand("setblock " + at(DOOR) + " alaindustrial:reactor_door" + state + ",half=lower]");
        server.runCommand("setblock " + at(DOOR.above()) + " alaindustrial:reactor_door" + state + ",half=upper]");
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                             TestServerContext server, String camera, String name) {
        server.runCommand("tp @p " + camera);
        L3Chunks.waitRender(singleplayer);
        context.waitTicks(5);
        Path path = takeCleanScreenshot(context, name);
        LOG.info("[GUITEST][MOD-716] {} -> {}", name, path.toAbsolutePath());
    }

    /** What the client received: logged, because a renderer draws from the client copy of each block entity. */
    private static void logClientState(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (mc.level == null) {
                return;
            }
            for (BlockPos pos : new BlockPos[] {TANK, DOOR, DOOR.above(), CABLE}) {
                LOG.info("[GUITEST][MOD-716][BER] {} state={} be={} renderer={}", pos.toShortString(),
                        mc.level.getBlockState(pos), mc.level.getBlockEntity(pos),
                        mc.level.getBlockEntity(pos) == null ? null
                                : mc.getBlockEntityRenderDispatcher().getRenderer(mc.level.getBlockEntity(pos)));
            }
        });
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
