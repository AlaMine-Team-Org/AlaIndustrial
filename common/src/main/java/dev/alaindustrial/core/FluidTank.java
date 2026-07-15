package dev.alaindustrial.core;

import java.util.function.Predicate;

/**
 * The platform-neutral single-fluid tank backing the pump and geothermal generator (MOD-028). Owns the
 * stored fluid + amount and implements {@link FluidPort} directly, mirroring how {@link EnergyBuffer}
 * is both the backing store and its own self-view.
 *
 * <p><b>Semantics.</b> The transaction-safe {@link #insert}/{@link #extract} math is an exact
 * re-implementation of Fabric's {@code SingleVariantStorage} and NeoForge's {@code ResourceStacksResourceHandler}
 * (byte-for-byte identical transfer math on both): {@code inserted = min(maxAmount, capacity - amount)}
 * when the tank is empty or already holds the same fluid and {@code canInsert} allows it;
 * {@code extracted = min(maxAmount, amount)} when the requested fluid matches what is stored and
 * {@code canExtract} allows it. A non-zero move enlists the tank with the transaction (snapshot-before-mutate,
 * reusing {@link EnergyPort.Participant}) and then adjusts {@link #fluid}/{@link #amount}.
 *
 * <p><b>Fluid lifecycle: deferred clear, not snapshotted.</b> {@link EnergyPort.Participant} is hard
 * {@code long}-typed (mirrors {@link EnergyBuffer}'s single {@code amount} field), so this tank's
 * transactional snapshot/rollback only carries {@link #amount}; the {@link #fluid} field is never put into
 * the snapshot journal. To keep `amount > 0 ⇒ fluid ≠ EMPTY` true across a rollback, {@link #extract}
 * therefore does <em>not</em> clear {@code fluid} when it drives {@code amount} to 0 — clearing happens at
 * the two transaction terminals instead: {@link #readSnapshot} on a rollback to 0, {@link #onFinalCommit}
 * on a commit to 0. This is what lets a full-drain followed by a rollback restore both {@code amount}
 * <em>and</em> {@code fluid}: extract left {@code fluid} in place, and the rollback to a positive
 * {@code amount} has nothing to undo on the {@code fluid} field. (An earlier version cleared {@code fluid}
 * eagerly inside {@code extract}; that broke the cross-mod capability contract — a rolled-back full drain
 * left the tank reporting {@code amount > 0, fluid == EMPTY}, making it invisible to other mods probing it
 * via {@code fluid()}.)
 *
 * <p><b>Transactions.</b> This tank is an {@link EnergyPort.Participant}: it snapshots/restores
 * {@link #amount}, and fires {@link #onCommit} once when the outermost modifying transaction commits. The
 * loader's {@link EnergyPort.Txn} bridges these hooks onto its native snapshot journal exactly as it does
 * for {@link EnergyBuffer} — see {@link FluidPort} class doc for why fluid reuses the energy transaction
 * seam instead of a parallel type.
 *
 * <p><b>Direct field access (internal drain/production + persistence).</b> {@link #fluid} and
 * {@link #amount} are public and mutable so machine content (the geothermal generator burning its own
 * tank) can mutate them directly outside any transaction, and so {@code saveAdditional}/{@code loadAdditional}
 * can persist them — the same pattern {@link EnergyBuffer#amount} allows. Direct mutators MUST uphold the
 * same invariant (clear {@link #fluid} to {@link FluidHolder#EMPTY} whenever they drop {@link #amount} to 0).
 */
public class FluidTank implements FluidPort, EnergyPort.Participant {

	/** Fluid currently held, or {@link FluidHolder#EMPTY}. Public — see class doc. */
	public FluidHolder fluid = FluidHolder.EMPTY;

	/** Stored amount in mB. Public — see class doc. */
	public long amount;

	public final long capacity;
	private final Predicate<FluidHolder> canInsert;
	private final Predicate<FluidHolder> canExtract;

	/** Fired once after the outermost transaction that inserted/extracted through this tank commits. */
	private final Runnable onCommit;

