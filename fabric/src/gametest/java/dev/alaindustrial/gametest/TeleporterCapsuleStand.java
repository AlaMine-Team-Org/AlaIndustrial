package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.differingPixels;
import static dev.alaindustrial.gametest.VisualStandSupport.explainWithDiff;
import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.TeleporterCapsuleBlock;
import dev.alaindustrial.compat.L3Chunks;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The teleporter capsule seen the way an arriving player sees it (MOD-632): standing on the capsule
 * floor, facing the door, looking ahead and looking down.
 *
 * <p>From there almost all the glass in view is the door, and the door is not chunk geometry but
 * {@code TeleporterCapsuleDoorRenderer}. So the gate compares the same view of a clear and of a purple
 * capsule: with the door drawn, the tint changes nearly every pixel; without it the two frames would show
 * the same floor and the same steel crown and barely differ.
 *
 * <p><b>What this lane cannot catch.</b> The defect that started it was NeoForge culling the renderer by
 * the station's own block, and this lane runs on Fabric, where vanilla never tests a block entity against
 * the frustum. That cause is guarded on the NeoForge side by {@code TeleporterCapsuleCullingTest} (the box
 * holds the arriving player's eyes) and {@code OffScreenRendererBoxTest} (NeoForge calls that box); this
 * stand guards the rest — a door that stops being drawn from inside for any other reason.
 *
 * <p><b>Water behind the shut door (MOD-777).</b> A block entity's translucent geometry is drawn before
 * the translucent terrain layer, so a door glass that wrote depth hid every water surface behind it. The
 * stand puts a pool ahead of the capsule and compares the view through the shut door with the same view
 * of the drained pool: with the water drawn, the pool region changes; with it hidden, the two frames show
 * the same empty basin.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TeleporterCapsuleStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Where the station stands. Clear of every other rig. */
	private static final int STATION_X = 236;
	private static final int STATION_Y = 101;
	private static final int STATION_Z = 150;
	private static final BlockPos STATION = new BlockPos(STATION_X, STATION_Y, STATION_Z);

	/** Pitches of the inside frames: straight ahead, and two looks down. */
	private static final int[] PITCHES = {0, 15, 30};
	/**
	 * The look down at the pool. From the capsule floor the pool lies 33 to 57 degrees below the horizon,
	 * seen through the door glass; at this pitch the frame spans 5 to 75 degrees, with no sky in it.
	 */
	private static final int POOL_PITCH = 40;

	private TeleporterCapsuleStand() {
	}

	public static void checkGlassFromInside(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("weather clear");
		server.runCommand("time set day");
		server.runCommand("gamemode spectator @p");
		// Loaded synchronously and kept loaded before the first command: a /fill that touches an unloaded
		// chunk refuses the whole box and only logs "That position is not loaded" (MOD-610).
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			for (int cx = (STATION_X - 4) >> 4; cx <= (STATION_X + 4) >> 4; cx++) {
				for (int cz = (STATION_Z - 4) >> 4; cz <= (STATION_Z + 4) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
				}
			}
		});
		server.runCommand("tp @p " + (STATION_X + 0.5) + " " + (STATION_Y + 1) + " " + (STATION_Z + 5.5) + " 180 0");
		L3Chunks.waitRender(singleplayer);
		server.runCommand("fill " + (STATION_X - 4) + " " + STATION_Y + " " + (STATION_Z - 4) + " "
				+ (STATION_X + 4) + " " + (STATION_Y + 5) + " " + (STATION_Z + 4) + " minecraft:air");
		server.runCommand("fill " + (STATION_X - 4) + " " + (STATION_Y - 1) + " " + (STATION_Z - 4) + " "
				+ (STATION_X + 4) + " " + (STATION_Y - 1) + " " + (STATION_Z + 4) + " minecraft:smooth_stone");

		Path clear = shootGlass(context, singleplayer, "clear", "minecraft:glass");
		Path purple = shootGlass(context, singleplayer, "purple", "minecraft:purple_stained_glass");

		int differing = differingPixels(clear, purple);
		int required = pixelCount(clear) / 2;
		LOG.info("[GUITEST][CAPSULE] clear vs purple from inside, looking ahead: {} px differ, required > {}",
				differing, required);
		if (differing <= required) {
			throw new AssertionError("[GUITEST][CAPSULE] from inside the capsule, looking ahead, a clear and a "
					+ "purple capsule differ in only " + differing + " px (required > " + required + ", half the "
					+ "frame) - the door glass in front of the player is not being drawn. Check that "
					+ "TeleporterCapsuleDoorRenderer still submits its glass for a shut door and is not culled. "
					+ explainWithDiff(clear, purple));
		}
		checkWaterBehindDoor(context, singleplayer);
	}

	/**
	 * The water of a pool ahead of the capsule stays visible through the shut door (MOD-777).
	 *
	 * <p>The basin is black concrete on purpose. Water dims the light on the blocks under it; on a pale
	 * floor that alone can move the pixels past the tolerance, and the drained frame would differ from a
	 * frame whose water was hidden. On black the light change stays inside the tolerance, and only the drawn
	 * water surface can make the two frames differ.
	 */
	private static void checkWaterBehindDoor(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TestServerContext server = singleplayer.getServer();
		assemble(context, singleplayer, "clear", "minecraft:glass");
		// The pool: two rows of water north of the capsule, where the shut door faces, one block deep.
		server.runCommand("fill " + (STATION_X - 4) + " " + (STATION_Y - 2) + " " + (STATION_Z - 4) + " "
				+ (STATION_X + 4) + " " + (STATION_Y - 1) + " " + (STATION_Z - 1) + " minecraft:black_concrete");
		String pool = "fill " + (STATION_X - 3) + " " + (STATION_Y - 1) + " " + (STATION_Z - 3) + " "
				+ (STATION_X + 3) + " " + (STATION_Y - 1) + " " + (STATION_Z - 2) + " ";
		server.runCommand(pool + "minecraft:water");
		Path water = shootPool(context, singleplayer, "capsule_water_behind_door");
		server.runCommand(pool + "minecraft:air");
		Path drained = shootPool(context, singleplayer, "capsule_water_drained");
		server.runCommand("fill " + (STATION_X - 4) + " " + (STATION_Y - 2) + " " + (STATION_Z - 4) + " "
				+ (STATION_X + 4) + " " + (STATION_Y - 1) + " " + (STATION_Z - 1) + " minecraft:smooth_stone");

		int differing = differingPixels(water, drained);
		int required = pixelCount(water) / 10;
		LOG.info("[GUITEST][CAPSULE] pool with water vs drained, through the shut door: {} px differ, required > {}",
				differing, required);
		if (differing <= required) {
			throw new AssertionError("[GUITEST][CAPSULE] through the shut capsule door, a pool with water and the "
					+ "same pool drained differ in only " + differing + " px (required > " + required + ", a tenth "
					+ "of the frame) - the water behind the door glass is not being drawn. Check that the door "
					+ "glass of TeleporterCapsuleDoorRenderer still uses a render type that writes no depth "
					+ "(TranslucentTypes.blockSheetNoDepthWrite). " + explainWithDiff(water, drained));
		}
	}

	/** From the capsule floor, facing the shut door, looking down at the pool. */
	private static Path shootPool(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name) {
		singleplayer.getServer().runCommand("tp @p " + (STATION_X + 0.5) + " "
				+ (STATION_Y + TeleporterBlock.CAPSULE_FLOOR) + " " + (STATION_Z + 0.5) + " 180 " + POOL_PITCH);
		L3Chunks.waitRender(singleplayer);
		context.waitTicks(10);
		return takeCleanScreenshot(context, name);
	}

	/** Builds a capsule of {@code glassBlock} and shoots it; returns the frame looking straight ahead. */
	private static Path shootGlass(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name,
			String glassBlock) {
		TestServerContext server = singleplayer.getServer();
		assemble(context, singleplayer, name, glassBlock);

		Path ahead = null;
		for (int pitch : PITCHES) {
			// An arriving player: on the capsule floor, centred, facing the door.
			server.runCommand("tp @p " + (STATION_X + 0.5) + " " + (STATION_Y + TeleporterBlock.CAPSULE_FLOOR) + " "
					+ (STATION_Z + 0.5) + " 180 " + pitch);
			L3Chunks.waitRender(singleplayer);
			context.waitTicks(10);
			Path frame = takeCleanScreenshot(context, "capsule_inside_" + name + "_pitch" + pitch);
			if (pitch == 0) {
				ahead = frame;
			}
		}
		// The same capsule from outside, facing its door: the renderer's box change must not show here.
		server.runCommand("tp @p " + (STATION_X + 0.5) + " " + (STATION_Y + 0.4) + " " + (STATION_Z - 3.0) + " 0 10");
		L3Chunks.waitRender(singleplayer);
		context.waitTicks(10);
		takeCleanScreenshot(context, "capsule_outside_" + name);
		return ahead;
	}

	/** Places a capsule of {@code glassBlock} on the station and fails unless the client sees it formed. */
	private static void assemble(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name,
			String glassBlock) {
		TestServerContext server = singleplayer.getServer();
		server.runCommand("fill " + STATION_X + " " + STATION_Y + " " + STATION_Z + " "
				+ STATION_X + " " + (STATION_Y + 2) + " " + STATION_Z + " minecraft:air");
		server.runCommand("setblock " + STATION_X + " " + STATION_Y + " " + STATION_Z
				+ " alaindustrial:teleporter[facing=north]");
		server.runCommand("setblock " + STATION_X + " " + (STATION_Y + 1) + " " + STATION_Z + " " + glassBlock);
		server.runCommand("setblock " + STATION_X + " " + (STATION_Y + 2) + " " + STATION_Z + " " + glassBlock);
		// Placed by command, so nothing calls setPlacedBy; the assembly is asked for directly.
		server.runOnServer(mc -> TeleporterBlock.tryAssemble(mc.overworld(), STATION));
		context.waitTicks(5);

		// Renderer gate: the door is drawn only for a formed station with a capsule cell above it. Without
		// this, a capsule that failed to assemble would be photographed as two plain glass blocks.
		boolean[] formed = new boolean[1];
		context.runOnClient(mc -> formed[0] = mc.level != null
				&& TeleporterBlock.isFormed(mc.level.getBlockState(STATION))
				&& mc.level.getBlockState(STATION.above()).getBlock() instanceof TeleporterCapsuleBlock);
		if (!formed[0]) {
			throw new AssertionError("[GUITEST][CAPSULE] the " + name + " capsule at " + STATION
					+ " did not assemble on the client - the frames would show loose glass, not the capsule");
		}
	}

	private static int pixelCount(Path frame) {
		try {
			BufferedImage image = ImageIO.read(frame.toFile());
			if (image == null) {
				throw new AssertionError("[GUITEST][CAPSULE] could not decode " + frame);
			}
			return image.getWidth() * image.getHeight();
		} catch (IOException e) {
			throw new AssertionError("[GUITEST][CAPSULE] could not read " + frame, e);
		}
	}
}
