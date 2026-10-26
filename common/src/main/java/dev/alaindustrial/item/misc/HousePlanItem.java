package dev.alaindustrial.item.misc;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * House plan (MOD-862, R99 of MOD-824): the plan of one of the four capsule house types of one style, found whole in
 * the ruin of that house, or crafted. It points at the house's intact template through the same
 * {@link RestorationPlan} component a hall plan carries, so the dispatcher and the drones read it the same way; it is
 * an item of its own for its own look. The town learns it once — put into the dispatcher or right-clicked onto the
 * core — and the plan is used up; every house of its type may then be ordered (round 2 of 0.1.219, Q4).
 * The word «blueprint» is never used: it is taken (R83).
 *
 * <p>The warehouse plan (MOD-892, R154) is this item too, naming the warehouse's intact template: learned the same
 * ways, found in the warehouse ruin or crafted, and named «Warehouse Plan» by {@link #planName}.
 */
public class HousePlanItem extends RestorationPlanItem {

	public HousePlanItem(Properties properties) {
		super(properties);
	}

	/** A house plan for {@code plan}. */
	public static ItemStack of(RestorationPlan plan) {
		ItemStack stack = new ItemStack(ModContent.HOUSE_PLAN.get());
		stack.set(ModDataComponents.RESTORATION_PLAN.get(), plan);
		return stack;
	}

	/** A house plan is learned, not kept in a slot (round 2 of 0.1.219, Q4). */
	@Override
	protected List<String> useKeys() {
		return List.of("tooltip.alaindustrial.house_plan.use");
	}

	/** The name of the warehouse plan (MOD-892). */
	public static final String WAREHOUSE_NAME = "item.alaindustrial.house_plan.warehouse";

	@Override
	public Component getName(ItemStack stack) {
		RestorationPlan plan = planOf(stack);
		return plan == null || !PlanSheetRules.learned(plan.building()) ? super.getName(stack)
				: planName(plan.building());
	}

	/**
	 * The name a plan of {@code building} reads: «House Plan (type 3)» for a house, «Warehouse Plan» for the
	 * warehouse, the plain «House Plan» for anything else.
	 */
	public static Component planName(String building) {
		int type = PlanSheetRules.houseType(building);
		if (type > 0) {
			return Component.translatable("item.alaindustrial.house_plan.typed", type);
		}
		return Component.translatable(PlanSheetRules.WAREHOUSE.equals(building) ? WAREHOUSE_NAME
				: "item.alaindustrial.house_plan");
	}
}
