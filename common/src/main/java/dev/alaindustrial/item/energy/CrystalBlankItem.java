package dev.alaindustrial.item.energy;

import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModContent;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The chargeable half of an EU crystal (MOD-504): a blank that fills up once and is then gone.
 *
 * <p>A blank stores EU and <b>gives nothing back</b> — no discharge slot drains it, no tool runs off
 * it. The only thing that ever happens to its charge is reaching the top, at which point
 * {@link #promote} replaces the blank with the finished crystal of its tier. The finished crystal is a
 * plain {@link Item} with no buffer whatsoever, so from that moment there is no bar, no charge line
 * and nothing left to charge or drain — it is simply a crafting material.
 *
 * <p><b>Why the swap is not done here.</b> An {@link ItemStack} cannot change its item in place, so the
 * replacement has to happen wherever the stack lives — a machine slot, a player's inventory, a foreign
 * mod's storage. {@link #promote} is the shared decision ("is this blank finished, and what does it
 * become"), and each charging site applies it to its own container. The sites are listed in the OKF
 * spec and covered by a gametest per path, because a missed one is a blank that silently stays a blank.
 */
public class CrystalBlankItem extends Item {
	private final CrystalTier tier;

	public CrystalBlankItem(Properties properties, CrystalTier tier) {
		super(properties);
		this.tier = tier;
	}

	/** Which rung of the ladder this blank belongs to. */
	public CrystalTier tier() {
		return tier;
	}

	/**
	 * The finished crystal this stack has become, or {@link ItemStack#EMPTY} if it is not a full blank.
	 *
	 * <p>Callers replace the stack in their own container with the result. Charge is <b>not</b> carried
	 * over — the finished crystal has no buffer to carry it into; the energy was the price of making it.
	 */
	public static ItemStack promote(ItemStack stack) {
		if (!(stack.getItem() instanceof CrystalBlankItem blank)) {
			return ItemStack.EMPTY;
		}
		if (ItemEnergy.get(stack) < ItemEnergy.capacity(stack)) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(ModContent.crystal(blank.tier).get(), stack.getCount());
	}

	// MOD-498 — Item#appendHoverText carries a vanilla soft-deprecation marker but is still the only hook
	// an item has for a tooltip line it computes itself; vanilla overrides it in its own items too.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.crystal_blank.charge",
				ItemEnergy.get(stack), ItemEnergy.capacity(stack)).withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.crystal_blank.hint")
				.withStyle(ChatFormatting.YELLOW));
	}

	// --- item bar: only the blank has one; the finished crystal is a plain item ---

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
		return tier == CrystalTier.ENERGY ? EnergyTier.MV.color() : EnergyTier.HV.color();
	}
}
