package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.radiation.RadiationSources.Source;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import dev.alaindustrial.skill.SkillHazard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Radiation exposure, once per sweep (MOD-470): the one place that decides how much dose everybody in
 * the world takes, and the only writer of it on a player.
 *
 * <p>Where radiation comes from is {@link RadiationSources}; what it does to villagers and cows is
 * {@link RadiationMobs}. This class owns the player's side — the shielding suit, its wear, the ceiling
 * that keeps raw ore from ever being lethal — and the ordering that keeps the sweep honest:
 *
 * <ul>
 * <li><b>Per level, not per player.</b> Mobs are gathered once for the whole level and irradiated
 * once. Running the mob sweep inside the player loop dosed a villager standing between two players
 * twice as fast as one standing next to a single player — a bug nobody would ever attribute to a
 * second player being nearby.</li>
 * <li><b>A creative player carries nothing.</b> They are exempt from radiation themselves, so having
 * their pockets zombify a village they flew over was a one-sided rule that only ever surprised
 * admins.</li>
 * </ul>
 */
public final class RadiationTicker {

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private RadiationTicker() {
	}

	/** Sweep every online player, and the convertible mobs around them, on the configured cadence. */
	public static void tickAll(MinecraftServer server) {
		if (!Config.radiationEnabled) {
			return;
		}
		if (server.getTickCount() % Math.max(1, Config.radiationTickInterval) != 0) {
			return;
		}
		Map<ServerLevel, List<ServerPlayer>> byLevel = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() instanceof ServerLevel level) {
				byLevel.computeIfAbsent(level, unused -> new ArrayList<>()).add(player);
			}
		}
		for (Map.Entry<ServerLevel, List<ServerPlayer>> entry : byLevel.entrySet()) {
			tickLevel(entry.getKey(), entry.getValue());
		}
	}

	private static void tickLevel(ServerLevel level, List<ServerPlayer> players) {
		int radius = Config.radiationSourceRadius;
		// Sweeps, not ticks: suit wear is paced in sweeps, and counting the game clock inside a loop that
		// only runs every radiationTickInterval is the exact mistake that once left mobs undamaged.
		long sweep = level.getGameTime() / Math.max(1, Config.radiationTickInterval);
		List<Source> carriedSources = new ArrayList<>();
		List<Vec3> anchors = new ArrayList<>(players.size());
		for (ServerPlayer player : players) {
			anchors.add(player.position());
			int carried = carriedDose(player);
			// Creative and spectator live outside the mechanic in both directions.
			if (carried > 0 && !player.isCreative() && !player.isSpectator()) {
				carriedSources.add(new Source(player.position(), carried));
			}
			tickPlayer(level, player, radius, carried, sweep);
		}
		RadiationMobs.sweep(level, anchors, carriedSources, radius);
	}

	private static void tickPlayer(ServerLevel level, ServerPlayer player, int radius, int carried,
			long sweep) {
		// The instrument reads for creative and spectator players too, and only the DOSE is skipped
		// below. A counter that went dead in creative would be dead exactly where it is first picked
		// up — it is handed out in the creative tab, and an operator checking a reactor room is the
		// most likely person to be holding one.
		tickGeiger(level, player, carried);
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		int capacity = Config.radiationDoseCapacity;
		int dose = RadiationDose.of(player);
		int worn = wornShieldingPieces(player);
		int perPiece = Config.radiationShieldPerPiecePercent;

		// What the world radiates at this spot: rods and dropped uranium, both attenuated by distance
		// and stopped by walls. What is in the player's own pockets gets neither — it is ON them.
		int rawField = RadiationSources.exposureAt(level, player, radius);
		int rawLow = RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_LOW)
				* Config.radiationDoseLowPerItem;
		int rawItems = Math.max(0, carried - rawLow);

		// The field is the one source the suit cannot fully answer: its cap sits below 100 on purpose,
		// so a full suit buys working time inside a live reactor instead of immunity to it.
		int field = RadiationCore.shielded(rawField, worn, perPiece, Config.radiationRodShieldCapPercent);
		int items = RadiationCore.shielded(rawItems, worn, perPiece, 100);
		int lowShielded = RadiationCore.shielded(rawLow, worn, perPiece, 100);

		// Wear is charged for what the SUIT stopped, and only that. Measuring it against the final dose
		// instead would bill the suit for the ore ceiling below as well — a player standing in a cave
		// with a bag of ore would wear their suit out on a limit that has nothing to do with shielding.
		wearSuit(player, (rawField - field) + (rawItems - items) + (rawLow - lowShielded), sweep);

		// Raw ore has a ceiling read against the dose already carried: no amount of it can push a
		// queasy miner into the lethal band, which is what keeps an early death from feeling arbitrary.
		int low = RadiationCore.cappedContribution(dose, lowShielded,
				RadiationCore.cappedCeiling(capacity, Config.radiationLowDoseCapPercent));

		// MOD-483 Respirator. Applied AFTER wearSuit above, deliberately: the suit is charged for
		// what it stopped, and billing it for the skill's share too would wear it out for protection
		// it never provided.
		int added = SkillHazard.doseAdded(field + items + low, player);
		if (added <= 0) {
			return;
		}
		RadiationDose.apply(player, RadiationCore.addDose(dose, added, capacity), capacity, false);
	}

	/**
	 * What this player's Geiger counter reads this sweep (MOD-475).
	 *
	 * <p>Runs before the dose does, and for every game mode, because the instrument reports the world
	 * rather than what the world is doing to you.
	 *
	 * <p><b>Its own radius, wider than the hazard's.</b> {@link Config#geigerRadius} is deliberately
	 * larger than {@link Config#radiationSourceRadius}, so the counter speaks in the band where the
	 * dose is still zero. Sharing the hazard's radius made the instrument useless as a warning: it
	 * went quiet right up until the moment the player was already being irradiated.
	 *
	 * <p><b>Raw figures, not shielded ones.</b> A counter measures the field; a suit answers carried
	 * items in full, so a shielded reading would drop to zero the moment a player put protection on,
	 * and silence would come to mean "you are covered" instead of "there is nothing here". What gets
	 * through the suit is the dosimeter's question (MOD-567).
	 */
	private static void tickGeiger(ServerLevel level, ServerPlayer player, int carried) {
		ItemStack counter = GeigerTicker.findCounter(player);
		if (counter.isEmpty()) {
			// Nothing to light: a counter outside this inventory keeps whatever lamp it had, and picks
			// the right one up on the next sweep after it is carried again.
			GeigerTicker.setStep(player, 0, 0);
			return;
		}
		// The counter's OWN radius, not the hazard's: a detector that first speaks where the dose has
		// already started climbing has failed at the one job it has. Everything heard beyond
		// radiationSourceRadius is pure warning, because out there the dose is exactly zero.
		// MOD-483 Dosimetrist: the counter reaches further.
		int geiger = SkillHazard.geigerRadius(Config.geigerRadius, player);
		int heard = RadiationSources.exposureAt(level, player, geiger, geiger)
				+ Math.max(0, carried);
		int hazard = RadiationCore.geigerStep(heard, Config.geigerFaintThreshold,
				Config.geigerBusyThreshold, Config.geigerLoudThreshold,
				Config.geigerOffScaleThreshold);
		// The ore scan is the expensive half, and above the ceiling it cannot change the answer.
		int ore = hazard >= 4 ? 0
				: RadiationCore.oreStep(
						RadiationSources.nearestOreDistance(level, player, Config.geigerOreRadius),
						Config.geigerOreRadius);
		GeigerTicker.setStep(player, hazard, ore);
		GeigerTicker.setLamp(counter, hazard > 0);
	}

	/** Dose per sweep from everything in this player's own inventory, containers opened one level. */
	private static int carriedDose(ServerPlayer player) {
		return RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_LOW) * Config.radiationDoseLowPerItem
				+ RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_MEDIUM) * Config.radiationDoseMediumPerItem
				+ RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_HIGH) * Config.radiationDoseHighPerItem;
	}

	/**
	 * Worn pieces of the shielding suit, 0..4. Asked of any wearer — player or mob (MOD-535) —
	 * because the count is a property of the equipment, not of who is in it.
	 */
	static int wornShieldingPieces(LivingEntity entity) {
		int worn = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.is(ModTags.Items.RADIATION_SHIELDING)) {
				worn++;
			}
		}
		return worn;
	}

	/**
	 * Spend suit durability for the dose it absorbed — one point at a time, paced by how fierce the
	 * source is.
	 *
	 * <p><b>Two rules, both learned the hard way.</b> Wear is capped at one point per sweep, because
	 * charging a point per {@code radiationDosePerSuitDurability} absorbed came to seventeen a second
	 * beside a four-rod column and destroyed the helmet in ten seconds. And wear is PACED rather than
	 * gated, because the version after that only wore the suit when a single sweep cleared the threshold
	 * — so a reactor ate it and carrying uranium in your pockets cost nothing at all, which is precisely
	 * the contact a player has most of the time. {@link RadiationCore#wearInterval} turns the ratio into
	 * a number of sweeps between points: a live core is one a second, a stray ingot is one every few.
	 */
	private static void wearSuit(ServerPlayer player, int absorbed, long sweep) {
		// MOD-483 Careful Wear: one durability point absorbs more dose, so the suit lasts longer.
		int interval = RadiationCore.wearInterval(absorbed,
				SkillHazard.dosePerDurability(Config.radiationDosePerSuitDurability, player));
		if (interval <= 0 || sweep % interval != 0) {
			return;
		}
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.is(ModTags.Items.RADIATION_SHIELDING)) {
				stack.hurtAndBreak(1, player, slot);
			}
		}
	}
}
