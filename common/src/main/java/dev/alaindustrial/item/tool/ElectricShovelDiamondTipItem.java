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
 * Diamond-Tipped Electric Shovel (MOD-481) — the upgrade tier of the {@link ElectricShovelItem} and the
 * fourth and last member of the diamond-tip family, after the {@link ElectricDrillDiamondTipItem}
 * (MOD-321), the {@link ElectricChainsawDiamondTipItem} (MOD-374) and the
 * {@link ElectricHoeDiamondTipItem} (MOD-378). Same deal as those three: a separate item, a faster
 * {@code TOOL} component, and one extra ability tied to what the tool is actually for.
 *
 * <p>Everything about energy is inherited untouched — {@link dev.alaindustrial.item.energy.ItemEnergy}
 * and the tooltip dispatcher both branch on {@code instanceof ElectricShovelItem}, so the buffer, the
 * input rate and the per-block drain all carry over with no change to the energy layer and no config key
 * of its own. The free path-making and campfire dousing of the base shovel are inherited too — see
 * {@link #useOn} for the one deliberate difference.
 *
 * <h2>Why Silk Touch here, when the hoe was told not to copy it</h2>
 * The hoe upgrade (MOD-378) rejected a Silk Touch mode because every block in
 * {@code #minecraft:mineable/hoe} already drops itself — silk-touching them changes nothing. The shovel
 * is the opposite case: its domain is full of blocks whose loot table deliberately substitutes something
 * else, and the substitution is exactly what a builder does not want. Verified against the 26.2 loot
 * tables before this class was written (project rule 1):
 * <ul>
 * <li>{@code grass_block}, {@code podzol}, {@code mycelium} — drop {@code dirt}; silk yields the block
 * itself, which is the only way to relocate a lawn.</li>
 * <li>{@code clay} — drops 4 clay balls; silk yields the clay block.</li>
 * <li>{@code gravel} — rolls flint (Fortune-scaled); silk always yields gravel.</li>
 * <li>{@code snow_block} / snow layers — drop snowballs; silk yields the block and the layers.</li>
 * <li>{@code suspicious_sand} / {@code suspicious_gravel} — silk yields the suspicious block.</li>
 * </ul>
 * So the perk is "by the tool's profile" in the same sense irrigation is for the hoe, rather than a
 * third copy of the drill's mode for its own sake.
 *
 * <h2>Why the mode is switchable and not just an enchantment</h2>
 * The base shovel can already be given Silk Touch at an enchanting table, but a vanilla enchantment is
 * permanent, and on a shovel it costs something concrete: Silk Touch and Fortune are mutually exclusive,
 * and Fortune on a shovel is what multiplies the {@code gravel → flint} roll. A player who enchants for
 * silk gives up flint farming for good. The switchable mode lets them keep Fortune for flint and flip to
 * silk only when they want the block itself.
 *
 * <p>The implementation is the drill's, verbatim and for the same verified reasons: {@code Tool.Rule} has
 * exactly three fields ({@code blocks}, {@code speed}, {@code correctForDrops}) and <b>no</b> silk-drops
 * flag, so the {@code TOOL} component cannot express Silk Touch; drops are decided by loot tables, which
 * test {@code minecraft:match_tool} against the {@code minecraft:enchantments} predicate. The only
 * mechanism that produces silk drops is therefore the real enchantment, so the toggle sets and clears
 * {@code minecraft:silk_touch} on the stack. It cannot be a preset default component either —
 * {@code ItemEnchantments} needs a {@code Holder<Enchantment>}, and enchantments live in a dynamic
 * (datapack) registry that does not exist yet when items are registered — so the write happens at
 * interaction time in {@link #use}, where {@code ServerLevel.registryAccess()} can resolve the holder.
 *
 * <p>A pleasant side effect of using the genuine enchantment: the stack shows the enchantment glint and
 * lists "Silk Touch I" in its tooltip while the mode is on, so the state is visible without any custom
 * rendering.
 *
 * <h2>NeoForge</h2>
 * The inherited {@code useOn} delegates to {@code Items.DIAMOND_SHOVEL.useOn(context)}, which on NeoForge
 * is gated on the held item declaring the shovel {@code ItemAbility}. This class therefore has a loader
 * subclass, {@code ElectricShovelDiamondTipItemNeoForge} — extending the NeoForge base shovel instead is
 * not an option, because the shared logic below must stay in {@code common/} where both loaders run it.
 */
public class ElectricShovelDiamondTipItem extends ElectricShovelItem {

	/** Digging speed on {@code #minecraft:mineable/shovel} — the base shovel's 9.0 plus the same
	 * {@code +1.5} step the drill (8.5 → 10.0), the chainsaw (9.0 → 10.5) and the hoe (9.0 → 10.5)
	 * upgrades took. Mining tier is untouched: the upgrade digs faster, it does not reach anything new. */
	private static final float MINING_SPEED = 10.5f;
	/** Enchantability — the diamond value, same as the base shovel. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers, inherited verbatim from the base shovel: a vanilla diamond shovel's. The upgrade is
	 * a digging tool, not a better weapon, and there is no {@code WEAPON} component on either. */
	private static final double ATTACK_DAMAGE = 1.5;
	private static final double ATTACK_SPEED = -3.0;

	public ElectricShovelDiamondTipItem(Properties properties) {
		super(properties);
	}

	/**
	 * The upgrade's item properties. This is a full copy of
	 * {@link ElectricShovelItem#electricShovelProperties} rather than a call into it, for the same reason
	 * all three sibling upgrades copy theirs: {@code Tool} is an immutable record, so "build the base
	 * component, then rebuild it with one different number" would be longer than this and would hide the
	 * one value that actually differs. The single difference is {@link #MINING_SPEED}.
	 *
	 * <p>Rule order is load-bearing and matches the base shovel: {@code deniesDrops} on the diamond
	 * deny-tag first, {@code minesAndDrops} on {@code #mineable/shovel} second — the first matching rule
	 * wins. {@code damagePerBlock = 0} so nothing ever calls {@code hurtAndBreak}, and {@code stacksTo(1)}
	 * is explicit because we skip {@code durability(...)}; see the base shovel's javadoc for why.
	 */
	public static Properties electricShovelDiamondTipProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
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

	// --- Silk Touch mode: shift-right-click flips it, the enchantment component carries the state ---

	/**
	 * Whether the shovel is currently in Silk Touch mode.
	 *
	 * <p>Reads the stored {@code ENCHANTMENTS} component and compares holders by resource key, which needs
	 * no registry access — so this is safe to call on the client (tooltip) and on the server alike. An
	 * absent component means normal drops, so a freshly crafted shovel starts in normal mode and still
	 * rolls flint out of gravel out of the box.
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
	 * path-making and campfire dousing are skipped while sneaking and vanilla falls through from a
	 * non-consuming {@code useOn} to {@code use}. A plain (non-sneaking) right-click still makes a dirt
	 * path and douses a campfire exactly as on the base shovel.
	 *
	 * <p>This override is needed here and was <b>not</b> needed on the chainsaw upgrade, because the two
	 * base tools differ: the base chainsaw's {@code useOn} does not consume (log stripping was given up),
	 * while the base shovel's delegates to {@code Items.DIAMOND_SHOVEL.useOn}, which flattens grass
	 * regardless of whether the player is sneaking. Without this, a shift-click on any flattenable block
	 * would silently turn into a path and the toggle would be unreachable there — the drill has the same
	 * conflict with its torch placement and solves it the same way.
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
	 * arrives through the normal stack sync. The holder is resolved from
	 * {@code ServerLevel.registryAccess()} because enchantments are a dynamic registry (see the class
	 * javadoc). The feedback sound plays on both sides, matching the drill and the chainsaw: on the client
	 * it is the toggling player's own prediction, which is what makes them hear it immediately
	 * ({@code Player.playSound} excludes the actor on the server).
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
								? "item.alaindustrial.electric_shovel_diamond_tip.silk_on"
								: "item.alaindustrial.electric_shovel_diamond_tip.silk_off")
								.withStyle(nowSilk ? ChatFormatting.AQUA : ChatFormatting.GRAY),
						true);
			}
		}
		// The same copper-bulb click the drill and the chainsaw use for their toggle — a powered device
		// switching mode.
		player.playSound(nowSilk ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				0.7F, nowSilk ? 1.15F : 0.9F);
		return InteractionResult.SUCCESS;
	}
}
