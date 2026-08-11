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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric Chainsaw (MOD-337) — the wood-side counterpart of the {@link ElectricDrillItem}: an
 * EU-powered, never-breaking axe that tears through logs and leaves instead of stone. It was
 * reserved in {@code docs/FUTURE_CONTENT.md} as {@code alaindustrial:electric_chainsaw} from the
 * day the drill shipped ("other electric tools (chainsaw/hoe/saber) are future work").
 *
 * <p>Everything energy-related reuses the drill's machinery rather than inventing a parallel one
 * (project rule 4): charge lives in the shared {@code pouch_energy} component through
 * {@link ItemEnergy}, which gains one {@code capacity}/{@code inputRate} branch for this class, so
 * every existing charger — the Battery Box charge slot, a worn Energy Pack, the Charge Pad —
 * charges the chainsaw with no changes on their side.
 *
 * <h2>Why a hand-built {@code TOOL} component, and not {@code extends AxeItem}</h2>
 * Unlike {@code PickaxeItem}, {@link net.minecraft.world.item.AxeItem} <i>does</i> still exist in
 * 26.2 and carries log stripping in its {@code useOn} — extending it would inherit that for free.
 * It cannot be used here: its constructor was disassembled before this class was written
 * (project rule 1) and reads
 * {@code super(props.axe(material, damage, speed))} — i.e. it applies {@code Properties.axe(...)}
 * <i>inside</i> the constructor, after any factory the caller passed has already run. That call
 * routes through {@code ToolMaterial.applyToolProperties}, which sets {@code MAX_DAMAGE}, making the
 * item damageable and handing the item bar to vanilla durability — exactly the "EU only, never
 * breaks" model this tool line is built on, and the same reason
 * {@link ElectricDrillItem#electricDrillProperties} assembles its component by hand. So the
 * {@code TOOL} component is built here too, mirroring what {@code applyToolProperties} would have
 * produced for a diamond axe, minus the durability.
 *
 * <p>The trade-off is deliberate and recorded in the task: <b>this tool does not strip logs</b>,
 * because the stripping map ({@code AxeItem.STRIPPABLES}) is {@code protected static} and reachable
 * only from a subclass. Re-implementing it would mean hardcoding a vanilla wood table that modded
 * woods would fall out of. Keeping the durability-free core matters more than the convenience.
 *
 * <h2>EU behaviour — identical in shape to the drill</h2>
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed while the chainsaw holds at least one
 * block's worth of EU; otherwise exactly {@code 1.0f}. That value is not an approximation —
 * {@code Player.getDestroySpeed} only applies Efficiency when the tool reports {@code > 1.0F}, so a
 * flat chainsaw is a plain hand and the enchantment cannot revive it.</li>
 * <li>{@link #mineBlock}: drains {@link Config#electricChainsawEuPerBlock} per block actually
 * mined, server-side only, only for blocks with non-zero hardness (so instant-break saplings are free,
 * just as they never wear a vanilla axe — <b>leaves are not</b>, see {@link #mineBlock}), and only when
 * there was enough EU to run at tool speed in the first place. Creative is dropped inside
 * {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 */
public class ElectricChainsawItem extends Item {

	/** Mining speed on {@code #minecraft:mineable/axe} — above a vanilla diamond axe (8.0), because the
	 * chainsaw is crafted around one and should out-cut the tool that goes into it. The same speed is
	 * applied to {@code #minecraft:leaves} (see the third rule) so clearing a canopy is not the slow
	 * half of felling a tree. */
	private static final float MINING_SPEED = 9.0f;
	/** Enchantability — the diamond value ({@code ToolMaterial.DIAMOND.enchantmentValue}), matching the drill. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — a vanilla diamond axe's: +5.0 damage → 6 displayed, -3.0 speed → 1.0 displayed.
	 * No {@code WEAPON} component: attacking neither drains EU nor wears the chainsaw. */
	private static final double ATTACK_DAMAGE = 5.0;
	private static final double ATTACK_SPEED = -3.0;

	public ElectricChainsawItem(Properties properties) {
		super(properties);
	}

	/**
	 * The chainsaw's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — the only difference).
	 *
	 * <p>Three {@code TOOL} rules. The first two are what {@code Properties.axe(ToolMaterial.DIAMOND,
	 * …)} would have produced: {@code deniesDrops} on the diamond deny-tag first (rule order matters —
	 * the first matching rule wins), then {@code minesAndDrops} on {@code #mineable/axe}. The third is
	 * this tool's own addition: {@code #minecraft:leaves} at the same speed, so the chainsaw is an axe
	 * for "wood <i>and</i> foliage" as the item was scoped in {@code FUTURE_CONTENT.md}. Vanilla puts
	 * leaves under {@code #mineable/hoe}, so without this rule a chainsaw would crawl through a canopy.
	 *
	 * <p>{@code damagePerBlock = 0} means {@code super.mineBlock} never calls {@code hurtAndBreak} —
	 * there is no durability to spend. {@code stacksTo(1)} is set explicitly because we skip
	 * {@code durability(...)}, which is where a vanilla tool's max-stack-size of 1 normally comes from.
	 */
	public static Properties electricChainsawProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
				.component(DataComponents.TOOL, new Tool(
						List.of(
								Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), MINING_SPEED),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.LEAVES), MINING_SPEED)),
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

	// --- mining: full speed while charged, hand speed when flat (drops kept either way) ---

	/**
	 * Returns exactly {@code 1.0f} when the chainsaw cannot afford a block — see the class javadoc for
	 * why the value must not be "slightly above 1.0". The mining tier and the drops still come from the
	 * {@code TOOL} component either way, so a flat chainsaw keeps every log's drop; it is just slow.
	 */
	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (ItemEnergy.get(stack) >= Config.electricChainsawEuPerBlock) {
			return super.getDestroySpeed(stack, state);
		}
		return 1.0f;
	}

	/**
	 * Drains EU for the block just broken. The two guards mirror vanilla's durability gate in
	 * {@code Item.mineBlock}: {@code !isClientSide} because {@code mineBlock} runs on both sides and the
	 * charge must only move on the server (the client picks the new value up from the synced
	 * {@code pouch_energy} component), and non-zero hardness so instant-break blocks cost nothing.
	 *
	 * <p><b>Which blocks that actually is, from the 26.2 sources</b> (MOD-389 — the earlier wording here
	 * claimed leaves and vines were free, and they are not): {@code SaplingBlock} is {@code .instabreak()},
	 * hardness {@code 0.0} → free. {@code leavesProperties()} is {@code .strength(0.2F)} and {@code VINE}
	 * is {@code 0.2} as well → both are charged the full per-block cost. The gate reads
	 * {@code getDestroySpeed}, not a block tag, so "leafy" has nothing to do with it: only a genuine
	 * zero-hardness block is free. This matters when balancing the chainsaw — a canopy is not free
	 * clearing.
	 */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0f
				&& ItemEnergy.get(stack) >= Config.electricChainsawEuPerBlock) {
			ItemEnergy.spend(stack, Config.electricChainsawEuPerBlock, owner);
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
