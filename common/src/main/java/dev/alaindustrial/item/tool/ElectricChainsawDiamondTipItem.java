package dev.alaindustrial.item.tool;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Diamond-Tipped Electric Chainsaw (MOD-374) — the upgrade tier of the {@link ElectricChainsawItem},
 * and the wood-side mirror of the {@link ElectricDrillDiamondTipItem}: the same EU-powered,
 * never-breaking diamond-tier axe, but it cuts faster and can switch its drops between normal and
 * Silk Touch on the fly.
 *
 * <p>Everything energy-related is inherited unchanged: {@code ItemEnergy.capacity/inputRate} dispatch
 * on {@code instanceof ElectricChainsawItem}, so this subclass shares the base chainsaw's buffer,
 * charge rate and per-block cost, and every charger that already accepts the base chainsaw accepts
 * this one with no changes on their side.
 *
 * <h2>What the Silk Touch mode is actually for here — leaves, not ore</h2>
 * The drill's toggle exists because permanent Silk Touch would break the mod's ore-doubling loop. The
 * chainsaw's domain has no ore, so the toggle earns its keep on a different block: <b>leaves</b>.
 * Verified against the 26.2 mojmap jar before writing (project rule 1) — every vanilla leaf loot
 * table gates its "drop the leaf block" entry behind an {@code any_of} of
 * {@code match_tool items=minecraft:shears} and {@code match_tool predicates.minecraft:enchantments
 * silk_touch>=1}, and wraps the sapling/stick/apple pools in an {@code inverted} of that same
 * {@code any_of}. So the genuine enchantment — and nothing else — flips a leaf block between
 * "drops itself" and "drops saplings and sticks".
 *
 * <p>Note the loot tables name the {@code minecraft:shears} <i>item</i> literally, not a shears tag,
 * which is why a chainsaw cannot reach that branch by being tagged as shears: the enchantment is the
 * only available lever. Logs are unaffected either way — {@code oak_log.json} and its siblings have a
 * single unconditional pool, so Silk Touch changes nothing about felling a trunk.
 *
 * <p>The mode is switchable rather than permanent for the same reason it is on the drill: a
 * permanently silk chainsaw could never farm saplings again, which would make the upgrade a
 * downgrade for anyone replanting a forest.
 *
 * <h2>How the mode is implemented</h2>
 * Identical mechanism to {@link ElectricDrillDiamondTipItem}, and for the same verified reasons:
 * {@code Tool.Rule} has exactly three fields ({@code blocks}, {@code speed}, {@code correctForDrops})
 * and no silk-drops flag, so the {@code TOOL} component cannot express Silk Touch; and it cannot be a
 * preset default component either, because {@code ItemEnchantments} needs a {@code Holder<Enchantment>}
 * from a <i>dynamic</i> (datapack) registry that does not exist yet when items are registered. The
 * write therefore happens at interaction time in {@link #use}, where
 * {@code ServerLevel.registryAccess()} can resolve the holder; reading needs no registry at all
 * ({@link #isSilkMode} compares holders by resource key).
 *
 * <p>Using the real enchantment buys the enchantment glint and a "Silk Touch I" tooltip line for
 * free, so the state is visible without any custom rendering.
 *
 * <h2>One thing that is simpler than on the drill</h2>
 * The drill had to override {@code useOn} to return {@code PASS} while sneaking, because its
 * inherited right-click places a torch (MOD-089) and would have eaten the sneak-click before
 * {@link #use} ever ran. {@link ElectricChainsawItem} overrides neither {@code useOn} nor
 * {@code use} — log stripping was deliberately given up when the class chose not to extend
 * {@code AxeItem} — so {@code Item.useOn} already returns {@code PASS} and vanilla falls straight
 * through to {@link #use}. No override is needed, and adding one would be dead code.
 */
public class ElectricChainsawDiamondTipItem extends ElectricChainsawItem {

	/** Cutting speed on {@code #minecraft:mineable/axe} and {@code #minecraft:leaves} — above the base
	 * chainsaw's 9.0, matching the +1.5 step the drill upgrade took (8.5 → 10.0). The mining tier stays
	 * diamond exactly as on the base chainsaw: the upgrade is meant to feel faster in the hand, not to
	 * unlock blocks the base chainsaw cannot break. The boost is unconditional — it does not depend on
	 * the Silk Touch mode, so switching drops never costs the player speed. */
	private static final float MINING_SPEED = 10.5f;
	/** Enchantability — the diamond value, same as the base chainsaw, for the enchanting table. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — identical to the base chainsaw: the upgrade is a cutting tool, not a better weapon. */
	private static final double ATTACK_DAMAGE = 5.0;
	private static final double ATTACK_SPEED = -3.0;

	public ElectricChainsawDiamondTipItem(Properties properties) {
		super(properties);
	}

	/**
	 * The upgraded chainsaw's item properties, applied identically by both loaders. Deliberately a full
	 * copy of {@link ElectricChainsawItem#electricChainsawProperties} rather than a call to it plus an
	 * override: the {@code TOOL} component is a single immutable record, so changing the cutting speed
	 * means rebuilding the whole component anyway, and a "build then replace" version would be longer
	 * and hide which value actually differs.
	 *
	 * <p>The one difference from the base chainsaw is {@link #MINING_SPEED}; everything else — all three
	 * rules in order ({@code deniesDrops} first, then {@code #mineable/axe}, then the chainsaw's own
	 * {@code #minecraft:leaves} rule), {@code damagePerBlock = 0} so nothing ever calls
	 * {@code hurtAndBreak}, explicit {@code stacksTo(1)} because we skip {@code durability(...)} — is the
	 * base chainsaw's reasoning verbatim; see its javadoc for why.
	 */
	public static Properties electricChainsawDiamondTipProperties(Properties props) {
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

	// --- Silk Touch mode: shift-right-click flips it, the enchantment component carries the state ---

	/**
	 * Whether the chainsaw is currently in Silk Touch mode.
	 *
	 * <p>Reads the stored {@code ENCHANTMENTS} component and compares holders by resource key, which
	 * needs no registry access — so this is safe to call on the client (tooltip) and on the server
	 * alike. An absent component means normal drops, so a freshly crafted chainsaw starts in normal
	 * mode and keeps dropping saplings out of the box.
	 *
	 * <p>Note this is honest about the state rather than about how it got there: a player who puts Silk
	 * Touch on at an enchanting table reads as "silk mode on" here, and the toggle will then turn it
	 * off. That is the intended behaviour — the mode is the enchantment, not a second parallel flag that
	 * could disagree with it.
	 */
	public static boolean isSilkMode(ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null) {
			return false;
		}
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.is(Enchantments.SILK_TOUCH)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Shift-right-click toggles Silk Touch mode; a plain right-click passes through so it does not
	 * interfere with anything else the hand might do.
	 *
	 * <p>Unlike the drill, no {@code useOn} override is needed to reach this method while sneaking —
	 * see the class javadoc.
	 *
	 * <p>The component write is server-only — the client would have no authority over it and the change
	 * arrives through the normal stack sync. The holder is resolved from
	 * {@code ServerLevel.registryAccess()} because enchantments are a dynamic registry. The feedback
	 * sound plays on both sides: on the client it is the toggling player's own prediction, which is what
	 * makes them hear it immediately ({@code Player.playSound} excludes the actor on the server).
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean nowSilk = !isSilkMode(stack);
		if (level instanceof ServerLevel serverLevel) {
			Holder<Enchantment> silkTouch = serverLevel.registryAccess()
					.lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.SILK_TOUCH);
			EnchantmentHelper.updateEnchantments(stack, mutable -> {
				if (nowSilk) {
					mutable.set(silkTouch, 1);
				} else {
					mutable.removeIf(enchantment -> enchantment.is(Enchantments.SILK_TOUCH));
				}
			});
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable(nowSilk
								? "item.alaindustrial.electric_chainsaw_diamond_tip.silk_on"
								: "item.alaindustrial.electric_chainsaw_diamond_tip.silk_off")
								.withStyle(nowSilk ? ChatFormatting.AQUA : ChatFormatting.GRAY),
						true);
			}
		}
		// The same copper-bulb click the drill upgrade and the Electromagnet use — a powered device
		// switching mode.
		player.playSound(nowSilk ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				0.7F, nowSilk ? 1.15F : 0.9F);
		return InteractionResult.SUCCESS;
	}
}
