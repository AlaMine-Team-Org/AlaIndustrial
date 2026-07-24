package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Fabric {@link MenuType} registration (MOD-190). The menu list itself lives in the loader-neutral
 * {@link ContentManifest#MENUS}; this replays it <b>eagerly</b> — Fabric registers each {@code MenuType}
 * the moment {@code init()} runs — and publishes it into its {@link ModContent} slot. The NeoForge
 * counterpart {@code ModMenusNeoForge} replays the same manifest lazily through a {@code DeferredRegister}.
 */
public final class ModMenus {
	private ModMenus() {
	}

	/** Replays {@link ContentManifest#MENUS}, registering each menu eagerly into the vanilla registry. */
	public static void init() {
		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			register(def);
		}
	}

	private static <T extends AbstractContainerMenu> void register(ContentManifest.MenuDef<T> def) {
		MenuType<T> type = Registry.register(BuiltInRegistries.MENU, Industrialization.id(def.id()),
				new MenuType<>((syncId, inv) -> def.factory().create(syncId, inv), FeatureFlags.VANILLA_SET));
		def.bind().accept(() -> type);
	}
}
