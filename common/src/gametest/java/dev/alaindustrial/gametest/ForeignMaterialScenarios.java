package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The cross-mod half of MOD-290, asserted rather than argued: <b>a material from a mod this one has
 * never heard of goes into this mod's machines and this mod's crafts</b>, purely because both sides
 * speak the {@code c:} convention tags.
 *
 * <h2>Why a stand-in, and why it is not a cheat</h2>
 *
 * The acceptance criterion asks for "a build with a foreign tech mod adding tin". No third-party tech
 * mod exists for MC 26.2, so the criterion cannot be met by installing one — but it can be met by
 * <em>being</em> one, which is the precedent {@code ForeignEnergyItemMod} set for the energy bridge
 * (MOD-084). Two items are registered under the {@code foreignmod} namespace from the gametest source
 * set (its own mod on Fabric; dev-only inside the mod on NeoForge, MOD-242) and dropped into
 * {@code #c:ingots/tin} and {@code #c:ores/tin} by a gametest-only datapack, exactly as a real mod
 * would.
 *
 * <p>What makes it a real test rather than a rehearsal: <b>nothing in {@code alaindustrial} mentions
 * those ids.</b> The macerator recipe names {@code #alaindustrial:macerable_tin}, which pulls in
 * {@code #c:ores/tin} with {@code "required": false}; the battery recipe names {@code #c:ingots/tin}
 * directly. Both resolve through the stock {@code Ingredient}, which does not record who registered
 * an item — so the code under test genuinely cannot tell the difference.
 *
 * <p><b>What it still does not prove</b>, and the task log says so: that some specific real mod puts
 * <em>its</em> tin into {@code c:ingots/tin}. That is the other mod's decision, and only a build with
 * it installed can confirm it. What is proved here is our whole half of the contract — the tags are
 * consumed, they are optional, and a foreign item reaching them is accepted end to end.
 *
 * <p>Suite contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class ForeignMaterialScenarios {

	private ForeignMaterialScenarios() {
	}

	/** Namespace of the stand-in mod. Deliberately not {@code alaindustrial}. */
	private static final String FOREIGN = "foreignmod";

	private static final Identifier FOREIGN_INGOT = Identifier.fromNamespaceAndPath(FOREIGN, "tin_ingot");
	private static final Identifier FOREIGN_ORE = Identifier.fromNamespaceAndPath(FOREIGN, "tin_ore");

	private static final BlockPos MAC = new BlockPos(1, 2, 1);

	/** The stand-in item, or {@code null} when the fake mod did not register on this loader. */
	private static Item foreign(Identifier id) {
		return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	private static TagKey<Item> itemTag(String namespace, String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
	}

	private static boolean tagHolds(TagKey<Item> tag, Item item) {
		for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
			if (holder.value() == item) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The foreign ore is pulled into the mod's own macerable list through {@code #c:ores/tin}, and the
	 * foreign ingot lands in {@code #c:ingots/tin}.
	 *
	 * <p>The first half is the one that can rot silently: {@code macerable_tin} lists our two ores by
	 * id <em>and</em> references {@code #c:ores/tin} with {@code "required": false}, and a typo behind
	 * that flag is invisible in the resolved tag — our own ids cover the same ground. A foreign item is
	 * the only thing that can tell the two apart, which is precisely why this test needs one.
	 *
	 * @implements TC-TAGS-004 — a foreign item reaches the mod's tags through the c: conventions.
	 */
	public static void tags04ForeignItemReachesOurTags(GameTestHelper helper) {
		Item ingot = foreign(FOREIGN_INGOT);
		Item ore = foreign(FOREIGN_ORE);
		if (ingot == null || ore == null) {
			helper.fail("the stand-in foreign mod did not register " + FOREIGN_INGOT + " / " + FOREIGN_ORE
					+ " on this loader — the cross-mod lane is not actually running");
			return;
		}
		if (!tagHolds(itemTag("c", "ingots/tin"), ingot)) {
			helper.fail(FOREIGN_INGOT + " is not in #c:ingots/tin — the test datapack did not merge into "
					+ "the tag the mod declares, so nothing below would mean anything");
			return;
		}
		if (!tagHolds(itemTag("c", "ores/tin"), ore)) {
			helper.fail(FOREIGN_ORE + " is not in #c:ores/tin");
			return;
		}
		// The point of the whole task: OUR tag absorbs a foreign item without naming it.
		if (!tagHolds(itemTag("alaindustrial", "macerable_tin"), ore)) {
			helper.fail(FOREIGN_ORE + " did not reach #alaindustrial:macerable_tin — the optional "
					+ "#c:ores/tin reference in that tag file is not resolving, and a foreign tin ore "
					+ "would silently not be macerable");
			return;
		}
		helper.succeed();
	}

	/**
	 * The macerator actually grinds the foreign ore, and the dust that comes out is <b>ours</b>.
	 *
	 * <p>Both halves matter. That it runs at all is the cross-mod promise; that the output is
	 * {@code alaindustrial:tin_dust} rather than anything else is the documented consequence the spec
	 * warns about — a foreign input yields our material, and that is intended, not a bug.
	 *
	 * @implements TC-TAGS-005 — the macerator processes a foreign mod's tin ore.
	 */
	public static void tags05MaceratorGrindsForeignOre(GameTestHelper helper) {
		Item ore = foreign(FOREIGN_ORE);
		if (ore == null) {
			helper.fail("the stand-in foreign mod did not register " + FOREIGN_ORE + " on this loader");
			return;
		}
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		MaceratorBlockEntity mac = helper.getBlockEntity(MAC, MaceratorBlockEntity.class);
		mac.getEnergyStorage().setAmountUntracked(8000); // more than one operation's E_op
		mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(ore));
		for (int i = 0; i < 400; i++) {
			mac.serverTick(helper.getLevel(), mac.getBlockPos(),
					helper.getLevel().getBlockState(mac.getBlockPos()));
		}
		ItemStack out = mac.getItem(MaceratorBlockEntity.OUTPUT_SLOT);
		if (out.isEmpty()) {
			helper.fail("the macerator produced nothing from " + FOREIGN_ORE
					+ " — a foreign tin ore is NOT accepted, which is the whole promise of MOD-290");
			return;
		}
		if (!out.is(ModContent.TIN_DUST.get())) {
			helper.fail("the macerator turned " + FOREIGN_ORE + " into " + out.getItem()
					+ ", expected alaindustrial:tin_dust");
			return;
		}
		helper.succeed();
	}

	/**
	 * A crafting recipe of this mod written against {@code #c:ingots/tin} is satisfied by the foreign
	 * ingot, and makes the mod's own item.
	 *
	 * <p>Goes through the server's real {@code RecipeManager} with a {@code CraftingInput} — the same
	 * path a crafting table and the Assembler take — rather than inspecting the ingredient, because an
	 * ingredient that "contains" the item and a recipe that actually resolves are not the same claim
	 * (a mis-declared recipe never loads at all, and inspection of loaded recipes cannot see that).
	 *
	 * @implements TC-TAGS-006 — a mod craft written on a c: tag accepts a foreign ingot.
	 */
	public static void tags06ForeignIngotFeedsOurCraft(GameTestHelper helper) {
		Item ingot = foreign(FOREIGN_INGOT);
		if (ingot == null) {
			helper.fail("the stand-in foreign mod did not register " + FOREIGN_INGOT + " on this loader");
			return;
		}
		// data/alaindustrial/recipe/battery.json — " C " / "TRT" / "TRT", T = #c:ingots/tin,
		// R = #c:dusts/redstone, C = alaindustrial:copper_cable.
		List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
		grid.set(1, new ItemStack(ModContent.COPPER_CABLE_ITEM.get()));
		grid.set(3, new ItemStack(ingot));
		grid.set(4, new ItemStack(net.minecraft.world.item.Items.REDSTONE));
		grid.set(5, new ItemStack(ingot));
		grid.set(6, new ItemStack(ingot));
		grid.set(7, new ItemStack(net.minecraft.world.item.Items.REDSTONE));
		grid.set(8, new ItemStack(ingot));

		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 3, grid);
		Optional<RecipeHolder<CraftingRecipe>> found =
				level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
		if (found.isEmpty()) {
			helper.fail("the battery recipe did not resolve with " + FOREIGN_INGOT + " in the tin slots — "
					+ "a craft written on #c:ingots/tin does not in fact accept a foreign mod's tin");
			return;
		}
		ItemStack result = found.get().value().assemble(input);
		if (!result.is(ModContent.BATTERY.get())) {
			helper.fail("the grid resolved to " + result.getItem() + ", expected alaindustrial:battery");
			return;
		}
		helper.succeed();
	}
}
