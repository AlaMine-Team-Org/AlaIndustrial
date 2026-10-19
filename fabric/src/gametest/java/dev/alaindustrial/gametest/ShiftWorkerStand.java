package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.L3Chunks;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Four shift workers in a row (MOD-832): a man in the open helmet, a woman in the window helmet, the dark-skinned
 * faces M3 and F4 whose contrast MOD-832 raised — shot from the front and from behind, three and a half blocks
 * away, the way a player meets them. The frames are for a human to review (shot family {@code shift_worker_}):
 * the body follows the look (classic or slim), the hair stops above the plate, the window's glass is drawn over the
 * face, and the number on the balloon reads.
 *
 * <p>What the stand asserts on its own: the workers are drawn at all — the back frame with the workers differs from
 * the same view after they are gone in more than a twentieth of the picture.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ShiftWorkerStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Where the row stands. Clear of every other rig (the capsule is at x 236, the BER row at 238). */
	private static final int ROW_X = 262;
	private static final int ROW_Y = 101;
	private static final int ROW_Z = 150;
	/** The four looks of the row, left to right as the camera in front sees them. */
	private static final String[] LOOKS = {"open_m1", "window_f1", "open_m3", "window_f4"};

	private ShiftWorkerStand() {
	}

	public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("weather clear");
		server.runCommand("time set day");
		server.runCommand("gamemode spectator @p");
		// Loaded and kept loaded before the first command: a /fill into an unloaded chunk refuses the whole box.
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int cx = (ROW_X - 6) >> 4; cx <= (ROW_X + 6) >> 4; cx++) {
				for (int cz = (ROW_Z - 6) >> 4; cz <= (ROW_Z + 6) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		server.runCommand("fill " + (ROW_X - 6) + " " + ROW_Y + " " + (ROW_Z - 6) + " " + (ROW_X + 6) + " "
				+ (ROW_Y + 4) + " " + (ROW_Z + 6) + " minecraft:air");
		server.runCommand("fill " + (ROW_X - 6) + " " + (ROW_Y - 1) + " " + (ROW_Z - 6) + " " + (ROW_X + 6) + " "
				+ (ROW_Y - 1) + " " + (ROW_Z + 6) + " minecraft:smooth_stone");
		List<ShiftWorker> row = new ArrayList<>();
		server.runOnServer(mc -> row.addAll(spawnRow(mc.overworld())));
		if (row.size() != LOOKS.length) {
			throw new AssertionError("[GUITEST][SHIFT_WORKER] only " + row.size() + " of " + LOOKS.length
					+ " workers were made");
		}

		shoot(context, singleplayer, (ROW_Z + 3.5), 180, "shift_worker_front");
		Path back = shoot(context, singleplayer, (ROW_Z - 3.5), 0, "shift_worker_back");
		server.runOnServer(mc -> row.forEach(ShiftWorker::discard));
		context.waitTicks(5);
		Path empty = shoot(context, singleplayer, (ROW_Z - 3.5), 0, "shift_worker_back_empty");

		int differing = differingPixels(back, empty);
		int required = pixelCount(back) / 20;
		LOG.info("[GUITEST][SHIFT_WORKER] back with workers vs without: {} px differ, required > {}", differing,
				required);
		if (differing <= required) {
			throw new AssertionError("[GUITEST][SHIFT_WORKER] the back frame with four workers differs from the empty "
					+ "one in only " + differing + " px - the workers are not being drawn. "
					+ explainWithDiff(back, empty));
		}
	}

	/** The row, facing south (toward the camera of the front frame), goals off so nobody turns to look. */
	private static List<ShiftWorker> spawnRow(ServerLevel level) {
		List<ShiftWorker> row = new ArrayList<>();
		for (int i = 0; i < LOOKS.length; i++) {
			BlockPos at = new BlockPos(ROW_X - 3 + 2 * i, ROW_Y, ROW_Z);
			ShiftWorker worker = ShiftWorkers.spawn(level, at, 0).orElse(null);
			if (worker == null) {
				continue;
			}
			ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
			ShiftWorkerRecord record = book.byEntity(worker.getUUID()).orElseThrow()
					.withLook(Industrialization.id(LOOKS[i]), LOOKS[i].contains("_f"));
			book.put(record);
			worker.showRecord(record);
			worker.setNoAi(true);
			worker.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
			worker.setYBodyRot(0.0F);
			worker.setYHeadRot(0.0F);
			row.add(worker);
		}
		return row;
	}

	private static int pixelCount(Path frame) {
		try {
			BufferedImage image = ImageIO.read(frame.toFile());
			if (image == null) {
				throw new AssertionError("[GUITEST][SHIFT_WORKER] could not decode " + frame);
			}
			return image.getWidth() * image.getHeight();
		} catch (IOException e) {
			throw new AssertionError("[GUITEST][SHIFT_WORKER] could not read " + frame, e);
		}
	}

	private static Path shoot(ClientGameTestContext context, TestSingleplayerContext singleplayer, double z, int yaw,
			String name) {
		singleplayer.getServer().runCommand("tp @p " + (ROW_X + 0.5) + " " + (ROW_Y + 0.4) + " " + z + " " + yaw
				+ " 8");
		L3Chunks.waitRender(singleplayer);
		context.waitTicks(10);
		return takeCleanScreenshot(context, name);
	}
}
