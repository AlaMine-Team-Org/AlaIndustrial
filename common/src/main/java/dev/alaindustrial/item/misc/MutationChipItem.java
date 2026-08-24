package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.IncubatorMode;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A mutation chip (MOD-118): the tooltip says which incubator mode the chip selects, with the
 * numbers behind Shift — the same two-layer convention the item pipe set (MOD-108). The figures are
 * read live from {@link Config} so a retuned server describes itself truthfully; the duplicate chip
 * quotes no chance at all, because every shipped duplicate recipe carries its own and the Config
 * default never applies to it.
 */
public class MutationChipItem extends Item {

	private final IncubatorMode mode;

	public MutationChipItem(Properties properties, IncubatorMode mode) {
		super(properties);
		this.mode = mode;
	}

	// MOD-498 — Item#appendHoverText is soft-deprecated by vanilla but is the ONLY hook an item has for
	// its own tooltip lines: ItemStack#addDetailsToTooltip calls it, and vanilla itself overrides it in
	// DiscFragmentItem, HangingEntityItem and SmithingTemplateItem. Data-component TooltipProviders cover
	// data-driven components, not lines derived per item from the chip's mode as these are.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		// Two short lines instead of one long one: tooltips are not wrapped by the game.
		adder.accept(Component.translatable(
				"item.alaindustrial.mutation_chip_" + mode.name().toLowerCase(Locale.ROOT) + ".hint")
				.withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("item.alaindustrial.mutation_chip.hint")
				.withStyle(ChatFormatting.GRAY));
		if (!TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		if (mode == IncubatorMode.DUPLICATE) {
			adder.accept(Component.translatable("item.alaindustrial.mutation_chip.tech.chance_varies")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else {
			long percent = Math.round(mode.baseChance() * 100.0);
			adder.accept(Component.translatable("item.alaindustrial.mutation_chip.tech.chance", percent)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		// Mirrors the block entity's own arithmetic so the numbers cannot drift from the machine.
		int euPerTick = Math.max(1, Math.round(Config.incubatorEuPerTick * Config.globalMachineSpeedMultiplier));
		int ticks = Config.scaledDuration(mode.baseDuration());
		String seconds = String.format("%.0f", ticks / 20.0);
		adder.accept(Component.translatable("item.alaindustrial.mutation_chip.tech.cycle",
				seconds, (long) euPerTick * ticks).withStyle(ChatFormatting.DARK_GRAY));
		adder.accept(Component.translatable("item.alaindustrial.mutation_chip.tech.miss")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
