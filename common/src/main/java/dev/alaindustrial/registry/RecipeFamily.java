package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * One recipe family the mod registers: its id, the {@link RecipeType} it owns (if any) and its
 * {@link RecipeSerializer}. Every family is declared once in {@link ModRecipes} and listed by
 * {@link ModRecipes#families()} in registration order; Fabric registers them eagerly in
 * {@link ModRecipes#init()}, NeoForge through a {@code DeferredRegister} in {@code ModRecipesNeoForge}, and
 * the Fabric entry point opts every serializer into fabric-api's recipe sync from the same list.
 *
 * <p>Adding a family of an existing kind is one declaration in {@link ModRecipes}; a new kind of family is
 * one class implementing this interface. Neither loader nor {@code recipe_sync_check.py} has a list to
 * extend.
 *
 * @param <R> the recipe class the serializer reads and writes
 */
public sealed interface RecipeFamily<R extends Recipe<?>>
		permits MachineRecipeFamily, ChargedCraft {

	/** The registry path of the type and the serializer — also the {@code "type"} recipe JSON names. */
	String id();

	/**
	 * A new instance of the recipe type this family registers, or empty for a family whose recipes report a
	 * vanilla type (the charge-transfer crafting recipe is a {@code crafting} recipe).
	 */
	Optional<RecipeType<R>> createType();

	/** A new instance of the serializer this family registers. */
	RecipeSerializer<R> createSerializer();

	/**
	 * Binds the registered instances. Called once per loader during its registration; the type supplier of a
	 * family without its own type is never read.
	 */
	void bind(Supplier<RecipeType<R>> type, Supplier<RecipeSerializer<R>> serializer);

	/** The registered serializer. */
	RecipeSerializer<R> serializer();

	/** The type supplier a loader binds to a family whose {@link #createType()} is empty. */
	static <R extends Recipe<?>> Supplier<RecipeType<R>> noType(String id) {
		return () -> {
			throw new IllegalStateException("recipe family " + id + " has no recipe type of its own");
		};
	}

	/** The {@link RecipeType} a machine family registers: named after its id, nothing else. */
	static <R extends Recipe<?>> RecipeType<R> namedType(String path) {
		Identifier id = Industrialization.id(path);
		return new RecipeType<R>() {
			@Override
			public String toString() {
				return id.toString();
			}
		};
	}
}
