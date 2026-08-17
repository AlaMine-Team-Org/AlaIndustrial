package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * L2 server game tests for tempered-iron hand tools (MOD-057) — tag membership + enchantability.
 *
 * <p><b>Root cause these tests guard:</b> {@code Item.Properties.{pickaxe,axe,hoe,shovel,sword}()}
 * in MC 26.2 attach only the {@code Tool}/{@code Weapon} data-component and do NOT add the item to
 * the vanilla membership tags {@code #minecraft:{pickaxes,axes,hoes,shovels,swords}}. Each
 * enchantment's {@code supported_items} resolves through the enchantable tag chain (e.g.
 * {@code efficiency} → {@code #minecraft:enchantable/mining} → {@code #minecraft:pickaxes}), so
 * without membership {@link Enchantment#canEnchant} returns false and the enchanting table offers
 * no enchantments — even though {@code isEnchantable()} is already true (the {@code ENCHANTABLE}
 * component is set by {@code ToolMaterial.applyCommonProperties}). The fix is five
 * {@code data/minecraft/tags/item/*.json} files with {@code "replace": false}; these tests pin that
 * the membership (and therefore enchantability) holds, catching any future regression that drops
 * the tag JSON.
 *
 * <p>Bodies live in {@link TemperedIronToolScenarios} (API notes there); the NeoForge lane registers the
 * same bodies in {@code NeoForgeGameTests}, and this class is the Fabric wiring (MOD-445 removed the
 * inline duplicates).
 */
public class TemperedIronToolsGameTest {

	/**
	 * TC-TI-001 (membership): each tempered-iron tool belongs to its vanilla tool-type membership tag.
	 * A dropped {@code data/minecraft/tags/item/<tool>.json} fails here. Asserts the positive mapping
	 * (pickaxe → {@link ItemTags#PICKAXES}, etc.) for all five tools in one pass.
	 *
	 * @implements TC-TI-001
	 * @covers MOD-057 (tag membership regression gate)
	 */
	@GameTest
	public void tcTi001_toolMembershipTags(GameTestHelper helper) {
		TemperedIronToolScenarios.toolMembershipTags(helper);
	}

	/**
	 * TC-TI-002 (enchantability): the enchanting table's filter ({@link Enchantment#canEnchant})
	 * accepts each tempered-iron tool for the enchantments its vanilla counterpart receives. This is
	 * the user-facing behavior the tag fix restores: a pickaxe gets {@code efficiency}/
	 * {@code unbreaking}/{@code fortune}/{@code silk_touch}/{@code mending}, a sword gets
	 * {@code sharpness}/{@code unbreaking}/{@code looting}. Also asserts a negative — the sword must
	 * NOT be accepted by {@code fortune} (sword is not in {@code #minecraft:enchantable/mining}) — so
	 * an over-broad tag JSON is caught too.
	 *
	 * @implements TC-TI-002
	 * @covers MOD-057 (enchantability regression gate)
	 */
	@GameTest
	public void tcTi002_enchantmentAccepted(GameTestHelper helper) {
		TemperedIronToolScenarios.enchantmentAccepted(helper);
	}
}
