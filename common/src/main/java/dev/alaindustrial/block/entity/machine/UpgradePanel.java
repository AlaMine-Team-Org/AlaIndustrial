package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.upgrade.OverclockMath;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.SkillMachine;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The four upgrade slots at the tail of a machine's inventory (MOD-080) and what the chips in them do:
 * the mute chip, the statistics chip (MOD-125) and the overclocker (MOD-392).
 *
 * <p>One component of the machine base (MOD-712, BE-1). The slots themselves stay in the machine's
 * inventory — they are part of its save, at the indices the slot layout gives them — so the panel reads
 * them through the list it was handed. Every question it asks the machine back ({@code hasUpgradeSlots},
 * {@code getOwner}, {@code baseEuPerTick}, …) goes through the machine's own method, so a machine that
 * overrides one of them is heard exactly as before.
 */
public final class UpgradePanel {

	/** Upgrade slots appended to the tail of every GUI machine's inventory (MOD-080). */
	public static final int SLOT_COUNT = 4;

	private final MachineBlockEntity machine;
	private final List<ItemStack> items;
	private final int start;
	private final int batterySlot;

	/**
	 * @param items the machine's whole inventory
	 * @param start index of the first upgrade slot in {@code items}
	 * @param batterySlot index of the battery drawer slot, or -1 — never read as an upgrade slot
	 */
	public UpgradePanel(MachineBlockEntity machine, List<ItemStack> items, int start, int batterySlot) {
		this.machine = machine;
		this.items = items;
		this.start = start;
		this.batterySlot = batterySlot;
	}

	/** The stack in upgrade-block index {@code i} (0-based), or empty when there is no such upgrade slot. */
	public ItemStack stack(int i) {
		int idx = start + i;
		return i >= 0 && i < SLOT_COUNT && idx < items.size() && idx != batterySlot ? items.get(idx) : ItemStack.EMPTY;
	}

	/**
	 * Whether a statistics chip is fitted (MOD-125). Without one this block measures nothing at all: no
	 * counters advance, nothing is written to its save tag, and no packet is ever built for it.
	 *
	 * <p>That is the point of making it a chip rather than a free feature — telemetry is something the
	 * player chooses to install where it matters, and a base full of un-instrumented machines costs
	 * exactly what it did before the chip existed.
	 */
	public boolean hasStatsChip() {
		// MOD-483 Free Telemetry: the panel reads the same way, the chip is simply no longer the
		// only way to switch it on — which frees the upgrade slot it used to occupy. Asked before the
		// panel exists on purpose (a panel-less block with the skill measures too), and kept in this order.
		if (SkillMachine.statsWithoutChip(machine.getLevel(), machine.getOwner())) {
			return true;
		}
		return anySlotHolds(ModContent.STATS_CHIP.get());
	}

	/**
	 * Whether a mute chip sits in ANY upgrade slot. Single source of truth for silencing the machine: the
	 * client hum manager and any future machine sound MUST honor it. Safe to read client-side —
	 * upgrade-slot contents sync with the block entity, so no extra networking is needed.
	 *
	 * <p>Scans every slot rather than only the active one: since MOD-392 all four slots accept upgrades,
	 * so the player may park the mute chip anywhere in the panel.
	 */
	public boolean isMuted() {
		return anySlotHolds(ModContent.MUTE_CHIP.get());
	}

	private boolean anySlotHolds(net.minecraft.world.item.Item chip) {
		if (!machine.hasUpgradeSlots()) {
			return false;
		}
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (machine.getUpgradeStack(i).is(chip)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * How many overclocker chips the machine can actually use.
	 *
	 * <p>Not a flat constant: a machine may hold no more chips than its own voltage tier can feed. Beyond
	 * that point the block would demand more EU/t than its tier lets it accept in a tick, so the extra chips
	 * could never pay off — the machine would simply starve while the player believes they bought speed.
	 * Derived by stepping the factor instead of a logarithm, so a retuned {@link Config#overclockerEuFactor}
	 * cannot drift the two apart through rounding.
	 */
	public int overclockerCap() {
		if (!machine.supportsOverclock() || !machine.hasUpgradeSlots()) {
			return 0;
		}
		int cap = OverclockMath.cap(machine.baseEuPerTick(), machine.getTier().maxVoltage(),
				Config.overclockerEuFactor, Config.overclockerMaxPerMachine);
		// MOD-483 Overclock Headroom: one chip beyond what the tier would feed. It pays for itself — the
		// fourth chip costs 6.5x the energy per operation — so no extra balancing is needed here.
		return SkillMachine.overclockerCap(cap, machine.getLevel(), machine.getOwner());
	}

	/**
	 * Steps of overclocking actually in effect: the tier of the chip in the panel, clamped to
	 * {@link #overclockerCap()}.
	 *
	 * <p>The tier rides on the item (three separate chips) rather than on a stack size, so "how much speed"
	 * is a property of what the player crafted, not of how many copies they crammed into a slot. The clamp
	 * still applies: a machine whose voltage tier cannot feed the chip runs at what it can.
	 */
	public int overclockerCount() {
		int cap = machine.overclockerCap();
		if (cap <= 0) {
			return 0;
		}
		int tier = 0;
		for (int i = 0; i < SLOT_COUNT; i++) {
			tier = Math.max(tier, OverclockerChipItem.tierOf(machine.getUpgradeStack(i)));
		}
		return Math.min(tier, cap);
	}

	/** The EU/t the machine actually draws: see {@link MachineBlockEntity#effectiveEuPerTick}. */
	public int effectiveEuPerTick(int baseEuPerTick) {
		return OverclockMath.euPerTick(baseEuPerTick, Config.globalMachineSpeedMultiplier,
				Config.overclockerEuFactor, machine.overclockerCount());
	}

	/**
	 * The operation length in ticks: see {@link MachineBlockEntity#effectiveDuration}.
	 *
	 * @param scaledTicks the duration AFTER the global speed multiplier. The machine base applies that
	 *     itself: the bytecode rule {@code machinesUseOverclockHelpers} lets no other class of the package
	 *     call the static {@code Config} shortcut outside a constructor, and this one is no exception.
	 */
	public int effectiveDuration(int scaledTicks) {
		int ticks = OverclockMath.duration(scaledTicks, Config.overclockerSpeedFactor, machine.overclockerCount());
		// MOD-483 Tuned Drive / Fine Tuning — applied last, on top of the chip, and only
		// while the owner is in the world.
		return SkillMachine.duration(ticks, machine.getLevel(), machine.getOwner());
	}
}
