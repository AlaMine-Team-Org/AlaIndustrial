package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.machine.ComponentRepair;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A generator's wearing component — the wind mill's rotor and the water mill's wheel, in all three
 * grades (MOD-189 for the wear, MOD-385 for the ladder). Registered through
 * {@code ContentManifest.durableComponent}, which supplies the grade's {@code durability(...)}.
 *
 * <p>Plain {@code new Item(...)} until MOD-384. It grew a class of its own for one reason: the repair
 * bench writes a repair counter onto the stack, and the durability bar cannot show it. A component
 * repaired three times looks exactly like a fresh one whose ceiling happens to be lower — the player
 * would have no way to tell how many repairs are left before the bench starts refusing. So the count
 * goes in the tooltip, and only once it is non-zero: a freshly crafted rotor keeps a clean tooltip and
 * stays component-identical to every other freshly crafted rotor.
 *
 * @see dev.alaindustrial.core.machine.ComponentRepair
 */
public class DurableComponentItem extends Item {

	public DurableComponentItem(Properties properties) {
		super(properties);
	}

	/** Repairs already performed on {@code stack}; absent component = never repaired. */
	public static int repairs(ItemStack stack) {
		Integer value = stack.get(ModDataComponents.REPAIR_COUNT.get());
		return value == null ? 0 : value;
	}

	/**
	 * Record that {@code stack} has now been repaired {@code count} times. A count of zero REMOVES the
	 * component rather than storing 0, so a never-repaired component carries no component patch at all
	 * (the same "absent is the default" contract as {@code SoulVesselItem.setKills} and
	 * {@code MAGNET_ENABLED}).
	 */
	public static void setRepairs(ItemStack stack, int count) {
		if (count <= 0) {
			stack.remove(ModDataComponents.REPAIR_COUNT.get());
		} else {
			stack.set(ModDataComponents.REPAIR_COUNT.get(), count);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		int done = repairs(stack);
		if (done <= 0) {
			return;
		}
		// Shown as "used / allowed" rather than "remaining" so the line stays informative at the limit
		// (4/4 reads as spent; "0 left" reads as an error). The limit is read live — an operator who
		// raises it mid-world must see the new headroom on components already in a chest.
		adder.accept(Component.translatable("item.alaindustrial.component.repairs",
						done, Math.max(done, ComponentRepair.maxRepairs(Config.repairBenchMaxDamageDecayPercent)))
				.withStyle(ChatFormatting.GRAY));
	}
}
