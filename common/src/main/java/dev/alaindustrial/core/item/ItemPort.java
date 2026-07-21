package dev.alaindustrial.core.item;
import dev.alaindustrial.core.energy.EnergyPort;

/**
 * Loader-neutral, transactional view of a sided item inventory.  Item pipes use this interface
 * instead of importing Fabric Transfer API or NeoForge's transfer handler directly.
 */
public interface ItemPort {

	/**
	 * Move up to {@code maxAmount} into another port in the supplied outer transaction.
	 * Implementations delegate to the loader's native atomic move helper: an output-only inventory
	 * may reject refunds, so common code must never implement this as extract/insert/refund.
	 */
	int moveTo(ItemPort target, int maxAmount, EnergyPort.Txn txn);
}
