package dev.alaindustrial.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The switchable Silk Touch mode of the diamond-tipped electric tools (drill, chainsaw, shovel), written
 * once (MOD-707). The mode IS the vanilla enchantment on the stack: no parallel flag that could disagree
 * with it, so an anvil or a grindstone that adds or removes Silk Touch switches the mode too.
 */
public final class SilkModeToggle {
	private SilkModeToggle() {
	}

	/** Whether the stack carries Silk Touch. Reading needs no registry: it scans the stored enchantments. */
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
	 * Shift-right-click toggles the mode; a plain right-click passes. The component write is server-only
	 * (the change arrives through the normal stack sync), the holder is resolved from the server's
	 * registry access because enchantments are a dynamic registry, and the action-bar message uses
	 * {@code messageKeyPrefix + ".silk_on" / ".silk_off"}. The copper-bulb click plays on both sides: on
	 * the client it is the player's own prediction ({@code Player.playSound} excludes the actor on the
	 * server).
	 */
	public static InteractionResult toggle(Level level, Player player, InteractionHand hand, String messageKeyPrefix) {
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
						Component.translatable(messageKeyPrefix + (nowSilk ? ".silk_on" : ".silk_off"))
								.withStyle(nowSilk ? ChatFormatting.AQUA : ChatFormatting.GRAY),
						true);
			}
		}
		player.playSound(nowSilk ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				0.7F, nowSilk ? 1.15F : 0.9F);
		return InteractionResult.SUCCESS;
	}

	/**
	 * The tooltip line of the mode, shown in BOTH states (MOD-716): while it is on vanilla already prints
	 * "Silk Touch I", but with it off nothing else hints that the tool has a toggle. Keys
	 * {@code <prefix>.silk_on} (aqua) and {@code <prefix>.silk_off} (grey).
	 */
	public static Function<ItemStack, MachineTooltipSpec.@Nullable Line> tooltipLine(String prefix) {
		return PoweredToolTooltip.toggle(SilkModeToggle::isSilkMode, prefix + ".silk_on",
				MachineTooltipSpec.Tone.AQUA, prefix + ".silk_off");
	}
}
