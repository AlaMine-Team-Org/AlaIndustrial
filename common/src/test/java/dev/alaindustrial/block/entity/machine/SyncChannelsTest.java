package dev.alaindustrial.block.entity.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests of the GUI channel declaration (MOD-712, BE-7): indices are ordinals, the four base channels
 * come first, every constant is bound, and the read/write semantics of the hand-written switches it
 * replaced hold — an index past the channels reads 0 and ignores a write, a read-only channel ignores one.
 */
class SyncChannelsTest {

	enum Pump { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, LEVEL, ID }

	enum Wrong { CAPACITY, ENERGY, PROGRESS, MAX_PROGRESS }

	enum TooShort { ENERGY, CAPACITY }

	private static SyncChannels.Builder<Pump> base(AtomicInteger energy) {
		return SyncChannels.of(Pump.class)
				.inherit(MachineChannels.ENERGY, energy::get, energy::set)
				.inherit(MachineChannels.CAPACITY, () -> 100, null)
				.inherit(MachineChannels.PROGRESS, () -> 2, null)
				.inherit(MachineChannels.MAX_PROGRESS, () -> 3, null);
	}

	@Test
	void indicesAreOrdinalsAndTheWidthIsTheEnum() {
		AtomicInteger energy = new AtomicInteger(7);
		SyncChannels channels = base(energy).read(Pump.LEVEL, () -> 40).read(Pump.ID, () -> 5).build();
		assertEquals(6, channels.getCount());
		assertEquals(7, channels.get(Pump.ENERGY.ordinal()));
		assertEquals(40, channels.get(Pump.LEVEL.ordinal()));
		assertEquals(5, channels.get(Pump.ID.ordinal()));
	}

	@Test
	void writesReachOnlyWritableChannelsAndOutOfRangeIsInert() {
		AtomicInteger energy = new AtomicInteger(7);
		AtomicInteger level = new AtomicInteger(1);
		SyncChannels channels = base(energy).readWrite(Pump.LEVEL, level::get, level::set).read(Pump.ID, () -> 5)
				.build();
		channels.set(Pump.ENERGY.ordinal(), 9);
		channels.set(Pump.LEVEL.ordinal(), 3);
		channels.set(Pump.ID.ordinal(), 99);
		channels.set(42, 99);
		assertEquals(9, energy.get());
		assertEquals(3, level.get());
		assertEquals(5, channels.get(Pump.ID.ordinal()));
		assertEquals(0, channels.get(42));
		assertEquals(0, channels.get(-1));
	}

	@Test
	void anUnboundChannelIsRefusedByName() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> base(new AtomicInteger()).read(Pump.LEVEL, () -> 0).build());
		assertTrue(e.getMessage().contains("ID"), e.getMessage());
	}

	@Test
	void anEnumThatDoesNotStartWithTheBaseFourIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> SyncChannels.of(Wrong.class));
		assertThrows(IllegalArgumentException.class, () -> SyncChannels.of(TooShort.class));
	}

	@Test
	void aDoubleBindingIsRefusedAndOverrideReplacesReadOnly() {
		AtomicInteger energy = new AtomicInteger(7);
		assertThrows(IllegalStateException.class, () -> base(energy).read(Pump.ENERGY, () -> 1));
		SyncChannels channels = base(energy).override(Pump.ENERGY, () -> 70).read(Pump.LEVEL, () -> 0)
				.read(Pump.ID, () -> 0).build();
		channels.set(Pump.ENERGY.ordinal(), 1);
		assertEquals(70, channels.get(Pump.ENERGY.ordinal()));
		assertEquals(7, energy.get());
	}

	@Test
	void aTailFollowsTheEnumAndIsReadByOffset() {
		SyncChannels channels = base(new AtomicInteger()).read(Pump.LEVEL, () -> 0).read(Pump.ID, () -> 0)
				.tail(3, offset -> 10 + offset).build();
		assertEquals(9, channels.getCount());
		assertEquals(10, channels.get(6));
		assertEquals(12, channels.get(8));
	}

	@Test
	void permilleIsZeroOnlyWhenEmptyAndCapped() {
		assertEquals(0, SyncChannels.permille(0, 1000));
		assertEquals(1, SyncChannels.permille(1, 100_000));
		assertEquals(500, SyncChannels.permille(500, 1000));
		assertEquals(1000, SyncChannels.permille(5000, 1000));
		assertEquals(Integer.MAX_VALUE, SyncChannels.clampInt(Long.MAX_VALUE));
	}
}
