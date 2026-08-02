package dev.alaindustrial.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Loader-neutral guards for the common-tag ingredients introduced by MOD-290.
 *
 * <p>The mod's recipes no longer name items directly where the material is interchangeable — they
 * name a {@code c:} tag ({@code #c:ingots/iron}, {@code #c:plates/tin}, …). That buys cross-mod
 * compatibility at the price of a new, entirely silent failure mode: if a tag is misspelled, or a
 * loader stops shipping it, the {@link Ingredient} resolves to <em>nothing</em>. The recipe still
 * loads, still validates, still passes every static check — and simply never matches in-game. No
 * crash, no log line, no failing test. The player just finds the recipe missing.
 *
 * <p>Both scenarios below run on Fabric and NeoForge deliberately: the {@code c:} tags are supplied
 * by the loader (fabric-convention-tags-v2 on one side, NeoForge core on the other), so "the tag is
 * populated" is a per-loader fact and has to be asserted on each lane separately.
 *
 * <p>Suite contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class RecipeTagScenarios {

	private RecipeTagScenarios() {}

	/**
	 * A representative slice of the {@code c:} tags the mod's recipes consume — one per family, split
	 * between tags the loader supplies and tags the mod declares itself. Not the full list (47 tags):
	 * this is a canary, and the exhaustive check is {@link #everyModRecipeIngredientResolves}.
	 */
	private static final String[] CANARY_TAGS = {
		// supplied by the loader (fabric-convention-tags-v2 / NeoForge core)
		"ingots/iron", "ingots/copper", "ingots/gold", "dusts/redstone", "gems/diamond",
		"gems/lapis", "raw_materials/iron", "glass_blocks/colorless", "rods/wooden", "strings",
		// declared by this mod in data/c/tags/
		"ingots/tin", "ingots/silver", "ingots/nickel", "ingots/uranium",
		"dusts/sulfur", "plates/iron", "raw_materials/tin", "ores/tin",
	};

	/** Recipe JSONs live under {@code data/<ns>/recipe/**}; the resource root has no trailing slash. */
	private static final String RECIPE_ROOT = "recipe";

	/** Item-tag JSONs live under {@code data/<ns>/tags/item/**}. */
	private static final String ITEM_TAG_ROOT = "tags/item";

	/**
	 * Every {@code c:} tag the mod's recipes rely on resolves to at least one item.
	 *
	 * <p>Fails loudly on the exact regression MOD-290 could introduce: a tag path typo, or a loader
	 * that no longer ships a convention tag we assumed was there.
	 *
	 * @implements TC-TAGS-001 — consumed common tags are non-empty on this loader.
	 */
	public static void consumedCommonTagsAreNonEmpty(GameTestHelper helper) {
		List<String> empty = new ArrayList<>();
		for (String path : CANARY_TAGS) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
			// getTagOrEmpty: an absent tag and an empty tag are the same failure here — nothing matches.
			if (!BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext()) {
				empty.add("#c:" + path);
			}
		}
		if (!empty.isEmpty()) {
			helper.fail("common tags consumed by mod recipes are EMPTY on this loader: " + empty
					+ " — a recipe naming an empty tag loads fine and then never matches in-game");
			return;
		}
		helper.succeed();
	}

	/**
	 * Every foreign tag the mod's own item-tag files pull in actually exists and is populated.
	 *
	 * <p>Those references carry {@code "required": false} so a missing tag cannot break world load.
	 * The price is that a <em>misspelled</em> name is skipped just as quietly: the file parses, the
	 * list loads short one entry, and the cross-mod half of the feature silently does not exist.
	 * Nothing else in the build notices — the sibling explicit ids keep every clean-install test
	 * green, so contents alone cannot tell "absorbed" from "ignored".
	 *
	 * <p>So this reads the tag JSONs back through the {@link net.minecraft.server.packs.resources.ResourceManager}
	 * and checks the referenced names themselves, rather than what they happen to resolve to.
	 *
	 * @implements TC-TAGS-003 — optional foreign tag references in the mod's tag files resolve.
	 */
	public static void referencedForeignTagsResolve(GameTestHelper helper) {
		ResourceManager resources = helper.getLevel().getServer().getResourceManager();
		Map<Identifier, Resource> files = resources.listResources(
				ITEM_TAG_ROOT, path -> path.getPath().endsWith(".json"));

		List<String> broken = new ArrayList<>();
		int refs = 0;
		for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
			if (!entry.getKey().getNamespace().equals("alaindustrial")) {
				continue;
			}
			JsonObject json;
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				json = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (IOException | RuntimeException e) {
				broken.add(entry.getKey() + ": unreadable (" + e + ")");
				continue;
			}
			JsonArray values = json.getAsJsonArray("values");
			if (values == null) {
				continue;
			}
			for (JsonElement value : values) {
				String id = value.isJsonObject()
						? value.getAsJsonObject().get("id").getAsString()
						: value.getAsString();
				if (!id.startsWith("#")) {
					continue; // a plain item id; the registry check is not ours to make here
				}
				String tagPath = id.substring(1);
				if (tagPath.startsWith("alaindustrial:")) {
					continue; // our own tag, covered by the files in this very scan
				}
				refs++;
				TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(tagPath));
				if (!BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext()) {
					broken.add(entry.getKey() + " -> " + id);
				}
			}
		}

		if (refs == 0) {
			helper.fail("no foreign tag references found in alaindustrial item-tag files — the scan "
					+ "found nothing to check and would pass vacuously");
			return;
		}
		if (!broken.isEmpty()) {
			helper.fail("optional foreign tag references that resolve to NOTHING (a typo behind "
					+ "'required: false' is skipped in silence): " + broken + " — checked " + refs);
			return;
		}
		helper.succeed();
	}

	/**
	 * Every shipped recipe JSON actually loaded, and none of the loaded ones carries an ingredient
	 * that accepts no item.
	 *
	 * <p>The first half is the one that matters and it was found the hard way: a recipe naming a tag
	 * that does not exist <em>fails to parse</em> and is dropped from the {@code RecipeManager}
	 * entirely. It never reaches any check that walks loaded recipes — the recipe is simply gone, and
	 * only a line buried in the server log says so. Comparing the JSON files on disk against the
	 * loaded recipes is therefore the only way to see it, and it covers every recipe at once instead
	 * of the handful that have a hand-written resolve test.
	 *
	 * <p>The second half covers the other shape: a tag that exists but is empty. There the recipe
	 * loads fine and the ingredient silently matches nothing.
	 *
	 * @implements TC-TAGS-002 — every mod recipe JSON loads, and no loaded ingredient matches nothing.
	 */
	public static void everyModRecipeIngredientResolves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// (a) shipped on disk
		int shipped = level.getServer().getResourceManager()
				.listResources(RECIPE_ROOT, path -> path.getPath().endsWith(".json"))
				.keySet().stream()
				.filter(path -> path.getNamespace().equals("alaindustrial"))
				.mapToInt(path -> 1)
				.sum();

		// (b) actually loaded, with a live ingredient scan on the way through
		List<String> dead = new ArrayList<>();
		int loaded = 0;
		for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
			Identifier id = holder.id().identifier();
			if (!id.getNamespace().equals("alaindustrial")) {
				continue;
			}
			loaded++;
			Recipe<?> recipe = holder.value();
			if (recipe.isSpecial()) {
				continue; // no fixed ingredient list to inspect
			}
			List<Ingredient> ingredients = recipe.placementInfo().ingredients();
			for (int i = 0; i < ingredients.size(); i++) {
				if (ingredients.get(i).isEmpty()) {
					dead.add(id + "[" + i + "]");
				}
			}
		}

		if (shipped == 0) {
			helper.fail("no alaindustrial recipe JSONs visible to the ResourceManager — the scan below "
					+ "would pass vacuously");
			return;
		}
		if (loaded < shipped) {
			helper.fail((shipped - loaded) + " of " + shipped + " alaindustrial recipe JSONs did NOT load "
					+ "(only " + loaded + " reached the RecipeManager) — a recipe that names a missing tag "
					+ "fails to parse and disappears; check the server log for 'Couldn't parse data file'");
			return;
		}
		if (!dead.isEmpty()) {
			helper.fail("recipes with an ingredient that matches NOTHING (empty tag): " + dead
					+ " — scanned " + loaded + " recipes");
			return;
		}
		helper.succeed();
	}
}
