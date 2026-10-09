package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.OilSlime;
import dev.alaindustrial.entity.OilSlimeSpawning;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The mod's living mobs, declared once (MOD-767). Everything a loader needs to make a mob exist — the
 * entity type, its default attributes, its spawn placement and its natural spawn — is one {@link MobDef};
 * Fabric ({@code ModEntities}) and NeoForge ({@code ModEntitiesNeoForge}) replay {@link #MOBS}, and the
 * NeoForge biome modifier JSON is checked against {@link NaturalSpawn} by {@code BiomeModifierTableTest}.
 * The renderer is the client half, {@code ClientMobRenderers}.
 *
 * <p>Code that needs the typed handle asks {@link MobDef#type()}, which reads the registry by id — the
 * same idiom the items use for the entity types they place, so nothing closes over a loader handle.
 */
public final class ModMobs {

	private ModMobs() {
	}

	/** Biomes an oil slime may appear in on its own; by default the whole Overworld. */
	public static final TagKey<Biome> HAS_OIL_SLIMES =
			TagKey.create(Registries.BIOME, Industrialization.id("has_oil_slimes"));

	/** Where and how often a mob appears on its own: biome tag, weight in its category, group size. */
	public record NaturalSpawn(TagKey<Biome> biomes, int weight, int minGroup, int maxGroup) {
	}

	/**
	 * One mob: its registry id, factory and category, the vanilla builder calls (size, eye height, …), its
	 * default attributes, where a spawn may stand and the rule that decides it, and its natural spawn.
	 */
	public record MobDef<T extends Mob>(String id, EntityType.EntityFactory<T> factory, MobCategory category,
			UnaryOperator<EntityType.Builder<T>> builder, Supplier<AttributeSupplier.Builder> attributes,
			SpawnPlacementType placement, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> spawnRule,
			NaturalSpawn naturalSpawn) {

		public Identifier key() {
			return Industrialization.id(id);
		}

		public ResourceKey<EntityType<?>> resourceKey() {
			return ResourceKey.create(Registries.ENTITY_TYPE, key());
		}

		/** The registered type; throws when asked before the loader registered it. */
		@SuppressWarnings("unchecked")
		public EntityType<T> type() {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key());
			if (type == null || !BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(key())) {
				throw new IllegalStateException("mob '" + key() + "' is not registered (yet)");
			}
			return (EntityType<T>) type;
		}
	}

	/**
	 * The oil slime (docs/mobs/oil_slime.md). Builder values are vanilla {@code EntityTypes.SLIME}'s (read from
	 * the 26.3 bytecode): 0.52 × 0.52 per size step, eye height 0.325, spawn box ×4, tracking 10, not in
	 * Peaceful. Attributes are the monster defaults vanilla gives the slime; its health follows the size.
	 */
	public static final MobDef<OilSlime> OIL_SLIME = new MobDef<>("oil_slime", OilSlime::new, MobCategory.MONSTER,
			b -> b.sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(10)
					.notInPeaceful(),
			Monster::createMonsterAttributes, SpawnPlacementTypes.ON_GROUND,
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, OilSlimeSpawning::canSpawn,
			new NaturalSpawn(HAS_OIL_SLIMES, 10, 1, 2));

	/** Every mob of the mod, in registration order. */
	public static final List<MobDef<?>> MOBS = List.of(OIL_SLIME);
}
