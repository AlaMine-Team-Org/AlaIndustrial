package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.SettlementAccess.Standing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 for who may use an ALA town's machines (owner's round 3, point 3; epic decision R98): a town nobody has woken is
 * open to all, a woken one to its restorer and members, an operator in creative looks without controlling, and every
 * other player is a stranger.
 */
class SettlementAccessTest {

	private static final UUID RESTORER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000003");

	@Test
	void aTownNobodyWokeIsOpenToEverybodySoTheFirstOpenerCanWakeIt() {
		Standing standing = SettlementAccess.standing(Optional.empty(), STRANGER, false);
		assertEquals(Standing.UNCLAIMED, standing);
		assertTrue(standing.admitted());
		assertTrue(standing.controls());
	}

	@Test
	void theRestorerIsAMemberAndControlsTheTown() {
		Standing standing = SettlementAccess.standing(Optional.of(RESTORER), RESTORER, false);
		assertEquals(Standing.MEMBER, standing);
		assertTrue(SettlementAccess.admits(Optional.of(RESTORER), RESTORER, false));
		assertTrue(SettlementAccess.controls(Optional.of(RESTORER), RESTORER));
	}

	@Test
	void anyOtherPlayerIsAStrangerAndIsTurnedAway() {
		assertEquals(Standing.STRANGER, SettlementAccess.standing(Optional.of(RESTORER), STRANGER, false));
		assertFalse(SettlementAccess.admits(Optional.of(RESTORER), STRANGER, false));
		assertFalse(SettlementAccess.controls(Optional.of(RESTORER), STRANGER));
	}

	@Test
	void anOperatorInCreativeLooksButControlsNothing() {
		Standing standing = SettlementAccess.standing(Optional.of(RESTORER), STRANGER, true);
		assertEquals(Standing.INSPECTOR, standing);
		assertTrue(standing.admitted());
		assertFalse(standing.controls());
	}

	@Test
	void theRestorerIsAMemberEvenWhenTheyAreAlsoAnOperator() {
		assertEquals(Standing.MEMBER, SettlementAccess.standing(Optional.of(RESTORER), RESTORER, true));
	}

	@Test
	void aMemberAddedLaterPlugsInWithoutChangingTheRule() {
		Standing standing = SettlementAccess.standing(Optional.of(RESTORER), List.of(MEMBER), MEMBER, false);
		assertEquals(Standing.MEMBER, standing);
		assertTrue(standing.controls());
		assertEquals(Standing.STRANGER,
				SettlementAccess.standing(Optional.of(RESTORER), List.of(MEMBER), STRANGER, false));
	}
}
