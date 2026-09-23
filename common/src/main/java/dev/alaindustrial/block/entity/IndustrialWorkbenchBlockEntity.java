package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Industrial Workbench's memory (MOD-656): the recipe last crafted on THIS bench.
 *
 * <p>Only the recipe key is kept, never the grid: the layout the player sees next time is rebuilt from
 * the recipe itself, so a datapack that changes the recipe changes the hint with it, and a recipe that
 * no longer exists simply shows nothing. The memory is per bench on purpose — a bench kept for doors and
 * another kept for sticks each remember their own job.
 *
 * <p>Server-only state: the client never reads it. The hint reaches the client as vanilla's ghost-recipe
 * packet when the menu opens ({@code IndustrialWorkbenchBlock}), so no update tag is sent.
 */
public class IndustrialWorkbenchBlockEntity extends BlockEntity {

	private static final String KEY_LAST_RECIPE = "LastRecipe";

	private @Nullable ResourceKey<Recipe<?>> lastRecipe;

	public IndustrialWorkbenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.INDUSTRIAL_WORKBENCH_BE.get(), pos, state);
	}

	public Optional<ResourceKey<Recipe<?>>> lastRecipe() {
		return Optional.ofNullable(lastRecipe);
	}

	/** Remember {@code recipe} as the one to hint next time; a repeat of the same recipe writes nothing. */
	public void remember(ResourceKey<Recipe<?>> recipe) {
		if (!recipe.equals(lastRecipe)) {
			lastRecipe = recipe;
			setChanged();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (lastRecipe != null) {
			output.store(KEY_LAST_RECIPE, Recipe.KEY_CODEC, lastRecipe);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lastRecipe = input.read(KEY_LAST_RECIPE, Recipe.KEY_CODEC).orElse(null);
	}
}
