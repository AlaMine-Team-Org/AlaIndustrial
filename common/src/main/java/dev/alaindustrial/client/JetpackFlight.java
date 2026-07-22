package dev.alaindustrial.client;

import dev.alaindustrial.item.JetpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client half of the jetpack (MOD-148): player motion is client-authoritative in 26.2, so the
 * velocity change lives here, ticked from both loaders' end-of-client-tick hooks (the same trio the
 * key handling uses). The server never applies motion — it reads the identical held-jump state from
 * the vanilla input sync and burns the EU / resets the fall distance on its side
 * ({@link JetpackItem#serverFlightTick}); the charge component is network-synchronized, so both
 * sides evaluate {@link JetpackItem#isPowered} against the same number.
 *
 * <p>Thrust is an acceleration, not a set speed: gravity keeps pulling, releasing jump starts a
 * normal fall, and the rise speed saturates at {@link #MAX_RISE_SPEED} so the ascent reads as an
 * engine, not an elevator. The powerless glide clamps only the downward speed — sideways momentum
 * (and an updraft) is left alone.
 */
public final class JetpackFlight {

	/** Upward acceleration per thrust tick — comfortably over vanilla gravity (~0.08/tick). */
	private static final double THRUST_ACCELERATION = 0.15;
	/** Ascent speed cap, blocks/tick (~10 m/s). */
	private static final double MAX_RISE_SPEED = 0.5;
	/** Sink-rate floor of the powerless glide, blocks/tick (~6 m/s down, slow enough to land soft). */
	private static final double GLIDE_FALL_SPEED = -0.3;

	/**
	 * Client mirror of the server's airborne-thrust session: the powerless glide only engages if the
	 * engine actually fired during THIS airborne stretch (an empty jetpack must fall like no jetpack
	 * at all — playtest feedback). Cleared on the ground; local player only, so a plain boolean.
	 */
	private static boolean thrustedThisFlight;

	private JetpackFlight() {
	}

	/** One end-of-client-tick step; registered by both client entrypoints. */
	public static void clientTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof JetpackItem)) {
			thrustedThisFlight = false;
			return;
		}
		if (player.onGround()) {
			thrustedThisFlight = false;
			return;
		}
		// Creative/spectator flight and riding have their own movement authorities — stay out.
		if (player.getAbilities().flying || player.isSpectator() || player.isPassenger()) {
			return;
		}
		// The engine only runs airborne while jump is held; a held jump on the ground is a vanilla
		// jump, the thrust picks up on the next tick. Mirrors JetpackItem.serverFlightTick.
		if (!player.input.keyPresses.jump()) {
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		if (JetpackItem.isPowered(chest, player)) {
			thrustedThisFlight = true;
			player.setDeltaMovement(velocity.x,
					Math.min(velocity.y + THRUST_ACCELERATION, MAX_RISE_SPEED), velocity.z);
			// The client accumulates its own fallDistance; left alone it plays the fall-landing thud
			// on touchdown even though the server dealt no damage (playtest feedback). Reset it in
			// step with the server's reset.
			player.resetFallDistance();
		} else if (thrustedThisFlight) {
			// Glide after a mid-flight cutout: damp the sink rate and keep the landing silent. An
			// engine that never fired this flight gives neither — the empty jetpack falls normally.
			if (velocity.y < GLIDE_FALL_SPEED) {
				player.setDeltaMovement(velocity.x, GLIDE_FALL_SPEED, velocity.z);
			}
			player.resetFallDistance();
		}
	}
}
