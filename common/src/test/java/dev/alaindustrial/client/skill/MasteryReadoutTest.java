package dev.alaindustrial.client.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.stats.LevelMath;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 characterization of MOD-695 (finding CFG-5): the mastery level a client shows on the dashboard and
 * in the skill tree is the SERVER's, not the one its own config file would give.
 *
 * <p>The scenario of the review: a dedicated server runs {@code euPerXp} ten times the default, the
 * player's local file is untouched. Before the fix the dashboard computed the level from the local
 * {@code Config.euPerXp} and showed a rank the server does not agree with.
 */
class MasteryReadoutTest {

	/** A million EU of machine work and nothing generated: enough to tell the two rates apart. */
	private static final long CONSUMED = 1_000_000L;

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	/**
	 * @implements MOD-695-LEVEL — the dashboard's level follows the server's XP rate, not the local file
	 * @covers MOD-695
	 */
	@Test
	void levelShownFollowsTheServersXpRate() {
		int localRate = Config.euPerXp;
		int serverRate = localRate * 10;
		int localLevel = LevelMath.levelForXp(LevelMath.xpOf(CONSUMED, 0, localRate, Config.euPerXpGenerated),
				Config.xpLevelOneCost, Config.levelXpMultiplier);
		int serverLevel = LevelMath.levelForXp(LevelMath.xpOf(CONSUMED, 0, serverRate, Config.euPerXpGenerated),
				Config.xpLevelOneCost, Config.levelXpMultiplier);
		assertNotEquals(localLevel, serverLevel, "the fixture must put the two rates on different levels");

		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("euPerXp", serverRate)).encode()));

		assertEquals(serverLevel, MasteryReadout.level(CONSUMED, 0, 0),
				"the dashboard must show the level the server computes, not the local file's " + localLevel);
		assertEquals(LevelMath.xpOf(CONSUMED, 0, serverRate, Config.euPerXpGenerated), MasteryReadout.xp(CONSUMED, 0));
		assertEquals(localRate, Config.euPerXp, "receiving the server's snapshot must not rewrite the local Config");
	}

	/** @implements MOD-695-LEVEL — with no snapshot from the server the client shows its local numbers */
	@Test
	void withoutASnapshotTheLocalNumbersStay() {
		int expected = LevelMath.levelForXp(LevelMath.xpOf(CONSUMED, 0, Config.euPerXp, Config.euPerXpGenerated),
				Config.xpLevelOneCost, Config.levelXpMultiplier);
		assertEquals(expected, MasteryReadout.level(CONSUMED, 0, 0));
	}

	/** @implements MOD-695-LEVEL — a rank already reached is never shown lower */
	@Test
	void theRankFloorStillHolds() {
		assertEquals(LevelMath.MAX_LEVEL, MasteryReadout.level(0, 0, LevelMath.MAX_LEVEL));
		assertEquals(1, MasteryReadout.level(0, 0, 0));
	}
}
