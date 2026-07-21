package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.compat.RecipeViewerInfo;
import dev.alaindustrial.client.compat.RecipeViewerInfo.Entry;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * REI display for an informational page (a block/item with no crafting recipe), built from a
 * {@link RecipeViewerInfo.Entry}. Routed to {@link AlaInfoCategory}.
 *
 * <p>The owning item is published as the sole {@code output} entry so that REI's R-key lookup finds
 * this display when the player presses R on the item: REI matches the focused entry against a
 * display's input/output ingredients. There are no real inputs or outputs — the page is pure text.
 *
 * <p>{@link #getSerializer()} returns {@code null}: this display is purely client-side (the
 * description references blocks/items and {@link dev.alaindustrial.Config Config} values, none of
 * which are server-only), so it is built directly by {@code AlaReiPlugin.registerDisplays} and never
 * round-tripped through the server-side {@code ServerDisplayRegistry}.
 */
public final class AlaInfoDisplay implements Display {
	/** Single category id shared by all informational pages. */
	public static final CategoryIdentifier<AlaInfoDisplay> CATEGORY =
			CategoryIdentifier.of(dev.alaindustrial.Industrialization.id("evolution_info"));

	private final EntryIngredient ownerEntry;
	private final Component title;
	private final List<Component> lines;

	public AlaInfoDisplay(Entry entry) {
		ItemLike owner = entry.owner().get();
		this.ownerEntry = EntryIngredients.of(owner);
		this.title = RecipeViewerInfo.title(entry);
		this.lines = RecipeViewerInfo.buildLines(entry);
	}

	public Component title() {
		return title;
	}

	public List<Component> lines() {
		return lines;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public List<EntryIngredient> getInputEntries() {
		// No real inputs — this is an info page, not a recipe.
		return List.of();
	}

	@Override
	public List<EntryIngredient> getOutputEntries() {
		// Publish the owner item so R-key lookup resolves to this display.
		return List.of(ownerEntry);
	}

	@Override
	public Optional<Identifier> getDisplayLocation() {
		return Optional.empty();
	}

	@Override
	@Nullable
	public DisplaySerializer<? extends Display> getSerializer() {
		// Client-only display: not synced from the server, so no serializer is registered.
		return null;
	}
}
