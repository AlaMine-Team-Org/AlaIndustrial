package dev.alaindustrial.item.tool;

import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

/**
 * Netherite-Tipped Electric Drill (MOD-534) — the third and last tier of the drill line: the same
 * EU-powered, never-breaking diamond-tier pickaxe as {@link ElectricDrillDiamondTipItem}, but it digs
 * faster, hits harder and carries half again as much charge.
 *
 * <p>It extends the diamond tip rather than the base drill, so the switchable Silk Touch mode
 * (MOD-321) and its sneak + right-click control are inherited whole — including the {@code useOn}
 * override that keeps a plain right-click on torch placement. The only thing the subclass adds on top
 * of new numbers is its own message/tooltip key prefix (see {@link #messageKeyPrefix}), so the action
 * bar and the tooltip name this tier rather than the one below it.
 *
 * <h2>What actually differs from the tier below</h2>
 * <ul>
 * <li><b>Mining speed 12.0</b> against the diamond tip's 10.0 (and a vanilla netherite pickaxe's 9.0).
 * The mining <b>tier is unchanged</b> — still diamond, the same {@code TOOL} rules as both tiers below.
 * Vanilla has no mining tier above diamond (26.2's {@code #incorrect_for_diamond_tool} is empty), so
 * "netherite" here is about how fast the work goes, not about unlocking blocks: exactly the relationship
 * a vanilla netherite pickaxe has to a diamond one.</li>
 * <li><b>Attack damage 7</b> (modifier 6.0 + the player's 1.0 base) against 6 on both tiers below —
 * matching the step vanilla gives netherite over diamond. Attack speed is untouched, and attacking
 * still spends no EU.</li>
 * <li><b>A 15 000 EU buffer</b> ({@code Config.electricDrillNetheriteTipBuffer}) against the 10 000 the
 * first two tiers share. This is the one difference that reaches outside the class: {@code ItemEnergy}
 * dispatches capacity on {@code instanceof}, so its branch for this class has to sit <b>before</b> the
 * {@code ElectricDrillItem} branch it would otherwise be swallowed by.</li>
 * </ul>
 *
 * <h2>What is deliberately NOT changed</h2>
 * The per-block cost stays at {@code Config.electricDrillEuPerBlock} (50) and the intake stays at the LV
 * ceiling ({@code Config.electricDrillInputRate}, 32) — inherited through the same {@code instanceof}
 * chain with no branch of their own. So the upgrade buys ~300 blocks per charge instead of ~200 without
 * charging any more per block: the player never pays more EU for the same work, and the cost of the tier
 * sits in its recipe (netherite) rather than in a running penalty. Torch placement (MOD-089) and its
 * 5 EU keep working unchanged, and the tool still never breaks.
 */
public class ElectricDrillNetheriteTipItem extends ElectricDrillDiamondTipItem {

	/** Mining speed on {@code #minecraft:mineable/pickaxe} — above the diamond tip's 10.0 and the base
	 * drill's 8.5, while the mining tier stays diamond on all three. Same intent as the tier below: the
	 * upgrade makes the work faster, it does not unlock blocks the drill could not already break. */
	private static final float MINING_SPEED = 12.0f;
	/** Enchantability — the diamond value, unchanged across the whole drill line. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — one above the two tiers below: +6.0 damage modifier → 7 displayed (1 player base
	 * + 6), mirroring vanilla's netherite-over-diamond step. Attack speed is the line's usual -2.7, and
	 * there is still no {@code WEAPON} component: attacking neither drains EU nor wears the drill. */
	private static final double ATTACK_DAMAGE = 6.0;
	private static final double ATTACK_SPEED = -2.7;

	public ElectricDrillNetheriteTipItem(Properties properties) {
		super(properties);
	}

	/**
	 * This tier's item properties, applied identically by both loaders. Deliberately a full copy of
	 * {@link ElectricDrillDiamondTipItem#electricDrillDiamondTipProperties} rather than a call plus an
	 * override, for the reason stated there: the {@code TOOL} component is a single immutable record, so
	 * changing the mining speed rebuilds the whole component anyway, and a "build then replace" version
	 * would be longer while hiding which values actually differ. Here two differ — {@link #MINING_SPEED}
	 * and {@link #ATTACK_DAMAGE}; everything else (rule order, {@code damagePerBlock = 0}, explicit
	 * {@code stacksTo(1)}) is the base drill's reasoning verbatim; see its javadoc for why.
	 */
	public static Properties electricDrillNetheriteTipProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
				.component(DataComponents.TOOL, new Tool(
						List.of(
								Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), MINING_SPEED)),
						1.0f, /*damagePerBlock*/ 0, /*canDestroyBlocksInCreative*/ true))
				.enchantable(ENCHANT_VALUE)
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE,
								new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED,
								new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.build());
	}

	/**
	 * Own key prefix, so the Silk Touch action-bar line and the tooltip read as this tier rather than the
	 * diamond one whose {@code use()} is inherited. Every tip item in the mod keeps its own pair of keys
	 * even where the wording matches, so the text can diverge later without a code change.
	 */
	@Override
	protected String messageKeyPrefix() {
		return "item.alaindustrial.electric_drill_netherite_tip";
	}
}
