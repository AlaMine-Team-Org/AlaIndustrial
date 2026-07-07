package dev.alaindustrial.core.neoforge;

import com.google.common.primitives.Ints;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidPort;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Reverse adapter (MOD-028): exposes a neutral {@link FluidPort} (a machine's common
 * {@link dev.alaindustrial.core.FluidTank}) as a NeoForge {@code ResourceHandler<FluidResource>}, so it
 * can be published through {@code Capabilities.Fluid.BLOCK} in {@code RegisterCapabilitiesEvent}. The
 * capability contract is the per-loader binding seam; the tank itself is loader-neutral. Mirrors
 * {@link BufferAsEnergyHandler}.
 *
 * <p><b>Single index (0).</b> The tanks behind this (pump, geothermal generator) are single-fluid, so this
 * handler always reports {@code size() == 1} and only ever addresses index 0 — mirrors how
 * {@link NeoForgeFluidPort} reads a foreign handler at index 0.
 *
 * <p><b>Long → int saturation.</b> The common contract is {@code long}-based; {@code ResourceHandler}'s
 * {@code insert}/{@code extract} are {@code int}-based (mB never realistically exceeds
 * {@code Integer.MAX_VALUE} for a tank sized in single-digit buckets, mirrors {@link BufferAsEnergyHandler}'s
 * EU↔FE saturation).
 *
 * <p>NeoForge passes a {@link TransactionContext}; this adapter wraps it as a neutral
 * {@code EnergyPort.Txn} ({@link NeoForgeEnergyPort#wrap}) so the tank's transaction enlistment reaches
 * NeoForge's native snapshot journal — the same transaction bridge the energy adapter uses (see
 * {@link FluidPort} class doc for why fluid reuses it).
 */
public final class TankAsResourceHandler implements ResourceHandler<FluidResource> {
	private static final int INDEX = 0;

	private final FluidPort port;

	private TankAsResourceHandler(FluidPort port) {
		this.port = port;
	}

	/** Wrap a neutral {@link FluidPort} as a {@link ResourceHandler}, or {@code null} for none. */
	public static ResourceHandler<FluidResource> of(FluidPort port) {
		return port == null ? null : new TankAsResourceHandler(port);
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public FluidResource getResource(int index) {
		checkIndex(index);
		FluidHolder fluid = port.fluid();
		return fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluid.fluid());
	}

	@Override
	public long getAmountAsLong(int index) {
		checkIndex(index);
		return port.getAmount();
	}

	@Override
	public long getCapacityAsLong(int index, FluidResource resource) {
		checkIndex(index);
		return port.getCapacity();
	}

	@Override
	public boolean isValid(int index, FluidResource resource) {
		checkIndex(index);
		if (resource.isEmpty()) {
			return false;
		}
		// Probe via a dry-run insert simulated by the caller's own transaction model is not available
		// here (no transaction in scope for a pure predicate), so approximate "valid" as "the tank's
		// current fluid is empty or matches" — the tank's own canInsert predicate is enforced for real by
		// insert() itself; this predicate is only a hint (see ResourceHandler#isValid javadoc: "the only
		// way to know if a handler will accept a resource is to try to insert it").
		FluidHolder current = port.fluid();
		return current.isEmpty() || current.is(resource.getFluid());
	}

	@Override
	public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
		checkIndex(index);
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		long inserted = port.insert(FluidHolder.of(resource.getFluid()), amount, NeoForgeEnergyPort.wrap(transaction));
		return Ints.saturatedCast(inserted);
	}

	@Override
	public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
		checkIndex(index);
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		long extracted = port.extract(FluidHolder.of(resource.getFluid()), amount, NeoForgeEnergyPort.wrap(transaction));
		return Ints.saturatedCast(extracted);
	}

	private static int checkIndex(int index) {
		if (index != INDEX) {
			throw new IndexOutOfBoundsException("TankAsResourceHandler only has index 0, got: " + index);
		}
		return index;
	}
}
