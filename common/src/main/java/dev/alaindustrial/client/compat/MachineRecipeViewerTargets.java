package dev.alaindustrial.client.compat;

import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.alaindustrial.client.screen.AlloySmelterScreen;
import dev.alaindustrial.client.screen.CanningMachineScreen;
import dev.alaindustrial.client.screen.CompressorScreen;
import dev.alaindustrial.client.screen.ElectricFurnaceScreen;
import dev.alaindustrial.client.screen.EnergyCondenserScreen;
import dev.alaindustrial.client.screen.ExtractorScreen;
import dev.alaindustrial.client.screen.GeothermalGeneratorScreen;
import dev.alaindustrial.client.screen.IncubatorScreen;
import dev.alaindustrial.client.screen.MaceratorScreen;
import dev.alaindustrial.client.screen.PolymerizerScreen;
import dev.alaindustrial.client.screen.SawmillScreen;
import dev.alaindustrial.client.screen.GalvanicBathScreen;
import dev.alaindustrial.client.screen.GuiRect;
import dev.alaindustrial.client.screen.ThermalCentrifugeScreen;
import dev.alaindustrial.client.screen.VulcanizerScreen;

/**
 * Loader-neutral click targets for opening machine recipe categories from a machine GUI.
 *
 * <p>The screens live in common while REI/JEI integrations live per loader. Keeping the targets here
 * prevents Fabric and NeoForge recipe-viewer integrations from drifting when the GUI atlas changes.
 *
 * <p>Every rectangle is the screen's own {@code PROGRESS_AREA} (MOD-716, CLI-5): the screen that draws the
 * arrow states where it is clickable, so moving the arrow is one edit in one file. No number lives here.
 */
public final class MachineRecipeViewerTargets {
	private MachineRecipeViewerTargets() {
	}

	public record Target(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			ModRecipes.Kind kind,
			GuiRect progressArea) {
	}

	public static final List<Target> ALL = List.of(
			new Target(MaceratorScreen.class, ModRecipes.MACERATION, MaceratorScreen.PROGRESS_AREA),
			new Target(ElectricFurnaceScreen.class, ModRecipes.SMELTING, ElectricFurnaceScreen.PROGRESS_AREA),
			new Target(CompressorScreen.class, ModRecipes.COMPRESSING, CompressorScreen.PROGRESS_AREA),
			new Target(ExtractorScreen.class, ModRecipes.EXTRACTING, ExtractorScreen.PROGRESS_AREA),
			new Target(VulcanizerScreen.class, ModRecipes.VULCANIZING, VulcanizerScreen.PROGRESS_AREA),
			// MOD-424: the shared golden arrow at (82,38).
			new Target(ThermalCentrifugeScreen.class, ModRecipes.CENTRIFUGING, ThermalCentrifugeScreen.PROGRESS_AREA),
			// Galvanic Bath (MOD-127): the arrow sits at (86,35), 24x17 — clicking it opens the
			// bath's recipes in JEI/REI instead of only reporting why it is idle.
			new Target(GalvanicBathScreen.class, ModRecipes.GALVANIC_BATH, GalvanicBathScreen.PROGRESS_AREA),
			// Sawmill (MOD-150): one screen, four recipe families (mode-switched). The progress sprite opens
			// all four categories at once — the loader plugins special-case a sawmill target and pass every
			// kind in SAWMILL_KINDS in a single click-area registration (mirrors the electric-furnace
			// SMELTING special-case). The target's own kind is the "primary" (planks) for iteration.
			// The rect is the saw blade from the machine's own atlas (MOD-215), not the shared arrow.
			new Target(SawmillScreen.class, ModRecipes.SAWING_PLANKS, SawmillScreen.PROGRESS_AREA),
			// Incubator (MOD-118): like the sawmill, one screen with several recipe families — here the
			// mode comes from the inserted chip rather than a button, but the arrow opens all three the
			// same way.
			new Target(IncubatorScreen.class, ModRecipes.MUTATION_TRANSFORM, IncubatorScreen.PROGRESS_AREA));

	/**
	 * The same, for machines whose recipes are fluid-fed ({@link ModRecipes.FluidKind}). A separate list
	 * rather than a wider {@link Target}: the two recipe families are different Java types, and widening
	 * {@code Target#kind} to a common supertype would push an {@code instanceof} into every plugin call
	 * site that reads it. One extra loop in each plugin is the cheaper half of that trade.
	 */
	public record FluidTarget(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			ModRecipes.FluidKind<?> kind,
			GuiRect progressArea) {
	}

