package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The clicking of the Geiger counter (MOD-475) — the only feedback radiation has ever had.
 *
 * <p>Radiation itself is swept once per {@code radiationTickInterval} (a second), and that cadence is
 * the reason this class exists instead of a few lines inside {@link RadiationTicker}. A sweep offers
 * exactly one chance to make a sound per second, so a "denser rattle" is unreachable from it: the top
 * step would sound the same as the bottom one. So the sweep decides the STEP and this class spends it,
 * tick by tick.
 *
 * <p><b>The sound is addressed to one player, not played into the world.</b> {@code
 * Level#playSound(null, …)} reaches everyone nearby, which in multiplayer turns two counters into four
 * clicks and makes a player's instrument audible to someone who does not own one. A targeted {@link
 * ClientboundSoundPacket} is the mod's existing answer to that (see {@code PlayerStatsTracker}), and
 * it keeps the subtitle: the client resolves that from the sound's own id, not from the packet.
 *
 * <p><b>Clicks are stochastic, not a metronome.</b> A perfectly regular repeat reads as a background
 * loop within a minute and stops being heard — the lesson already written down on the reactor's
 * critical alarm. Each tick rolls against the step's chance and jitters the pitch, so the instrument
 * sounds like an instrument rather than a clock.
 */
public final class GeigerTicker {

	/**
	 * Chance of a click on any given tick, by step. Index 0 is silence.
	 *
	 * <p>Three clicks a second at step 1, eight at step 2, fifteen at step 3, and a continuous rattle
	 * at the top. The gap between steps is wide on purpose: the player is meant to hear the difference
	 * without counting.
	 *
	 * <p><b>The first version was five times quieter and it read as broken.</b> At one click a second
	 * the bottom step is indistinguishable from an instrument that is simply not working — tested in
	 * game, standing beside a chest that was already dealing damage. A detector's lowest rung has to
	 * sound like a detector, not like a fault.
	 */
	private static final float[] CHANCE_BY_STEP = { 0.0f, 0.15f, 0.4f, 0.75f, 1.0f };

	/**
	 * Chance per tick for the ORE signal, which lives below the hazard scale on purpose.
	 *
	 * <p>Its top grade stays just under the hazard scale's first, so a rich vein can never be mistaken
	 * for danger however close the player stands. Together with the lower pitch this is what keeps the
	 * two meanings apart in the ear — the pitch says WHICH signal, the rate says how much.
	 */
	private static final float[] ORE_CHANCE_BY_STEP = { 0.0f, 0.04f, 0.08f, 0.14f };

	/** Ore reads a tone lower than hazard — same instrument, unmistakably different signal. */
	private static final float ORE_PITCH = 0.82f;

	/** Pitch spread around 1.0 — enough to sound mechanical, not enough to sound like a different tool. */
	private static final float PITCH_JITTER = 0.08f;

	/**
	 * A reading and the server tick it was taken on.
	 *
	 * <p>The timestamp is not bookkeeping, it is the safety net. The sweep that produces readings has
	 * several early exits — radiation switched off in the config, a creative or spectator player, a
	 * world with no players in it — and every one of them leaves the last reading behind. Without an
	 * age, a player who flips to creative next to a reactor keeps hearing a rattle forever, and so does
	 * everyone in a world where an operator has just turned radiation off. Anything older than a couple
	 * of sweeps is treated as no reading at all, which turns every one of those exits into silence
	 * without the sweep having to know this class exists.
	 */
	private record Reading(int step, int oreStep, long takenAt) {
	}

	/**
	 * What each player's counter is currently reading, refreshed once per sweep.
	 *
	 * <p>Server-side and per-player; an entry is dropped as soon as the reading is zero, so a world
	 * where nobody carries a counter holds an empty map and this class costs one {@code isEmpty()} per
	 * tick.
	 */
	private static final Map<UUID, Reading> STEPS = new HashMap<>();

	private GeigerTicker() {
	}

	/**
	 * Record what this player's counter reads. Called from the sweep, which already knows the field.
	 *
	 * @param step 0 (silent) to 4 (off the scale), from {@link RadiationCore#geigerStep}
	 */
	public static void setStep(ServerPlayer player, int step, int oreStep) {
		if (step <= 0 && oreStep <= 0) {
			STEPS.remove(player.getUUID());
			return;
		}
		STEPS.put(player.getUUID(), new Reading(
				Math.clamp(step, 0, CHANCE_BY_STEP.length - 1),
				Math.clamp(oreStep, 0, ORE_CHANCE_BY_STEP.length - 1),
				player.level().getGameTime()));
	}

	/** Forget a player — called when they leave, so the map cannot outlive the session. */
	public static void forget(UUID player) {
		STEPS.remove(player);
	}

