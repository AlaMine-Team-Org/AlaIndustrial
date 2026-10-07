package dev.alaindustrial.item.tool;

import dev.alaindustrial.compat.RightClickTransform;
import dev.alaindustrial.item.energy.ItemEnergy;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.energy.PoweredToolTooltip;

/**
 * Diamond-Tipped Electric Hoe (MOD-378) — the upgrade tier of the {@link ElectricHoeItem}, third in the
 * family after the {@link ElectricDrillDiamondTipItem} (MOD-321) and the
 * {@link ElectricChainsawDiamondTipItem} (MOD-374). Same deal as those two: a separate item, a faster
 * {@code TOOL} component, and one extra ability tied to what the tool is actually for.
 *
 * <p>Everything about energy is inherited untouched — {@link ItemEnergy} and the tooltip dispatcher both
 * branch on {@code instanceof ElectricHoeItem}, so the buffer, the input rate, the per-block drain and the
 * per-till drain all carry over with no change to the energy layer.
 *
 * <h2>Why the extra ability is irrigation and not Silk Touch</h2>
 * The other two upgrades both got a switchable Silk Touch mode, and copying it here would not pay off: in
 * {@code #minecraft:mineable/hoe} only the sculk family and leaves fail to drop themselves without Silk
 * Touch, and the ordinary enchantment already goes on the hoe ({@code #minecraft:hoes} is in
 * {@code #minecraft:enchantable/mining_loot}). A switchable mode for so few blocks is not worth it. What
 * the hoe alone has is <b>tilling</b>, so that is where the upgrade earns its keep.
 *
 * <h2>What "instant irrigation" is worth — the vanilla numbers behind it</h2>
 * Disassembled from the 26.2 mojmap jar before this class was written (project rule 1), because the value
 * of the perk is not obvious from the outside:
 * <ul>
 * <li>{@code FarmlandBlock}'s default state is {@code MOISTURE = 0} — freshly tilled farmland is <b>dry</b>.</li>
 * <li>{@code FarmlandBlock.randomTick}: with no water in range and no rain, moisture drops by one per
 * random tick; once it is already {@code 0} and nothing in {@code #minecraft:maintains_farmland} sits on
 * top, the block <b>reverts to dirt</b>.</li>
 * <li>{@code FarmlandBlock.isNearWater} scans a box from {@code (-4, 0, -4)} to {@code (+4, +1, +4)} for
 * {@code #minecraft:water}.</li>
 * </ul>
 * So in vanilla, tilling more than four blocks from water and not planting immediately is close to
 * pointless — the plot dries out and turns back into dirt. This upgrade hands the fresh plot a full
 * {@code MOISTURE = 7} instead, which both feeds vanilla's faster growth on moist soil and buys seven
 * random ticks before the plot can revert. It is a head start, not a sprinkler: the value decays on
 * exactly the vanilla schedule, and no new block, block entity or config key exists to keep it topped up.
 *
 * <h2>Two traps in {@link #useOn}, both load-bearing</h2>
 * <ul>
 * <li><b>{@code CONSUME} counts as a consumed action.</b> In 26.2 {@code InteractionResult.CONSUME} is an
 * {@code InteractionResult.Success}, whose {@code consumesAction()} returns {@code true} — and
 * {@link ElectricHoeItem#useOn} still returns exactly {@code CONSUME} on the "not enough charge" path.
 * Since MOD-389 that path is reached only for a block a hoe could actually convert, so the original
 * exploit route (a flat hoe clicking <i>existing farmland</i>, which is not tillable and now answers
 * {@code PASS}) is closed one layer earlier. The {@code alreadyFarmland} snapshot below stays as the guard
 * that does not depend on that ordering: the perk fires only on a block that was <i>not</i> farmland before
 * the call and <i>is</i> farmland after it, which is only true for a till that happened and got paid for.
 * Do not "simplify" it back to {@code consumesAction()} alone — that is one reordering away from free
 * irrigation again.</li>
 * <li><b>Not every tillable turns into farmland.</b> {@code HoeItem.TILLABLES} in 26.2 maps grass block,
 * dirt path and dirt to farmland, but coarse dirt and rooted dirt to plain <i>dirt</i>. The
 * {@code hasProperty} check covers that, and covers modded farmland that reuses the vanilla property —
 * the same idiom {@code TrellisBlock} already uses to read soil moisture.</li>
 * </ul>
 */
