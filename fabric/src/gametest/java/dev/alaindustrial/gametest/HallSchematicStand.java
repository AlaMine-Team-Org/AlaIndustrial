package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.render.HallSchematicOverlay;
import dev.alaindustrial.compat.L3Chunks;
import dev.alaindustrial.gametest.visual.VisualWorld;
import dev.alaindustrial.network.HallSchematicPayload;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The town hall's schematic reaches the frame (MOD-857, owner's round 2, point 7): a half-built wall beside a
 * settlement core, then the same view with the wall's missing cells shown as ghosts — the reply handed to the client as
 * the server would send it (the core is an unbound one, of no town, so the server answers no request). Frames for a
 * human to review (shot family {@code hall_schematic_}): the real blocks that belong in the wall's gaps, translucent
 * and a little blue — stone bricks, glass, stairs turned to the camera along the top — and in front of the wall a
 * carpet, a two-cell door and a chest (a block with no model of its own, drawn as its box); the real blocks untouched
 * and not hidden (owner's round 3, point 6).
 *
 * <p>What the stand asserts on its own: every ghost was baked, and the ghosts are drawn at all — the frame with them
 * differs from the frame without them by far more than two frames without them differ from each other.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HallSchematicStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Where the wall stands. Clear of every other rig (the shift workers are at x 262). */
	private static final int WALL_X = 286;
	private static final int WALL_Y = 101;
	private static final int WALL_Z = 150;
	/** The wall's width and height; every other cell is a gap the schematic fills. */
	private static final int WIDTH = 7;
	private static final int HEIGHT = 4;

	private HallSchematicStand() {
	}

	public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("weather clear");
		server.runCommand("time set day");
		server.runCommand("gamemode spectator @p");
		// Loaded and kept loaded before the first command: a /fill into an unloaded chunk refuses the whole box.
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int cx = (WALL_X - 8) >> 4; cx <= (WALL_X + 8) >> 4; cx++) {
				for (int cz = (WALL_Z - 8) >> 4; cz <= (WALL_Z + 8) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		server.runCommand("fill " + (WALL_X - 8) + " " + WALL_Y + " " + (WALL_Z - 8) + " " + (WALL_X + 8) + " "
				+ (WALL_Y + 6) + " " + (WALL_Z + 8) + " minecraft:air");
		server.runCommand("fill " + (WALL_X - 8) + " " + (WALL_Y - 1) + " " + (WALL_Z - 8) + " " + (WALL_X + 8) + " "
				+ (WALL_Y - 1) + " " + (WALL_Z + 8) + " minecraft:smooth_stone");
		BlockPos core = new BlockPos(WALL_X - 5, WALL_Y, WALL_Z + 2);
		// Unbound (R53): a core of no town never takes a place of its own, so the server answers no schematic request
		// and its reply cannot clear the ghosts handed below. A bare core placed by command would found a town on its
		// first tick and answer "no plan".
		server.runCommand("setblock " + core.getX() + " " + core.getY() + " " + core.getZ()
				+ " alaindustrial:settlement_core{Unbound:1b}");
		List<HallSchematicPayload.Ghost> ghosts = new ArrayList<>();
		for (int dx = 0; dx < WIDTH; dx++) {
			for (int dy = 0; dy < HEIGHT; dy++) {
				BlockPos cell = new BlockPos(WALL_X - WIDTH / 2 + dx, WALL_Y + dy, WALL_Z);
				BlockState want = dy == HEIGHT - 1
						? Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
						: dx == WIDTH / 2 && dy < 2 ? Blocks.GLASS.defaultBlockState()
						: Blocks.STONE_BRICKS.defaultBlockState();
				if ((dx + dy) % 2 == 0) {
					server.runCommand("setblock " + cell.getX() + " " + cell.getY() + " " + cell.getZ() + " "
							+ BuiltInRegistries.BLOCK.getKey(want.getBlock()));
				} else {
					ghosts.add(new HallSchematicPayload.Ghost(cell.asLong(), want));
				}
			}
		}
		// In front of the wall: states a box could not show — a carpet, both halves of a door, and a chest.
		BlockPos front = new BlockPos(WALL_X, WALL_Y, WALL_Z + 2);
		ghosts.add(new HallSchematicPayload.Ghost(front.west(2).asLong(), Blocks.MOSS_CARPET.defaultBlockState()));
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		ghosts.add(new HallSchematicPayload.Ghost(front.asLong(), door));
		ghosts.add(new HallSchematicPayload.Ghost(front.above().asLong(),
				door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
		ghosts.add(new HallSchematicPayload.Ghost(front.east(2).asLong(),
				Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)));
		server.runCommand("tp @p " + (WALL_X + 0.5) + " " + (WALL_Y + 2.5) + " " + (WALL_Z + 7.5) + " 180 12");
		VisualWorld.awaitNoScreen(context);
		L3Chunks.waitRender(singleplayer);
		context.waitTicks(20);

		Path offA = takeCleanScreenshot(context, "hall_schematic_off_a");
		context.waitTicks(5);
		Path offB = takeCleanScreenshot(context, "hall_schematic_off_b");

		context.runOnClient(mc -> {
			HallSchematicOverlay.toggle(core);
			HallSchematicOverlay.receive(new HallSchematicPayload(Level.OVERWORLD, core, true, ghosts.size(), ghosts));
		});
		context.waitTicks(5);
		Path on = takeCleanScreenshot(context, "hall_schematic_on");
		int[] baked = context.computeOnClient(mc -> new int[] {HallSchematicOverlay.ghostsAt(core).cellCount(),
				HallSchematicOverlay.ghostsAt(core).quadCount()});
		context.runOnClient(mc -> HallSchematicOverlay.clear());

		int noise = differingPixels(offA, offB);
		int signal = differingPixels(offA, on);
		LOG.info("[GUITEST][HALL_SCHEMATIC] ghosts={} baked={} quads={} noise={} signal={}", ghosts.size(), baked[0],
				baked[1], noise, signal);
		if (baked[0] != ghosts.size()) {
			throw new AssertionError("[GUITEST][HALL_SCHEMATIC] " + baked[0] + " of " + ghosts.size()
					+ " ghosts were baked into the frame data - a block's model or box was lost");
		}
		if (signal <= Math.max(200, noise * 10)) {
			throw new AssertionError("[GUITEST][HALL_SCHEMATIC] the town hall's schematic did not reach the frame: "
					+ signal + " px changed with the ghosts against " + noise + " between two frames without them. "
					+ explainWithDiff(offA, on));
		}
	}
}
