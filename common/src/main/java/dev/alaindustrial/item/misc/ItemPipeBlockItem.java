package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.core.item.PipeTier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The Item Pipe's block item, carrying its tooltip (MOD-108) — for BOTH grades (MOD-581).
 *
 * <p>Two layers, the convention players already expect: a short plain-language description always
 * visible, and the throughput number behind Shift. Kept deliberately terse — Minecraft does not wrap
 * tooltips, so a long line runs off the screen; the first draft did exactly that and had to be split
 * into two short lines. "Passive / needs no EU" and the list of face modes were dropped as noise: the
 * wrench announces the mode it sets, and no pipe in the mod uses energy.
 *
 * <p>The rate is read from the item's own {@link PipeTier} rather than written into the lang files, so
 * a server that retunes a pipe shows its own values instead of lying to the player.
 */
public class ItemPipeBlockItem extends BlockItem {

	public ItemPipeBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	// MOD-498 — soft-deprecated by vanilla, but still the only hook an item has for tooltip lines it
	// computes itself: ItemStack#addDetailsToTooltip calls it, and vanilla overrides it in DiscFragmentItem,
	// HangingEntityItem and SmithingTemplateItem. A data-component TooltipProvider cannot produce these
	// lines — the throughput below is read from the live Config, not from a component on the stack.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		String base = langBase();
		// Two short lines instead of one long one: tooltips are not wrapped by the game.
		adder.accept(Component.translatable(base + ".hint").withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable(base + ".hint2").withStyle(ChatFormatting.GRAY));
		if (!TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		int batch = Math.max(1, tier().itemsPerTransfer());
		int interval = Math.max(1, Config.itemPipeTransferIntervalTicks);
		// Throughput as items per second, one decimal: 2 items / 20 ticks reads as "2.0/s". Computed from
		// the live config so a retuned server is described truthfully.
		String perSecond = String.format("%.1f", batch * 20.0 / interval);
		adder.accept(Component.translatable(base + ".tech.rate", batch, interval, perSecond)
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * The grade THIS item places, taken from its own block.
	 *
	 * <p>Not {@code Config.itemPipeItemsPerTransfer}: that knob is the BASIC grade's, and reading it
	 * here is what made the advanced pipe advertise 2 items per second while it moved 4 (MOD-581, found
	 * in play). The block is the one place the grade cannot be lost — {@link ItemPipeBlock#tier()}
	 * explains why it is a type rather than a field.
	 */
	private PipeTier tier() {
		return getBlock() instanceof ItemPipeBlock pipe ? pipe.tier() : PipeTier.BASIC;
	}

	/**
	 * The lang prefix of THIS item, derived from its registry id and never spelled out.
	 *
	 * <p>A literal {@code "item.alaindustrial.item_pipe"} here gave both grades the basic pipe's text —
	 * the same defect the advanced magnet shipped with (MOD-580). The prefix is {@code item.} while
	 * {@link #getDescriptionId()} would return {@code block.}: these keys are authored under the item
	 * namespace, as they are for the fluid pipe, so the id is built rather than borrowed.
	 */
	private String langBase() {
		Identifier id = BuiltInRegistries.ITEM.getKey(this);
		return "item." + id.getNamespace() + "." + id.getPath();
	}
}
