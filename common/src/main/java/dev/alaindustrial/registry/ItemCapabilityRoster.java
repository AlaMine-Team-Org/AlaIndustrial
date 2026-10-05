package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.energy.PoweredItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Which items publish the item energy capability, derived by <b>interface</b> (MOD-707) — the item-side
 * twin of {@link BlockCapabilityRoster} (MOD-433), one answer for both loaders.
 *
 * <p>Until MOD-707 each loader named the powered items in a hand-written list —
 * {@code StackAsEnergyStorage.register} on Fabric and the {@code Capabilities.Energy.ITEM} block of
 * {@code IndustrializationNeoForge#registerCapabilities} — and twice those lists fell behind the items
 * (MOD-372). An item now publishes the capability because of what it <i>is</i>: its class implements
 * {@link PoweredItem}. Both loaders replay {@link #energyItems()}.
 *
 * <p><b>Why the interface and not {@code ItemEnergy.capacity > 0}.</b> The interface does not depend on
 * {@code Config} being loaded when the loader registers capabilities, and it keeps the gametest guard
 * {@code PoweredItemCatalog} (expected set from {@code capacity > 0}) an independent oracle instead of a
 * tautology.
 *
 * <p><b>When it may be called.</b> It reads the live item registry, so only after the mod's items are
 * registered: after {@code ModItems.init()} on Fabric, inside {@code RegisterCapabilitiesEvent} (fired
 * after the registries froze) on NeoForge.
 */
public final class ItemCapabilityRoster {
	private ItemCapabilityRoster() {
	}

	/** Every {@code alaindustrial} item whose class implements {@link PoweredItem}, sorted by id. */
	public static List<Item> energyItems() {
		List<Identifier> ids = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())
					&& BuiltInRegistries.ITEM.getValue(id) instanceof PoweredItem) {
				ids.add(id);
			}
		}
		ids.sort(Comparator.comparing(Identifier::toString));
		List<Item> items = new ArrayList<>(ids.size());
		for (Identifier id : ids) {
			items.add(BuiltInRegistries.ITEM.getValue(id));
		}
		return List.copyOf(items);
	}
}
