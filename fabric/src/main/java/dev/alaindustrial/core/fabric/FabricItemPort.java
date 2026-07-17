package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.EnergyPort;
import dev.alaindustrial.core.ItemPort;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;

/** Adapter from Fabric {@link Storage} item inventory to the common pipe port. */
public final class FabricItemPort implements ItemPort {
	private final Storage<ItemVariant> delegate;

	private FabricItemPort(Storage<ItemVariant> delegate) {
		this.delegate = delegate;
	}

	public static ItemPort of(Storage<ItemVariant> storage) {
		return storage == null ? null : new FabricItemPort(storage);
	}

	@Override
	public int moveTo(ItemPort target, int maxAmount, EnergyPort.Txn txn) {
		if (!(target instanceof FabricItemPort fabricTarget)) {
			throw new IllegalArgumentException("Cannot move Fabric items into a foreign item-port implementation");
		}
		return (int) StorageUtil.move(delegate, fabricTarget.delegate, variant -> true, maxAmount, unwrap(txn));
	}

	private static net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext unwrap(EnergyPort.Txn txn) {
		if (txn instanceof FabricEnergyPort.FabricTxn ft) return ft.ctx();
		throw new IllegalArgumentException("Expected a Fabric transaction handle, got: " + txn);
	}
}
