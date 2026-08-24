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
 * Loader-neutral gametest bodies for what the electric tool line shares across items (suite
 * TC-ETOOL-001). Wrapped by the Fabric {@code ElectricToolTagsGameTest} suite and registered on the
 * NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders run the SAME checks.
 *
 * <p>Two generations of content live here. FUN01/FUN02 (MOD-389) cover the membership tags and
 * enchantability of the three <b>diamond-tipped upgrades</b>. FUN03/FUN04 (MOD-364) cover the same
 * ground for the <b>base</b> chainsaw, shovel and hoe, which no test had ever touched — and the shovel in
 * particular was absent from this suite entirely, because it is the one tool of the line with no
 * diamond-tipped upgrade to test. FUN05 (MOD-364) is a different kind of check: it audits the
 * {@link ElectricToolEnergyScenarios.ToolCase} table that eighteen parameterised EU tests are driven
 * from, which is the only thing standing between those tests and a silent copy-paste of the wrong tool.
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

	/**
	 * TC-ETOOL-001-FUN03 (MOD-364) — the three <b>base</b> tools carry their own membership tags:
	 * {@code electric_chainsaw} ∈ {@code #minecraft:axes}, {@code electric_shovel} ∈
	 * {@code #minecraft:shovels}, {@code electric_hoe} ∈ {@code #minecraft:hoes}.
	 *
	 * <p>FUN01 covers only the upgrades, and the shovel has none — so before this body, the shovel's tag
	 * membership was asserted by nothing at all, on either loader. That matters more than it sounds: the
	 * mechanism written out in this class's javadoc (an {@code Item.Properties.shovel()} attaches the
	 * {@code Tool} component and adds <i>nothing</i> to {@code #minecraft:shovels}) applies to base tools
	 * exactly as it does to upgrades, and MOD-057 is the proof that it bites.
	 *
	 * <p>Written as a sweep rather than three tests, following {@link TemperedIronToolScenarios}: the
	 * shape is identical per tool and a failure names the item it tripped over.
	 *
	 * <p>Mirrors: ElectricToolTagsGameTest.tcEtool001Fun03_baseMembershipTags
	 */
	public static void fun03BaseMembershipTags(GameTestHelper helper) {
		assertInTag(helper, ModContent.ELECTRIC_CHAINSAW.get(), ItemTags.AXES,
				"electric_chainsaw", "#minecraft:axes");
		assertInTag(helper, ModContent.ELECTRIC_SHOVEL.get(), ItemTags.SHOVELS,
				"electric_shovel", "#minecraft:shovels");
		assertInTag(helper, ModContent.ELECTRIC_HOE.get(), ItemTags.HOES,
				"electric_hoe", "#minecraft:hoes");
		helper.succeed();
	}

	/**
	 * TC-ETOOL-001-FUN04 (MOD-364) — the enchanting table's own filter accepts each base tool for the
	 * enchantments its vanilla counterpart receives, and rejects the ones from other domains.
	 *
	 * <p>FUN03 pins the mechanism (tag membership), this pins the outcome the player sees. The
	 * enchantment sets were read out of the vanilla tag JSONs rather than assumed:
	 * {@code efficiency} resolves through {@code #enchantable/mining} (axes, pickaxes, shovels, hoes,
	 * shears); {@code unbreaking} and {@code mending} through {@code #enchantable/durability};
	 * {@code fortune} and {@code silk_touch} through {@code #enchantable/mining_loot} — so all five apply
	 * to all three tools, including Silk Touch on a shovel, which is how a player collects snow layers.
	 *
	 * <p>The negatives are what stops an over-broad tag JSON from passing: {@code protection} resolves
	 * through {@code #enchantable/armor}, and {@code looting} through {@code #enchantable/melee_weapon},
	 * which is {@code #swords} + {@code #spears} only. Note the asymmetry with FUN02's comment — an axe is
	 * in {@code #enchantable/sharp_weapon} (so <i>sharpness</i> would accept the chainsaw) but not in
	 * {@code melee_weapon}, so {@code looting} must reject all three, chainsaw included.
	 *
	 * <p>Mirrors: ElectricToolTagsGameTest.tcEtool001Fun04_baseEnchantmentAccepted
	 */
	public static void fun04BaseEnchantmentAccepted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> efficiency = enchantment(level, Enchantments.EFFICIENCY);
		Holder<Enchantment> unbreaking = enchantment(level, Enchantments.UNBREAKING);
		Holder<Enchantment> mending = enchantment(level, Enchantments.MENDING);
		Holder<Enchantment> fortune = enchantment(level, Enchantments.FORTUNE);
		Holder<Enchantment> silkTouch = enchantment(level, Enchantments.SILK_TOUCH);
		Holder<Enchantment> protection = enchantment(level, Enchantments.PROTECTION);
		Holder<Enchantment> looting = enchantment(level, Enchantments.LOOTING);

		String[] names = {"electric_chainsaw", "electric_shovel", "electric_hoe"};
		ItemStack[] tools = {
				new ItemStack(ModContent.ELECTRIC_CHAINSAW.get()),
				new ItemStack(ModContent.ELECTRIC_SHOVEL.get()),
				new ItemStack(ModContent.ELECTRIC_HOE.get())};
		for (int i = 0; i < tools.length; i++) {
			ItemStack tool = tools[i];
			String name = names[i];
			assertCanEnchant(helper, efficiency, tool, "efficiency", name);
			assertCanEnchant(helper, unbreaking, tool, "unbreaking", name);
			assertCanEnchant(helper, mending, tool, "mending", name);
			assertCanEnchant(helper, fortune, tool, "fortune", name);
			assertCanEnchant(helper, silkTouch, tool, "silk_touch", name);
			assertCannotEnchant(helper, protection, tool, "protection", name,
					"an armour enchantment must never accept a tool — the tag JSON is too broad");
			assertCannotEnchant(helper, looting, tool, "looting", name,
					"#enchantable/melee_weapon is swords and spears only — no tool belongs there");
			if (!tool.isEnchantable()) {
				helper.fail(name + " must be enchantable at the table (ENCHANTABLE component present, "
						+ "unenchanted)");
			}
		}
		helper.succeed();
	}

	/**
	 * TC-ETOOL-001-FUN05 (MOD-364) — the parameter table behind the shared EU contract is audited against
	 * the real items. See {@link ElectricToolEnergyScenarios#energyCaseRosterIsHonest} for what it checks
	 * and why eighteen otherwise-green tests depend on it.
	 *
	 * <p>Mirrors: ElectricToolTagsGameTest.tcEtool001Fun05_energyCaseRosterIsHonest
	 */
	public static void fun05EnergyCaseRosterIsHonest(GameTestHelper helper) {
		ElectricToolEnergyScenarios.energyCaseRosterIsHonest(helper);
	}

	private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/**
	 * Assert {@code item}'s default stack is a member of {@code tag}; fail with a readable message
	 * otherwise. Shared by the upgrade cases (FUN01) and the base-tool cases (FUN03), so the message names
	 * the mechanism rather than either generation of item.
	 */
	private static void assertInTag(GameTestHelper helper, Item item, TagKey<Item> tag, String itemName,
			String tagName) {
		if (!new ItemStack(item).is(h -> h.is(tag))) {
			helper.fail(itemName + " is not in " + tagName + " — the membership tag is missing, so every "
					+ "enchantment resolving through that tag will refuse the item (MOD-057 / MOD-389)");
		}
	}

	/** Assert {@code enchantment} accepts {@code stack}; fail with a readable message otherwise. */
	// MOD-498 — Enchantment#canEnchant is deprecated by NeoForge only, not by vanilla. Its replacement,
	// ItemStack#supportsEnchantment(Holder), is added by the NeoForge patch and does not exist in the
	// vanilla class this shared scenario is also compiled against for Fabric, so the vanilla method is
	// the only call that works on both loaders.
	@SuppressWarnings("deprecation")
	private static void assertCanEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack,
			String enchName, String itemName) {
		if (!enchantment.value().canEnchant(stack)) {
			helper.fail(enchName + " rejected " + itemName + " — the item is not in that enchantment's "
					+ "supported_items, i.e. it is missing from the tag chain those items resolve through "
					+ "(MOD-057 / MOD-389)");
		}
	}

	/** Assert {@code enchantment} rejects {@code stack} — the negative half, guarding an over-broad tag. */
	// MOD-498 — same as the positive half above: NeoForge deprecates canEnchant in favour of its own
	// ItemStack#supportsEnchantment(Holder), which vanilla has no equivalent of, and this body is
	// compiled for Fabric too.
	@SuppressWarnings("deprecation")
	private static void assertCannotEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack,
			String enchName, String itemName, String why) {
		if (enchantment.value().canEnchant(stack)) {
			helper.fail(enchName + " accepted " + itemName + " — " + why);
		}
	}
}
