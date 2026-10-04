package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.compat.RightClickTransform;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.energy.PoweredToolTooltip;

/**
 * Electric Hoe (MOD-342) — the fourth and last member of the EU hand-tool line, after the
 * {@link ElectricDrillItem} (stone), the {@link ElectricChainsawItem} (wood) and the
 * {@link ElectricShovelItem} (earth). Same contract as its three siblings: it runs on EU instead of
 * durability, never breaks, and when it runs flat it degrades to hand speed with every drop intact.
 *
 * <p>Energy is not re-implemented (project rule 4): the charge lives in the shared
 * {@code pouch_energy} component through {@link ItemEnergy}, which reads this class's
 * {@code PoweredItem} answers (MOD-707) — so the Battery Box charge slot, a worn Energy Pack and the
 * Charge Pad all charge it with no changes on their side.
 *
 * <h2>Why a hand-built {@code TOOL} component, and not {@code extends HoeItem}</h2>
 * The same reason the chainsaw could not extend {@code AxeItem} and the shovel could not extend
 * {@code ShovelItem}: {@link net.minecraft.world.item.HoeItem}'s constructor applies
 * {@code Properties.hoe(material, damage, speed)} <i>inside</i> itself, after any factory the caller
 * passed has already run. That routes through {@code ToolMaterial.applyToolProperties}, which sets
 * {@code MAX_DAMAGE} — the item becomes damageable and the item bar goes to vanilla durability, which
 * is exactly what the "EU only, never breaks" model rules out. So the {@code TOOL} component is
 * assembled here by hand, mirroring what a diamond hoe would have produced, minus the durability.
 *
 * <h2>Tilling and the other right-click conversions — kept, but powered</h2>
 * The conversions are vanilla's own — dirt/grass/path → farmland, coarse dirt → dirt, rooted dirt → dirt
 * (dropping a hanging root) — reached through {@link RightClickTransform#HOE}:
 * {@link #electricHoeProperties} declares the conversion and {@link #useOn} applies it. How vanilla carries
 * a hoe's conversion is not the same on every Minecraft line, and that difference lives in the facade, not
 * here. The conversion cannot wear this tool out: its durability hit is a no-op on an item with no
 * {@code MAX_DAMAGE}, which is precisely what this item is.
 *
 * <p>Unlike {@link ElectricShovelItem#useOn}, which makes dirt paths for free, this interaction
 * <b>costs EU</b> — see {@link #useOn} for the gate and the reasoning. The split is not arbitrary: a
 * dirt path is a cosmetic nicety a wooden shovel also makes, whereas tilling is the hoe's whole job.
 * The first cut of this class made tilling free by analogy with the shovel and the tool then spent no
 * energy at all in normal play, which is the defect that produced the current design.
 *
 * <h2>EU behaviour — identical in shape to the other three</h2>
 * The line's contract, written once in {@link ElectricMiningToolItem} (MOD-707):
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed while the hoe holds at least one block's worth
 * of EU; otherwise exactly {@code 1.0f}. That value is not an approximation —
 * {@code Player.getDestroySpeed} only applies Efficiency when the tool reports {@code > 1.0F}, so a
 * flat hoe is a plain hand and the enchantment cannot revive it.</li>
 * <li>{@link #mineBlock}: drains {@link ToolConfig#electricHoeEuPerBlock} per block actually broken,
 * server-side only, only for blocks with non-zero hardness, and only when there was enough EU to run
 * at tool speed in the first place. Creative is dropped inside {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 */
public class ElectricHoeItem extends ElectricMiningToolItem {

	/** Speed on {@code #minecraft:mineable/hoe} — above a vanilla diamond hoe (8.0), matching the other
	 * three powered tools, so the line feels like one product. */
	private static final float MINING_SPEED = 9.0f;
	/** Enchantability — the diamond value ({@code ToolMaterial.DIAMOND.enchantmentValue}). */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — a vanilla diamond hoe's: +0.0 damage → 1.0 displayed, -0.0 speed → 4.0 displayed.
	 * The hoe is the one vanilla tool that is not a weapon at all; we keep that. No {@code WEAPON}
	 * component either: attacking neither drains EU nor wears the hoe. */
	private static final double ATTACK_DAMAGE = 0.0;
	private static final double ATTACK_SPEED = 0.0;

	public ElectricHoeItem(Properties properties) {
		super(properties);
	}

