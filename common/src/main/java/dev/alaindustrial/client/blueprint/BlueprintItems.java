package dev.alaindustrial.client.blueprint;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Item icons a blueprint shows going into and out of blocks (MOD-797), cached by id. */
final class BlueprintItems {

	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private BlueprintItems() {
	}

	/** One of {@code itemId}, or an empty stack when the id is unknown. */
	static ItemStack stack(String itemId) {
		return CACHE.computeIfAbsent(itemId, id -> {
			Identifier key = Identifier.tryParse(id);
			if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
				return ItemStack.EMPTY;
			}
			return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
		});
	}
}
