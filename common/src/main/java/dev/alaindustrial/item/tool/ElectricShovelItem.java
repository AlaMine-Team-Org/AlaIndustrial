package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric Shovel (MOD-338) — the earth-side member of the EU hand-tool line, after the
 * {@link ElectricDrillItem} (stone) and the {@link ElectricChainsawItem} (wood). Same contract as its
 * two siblings: it runs on EU instead of durability, never breaks, and when it runs flat it degrades
 * to hand speed with every drop intact rather than becoming dead weight.
 *
 * <p>Energy is not re-implemented (project rule 4): the charge lives in the shared
 * {@code pouch_energy} component through {@link ItemEnergy}, which gains one {@code capacity} /
 * {@code inputRate} branch for this class — so the Battery Box charge slot, a worn Energy Pack and the
 * Charge Pad all charge it with no changes on their side.
 *
 * <h2>Why a hand-built {@code TOOL} component, and not {@code extends ShovelItem}</h2>
 * {@link net.minecraft.world.item.ShovelItem} still exists in 26.2, but it cannot be extended here for
 * the same reason {@code AxeItem} could not be extended by the chainsaw. Its constructor was
 * disassembled before this class was written (project rule 1) and reads
 * {@code super(props.shovel(material, damage, speed))} — it applies {@code Properties.shovel(...)}
 * <i>inside</i> the constructor, after any factory the caller passed has already run. That call routes
 * through {@code ToolMaterial.applyToolProperties}, which sets {@code MAX_DAMAGE}, making the item
 * damageable and handing the item bar to vanilla durability — exactly the "EU only, never breaks"
 * model this tool line is built on. So the {@code TOOL} component is assembled here by hand, mirroring
 * what {@code applyToolProperties} would have produced for a diamond shovel, minus the durability.
 *
 * <h2>Path-making and campfire dousing — kept, and on 26.3 they are two different mechanisms</h2>
 * The chainsaw had to give up log stripping because {@code AxeItem.STRIPPABLES} was
 * {@code protected static}. The shovel never had to give up the equivalent, but on 26.3 it keeps it a
 * different way (MOD-226), and the two halves split apart:
 * <ul>
 * <li><b>The dirt path is a data component.</b> {@code ShovelItem} is gone and {@code Item.useOn} reads
 * {@code minecraft:block_transformer} off the <i>stack in hand</i>, so the old
 * {@code Items.DIAMOND_SHOVEL.useOn(context)} delegation found our (absent) component and answered
 * {@code PASS} — a silent no-op. {@link #electricShovelProperties} now declares
 * {@link BlockTransformers#SHOVEL}, exactly as {@code Properties.shovel(...)} does for a vanilla shovel,
 * and {@link #useOn} calls {@code super.useOn}.</li>
 * <li><b>Dousing is a tag, and it never went through this method at all.</b>
 * {@code CampfireBlock.useItemOn} tests {@code itemStack.is(ItemTags.DOUSES_CAMPFIRES)}, and the block's
 * interaction runs before the item's ({@code ServerPlayerGameMode.useItemOn}). Vanilla defines that tag
 * as {@code #minecraft:shovels}, which this item is already in, so dousing needs no code here on either
 * loader — NeoForge's {@code IItemExtension.canPerformAction} answers {@code SHOVEL_DOUSE} from the very
 * same tag.</li>
 * </ul>
 * The transform still cannot wear this tool out: for a non-stackable stack {@code transformBlock} ends in
 * {@code hurtAndBreak(item_damage_per_use, …)}, which routes through {@code processDurabilityChange},
 * whose first act is {@code if (!isDamageableItem()) return 0;} — a no-op for a tool with no
 * {@code MAX_DAMAGE}, which is precisely what this item is.
 *
 * <p>The interaction is deliberately <b>free</b> — it is not gated on EU the way the drill's torch
 * placement is. Making a path is something a wooden shovel does; charging for it would contradict the
 * "a flat tool still works, just slowly" rule that defines this whole line, and would leave a
 * discharged electric shovel strictly worse than a stick with a plank on it.
 *
 * <h2>EU behaviour — identical in shape to the drill and the chainsaw</h2>
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed while the shovel holds at least one block's
 * worth of EU; otherwise exactly {@code 1.0f}. That value is not an approximation —
 * {@code Player.getDestroySpeed} only applies Efficiency when the tool reports {@code > 1.0F}, so a
 * flat shovel is a plain hand and the enchantment cannot revive it.</li>
 * <li>{@link #mineBlock}: drains {@link Config#electricShovelEuPerBlock} per block actually dug,
 * server-side only, only for blocks with non-zero hardness (so genuinely instant-break blocks cost
 * nothing, just as they never wear a vanilla shovel — <b>a snow layer is not one of them</b>, see
 * {@link #mineBlock}), and only when there was enough EU to run at tool speed in the first place.
 * Creative is dropped inside {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 */
public class ElectricShovelItem extends Item {

	/** Digging speed on {@code #minecraft:mineable/shovel} — above a vanilla diamond shovel (8.0), because
	 * the tool is crafted around one and should out-dig the shovel that goes into it. Matches the
	 * chainsaw's 9.0, so the two siblings feel like one product line. */
	private static final float MINING_SPEED = 9.0f;
	/** Enchantability — the diamond value ({@code ToolMaterial.DIAMOND.enchantmentValue}), matching the
	 * drill and the chainsaw. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — a vanilla diamond shovel's: +1.5 damage → 2.5 displayed, -3.0 speed → 1.0
	 * displayed. No {@code WEAPON} component: attacking neither drains EU nor wears the shovel. */
	private static final double ATTACK_DAMAGE = 1.5;
	private static final double ATTACK_SPEED = -3.0;

	public ElectricShovelItem(Properties properties) {
		super(properties);
	}

	/**
	 * The shovel's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — the only difference).
	 *
	 * <p>Two {@code TOOL} rules, which is what {@code Properties.shovel(ToolMaterial.DIAMOND, …)} would
	 * have produced: {@code deniesDrops} on the diamond deny-tag first (rule order matters — the first
	 * matching rule wins), then {@code minesAndDrops} on {@code #mineable/shovel}. Unlike the chainsaw,
	 * no third rule is needed: everything this tool is for — dirt, sand, gravel, clay, snow, soul sand,
	 * concrete powder — already lives under that one vanilla tag.
	 *
	 * <p>{@code damagePerBlock = 0} means {@code super.mineBlock} never calls {@code hurtAndBreak} —
	 * there is no durability to spend. {@code stacksTo(1)} is set explicitly because we skip
	 * {@code durability(...)}, which is where a vanilla tool's max-stack-size of 1 normally comes from.
	 *
	 * <p>{@code delayedHolderComponent(BLOCK_TRANSFORMER, SHOVEL)} is the second half of
	 * {@code Properties.shovel(...)} — the half that carries path-making, which on 26.3 is a data
	 * component and not a class (MOD-226; see the class javadoc). It has to be <i>delayed</i> because
	 * {@code block_transformer} is a datapack registry: no {@code Holder} for it exists while items are
	 * being registered. Because it lands in the item's DEFAULT component map, stacks saved by 26.2 worlds
	 * pick it up on load without a datafixer — an {@code ItemStack} is always rebuilt as
	 * {@code item.components()} plus the stack's own patch.
	 */
	public static Properties electricShovelProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
				.delayedHolderComponent(DataComponents.BLOCK_TRANSFORMER, BlockTransformers.SHOVEL)
				.component(DataComponents.TOOL, new Tool(
						List.of(
								Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), MINING_SPEED)),
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

	// --- right-click: vanilla shovel interactions (dirt path, campfire dousing), free of charge ---

	/**
	 * Runs the shovel block transformer this item declares, turning grass/dirt/podzol/mycelium/rooted dirt
	 * into a dirt path. {@code super.useOn} is {@code Item.useOn}, which on 26.3 <i>is</i> the transformer
	 * runner; see the class javadoc for why the old call into {@code Items.DIAMOND_SHOVEL} silently
	 * stopped making paths (MOD-226).
	 *
	 * <p>Campfire dousing — the shovel's other advertised right-click — does not pass through here at all
	 * on 26.3: {@code CampfireBlock.useItemOn} decides it from the {@code #minecraft:douses_campfires}
	 * item tag before the item's {@code useOn} is ever reached. See the class javadoc.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		return super.useOn(context);
	}

	// --- digging: full speed while charged, hand speed when flat (drops kept either way) ---

	/**
	 * Returns exactly {@code 1.0f} when the shovel cannot afford a block — see the class javadoc for why
	 * the value must not be "slightly above 1.0". The mining tier and the drops still come from the
	 * {@code TOOL} component either way, so a flat shovel keeps every block's drop; it is just slow.
	 */
	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (ItemEnergy.get(stack) >= Config.electricShovelEuPerBlock) {
			return super.getDestroySpeed(stack, state);
		}
		return 1.0f;
	}

	/**
	 * Drains EU for the block just dug. The two guards mirror vanilla's durability gate in
	 * {@code Item.mineBlock}: {@code !isClientSide} because {@code mineBlock} runs on both sides and the
	 * charge must only move on the server (the client picks the new value up from the synced
	 * {@code pouch_energy} component), and non-zero hardness so instant-break blocks cost nothing.
	 *
	 * <p><b>Snow layers are not among them</b> (MOD-389 — the earlier wording here said they were):
	 * {@code Blocks.SNOW} is {@code .strength(0.1F)} in the 26.2 sources, so every layer costs the full
	 * per-block drain. Short grass ({@code .instabreak()}, {@code 0.0}) is genuinely free, but it is not a
	 * shovel block either. The gate reads {@code getDestroySpeed}, so the only free blocks are the ones with
	 * hardness exactly {@code 0.0}.
	 */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0f
				&& ItemEnergy.get(stack) >= Config.electricShovelEuPerBlock) {
			ItemEnergy.spend(stack, Config.electricShovelEuPerBlock, owner);
		}
		return super.mineBlock(stack, level, state, pos, owner);
	}

	// --- item bar shows the EU charge in the LV tier colour (numbers are in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		long capacity = ItemEnergy.capacity(stack);
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH, MAX_BAR_WIDTH * ItemEnergy.get(stack) / capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyTier.LV.color();
	}
}
