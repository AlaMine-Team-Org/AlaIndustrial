package dev.alaindustrial.teleporter;

import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TeleportFadePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What a jump looks and sounds like (MOD-106): the vortex, the rising whine, and the screen going
 * dark.
 *
 * <p>All of it is driven from the server tick, which is what makes it a scene rather than a private
 * effect: {@code ServerLevel#sendParticles} reaches every player in range, so a bystander watches
 * you wind up and vanish, and the jumper sees the same thing in third person. Only the screen fade
 * is per-player, since that one is about the jumper's eyes.
 *
 * <p>Kept out of {@link TeleportWarmupManager} on purpose — that class owns the rules of a jump
 * (who may, when, at what cost), and none of them should have to be read around particle maths.
 */
final class TeleportEffects {

	/** Fraction of the warmup, at the end, spent going dark. The last second, at any warmup length. */
	private static final float FADE_SECONDS = 1.0f;
	/** How often the charge-up whine repeats. Every tick would be a drone, not a build-up. */
	private static final int SOUND_EVERY_TICKS = 4;
	/** Particles on the ring per tick. Three is a vortex; more is a fog that hides the player. */
	private static final int RING_POINTS = 3;

	private TeleportEffects() {
	}

	/**
	 * One tick of wind-up.
	 *
	 * @param elapsed ticks since the warmup started
	 * @param total   ticks the warmup lasts ({@code Config.teleporterWarmupTicks})
	 */
	static void tickWarmup(ServerPlayer player, long elapsed, int total) {
		float progress = total <= 0 ? 1.0f : Math.min(1.0f, (float) elapsed / total);
		spawnVortex(player, progress);
		if (elapsed % SOUND_EVERY_TICKS == 0) {
			playCharge(player, progress);
		}
		sendFade(player, fadeFor(elapsed, total));
	}

	/** Darkness for this tick: nothing until the last second, then a straight ramp to full. */
	private static float fadeFor(long elapsed, int total) {
		float fadeTicks = FADE_SECONDS * 20.0f;
		// A warmup shorter than the fade window darkens across the whole of it rather than snapping.
		float start = Math.max(0.0f, total - fadeTicks);
		float span = Math.max(1.0f, total - start);
		return elapsed < start ? 0.0f : Math.min(1.0f, (elapsed - start) / span);
	}

	/**
	 * The purple funnel: a ring that tightens and climbs as the jump nears, around a rising core.
	 *
	 * <p>Vanilla's portal particles rather than a custom type — they are already the mod-neutral
	 * purple of teleportation, cost no new asset on either loader, and drift-and-shrink exactly like
	 * energy being pulled in. The mod's own particle (MOD-085) exists because a torch needed a colour
	 * vanilla has no flame for; that is not the case here.
	 */
	private static void spawnVortex(ServerPlayer player, float progress) {
		ServerLevel level = player.level();
		// Spin from world time so every watcher sees the same funnel turning.
		double spin = level.getGameTime() * 0.4;
		double radius = 1.5 - 1.1 * progress;
		double height = 0.2 + 1.7 * progress;

		for (int i = 0; i < RING_POINTS; i++) {
			double angle = spin + (Math.PI * 2.0 / RING_POINTS) * i;
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			// count 0 puts the particle exactly here and reads the three offsets as a velocity
			// (ClientPacketListener#handleParticleEvent) — the only way to aim each one inward.
			level.sendParticles(ParticleTypes.PORTAL, x, player.getY() + height, z, 0,
					-Math.cos(angle), 0.15, -Math.sin(angle), 0.6);
		}
		// The core, thickening as the funnel closes.
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.2, player.getZ(),
				1 + (int) (progress * 3.0f), 0.28, 0.05, 0.28, 0.04);
	}

	/** The charge-up: quiet and low at the start, loud and high by the jump. */
	private static void playCharge(ServerPlayer player, float progress) {
		float volume = 0.2f + 0.9f * progress;
		float pitch = 0.7f + 1.1f * progress;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, volume, pitch);
	}

	/** The jump landed: snap the screen back and thump at both ends of it. */
	static void arrived(ServerPlayer player) {
		sendFade(player, 0.0f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.level().sendParticles(ParticleTypes.REVERSE_PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
	}

	/**
	 * The warmup is over without a jump — clear the screen now.
	 *
	 * <p>Belt to the client's braces: {@code TeleportFadeHud} already clears itself once levels stop
	 * arriving, so this only makes it immediate. If it never lands, nothing is stuck.
	 */
	static void clear(ServerPlayer player) {
		sendFade(player, 0.0f);
	}

	private static void sendFade(ServerPlayer player, float strength) {
		NetworkDispatcher.get().sendToPlayer(player, new TeleportFadePayload(strength));
	}
}
