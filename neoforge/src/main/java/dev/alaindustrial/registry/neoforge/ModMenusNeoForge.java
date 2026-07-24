package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge {@link MenuType} registration (MOD-190). Replays the loader-neutral
 * {@link ContentManifest#MENUS} through a {@link DeferredRegister} (lazy) and binds each holder into
 * {@link dev.alaindustrial.registry.ModContent}. Mirrors the eager Fabric {@code ModMenus}.
 *
 * <p><b>Split constraint (verified 26.2 API):</b> the {@code DeferredRegister} and its
 * {@code register(modBus)} must live on the {@code neoforge} side. NeoForge builds a networked
 * {@code MenuType} via {@code IMenuTypeExtension.create(IContainerFactory)} — the loader replacement for
 * the vanilla {@code new MenuType<>(factory, FeatureFlags)} the Fabric side uses. Since
 * {@code IContainerFactory<T> extends MenuType.MenuSupplier<T>} and ignores the extra buffer, the
 * manifest's 2-arg client factory plugs straight in.
 *
 * <p><b>Timing.</b> The {@code static} block queues every entry into {@link #MENUS} at class-load (when
 * the entrypoint first touches {@code MENUS} to call {@code register(modBus)}); the {@code ModContent}
 * bindings are held in {@link #BINDINGS} and applied later by {@link #init()} — a {@code DeferredHolder}
 * is a {@code Supplier}, so binding it before the {@code RegisterEvent} fires is safe (it resolves lazily).
 */
public final class ModMenusNeoForge {
	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, Industrialization.MOD_ID);

	/** ModContent bindings, deferred until {@link #init()} runs (after {@code MENUS} is populated). */
	private static final List<Runnable> BINDINGS = new ArrayList<>();

	static {
		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			declare(def);
		}
	}

	private ModMenusNeoForge() {
	}

	private static <T extends AbstractContainerMenu> void declare(ContentManifest.MenuDef<T> def) {
		DeferredHolder<MenuType<?>, MenuType<T>> holder =
				register(def.id(), (id, inv, buf) -> def.factory().create(id, inv));
		BINDINGS.add(() -> def.bind().accept(holder::get));
	}

	private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> register(
			String name, IContainerFactory<T> factory) {
		return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
	}

	/**
	 * Publishes each registered {@code MenuType} holder into its
	 * {@link dev.alaindustrial.registry.ModContent} slot. Called from {@code bindContentFacade}, mirroring
	 * the {@code ModContent.X_MENU = ...} assignments the Fabric {@code ModMenus} makes.
	 */
	public static void init() {
		BINDINGS.forEach(Runnable::run);
	}
}
