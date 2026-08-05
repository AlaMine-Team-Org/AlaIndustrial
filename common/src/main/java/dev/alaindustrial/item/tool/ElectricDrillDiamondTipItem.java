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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Diamond-Tipped Electric Drill (MOD-321) — the upgrade tier of the {@link ElectricDrillItem}: the
 * same EU-powered, never-breaking diamond-tier pickaxe, but it digs faster and can switch its drops
 * between normal and Silk Touch on the fly.
 *
 * <p>Everything energy-related is inherited unchanged: {@code ItemEnergy.capacity/inputRate}
 * dispatch on {@code instanceof ElectricDrillItem}, so this subclass shares the base drill's buffer,
 * charge rate and per-block cost, and every charger that already accepts the base drill accepts this
 * one with no changes on their side. The torch-placing right-click (MOD-089) is inherited too — see
 * {@link #useOn} for the one deliberate difference.
 *
 * <h2>Why the upgrade is not just "the base drill with Silk Touch on it"</h2>
 * The base drill can already be given Silk Touch at an enchanting table, but a vanilla enchantment is
 * permanent: it also locks out Fortune (the two are mutually exclusive) and it makes every ore drop as
 * a block, which breaks the mod's own ore-doubling loop (ore → Macerator → 2 dust). The upgrade instead
 * carries a <b>switchable</b> mode, so the player keeps the normal drops that feed the Macerator and
 * flips to Silk Touch only when they want the block itself.
 *
 * <h2>How the Silk Touch mode is implemented</h2>
 * Verified against the 26.2 mojmap jar before writing (project rule 1): {@code Tool.Rule} has exactly
 * three fields — {@code blocks}, {@code speed}, {@code correctForDrops} — and <b>no</b> silk-drops flag,
 * so the {@code TOOL} component cannot express Silk Touch. Drops are decided by loot tables, which test
 * {@code minecraft:match_tool} against the {@code minecraft:enchantments} predicate (this mod's own ore
 * tables do exactly that). The only mechanism that produces silk drops is therefore the real enchantment,
 * so the toggle sets and clears {@code minecraft:silk_touch} on the stack.
 *
 * <p>It cannot be a preset default component either: {@code ItemEnchantments} needs a
 * {@code Holder<Enchantment>}, and enchantments live in a <i>dynamic</i> (datapack) registry that does
 * not exist yet when items are registered — {@code Item.Properties} accordingly offers only
 * {@code enchantable(int)} (the enchantability value), no enchantment-component helper. The write
 * therefore happens at interaction time in {@link #use}, where {@code ServerLevel.registryAccess()} can
 * resolve the holder. Reading the mode needs no registry at all: {@link #isSilkMode} scans the stored
 * component's holders with {@code Holder.is(ResourceKey)}.
 *
 * <p>A pleasant side effect of using the genuine enchantment: the stack shows the enchantment glint and
 * lists "Silk Touch I" in its tooltip while the mode is on, so the state is visible without any custom
 * rendering.
 */
public class ElectricDrillDiamondTipItem extends ElectricDrillItem {

	/** Mining speed on {@code #minecraft:mineable/pickaxe} — above the base drill's 8.5 (and vanilla
	 * netherite's 9.0), while the mining tier stays diamond exactly as on the base drill. The upgrade is
	 * meant to feel faster in the hand, not to unlock blocks the base drill cannot break. */
	private static final float MINING_SPEED = 10.0f;
	/** Enchantability — the diamond value, same as the base drill, for the enchanting table. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — identical to the base drill: the upgrade is a mining tool, not a better weapon. */
	private static final double ATTACK_DAMAGE = 5.0;
	private static final double ATTACK_SPEED = -2.7;

	public ElectricDrillDiamondTipItem(Properties properties) {
		super(properties);
	}

	/**
	 * The upgraded drill's item properties, applied identically by both loaders. Deliberately a full copy
	 * of {@link ElectricDrillItem#electricDrillProperties} rather than a call to it plus an override: the
	 * {@code TOOL} component is a single immutable record, so changing the mining speed means rebuilding
	 * the whole component anyway, and a "build then replace" version would be longer and hide which value
	 * actually differs. The one difference from the base drill is {@link #MINING_SPEED}; everything else —
	 * rule order ({@code deniesDrops} first, then {@code minesAndDrops}), {@code damagePerBlock = 0} so
	 * nothing ever calls {@code hurtAndBreak}, explicit {@code stacksTo(1)} because we skip
	 * {@code durability(...)} — is the base drill's reasoning verbatim; see its javadoc for why.
	 */
	public static Properties electricDrillDiamondTipProperties(Properties props) {
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

	// --- Silk Touch mode: shift-right-click flips it, the enchantment component carries the state ---

	/**
	 * Whether the drill is currently in Silk Touch mode.
	 *
	 * <p>Reads the stored {@code ENCHANTMENTS} component and compares holders by resource key, which needs
	 * no registry access — so this is safe to call on the client (tooltip) and on the server alike. An
	 * absent component means normal drops, so a freshly crafted drill starts in normal mode and feeds the
	 * Macerator out of the box.
	 *
	 * <p>Note this is honest about the state rather than about how it got there: a player who puts Silk
	 * Touch on at an enchanting table reads as "silk mode on" here, and the toggle will then turn it off.
	 * That is the intended behaviour — the mode is the enchantment, not a second parallel flag that could
	 * disagree with it.
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
	 * Shift-clicking a block must reach {@link #use} so it can toggle the mode, so the inherited
	 * torch placement (MOD-089) is skipped while sneaking and vanilla falls through from a
	 * non-consuming {@code useOn} to {@code use}. A plain (non-sneaking) right-click still places a
	 * torch exactly as on the base drill.
	 *
	 * <p>This is the only inherited behaviour the upgrade changes, and it costs nothing in practice:
	 * torches are placed with a plain click, and the base drill's own javadoc already describes
	 * shift-click as reserved for normal interaction.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		return super.useOn(context);
	}

	/**
	 * Shift-right-click toggles Silk Touch mode; a plain right-click passes through so it does not
	 * interfere with anything else the hand might do.
	 *
	 * <p>The component write is server-only — the client would have no authority over it and the change
	 * arrives through the normal stack sync. The holder is resolved from {@code ServerLevel.registryAccess()}
	 * because enchantments are a dynamic registry (see the class javadoc). The feedback sound plays on both
	 * sides, matching {@code MagnetItem}: on the client it is the toggling player's own prediction, which is
	 * what makes them hear it immediately.
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
								? "item.alaindustrial.electric_drill_diamond_tip.silk_on"
								: "item.alaindustrial.electric_drill_diamond_tip.silk_off")
								.withStyle(nowSilk ? ChatFormatting.AQUA : ChatFormatting.GRAY),
						true);
			}
		}
		// The same copper-bulb click the Electromagnet uses for its toggle — a powered device switching mode.
		player.playSound(nowSilk ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				0.7F, nowSilk ? 1.15F : 0.9F);
		return InteractionResult.SUCCESS;
	}
}
