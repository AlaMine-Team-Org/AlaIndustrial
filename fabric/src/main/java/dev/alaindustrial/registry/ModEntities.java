package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

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

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
			String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Bind the eager-registered types into the loader-neutral {@link ModContent} facade. */
	public static void init() {
		ModContent.STOCK_DISPLAY_FRAME = () -> STOCK_DISPLAY_FRAME;
	}
}
