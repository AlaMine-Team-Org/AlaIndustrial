package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Tier evolution of a machine (MOD-211, generalised in MOD-278): the evolution counter and the chip it
 * belongs to, saved under {@code EvolveProgress} and {@code EvolveChip}, and the swap of a block for its
 * grown branch.
 *
 * <p>Four machines evolve — the solar panel, the daylight solar panel, the wind mill and the mob
 * repeller — and they call this class by name (MOD-712, BE-1); the machine base no longer carries code
 * that thirty-odd other machines never run.
 */
public final class EvolutionHelper {

	/** No chip is earning the evolution counter right now. */
	public static final int EVOLVE_CHIP_NONE = 0;
	/** The counter belongs to a day alignment chip. */
	public static final int EVOLVE_CHIP_DAY = 1;
	/** The counter belongs to a night alignment chip. */
	public static final int EVOLVE_CHIP_NIGHT = 2;
	/**
	 * The counter belongs to a resonance chip (MOD-602) — the one chip that serves BOTH branches on the
	 * second rung, since by then the panel already knows which branch it is.
	 */
	public static final int EVOLVE_CHIP_RESONANCE = 3;

	private EvolutionHelper() {}

	/**
	 * Write the evolution counter under the canonical NBT key. Every evolvable generator persists its chip
	 * progress identically; one literal keeps the save format consistent and makes a future rename a
	 * one-line change. The key stays {@code "EvolveProgress"} for existing single-player saves.
	 */
	public static void saveEvolve(ValueOutput output, int evolveProgress) {
		output.putInt("EvolveProgress", evolveProgress);
	}

	/** Read the counter written by {@link #saveEvolve}; {@code 0} when absent (an older save, a new block). */
	public static int loadEvolve(ValueInput input) {
		return input.getIntOr("EvolveProgress", 0);
	}

	/**
	 * Which chip the evolution counter belongs to, persisted next to the counter itself.
	 *
	 * <p>Without it the counter is anonymous, and an anonymous counter cannot be abandoned: a player who
	 * pulls the chip out keeps the progress it earned, and one who swaps a day chip for a night chip
	 * carries progress across the fork the chip is supposed to decide. The slot cannot answer this on its
	 * own — a swap performed in a single click never leaves it empty for a tick to see.
	 */
	public static void saveEvolveChip(ValueOutput output, int evolveChip) {
		output.putInt("EvolveChip", evolveChip);
	}

	/**
	 * Read the chip marker written by {@link #saveEvolveChip}. Absent in saves written before the marker
	 * existed; {@link #EVOLVE_CHIP_NONE} there means the first tick simply re-attributes the counter to
	 * whatever chip is in the slot, which is exactly the old behaviour for that one tick and costs an
	 * existing player nothing.
	 */
	public static int loadEvolveChip(ValueInput input) {
		return input.getIntOr("EvolveChip", EVOLVE_CHIP_NONE);
	}

	/** Classify the stack in an evolution chip slot into one of the {@code EVOLVE_CHIP_*} codes. */
	public static int evolveChipOf(ItemStack chip) {
		if (chip.is(ModContent.ALIGNMENT_CHIP_DAY.get())) {
			return EVOLVE_CHIP_DAY;
		}
		if (chip.is(ModContent.ALIGNMENT_CHIP_NIGHT.get())) {
			return EVOLVE_CHIP_NIGHT;
		}
		return chip.is(ModContent.RESONANCE_CHIP.get()) ? EVOLVE_CHIP_RESONANCE : EVOLVE_CHIP_NONE;
	}

	/**
	 * Replace {@code machine} with its evolved branch — the solar panel and wind mill evolution paths
	 * (MOD-211) and the mob repeller tier ladder (MOD-278). Carries stored EU (clamped to the evolved
	 * block's capacity) and the owner, preserves the FACING blockstate when both the old and new blocks
	 * have one, and consumes the trigger slot: the caller passes {@code slotOverrides} that does both jobs
	 * specific to its slot layout — e.g. clearing {@code CHIP_SLOT} on both, and snapshotting the wind-mill
	 * rotor so it can be re-placed on the evolved mill.
	 *
	 * @param items the machine's inventory, whose override slots are emptied in place before the swap
	 * @param target the block to evolve into (e.g. {@code ModContent.DAYLIGHT_SOLAR_PANEL.get()})
	 * @param slotOverrides slots to carry into the evolved block (slot index → stack); empty for the panel
	 */
	public static void evolveInto(MachineBlockEntity machine, List<ItemStack> items, Level level, BlockPos pos,
			Block target, Map<Integer, ItemStack> slotOverrides) {
		long saved = machine.getEnergyStorage().getAmount();
		// Carry ownership across the evolution. The evolved block is created via setBlockAndUpdate — NOT a
		// player placement — so setPlacedBy never runs and the new block entity would default to a null
		// owner. Without this, an evolved T2 generator (wind mills, solar panels) attributes none of its
		// production to the player: it silently vanished from the profile's per-generator breakdown (MOD-133).
		UUID savedOwner = machine.getOwner();
		String savedOwnerName = machine.getOwnerName();
		for (Map.Entry<Integer, ItemStack> entry : slotOverrides.entrySet()) {
			// Caller has already snapshotted these into the overrides map; clear the source slot so the
			// block's inventory reads empty before the swap (the chip slot is always cleared here too —
			// see callers).
			items.set(entry.getKey(), ItemStack.EMPTY);
		}
		BlockState oldState = machine.getBlockState();
		BlockState newState = target.defaultBlockState();
		// Preserve FACING when both old and new blocks have it (wind mill family); the solar panels
		// have no FACING, so this is a no-op for them.
		if (oldState.hasProperty(HorizontalMachineBlock.FACING)
				&& newState.hasProperty(HorizontalMachineBlock.FACING)) {
			newState = newState.setValue(HorizontalMachineBlock.FACING,
					oldState.getValue(HorizontalMachineBlock.FACING));
		}
		level.setBlockAndUpdate(pos, newState);
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity evolved) {
			evolved.setOwner(savedOwner, savedOwnerName);
			evolved.getEnergyStorage().setAmountUntracked(Math.min(saved, evolved.getEnergyStorage().getCapacity()));
			for (Map.Entry<Integer, ItemStack> entry : slotOverrides.entrySet()) {
				int slot = entry.getKey();
				ItemStack stack = entry.getValue();
				if (!stack.isEmpty() && slot >= 0 && slot < evolved.getContainerSize()) {
					evolved.setItem(slot, stack);
				}
			}
			evolved.setChanged();
		}
	}
}
