package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.TeleporterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * MOD-632 — on NeoForge the teleporter capsule's door vanished whenever the player inside looked ahead.
 *
 * <p>NeoForge culls a block entity renderer against {@code getRenderBoundingBox} before anything else,
 * and the default box is the station's block, which sits under the arriving player's feet. The fix is a
 * box around the whole capsule. Two things have to hold for that to work, and each fails silently on its
 * own: the box has to contain the player's eyes — checked here — and NeoForge has to be calling our method at
 * all. The second is not specific to the capsule: every renderer that draws beyond its block needs it, and
 * {@code dev.alaindustrial.arch.OffScreenRendererBoxTest} checks it for all of them (MOD-633).
 */
class TeleporterCapsuleCullingTest {

	/** A player standing, in blocks (vanilla's standing eye height). */
	private static final double STANDING_EYE_HEIGHT = 1.62;
	/** The top of the door, in blocks above the station: {@code TeleporterCapsuleDoorRenderer.DOOR_TOP}. */
	private static final double DOOR_TOP = 40.0 / 16.0;

	@Test
	void capsuleBoundsHoldTheArrivingPlayersEyesAndTheWholeDoor() {
		BlockPos station = new BlockPos(12, 64, -7);
		AABB box = TeleporterBlock.capsuleBounds(station);
		Vec3 eyes = new Vec3(station.getX() + 0.5,
				station.getY() + TeleporterBlock.CAPSULE_FLOOR + STANDING_EYE_HEIGHT, station.getZ() + 0.5);

		assertTrue(box.contains(eyes), "the eyes of a player standing in the capsule (" + eyes + ") are outside "
				+ box + " - from there the box leaves the view as soon as they look away from it, and NeoForge "
				+ "stops drawing the door");
		assertTrue(box.minY <= station.getY() && box.maxY >= station.getY() + DOOR_TOP,
				box + " does not reach from the station's floor to the top of the door");
	}
}
