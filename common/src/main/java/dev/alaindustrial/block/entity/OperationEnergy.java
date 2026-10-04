package dev.alaindustrial.block.entity;

import dev.alaindustrial.skill.SkillMachine;
import net.minecraft.world.level.Level;

/**
 * How one tick of a machine's operation is paid for: the single rule behind
 * {@link MachineBlockEntity#spendOperationEnergy}, shared by {@link ProcessingCycle} and by the machines
 * that run a cycle of their own — the assembler, the incubator, the distillation column and the thermal
 * centrifuge (MOD-712, owner decision D4 on the boundary of ADR-021).
 *
 * <p>Two Mechanic skills (MOD-483) act on that tick, and this is the only place that applies them. They
 * used to be applied inline in {@code ProcessingCycle.run}, so the four machines outside the component
 * never saw them although the skills promise "your machinery"; a rule written once cannot be forgotten
 * by the next machine with its own cycle.
 *
 * <p>Only the tick that advances an operation goes through here. A pre-stage that buys no progress —
 * the column's warm-up, the centrifuge's spin-up and idle trickle — is preparation, not the operation,
 * and is paid at the full rate by the machine itself.
 */
final class OperationEnergy {

	private OperationEnergy() {
	}

	/**
	 * Resilient Cycle (MOD-483): whether an operation the buffer can no longer pay for may still run this
	 * tick on whatever charge is left.
	 *
	 * <p>Past the skill's threshold only, and the energy is still spent — only the demand for an incoming
	 * supply is waived, which is why a switch cutting power mid-run cannot be farmed for free operations.
	 *
	 * <p>MOD-576: and ONLY that demand. {@code readyExceptEnergy} is the machine saying "the supply is the
	 * only thing missing"; testing the full working verdict instead let the skill waive a full output slot
	 * or a missing part too, and the completion then ran with no room for its result.
	 */
	static boolean coasts(MachineBlockEntity machine, Level level, boolean readyExceptEnergy) {
		return readyExceptEnergy && machine.energy.getAmount() > 0
				&& SkillMachine.canCoast(machine.progress, machine.maxProgress, level, machine.getOwner());
	}

	/**
	 * Pay for one tick of an operation, or decline it. Returns whether the tick runs — paid
	 * ({@code canWork}) or coasting under Resilient Cycle; when it returns false nothing was spent and the
	 * caller must not advance progress.
	 *
	 * <p>Precise Draw (MOD-483): one tick in {@code skillPreciseDrawEveryTicks} costs nothing, which is 10 %
	 * off the operation. Counted in ticks of progress (before this tick's step) because a basic machine
	 * draws 2 EU/t and a percentage of two rounds to nothing or to half.
	 */
	static boolean spend(MachineBlockEntity machine, Level level, int euPerTick, boolean canWork,
			boolean readyExceptEnergy) {
		if (!canWork && !coasts(machine, level, readyExceptEnergy)) {
			return false;
		}
		if (!SkillMachine.freeDrainTick(machine.progress, level, machine.getOwner())) {
			machine.energy.drainInternal(euPerTick);
		}
		return true;
	}
}
