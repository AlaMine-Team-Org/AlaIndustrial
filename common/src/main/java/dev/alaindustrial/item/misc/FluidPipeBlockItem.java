package dev.alaindustrial.item.misc;

import dev.alaindustrial.block.AdvancedFluidPipeBlock;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ReinforcedFluidPipeBlock;
import dev.alaindustrial.block.ReinforcedSteamPipeBlock;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.fluid.PipeFamily;
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
 * <p>The rate is read from the server's numbers ({@code ServerBalance}, MOD-761) rather than baked into the
 * lang files, so a server that retunes the pipe shows its own numbers instead of lying to the player.
 *
 * <p>Both grades share this item class (MOD-660). What tells them apart on the tooltip is the one rule
 * that tells them apart in the world: the reinforced pipe says it survives a reactor, and the plain one
 * warns, behind Shift, that it does not.
 *
 * <p>The advanced grade shares it too (MOD-675): its rate line quotes its own, larger segment, and one
 * extra line up front says what the grade is for.
 *
 * <p>The steam pipes share it too (MOD-662). Their first line says what they carry instead of the
 * fluid pipe's, and behind Shift each family names the other one's job, since a player who laid steam
 * through a fluid pipe before the split needs to hear it once.
 */
public class FluidPipeBlockItem extends BlockItem {

	public FluidPipeBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	// MOD-498 — Item#appendHoverText carries a vanilla soft-deprecation marker but is still the only hook
	// an item has for its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself
	// overrides it (DiscFragmentItem, HangingEntityItem, SmithingTemplateItem). A data-component
	// TooltipProvider could not produce these lines — the rate is read from ServerBalance at render time.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		boolean steam = getBlock() instanceof FluidPipeBlock pipe && pipe.family() == PipeFamily.STEAM;
		boolean advanced = getBlock() instanceof AdvancedFluidPipeBlock;
		if (advanced) {
			// MOD-675: the second grade's player already knows what a fluid pipe does — one line says what
			// this one adds; the rest waits behind Shift.
			adder.accept(Component.translatable("item.alaindustrial.fluid_pipe_advanced.hint")
					.withStyle(ChatFormatting.GOLD));
		} else {
			adder.accept(Component.translatable(steam
							? "item.alaindustrial.steam_pipe.hint"
							: "item.alaindustrial.fluid_pipe.hint")
					.withStyle(ChatFormatting.GRAY));
			adder.accept(Component.translatable("item.alaindustrial.fluid_pipe.hint2")
					.withStyle(ChatFormatting.GRAY));
		}
		boolean reinforced = getBlock() instanceof ReinforcedFluidPipeBlock
				|| getBlock() instanceof ReinforcedSteamPipeBlock;
		if (reinforced) {
			adder.accept(Component.translatable("item.alaindustrial.reinforced_fluid_pipe.hint")
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		if (!TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		// The grade's own segment (MOD-675) — read from the block, so each grade quotes its own knob.
		int perTick = getBlock() instanceof FluidPipeBlock pipe
				? pipe.segmentCapacity(ServerBalance.fluidPipeSegmentBuffer(),
						ServerBalance.fluidPipeAdvancedSegmentBuffer())
				: Math.max(1, ServerBalance.fluidPipeSegmentBuffer());
		// Buckets per second, to one decimal: 50 mB/t → 1.0 B/s. Players think in buckets.
		String bucketsPerSecond = String.format(java.util.Locale.ROOT, "%.1f", perTick * 20 / 1000.0D);
		adder.accept(Component.translatable("item.alaindustrial.fluid_pipe.tech.rate",
						perTick, bucketsPerSecond)
				.withStyle(ChatFormatting.DARK_GRAY));
		if (advanced) {
			// The advanced grade stops at what it adds: rate and the slowdown. Steam and the reactor are the
			// basic and reinforced pipes' lines to say.
			adder.accept(Component.translatable("item.alaindustrial.fluid_pipe_advanced.tech.slow")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		adder.accept(Component.translatable(steam
						? "item.alaindustrial.steam_pipe.tech.family"
						: "item.alaindustrial.fluid_pipe.tech.steam")
				.withStyle(ChatFormatting.DARK_GRAY));
		adder.accept(Component.translatable(reinforced
						? "item.alaindustrial.reinforced_fluid_pipe.tech.reactor"
						: "item.alaindustrial.fluid_pipe.tech.reactor")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
