package dev.alaindustrial.item;

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

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		FluidTankContents contents = stack.get(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (contents == null) {
			adder.accept(Component.translatable("tooltip.alaindustrial.fluid_tank.empty")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		adder.accept(Component.translatable("tooltip.alaindustrial.fluid_tank.contents",
						fluidName(contents), contents.amount(), dev.alaindustrial.Config.fluidTankCapacity)
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
		if (contents == null || dev.alaindustrial.Config.fluidTankCapacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH,
				MAX_BAR_WIDTH * contents.amount() / dev.alaindustrial.Config.fluidTankCapacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		FluidTankContents contents = stack.get(dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS.get());
		return contents == null ? 0 : FluidTankVisuals.fallbackColor(contents.fluid().value());
	}
}
