package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/**
 * Reverse adapter (MOD-028): exposes a neutral {@link FluidPort} as a Fabric {@code Storage<FluidVariant>},
 * so a machine's platform-neutral fluid tank (pump, geothermal generator) can be published through
 * Fabric's {@code FluidStorage.SIDED} capability lookup. The Fabric-side capability contract is the
 * per-loader binding seam; the tank itself is loader-neutral. Mirrors {@link PortAsEnergyStorage}.
 *
 * <p>Fabric passes a {@code TransactionContext}; this adapter wraps it as a neutral {@link EnergyPort.Txn}
 * ({@link FabricEnergyPort#wrap}) so the tank's transaction enlistment reaches Fabric's native snapshot
 * journal, then converts mB↔droplets at the boundary (see {@link FabricFluidPort} for the conversion
 * rationale — the same {@link FluidAmounts#FABRIC_DROPLETS_PER_MB} factor applies here, in reverse).
 */
public final class TankAsFluidStorage implements Storage<FluidVariant> {
	private final FluidPort port;

	private TankAsFluidStorage(FluidPort port) {
		this.port = port;
	}

	/** Wrap a neutral {@link FluidPort} as a Fabric {@link Storage}, or {@code null} for none. */
	public static Storage<FluidVariant> of(FluidPort port) {
		return port == null ? null : new TankAsFluidStorage(port);
	}

	private static long toDroplets(long mb) {
		return Math.multiplyExact(mb, FluidAmounts.FABRIC_DROPLETS_PER_MB);
	}

	private static long toMb(long droplets) {
		return droplets / FluidAmounts.FABRIC_DROPLETS_PER_MB;
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		if (resource.isBlank() || maxAmount <= 0) {
			return 0;
		}
		long insertedMb = port.insert(FluidHolder.of(resource.getFluid()), toMb(maxAmount),
				FabricEnergyPort.wrap(transaction));
		return toDroplets(insertedMb);
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		if (resource.isBlank() || maxAmount <= 0) {
			return 0;
		}
		long extractedMb = port.extract(FluidHolder.of(resource.getFluid()), toMb(maxAmount),
				FabricEnergyPort.wrap(transaction));
		return toDroplets(extractedMb);
	}

	@Override
	public boolean supportsInsertion() {
		return port.supportsInsertion();
	}

	@Override
	public boolean supportsExtraction() {
		return port.supportsExtraction();
	}

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		FluidHolder fluid = port.fluid();
		FluidVariant variant = fluid.isEmpty() ? FluidVariant.blank() : FluidVariant.of(fluid.fluid());
		StorageView<FluidVariant> view = new StorageView<>() {
			@Override
			public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
				return TankAsFluidStorage.this.extract(resource, maxAmount, transaction);
			}

			@Override
			public boolean isResourceBlank() {
				return variant.isBlank();
			}

			@Override
			public FluidVariant getResource() {
				return variant;
			}

			@Override
			public long getAmount() {
				return toDroplets(port.getAmount());
			}

			@Override
			public long getCapacity() {
				return toDroplets(port.getCapacity());
			}
		};
		return java.util.List.of(view).iterator();
	}
}
