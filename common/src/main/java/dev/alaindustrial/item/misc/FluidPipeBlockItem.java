package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ReinforcedFluidPipeBlock;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The Fluid Pipe's block item and its tooltip (MOD-151), following the item pipe's layout: two short
 * plain-language lines always visible, the throughput number behind Shift.
 *
 * <p>The rate is read from {@link Config} rather than baked into the lang files, so a server that
 * retunes the pipe shows its own numbers instead of lying to the player.
 *
 * <p>Both grades share this item class (MOD-660). What tells them apart on the tooltip is the one rule
 * that tells them apart in the world: the reinforced pipe says it survives a reactor, and the plain one
 * warns, behind Shift, that it does not.
 */
public class FluidPipeBlockItem extends BlockItem {

	public FluidPipeBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	// MOD-498 — Item#appendHoverText carries a vanilla soft-deprecation marker but is still the only hook
	// an item has for its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself
	// overrides it (DiscFragmentItem, HangingEntityItem, SmithingTemplateItem). A data-component
	// TooltipProvider could not produce these lines — the rate is read from Config at render time.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.fluid_pipe.hint")
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.fluid_pipe.hint2")
				.withStyle(ChatFormatting.GRAY));
		boolean reinforced = getBlock() instanceof ReinforcedFluidPipeBlock;
		if (reinforced) {
			adder.accept(Component.translatable("item.alaindustrial.reinforced_fluid_pipe.hint")
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		if (!TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		int perTick = Math.max(1, Config.fluidPipeSegmentBuffer);
		// Buckets per second, to one decimal: 50 mB/t → 1.0 B/s. Players think in buckets.
		String bucketsPerSecond = String.format(java.util.Locale.ROOT, "%.1f", perTick * 20 / 1000.0D);
		adder.accept(Component.translatable("item.alaindustrial.fluid_pipe.tech.rate",
						perTick, bucketsPerSecond)
				.withStyle(ChatFormatting.DARK_GRAY));
		adder.accept(Component.translatable(reinforced
						? "item.alaindustrial.reinforced_fluid_pipe.tech.reactor"
						: "item.alaindustrial.fluid_pipe.tech.reactor")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
