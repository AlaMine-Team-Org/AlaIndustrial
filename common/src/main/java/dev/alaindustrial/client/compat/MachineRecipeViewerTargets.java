package dev.alaindustrial.client.compat;

import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.screen.ExtractorScreen;
import dev.alaindustrial.client.screen.MaceratorScreen;

/**
 * Loader-neutral click targets for opening machine recipe categories from a machine GUI.
 *
 * <p>The screens live in common while REI/JEI integrations live per loader. Keeping the hitboxes here
 * prevents Fabric and NeoForge recipe-viewer integrations from drifting when the GUI atlas changes.
 */
public final class MachineRecipeViewerTargets {
	private MachineRecipeViewerTargets() {
	}

	public record GuiRect(int x, int y, int width, int height) {
	}

	public record Target(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			ModRecipes.Kind kind,
			GuiRect progressArea) {
	}

	public static final List<Target> ALL = List.of(
			new Target(MaceratorScreen.class, ModRecipes.MACERATION, new GuiRect(82, 38, 25, 9)),
			new Target(ElectricFurnaceScreen.class, ModRecipes.SMELTING, new GuiRect(82, 38, 25, 9)),
			new Target(CompressorScreen.class, ModRecipes.COMPRESSING, new GuiRect(81, 34, 25, 18)),
			new Target(ExtractorScreen.class, ModRecipes.EXTRACTING, new GuiRect(80, 37, 29, 10)));
}
