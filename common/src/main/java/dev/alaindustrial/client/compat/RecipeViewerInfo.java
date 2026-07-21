package dev.alaindustrial.client.compat;

import dev.alaindustrial.Config;
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
	 * <p>Today: the non-copper cables (tin / insulated copper / insulated tin) and the
	 * water mill + high-altitude/storm windmills. The base {@code wind_mill} and {@code copper_cable}
	 * stay visible. Each entry is the same {@code Supplier<? extends ItemLike>} used everywhere else
	 * in the mod, so the Fabric and NeoForge recipe viewers read one source and cannot drift.
	 */
	public static List<Supplier<? extends ItemLike>> hiddenFromRecipeViewerItems() {
		return List.of(
				// Generators (no recipe yet; hidden from creative tab too).
				ModContent.WATER_MILL,
				ModContent.HIGH_ALTITUDE_WIND_MILL,
				ModContent.STORM_WIND_MILL,
				// Cables — only copper_cable ships in v1.0.
				ModContent.TIN_CABLE,
				ModContent.INSULATED_COPPER_CABLE,
				ModContent.INSULATED_TIN_CABLE,
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
