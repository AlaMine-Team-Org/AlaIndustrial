package dev.alaindustrial.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** MOD-257 regression coverage for the two expanded processing-recipe schemas. */
@ExtendWith(EphemeralTestServerProvider.class)
class RecipeCodecTest {

	private static RegistryOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	private static JsonElement json(String value) {
		return JsonParser.parseString(value);
	}

	private static AlaProcessingRecipe decodeItems(MinecraftServer server, String value) {
		return AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.parse(ops(server), json(value)).getOrThrow();
	}

	private static FluidOutputRecipe decodeFluids(MinecraftServer server, String value) {
		return FluidOutputRecipe.mapCodec(ModRecipes.DISTILLING).codec()
				.parse(ops(server), json(value)).getOrThrow();
	}

	@Test
	void legacySingleIngredientStillDecodes(MinecraftServer server) {
		AlaProcessingRecipe recipe = decodeItems(server, """
				{
				  "ingredient": "minecraft:iron_ingot",
				  "result": {"id": "minecraft:gold_ingot", "count": 1},
				  "energy": 300
				}
				""");

		assertEquals(1, recipe.ingredients().size());
		assertTrue(recipe.ingredient().test(new ItemStack(Items.IRON_INGOT)));

		JsonElement encoded = AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.encodeStart(ops(server), recipe).getOrThrow();
		JsonObject object = encoded.getAsJsonObject();
		assertTrue(object.has("ingredient"), "a one-slot recipe must retain the legacy singular field");
		assertFalse(object.has("ingredients"), "a one-slot recipe must not encode as the two-slot form");
		assertEquals(recipe, AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.parse(ops(server), encoded).getOrThrow());
	}

	@Test
	void twoIngredientsPersistentAndNetworkCodecsRoundTrip(MinecraftServer server) {
		AlaProcessingRecipe recipe = decodeItems(server, """
				{
				  "ingredients": ["minecraft:iron_ingot", "minecraft:redstone"],
				  "result": {"id": "minecraft:gold_ingot", "count": 2},
				  "energy": 300
				}
				""");

		JsonElement encoded = AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.encodeStart(ops(server), recipe).getOrThrow();
		JsonObject object = encoded.getAsJsonObject();
		assertTrue(object.has("ingredients"), "the canonical encoder must write the new list field");
		assertFalse(object.has("ingredient"), "the canonical encoder must not write the legacy scalar field");
		assertEquals(recipe, AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.parse(ops(server), encoded).getOrThrow());

		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		AlaProcessingRecipe.streamCodec(ModRecipes.MACERATION).encode(buffer, recipe);
		assertEquals(recipe, AlaProcessingRecipe.streamCodec(ModRecipes.MACERATION).decode(buffer));
	}

	@Test
	void multiIngredientMatchingIsOrderedAndExactSize(MinecraftServer server) {
		AlaProcessingRecipe recipe = decodeItems(server, """
				{
				  "ingredients": ["minecraft:iron_ingot", "minecraft:redstone"],
				  "result": {"id": "minecraft:gold_ingot"},
				  "energy": 300
				}
				""");

		assertTrue(recipe.matches(new ProcessingRecipeInput(
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE)), null));
		assertFalse(recipe.matches(new ProcessingRecipeInput(
				new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT)), null));
		assertFalse(recipe.matches(new ProcessingRecipeInput(new ItemStack(Items.IRON_INGOT)), null));
	}

	@Test
	void ingredientSchemaRejectsAmbiguousMissingAndOversizedForms(MinecraftServer server) {
		String result = "\"result\":{\"id\":\"minecraft:gold_ingot\"},\"energy\":300";
		assertItemDecodeFails(server, "{" + result + "}");
		assertItemDecodeFails(server, "{\"ingredient\":\"minecraft:iron_ingot\","
				+ "\"ingredients\":[\"minecraft:iron_ingot\"]," + result + "}");
		assertItemDecodeFails(server, "{\"ingredients\":[\"minecraft:iron_ingot\"]," + result + "}");
		assertItemDecodeFails(server, "{\"ingredients\":[\"minecraft:iron_ingot\","
				+ "\"minecraft:redstone\",\"minecraft:coal\"]," + result + "}");
	}

	private static void assertItemDecodeFails(MinecraftServer server, String value) {
		assertTrue(AlaProcessingRecipe.mapCodec(ModRecipes.MACERATION).codec()
				.parse(ops(server), json(value)).error().isPresent(), value);
	}

	@Test
	void twoFluidResultsPersistentAndNetworkCodecsRoundTrip(MinecraftServer server) {
		FluidOutputRecipe recipe = decodeFluids(server, """
				{
				  "fluid": "minecraft:water",
				  "amount": 1000,
				  "fluid_results": [
				    {"fluid": "minecraft:lava", "amount": 700},
				    {"fluid": "minecraft:water", "amount": 200}
				  ],
				  "energy": 400
				}
				""");

		assertEquals(2, recipe.fluidResults().size());
		JsonElement encoded = FluidOutputRecipe.mapCodec(ModRecipes.DISTILLING).codec()
				.encodeStart(ops(server), recipe).getOrThrow();
		assertEquals(recipe, FluidOutputRecipe.mapCodec(ModRecipes.DISTILLING).codec()
				.parse(ops(server), encoded).getOrThrow());

		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		FluidOutputRecipe.streamCodec(ModRecipes.DISTILLING).encode(buffer, recipe);
		assertEquals(recipe, FluidOutputRecipe.streamCodec(ModRecipes.DISTILLING).decode(buffer));
	}

	@Test
	void fluidResultSchemaRejectsInvalidCardinalityAndAmounts(MinecraftServer server) {
		assertFluidDecodeFails(server, """
				{"fluid":"minecraft:water","fluid_results":[],"energy":400}
				""");
		assertFluidDecodeFails(server, """
				{"fluid":"minecraft:water","fluid_results":[
				  {"fluid":"minecraft:water","amount":1},
				  {"fluid":"minecraft:lava","amount":2},
				  {"fluid":"minecraft:water","amount":3}
				],"energy":400}
				""");
		assertFluidDecodeFails(server, """
				{"fluid":"minecraft:water","fluid_results":[
				  {"fluid":"minecraft:lava","amount":0}
				],"energy":400}
				""");
	}

	private static void assertFluidDecodeFails(MinecraftServer server, String value) {
		assertTrue(FluidOutputRecipe.mapCodec(ModRecipes.DISTILLING).codec()
				.parse(ops(server), json(value)).error().isPresent(), value);
	}
}
