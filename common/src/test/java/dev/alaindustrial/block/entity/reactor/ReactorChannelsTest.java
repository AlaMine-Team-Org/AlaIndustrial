package dev.alaindustrial.block.entity.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.block.entity.machine.MachineChannels;
import dev.alaindustrial.core.structure.RoomScan;
import org.junit.jupiter.api.Test;

/**
 * L1 pin of the reactor console's channel indices (MOD-713 batch 1f): the index of a channel is the wire
 * format between the controller and the console, so moving a constant in {@link ReactorChannels} must fail
 * here before it reaches a player's screen. The values are the ones the hand-numbered {@code DATA_*}
 * constants carried before the batch.
 */
class ReactorChannelsTest {

	@Test
	void theBaseFourComeFirst() {
		for (MachineChannels base : MachineChannels.values()) {
			assertEquals(base.name(), ReactorChannels.values()[base.ordinal()].name());
		}
	}

	@Test
	void theNamedChannelsKeepTheirIndices() {
		assertEquals(4, ReactorChannels.STATUS.ordinal());
		assertEquals(11, ReactorChannels.HEAT_PERCENT.ordinal());
		assertEquals(20, ReactorChannels.ENERGY_HUNDREDS.ordinal());
		assertEquals(24, ReactorChannels.COOLANT_SHARE.ordinal());
		assertEquals(27, ReactorChannels.BOX_WEST.ordinal());
		assertEquals(32, ReactorChannels.HOLE_COUNT.ordinal());
	}

	@Test
	void theHolesFollowTheNamedChannelsThreeEach() {
		assertEquals(33, ReactorChannels.HOLE_FIRST);
		assertEquals(33 + 3 * RoomScan.MAX_LISTED_HOLES, ReactorChannels.COUNT);
		assertEquals(69, ReactorChannels.COUNT);
	}
}
