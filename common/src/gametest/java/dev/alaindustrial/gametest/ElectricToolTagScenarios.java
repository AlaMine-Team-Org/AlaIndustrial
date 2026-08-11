package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Loader-neutral gametest bodies for the membership tags of the three diamond-tipped electric tools
 * (MOD-389, suite TC-ETOOL-001). Wrapped by the Fabric {@code ElectricToolTagsGameTest} suite and
 * registered on the NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders run
 * the SAME checks.
 *
 * <p><b>The defect these guard is a recurrence of MOD-057</b>, whose mechanism is written out in
 * {@link TemperedIronToolScenarios} and holds here word for word: {@code Item.Properties.{pickaxe,axe,
 * hoe}()} attach only the {@code Tool} data-component and add nothing to
 * {@code #minecraft:{pickaxes,axes,hoes}}, while every enchantment's {@code supported_items} resolves
 * through those very tags. All three upgrades declared {@code .enchantable(10)} and belonged to no tag at
 * all, so the enchanting table offered a player nothing for a tool they had just spent diamonds to build —
 * while the un-upgraded version worked. The base tools were tagged; only the upgrades were forgotten,
 * which is exactly the shape of gap a test on the base tools alone cannot see.
 *
 * <p>The drill's two extra tags are not decoration. {@code #minecraft:cluster_max_harvestables} is read by
 * vanilla's amethyst-cluster loot table (4 shards instead of 2), so a missing entry made the <i>upgrade</i>
 * mine amethyst worse than the tool it was crafted from; {@code #c:tools/mining_tool} is the cross-mod
 * convention tag other mods query.
 */
public final class ElectricToolTagScenarios {

	private ElectricToolTagScenarios() {}

	/** {@code #c:tools/mining_tool} — the cross-mod convention tag; it has no {@code ItemTags} constant. */
	private static final TagKey<Item> C_MINING_TOOL =
			TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/mining_tool"));

	/**
	 * TC-ETOOL-001-FUN01 — every diamond-tipped upgrade sits in the same membership tags as the base tool
	 * it upgrades. Deleting a line from any of the five tag JSONs reddens this.
	 *
	 * <p>Mirrors: ElectricToolTagsGameTest.tcEtool001Fun01_tipMembershipTags
	 */
	public static void fun01TipMembershipTags(GameTestHelper helper) {
		Item drillTip = ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get();

		assertInTag(helper, drillTip, ItemTags.PICKAXES, "electric_drill_diamond_tip", "#minecraft:pickaxes");
		assertInTag(helper, ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get(), ItemTags.AXES,
				"electric_chainsaw_diamond_tip", "#minecraft:axes");
		assertInTag(helper, ModContent.ELECTRIC_HOE_DIAMOND_TIP.get(), ItemTags.HOES,
				"electric_hoe_diamond_tip", "#minecraft:hoes");

		// The drill's two extras — parity with the base drill, not optional polish (see the class javadoc).
		assertInTag(helper, drillTip, ItemTags.CLUSTER_MAX_HARVESTABLES, "electric_drill_diamond_tip",
				"#minecraft:cluster_max_harvestables");
		assertInTag(helper, drillTip, C_MINING_TOOL, "electric_drill_diamond_tip", "#c:tools/mining_tool");
		helper.succeed();
	}

	/**
	 * TC-ETOOL-001-FUN02 — the enchanting table's own filter ({@link Enchantment#canEnchant}) accepts each
	 * upgrade for the enchantments its base tool receives. This is the player-facing behaviour the tags
	 * restore; FUN01 pins the mechanism, this pins the outcome.
	 *
	 * <p><b>With a negative control per tool</b>, so an over-broad tag JSON is caught too:
	 * {@code protection} resolves through {@code #minecraft:enchantable/armor} and must reject all three.
	 * The obvious second choice — "a sharpness-style weapon enchantment must reject a tool" — is a trap
	 * here and was verified against the vanilla tag JSONs rather than assumed:
	 * {@code #minecraft:enchantable/sharp_weapon} <i>includes</i> {@code #minecraft:axes}, so sharpness
	 * legitimately accepts the chainsaw. {@code looting} ({@code enchantable/melee_weapon} = swords and
	 * spears) is used for the drill and the hoe instead.
	 *
	 * <p>Mirrors: ElectricToolTagsGameTest.tcEtool001Fun02_tipEnchantmentAccepted
	 */
	public static void fun02TipEnchantmentAccepted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> efficiency = enchantment(level, Enchantments.EFFICIENCY);
		Holder<Enchantment> unbreaking = enchantment(level, Enchantments.UNBREAKING);
		Holder<Enchantment> fortune = enchantment(level, Enchantments.FORTUNE);
		Holder<Enchantment> silkTouch = enchantment(level, Enchantments.SILK_TOUCH);
		Holder<Enchantment> mending = enchantment(level, Enchantments.MENDING);
		Holder<Enchantment> protection = enchantment(level, Enchantments.PROTECTION);
		Holder<Enchantment> looting = enchantment(level, Enchantments.LOOTING);

		ItemStack drillTip = new ItemStack(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get());
		ItemStack chainsawTip = new ItemStack(ModContent.ELECTRIC_CHAINSAW_DIAMOND_TIP.get());
		ItemStack hoeTip = new ItemStack(ModContent.ELECTRIC_HOE_DIAMOND_TIP.get());

		String[] names = {"electric_drill_diamond_tip", "electric_chainsaw_diamond_tip", "electric_hoe_diamond_tip"};
		ItemStack[] tips = {drillTip, chainsawTip, hoeTip};
		for (int i = 0; i < tips.length; i++) {
			ItemStack tip = tips[i];
			String name = names[i];
			assertCanEnchant(helper, efficiency, tip, "efficiency", name);
			assertCanEnchant(helper, unbreaking, tip, "unbreaking", name);
			assertCanEnchant(helper, fortune, tip, "fortune", name);
			assertCanEnchant(helper, mending, tip, "mending", name);
			assertCannotEnchant(helper, protection, tip, "protection", name,
					"an armour enchantment must never accept a tool — the tag JSON is too broad");
		}
		// Silk Touch is a mining-loot enchantment: correct for the drill, and the chainsaw upgrade ships a
		// Silk-Touch mode of its own, so both must be offerable.
		assertCanEnchant(helper, silkTouch, drillTip, "silk_touch", "electric_drill_diamond_tip");
		assertCanEnchant(helper, silkTouch, chainsawTip, "silk_touch", "electric_chainsaw_diamond_tip");

		// Looting is melee-only (#enchantable/melee_weapon = swords, spears) — a pickaxe and a hoe are not.
		assertCannotEnchant(helper, looting, drillTip, "looting", "electric_drill_diamond_tip",
				"a pickaxe is not a melee weapon");
		assertCannotEnchant(helper, looting, hoeTip, "looting", "electric_hoe_diamond_tip",
				"a hoe is not a melee weapon");
		helper.succeed();
	}

	private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** Assert {@code item}'s default stack is a member of {@code tag}; fail with a readable message otherwise. */
	private static void assertInTag(GameTestHelper helper, Item item, TagKey<Item> tag, String itemName,
			String tagName) {
		if (!new ItemStack(item).is(h -> h.is(tag))) {
			helper.fail(itemName + " is not in " + tagName + " (MOD-389 membership tag missing)");
		}
	}

	/** Assert {@code enchantment} accepts {@code stack}; fail with a readable message otherwise. */
	private static void assertCanEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack,
			String enchName, String itemName) {
		if (!enchantment.value().canEnchant(stack)) {
			helper.fail(enchName + " rejected " + itemName + " — the upgrade is not in the enchantment's "
					+ "supported_items (MOD-389)");
		}
	}

	/** Assert {@code enchantment} rejects {@code stack} — the negative half, guarding an over-broad tag. */
	private static void assertCannotEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack,
			String enchName, String itemName, String why) {
		if (enchantment.value().canEnchant(stack)) {
			helper.fail(enchName + " accepted " + itemName + " — " + why);
		}
	}
}
