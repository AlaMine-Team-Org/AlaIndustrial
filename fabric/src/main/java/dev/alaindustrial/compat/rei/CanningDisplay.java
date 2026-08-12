package dev.alaindustrial.compat.rei;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.compat.CanningExchange;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * REI display for one Canning Machine exchange (MOD-383): N of some food plus one empty can, for one
 * canned ration.
 *
 * <p>Unlike every other machine display here this one is <b>not</b> built from a recipe, because the
 * machine has none — the card comes from {@link CanningExchange}, which derives it from the item
 * registry and the live balance values. There is consequently nothing for the server to send, so
 * {@link #getSerializer()} returns {@code null} and {@code AlaReiCommonPlugin} is left alone: the
 * displays are built client-side in {@code AlaReiPlugin.registerDisplays}, exactly as
 * {@link AlaInfoDisplay} is.
 */
public final class CanningDisplay implements Display {
	/** The one category every canning card routes to ({@code alaindustrial:canning}). */
	public static final CategoryIdentifier<CanningDisplay> CATEGORY =
			CategoryIdentifier.of(Industrialization.id("canning"));

	private final List<EntryIngredient> inputs;
	private final List<EntryIngredient> outputs;

	public CanningDisplay(CanningExchange.Card card) {
		// The food slot carries the whole per-ration count, not one item: "1 sweet berry -> 1 ration"
		// would understate the real cost eleven-fold, and the rate is the only thing this card teaches.
		this.inputs = List.of(
				EntryIngredients.of(card.food(), card.itemsPerRation()),
				EntryIngredients.of(ModContent.EMPTY_CAN.get()));
		this.outputs = List.of(EntryIngredients.of(ModContent.CANNED_RATION.get()));
	}

	/** Total EU one ration costs — read live, so a retuned config shows through. */
	public int energy() {
		return CanningExchange.energyPerRation();
	}

	/** Ticks one ration takes on an un-upgraded machine. */
	public int processingTicks() {
		return CanningExchange.ticksPerRation();
	}

	@Override
	public List<EntryIngredient> getInputEntries() {
		return inputs;
	}

	@Override
	public List<EntryIngredient> getOutputEntries() {
		return outputs;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public Optional<Identifier> getDisplayLocation() {
		// No recipe file behind this card, so there is no location to report.
		return Optional.empty();
	}

	@Override
	@Nullable
	public DisplaySerializer<? extends Display> getSerializer() {
		// Client-only display: never round-tripped through the server-side registry.
		return null;
	}
}
