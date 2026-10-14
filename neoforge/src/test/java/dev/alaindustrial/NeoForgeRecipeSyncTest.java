package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.registry.ModRecipes;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-750 — the NeoForge server asks to sync every recipe type of the mod. Goes through the real
 * {@link OnDatapackSyncEvent} (the one object NeoForge reads {@code getRecipeTypesToSend()} from when it
 * builds the client packet), not the single-player shortcut the JEI plugin takes. The oracle is the live
 * {@code RECIPE_TYPE} registry filtered to our namespace, so a type registered but missing from
 * {@link ModRecipes#families()} fails here too. What it cannot prove: that a remote client then shows the
 * categories — that needs a dedicated server and a client (see the task's manual criterion).
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class NeoForgeRecipeSyncTest {

	@Test
	void listenerRequestsEveryRegisteredModRecipeType() {
		Set<RecipeType<?>> registered = new HashSet<>();
		for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
			if (BuiltInRegistries.RECIPE_TYPE.getKey(type).getNamespace().equals(Industrialization.MOD_ID)) {
				registered.add(type);
			}
		}
		assertTrue(registered.size() >= 10, "the registry scan found only " + registered.size()
				+ " mod recipe types - the oracle itself is broken");

		OnDatapackSyncEvent event = new OnDatapackSyncEvent(null, null);
		assertTrue(event.getRecipeTypesToSend().isEmpty(), "a fresh event must start empty");
		NeoForgeRecipeSync.requestModRecipeTypes(event);

		Set<RecipeType<?>> requested = event.getRecipeTypesToSend();
		Set<RecipeType<?>> missing = new HashSet<>(registered);
		missing.removeAll(requested);
		assertTrue(missing.isEmpty(), "mod recipe types not requested for client sync: " + missing);
		assertEquals(registered, new HashSet<>(requested), "requested set differs from the registered mod types");
		assertFalse(requested.contains(net.minecraft.world.item.crafting.RecipeType.CRAFTING),
				"vanilla types are JEI's to request, not ours");
		assertEquals(ModRecipes.syncedRecipeTypes().size(), requested.size());
	}
}
