package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The Item Pipe's block item, carrying its tooltip (MOD-108).
 *
 * <p>Two layers, the convention players already expect: a short plain-language description always
 * visible, and the throughput number behind Shift. Kept deliberately terse — Minecraft does not wrap
 * tooltips, so a long line runs off the screen; the first draft did exactly that and had to be split
 * into two short lines. "Passive / needs no EU" and the list of face modes were dropped as noise: the
 * wrench announces the mode it sets, and no pipe in the mod uses energy.
 *
 * <p>The rate is read from {@link Config} rather than written into the lang files, so a server that
 * retunes the pipe shows its own values instead of lying to the player.
 */
public class ItemPipeBlockItem extends BlockItem {

	public ItemPipeBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		// Two short lines instead of one long one: tooltips are not wrapped by the game.
		adder.accept(Component.translatable("item.alaindustrial.item_pipe.hint")
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.item_pipe.hint2")
				.withStyle(ChatFormatting.GRAY));
		if (!TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		int batch = Math.max(1, Config.itemPipeItemsPerTransfer);
		int interval = Math.max(1, Config.itemPipeTransferIntervalTicks);
		// Throughput as items per second, one decimal: 2 items / 20 ticks reads as "2.0/s". Computed from
		// the live config so a retuned server is described truthfully.
		String perSecond = String.format("%.1f", batch * 20.0 / interval);
		adder.accept(Component.translatable("item.alaindustrial.item_pipe.tech.rate", batch, interval, perSecond)
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
