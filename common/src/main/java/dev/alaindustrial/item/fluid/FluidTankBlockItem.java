package dev.alaindustrial.item.fluid;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Item form of the portable fluid tank, including its carried-fluid tooltip (MOD-111). */
public final class FluidTankBlockItem extends BlockItem {
	public FluidTankBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	// MOD-498 — soft-deprecated by vanilla, but still the only hook an item has for tooltip lines it
	// computes itself: ItemStack#addDetailsToTooltip calls it, and vanilla overrides it in DiscFragmentItem,
	// HangingEntityItem and SmithingTemplateItem. A data-component TooltipProvider covers data-driven
	// components, not text derived per stack like the carried fluid and its amount below.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		FluidTankContents contents = stack.get(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (contents == null) {
			adder.accept(Component.translatable("tooltip.alaindustrial.fluid_tank.empty_capacity",
							dev.alaindustrial.block.FluidTankBlock.tierOf(getBlock()).capacity())
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		// MOD-612 — the capacity comes from THIS item's block, never from the basic tank's knob. The
		// mod has shipped a tier-2 tooltip describing tier 1 twice (advanced pipe 0.1.149, advanced
		// magnet 0.1.148), both times because a number was written at the call site.
		adder.accept(Component.translatable("tooltip.alaindustrial.fluid_tank.contents",
						fluidName(contents), contents.amount(),
						dev.alaindustrial.block.FluidTankBlock.tierOf(getBlock()).capacity())
				.withStyle(ChatFormatting.AQUA));
	}

	private static Component fluidName(FluidTankContents contents) {
		var state = contents.fluid().value().defaultFluidState().createLegacyBlock();
		return state.isAir()
				? Component.translatable("item.alaindustrial.filled_vacuum_capsule.fluid_unknown")
				: state.getBlock().getName();
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		FluidTankContents contents = stack.get(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
		// MOD-612 — the bar measures against THIS grade's capacity: on the advanced tank the basic
		// knob would draw a full bar at half full.
		int capacity = dev.alaindustrial.block.FluidTankBlock.tierOf(getBlock()).capacity();
		if (contents == null || capacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH, MAX_BAR_WIDTH * contents.amount() / capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		FluidTankContents contents = stack.get(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
		return contents == null ? 0 : FluidTankVisuals.fallbackColor(contents.fluid().value());
	}
}