	/** Fluid-fed machines' click targets. */
	public static final List<FluidTarget> FLUID_ALL = List.of(
			new FluidTarget(PolymerizerScreen.class, ModRecipes.POLYMERIZING, PolymerizerScreen.PROGRESS_AREA),
			// MOD-251 round 2: the distillation column — the click target is the tower schematic
			// in the GUI's centre (the plain arrow is gone).
			new FluidTarget(dev.alaindustrial.client.screen.DistillationColumnScreen.class,
					ModRecipes.DISTILLING, dev.alaindustrial.client.screen.DistillationColumnScreen.PROGRESS_AREA));

	/**
	 * The same again for the multi-component alloying family ({@link ModRecipes.AlloyKind}) — a third
	 * list for the same reason {@link FluidTarget} is a second one: the family types are unrelated in
	 * Java, and widening {@code kind} to a common supertype would push an {@code instanceof} into every
	 * plugin call site.
	 */
	public record AlloyTarget(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			ModRecipes.AlloyKind<?> kind,
			GuiRect progressArea) {
	}

	/**
	 * The alloy smelter's click target.
	 *
	 * <p>MOD-457 replaced the shared 25×9 arrow with the 42×43 merge arrow, and this rect had been left
	 * describing the old one — a 225 px² hitbox floating inside a 1806 px² picture. The player aiming at
	 * the arrow to open the recipe list mostly missed it. It now covers the arrow, and since MOD-716 the
	 * screen states it next to the sprite.
	 */
	public static final List<AlloyTarget> ALLOY_ALL = List.of(
			new AlloyTarget(AlloySmelterScreen.class, ModRecipes.ALLOYING, AlloySmelterScreen.PROGRESS_AREA));

	/**
	 * And a fourth list for the Canning Machine (MOD-383), which has no {@link ModRecipes.Kind} to key on
	 * at all: it matches no JSON recipe, so its category is built from {@link CanningExchange} instead.
	 * The record therefore carries no kind — the screen and its hitbox are the whole target, and the
	 * loader plugins pair it with their own single canning category id.
	 */
	public record CanningTarget(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			GuiRect progressArea) {
	}

	/** The canning machine's click target. */
	public static final List<CanningTarget> CANNING_ALL = List.of(
			new CanningTarget(CanningMachineScreen.class, CanningMachineScreen.PROGRESS_AREA));

	/**
	 * And a fifth list for machines that have no recipe of any kind (MOD-420) — not even a computed one
	 * like the canning machine's. These open the informational category instead: see
	 * {@link RecipeViewerInfo#machineInfoEntries()}. Like {@link CanningTarget} the record carries no
	 * kind, because there is none to carry.
	 */
	public record InfoTarget(
			Class<? extends AbstractContainerScreen<?>> screenClass,
			GuiRect progressArea) {
	}

	/**
	 * Click targets for the machine-info pages: the geothermal generator's baked-in arrow (a pixel
	 * measurement of its atlas) and the energy condenser's ring band above its output slot — see each
	 * screen's {@code PROGRESS_AREA}.
	 */
	public static final List<InfoTarget> INFO_ALL = List.of(
			new InfoTarget(GeothermalGeneratorScreen.class, GeothermalGeneratorScreen.PROGRESS_AREA),
			new InfoTarget(EnergyCondenserScreen.class, EnergyCondenserScreen.PROGRESS_AREA));

	/** The four sawmill recipe families, in button order — used by REI/JEI to open every mode from the sprite. */
	public static final List<ModRecipes.Kind> SAWMILL_KINDS = List.of(
			ModRecipes.SAWING_PLANKS, ModRecipes.SAWING_STICKS, ModRecipes.SAWING_SLABS, ModRecipes.SAWING_STAIRS);

	/** The three incubator mutation families, in chip order. */
	public static final List<ModRecipes.Kind> MUTATION_KINDS = List.of(
			ModRecipes.MUTATION_TRANSFORM, ModRecipes.MUTATION_DUPLICATE, ModRecipes.MUTATION_CREATE);

	/** True when {@code kind} is one of the sawmill's four mode families. */
	public static boolean isSawmill(ModRecipes.Kind kind) {
		return SAWMILL_KINDS.contains(kind);
	}

	/** True when {@code kind} is one of the incubator's three mutation families. */
	public static boolean isMutation(ModRecipes.Kind kind) {
		return MUTATION_KINDS.contains(kind);
	}
}
