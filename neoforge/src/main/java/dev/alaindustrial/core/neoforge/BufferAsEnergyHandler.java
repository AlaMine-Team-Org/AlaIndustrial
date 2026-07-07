package dev.alaindustrial.core.neoforge;

import com.google.common.primitives.Ints;
import dev.alaindustrial.core.EnergyPort;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Reverse adapter (MOD-022 Phase 2): exposes a neutral {@link EnergyPort} (a machine's common
 * {@link dev.alaindustrial.core.EnergyBuffer}, face-role wrapped) as a NeoForge {@link EnergyHandler},
 * so it can be published through {@code Capabilities.Energy.BLOCK} in
 * {@code RegisterCapabilitiesEvent}. The capability contract is the per-loader binding seam; the buffer
 * itself is loader-neutral.
 *
 * <p><b>Long → int saturation.</b> The common contract is {@code long}-based; {@code EnergyHandler}'s
 * {@code insert}/{@code extract} are {@code int}-based. We pin a 1:1 EU:FE ratio, so the transfer amount
 * passes through numerically; the {@code int} results are {@linkplain Ints#saturatedCast saturated} from
 * the buffer's {@code long} returns (never an issue in practice — a single transfer is bounded by the
 * tier packet cap, far below {@code Integer.MAX_VALUE}). The getters report the buffer's true
 * {@code long} amount/capacity.
 *
 * <p>NeoForge passes a {@link TransactionContext}; this adapter wraps it as a neutral
 * {@link EnergyPort.Txn} ({@link NeoForgeEnergyPort#wrap}) so the buffer's transaction enlistment reaches
 * NeoForge's native snapshot journal.
 */
public final class BufferAsEnergyHandler implements EnergyHandler {
	private final EnergyPort port;

	private BufferAsEnergyHandler(EnergyPort port) {
		this.port = port;
	}

	/** Wrap a neutral {@link EnergyPort} as an {@link EnergyHandler}, or {@code null} for none. */
	public static EnergyHandler of(EnergyPort port) {
		return port == null ? null : new BufferAsEnergyHandler(port);
	}

	@Override
	public long getAmountAsLong() {
		return port.getAmount();
	}

	@Override
	public long getCapacityAsLong() {
		return port.getCapacity();
	}

	// The int amount widens losslessly to the port's long ask; only the long RETURN is narrowed. It cannot
	// exceed the int amount requested (a buffer never returns more than asked), so the saturatedCast is a
	// safe int→int no-op in practice and never loses EU — the cast just satisfies the int return type.
	@Override
	public int insert(int amount, TransactionContext transaction) {
		return Ints.saturatedCast(port.insert(amount, NeoForgeEnergyPort.wrap(transaction)));
	}

	@Override
	public int extract(int amount, TransactionContext transaction) {
		return Ints.saturatedCast(port.extract(amount, NeoForgeEnergyPort.wrap(transaction)));
	}
}
