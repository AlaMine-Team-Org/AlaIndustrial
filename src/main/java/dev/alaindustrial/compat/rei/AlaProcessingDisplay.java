package dev.alaindustrial.compat.rei;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * REI display for one {@link AlaProcessingRecipe} (macerator / electric furnace / compressor /
 * extractor). One class serves all four machines: the recipe's {@link ModRecipes.Kind} picks the
 * category, and the displayed EU cost / time come from the recipe.
 *
 * <p>Lives in the <em>main</em> (common) source set — it is pure recipe data (inputs, outputs, kind,
 * energy) built on REI's common API only, with no client/GUI types. Displays are built server-side by
 * {@link AlaReiCommonPlugin} (where the {@code RecipeManager} is populated) and synced to the client
 * via {@link #SERIALIZER}; the client only supplies the category widgets ({@code AlaProcessingCategory}).
 * This split is required on MC 26.2, where the client no longer receives full recipes.
 */
public class AlaProcessingDisplay extends BasicDisplay {
	private final ModRecipes.Kind kind;
	private final int energy;

	/** Build a display from a live recipe (the common case, used by the server-side filler). */
	public AlaProcessingDisplay(AlaProcessingRecipe recipe) {
		this(List.of(EntryIngredients.ofIngredient(recipe.ingredient())),
				List.of(EntryIngredients.of(recipe.result())),
				recipe.kind(), recipe.energy());
	}

	private AlaProcessingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			ModRecipes.Kind kind, int energy) {
		super(inputs, outputs);
		this.kind = kind;
		this.energy = energy;
	}

	/** Total EU spent to complete one operation (the recipe's nominal cost). */
	public int energy() {
		return energy;
	}

	/**
	 * Base processing time in ticks. Mirrors the machine's own formula
	 * ({@code MaceratorBlockEntity#onServerTick}): {@code max(1, energy / machineEuPerTick)}. The
	 * global speed multiplier ({@code scaledDuration}) is a runtime balance knob and intentionally
	 * not applied here — the viewer shows the recipe's intrinsic time.
	 */
	public int processingTicks() {
		return Math.max(1, energy / Config.machineEuPerTick);
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		// Each machine kind maps 1:1 to a REI category id (alaindustrial:<kind>). Computed directly so
		// this common-side class stays independent of the client plugin.
		return CategoryIdentifier.of(Industrialization.id(kind.id()));
	}

	@Override
	public DisplaySerializer<? extends me.shedaniel.rei.api.common.display.Display> getSerializer() {
		return SERIALIZER;
	}

	/** Round-trips a display as {inputs, outputs, kind id, energy}; kind resolves back via id. */
	public static final DisplaySerializer<AlaProcessingDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
					EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
					com.mojang.serialization.Codec.STRING.fieldOf("kind").forGetter(d -> d.kind.id()),
					com.mojang.serialization.Codec.INT.fieldOf("energy").forGetter(d -> d.energy)
			).apply(instance, AlaProcessingDisplay::fromParts)),
			StreamCodec.composite(
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getInputEntries,
					EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), BasicDisplay::getOutputEntries,
					ByteBufCodecs.STRING_UTF8, d -> d.kind.id(),
					ByteBufCodecs.INT, d -> d.energy,
					AlaProcessingDisplay::fromParts));

	private static AlaProcessingDisplay fromParts(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
			String kindId, int energy) {
		return new AlaProcessingDisplay(inputs, outputs, ModRecipes.byId(kindId), energy);
	}
}
