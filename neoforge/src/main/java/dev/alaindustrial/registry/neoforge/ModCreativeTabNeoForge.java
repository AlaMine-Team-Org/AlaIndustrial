package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.CreativeTabContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge creative-tab registration. The item list is shared with Fabric via
 * {@link CreativeTabContent}, so release-visible content stays identical across loaders.
 */
public final class ModCreativeTabNeoForge {
	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Industrialization.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.alaindustrial"))
					.icon(() -> new ItemStack(ModItemsNeoForge.MACERATOR_ITEM.get()))
					.displayItems((params, output) -> CreativeTabContent.main(output::accept))
					.build());

	private ModCreativeTabNeoForge() {
	}
}
