package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModDamageTypes;
import dev.alaindustrial.registry.ModEffects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What radiation does to the villagers and the livestock (MOD-470).
 *
 * <p><b>This deliberately reverses the earlier "mobs are never irradiated" decision — for exactly
 * three species.</b> The blanket rule existed to keep radiation from silently killing farms and from
 * costing an entity sweep every tick. Both concerns survive here: nothing dies of radiation, the list
 * is closed, and the sweep only runs where a player already stands in a radiation field.
 *
 * <ul>
 * <li><b>Villager</b> — takes light damage, looks visibly unwell, and once the dose fills up turns
 * into a <b>zombie villager</b>, keeping its profession, trades and experience. A zombie villager is
 * not on the list, so from that moment it takes nothing: it is already what radiation makes of you.</li>
 * <li><b>Wandering trader</b> — the same ending, minus the profession it never had.</li>
 * <li><b>Cow</b> — no damage worth the name, and it becomes a <b>mooshroom</b>: the one place in
 * vanilla where a mushroom growing out of an animal is canon.</li>
 * </ul>
 *
 * <p>The dose is carried the same way the player's is: as the remaining duration of the radiation
 * effect on the mob itself. No per-entity bookkeeping, no attachment, and it survives a chunk reload.
 */
public final class RadiationMobs {

	/** Share of maximum health below which radiation stops hurting a mob. It transforms them, not kills. */
	private static final float HEALTH_FLOOR = 0.4f;

	private RadiationMobs() {
	}

