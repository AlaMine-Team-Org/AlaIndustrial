package dev.alaindustrial.item.energy;

import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The hover tooltip of a powered tool or wearable (MOD-716, ADR-040), declared by the item through
 * {@link PoweredItem#toolTooltip()}: what it is for, any state lines the stack carries, and its charge — a gold
 * {@code <eu> / <capacity>} line, or a red "depleted" line once {@link #depleted} says the item can no longer
 * work. Shown the same with or without [SHIFT]: the charge is the one thing a player checks on a powered tool.
 *
 * <p>Replaces seven hand-written copies of the same shape in the client's {@code MachineTooltips} (drill,
 * chainsaw, shovel, hoe, saber, bow, jetpack) and the energy pack's eighth.
 *
 * @param usageKey     translation key of the first line, grey
 * @param usageArgs    its arguments, read live (the per-use EU cost, through {@code ServerBalance})
 * @param beforeCharge state lines between the usage line and the charge line; a function returns null when
 *                     the stack has no such state (a drill without the column bore)
 * @param chargeKey    translation key of the gold charge line, arguments {@code (eu, capacity)}
 * @param depletedKey  translation key of the red line shown instead of the charge
 * @param depleted     when the red line replaces the charge line
 * @param afterCharge  state lines after the charge line
 */
public record PoweredToolTooltip(String usageKey, List<IntSupplier> usageArgs,
		List<Function<ItemStack, MachineTooltipSpec.@Nullable Line>> beforeCharge, String chargeKey,
		String depletedKey, Predicate<ItemStack> depleted,
		List<Function<ItemStack, MachineTooltipSpec.@Nullable Line>> afterCharge) {

	public PoweredToolTooltip {
		usageArgs = List.copyOf(usageArgs);
		beforeCharge = List.copyOf(beforeCharge);
		afterCharge = List.copyOf(afterCharge);
	}

	/** The usual "depleted" rule: no EU left at all. */
	public static boolean empty(ItemStack stack) {
		return ItemEnergy.get(stack) <= 0;
	}

	/**
	 * The common shape: one usage line, the charge, nothing else. {@code key} names the item's tooltip keys:
	 * {@code tooltip.alaindustrial.<key>.usage / .charge / .depleted}.
	 */
	public static PoweredToolTooltip of(String key, List<IntSupplier> usageArgs) {
		String prefix = "tooltip.alaindustrial." + key;
		return new PoweredToolTooltip(prefix + ".usage", usageArgs, List.of(), prefix + ".charge",
				prefix + ".depleted", PoweredToolTooltip::empty, List.of());
	}

	/** This tooltip with {@code line} added before the charge line. */
	public PoweredToolTooltip withBeforeCharge(Function<ItemStack, MachineTooltipSpec.@Nullable Line> line) {
		return new PoweredToolTooltip(usageKey, usageArgs, append(beforeCharge, line), chargeKey, depletedKey,
				depleted, afterCharge);
	}

	/** This tooltip with {@code line} added after the charge line. */
	public PoweredToolTooltip withAfterCharge(Function<ItemStack, MachineTooltipSpec.@Nullable Line> line) {
		return new PoweredToolTooltip(usageKey, usageArgs, beforeCharge, chargeKey, depletedKey, depleted,
				append(afterCharge, line));
	}

	/** This tooltip with another usage key (a tier that names its own reference tool). */
	public PoweredToolTooltip withUsageKey(String key) {
		return new PoweredToolTooltip(key, usageArgs, beforeCharge, chargeKey, depletedKey, depleted, afterCharge);
	}

	/** This tooltip with another "depleted" rule (a saber below one hit's worth, a bow that shows uncharged). */
	public PoweredToolTooltip withDepleted(Predicate<ItemStack> rule) {
		return new PoweredToolTooltip(usageKey, usageArgs, beforeCharge, chargeKey, depletedKey, rule, afterCharge);
	}

	/** The usage line's arguments as the line shows them now. */
	public Object[] usageValues() {
		Object[] values = new Object[usageArgs.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = usageArgs.get(i).getAsInt();
		}
		return values;
	}

	/**
	 * A two-state line: {@code onKey} in {@code onTone} while {@code state} holds, {@code offKey} in grey
	 * otherwise. Shown in both states on purpose — with the state off nothing else in the UI says the item has a
	 * mode at all.
	 */
	public static Function<ItemStack, MachineTooltipSpec.@Nullable Line> toggle(Predicate<ItemStack> state,
			String onKey, MachineTooltipSpec.Tone onTone, String offKey) {
		return stack -> state.test(stack)
				? MachineTooltipSpec.text(onKey, onTone)
				: MachineTooltipSpec.text(offKey, MachineTooltipSpec.Tone.GRAY);
	}

	private static <T> List<T> append(List<T> list, T element) {
		List<T> out = new java.util.ArrayList<>(list);
		out.add(element);
		return out;
	}
}
