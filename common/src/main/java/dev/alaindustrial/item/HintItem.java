package dev.alaindustrial.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A plain item that shows one or more gray hint lines under its name. The hint translation keys are
 * passed at construction (rather than derived) so the item stays loader-neutral and free of any
 * name-mangling assumptions; passing several keys wraps a long hint onto tidy short lines. Used for
 * the upgrade chips (MOD-080), whose effect isn't obvious from the name alone.
 */
public class HintItem extends Item {

	private final String[] hintKeys;

	public HintItem(Properties properties, String... hintKeys) {
		super(properties);
		this.hintKeys = hintKeys;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		for (String key : hintKeys) {
			adder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}
}
