package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.compat.RightClickTransform;
import java.util.List;
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
import net.minecraft.world.level.block.Block;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.energy.PoweredToolTooltip;

/**
 * Electric Shovel (MOD-338) — the earth-side member of the EU hand-tool line, after the
 * {@link ElectricDrillItem} (stone) and the {@link ElectricChainsawItem} (wood). Same contract as its
 * two siblings: it runs on EU instead of durability, never breaks, and when it runs flat it degrades
 * to hand speed with every drop intact rather than becoming dead weight.
 *
 * <p>Energy is not re-implemented (project rule 4): the charge lives in the shared
 * {@code pouch_energy} component through {@link ItemEnergy}, which reads this class's
 * {@code PoweredItem} answers (MOD-707) — so the Battery Box charge slot, a worn Energy Pack and the
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
 * <h2>Path-making and campfire dousing — kept, unlike the chainsaw's log stripping</h2>
 * The chainsaw had to give up log stripping because {@code AxeItem.STRIPPABLES} was
 * {@code protected static}. The shovel keeps the equivalent: its vanilla right-click conversions — grass,
 * dirt, podzol, mycelium, coarse and rooted dirt into a dirt path, and dousing a lit campfire — are reached
 * through {@link RightClickTransform#SHOVEL}: {@link #electricShovelProperties} declares the conversion and
 * {@link #useOn} applies it. How vanilla carries a shovel's conversions (and whether dousing is one of them
 * or a property of the campfire) is not the same on every Minecraft line; that difference lives in the
 * facade, not here. The conversion cannot wear this tool out: its durability hit is a no-op on an item with
 * no {@code MAX_DAMAGE}, which is precisely what this item is.
 *
 * <p>The interaction is deliberately <b>free</b> — it is not gated on EU the way the drill's torch
 * placement is. Making a path is something a wooden shovel does; charging for it would contradict the
 * "a flat tool still works, just slowly" rule that defines this whole line, and would leave a
 * discharged electric shovel strictly worse than a stick with a plank on it.
 *
 * <h2>EU behaviour — identical in shape to the drill and the chainsaw</h2>
 * The line's contract, written once in {@link ElectricMiningToolItem} (MOD-707):
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed while the shovel holds at least one block's
 * worth of EU; otherwise exactly {@code 1.0f}. That value is not an approximation —
 * {@code Player.getDestroySpeed} only applies Efficiency when the tool reports {@code > 1.0F}, so a
 * flat shovel is a plain hand and the enchantment cannot revive it.</li>
 * <li>{@link #mineBlock}: drains {@link ToolConfig#electricShovelEuPerBlock} per block actually dug,
 * server-side only, only for blocks with non-zero hardness (so genuinely instant-break blocks cost
 * nothing, just as they never wear a vanilla shovel — <b>a snow layer is not one of them</b>, see
 * below), and only when there was enough EU to run at tool speed in the first place.
 * Creative is dropped inside {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 *
 * <p><b>Snow layers are not free</b> (MOD-389 — the earlier wording here said they were):
 * {@code Blocks.SNOW} is {@code .strength(0.1F)} in the 26.2 sources, so every layer costs the full
 * per-block drain. Short grass ({@code .instabreak()}, {@code 0.0}) is genuinely free, but it is not a
 * shovel block either. The gate reads {@code getDestroySpeed}, so the only free blocks are the ones with
 * hardness exactly {@code 0.0}.
 */
public class ElectricShovelItem extends ElectricMiningToolItem {

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
	 * <p>{@code RightClickTransform.SHOVEL.declare} is the other half of {@code Properties.shovel(...)} —
	 * the half that carries path-making (see {@link RightClickTransform#declare}).
	 */
	public static Properties electricShovelProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return RightClickTransform.SHOVEL.declare(props.stacksTo(1))
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
	 * Applies the shovel conversion this item declares ({@link RightClickTransform#SHOVEL}): the
	 * right-click a player expects from any shovel, most visibly turning grass into a dirt path. Free of
	 * charge — see the class javadoc.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		return RightClickTransform.SHOVEL.apply(context);
	}

	/** MOD-707: the energy numbers of this tool's tier; a higher tier of the same line overrides it. */
	@Override
	protected ElectricToolTier toolTier() {
		return ElectricToolTier.SHOVEL;
	}

	/** Usage, then the charge (MOD-716, ADR-040). */
	@Override
	public PoweredToolTooltip toolTooltip() {
		return PoweredToolTooltip.of("electric_shovel", List.of(ServerBalance::electricShovelEuPerBlock));
	}
}
