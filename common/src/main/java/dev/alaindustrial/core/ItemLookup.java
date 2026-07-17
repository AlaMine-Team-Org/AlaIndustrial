package dev.alaindustrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Loader-neutral lookup for the item capability exposed on one world face. */
public interface ItemLookup {
	ItemPort find(Level level, BlockPos pos, Direction side);

	ItemLookup[] INSTANCE = new ItemLookup[1];

	static void install(ItemLookup impl) {
		INSTANCE[0] = impl;
	}

	static ItemLookup get() {
		ItemLookup impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException("ItemLookup not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
