package dev.alaindustrial.core.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-neutral read-only lookup for foreign storages (MOD-480), following the {@code ItemLookup}
 * shape. Vanilla containers are read through {@link net.minecraft.world.Container} directly (see
 * {@link ContainerScan}); this covers the rest — mod storages that expose only their loader's
 * capability and never implement {@code Container}.
 */
public interface ItemViewLookup {

	/** The storage exposed at {@code side} of {@code pos}, or {@code null}. */
	@Nullable
	ItemView find(Level level, BlockPos pos, @Nullable Direction side);

	ItemViewLookup[] INSTANCE = new ItemViewLookup[1];

	static void install(ItemViewLookup impl) {
		INSTANCE[0] = impl;
	}

	static ItemViewLookup get() {
		ItemViewLookup impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException(
					"ItemViewLookup not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
