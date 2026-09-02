package dev.alaindustrial.block.entity;

import net.minecraft.world.level.Level;

/**
 * The processing cycle a powered machine runs, written once (MOD-557).
 *
 * <p>The cycle is an ORDER, and that is the whole reason it is a component rather than a habit. Every
 * machine in this mod that turns EU into a finished operation performs the same eight steps in the same
 * sequence:
 *
 * <ol>
 *   <li>derive the effective draw and the effective operation length — through the INSTANCE helpers, so
 *       the overclocker chips in the panel are actually seen (MOD-392);</li>
 *   <li>decide whether this tick can be paid for and worked (the machine's own predicate);</li>
 *   <li>show it: the {@code lit} blockstate follows that decision;</li>
 *   <li>report the draw to the statistics panel — the working rate, or 0 when stopped (MOD-125);</li>
 *   <li>take the EU out of the buffer;</li>
 *   <li>advance progress by one tick;</li>
 *   <li>at the top of the bar, commit the operation — the one machine-specific step;</li>
 *   <li>count the operation, credit its EU cost to the owner (MOD-133) and answer the idle-sleep gate
 *       (R-29).</li>
 * </ol>
 *
 * <p>Eleven machines each wrote that sequence out by hand, and every one of the steps had been forgotten
 * by somebody at least once — the rate report, the useful-work credit, the sleep answer. Any change to
 * how a machine sleeps or how its energy is accounted meant eleven identical edits, and the eleventh
 * showed up in the game rather than in a diff.
 *
 * <p><b>What the machine still owns.</b> Everything that is genuinely about THIS machine: resolving the
 * recipe, deciding {@code canWork}, its status line, consuming the inputs and writing the results. The
 * cycle asks for those as a job and does nothing clever with them.
 *
 * <p><b>Not a base class.</b> A machine composes with a cycle ({@code private final ProcessingCycle
 * cycle = new ProcessingCycle(this)}) instead of inheriting one, because the machines that hand-wrote
 * this loop did so precisely BECAUSE the single inheritance slot was already spent — on
 * {@link MachineBlockEntity} — and because {@link AbstractProcessingMachineBlockEntity}, the only base
 * that carried the loop, is shaped for "one input → one output" and cannot describe three interchangeable
 * inputs, a fluid tank or a heat tier.
 *
 * <p><b>Out of scope on purpose:</b> the reactor controller and the assembler. In both, this loop is
 * fused with a scheduler of their own, and splitting them is separate work with a separate risk
 * assessment. The distillation column and the thermal centrifuge spend a paid tick on a pre-stage
 * (warm-up, spin-up) BEFORE progress may advance, which is a genuine second shape rather than a copy of
 * this one; they keep their hand-written loops until that shape is worth naming.
 */
public final class ProcessingCycle {

	/** The work a machine commits when a paid run reaches the top of its bar. */
	@FunctionalInterface
	public interface Completion {
		/**
		 * Consume the inputs and write the results. Called once, with progress already back at zero; the
		 * cycle counts the operation and credits its cost afterwards, so an implementation must not do
		 * either itself.
		 */
		void commit();
	}

	private final MachineBlockEntity machine;

	public ProcessingCycle(MachineBlockEntity machine) {
		this.machine = machine;
	}

	/**
	 * Open this tick's job.
	 *
	 * <p>Both arguments are the machine's own UNSCALED numbers — its tariff in EU/t and the operation
	 * length in ticks that tariff implies — because the scaling belongs to the cycle and applying it
	 * twice is the classic way to make a retuned server run at the square of its configured speed. The
	 * derived values are readable back through {@link Job#euPerTick()} and {@link Job#duration()}, which
	 * is what a machine's affordability test needs.
	 */
	public Job job(int baseEuPerTick, int baseDuration) {
		return new Job(baseEuPerTick, baseDuration);
	}

	/**
	 * One tick's assignment: what it costs, how long the operation is, whether it may run, and whether
	 * the job the accumulated progress was bought for still exists.
	 *
	 * <p>Built fresh each tick and consumed immediately by {@link #run}; nothing survives the tick.
	 */
	public final class Job {

