package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.EnergyPort;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidPort;
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
 * 81000 droplets/bucket ÷ 1000 mB/bucket). The mod's fluid transactions always move whole buckets, so the
 * multiply/divide never truncates.
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
		return Math.multiplyExact(mb, FluidAmounts.FABRIC_DROPLETS_PER_MB);
	}

	private static long toMb(long droplets) {
		return droplets / FluidAmounts.FABRIC_DROPLETS_PER_MB;
	}

	private static FluidVariant toVariant(FluidHolder holder) {
		return holder.isEmpty() ? FluidVariant.blank() : FluidVariant.of(holder.fluid());
	}

	@Override
	public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		long inserted = delegate.insert(toVariant(fluid), toDroplets(maxAmount), unwrap(txn));
		return toMb(inserted);
	}

	@Override
	public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		long extracted = delegate.extract(toVariant(fluid), toDroplets(maxAmount), unwrap(txn));
		return toMb(extracted);
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
