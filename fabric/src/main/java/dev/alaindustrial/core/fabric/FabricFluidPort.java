package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;

/**
 * Fabric implementation of the platform-neutral {@link FluidPort} (MOD-028): a thin adapter over a Fabric
 * {@link Storage}{@code <FluidVariant>}. Fabric's fluid Transfer API is Fabric-only; the NeoForge side
 * implements {@code FluidPort} over {@code ResourceHandler<FluidResource>} instead — mirrors
 * {@link FabricEnergyPort}.
 *
 * <p><b>Unit conversion (MOD-028 decision, {@code FluidAmounts}).</b> Fabric counts in droplets
 * ({@code FluidConstants.BUCKET == 81000}); the neutral {@link FluidPort} contract counts in millibuckets
 * (mB, {@code FluidAmounts.BUCKET == 1000}). This adapter is the ONLY side that converts: every {@code long}
 * mB amount crossing the boundary is scaled by {@link FluidAmounts#FABRIC_DROPLETS_PER_MB} (81, exact —
 * 81000 droplets/bucket ÷ 1000 mB/bucket).
 *
 * <p><b>Foreign storages do not owe us multiples of 81 (MOD-284).</b> Our own transfers move whole
 * buckets, so scaling mB up is always exact — but the value coming BACK from a foreign
 * {@code Storage<FluidVariant>} is whatever that mod chose to move, and common fractions of a bucket do
 * not divide evenly: a ninth is 9000 droplets, a bottle 27000. Flooring such a result silently
 * desynchronises the two sides of a transfer — on an insert the source is debited less than really
 * crossed (fluid appears), on an extract the donor loses droplets nobody is credited for (fluid
 * vanishes). Both {@link #insert} and {@link #extract} therefore move the sub-mB tail back within the
 * same transaction and report only the exact whole-mB part.
 *
 * <p>The neutral {@link EnergyPort.Txn} handle wraps Fabric's {@code TransactionContext} — fluid reuses the
 * exact same {@link FabricEnergyPort.FabricTxn} bridge the energy adapter already established (see
 * {@link FluidPort} class doc for why), so this class never talks to Fabric's transaction API directly; it
 * only unwraps a {@link FabricEnergyPort.FabricTxn} back to its {@code TransactionContext} via
 * {@code FabricTxn.ctx()} before delegating.
 */
public final class FabricFluidPort implements FluidPort {
	private final Storage<FluidVariant> delegate;

	public FabricFluidPort(Storage<FluidVariant> delegate) {
		this.delegate = delegate;
	}

	/** Wrap a Fabric fluid storage as a neutral {@link FluidPort}, or {@code null} for a {@code null} storage. */
	public static FluidPort of(Storage<FluidVariant> storage) {
		return storage == null ? null : new FabricFluidPort(storage);
	}

	private static long toDroplets(long mb) {
		return FluidAmounts.toDroplets(mb);
	}

	private static long toMb(long droplets) {
		return FluidAmounts.toMbFloor(droplets);
	}

	private static FluidVariant toVariant(FluidHolder holder) {
		return holder.isEmpty() ? FluidVariant.blank() : FluidVariant.of(holder.fluid());
	}

	@Override
	public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		FluidVariant variant = toVariant(fluid);
		var ctx = unwrap(txn);
		long insertedDroplets = delegate.insert(variant, toDroplets(maxAmount), ctx);
		// MOD-284: the neutral contract is in mB, so a tail below one mB cannot be reported. Reporting
		// only the floor would have us debit the source less than actually crossed the boundary —
		// fluid out of nowhere. Pull the tail back inside the same transaction so both sides agree.
		long tail = FluidAmounts.remainderDroplets(insertedDroplets);
		if (tail > 0) {
			delegate.extract(variant, tail, ctx);
		}
		return toMb(insertedDroplets);
	}

	@Override
	public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		FluidVariant variant = toVariant(fluid);
		var ctx = unwrap(txn);
		long extractedDroplets = delegate.extract(variant, toDroplets(maxAmount), ctx);
		// MOD-284: mirror of insert. Flooring an unreportable tail here would leave those droplets
		// gone from the donor while nobody was credited for them — fluid destroyed. Put them back.
		long tail = FluidAmounts.remainderDroplets(extractedDroplets);
		if (tail > 0) {
			delegate.insert(variant, tail, ctx);
		}
		return toMb(extractedDroplets);
	}

	@Override
	public FluidHolder fluid() {
		for (StorageView<FluidVariant> view : delegate) {
			if (!view.isResourceBlank()) {
				return FluidHolder.of(view.getResource().getFluid());
			}
		}
		return FluidHolder.EMPTY;
	}

	@Override
	public long getAmount() {
		long total = 0;
		for (StorageView<FluidVariant> view : delegate) {
			if (!view.isResourceBlank()) {
				total += view.getAmount();
			}
		}
		return toMb(total);
	}

	@Override
	public long getCapacity() {
		long total = 0;
		for (StorageView<FluidVariant> view : delegate) {
			total += view.getCapacity();
		}
		return toMb(total);
	}

	@Override
	public boolean supportsInsertion() {
		return delegate.supportsInsertion();
	}

	@Override
	public boolean supportsExtraction() {
		return delegate.supportsExtraction();
	}

	private static net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext unwrap(EnergyPort.Txn txn) {
		if (txn instanceof FabricEnergyPort.FabricTxn ft) {
			return ft.ctx();
		}
		throw new IllegalArgumentException("Expected a Fabric transaction handle, got: " + txn);
	}
}