		private final int euPerTick;
		private final int duration;
		private boolean canWork;
		private boolean jobIntact = true;
		private boolean keepAwake;
		private boolean alreadyChanged;

		private Job(int baseEuPerTick, int baseDuration) {
			this.euPerTick = machine.effectiveEuPerTick(baseEuPerTick);
			this.duration = machine.effectiveDuration(baseDuration);
		}

		/** The draw this tick after the speed knob and the overclocker chips. */
		public int euPerTick() {
			return euPerTick;
		}

		/** The operation length in ticks after the speed knob and the overclocker chips. */
		public int duration() {
			return duration;
		}

		/**
		 * Whether this tick may be paid for and worked — the machine's own verdict, covering everything
		 * from "a recipe matched" to "the output slot has room" to "the buffer holds one tick's draw".
		 */
		public Job canWork(boolean value) {
			this.canWork = value;
			return this;
		}

		/**
		 * Whether the job the accumulated progress was bought for still exists (R-NRG-10). When it does
		 * not, progress restarts from zero; while it does, an unpaid tick merely FREEZES progress, so a
		 * flat buffer or a full output slot costs the player nothing they had already earned.
		 *
		 * <p>Defaults to {@code true}: a machine that says nothing never throws progress away.
		 */
		public Job jobIntact(boolean value) {
			this.jobIntact = value;
			return this;
		}

		/**
		 * An extra reason not to sleep this tick, even though no work was done — the fluid machines
		 * exchanging a bucket, for instance. Without it the idle-sleep gate (R-29) would make the next
		 * container swap wait out the full nap.
		 */
		public Job keepAwake(boolean value) {
			this.keepAwake = value;
			return this;
		}

		/**
		 * State outside the cycle already changed this tick and must be persisted even if no work
		 * happens — the canning machine's free food absorption is the one caller. Folded into the
		 * cycle's own decision so the machine is marked dirty exactly once per tick.
		 */
		public Job alreadyChanged(boolean value) {
			this.alreadyChanged = value;
			return this;
		}

		/**
		 * Run the eight steps and answer the idle-sleep gate: 0 to keep ticking,
		 * {@link EnergyBlockEntity#IDLE_SLEEP_TICKS} to nap.
		 */
		public int run(Level level, Completion completion) {
			return ProcessingCycle.this.run(level, this, completion);
		}
	}

	private int run(Level level, Job job, Completion completion) {
		// The bar's length is part of the assignment, so the GUI shows a meaningful operation even on a
		// tick that does no work — and a machine can never publish a length it did not run at.
		machine.maxProgress = job.duration;
		machine.updateLit(job.canWork);
		// MOD-125: the statistics panel's "now" line for a consumer is its draw, and a stopped machine
		// reports 0 rather than keeping its last reading. Recorded BEFORE the drain: this call is what
		// switches the buffer's counters on, and the very tick a statistics chip is fitted must already
		// count its own draw.
		machine.recordEuRate(job.canWork ? job.euPerTick : 0);

		boolean changed = job.alreadyChanged;
		if (job.canWork) {
			machine.energy.drainInternal(job.euPerTick);
			machine.progress++;
			if (machine.progress >= machine.maxProgress) {
				machine.progress = 0;
				completion.commit();
				machine.recordItemProcessed(); // MOD-125: lifetime operation counter
				// MOD-133: a COMPLETED operation is the mod's only XP source, so a contraption that
				// aborts one mid-run burns EU and earns nothing.
				machine.creditUsefulWork(level, (long) job.euPerTick * machine.maxProgress);
			}
			changed = true;
		} else if (!job.jobIntact && machine.progress != 0) {
			machine.progress = 0;
			changed = true;
		}
		if (changed) {
			machine.setChanged();
		}
		// Idle → sleep until inventory, energy or a neighbour wakes the block (R-29).
		return job.canWork || job.keepAwake ? 0 : EnergyBlockEntity.IDLE_SLEEP_TICKS;
	}
}
