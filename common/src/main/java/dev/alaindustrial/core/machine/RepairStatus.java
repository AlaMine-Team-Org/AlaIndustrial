package dev.alaindustrial.core.machine;

/**
 * Why the component repair bench is or is not working right now (MOD-384) — the single answer its
 * screen prints and its tick loop gates on. Minecraft-free, like {@link ComponentRepair} beside it,
 * so the priority order below is asserted directly by the L1 suite instead of only through a running
 * machine.
 *
 * <p>The order in {@link #resolve} is the order the player should fix things in, and it is the whole
 * content of this type: a bench holding a spent component reports the spent component, not the
 * missing plate, because fetching the plate would not help.
 */
public enum RepairStatus {
	/** The target slot holds nothing the bench recognises as a rotor or a wheel. */
	NO_TARGET,
	/** A component is in place but has taken no wear — there is nothing to restore. */
	NOT_DAMAGED,
	/** The component has used up its allowance of repairs and can never be repaired again. */
	LIMIT_REACHED,
	/** Repairable, but the matching material for its grade is not in the material slot. */
	NEEDS_MATERIAL,
	/** Everything is in place; the bench works as long as it has power. */
	READY;

	/**
	 * Classify the current contents of the bench.
	 *
	 * @param hasComponent the target slot holds a recognised rotor/wheel of any grade
	 * @param damaged that component has non-zero wear
	 * @param repairsDone repairs already performed on it
	 * @param maxRepairs the allowance, derived from the decay step by {@link ComponentRepair#maxRepairs}
	 * @param hasMaterial the material slot holds the item this component's grade is repaired with
	 */
	public static RepairStatus resolve(boolean hasComponent, boolean damaged, int repairsDone,
			int maxRepairs, boolean hasMaterial) {
		if (!hasComponent) {
			return NO_TARGET;
		}
		if (!damaged) {
			return NOT_DAMAGED;
		}
		if (!ComponentRepair.canRepair(repairsDone, maxRepairs)) {
			return LIMIT_REACHED;
		}
		if (!hasMaterial) {
			return NEEDS_MATERIAL;
		}
		return READY;
	}

	/** Whether the bench may spend EU and advance this tick (power is checked separately). */
	public boolean canWork() {
		return this == READY;
	}

	/**
	 * Whether a component that still HAS repairs coming sits in the slot — true for {@link #READY} and
	 * {@link #NEEDS_MATERIAL}.
	 *
	 * <p>This is what decides whether progress freezes or resets, mirroring the processing machines'
	 * contract (R-NRG-10): a run interrupted by a missing plate or a flat buffer resumes where it left
	 * off, while pulling the component out — or dropping in one that cannot be repaired — starts over.
	 * It is also the "still the bench's business" test that keeps automation from hauling a component
	 * away mid-repair.
	 */
	public boolean hasRepairableTarget() {
		return this == READY || this == NEEDS_MATERIAL;
	}

	/** The sync-channel code for this status; see {@link #byCode}. */
	public int code() {
		return ordinal();
	}

	/**
	 * The status for a code received over the menu's data channel, falling back to {@link #NO_TARGET}
	 * for anything out of range — a client that somehow reads a stale or truncated value shows "nothing
	 * loaded" rather than throwing in the render thread.
	 */
	public static RepairStatus byCode(int code) {
		RepairStatus[] all = values();
		return code >= 0 && code < all.length ? all[code] : NO_TARGET;
	}
}
