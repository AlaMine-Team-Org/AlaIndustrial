package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ChestBoatVariants;
import dev.alaindustrial.entity.ModChestBoat;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entity-type registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModEntities} 1:1 (same ids, same builder values — see that
 * class for the vanilla-item-frame provenance of each builder call). {@code build(ResourceKey)} is
 * required on 26.2; NeoForge's {@code RegisterEvent} for ENTITY_TYPE fires before ITEM, and the frame's
 * item resolves the registered type by id inside its own factory (MOD-554,
 * {@code ContentManifest#stockDisplayFrameType}) rather than holding a handle, so no ordering
 * assumption is load-bearing.
 */
public final class ModEntitiesNeoForge {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
			DeferredRegister.create(Registries.ENTITY_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<EntityType<?>, EntityType<StockDisplayFrameEntity>> STOCK_DISPLAY_FRAME =
			ENTITY_TYPES.register("stock_display_frame",
					() -> EntityType.Builder.<StockDisplayFrameEntity>of(StockDisplayFrameEntity::new, MobCategory.MISC)
							.noLootTable()
							.sized(0.5F, 0.5F)
							.eyeHeight(0.0F)
							.clientTrackingRange(10)
							.updateInterval(Integer.MAX_VALUE)
							.build(ResourceKey.create(Registries.ENTITY_TYPE,
									Industrialization.id("stock_display_frame"))));

	/** The chest boats (MOD-785) — same pairs, ids and builder values as the Fabric {@code ModEntities}. */
	public static final Map<String, DeferredHolder<EntityType<?>, EntityType<ModChestBoat>>> CHEST_BOATS =
			registerChestBoats();

	private static Map<String, DeferredHolder<EntityType<?>, EntityType<ModChestBoat>>> registerChestBoats() {
		Map<String, DeferredHolder<EntityType<?>, EntityType<ModChestBoat>>> holders = new LinkedHashMap<>();
		for (ChestBoatVariants.Variant variant : ChestBoatVariants.ALL) {
			holders.put(variant.id(), ENTITY_TYPES.register(variant.id(),
					() -> EntityType.Builder.<ModChestBoat>of(variant::create, MobCategory.MISC)
							.noLootTable()
							.sized(1.375F, 0.5625F)
							.eyeHeight(0.5625F)
							.clientTrackingRange(10)
							.build(ResourceKey.create(Registries.ENTITY_TYPE, Industrialization.id(variant.id())))));
		}
		return holders;
	}

	private ModEntitiesNeoForge() {
	}

	/**
	 * Bind the holder into the loader-neutral {@link ModContent} facade. The slot is
	 * {@code Supplier<EntityType<?>>} while the holder supplies {@code EntityType<StockDisplayFrameEntity>};
	 * generics are invariant, so bind via the (still-lazy) method reference — same story as
	 * {@code ModItemsNeoForge}'s NETWORK_ANALYZER.
	 */
	public static void init() {
		ModContent.STOCK_DISPLAY_FRAME = STOCK_DISPLAY_FRAME::get;
	}
}
