package dev.alaindustrial.item.tool;

import dev.alaindustrial.item.energy.ItemEnergy;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric Hoe (MOD-342) — the fourth and last member of the EU hand-tool line, after the
 * {@link ElectricDrillItem} (stone), the {@link ElectricChainsawItem} (wood) and the
 * {@link ElectricShovelItem} (earth). Same contract as its three siblings: it runs on EU instead of
 * durability, never breaks, and when it runs flat it degrades to hand speed with every drop intact.
 *
 * <p>Energy is not re-implemented (project rule 4): the charge lives in the shared
 * {@code pouch_energy} component through {@link ItemEnergy}, which gains one {@code capacity} /
 * {@code inputRate} branch for this class — so the Battery Box charge slot, a worn Energy Pack and the
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
 * {@code HoeItem.useOn} was disassembled before this class was written (project rule 1) and is
 * delegatable for the same three reasons the shovel's was:
 * <ul>
 * <li>it is {@code public};</li>
 * <li>its bytecode contains <b>no {@code aload_0}</b> — it reads nothing from {@code this}, only the
 * {@link UseOnContext} and the static {@code TILLABLES} map;</li>
 * <li>the only thing it does to the stack is {@code getItemInHand().hurtAndBreak(...)}, which routes
 * through {@code processDurabilityChange} and returns immediately on {@code !isDamageableItem()} — a
 * no-op for a tool with no {@code MAX_DAMAGE}, which is precisely what this item is.</li>
 * </ul>
 * So {@link #useOn} hands the context to the vanilla diamond hoe and the tool keeps every vanilla
 * conversion: dirt/grass/path → farmland, coarse dirt → dirt, rooted dirt → dirt (dropping a hanging
 * root). Delegation also beats re-implementing the table, because {@code TILLABLES} is mutable and
 * other mods add to it.
 *
 * <p>Unlike {@link ElectricShovelItem#useOn}, which makes dirt paths for free, this interaction
 * <b>costs EU</b> — see {@link #useOn} for the gate and the reasoning. The split is not arbitrary: a
 * dirt path is a cosmetic nicety a wooden shovel also makes, whereas tilling is the hoe's whole job.
 * The first cut of this class made tilling free by analogy with the shovel and the tool then spent no
 * energy at all in normal play, which is the defect that produced the current design.
 *
 * <h2>EU behaviour — identical in shape to the other three</h2>
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed while the hoe holds at least one block's worth
 * of EU; otherwise exactly {@code 1.0f}. That value is not an approximation —
 * {@code Player.getDestroySpeed} only applies Efficiency when the tool reports {@code > 1.0F}, so a
 * flat hoe is a plain hand and the enchantment cannot revive it.</li>
 * <li>{@link #mineBlock}: drains {@link Config#electricHoeEuPerBlock} per block actually broken,
 * server-side only, only for blocks with non-zero hardness, and only when there was enough EU to run
 * at tool speed in the first place. Creative is dropped inside {@link ItemEnergy#spend} (MOD-081).</li>
 * </ul>
 */
public class ElectricHoeItem extends Item {

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
	 */
	public static Properties electricHoeProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
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
	 * Delegates to the vanilla diamond hoe's {@code useOn}, giving this tool every conversion a player
	 * expects from a hoe — most visibly turning dirt, grass and dirt paths into farmland. See the class
	 * javadoc for the disassembly that proves the delegation is safe.
	 *
	 * <p>The receiver has to be {@code Items.DIAMOND_HOE} rather than {@code super}: {@code useOn} is
	 * virtual, and {@code Item.useOn} (our real superclass method) just returns {@code PASS}.
	 *
	 * <p><b>Tilling is powered</b>, following the drill's torch (MOD-097) rather than the shovel's free
	 * dirt paths: below {@link Config#electricHoeTillEuCost} the hoe tills nothing and says so on the
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
	 * <li>The charge is taken <b>after</b> the vanilla call and only when it reports
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
				&& ItemEnergy.get(hoe) < Config.electricHoeTillEuCost) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable("item.alaindustrial.electric_hoe.till_no_charge")
								.withStyle(ChatFormatting.RED),
						true);
			}
			return InteractionResult.CONSUME;
		}

		InteractionResult result = Items.DIAMOND_HOE.useOn(context);
		if (result.consumesAction() && player != null && !context.getLevel().isClientSide()) {
			ItemEnergy.spend(hoe, Config.electricHoeTillEuCost, player);
		}
		return result;
	}

	/**
	 * Would a hoe convert the clicked block? Asked <b>before</b> the charge gate in {@link #useOn} so a flat
	 * hoe neither swallows an unrelated right-click nor reports an empty buffer to a player who was not
	 * tilling (MOD-389). Reads the world, changes nothing.
	 *
	 * <p>This is the one place where the two loaders genuinely disagree, so it is overridable rather than
	 * static. The implementation here is vanilla's — {@link VanillaTillables} reads the real
	 * {@code HoeItem.TILLABLES}, so modded tillables count too — and it is what Fabric runs. NeoForge
	 * patches that map out of the flow and answers from the block instead, so
	 * {@code ElectricHoeItemNeoForge} (and the upgrade's own NeoForge subclass, which cannot inherit it)
	 * override this with {@code getToolModifiedState(…, HOE_TILL, simulate = true)}. A single shared
	 * implementation would be wrong on one loader either way: vanilla's map misses what a NeoForge mod adds
	 * through {@code BlockToolModificationEvent}, and NeoForge's copy of the rules drops vanilla's DOWN-face
	 * check.
	 */
	protected boolean wouldTill(UseOnContext context) {
		return VanillaTillables.wouldTill(context);
	}

	// --- breaking: full speed while charged, hand speed when flat (drops kept either way) ---

	/**
	 * Returns exactly {@code 1.0f} when the hoe cannot afford a block — see the class javadoc for why the
	 * value must not be "slightly above 1.0". The mining tier and the drops still come from the
	 * {@code TOOL} component either way, so a flat hoe keeps every block's drop; it is just slow.
	 */
	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (ItemEnergy.get(stack) >= Config.electricHoeEuPerBlock) {
			return super.getDestroySpeed(stack, state);
		}
		return 1.0f;
	}

	/**
	 * Drains EU for the block just broken. The two guards mirror vanilla's durability gate in
	 * {@code Item.mineBlock}: {@code !isClientSide} because {@code mineBlock} runs on both sides and the
	 * charge must only move on the server (the client picks the new value up from the synced
	 * {@code pouch_energy} component), and non-zero hardness so instant-break blocks cost nothing. The
	 * drain is only taken when there was enough EU to break at tool speed, so a block chewed through at
	 * hand speed is free.
	 */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0f
				&& ItemEnergy.get(stack) >= Config.electricHoeEuPerBlock) {
			ItemEnergy.spend(stack, Config.electricHoeEuPerBlock, owner);
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
