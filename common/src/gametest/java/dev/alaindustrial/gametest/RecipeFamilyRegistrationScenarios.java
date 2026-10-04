package dev.alaindustrial.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.alaindustrial.Industrialization;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * L2 characterization of the recipe families' registration (MOD-708, batch 0), on both loaders.
 *
 * <p>Two facts are pinned. Every {@code "type"} the mod ships under {@code data/alaindustrial/recipe/**}
 * has a registered {@code RecipeSerializer}, and a registered {@code RecipeType} unless it is a crafting
 * recipe (the charge-transfer recipe reports vanilla's {@code crafting} type). And the mod's serializer
 * and type ids sit in the registries in the order written below — the order the families are
 * registered in. Folding the three family classes and the charge-transfer branch into one list must
 * keep both.
 *
 * <p>The ids are compared by their numeric registry id, so only the mod's own relative order is
 * pinned; what the game or another mod registers in between does not matter.
 *
 * <p><b>Updated only by hand</b> from the capture this scenario logs on a mismatch, in a commit that names
 * the behaviour change (ADR-032).
 */
public final class RecipeFamilyRegistrationScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RecipeFamilyRegistrationScenarios::recipeFamiliesRegisteredInOrder,
						"recipe_families_registered_in_order").ticks(40));

		private Roster() {}
	}

	private RecipeFamilyRegistrationScenarios() {}

	/** The mod's recipe serializers, in registry order. The charge-transfer crafting serializer is first. */
	static final List<String> EXPECTED_SERIALIZERS = List.of(
			"crafting_shaped_charge_transfer",
			"maceration", "smelting", "compressing", "extracting", "vulcanizing", "galvanic_bath", "centrifuging",
			"sawing_planks", "sawing_sticks", "sawing_slabs", "sawing_stairs",
			"mutation_transform", "mutation_duplicate", "mutation_create", "fermenting",
			"polymerizing", "distilling", "alloying");

	/** The mod's recipe types, in registry order. The crafting recipe has none: it is vanilla's crafting. */
	static final List<String> EXPECTED_TYPES = List.of(
			"maceration", "smelting", "compressing", "extracting", "vulcanizing", "galvanic_bath", "centrifuging",
			"sawing_planks", "sawing_sticks", "sawing_slabs", "sawing_stairs",
			"mutation_transform", "mutation_duplicate", "mutation_create", "fermenting",
			"polymerizing", "distilling", "alloying");

	/** The type the charge-transfer recipes declare; it is a serializer only. */
	private static final String CRAFTING_ONLY = "crafting_shaped_charge_transfer";

	/** Paths of the mod's entries in {@code registry}, ordered by their numeric registry id. */
	static <T> List<String> modIdsInOrder(Registry<T> registry) {
		List<T> values = new ArrayList<>();
		for (T value : registry) {
			Identifier key = registry.getKey(value);
			if (key != null && Industrialization.MOD_ID.equals(key.getNamespace())) {
				values.add(value);
			}
		}
		values.sort((a, b) -> Integer.compare(registry.getId(a), registry.getId(b)));
		List<String> paths = new ArrayList<>();
		for (T value : values) {
			paths.add(registry.getKey(value).getPath());
		}
		return paths;
	}

	/** The mod's own recipe types named by the shipped recipe files, without namespace, sorted. */
	static TreeSet<String> shippedTypes(GameTestHelper helper) {
		TreeSet<String> types = new TreeSet<>();
		Map<Identifier, Resource> files = helper.getLevel().getServer().getResourceManager().listResources("recipe",
				id -> Industrialization.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			try (BufferedReader reader = file.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
					continue;
				}
				String type = json.getAsJsonObject().get("type").getAsString();
				String prefix = Industrialization.MOD_ID + ":";
				if (type.startsWith(prefix)) {
					types.add(type.substring(prefix.length()));
				}
			} catch (IOException | RuntimeException e) {
				throw new IllegalStateException("cannot read recipe " + file.getKey() + ": " + e, e);
			}
		}
		return types;
	}

	/**
	 * @implements MOD-708-RCP01 — every shipped recipe type has its serializer (and its type, unless it is a
	 *     crafting recipe), and the mod's serializers and types keep their registration order, on both loaders
	 */
	public static void recipeFamiliesRegisteredInOrder(GameTestHelper helper) {
		TreeSet<String> shipped = shippedTypes(helper);
		if (shipped.size() < 10) {
			helper.fail("MOD-708: only " + shipped.size() + " mod recipe types found in the data — the resource"
					+ " listing no longer reaches data/alaindustrial/recipe");
			return;
		}
		List<String> missing = new ArrayList<>();
		for (String type : shipped) {
			Identifier id = Industrialization.id(type);
			if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id)) {
				missing.add("serializer " + id);
			}
			if (!CRAFTING_ONLY.equals(type) && !BuiltInRegistries.RECIPE_TYPE.containsKey(id)) {
				missing.add("type " + id);
			}
		}
		if (!missing.isEmpty()) {
			helper.fail("MOD-708: shipped recipe types without registration: " + missing);
			return;
		}
		List<String> serializers = modIdsInOrder(BuiltInRegistries.RECIPE_SERIALIZER);
		List<String> types = modIdsInOrder(BuiltInRegistries.RECIPE_TYPE);
		if (!serializers.equals(EXPECTED_SERIALIZERS) || !types.equals(EXPECTED_TYPES)) {
			Industrialization.LOGGER.info("MOD-708 recipe registration differs\nserializers {}\ntypes {}\nshipped {}",
					serializers, types, shipped);
			helper.fail("MOD-708: recipe registration order differs — serializers " + serializers + " (expected "
					+ EXPECTED_SERIALIZERS + "), types " + types + " (expected " + EXPECTED_TYPES + ")");
			return;
		}
		helper.succeed();
	}
}
