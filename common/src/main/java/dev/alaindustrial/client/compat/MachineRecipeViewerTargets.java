package dev.alaindustrial.client.compat;

import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.screen.ExtractorScreen;
import dev.alaindustrial.client.screen.MaceratorScreen;
import dev.alaindustrial.client.screen.SawmillScreen;

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
			new Target(ExtractorScreen.class, ModRecipes.EXTRACTING, new GuiRect(80, 37, 29, 10)),
			// Sawmill (MOD-150): one screen, four recipe families (mode-switched). The progress sprite opens
			// all four categories at once — the loader plugins special-case a sawmill target and pass every
			// kind in SAWMILL_KINDS in a single click-area registration (mirrors the electric-furnace
			// SMELTING special-case). The target's own kind is the "primary" (planks) for iteration.
			// The rect is the saw blade from the machine's own atlas (MOD-215), not the shared arrow —
			// it must track SawmillScreen.PROGRESS, or the click-area drifts off the sprite.
			new Target(SawmillScreen.class, ModRecipes.SAWING_PLANKS, new GuiRect(82, 20, 22, 12)));

	/** The four sawmill recipe families, in button order — used by REI/JEI to open every mode from the sprite. */
	public static final List<ModRecipes.Kind> SAWMILL_KINDS = List.of(
			ModRecipes.SAWING_PLANKS, ModRecipes.SAWING_STICKS, ModRecipes.SAWING_SLABS, ModRecipes.SAWING_STAIRS);

	/** True when {@code kind} is one of the sawmill's four mode families. */
	public static boolean isSawmill(ModRecipes.Kind kind) {
		return SAWMILL_KINDS.contains(kind);
	}
}
