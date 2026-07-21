package dev.alaindustrial.core.fluid;
import dev.alaindustrial.core.energy.EnergyMover;
import dev.alaindustrial.core.energy.EnergyPort;

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
 *
 * <p><b>L1 testability.</b> {@link #move}'s signature takes {@link FluidPort} + {@link FluidHolder}
 * (both coupled to {@code net.minecraft.Fluid}, absent from {@code :common}'s L1 runtime), so this class
 * itself is exercised only by the Fabric L2 gametests. The refund arithmetic — the shortfall computation
 * and the moved-with-refund return — is extracted into {@link FluidMoverMath} and covered there by
 * {@code FluidMoverMathTest} + pitest (MOD-113).
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
		if (FluidMoverMath.nothingExtracted(extracted)) {
			return 0;
		}
		long inserted = to.insert(filterFluid, extracted, txn);
		if (FluidMoverMath.shortfallNeeded(inserted, extracted)) {
			// Refund the shortfall back into the source within the same transaction so nothing is
			// destroyed — mirrors StorageUtil.move's partial-acceptance refund. The refund target is the
			// same tank we just extracted from, which always has at least `shortfall` room freed by the
			// extract above, so `refunded` should always equal `shortfall` in practice.
			// Refund arithmetic extracted to FluidMoverMath (MOD-113) so the L1 suite + pitest cover the
			// shortfall / movedWithRefund MATH mutants without a live FluidHolder / FluidPort.
			long shortfall = FluidMoverMath.shortfall(extracted, inserted);
			long refunded = from.insert(filterFluid, shortfall, txn);
			return FluidMoverMath.movedWithRefund(inserted, refunded);
		}
		return inserted;
	}
}
