package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ChestBoatVariants;
import dev.alaindustrial.entity.ModChestBoat;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registration for all Industrialization entity types (Fabric, eager — same idiom as
 * {@code ModBlockEntities}/{@code ModMenus}). First entity: the Stock Display Frame (MOD-066).
 *
 * <p>Builder values mirror vanilla {@code EntityTypes.ITEM_FRAME} (verified against the decompiled
 * 26.2 source): {@code MobCategory.MISC}, {@code sized(0.5, 0.5)}, {@code eyeHeight(0)},
 * {@code clientTrackingRange(10)}, {@code updateInterval(Integer.MAX_VALUE)} — the frame never
 * moves, and dirty {@code SynchedEntityData} (the stock count) syncs immediately regardless of the
 * update interval. The count text is drawn flat on the frame face by the renderer (sign-text
 * idiom), so no NAME_TAG attachment tweaks are needed.
 */
public final class ModEntities {
	private ModEntities() {
	}

	public static final EntityType<StockDisplayFrameEntity> STOCK_DISPLAY_FRAME = register(
			"stock_display_frame",
			EntityType.Builder.<StockDisplayFrameEntity>of(StockDisplayFrameEntity::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.5F, 0.5F)
					.eyeHeight(0.0F)
					.clientTrackingRange(10)
					.updateInterval(Integer.MAX_VALUE));

	/**
	 * The chest boats (MOD-785), one entity type per wood × chest pair of {@link ChestBoatVariants#ALL},
	 * keyed by id in table order. Builder values are vanilla {@code EntityTypes.OAK_CHEST_BOAT}'s (verified
	 * against the decompiled 26.3 source; the chest raft's are the same): size 1.375 × 0.5625, eye height
	 * 0.5625, tracking 10.
	 */
	public static final Map<String, EntityType<ModChestBoat>> CHEST_BOATS = registerChestBoats();

	private static Map<String, EntityType<ModChestBoat>> registerChestBoats() {
		Map<String, EntityType<ModChestBoat>> types = new LinkedHashMap<>();
		for (ChestBoatVariants.Variant variant : ChestBoatVariants.ALL) {
			types.put(variant.id(), register(variant.id(),
					EntityType.Builder.<ModChestBoat>of(variant::create, MobCategory.MISC)
							.noLootTable()
							.sized(1.375F, 0.5625F)
							.eyeHeight(0.5625F)
							.clientTrackingRange(10)));
		}
		return Map.copyOf(types);
	}

	/**
	 * The mod's living mobs (MOD-767), replayed from the shared {@link ModMobs#MOBS}: Fabric's mob builder
	 * carries the spawn placement and the default attributes, the shared definition adds the vanilla builder
	 * calls. Keyed by id in declaration order.
	 */
	public static final Map<String, EntityType<?>> MOBS = registerMobs();

	private static Map<String, EntityType<?>> registerMobs() {
		Map<String, EntityType<?>> types = new LinkedHashMap<>();
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			types.put(def.id(), registerMob(def));
		}
		return Map.copyOf(types);
	}

	private static <T extends Mob> EntityType<T> registerMob(ModMobs.MobDef<T> def) {
		EntityType.Builder<T> builder = FabricEntityType.Builder.createMob(def.factory(), def.category(),
				mob -> mob.spawnPlacement(def.placement(), def.heightmap(), def.spawnRule())
						.defaultAttributes(def.attributes()));
		return register(def.id(), def.builder().apply(builder));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
			String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Bind the eager-registered types into the loader-neutral {@link ModContent} facade. */
	public static void init() {
		ModContent.STOCK_DISPLAY_FRAME = () -> STOCK_DISPLAY_FRAME;
		// Natural spawns of the mobs (MOD-767): the NeoForge counterpart is the add_spawns biome modifier JSON.
		for (ModMobs.MobDef<?> def : ModMobs.MOBS) {
			ModMobs.NaturalSpawn spawn = def.naturalSpawn();
			BiomeModifications.addSpawn(BiomeSelectors.tag(spawn.biomes()), def.category(), MOBS.get(def.id()),
					spawn.weight(), spawn.minGroup(), spawn.maxGroup());
		}
	}
}
