package dev.alaindustrial.core.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The pull every block and item of the mod applies to a loose drop (MOD-132, MOD-802): the electromagnet
 * draws drops to its carrier, the vacuum collector to itself.
 *
 * <p>One rule, written once: <em>set</em> the entity's velocity straight at the target at
 * {@code min(maxSpeed, dist × gain)} blocks per tick. Setting rather than adding overrides gravity and
 * ground friction instead of fighting them, so a drop below the target rises to it and one above sinks —
 * the accumulate-and-drag model of the experience orb was far too weak on grounded drops. The speed eases as
 * the drop arrives, so it does not jitter on top of the target, and stays under the pickup window so it does
 * not tunnel through.
 */
public final class DropSeek {

	private DropSeek() {
	}

	/**
	 * Seek {@code target}. Returns whether the entity was moved: one already within {@code snap} blocks is
	 * left alone, for whatever picks it up there.
	 */
	public static boolean seek(Entity pulled, Vec3 target, double maxSpeed, double gain, double snap) {
		Vec3 delta = new Vec3(target.x - pulled.getX(), target.y - pulled.getY(), target.z - pulled.getZ());
		double dist = delta.length();
		if (dist < snap) {
			return false;
		}
		double speed = Math.min(maxSpeed, dist * gain);
		pulled.setDeltaMovement(delta.scale(speed / dist)); // unit direction × speed
		return true;
	}
}
