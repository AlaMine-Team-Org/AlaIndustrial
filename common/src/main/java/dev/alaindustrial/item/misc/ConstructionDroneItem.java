package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.client.tooltip.TooltipWrap;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The tier-1 builder drone as an item (MOD-858, owner's round 1): crafted, put into an empty drone nest with a
 * right-click, taken out again with a sneaking right-click by an empty hand. Its battery rides on the stack as
 * {@link ModDataComponents#STORED_ENERGY}, so a drone taken out keeps its charge and brings it to the next nest.
 *
 * <p>Deliberately not a {@code PoweredItem}: the drone charges from the settlement core through its nest and from
 * nothing else (R94), so no battery box, charge pad or energy pack may fill it. The bar under the icon shows the
 * charge, the tooltip says where it charges.
 */
public class ConstructionDroneItem extends Item {

	/** The bar's colour: the mod's charge cyan. */
	private static final int BAR_COLOUR = 0x3FB6E0;

	public ConstructionDroneItem(Properties properties) {
		super(properties);
	}

	/** A drone with {@code charge} EU in its battery. */
	public static ItemStack withCharge(long charge) {
		ItemStack stack = new ItemStack(ModContent.CONSTRUCTION_DRONE.get());
		setCharge(stack, charge);
		return stack;
	}

	/** The EU in the drone's battery; none for a stack that never held any. */
	public static long charge(ItemStack stack) {
		Long stored = stack.get(ModDataComponents.STORED_ENERGY.get());
		return stored == null ? 0L : Math.max(0L, Math.min(DroneNestRules.batteryCapacity(), stored));
	}

	/** Writes the battery onto the stack; an empty battery leaves no component. */
	public static void setCharge(ItemStack stack, long charge) {
		long clamped = Math.max(0L, Math.min(DroneNestRules.batteryCapacity(), charge));
		if (clamped == 0L) {
			stack.remove(ModDataComponents.STORED_ENERGY.get());
		} else {
			stack.set(ModDataComponents.STORED_ENERGY.get(), clamped);
		}
	}

	/** The battery as a whole percent of {@code capacity}, 0..100. */
	public static int percent(long charge, long capacity) {
		return (int) Math.round(100.0 * Math.max(0L, Math.min(capacity, charge)) / Math.max(1L, capacity));
	}

	/**
	 * The battery's size as the client shows it: the server's knob through {@link ServerBalance} (MOD-761), never the
	 * player's own config file. The server-side rules keep {@link DroneNestRules#batteryCapacity()}.
	 */
	private static long shownCapacity() {
		return Math.max(1, ServerBalance.settlementDroneBattery());
	}

	/** The EU on the stack as the client shows it, clamped to {@link #shownCapacity()}. */
	private static long shownCharge(ItemStack stack) {
		Long stored = stack.get(ModDataComponents.STORED_ENERGY.get());
		return stored == null ? 0L : Math.max(0L, Math.min(shownCapacity(), stored));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return shownCharge(stack) < shownCapacity();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(MAX_BAR_WIDTH * (float) shownCharge(stack) / shownCapacity()), 0, MAX_BAR_WIDTH);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOUR;
	}

	// MOD-498: see TownHallPlanSheetItem.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.construction_drone.charge",
				percent(shownCharge(stack), shownCapacity())).withStyle(ChatFormatting.AQUA));
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.construction_drone.use")
				.withStyle(ChatFormatting.GRAY));
		TooltipWrap.add(adder, Component.translatable("tooltip.alaindustrial.construction_drone.charges")
				.withStyle(ChatFormatting.GRAY));
	}
}
