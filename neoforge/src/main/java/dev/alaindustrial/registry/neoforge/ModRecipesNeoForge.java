package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.RecipeFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge recipe registration (MOD-022 facade). NeoForge freezes the vanilla
 * {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries before mod construction, so the neutral
 * {@link ModRecipes} cannot self-register there (unlike Fabric). These {@link DeferredRegister}s replay
 * {@link ModRecipes#families()} — one {@link RecipeType} (when the family has its own) and one
 * {@link RecipeSerializer} per {@link RecipeFamily}, in list order — and {@link #init()} binds each family
 * to its deferred holders (each a {@code Supplier}, resolved lazily after the {@code RegisterEvent}).
 */
public final class ModRecipesNeoForge {
	public static final DeferredRegister<RecipeType<?>> TYPES =
			DeferredRegister.create(Registries.RECIPE_TYPE, Industrialization.MOD_ID);
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, Industrialization.MOD_ID);

	private static final List<Runnable> BINDERS = new ArrayList<>();

	static {
		for (RecipeFamily<?> family : ModRecipes.families()) {
			BINDERS.add(register(family));
		}
	}

	/** Queues one family on the two registers and returns the step that binds it to the holders. */
	private static <R extends Recipe<?>> Runnable register(RecipeFamily<R> family) {
		Supplier<RecipeType<R>> type = family.createType().isPresent()
				? TYPES.register(family.id(), () -> family.createType().orElseThrow())::get
				: RecipeFamily.noType(family.id());
		DeferredHolder<RecipeSerializer<?>, RecipeSerializer<R>> serializer =
				SERIALIZERS.register(family.id(), family::createSerializer);
		return () -> family.bind(type, serializer::get);
	}

	/** Bind each family to its deferred holders (lazy suppliers). Called from the {@code @Mod} ctor. */
	public static void init() {
		for (Runnable binder : BINDERS) {
			binder.run();
		}
	}

	private ModRecipesNeoForge() {
	}
}
