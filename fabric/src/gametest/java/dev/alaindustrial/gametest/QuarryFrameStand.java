package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.block.QuarryControllerBlock;
import dev.alaindustrial.command.demo.QuarryStand;
import dev.alaindustrial.compat.L3Chunks;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.gametest.visual.VisualWorld;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The formed quarry frame reaches the frame as the designer's model (MOD-950): the operator's quarry stand — a 9 × 9
 * frame of tempered-iron foundations with its power line — built by {@link QuarryStand}, the code behind
 * {@code /ala demo quarry build}, and seen from the stand's own camera. Frames for a human to review (shot family
 * {@code quarry_frame_}): formed, the four towers with their lattices, the two paths, the bridge with its saddles
 * and the cart, and none of the cubes under them; loose, the same view with one far tower cut short, every block back.
 *
 * <p>What the stand asserts on its own: the frame forms on the server, and the model is drawn at all — the formed
 * frame differs from the loose one by far more than two formed frames differ from each other.
 */
@SuppressWarnings("UnstableApiUsage")
public final class QuarryFrameStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** The arena's north-west corner, on its floor. Clear of every other rig (the town hall wall is at x 286). */
	private static final BlockPos ORIGIN = new BlockPos(344, 100, 140);
	/** How long the controller gets to scan and the client to receive the frame. */
	private static final int SETTLE_TICKS = 50;
	/** How long the client may take to light the frame's air, and what full daylight reads. */
	private static final int LIGHT_WAIT_TICKS = 400;
	private static final int FULL_SKY = 15;

	private QuarryFrameStand() {
	}

	public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("weather clear");
		server.runCommand("time set day");
		server.runCommand("gamemode spectator @p");
		// Loaded and kept loaded before the build: the stand writes blocks one by one into the whole arena.
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			int span = QuarryStand.WIDTH + 16;
			for (int cx = (ORIGIN.getX() - 8) >> 4; cx <= (ORIGIN.getX() + span) >> 4; cx++) {
				for (int cz = (ORIGIN.getZ() - 8) >> 4; cz <= (ORIGIN.getZ() + span) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		// The camera first, the build after, the way an operator stands by the arena: a chunk the client first
		// receives while the server is still lighting the new blocks keeps a section without daylight.
		double[] at = QuarryStand.camera(ORIGIN);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %.1f %.1f -45 30", at[0], at[1], at[2]));
		VisualWorld.awaitNoScreen(context);
		L3Chunks.waitRender(singleplayer);
		server.runOnServer(mc -> QuarryStand.build(mc.overworld(), ORIGIN));
		context.waitTicks(SETTLE_TICKS);

		BlockPos controller = QuarryStand.controller(ORIGIN);
		boolean formed = server.computeOnServer(mc ->
				mc.overworld().getBlockState(controller).getValue(QuarryControllerBlock.FORMED));
		if (!formed) {
			throw new AssertionError("[GUITEST][QUARRY_FRAME] the stand's frame did not form within " + SETTLE_TICKS
					+ " ticks; the scan reports what is wrong on a right click at " + controller);
		}
		// The model takes its light from the open air above the frame. A chunk the client received before the server
		// had lit the new blocks stays dark there (seen when the build came before the camera), and the model would
		// be shot black - so the frame is shot only in daylight.
		BlockPos sky = controller.above(QuarryFrameScan.HEAD_LAYERS);
		int waited = 0;
		while (context.computeOnClient(mc -> mc.level.getBrightness(LightLayer.SKY, sky)) < FULL_SKY) {
			if (waited >= LIGHT_WAIT_TICKS) {
				throw new AssertionError("[GUITEST][QUARRY_FRAME] no daylight reached the client over " + sky
						+ " within " + LIGHT_WAIT_TICKS + " ticks; the frame would be shot black");
			}
			context.waitTicks(10);
			waited += 10;
		}
		LOG.info("[GUITEST][QUARRY_FRAME] daylight over the frame after {} more ticks", waited);
		Path formedA = takeCleanScreenshot(context, "quarry_frame_formed");
		context.waitTicks(5);
		Path formedB = takeCleanScreenshot(context, "quarry_frame_formed_again");

		// The far tower's top head layer away: the frame comes apart on the next sweep and every block shows again.
		BlockPos far = QuarryStand.farTowerTop(ORIGIN);
		server.runCommand("fill " + far.getX() + " " + far.getY() + " " + far.getZ() + " " + (far.getX() + 2) + " "
				+ far.getY() + " " + (far.getZ() + 2) + " minecraft:air");
		context.waitTicks(SETTLE_TICKS);
		boolean stillFormed = server.computeOnServer(mc ->
				mc.overworld().getBlockState(controller).getValue(QuarryControllerBlock.FORMED));
		if (stillFormed) {
			throw new AssertionError("[GUITEST][QUARRY_FRAME] the frame stayed formed with a far head layer gone");
		}
		Path loose = takeCleanScreenshot(context, "quarry_frame_loose");

		int noise = differingPixels(formedA, formedB);
		int signal = differingPixels(formedA, loose);
		LOG.info("[GUITEST][QUARRY_FRAME] noise={} signal={}", noise, signal);
		if (signal <= Math.max(2000, noise * 10)) {
			throw new AssertionError("[GUITEST][QUARRY_FRAME] the formed frame did not reach the frame as the "
					+ "designer's model: " + signal + " px differ between formed and loose against " + noise
					+ " between two formed frames. " + explainWithDiff(formedA, loose));
		}
		server.runOnServer(mc -> QuarryStand.clear(mc.overworld(), ORIGIN));
	}
}
