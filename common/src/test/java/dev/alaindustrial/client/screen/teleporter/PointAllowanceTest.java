package dev.alaindustrial.client.screen.teleporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 for MOD-695: the «Stations» tab counts free points against the SERVER's limit. A player whose
 * local file says 16 on a server that allows 5 used to be shown eleven free points that the server
 * would refuse.
 */
class PointAllowanceTest {

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	/**
	 * @implements MOD-695-POINTS — "free N" is the server's limit minus the bound points
	 * @covers MOD-695
	 */
	@Test
	void freePointsCountAgainstTheServersLimit() {
		int local = Config.teleporterMaxPoints;
		int server = local + 7;
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("teleporterMaxPoints", server)).encode()));

		assertEquals(server, PointAllowance.max());
		assertEquals(server - 3, PointAllowance.free(3));
		assertEquals(0, PointAllowance.free(server + 4), "more bound than allowed is zero free, never negative");
		assertEquals(local, Config.teleporterMaxPoints, "the local Config is left alone");
	}

	/** @implements MOD-695-POINTS — before the server says anything, the local limit is used */
	@Test
	void withoutASnapshotTheLocalLimitIsUsed() {
		assertEquals(Config.teleporterMaxPoints, PointAllowance.max());
		assertEquals(Math.max(0, Config.teleporterMaxPoints - 2), PointAllowance.free(2));
	}
}
