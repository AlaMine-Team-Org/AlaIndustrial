package dev.alaindustrial.core;

/**
 * Platform-neutral fluid move helper (MOD-028): the neutral replacement for Fabric's
 * {@code StorageUtil.move}, mirroring {@link EnergyMover}. Moves up to {@code maxAmount} mB of a fluid
 * matching {@code filter} from {@code from} into {@code to} within the caller's transaction {@code txn},
 * returning the amount actually moved.
 *
 * <p>Unlike {@link EnergyMover}, the pump/geothermal generator only ever move whole buckets in one flat
 * transaction (never split across a probe + a separately-sized commit — see {@code PumpBlockEntity}), and
 * the tanks involved always accept/refuse a fixed single fluid (lava) rather than a
 * runtime-varying-capacity buffer, so a straightforward "extract, then insert exactly what came out" is
 * loss-free here: {@link #move} extracts up to {@code maxAmount} from {@code from}, then inserts exactly
 * that extracted amount into {@code to}. If {@code to} accepts less than {@code from} gave up (e.g. it is
 * nearly full), the shortfall is inserted back into {@code from} within the SAME transaction before
 * returning, so a partial acceptance never destroys fluid — mirrors {@code StorageUtil.move}'s behaviour
 * (which also refunds unaccepted amounts back to the source).
 */
public final class FluidMover {
	private FluidMover() {
	}

	/**
	 * Move up to {@code maxAmount} mB of {@code filterFluid} from {@code from} into {@code to} under
	 * {@code txn}. Returns the amount actually moved (0 if either port is {@code null}, empty, or refuses).
	 */
	public static long move(FluidPort from, FluidPort to, FluidHolder filterFluid, long maxAmount,
			EnergyPort.Txn txn) {
		if (from == null || to == null || maxAmount <= 0) {
			return 0;
		}
		long extracted = from.extract(filterFluid, maxAmount, txn);
		if (extracted <= 0) {
			return 0;
		}
		long inserted = to.insert(filterFluid, extracted, txn);
		if (inserted < extracted) {
			// Refund the shortfall back into the source within the same transaction so nothing is
			// destroyed — mirrors StorageUtil.move's partial-acceptance refund. The refund target is the
			// same tank we just extracted from, which always has at least `shortfall` room freed by the
			// extract above, so `refunded` should always equal `shortfall` in practice.
			long shortfall = extracted - inserted;
			long refunded = from.insert(filterFluid, shortfall, txn);
			return inserted + refunded;
		}
		return inserted;
	}
}
