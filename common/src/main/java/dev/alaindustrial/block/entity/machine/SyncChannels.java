package dev.alaindustrial.block.entity.machine;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * A machine's GUI sync channels, declared once (MOD-712, BE-7): built once from a reader (and, where the
 * vanilla sync writes back, a writer) per constant of the machine's channel enum. The machine base hands it
 * to a menu as the vanilla {@code ContainerData}; this class itself knows nothing of Minecraft, so L1 tests it.
 *
 * <p>Before this every machine wrote an anonymous {@code ContainerData}: a {@code switch} over bare indices
 * in {@code get}, a second one in {@code set}, a hand-counted {@code DATA_COUNT}, and a comment saying what
 * each index meant — four places for one number on a panel, kept in step by hand. Now the enum IS the
 * index map: a channel's index is its constant's ordinal, the width is the enum's size, and a menu reads a
 * channel by its name ({@code MachineMenu.channel}). The indices themselves are unchanged — every channel
 * enum starts with the four of {@link MachineChannels}, and the builder refuses one that does not.
 *
 * <p>An index past the declared channels reads 0 and ignores a write, as the hand-written switches did.
 *
 * <p><b>Every channel must fit a signed 16-bit short.</b> {@code ClientboundContainerSetDataPacket} writes
 * each value with {@code writeShort}, so anything outside {@code -32768..32767} arrives truncated to its
 * low 16 bits. That is why a tank level travels as a {@link #permille}, a fluid as its registry id, and a
 * colour never: a packed ARGB is 32 bits (lava's red once arrived as 0).
 */
public final class SyncChannels {

	private final IntSupplier[] readers;
	private final @Nullable IntConsumer[] writers;

	private SyncChannels(IntSupplier[] readers, @Nullable IntConsumer[] writers) {
		this.readers = readers;
		this.writers = writers;
	}

	/** Start declaring the channels of {@code channels}, one binding per constant. */
	public static <C extends Enum<C>> Builder<C> of(Class<C> channels) {
		return new Builder<>(channels, true);
	}

	/**
	 * Channels of a screen that is not a machine's — no energy bar, no progress (the teleporter station's
	 * view): the enum need not start with the four of {@link MachineChannels}.
	 */
	public static <C extends Enum<C>> Builder<C> standalone(Class<C> channels) {
		return new Builder<>(channels, false);
	}

	/** The value of channel {@code index}; 0 past the declared channels. */
	public int get(int index) {
		return index >= 0 && index < readers.length ? readers[index].getAsInt() : 0;
	}

	/** Write channel {@code index} when it takes a write; otherwise, or past the channels, nothing. */
	public void set(int index, int value) {
		if (index >= 0 && index < writers.length && writers[index] != null) {
			writers[index].accept(value);
		}
	}

	/** How many channels: the enum's constants plus a {@link Builder#tail}. */
	public int getCount() {
		return readers.length;
	}

	/** {@code value} clamped into an {@code int}: a channel is 32 bits, a buffer or a bank is 64. */
	public static int clampInt(long value) {
		return (int) Math.min(Integer.MAX_VALUE, value);
	}

	/**
	 * How full a tank is, in permille of {@code capacity}: 0 only when empty, at least 1 otherwise (a drop
	 * of fluid still draws a line), at most 1000. A channel syncs a {@code short}, so a raw mB amount would
	 * arrive truncated — this is why a tank level travels as a permille.
	 */
	public static int permille(long amount, long capacity) {
		return amount <= 0 ? 0 : Math.max(1, (int) Math.min(amount * 1000L / capacity, 1000));
	}

	/** Binds every constant of a channel enum, then {@link #build()}s the {@link SyncChannels}. */
	public static final class Builder<C extends Enum<C>> {
		private final C[] constants;
		private final IntSupplier[] readers;
		private final @Nullable IntConsumer[] writers;
		private int tailCount;
		private @Nullable IntUnaryOperator tailReader;

		private Builder(Class<C> type, boolean machine) {
			this.constants = type.getEnumConstants();
			this.readers = new IntSupplier[constants.length];
			this.writers = new IntConsumer[constants.length];
			for (MachineChannels channel : machine ? MachineChannels.values() : new MachineChannels[0]) {
				int index = channel.ordinal();
				if (constants.length <= index || !constants[index].name().equals(channel.name())) {
					throw new IllegalArgumentException(type.getName() + " must start with the channels of"
							+ " MachineChannels (ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS), in that order");
				}
			}
		}

		/** A channel the server computes and nothing writes back. */
		public Builder<C> read(C channel, IntSupplier reader) {
			return bind(channel.ordinal(), reader, null);
		}

		/** A channel a write sets — the vanilla sync on the client side, or a test. */
		public Builder<C> readWrite(C channel, IntSupplier reader, IntConsumer writer) {
			return bind(channel.ordinal(), reader, writer);
		}

		/**
		 * Bind this enum's constant named like {@code inherited} — a channel a base class declares in an enum
		 * of its own ({@link MachineChannels}, a processing machine's status) — which must sit at the same index.
		 */
		public Builder<C> inherit(Enum<?> inherited, IntSupplier reader, @Nullable IntConsumer writer) {
			int index = inherited.ordinal();
			if (index >= constants.length || !constants[index].name().equals(inherited.name())) {
				throw new IllegalArgumentException(constants.getClass().getComponentType().getName() + " has no "
						+ inherited.name() + " at index " + index + ", where "
						+ inherited.getClass().getSimpleName() + " puts it");
			}
			return bind(index, reader, writer);
		}

		/**
		 * Re-bind an already bound channel as a read-only one: a machine whose base channel means something
		 * else (the CESU sends its energy in hundreds, the lightning rod a fixed bar length).
		 */
		public Builder<C> override(C channel, IntSupplier reader) {
			if (readers[channel.ordinal()] == null) {
				throw new IllegalStateException(channel + " is not bound yet — override replaces a binding");
			}
			readers[channel.ordinal()] = reader;
			writers[channel.ordinal()] = null;
			return this;
		}

		/**
		 * {@code count} more read-only channels after the enum's, read by their offset from its end: a list
		 * whose length is a constant but whose entries have no names (the reactor's breach offsets).
		 */
		public Builder<C> tail(int count, IntUnaryOperator reader) {
			this.tailCount = count;
			this.tailReader = reader;
			return this;
		}

		private Builder<C> bind(int index, IntSupplier reader, @Nullable IntConsumer writer) {
			if (readers[index] != null) {
				throw new IllegalStateException(constants[index] + " is bound twice — use override to replace it");
			}
			readers[index] = reader;
			writers[index] = writer;
			return this;
		}

		/** The channels; fails naming the first constant nobody bound. */
		public SyncChannels build() {
			IntSupplier[] allReaders = new IntSupplier[constants.length + tailCount];
			@Nullable IntConsumer[] allWriters = new IntConsumer[constants.length + tailCount];
			for (int i = 0; i < constants.length; i++) {
				if (readers[i] == null) {
					throw new IllegalStateException("channel " + constants[i] + " has no reader");
				}
				allReaders[i] = readers[i];
				allWriters[i] = writers[i];
			}
			for (int i = 0; i < tailCount; i++) {
				int offset = i;
				IntUnaryOperator tail = tailReader;
				allReaders[constants.length + i] = () -> tail.applyAsInt(offset);
			}
			return new SyncChannels(allReaders, allWriters);
		}
	}
}
