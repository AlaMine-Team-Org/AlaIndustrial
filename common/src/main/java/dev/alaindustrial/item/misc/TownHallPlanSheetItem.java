package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.tooltip.TooltipWrap;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.List;
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
	 * Whether {@code stack} goes into the dispatcher's sheet slot {@code slot}, 0-based: sheet N into slot N only
	 * (owner's round 1 of 0.1.219), so an empty slot shows which sheet is still missing; a blank fits none.
	 */
	public static boolean fitsSheetSlot(ItemStack stack, int slot) {
		PlanSheet sheet = stack.is(ModContent.TOWN_HALL_PLAN_SHEET.get()) ? sheetOf(stack) : null;
		return sheet != null && PlanSheetRules.fitsSlot(slot, sheet.sheet());
	}

	/**
	 * Puts every sheet in the {@value PlanSheetRules#SHEETS} sheet slots of {@code items} from {@code first} on into
	 * its own slot ({@link PlanSheetRules#homeSlots}), for a save written before sheet N went into slot N only. A
	 * stack with no slot of its own left — a blank, a second stack of one number — stays in a free sheet slot for the
	 * player to take out; nothing is dropped.
	 */
	public static void sortIntoOwnSlots(List<ItemStack> items, int first) {
		int[] numbers = new int[PlanSheetRules.SHEETS];
		ItemStack[] stacks = new ItemStack[PlanSheetRules.SHEETS];
		for (int i = 0; i < stacks.length; i++) {
			stacks[i] = items.get(first + i);
			PlanSheet sheet = sheetOf(stacks[i]);
			numbers[i] = stacks[i].isEmpty() ? -1 : sheet == null ? 0 : sheet.sheet();
			items.set(first + i, ItemStack.EMPTY);
		}
		int[] target = PlanSheetRules.homeSlots(numbers);
		for (int i = 0; i < stacks.length; i++) {
			if (target[i] >= 0) {
				items.set(first + target[i], stacks[i]);
			}
		}
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
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.blank")
					.withStyle(ChatFormatting.GRAY));
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.blank_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.plan.style", shortStyleName(sheet.style()))
				.withStyle(ChatFormatting.AQUA));
		TooltipWrap.add(adder, Component.translatable(whereKey(sheet.sheet())).withStyle(ChatFormatting.GRAY));
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.assemble")
				.withStyle(ChatFormatting.DARK_GRAY));
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.assemble_where")
				.withStyle(ChatFormatting.DARK_GRAY));
		// The recipe is a detail (owner, 2026-10-21): under [SHIFT], like the other items' details.
		if (TooltipKeys.shiftDown()) {
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.town_hall_plan_sheet.craftable")
					.withStyle(ChatFormatting.GRAY));
		} else {
			TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
