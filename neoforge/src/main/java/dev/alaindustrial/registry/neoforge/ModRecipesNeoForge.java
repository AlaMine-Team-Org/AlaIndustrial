package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.PolymerizingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModRecipes.FluidKind;
import dev.alaindustrial.registry.ModRecipes.Kind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge machine-recipe registration (MOD-022 facade). NeoForge freezes the vanilla
 * {@code RECIPE_TYPE}/{@code RECIPE_SERIALIZER} registries before mod construction, so the neutral
 * {@link ModRecipes} cannot self-register there (unlike Fabric). These {@link DeferredRegister}s register
 * one {@link RecipeType}+{@link RecipeSerializer} per {@link Kind} on the mod bus, then {@link #init()}
 * binds each kind to its deferred holders (each a {@code Supplier}, resolved lazily after the
 * {@code RegisterEvent}).
 */
public final class ModRecipesNeoForge {
	public static final DeferredRegister<RecipeType<?>> TYPES =
			DeferredRegister.create(Registries.RECIPE_TYPE, Industrialization.MOD_ID);
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, Industrialization.MOD_ID);

	private record Holders(Kind kind,
			DeferredHolder<RecipeType<?>, RecipeType<AlaProcessingRecipe>> type,
			DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlaProcessingRecipe>> serializer) {
	}

	/** The same pairing for the fluid-input families (MOD-019) — a different recipe class, same lifecycle. */
	private record FluidHolders(FluidKind kind,
			DeferredHolder<RecipeType<?>, RecipeType<PolymerizingRecipe>> type,
			DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PolymerizingRecipe>> serializer) {
	}

	private static final List<Holders> HOLDERS = new ArrayList<>();
	private static final List<FluidHolders> FLUID_HOLDERS = new ArrayList<>();

	static {
		for (Kind kind : ModRecipes.kinds()) {
			DeferredHolder<RecipeType<?>, RecipeType<AlaProcessingRecipe>> type =
					TYPES.register(kind.id(), () -> ModRecipes.createType(kind));
			DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlaProcessingRecipe>> serializer =
					SERIALIZERS.register(kind.id(), () -> ModRecipes.createSerializer(kind));
			HOLDERS.add(new Holders(kind, type, serializer));
		}
		for (FluidKind kind : ModRecipes.fluidKinds()) {
			DeferredHolder<RecipeType<?>, RecipeType<PolymerizingRecipe>> type =
					TYPES.register(kind.id(), () -> ModRecipes.createType(kind));
			DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PolymerizingRecipe>> serializer =
					SERIALIZERS.register(kind.id(), () -> ModRecipes.createSerializer(kind));
			FLUID_HOLDERS.add(new FluidHolders(kind, type, serializer));
		}
	}

	/** Bind each family to its deferred holders (lazy suppliers). Called from the {@code @Mod} ctor. */
	public static void init() {
		for (Holders h : HOLDERS) {
			h.kind().bind(h.type()::get, h.serializer()::get);
		}
		for (FluidHolders h : FLUID_HOLDERS) {
			h.kind().bind(h.type()::get, h.serializer()::get);
		}
	}

	private ModRecipesNeoForge() {
	}
}
