package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.WaterMillWheelGeometry;
import dev.alaindustrial.core.environment.WindMillRotorGeometry;
import dev.alaindustrial.core.machine.WornComponentCue;
import dev.alaindustrial.core.machine.WornCueKind;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The warning a running wind or water mill gives when its rotor / wheel is nearly spent (MOD-654): now and
 * then a creak and, from the hub of the blade disc, smoke (splinters from the rim of the wheel on the water
 * mill), more often the less is left. One component of the generator base; the decision itself is the pure
 * {@link WornComponentCue}, this class owns the cooldown and sends the effects.
 *
 * <p>Server-side only: the server sends the sound and the particles, the client keeps no wear state. The
 * cooldown is transient (never saved) and starts at a random point inside the first pause, so a farm of mills
 * that wore out together, or a server that has just restarted, does not creak in chorus. The caller ticks it
 * only on a tick the mill really produced, so a stopped mill (no wind, no water) never sounds.
 *
 * <p>Only the {@code sendParticles} overloads that Minecraft 26.2 shares with 26.3: the 9-argument one and the
 * one with the {@code force} / {@code overrideLimiter} flags (verified with javap on both jars).
 */
public final class WornComponentVoice {
	/** -1 = not armed (the component is not worn): the next worn tick draws the first pause. */
	private int cooldown = -1;
	private int cuesSent;

	/**
	 * One producing tick of a mill whose component is {@code stack} (after this tick's wear step).
	 *
	 * @param muted whether a mute chip silences the sound (the particles stay)
	 */
	public void tick(ServerLevel level, BlockPos pos, Direction facing, WornCueKind kind, ItemStack stack,
			boolean muted) {
		if (kind == WornCueKind.NONE || !GeneratorConfig.wornComponentCueEnabled || stack.isEmpty()) {
			cooldown = -1;
			return;
		}
		int damage = stack.getDamageValue();
		int max = stack.getMaxDamage();
		int percent = GeneratorConfig.wornComponentCueThresholdPercent;
		int cap = GeneratorConfig.wornComponentCueCapPoints;
		if (!WornComponentCue.isWorn(damage, max, percent, cap)) {
			cooldown = -1; // repaired or still fresh: silent, and the next crossing starts a new random pause
			return;
		}
		int slow = GeneratorConfig.wornComponentCueSlowTicks;
		int fast = GeneratorConfig.wornComponentCueFastTicks;
		RandomSource random = level.getRandom();
		if (cooldown < 0) {
			cooldown = WornComponentCue.firstDelay(damage, max, percent, cap, slow, fast, random.nextFloat());
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = WornComponentCue.nextDelay(damage, max, percent, cap, slow, fast, random.nextFloat());
		boolean critical = WornComponentCue.isCritical(damage, max, percent, cap);
		int puffs = WornComponentCue.puffCount(damage, max, percent, cap);
		emit(level, pos, facing, kind, critical, puffs, muted, random);
		cuesSent++;
	}

	private void emit(ServerLevel level, BlockPos pos, Direction facing, WornCueKind kind, boolean critical,
			int puffs, boolean muted, RandomSource random) {
		double push = kind == WornCueKind.ROTOR ? WindMillRotorGeometry.DISC_PUSH : WaterMillWheelGeometry.DISC_PUSH;
		double cx = pos.getX() + 0.5 + facing.getStepX() * push;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5 + facing.getStepZ() * push;
		if (!muted) {
			level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					ModSounds.WORN_ROTOR_CREAK.get(), SoundSource.BLOCKS, 0.5f + 0.2f * random.nextFloat(),
					0.9f + 0.2f * random.nextFloat());
		}
		// The disc spans the sideways axis and the vertical; the sideways vector is (|fz|, 0, |fx|).
		double sideX = Math.abs(facing.getStepZ());
		double sideZ = Math.abs(facing.getStepX());
		if (kind == WornCueKind.ROTOR) {
			// Campfire-style clouds: large, slow, rising a long way, so the smoke is seen from the ground
			// below a tall tower. count = 0 makes the offset a velocity, so every cloud is sent on its own
			// with an upward drift; force + overrideLimiter send it past the normal particle distance.
			for (int i = 0; i < puffs; i++) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true,
						cx + (random.nextDouble() - 0.5) * 0.5 * sideX,
						cy + (random.nextDouble() - 0.5) * 0.5,
						cz + (random.nextDouble() - 0.5) * 0.5 * sideZ,
						0, (random.nextDouble() - 0.5) * 0.02, 0.07 + 0.03 * random.nextDouble(),
						(random.nextDouble() - 0.5) * 0.02, 1.0);
			}
			// Ragged wisps around the hub.
			level.sendParticles(ParticleTypes.SMOKE, true, true, cx, cy, cz, 6, 0.05 + 0.35 * sideX, 0.35,
					0.05 + 0.35 * sideZ, 0.02);
			if (critical) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, cx, cy, cz, 2, 0.05 + 0.3 * sideX, 0.3,
						0.05 + 0.3 * sideZ, 0.02);
			}
		} else {
			// Splinters fly off the rim of the wheel, a few from different points.
			double r = WaterMillWheelGeometry.DISC_HALF_SIZE;
			ParticleOptions splinters = new BlockParticleOption(ParticleTypes.BLOCK,
					Blocks.OAK_PLANKS.defaultBlockState());
			for (int i = 0; i < 3; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double px = cx + sideX * Math.cos(angle) * r;
				double py = cy + Math.sin(angle) * r;
				double pz = cz + sideZ * Math.cos(angle) * r;
				level.sendParticles(splinters, px, py, pz, 6, 0.1, 0.1, 0.1, 0.05);
			}
		}
	}

	/** Cues sent since load: "is the warning being sent" — for the gametests. Never saved. */
	public int cuesSent() {
		return cuesSent;
	}
}