	/**
	 * Irradiate every convertible mob around this level's players — each of them exactly once.
	 *
	 * <p>Anchored on the players' POSITIONS rather than on the players themselves: nothing here needs
	 * the player, and a bare {@code Vec3} is what lets the gametests drive a sweep without conjuring one.
	 *
	 * <p>Anchored on the players rather than on the reactors on purpose: it bounds the cost (no sweep in
	 * chunks nobody is watching) and it means the transformation happens where somebody is there to see
	 * it. A villager left alone in a reactor room while the owner is away simply waits.
	 *
	 * <p><b>Gathered into a map first, and that is not tidiness.</b> The boxes of two players standing
	 * together overlap, and the previous version — a sweep per player — dosed everything in the overlap
	 * twice per tick. A villager converted twice as fast because a second player happened to be nearby,
	 * which is not a difficulty anybody would ever diagnose.
	 *
	 * @param carriedSources uranium in the players' own pockets, as point sources at their feet. A
	 *                       villager standing next to somebody carrying fuel rods is being irradiated by
	 *                       them — through distance and through walls, like every other source.
	 */
	public static void sweep(ServerLevel level, List<Vec3> anchors,
			List<RadiationSources.Source> carriedSources, int radius) {
		if (!Config.radiationMobsEnabled || anchors.isEmpty()) {
			return;
		}
		Map<Integer, Mob> found = new LinkedHashMap<>();
		for (Vec3 anchor : anchors) {
			AABB box = AABB.ofSize(anchor, radius * 2.0, radius * 2.0, radius * 2.0);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, RadiationMobs::isConvertible)) {
				found.putIfAbsent(mob.getId(), mob);
			}
		}
		for (Mob mob : found.values()) {
			int exposure = RadiationSources.exposureAt(level, mob, radius)
					+ RadiationSources.doseFrom(level, mob, carriedSources, radius);
			if (exposure > 0) {
				expose(level, mob, exposure);
			}
		}
	}

	/** The closed list. A zombie villager or an existing mooshroom is already past the end of it. */
	private static boolean isConvertible(Mob mob) {
		return mob instanceof AbstractVillager || (mob instanceof Cow && !(mob instanceof MushroomCow));
	}

	private static void expose(ServerLevel level, Mob mob, int exposure) {
		int capacity = Config.radiationDoseCapacity;
		int next = RadiationCore.addDose(RadiationDose.of(mob), exposure, capacity);
		RadiationDose.apply(mob, next, capacity, true);

		// Symptoms follow the DOSE, not the mere presence of a source. A source too weak to outpace the
		// decay leaves the dose at zero for ever, and the old code still hurt the mob every sweep — so a
		// villager visibly suffered from a single thrown ingot and could never transform, which is what
		// the playtest reported. Now sickness and progress are the same thing.
		if (RadiationCore.level(next, capacity) >= 1 && !(mob instanceof Cow)) {
			int symptom = Config.radiationSymptomIntervalTicks * 3;
			mob.addEffect(new MobEffectInstance(MobEffects.NAUSEA, symptom, 0, true, true, true));
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, symptom, 0, true, true, true));
			hurt(level, mob);
		}

		if (next >= RadiationCore.cappedCeiling(capacity, Config.radiationMobConvertPercent)) {
			convert(level, mob);
		}
	}

	/**
	 * A hit every {@code radiationMobDamageIntervalTicks}, and never a fatal one.
	 *
	 * <p><b>Both halves of this are bug fixes, found the hard way.</b> The cadence used to be
	 * {@code gameTime % interval == 0} — read inside a sweep that itself only runs every
	 * {@code radiationTickInterval} ticks. The sweep always lands on the same residue class, so unless
	 * the two numbers happen to align, that condition is false on every sweep forever: villagers took
	 * the effect and never a single point of damage, exactly as the first playtest reported. Counting
	 * sweeps instead of ticks cannot drift that way.
	 *
	 * <p>The floor is the other half. Radiation is a transformation here, not a cull — a villager that
	 * bleeds out before the dose fills up is a lost trader, so damage stops at
	 * {@link #HEALTH_FLOOR} of maximum health and the sickness simply persists.
	 */
	private static void hurt(ServerLevel level, Mob mob) {
		long sweep = level.getGameTime() / Math.max(1, Config.radiationTickInterval);
		int perHit = Math.max(1, Config.radiationMobDamageIntervalTicks / Math.max(1, Config.radiationTickInterval));
		if (sweep % perHit != 0) {
			return;
		}
		if (mob.getHealth() <= mob.getMaxHealth() * HEALTH_FLOOR) {
			return;
		}
		mob.hurtServer(level, ModDamageTypes.radiation(level), Config.radiationDamageSick);
	}

	private static void convert(ServerLevel level, Mob mob) {
		if (mob instanceof Cow cow) {
			cow.convertTo(EntityTypes.MOOSHROOM, ConversionParams.single(cow, false, false),
					converted -> finishAnimal(level, converted));
			return;
		}
		mob.convertTo(EntityTypes.ZOMBIE_VILLAGER, ConversionParams.single(mob, true, true),
				converted -> finishZombieVillager(level, mob, converted));
	}

	/** Carry the villager across: a trader that loses its stock to radiation is a bug report, not a story. */
	private static void finishZombieVillager(ServerLevel level, Mob source, ZombieVillager converted) {
		if (source instanceof Villager villager) {
			converted.setVillagerData(villager.getVillagerData());
			converted.setVillagerXp(villager.getVillagerXp());
		}
		if (source instanceof AbstractVillager merchant) {
			converted.setTradeOffers(merchant.getOffers().copy());
		}
		converted.setPersistenceRequired();
		// Vanilla's conversion copies every active effect across (ConversionType.SINGLE), so without this
		// the zombie villager stands there glowing with radiation particles for minutes after it stopped
		// being able to take any — the dose belonged to who it used to be.
		RadiationDose.clear(converted);
		effects(level, converted, SoundEvents.ZOMBIE_VILLAGER_CONVERTED);
	}

	private static void finishAnimal(ServerLevel level, MushroomCow converted) {
		converted.setPersistenceRequired();
		RadiationDose.clear(converted);
		effects(level, converted, SoundEvents.MOOSHROOM_CONVERT);
	}

	private static void effects(ServerLevel level, Mob mob, net.minecraft.sounds.SoundEvent sound) {
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.NEUTRAL, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY(0.6), mob.getZ(),
				12, 0.4, 0.5, 0.4, 0.0);
	}
}
