package dev.alaindustrial.item.misc;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Town hall plan sheet (k/3) (MOD-859, R92 of MOD-824): one of the three sheets a work town's hall restoration plan is
 * put together from. One item for every sheet and style; which sheet it is lives in its {@link PlanSheet} component,
 * which the town's loot tables write — and the six crafting recipes, one per sheet and built-in style, for a sheet
 * that was lost or never found. A stack without the component — the creative tab's — is a blank sheet that
 * makes no plan.
 */
public class TownHallPlanSheetItem extends Item {

	/** Sheets of one style and number stack; a chest of found sheets stays tidy. */
	public static final int STACK_SIZE = 16;

	public TownHallPlanSheetItem(Properties properties) {
		super(properties);
	}

	/** The sheet this stack is, or {@code null} for a blank. */
	public static PlanSheet sheetOf(ItemStack stack) {
		return stack.get(ModDataComponents.PLAN_SHEET.get());
	}

	/** A sheet {@code sheet} of {@code style}, as the loot tables hand it out. */
	public static ItemStack of(Identifier style, int sheet) {
		ItemStack stack = new ItemStack(ModContent.TOWN_HALL_PLAN_SHEET.get());
		stack.set(ModDataComponents.PLAN_SHEET.get(), new PlanSheet(style, sheet));
		return stack;
	}

	/**
	 * The name of a style for a player: its translation key {@code settlement.<ns>.style.<path>} (R47), falling back
	 * to the id for a style from a datapack that brings no name. The client has no style registry to read the name
	 * from, so the key is derived from the id the same way the built-in styles name theirs.
	 */
	public static Component styleName(Identifier style) {
		return Component.translatableWithFallback(
				"settlement." + style.getNamespace() + ".style." + style.getPath(), style.toString());
	}

	/**
	 * The short name of a style for a tooltip line ("plains"): its key {@code settlement.<ns>.style.<path>.short},
	 * falling back to {@link #styleName} for a style from a datapack that brings no short name.
	 */
	public static Component shortStyleName(Identifier style) {
		String key = "settlement." + style.getNamespace() + ".style." + style.getPath() + ".short";
		return Language.getInstance().has(key) ? Component.translatable(key) : styleName(style);
	}

	/** The tooltip key naming where sheet {@code sheet} lies, 1..{@value PlanSheetRules#SHEETS}. */
	public static String whereKey(int sheet) {
		return "tooltip.alaindustrial.town_hall_plan_sheet.where." + sheet;
	}

	@Override
	public Component getName(ItemStack stack) {
		PlanSheet sheet = sheetOf(stack);
		return sheet == null ? super.getName(stack)
				: Component.translatable("item.alaindustrial.town_hall_plan_sheet.numbered", sheet.sheet(),
						PlanSheetRules.SHEETS);
	}

	// MOD-498: Item#appendHoverText is soft-deprecated by Mojang ("internal"), but it is the only hook an item has
	// for its own lines computed per stack (see AssemblyBlueprintItem).
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		PlanSheet sheet = sheetOf(stack);
		// Short lines, one fact each: a tooltip never wraps by itself, so a long sentence runs across the screen.
		if (sheet == null) {
			adder.accept(Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.blank")
					.withStyle(ChatFormatting.GRAY));
			adder.accept(Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.blank_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		adder.accept(Component.translatable("tooltip.alaindustrial.plan.style", shortStyleName(sheet.style()))
				.withStyle(ChatFormatting.AQUA));
		adder.accept(Component.translatable(whereKey(sheet.sheet())).withStyle(ChatFormatting.GRAY));
		adder.accept(Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.assemble")
				.withStyle(ChatFormatting.DARK_GRAY));
		adder.accept(Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.assemble_where")
				.withStyle(ChatFormatting.DARK_GRAY));
		// The recipe is a detail (owner, 2026-10-21): under [SHIFT], like the other items' details.
		if (TooltipKeys.shiftDown()) {
			adder.accept(Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.craftable")
					.withStyle(ChatFormatting.GRAY));
		} else {
			adder.accept(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
