package dev.alaindustrial.client.compat;

import dev.alaindustrial.Config;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

/**
 * Loader-neutral descriptions shown by recipe viewers (REI on Fabric, JEI on NeoForge) for blocks and
 * items that have no ordinary crafting recipe — foremost the solar panel evolution line.
 *
 * <p>Keeps the description data (which item owns it, lang keys, which {@link Config} values to inject)
 * in {@code common}, so the Fabric REI integration and the NeoForge JEI integration read the same
 * source and cannot drift. Adding a new evolution line (e.g. a future wind-mill branch) is a new entry
 * returned from a factory method, not duplicated code.
 *
 * <p>Numbers are never hardcoded here: each {@link Line} carries {@link IntSupplier}s that read the
 * live {@link Config} value when {@link #buildLines(Entry)} is called, so a config reload is reflected
 * without rebuilding the entries.
 */
public final class RecipeViewerInfo {
	private RecipeViewerInfo() {
	}

	/** One localisable text line: a lang key plus the integer arguments to substitute for its {@code %s}. */
	public record Line(String key, List<IntSupplier> args) {
		/** Convenience for a line without arguments. */
		public static Line of(String key) {
			return new Line(key, List.of());
		}
	}

	/**
	 * An informational page: the owning item (shown as the page icon and, for REI, the entry that the
	 * R-key resolves against), a title lang key, and the ordered description lines.
	 *
	 * <p>{@code Supplier<? extends ItemLike>} so a {@code Supplier<Block>} (from {@link ModContent}) is
	 * assignable — block items render in recipe viewers just like plain items.
	 */
	public record Entry(Supplier<? extends ItemLike> owner, String titleKey, List<Line> lines) {
	}

	/**
	 * Blocks/items registered for internal/worldgen reasons but deliberately kept out of the player's
	 * hands for the v1.0 release (see {@code fabric/.../registry/ModItems.java} creative-tab section —
	 * they have no {@code output.accept(...)} entry). They are also removed from the recipe viewer
	 * (REI on Fabric, JEI on NeoForge) so the player never sees them before they ship.
	 *
	 * <p>Today: only the recipe-less filled vacuum capsule. The base
	 * {@code water_mill}, {@code wind_mill}, both T2 wind mills (restored in MOD-172 — recipe-less,
	 * obtained by evolution) and every shipped cable grade — {@code tin_cable}, {@code copper_cable},
	 * {@code gold_cable} (MOD-219), {@code electrum_cable} (MOD-358) and all four insulated variants
	 * (MOD-259/MOD-268/MOD-358) — stay visible.
	 * Each entry is the same {@code Supplier<? extends ItemLike>} used everywhere else in the mod, so
	 * the Fabric and NeoForge recipe viewers read one source and cannot drift.
	 */
	public static List<Supplier<? extends ItemLike>> hiddenFromRecipeViewerItems() {
		return List.of(
				// Filled capsule (MOD-063) has no recipe — it is obtained by filling an empty capsule, so it
				// would otherwise show as a recipe-less entry. Empty vacuum_capsule stays visible (craftable).
				ModContent.FILLED_VACUUM_CAPSULE);
	}

	/**
	 * The two solar T2 branches that have no crafting recipe and are obtained only by evolving the
	 * base panel. The base {@code solar_panel} itself is craftable (see {@code solar_panel.json}) and is
	 * intentionally left out — it does not need an informational page.
	 */
	public static List<Entry> solarEvolutionEntries() {
		return List.of(
				// Daylight branch — evolved from the base via a Solar chip.
				new Entry(ModContent.DAYLIGHT_SOLAR_PANEL, "jei.alaindustrial.daylight_solar_panel.title", List.of(
						Line.of("jei.alaindustrial.daylight_solar_panel.line1"),
						new Line("jei.alaindustrial.daylight_solar_panel.line2", List.of(
								() -> Config.daylightEuPerTick)),
						Line.of("jei.alaindustrial.daylight_solar_panel.line3"))),
				// Moonlit branch — evolved from the base via a Lunar chip.
				new Entry(ModContent.MOONLIT_SOLAR_PANEL, "jei.alaindustrial.moonlit_solar_panel.title", List.of(
						Line.of("jei.alaindustrial.moonlit_solar_panel.line1"),
						new Line("jei.alaindustrial.moonlit_solar_panel.line2", List.of(
								() -> Config.moonlitEuPerTick, () -> Config.moonlitWeatherEuPerTick)),
						Line.of("jei.alaindustrial.moonlit_solar_panel.line3"))));
	}