	/**
	 * @param capacity   maximum mB this tank holds
	 * @param canInsert  which fluids may be inserted (e.g. pump/geo: lava only)
	 * @param canExtract which fluids may be extracted by a neighbour (pump: any; geo: never — R-CON-08)
	 * @param onCommit   run once when the outermost modifying transaction commits (persistence + wake);
	 *                   may be a no-op
	 */
	public FluidTank(long capacity, Predicate<FluidHolder> canInsert, Predicate<FluidHolder> canExtract,
			Runnable onCommit) {
		if (capacity < 0) {
			throw new IllegalArgumentException("Fluid tank capacity must be non-negative");
		}
		this.capacity = capacity;
		this.canInsert = canInsert;
		this.canExtract = canExtract;
		this.onCommit = onCommit;
	}

	@Override
	public long insert(FluidHolder inserted, long maxAmount, EnergyPort.Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		if (inserted == null || inserted.isEmpty() || maxAmount == 0) {
			return 0;
		}
		if (!canInsert.test(inserted)) {
			return 0;
		}
		if (!fluid.isEmpty() && !fluid.equals(inserted)) {
			return 0; // tank already holds a different fluid — single-variant, like SingleVariantStorage
		}
		long room = capacity - amount;
		long toInsert = Math.min(maxAmount, room);
		if (toInsert > 0) {
			txn.enlist(this);
			fluid = inserted;
			amount += toInsert;
			return toInsert;
		}
		return 0;
	}

	@Override
	public long extract(FluidHolder requested, long maxAmount, EnergyPort.Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		if (requested == null || requested.isEmpty() || maxAmount == 0) {
			return 0;
		}
		if (fluid.isEmpty() || !fluid.equals(requested)) {
			return 0;
		}
		if (!canExtract.test(fluid)) {
			return 0;
		}
		long toExtract = Math.min(maxAmount, amount);
		if (toExtract > 0) {
			txn.enlist(this);
			amount -= toExtract;
			// NOTE: `fluid` is NOT cleared here even when amount hits 0. The snapshot journal only carries
			// `amount` (the EnergyPort.Participant API is long-typed — see EnergyPort.Participant
			// class doc), so on a rollback to a positive amount this tank could not restore a `fluid` that
			// extract() had already wiped — the tank would read amount>0 with fluid==EMPTY, becoming
			// invisible to capability readers (TankAsFluidStorage/TankAsResourceHandler report fluid()). The
			// fluid-empty invariant is upheld instead at the two transaction terminals: readSnapshot clears
			// `fluid` on a rollback to 0, onFinalCommit clears it on a commit to 0.
			return toExtract;
		}
		return 0;
	}

	@Override
	public FluidHolder fluid() {
		return fluid;
	}

	@Override
	public long getAmount() {
		return amount;
	}

	@Override
	public long getCapacity() {
		return capacity;
	}

	@Override
	public boolean supportsInsertion() {
		return capacity > 0;
	}

	@Override
	public boolean supportsExtraction() {
		return capacity > 0;
	}

	// --- EnergyPort.Participant: the loader's native journal drives these ---

	@Override
	public long createSnapshot() {
		return amount;
	}

	@Override
	public void readSnapshot(long snapshot) {
		amount = snapshot;
		// Restore the fluid-empty invariant (see class doc): a rollback to 0 must clear the fluid. A
		// rollback to a positive amount leaves `fluid` untouched — it was non-empty before the transaction
		// (extract requires an exact fluid match), and extract() no longer pre-clears it (see extract()),
		// so the pre-transaction fluid is still in place and needs no separate restore.
		if (amount == 0) {
			fluid = FluidHolder.EMPTY;
		}
	}

	@Override
	public void onFinalCommit() {
		// Uphold the fluid-empty invariant on the commit path too: a committed extract that drained the
		// tank to 0 must clear `fluid` (extract() no longer does this itself — see extract()).
		if (amount == 0) {
			fluid = FluidHolder.EMPTY;
		}
		onCommit.run();
	}
}
