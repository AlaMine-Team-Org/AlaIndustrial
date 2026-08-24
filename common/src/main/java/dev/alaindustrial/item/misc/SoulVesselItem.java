package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Soul Vessel (MOD-278) — the Mob Repeller's upgrade currency. While it sits anywhere in a player's
 * inventory, every hostile mob killed <em>by that player personally</em> yields one soul into it
 * (see {@link dev.alaindustrial.entity.SoulVesselKills}); a vessel holding enough souls fits the
 * repeller's slot and is consumed whole to evolve the block one tier.
 *
 * <p>The counter lives in {@link ModDataComponents#SOUL_VESSEL_KILLS} on the stack. Two rules
 * follow from that: the item registers with {@code stacksTo(1)} — stacks with different components
 * never merge, so pretending to stack would only confuse (each vessel keeps its own memory) — and
 * all reads/writes go through this class so "absent component = 0" stays the single convention.
 *
 * <p>The counter is hard-capped at {@link Config#mobRepellerEvolveKillsHv} (the highest threshold):
 * souls beyond the largest jump could never be spent, so collecting them would be a silent lie.
 */
public class SoulVesselItem extends Item {

	public SoulVesselItem(Properties properties) {
		super(properties);
	}

	/** Souls stored on {@code stack}; an absent component reads as zero. */
	public static int kills(ItemStack stack) {
		Integer value = stack.get(ModDataComponents.SOUL_VESSEL_KILLS.get());
		return value == null ? 0 : value;
	}

	/** Write the counter, clamped to [0, cap]; zero removes the component (fresh vessels stack). */
	public static void setKills(ItemStack stack, int kills) {
		int clamped = Math.max(0, Math.min(kills, cap()));
		if (clamped <= 0) {
			stack.remove(ModDataComponents.SOUL_VESSEL_KILLS.get());
		} else {
			stack.set(ModDataComponents.SOUL_VESSEL_KILLS.get(), clamped);
		}
	}

	/** The vessel's hard cap — the highest evolve threshold; beyond it a soul could never be spent. */
	public static int cap() {
		return Config.mobRepellerEvolveKillsHv;
	}

	/** Whether this vessel has room for another soul. */
	public static boolean hasRoom(ItemStack stack) {
		return kills(stack) < cap();
	}

	// --- tooltip: flavor, the counter, and the two jump thresholds ---

	// MOD-498 — Item#appendHoverText carries a vanilla soft-deprecation marker but is still the only hook
	// an item has for its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself
	// overrides it (DiscFragmentItem, HangingEntityItem, SmithingTemplateItem). A data-component
	// TooltipProvider could not produce these lines — they mix the stack's soul count with Config values.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.soul_vessel.flavor")
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.soul_vessel.souls",
				kills(stack), cap()).withStyle(ChatFormatting.AQUA));
		adder.accept(Component.translatable("item.alaindustrial.soul_vessel.thresholds",
				Config.mobRepellerEvolveKillsMv, Config.mobRepellerEvolveKillsHv)
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	// --- item bar: soul fill in the soul-fire teal ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return kills(stack) > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return (int) Math.min(MAX_BAR_WIDTH, (long) MAX_BAR_WIDTH * kills(stack) / Math.max(1, cap()));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x4FD8DE; // soul-fire teal
	}
}
