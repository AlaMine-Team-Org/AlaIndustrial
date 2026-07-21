package dev.alaindustrial.core.item;
import dev.alaindustrial.core.energy.EnergyTransactions;

/** Loss-free transactional item transfer shared by both loaders. */
public final class ItemMover {
	private ItemMover() {
	}

	/** Move up to {@code maxAmount}; the loader port performs its native atomic move. */
	public static int move(ItemPort from, ItemPort to, int maxAmount) {
		if (from == null || to == null || maxAmount <= 0) {
			return 0;
		}
		int[] moved = {0};
		EnergyTransactions.get().runCommitting(txn -> {
			moved[0] = from.moveTo(to, maxAmount, txn);
		});
		return moved[0];
	}
}
