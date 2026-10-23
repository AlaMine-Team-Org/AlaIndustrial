package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.tooltip.TooltipWrap;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Restoration plan (MOD-859, R83 of MOD-824): what a settlement's owner gives the construction drone (MOD-858). It
 * points at the intact template of one building of one style through its {@link RestorationPlan} component and holds
 * nothing else. A stack without the component — the creative tab's — is an empty plan no drone takes. Never stacks:
 * plans for different buildings look alike.
 */
public class RestorationPlanItem extends Item {

	public RestorationPlanItem(Properties properties) {
		super(properties);
	}

	/** What this plan points at, or {@code null} for an empty plan. */
	public static RestorationPlan planOf(ItemStack stack) {
		return stack.get(ModDataComponents.RESTORATION_PLAN.get());
	}

	/** A plan for {@code plan}. */
	public static ItemStack of(RestorationPlan plan) {
		ItemStack stack = new ItemStack(ModContent.RESTORATION_PLAN.get());
		stack.set(ModDataComponents.RESTORATION_PLAN.get(), plan);
		return stack;
	}

	// MOD-498: see TownHallPlanSheetItem.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		RestorationPlan plan = planOf(stack);
		if (plan == null) {
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.restoration_plan.empty")
					.withStyle(ChatFormatting.GRAY));
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.restoration_plan.empty_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		TooltipWrap.add(adder, Component.translatableWithFallback(
				"tooltip.alaindustrial.restoration_plan.building." + plan.building(), plan.building())
				.withStyle(ChatFormatting.AQUA));
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.plan.style",
				TownHallPlanSheetItem.shortStyleName(plan.style())).withStyle(ChatFormatting.GRAY));
		for (String key : useKeys()) {
			TooltipWrap.add(adder, Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** The tooltip's lines on what to do with the plan. */
	protected List<String> useKeys() {
		return List.of("tooltip.alaindustrial.restoration_plan.use",
				"tooltip.alaindustrial.restoration_plan.use_where");
	}
}