public class ElectricHoeDiamondTipItem extends ElectricHoeItem {

	/** Speed on {@code #minecraft:mineable/hoe} — the base hoe's 9.0 plus the same {@code +1.5} step the
	 * drill (8.5 → 10.0) and the chainsaw (9.0 → 10.5) upgrades took. Mining tier is untouched: the
	 * upgrade digs faster, it does not reach anything new. */
	private static final float MINING_SPEED = 10.5f;
	/** Enchantability — the diamond value, same as the base hoe. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers, inherited verbatim from the base hoe: a hoe is the one vanilla tool that is not a
	 * weapon, and the upgrade does not change that. No {@code WEAPON} component either. */
	private static final double ATTACK_DAMAGE = 0.0;
	private static final double ATTACK_SPEED = 0.0;

	public ElectricHoeDiamondTipItem(Properties properties) {
		super(properties);
	}

	/**
	 * The upgrade's item properties. This is a full copy of
	 * {@link ElectricHoeItem#electricHoeProperties} rather than a call into it, for the same reason both
	 * sibling upgrades copy theirs: {@code Tool} is an immutable record, so "build the base component,
	 * then rebuild it with one different number" would be longer than this and would hide the one value
	 * that actually differs. The single difference is {@link #MINING_SPEED}.
	 *
	 * <p>Rule order is load-bearing and matches the base hoe: {@code deniesDrops} on the diamond deny-tag
	 * first, {@code minesAndDrops} on {@code #mineable/hoe} second — the first matching rule wins.
	 *
	 * <p>The {@link RightClickTransform#HOE} declaration has to be repeated here for the same reason the
	 * rest of this method is a copy: the upgrade builds its own {@code Properties} and inherits nothing from
	 * the base hoe's factory. Leaving it out can give the upgrade a {@code useOn} that reaches the
	 * irrigation code and never tills (MOD-226) — the exact shape of the defect this replaced, one tier up.
	 */
	public static Properties electricHoeDiamondTipProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return RightClickTransform.HOE.declare(props.stacksTo(1))
				.component(DataComponents.TOOL, new Tool(
						List.of(
								Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), MINING_SPEED)),
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

	// --- right-click: the base hoe's powered tilling, plus the plot comes out already watered ---

	/**
	 * Runs the base hoe's tilling — charge gate, delegation to the vanilla diamond hoe, EU spend, all
	 * unchanged — and then hands the freshly created plot a full {@link FarmlandBlock#MAX_MOISTURE}.
	 *
	 * <p>The {@code alreadyFarmland} snapshot is the guard that makes this correct rather than exploitable;
	 * see the class javadoc for why {@code result.consumesAction()} on its own is not enough. The write is
	 * server-side only: {@code useOn} runs on both sides, but the vanilla consumer that swaps the block in
	 * only runs on the server, and clients pick the new state up from the block update.
	 *
	 * <p>Flag {@code 2} on {@code setBlock} mirrors what {@code FarmlandBlock.randomTick} itself uses when
	 * it changes this exact property — clients are told, no redundant neighbour churn.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		boolean alreadyFarmland = level.getBlockState(pos).hasProperty(FarmlandBlock.MOISTURE);

		InteractionResult result = super.useOn(context);

		if (!alreadyFarmland && !level.isClientSide() && result.consumesAction()) {
			BlockState tilled = level.getBlockState(pos);
			if (tilled.hasProperty(FarmlandBlock.MOISTURE)) {
				level.setBlock(pos, tilled.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 2);
			}
		}
		return result;
	}

	/** The base tooltip, then the irrigation perk. */
	@Override
	public PoweredToolTooltip toolTooltip() {
		// MOD-378: the tipped hoe waters every plot it tills; nothing in the vanilla UI hints at it, so the
		// tooltip is the only place a player learns the upgrade does more than dig faster.
		return super.toolTooltip().withAfterCharge(stack -> MachineTooltipSpec.text(
				"tooltip.alaindustrial.electric_hoe_diamond_tip.irrigation", MachineTooltipSpec.Tone.AQUA));
	}
}
