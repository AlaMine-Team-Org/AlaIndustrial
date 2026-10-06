package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.environment.MobWheelProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The live entity types the mob wheel (MOD-763) takes and the profile each runs with — the game side of the
 * Minecraft-free {@link MobWheelProfile} table, which names the same types by id. The game test
 * {@code mob_wheel_every_runner_fits} checks the two agree type for type and that every box fits the wheel.
 */
public final class MobWheelRoster {
	private MobWheelRoster() {
	}

	/** Every accepted entity type and its profile, in the order of {@link MobWheelProfile#RUNNER_TYPE_IDS}. */
	public static final Map<EntityType<?>, MobWheelProfile> TYPES = build();

	private static Map<EntityType<?>, MobWheelProfile> build() {
		Map<EntityType<?>, MobWheelProfile> types = new LinkedHashMap<>();
		types.put(EntityTypes.CHICKEN, MobWheelProfile.CHICKEN);
		types.put(EntityTypes.PIG, MobWheelProfile.PIG);
		types.put(EntityTypes.SHEEP, MobWheelProfile.SHEEP);
		types.put(EntityTypes.COW, MobWheelProfile.COW);
		types.put(EntityTypes.GOAT, MobWheelProfile.GOAT);
		types.put(EntityTypes.VILLAGER, MobWheelProfile.VILLAGER);
		types.put(EntityTypes.ZOMBIE, MobWheelProfile.ZOMBIE);
		types.put(EntityTypes.ZOMBIE_VILLAGER, MobWheelProfile.ZOMBIE);
		types.put(EntityTypes.HUSK, MobWheelProfile.HUSK);
		types.put(EntityTypes.DROWNED, MobWheelProfile.DROWNED);
		types.put(EntityTypes.SKELETON, MobWheelProfile.SKELETON);
		types.put(EntityTypes.STRAY, MobWheelProfile.STRAY);
		types.put(EntityTypes.BOGGED, MobWheelProfile.BOGGED);
		types.put(EntityTypes.PARCHED, MobWheelProfile.PARCHED);
		types.put(EntityTypes.WITCH, MobWheelProfile.WITCH);
		types.put(EntityTypes.VINDICATOR, MobWheelProfile.VINDICATOR);
		types.put(EntityTypes.PILLAGER, MobWheelProfile.PILLAGER);
		types.put(EntityTypes.EVOKER, MobWheelProfile.EVOKER);
		types.put(EntityTypes.PIGLIN, MobWheelProfile.PIGLIN);
		types.put(EntityTypes.PIGLIN_BRUTE, MobWheelProfile.PIGLIN_BRUTE);
		types.put(EntityTypes.ZOMBIFIED_PIGLIN, MobWheelProfile.ZOMBIFIED_PIGLIN);
		types.put(EntityTypes.CREEPER, MobWheelProfile.CREEPER);
		return Collections.unmodifiableMap(types);
	}

	/** The wheel's profile for a mob, or {@code null} when it does not take that mob (no babies, D3). */
	@Nullable
	public static MobWheelProfile profileOf(Mob mob) {
		return mob.isBaby() ? null : TYPES.get(mob.getType());
	}

	/** The mob's entity type id as a string, e.g. {@code "minecraft:zombie_villager"}. */
	public static String typeIdOf(Mob mob) {
		return typeId(mob.getType());
	}

	/** An entity type's id as a string. */
	public static String typeId(EntityType<?> type) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id == null ? "" : id.toString();
	}
}
