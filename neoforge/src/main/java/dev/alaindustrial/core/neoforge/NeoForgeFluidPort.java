package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import dev.alaindustrial.core.fluid.FluidTank;

/**
 * NeoForge implementation of the platform-neutral {@link FluidPort} (MOD-028): an adapter over a NeoForge
 * {@code ResourceHandler<FluidResource>} ({@code net.neoforged.neoforge.transfer.fluid} /
 * {@code net.neoforged.neoforge.transfer.ResourceHandler}, verified against 26.2.0.8-beta — NeoForge fluid
 * moved to the SAME transfer-rework as energy, not the classic {@code IFluidHandler}). This is the
 * read/insert/extract view of a foreign or self-published {@code ResourceHandler}; the reverse direction
 * (publishing a common {@code FluidTank} as a {@code ResourceHandler} for the capability lookup) lives in
 * {@link TankAsResourceHandler}. Mirrors {@link NeoForgeEnergyPort}.
 *
 * <p><b>Units.</b> NeoForge's fluid resource handler already counts in millibuckets
 * ({@code FluidType.BUCKET_VOLUME == 1000}), the same internal unit the neutral {@link FluidPort} contract
 * uses (MOD-028 decision) — so unlike the Fabric adapter, this side needs NO conversion; amounts pass
 * through 1:1 (only the {@code long}↔{@code int} width differs, handled the same way
 * {@link NeoForgeEnergyPort} handles EU↔FE).
 *
 * <p><b>API shape.</b> {@code ResourceHandler.insert}/{@code extract} take an {@code int} amount and throw
 * {@code IllegalArgumentException} if the resource is empty (verified:
 * {@code TransferPreconditions.checkNonEmptyNonNegative}) — this adapter short-circuits to 0 for an empty
 * {@link FluidHolder} instead of ever calling through with an empty {@link FluidResource}. This handler is
 * addressed at a single index (0): the tanks behind it (pump, geothermal generator) are single-fluid, like
 * a {@code SingleVariantStorage} on Fabric.
 *
 * <p>The neutral {@link EnergyPort.Txn} handle wraps a NeoForge {@link TransactionContext}
 * ({@link NeoForgeEnergyPort.NeoForgeTxn}) — fluid reuses the exact transaction bridge the energy adapter
 * already established (see {@link FluidPort} class doc for why): this adapter unwraps it via
 * {@link NeoForgeEnergyPort#unwrap} before delegating, and never opens/commits a transaction itself.
 */
public final class NeoForgeFluidPort implements FluidPort {
	private static final int INDEX = 0;

	private final ResourceHandler<FluidResource> delegate;

	public NeoForgeFluidPort(ResourceHandler<FluidResource> delegate) {
		this.delegate = delegate;
	}

	/** Wrap a {@link ResourceHandler} as a neutral {@link FluidPort}, or {@code null} for none. */
	public static FluidPort of(ResourceHandler<FluidResource> handler) {
		return handler == null ? null : new NeoForgeFluidPort(handler);
	}

	private static FluidResource toResource(FluidHolder holder) {
		return FluidResource.of(holder.fluid());
	}

	@Override
	public long insert(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		int ask = (int) Math.min(maxAmount, Integer.MAX_VALUE);
		return delegate.insert(INDEX, toResource(fluid), ask, NeoForgeEnergyPort.unwrap(txn));
	}

	@Override
	public long extract(FluidHolder fluid, long maxAmount, EnergyPort.Txn txn) {
		if (fluid == null || fluid.isEmpty() || maxAmount <= 0) {
			return 0;
		}
		int ask = (int) Math.min(maxAmount, Integer.MAX_VALUE);
		return delegate.extract(INDEX, toResource(fluid), ask, NeoForgeEnergyPort.unwrap(txn));
	}

	@Override
	public FluidHolder fluid() {
		if (delegate.size() <= INDEX) {
			return FluidHolder.EMPTY;
		}
		FluidResource resource = delegate.getResource(INDEX);
		return resource.isEmpty() ? FluidHolder.EMPTY : FluidHolder.of(resource.getFluid());
	}

	@Override
	public long getAmount() {
		return delegate.size() <= INDEX ? 0 : delegate.getAmountAsLong(INDEX);
	}

	@Override
	public long getCapacity() {
		if (delegate.size() <= INDEX) {
			return 0;
		}
		// getCapacityAsLong wants a resource to check validity against; the current resource (or, if
		// empty, any resource this handler would accept) reports the tank's real capacity, mirroring how
		// FluidStacksResourceHandler#getCapacity ignores the resource argument entirely for a
		// fixed-capacity tank (verified in the decompiled 26.2.0.8-beta base class).
		FluidResource current = delegate.getResource(INDEX);
		return delegate.getCapacityAsLong(INDEX, current);
	}

	@Override
	public boolean supportsInsertion() {
		return delegate.size() > INDEX;
	}

	@Override
	public boolean supportsExtraction() {
		return delegate.size() > INDEX;
	}
}
