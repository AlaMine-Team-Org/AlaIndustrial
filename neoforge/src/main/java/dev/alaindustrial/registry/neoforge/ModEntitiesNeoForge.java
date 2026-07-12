package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entity-type registration (MOD-022 registration-facade). Mirrors the Fabric
 * {@code dev.alaindustrial.registry.ModEntities} 1:1 (same ids, same builder values — see that
 * class for the vanilla-item-frame provenance of each builder call). {@code build(ResourceKey)} is
 * required on 26.2; NeoForge's {@code RegisterEvent} for ENTITY_TYPE fires before ITEM, but the
 * frame's item still resolves this holder lazily inside its registration lambda
 * (see {@code ModItemsNeoForge.STOCK_DISPLAY_FRAME_ITEM}) so no ordering assumption is load-bearing.
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
