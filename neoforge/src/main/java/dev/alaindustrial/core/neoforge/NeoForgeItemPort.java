package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.EnergyPort;
import dev.alaindustrial.core.ItemPort;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Adapter from NeoForge's 26.2 {@link ResourceHandler} item API to the common pipe port. */
public final class NeoForgeItemPort implements ItemPort {
	private final ResourceHandler<ItemResource> delegate;

	private NeoForgeItemPort(ResourceHandler<ItemResource> delegate) {
		this.delegate = delegate;
	}

	public static ItemPort of(ResourceHandler<ItemResource> handler) {
		return handler == null ? null : new NeoForgeItemPort(handler);
	}

	@Override
	public int moveTo(ItemPort target, int maxAmount, EnergyPort.Txn txn) {
		if (!(target instanceof NeoForgeItemPort neoForgeTarget)) {
			throw new IllegalArgumentException("Cannot move NeoForge items into a foreign item-port implementation");
		}
		return ResourceHandlerUtil.moveStacking(delegate, neoForgeTarget.delegate, resource -> true,
				maxAmount, NeoForgeEnergyPort.unwrap(txn));
	}
}