	/**
	 * The hoe's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — the only difference).
	 *
	 * <p>Two {@code TOOL} rules, which is what {@code Properties.hoe(ToolMaterial.DIAMOND, …)} would have
	 * produced: {@code deniesDrops} on the diamond deny-tag first (rule order matters — the first
	 * matching rule wins), then {@code minesAndDrops} on {@code #mineable/hoe}. No third rule: unlike the
	 * chainsaw, nothing this tool is for sits outside its own vanilla tag.
	 *
	 * <p>{@code damagePerBlock = 0} means {@code super.mineBlock} never calls {@code hurtAndBreak} —
	 * there is no durability to spend. {@code stacksTo(1)} is set explicitly because we skip
	 * {@code durability(...)}, which is where a vanilla tool's max-stack-size of 1 normally comes from.
	 *
	 * <p>{@code RightClickTransform.HOE.declare} is the other half of {@code Properties.hoe(...)} — the
	 * half that carries the tilling (see {@link RightClickTransform#declare}).
	 */
	public static Properties electricHoeProperties(Properties props) {
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

	// --- right-click: vanilla hoe conversions, powered ---

	/**
	 * Applies the hoe conversion this item declares ({@link RightClickTransform#HOE}), giving it every
	 * conversion a player expects from a hoe — most visibly turning dirt, grass and dirt paths into
	 * farmland.
	 *
	 * <p><b>Tilling is powered</b>, following the drill's torch (MOD-097) rather than the shovel's free
	 * dirt paths: below {@link ToolConfig#electricHoeTillEuCost} the hoe tills nothing and says so on the
	 * action bar. A path is a cosmetic nicety a wooden shovel also makes, but tilling is this tool's
	 * whole job — if it were free the hoe would never spend a single EU in normal play, which is exactly
	 * the defect this replaced.
	 *
	 * <p>Three details that are easy to get wrong:
	 * <ul>
	 * <li><b>Applicability is decided before the charge, not after</b> (MOD-389). The charge gate used to
	 * be the first thing in the method, so a flat hoe answered {@code CONSUME} to a right-click on
	 * <i>anything</i> — stone, a chest, a door — and shouted "not enough charge" at a player who was never
	 * tilling. Worse, {@code CONSUME} means "handled", so the click never reached the off-hand: no block
	 * placed, no food eaten. {@link #wouldTill} is asked first and returns {@code PASS} for a block a hoe
	 * cannot convert, exactly as vanilla would have.</li>
	 * <li>The charge is taken <b>after</b> the conversion and only when it reports
	 * {@code consumesAction()} — soil that is already farmland must not drain the buffer.</li>
	 * <li>The spend is server-side only. {@code useOn} runs on both sides and the client picks the new
	 * value up from the synced {@code pouch_energy} component.</li>
	 * </ul>
	 * Creative is exempt from the gate, and {@link ItemEnergy#spend} drops the spend there anyway
	 * (MOD-081).
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack hoe = context.getItemInHand();

		// MOD-389: not a tillable block → this tool has nothing to say. PASS, so the off-hand still runs.
		if (!wouldTill(context)) {
			return InteractionResult.PASS;
		}

		if (player != null && !player.getAbilities().instabuild
				&& ItemEnergy.get(hoe) < ToolConfig.electricHoeTillEuCost) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable("item.alaindustrial.electric_hoe.till_no_charge")
								.withStyle(ChatFormatting.RED),
						true);
			}
			return InteractionResult.CONSUME;
		}

		InteractionResult result = RightClickTransform.HOE.apply(context);
		if (result.consumesAction() && player != null && !context.getLevel().isClientSide()) {
			ItemEnergy.spend(hoe, ToolConfig.electricHoeTillEuCost, player);
		}
		return result;
	}

	/**
	 * Would a hoe convert the clicked block? Asked <b>before</b> the charge gate in {@link #useOn} so a flat
	 * hoe neither swallows an unrelated right-click nor reports an empty buffer to a player who was not
	 * tilling (MOD-389). Reads the world, changes nothing.
	 *
	 * <p>The answer is {@link RightClickTransform#wouldTill}. It is overridable rather than static for a
	 * loader whose hoe does not read the rules that facade reads: such a loader's subclass answers from its
	 * own side-effect-free probe instead.
	 */
	protected boolean wouldTill(UseOnContext context) {
		return RightClickTransform.wouldTill(context);
	}

	/** MOD-707: the energy numbers of this tool's tier; a higher tier of the same line overrides it. */
	@Override
	protected ElectricToolTier toolTier() {
		return ElectricToolTier.HOE;
	}

	/** Usage, then the charge (MOD-716, ADR-040). */
	@Override
	public PoweredToolTooltip toolTooltip() {
		return PoweredToolTooltip.of("electric_hoe", List.of(ServerBalance::electricHoeEuPerBlock));
	}
}
