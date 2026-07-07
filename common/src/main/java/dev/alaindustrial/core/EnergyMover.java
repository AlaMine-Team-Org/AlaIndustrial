package dev.alaindustrial.core;

/**
 * Platform-neutral energy move helper (MOD-022 Phase 2): the neutral replacement for Team Reborn's
 * {@code EnergyStorageUtil.move} (Fabric) and {@code EnergyHandlerUtil.move} (NeoForge). Moves up to
 * {@code maxAmount} EU from {@code from} into {@code to} within the caller's transaction {@code txn},
 * and returns the amount actually moved.
 *
 * <p><b>Loss-free by construction.</b> Team Reborn's {@code EnergyStorageUtil.move} opens a <em>nested</em>
 * transaction to simulate the extract, then transfers only the amount the target actually accepts, so no
 * EU is ever created or destroyed. Reproducing "extract, then refund the surplus" in one flat transaction
 * is <b>not</b> equivalent: the refund goes back through {@link EnergyPort#insert}, which is rate-capped,
 * and a generator buffer publishes {@code maxInsert == 0} (it only emits). The surplus refund then returns
 * 0 and the extracted EU is lost — a generator whose only neighbour is full would leak its whole buffer.
 *
 * <p>This helper instead splits the move into a <b>probe</b> and a <b>commit</b> so the amount is sized to
 * what the target will take before anything is extracted, mirroring the pattern the cable network uses:
 * {@link #probe} runs in a dry-run transaction (opened + aborted by {@link EnergyTransactions#simulate}) and
 * must be called <em>before</em> the committing transaction is opened; {@link #commit} then transfers
 * exactly the probed amount inside the caller's committing {@code txn}. No refund path, so no rate-cap loss.
 */
public final class EnergyMover {
	private EnergyMover() {
	}

	/**
	 * Dry-run the move: the EU that would actually flow from {@code from} into {@code to} this instant,
	 * bounded by {@code maxAmount}, the source's extractable amount and the target's insertable room/rate.
	 * Runs in its own dry-run transaction and commits nothing — call this <em>before</em> opening the
	 * committing transaction that {@link #commit} uses, never while one is open (the two loaders forbid a
	 * second outer transaction on the same thread).
	 */
	public static long probe(EnergyPort from, EnergyPort to, long maxAmount) {
		if (from == null || to == null || maxAmount <= 0) {
			return 0;
		}
		return EnergyTransactions.get().simulate(sim -> {
			long extractable = from.extract(maxAmount, sim);
			if (extractable <= 0) {
				return 0L;
			}
			return to.insert(extractable, sim);
		});
	}

	/**
	 * Transfer exactly {@code amount} EU from {@code from} into {@code to} under the caller's committing
	 * {@code txn}. {@code amount} must be the value returned by a preceding {@link #probe} for the same
	 * pair, so the extract and insert both succeed in full and no surplus is ever left over to refund.
	 */
	public static void commit(EnergyPort from, EnergyPort to, long amount, EnergyPort.Txn txn) {
		if (amount <= 0) {
			return;
		}
		from.extract(amount, txn);
		to.insert(amount, txn);
	}
}
