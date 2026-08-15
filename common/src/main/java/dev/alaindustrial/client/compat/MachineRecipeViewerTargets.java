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
import dev.alaindustrial.client.screen.VulcanizerScreen;

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
			new Target(VulcanizerScreen.class, ModRecipes.VULCANIZING, new GuiRect(79, 31, 25, 9)),
			// Galvanic Bath (MOD-127): the arrow sits at (86,35), 24x17 — clicking it opens the
			// bath's recipes in JEI/REI instead of only reporting why it is idle.
			new Target(GalvanicBathScreen.class, ModRecipes.GALVANIC_BATH, new GuiRect(86, 35, 24, 17)),
			// Sawmill (MOD-150): one screen, four recipe families (mode-switched). The progress sprite opens
			// all four categories at once — the loader plugins special-case a sawmill target and pass every
			// kind in SAWMILL_KINDS in a single click-area registration (mirrors the electric-furnace
			// SMELTING special-case). The target's own kind is the "primary" (planks) for iteration.
			// The rect is the saw blade from the machine's own atlas (MOD-215), not the shared arrow —
			// it must track SawmillScreen.PROGRESS, or the click-area drifts off the sprite.
			new Target(SawmillScreen.class, ModRecipes.SAWING_PLANKS, new GuiRect(82, 20, 22, 12)),
			// Incubator (MOD-118): like the sawmill, one screen with several recipe families — here the
			// mode comes from the inserted chip rather than a button, but the arrow opens all three the
			// same way. The rect tracks IncubatorScreen.ARROW_*.
			new Target(IncubatorScreen.class, ModRecipes.MUTATION_TRANSFORM, new GuiRect(112, 20, 24, 16)));

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

	/** Fluid-fed machines' click targets. Each rect tracks its screen's {@code ARROW_*} constants. */
	public static final List<FluidTarget> FLUID_ALL = List.of(
			new FluidTarget(PolymerizerScreen.class, ModRecipes.POLYMERIZING, new GuiRect(79, 35, 24, 17)),
			// MOD-251 round 2: the distillation column — the click target is the tower schematic
			// in the GUI's centre (the plain arrow is gone).
			new FluidTarget(dev.alaindustrial.client.screen.DistillationColumnScreen.class,
					ModRecipes.DISTILLING, new GuiRect(59, 40, 24, 56)));

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

	/** The alloy smelter's click target. The rect tracks {@code AlloySmelterScreen.PROGRESS}. */
	public static final List<AlloyTarget> ALLOY_ALL = List.of(
			new AlloyTarget(AlloySmelterScreen.class, ModRecipes.ALLOYING, new GuiRect(79, 38, 25, 9)));

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

	/** The canning machine's click target. The rect tracks {@code CanningMachineScreen.PROGRESS}. */
	public static final List<CanningTarget> CANNING_ALL = List.of(
			new CanningTarget(CanningMachineScreen.class, new GuiRect(79, 31, 25, 9)));

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
	 * Click targets for the machine-info pages. Both rects were verified by a pixel scan of the
	 * machine's own atlas rather than copied from another screen:
	 *
	 * <ul>
	 *   <li><b>Geothermal generator</b> — the arrow between the fuel and container slots occupies
	 *       x 82..91, y 38..44 in {@code textures/gui/container/geothermal_generator.png}. It is baked
	 *       into the frame (the screen draws no progress sprite of its own), so there is no
	 *       {@code ARROW_*} constant to track — this rect IS the measurement.</li>
	 *   <li><b>Energy condenser</b> — the ring gauge, but only its band ABOVE the output slot. The full
	 *       ring bounding box would swallow the slot at (80,38): a click area covering a slot steals the
	 *       click, and the player could no longer take the clot out. The rect stops at y 37 for that
	 *       reason. Geometry tracks {@code EnergyCondenserScreen.CX/CY/FILL_R_OUT}.</li>
	 * </ul>
	 */
	public static final List<InfoTarget> INFO_ALL = List.of(
			new InfoTarget(GeothermalGeneratorScreen.class, new GuiRect(82, 38, 10, 7)),
			new InfoTarget(EnergyCondenserScreen.class, new GuiRect(57, 15, 63, 23)));

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