	/**
	 * Every tick: spend the reading left by the last sweep, while it is still fresh.
	 *
	 * <p>Deliberately does NOT look in anybody's inventory. That question is answered once per sweep,
	 * where the answer is already needed; asking it here would walk forty-odd slots twenty times a
	 * second to learn something that changes at most once. A counter put into a chest therefore goes
	 * quiet within a sweep rather than instantly — a second of tail on a sound that is itself
	 * intermittent, and the price of not paying that walk every tick.
	 */
	public static void tick(MinecraftServer server) {
		if (STEPS.isEmpty()) {
			return;
		}
		float volume = clientVolume();
		if (volume <= 0.0f) {
			// The knob documents 0 as "silences the counter"; sending packets nobody can hear would
			// still cost twenty of them a second at the top step.
			return;
		}
		// Iterating the readings rather than the player list: an entry for somebody who left without
		// the logout hook firing would otherwise never be looked at again, and its mere presence
		// defeats the isEmpty() fast path for the rest of the server's uptime.
		Iterator<Map.Entry<UUID, Reading>> entries = STEPS.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Reading> entry = entries.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				entries.remove();
				continue;
			}
			Reading reading = entry.getValue();
			if (RadiationCore.readingWentStale(player.level().getGameTime(), reading.takenAt(),
					Config.radiationTickInterval)) {
				entries.remove();
				continue;
			}
			RandomSource random = player.level().getRandom();
			boolean hazard = random.nextFloat() < CHANCE_BY_STEP[reading.step()];
			boolean ore = !hazard && random.nextFloat() < ORE_CHANCE_BY_STEP[reading.oreStep()];
			if (!hazard && !ore) {
				continue;
			}
			float base = hazard ? 1.0f : ORE_PITCH;
			float pitch = base + (random.nextFloat() * 2.0f - 1.0f) * PITCH_JITTER;
			player.connection.send(new ClientboundSoundPacket(
					Holder.direct(ModSounds.GEIGER_CLICK.get()), SoundSource.PLAYERS,
					player.getX(), player.getY(), player.getZ(),
					volume, pitch, random.nextLong()));
		}
	}

	/** Drop every reading — used when a world is unloaded, so state cannot cross sessions. */
	public static void clear() {
		STEPS.clear();
	}

	/**
	 * The counter in this player's own inventory, or {@link ItemStack#EMPTY}.
	 *
	 * <p>Returns the stack, because the caller also has to light its lamp.
	 *
	 * <p>A flat walk with an early exit, deliberately NOT {@code RadiationSources.carried}: that one
	 * unwraps nested containers and reads three data components per stack, which is a real cost to pay
	 * once a second for a yes/no question. A counter inside a shulker or a pouch does not click, and
	 * that is the decision, not an oversight — an instrument buried in a bag is not on duty.
	 *
	 * <p>Finding it once is also what keeps five counters from sounding like five: the answer is a
	 * boolean, not a count.
	 */
	public static ItemStack findCounter(ServerPlayer player) {
		// The off-hand is checked separately and on purpose: getNonEquipmentItems() is main + hotbar
		// only, as recorded on TeleportEngine. Without this line the one place a player would most
		// naturally park an instrument — the left hand — would be the one place it stayed silent, and
		// the item would read as broken.
		if (player.getOffhandItem().is(ModContent.GEIGER_COUNTER.get())) {
			return player.getOffhandItem();
		}
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(ModContent.GEIGER_COUNTER.get())) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Light or clear the counter's lamp — red while a HAZARD is heard, green otherwise (MOD-475).
	 *
	 * <p><b>Ore leaves the lamp green on purpose.</b> The ore signal means "safe, dig here"; a lamp
	 * that lit for it as well would have nothing left to say about danger, and red at a glance is the
	 * only reading this item offers that does not require listening.
	 *
	 * <p><b>Written only when the state flips.</b> The component is on a stack sitting in an inventory,
	 * so every write dirties a slot and syncs it; the sweep runs every second and the click loop twenty
	 * times a second, while this value changes about as often as a player walks in or out of a field.
	 * Absent rather than {@code false} when dark, so a counter that has never seen radiation still
	 * stacks with a freshly crafted one.
	 */
	public static void setLamp(ItemStack counter, boolean alert) {
		if (counter.isEmpty()) {
			return;
		}
		boolean lit = counter.has(ModDataComponents.GEIGER_ALERT.get());
		if (lit == alert) {
			return;
		}
		if (alert) {
			counter.set(ModDataComponents.GEIGER_ALERT.get(), true);
		} else {
			counter.remove(ModDataComponents.GEIGER_ALERT.get());
		}
	}

	/** Volume, kept as a hook for the client-side slider the task calls for. */
	private static float clientVolume() {
		return Math.clamp(Config.geigerVolumePercent / 100.0f, 0.0f, 1.0f);
	}
}
