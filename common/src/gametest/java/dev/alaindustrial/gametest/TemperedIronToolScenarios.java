package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Loader-neutral world-based gametest bodies for tempered-iron hand tools (MOD-057). Each scenario is
 * a plain {@code Consumer<GameTestHelper>} using only vanilla {@code GameTestHelper} + loader-neutral
 * content ({@link ModContent}) — no loader-specific gametest infrastructure. Both the Fabric
 * {@code @GameTest} suite ({@code TemperedIronToolsGameTest}) and the NeoForge {@code gameTestServer}
 * lane (via {@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests}) exercise the SAME checks.
 *
 * <p><b>What these tests guard (MOD-057 root cause):</b> {@code Item.Properties.{pickaxe,axe,hoe,
 * shovel,sword}()} in MC 26.2 attach only the {@code Tool}/{@code Weapon} data-component and do NOT
 * add the item to the vanilla membership tags {@code #minecraft:{pickaxes,axes,hoes,shovels,swords}}.
 * Each enchantment's {@code supported_items} resolves through the enchantable tag chain
 * (e.g. {@code efficiency} → {@code #minecraft:enchantable/mining} → {@code #minecraft:pickaxes}),
 * so without membership {@link Enchantment#canEnchant} returns false and the enchanting table offers
 * no enchantments. The fix is five {@code data/minecraft/tags/item/*.json} files with
 * {@code "replace": false}; these tests pin that the membership (and therefore enchantability) holds,
 * catching any future regression that drops the tag JSON.
 *
 * <p>API symbols verified against {@code minecraft-common-deobf-26.2.jar} via {@code javap}:
 * {@link ItemStack#is(java.util.function.Predicate)} takes {@code Predicate<Holder<Item>>};
 * {@link Holder#is(net.minecraft.tags.TagKey)} yields the predicate;
 * {@link Enchantment#canEnchant(ItemStack)} checks {@code definition.supportedItems().contains(stack.typeHolder())}.
 */
public final class TemperedIronToolScenarios {

	private TemperedIronToolScenarios() {}

	/**
	 * MOD-057 membership: each tempered-iron tool belongs to its vanilla tool-type membership tag.
	 * A dropped {@code data/minecraft/tags/item/<tool>.json} fails here. Asserts the positive mapping
	 * (pickaxe → {@link ItemTags#PICKAXES}, etc.) for all five tools in one pass.
	 *
	 * <p>Mirrors: TemperedIronToolsGameTest.tcTi001_toolMembershipTags
	 */
	public static void toolMembershipTags(GameTestHelper helper) {
		assertInTag(helper, ModContent.TEMPERED_IRON_PICKAXE.get(), ItemTags.PICKAXES, "tempered_iron_pickaxe", "#minecraft:pickaxes");
		assertInTag(helper, ModContent.TEMPERED_IRON_AXE.get(),     ItemTags.AXES,     "tempered_iron_axe",     "#minecraft:axes");
		assertInTag(helper, ModContent.TEMPERED_IRON_HOE.get(),     ItemTags.HOES,     "tempered_iron_hoe",     "#minecraft:hoes");
		assertInTag(helper, ModContent.TEMPERED_IRON_SHOVEL.get(),  ItemTags.SHOVELS,  "tempered_iron_shovel",  "#minecraft:shovels");
		assertInTag(helper, ModContent.TEMPERED_IRON_SWORD.get(),   ItemTags.SWORDS,   "tempered_iron_sword",   "#minecraft:swords");
		helper.succeed();
	}

	/**
	 * MOD-057 enchantability: the enchanting table's filter ({@link Enchantment#canEnchant}) accepts
	 * each tempered-iron tool for the enchantments its vanilla counterpart receives. This is the
	 * user-facing behavior the tag fix restores: a pickaxe gets {@code efficiency}/{@code unbreaking}/
	 * {@code fortune}/{@code silk_touch}/{@code mending}, a sword gets {@code sharpness}/
	 * {@code unbreaking}/{@code looting}. Also asserts a negative — the sword must NOT be accepted by
	 * {@code fortune} (sword is not in {@code #minecraft:enchantable/mining}) — so an over-broad tag
	 * JSON is caught too.
	 *
	 * <p>Mirrors: TemperedIronToolsGameTest.tcTi002_enchantmentAccepted
	 */
	public static void enchantmentAccepted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
		Holder<Enchantment> unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
		Holder<Enchantment> fortune    = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
		Holder<Enchantment> silkTouch  = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		Holder<Enchantment> mending    = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
		Holder<Enchantment> sharpness  = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
		Holder<Enchantment> looting    = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);

		ItemStack pickaxe = new ItemStack(ModContent.TEMPERED_IRON_PICKAXE.get());
		ItemStack sword   = new ItemStack(ModContent.TEMPERED_IRON_SWORD.get());

		// Pickaxe: mining + durability enchantments must be accepted.
		assertCanEnchant(helper, efficiency, pickaxe, "efficiency", "tempered_iron_pickaxe");
		assertCanEnchant(helper, unbreaking, pickaxe, "unbreaking", "tempered_iron_pickaxe");
		assertCanEnchant(helper, fortune,    pickaxe, "fortune",    "tempered_iron_pickaxe");
		assertCanEnchant(helper, silkTouch,  pickaxe, "silk_touch", "tempered_iron_pickaxe");
		assertCanEnchant(helper, mending,    pickaxe, "mending",    "tempered_iron_pickaxe");

		// Sword: weapon + durability enchantments must be accepted.
		assertCanEnchant(helper, sharpness, sword, "sharpness", "tempered_iron_sword");
		assertCanEnchant(helper, unbreaking, sword, "unbreaking", "tempered_iron_sword");
		assertCanEnchant(helper, looting,   sword, "looting",   "tempered_iron_sword");
		assertCanEnchant(helper, mending,   sword, "mending",   "tempered_iron_sword");

		// Negative: a sword is NOT a mining tool — fortune must reject it (guards over-broad tags).
		if (fortune.value().canEnchant(sword)) {
			helper.fail("fortune accepted tempered_iron_sword — sword must not be in #minecraft:enchantable/mining");
		}
		helper.succeed();
	}

	/** Assert {@code item}'s default stack is a member of {@code tag}; fail with a readable message otherwise. */
	private static void assertInTag(GameTestHelper helper, Item item, net.minecraft.tags.TagKey<Item> tag,
			String itemName, String tagName) {
		ItemStack stack = new ItemStack(item);
		if (!stack.is(h -> h.is(tag))) {
			helper.fail(itemName + " is not in " + tagName + " (MOD-057 membership tag missing)");
		}
	}

	/** Assert {@code enchantment} accepts {@code stack}; fail with a readable message otherwise. */
	private static void assertCanEnchant(GameTestHelper helper, Holder<Enchantment> enchantment, ItemStack stack,
			String enchName, String itemName) {
		if (!enchantment.value().canEnchant(stack)) {
			helper.fail(enchName + " rejected " + itemName + " — tool not in the enchantment's supported_items (MOD-057)");
		}
	}
}