	/**
	 * The incubator's rarity grades (MOD-118). A recipe card can show the chance of an operation
	 * succeeding, but not what happens on top of a success — the second roll that decides how good the
	 * result is. There is no recipe to hang that on, so it lives here, on the machine itself.
	 *
	 * <p>The odds are read live from {@link Config}, as percentages, so a server that retunes them does
	 * not leave the page lying.
	 */
	public static List<Entry> mutationGradeEntries() {
		return List.of(new Entry(ModContent.INCUBATOR, "jei.alaindustrial.mutation_grades.title", List.of(
				Line.of("jei.alaindustrial.mutation_grades.line1"),
				new Line("jei.alaindustrial.mutation_grades.line2", List.of(
						() -> percent(1.0 - Config.mutationGradeRare - Config.mutationGradeEpic
								- Config.mutationGradeLegendary),
						() -> percent(Config.mutationGradeRare),
						() -> percent(Config.mutationGradeEpic),
						() -> percent(Config.mutationGradeLegendary))),
				new Line("jei.alaindustrial.mutation_grades.line3", List.of(
						() -> percent(MutationGrade.RARE.geneBonus()),
						() -> percent(MutationGrade.EPIC.geneBonus()),
						() -> percent(MutationGrade.LEGENDARY.geneBonus()),
						() -> percent(Config.mutationChanceCap))),
				Line.of("jei.alaindustrial.mutation_grades.line4"))));
	}

	/**
	 * Machines whose behaviour is not expressible as a recipe (MOD-420). A processing machine gets its
	 * card for free — it has a {@link dev.alaindustrial.registry.ModRecipes.Kind} and JSON recipes, so
	 * the viewer can draw "these items in, those items out". These two have neither: the geothermal
	 * generator burns lava through hardcoded item checks, and the condenser has no input item at all —
	 * it eats surplus EU and prints a clot once the bank passes a threshold. Without this page their
	 * GUIs answer nothing when clicked.
	 *
	 * <p>Deliberately terse: {@code AlaInfoCategory} reserves eight visual lines after word-wrap at
	 * ~148 px and neither clips nor scrolls, so a ninth line would simply hang outside the panel. Every
	 * line here is written to survive Russian, the longest of the twenty shipped locales.
	 *
	 * <p>Titles reuse the blocks' own {@code block.alaindustrial.*} keys rather than dedicated ones:
	 * the page names the machine, and a second key could only ever drift away from the first.
	 */
	public static List<Entry> machineInfoEntries() {
		return List.of(
				// Geothermal generator: two accepted fuels, a fixed burn, and the emptied container back.
				new Entry(ModContent.GEOTHERMAL_GENERATOR, "block.alaindustrial.geothermal_generator", List.of(
						Line.of("jei.alaindustrial.geothermal_generator.line1"),
						new Line("jei.alaindustrial.geothermal_generator.line2", List.of(
								() -> Config.geothermalEuPerTick,
								() -> Config.geothermalBurnTicks,
								() -> Config.geothermalEuPerTick * Config.geothermalBurnTicks)),
						Line.of("jei.alaindustrial.geothermal_generator.line3"))),
				// Condenser: the intake rate, the first threshold, and the one rule that surprises players —
				// taking the clot clears the whole bank, not the tier's price.
				new Entry(ModContent.ENERGY_CONDENSER, "block.alaindustrial.energy_condenser", List.of(
						new Line("jei.alaindustrial.energy_condenser.line1", List.of(
								() -> Config.condenserInputRate)),
						new Line("jei.alaindustrial.energy_condenser.line2", List.of(
								() -> Config.clotThresholdI / 1000)),
						Line.of("jei.alaindustrial.energy_condenser.line3"))));
	}

	/** A 0..1 share as whole percent — the unit every number on the grade page is written in. */
	private static int percent(double share) {
		return (int) Math.round(share * 100.0);
	}

	/** The localised title for an entry. */
	public static Component title(Entry entry) {
		return Component.translatable(entry.titleKey());
	}

	/**
	 * Builds the localised description lines for an entry, substituting the live {@link Config} values
	 * for each line's {@code %s} placeholders.
	 */
	public static List<Component> buildLines(Entry entry) {
		List<Component> lines = new ArrayList<>(entry.lines().size());
		for (Line line : entry.lines()) {
			Object[] args = new Object[line.args().size()];
			for (int i = 0; i < args.length; i++) {
				args[i] = line.args().get(i).getAsInt();
			}
			lines.add(Component.translatable(line.key(), args));
		}
		return lines;
	}
}
