package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModRecipes.Kind;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-022 — NeoForge REGISTRATION-completeness guard. Boots a real {@link MinecraftServer} (via
 * {@link EphemeralTestServerProvider}) so every mod-bus {@code RegisterEvent} fired and datapacks loaded,
 * then asserts the registries + neutral facades that the multiloader migration WOULD leave empty on
 * NeoForge if the {@code DeferredRegister} + {@code init()} wiring regressed.
 *
 * <p>These are the exact defects the review caught: NeoForge freezes the vanilla registries before mod
 * construction, so the common {@code Mod*} classes' direct {@code Registry.register} (fine on Fabric) never
 * runs there. Each machine {@code RecipeType}/{@code RecipeSerializer}, the {@code network_energized}
 * trigger, the {@code stored_energy}/{@code network_scan} data components and the {@code macerator_grind}
 * sound must instead be registered via a {@code DeferredRegister} (see {@code Mod*NeoForge}). Before that
 * fix every assertion below failed — recipes did not parse, and the facade suppliers threw
 * {@code IllegalStateException "read before its loader bound it"}. This test is the regression guard so a
 * future missed NeoForge registration fails CI instead of shipping a silently-dead subsystem.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeRegistrationTest {

	private static Identifier id(String path) {
		return Industrialization.id(path);
	}

	/** Every custom machine {@link RecipeType} + {@link RecipeSerializer} registered under the mod namespace. */
	@Test
	void recipeTypesAndSerializersRegistered() {
		for (Kind kind : ModRecipes.kinds()) {
			Identifier id = id(kind.id());
			assertTrue(BuiltInRegistries.RECIPE_TYPE.containsKey(id),
					"RECIPE_TYPE missing alaindustrial:" + kind.id() + " (NeoForge DeferredRegister regressed)");
			assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id),
					"RECIPE_SERIALIZER missing alaindustrial:" + kind.id() + " (recipes would not parse)");
			// The neutral facade must resolve to the registered instances, not throw the unbound-supplier guard.
			assertNotNull(kind.type(), "ModRecipes." + kind.id() + ".type() unbound on NeoForge");
			assertNotNull(kind.serializer(), "ModRecipes." + kind.id() + ".serializer() unbound on NeoForge");
		}
	}

	/** The {@code network_energized} advancement trigger + its neutral facade handle. */
	@Test
	void criterionTriggerRegistered() {
		assertTrue(BuiltInRegistries.TRIGGER_TYPES.containsKey(id("network_energized")),
				"TRIGGER_TYPES missing alaindustrial:network_energized (would crash on machine/cable placement)");
		assertNotNull(ModCriteria.NETWORK_ENERGIZED.get(), "ModCriteria.NETWORK_ENERGIZED unbound on NeoForge");
	}

	/** The custom data components + their neutral facade handles. */
	@Test
	void dataComponentsRegistered() {
		assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id("stored_energy")),
				"DATA_COMPONENT_TYPE missing alaindustrial:stored_energy (battery box would lose EU on drop)");
		assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id("network_scan")),
				"DATA_COMPONENT_TYPE missing alaindustrial:network_scan");
		assertNotNull(ModDataComponents.STORED_ENERGY.get(), "ModDataComponents.STORED_ENERGY unbound on NeoForge");
		assertNotNull(ModDataComponents.NETWORK_SCAN.get(), "ModDataComponents.NETWORK_SCAN unbound on NeoForge");
	}

	/** The macerator ambient sound + its neutral facade handle. */
	@Test
	void soundRegistered() {
		assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id("macerator_grind")),
				"SOUND_EVENT missing alaindustrial:macerator_grind");
		assertNotNull(ModSounds.MACERATOR_GRIND.get(), "ModSounds.MACERATOR_GRIND unbound on NeoForge");
	}

	/**
	 * End-to-end proof the recipe serializers actually parse data: the server's recipe manager holds at
	 * least one recipe in the mod namespace. Before the fix, every {@code alaindustrial:*} recipe failed to
	 * parse ("Unknown registry key in recipe_serializer") and this count was zero.
	 */
	@Test
	void recipesActuallyLoaded(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");
		int modRecipes = 0;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())) {
				modRecipes++;
			}
		}
		assertTrue(modRecipes > 0,
				"no alaindustrial recipes parsed — recipe serializers not registered on NeoForge");
	}
}
