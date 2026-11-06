package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.core.quarry.QuarryMotion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What the quarry's hot drill shows (MOD-952, owner 2026-11-06): the heat bar alone did not tell a player at the pit
 * why the drill stood. Smoke rises from the head once it is half way hot, an overheated head smokes thick, spits
 * sparks and hisses, and water cooling it goes up as steam. Sent from the server, so every player near the pit sees
 * the same.
 */
public final class QuarryDrillEffects {

	private static final int HALF = 500;

	private QuarryDrillEffects() {
	}

	/**
	 * One tick of effects at the drill's head. {@code heatPermille} is the heat's share of the scale, {@code steaming}
	 * whether water took heat this tick.
	 */
	public static void tick(ServerLevel level, QuarryFrameLayout layout, QuarryDigger digger, int heatPermille,
			boolean overheated, boolean steaming) {
		if (!digger.placed()) {
			return;
		}
		long time = level.getGameTime();
		QuarryMotion.Pose pose = digger.pose(time);
		double x = layout.alongX() ? pose.along() : pose.across();
		double z = layout.alongX() ? pose.across() : pose.along();
		double y = Math.min(pose.tip(), layout.baseY() + QuarryFramePlacements.drillRestingTip())
				+ QuarryFramePlacements.drillHeadHeight() * 0.5D;
		if (overheated) {
			if (time % 4 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.4, 0.3, 0.4, 0.01);
			}
			if (time % 10 == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.3, 0.2, 0.3, 0.0);
			}
			if (time % 40 == 0) {
				level.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
			}
		} else if (heatPermille >= HALF && time % Math.max(2, 12 - heatPermille / 100) == 0) {
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.35, 0.3, 0.35, 0.01);
		}
		if (steaming && time % 4 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, x, y + 0.3D, z, 1, 0.3, 0.2, 0.3, 0.02);
		}
	}
}
