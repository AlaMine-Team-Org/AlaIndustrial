package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.VanillaSmeltingMirror;
import java.util.List;
import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import me.shedaniel.rei.api.common.registry.display.ServerDisplayRegistry;

/**
 * Common (server-side) half of the AlaIndustrial REI integration (MOD-018). This is where the actual
 * recipe → display filling happens.
 *
 * <p>On MC 26.2 the client no longer receives the full {@code RecipeManager} (it is display-centric:
 * only synced recipe displays reach the client). REI therefore exposes recipe-based display filling
 * only on {@link ServerDisplayRegistry} (the {@code rei_common} entrypoint), which runs where the
 * recipe manager is populated. REI then serialises the produced displays with {@link
 * AlaProcessingDisplay#SERIALIZER} and syncs them to every client, so they appear in singleplayer and
 * on dedicated servers alike. The client half ({@code AlaReiPlugin}) only registers categories and
 * workstations.
 *
 * <p>Optional dependency: this class is loaded only when REI itself invokes the {@code rei_common}
 * entrypoint, so the mod runs fine without REI installed.
 */
public class AlaReiCommonPlugin implements REICommonPlugin {

	@Override
	public void registerDisplaySerializer(DisplaySerializerRegistry registry) {
		// Single serializer for all four machine families (the display carries its own kind).
		registry.register(Industrialization.id("processing"), AlaProcessingDisplay.SERIALIZER);
	}

	@Override
	public void registerDisplays(ServerDisplayRegistry registry) {
		// One filler claims every AlaProcessingRecipe (all four machines share the class); each display
		// routes itself to the right category via its recipe kind. REI iterates the server recipe
		// manager, builds the displays here, and syncs them to clients.
		registry.beginRecipeFiller(AlaProcessingRecipe.class)
				.fill(holder -> new AlaProcessingDisplay(holder.value()));
		// MOD-086: the electric furnace also runs every vanilla smelt (RecipeType.SMELTING fallback), so
		// its category lists those too — otherwise players opening it see only the mod's recipes and
		// cannot tell the machine smelts ores, food and sand as well. Each vanilla recipe is mirrored
		// into an AlaProcessingRecipe carrying the furnace's real EU cost; see VanillaSmeltingMirror
		// (including why the five duplicate "balance anchor" recipes are left in).
		// fillMultiple (not fill): a recipe that cannot be mirrored yields no display at all, and an
		// empty collection is the only way to say "skip this one" — fill() would have to return null.
		registry.beginRecipeFiller(SmeltingRecipe.class)
				.fillMultiple(holder -> {
					AlaProcessingRecipe mirror = VanillaSmeltingMirror.mirror(holder.value());
					return mirror == null ? List.of() : List.of(new AlaProcessingDisplay(mirror));
				});
	}
}
