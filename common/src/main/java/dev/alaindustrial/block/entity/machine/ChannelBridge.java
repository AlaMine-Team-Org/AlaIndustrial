package dev.alaindustrial.block.entity.machine;

import java.util.function.Supplier;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;

/**
 * A block entity's {@link SyncChannels} as the vanilla {@link ContainerData} a menu binds (MOD-235,
 * MOD-712 BE-7) — the one adapter between the two, kept apart from {@code SyncChannels} so that class stays
 * free of Minecraft and L1 can test it.
 *
 * <p>Held by the machine base as one field: the channels are built once, on the first {@link #data()}
 * call, from the factory it was handed — a server-side object; a client menu has a stub of the same width.
 */
public final class ChannelBridge {

	private final Supplier<SyncChannels> factory;
	private @Nullable ContainerData data;

	/** @param factory builds the channels; called once, on first use */
	public ChannelBridge(Supplier<SyncChannels> factory) {
		this.factory = factory;
	}

	/** The channels as {@link ContainerData}, built on the first call. */
	public ContainerData data() {
		if (data == null) {
			data = of(factory.get());
		}
		return data;
	}

	/** {@code channels} as the vanilla {@link ContainerData} a menu binds. */
	public static ContainerData of(SyncChannels channels) {
		return new ContainerData() {
			@Override
			public int get(int index) {
				return channels.get(index);
			}

			@Override
			public void set(int index, int value) {
				channels.set(index, value);
			}

			@Override
			public int getCount() {
				return channels.getCount();
			}
		};
	}
}
