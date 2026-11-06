package dev.alaindustrial.core.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.quarry.QuarryMotion.Leg;
import dev.alaindustrial.core.quarry.QuarryMotion.Phase;
import dev.alaindustrial.core.quarry.QuarryMotion.Pose;
import org.junit.jupiter.api.Test;

/** Where the quarry's bridge, cart and drill stand through an operation (MOD-952). */
class QuarryMotionTest {

	private static final float EPS = 1.0E-5F;
	/** From the bridge at 10.5 and the cart at 3.5 with the drill up at 70, to the cell (14, 64, 6). */
	private static final Leg LEG = new Leg(10.5F, 3.5F, 70.0F, 14.5F, 6.5F, 65.0F);

	private static void assertPose(Pose pose, float along, float across, float tip, boolean spinning) {
		assertEquals(along, pose.along(), EPS, "along");
		assertEquals(across, pose.across(), EPS, "across");
		assertEquals(tip, pose.tip(), EPS, "tip");
		assertEquals(spinning, pose.spinning(), "spinning");
	}

	@Test
	void theTravelMovesBridgeAndCartAtTheOldHeight() {
		assertPose(QuarryMotion.pose(Phase.TRAVEL, 0.0F, LEG), 10.5F, 3.5F, 70.0F, false);
		assertPose(QuarryMotion.pose(Phase.TRAVEL, 0.5F, LEG), 12.5F, 5.0F, 70.0F, false);
		assertPose(QuarryMotion.pose(Phase.TRAVEL, 1.0F, LEG), 14.5F, 6.5F, 70.0F, false);
	}

	@Test
	void theDrillIsLoweredOntoTheBlockTop() {
		assertPose(QuarryMotion.pose(Phase.LOWER, 0.0F, LEG), 14.5F, 6.5F, 70.0F, false);
		assertPose(QuarryMotion.pose(Phase.LOWER, 0.5F, LEG), 14.5F, 6.5F, 67.5F, false);
		assertPose(QuarryMotion.pose(Phase.LOWER, 1.0F, LEG), 14.5F, 6.5F, 65.0F, false);
	}

	@Test
	void theDrillBitesIntoTheBlockAndTurnsOnlyWhileDrilling() {
		assertPose(QuarryMotion.pose(Phase.DRILL, 0.0F, LEG), 14.5F, 6.5F, 65.0F, true);
		assertPose(QuarryMotion.pose(Phase.DRILL, 0.5F, LEG), 14.5F, 6.5F, 65.0F - QuarryMotion.BITE / 2, true);
		assertPose(QuarryMotion.pose(Phase.DRILL, 1.0F, LEG), 14.5F, 6.5F, 65.0F - QuarryMotion.BITE, false);
	}

	@Test
	void theDrillIsRaisedBackToTheBlockTopAndRestsThere() {
		assertPose(QuarryMotion.pose(Phase.RAISE, 0.0F, LEG), 14.5F, 6.5F, 65.0F - QuarryMotion.BITE, false);
		assertPose(QuarryMotion.pose(Phase.RAISE, 1.0F, LEG), 14.5F, 6.5F, 65.0F, false);
		assertPose(QuarryMotion.pose(Phase.IDLE, 0.0F, LEG), 14.5F, 6.5F, 65.0F, false);
	}

	@Test
	void theNextLayerStartsFromWhereTheLastOperationLeftTheDrill() {
		Pose end = QuarryMotion.pose(Phase.IDLE, 0.0F, LEG);
		Leg next = new Leg(end.along(), end.across(), end.tip(), end.along(), end.across(), 64.0F);
		assertPose(QuarryMotion.pose(Phase.TRAVEL, 0.5F, next), 14.5F, 6.5F, 65.0F, false);
		assertPose(QuarryMotion.pose(Phase.LOWER, 1.0F, next), 14.5F, 6.5F, 64.0F, false);
	}

	@Test
	void aRestingLegStaysPut() {
		Leg rest = Leg.resting(8.5F, 4.5F, 72.0F);
		for (Phase phase : Phase.values()) {
			Pose pose = QuarryMotion.pose(phase, 1.0F, rest);
			assertEquals(8.5F, pose.along(), EPS, phase.name());
			assertEquals(4.5F, pose.across(), EPS, phase.name());
		}
	}

	@Test
	void progressRunsFromTheStartAndHoldsAtTheEnd() {
		assertEquals(0.0F, QuarryMotion.progress(100L, 40, 90.0D), EPS, "before the start");
		assertEquals(0.25F, QuarryMotion.progress(100L, 40, 110.0D), EPS);
		assertEquals(1.0F, QuarryMotion.progress(100L, 40, 500.0D), EPS, "a held phase stays at its end");
		assertEquals(1.0F, QuarryMotion.progress(100L, 0, 100.0D), EPS, "a phase of no ticks is over");
	}

	@Test
	void progressBeyondTheEndsIsClamped() {
		assertPose(QuarryMotion.pose(Phase.TRAVEL, 2.0F, LEG), 14.5F, 6.5F, 70.0F, false);
		assertPose(QuarryMotion.pose(Phase.TRAVEL, -1.0F, LEG), 10.5F, 3.5F, 70.0F, false);
	}

	@Test
	void thePipeGrowsOnlyBelowItsRestingLength() {
		assertEquals(0.0F, QuarryMotion.drop(66.8F, 72.0F), EPS, "pulled up: resting length");
		assertEquals(1.8F, QuarryMotion.drop(66.8F, 65.0F), EPS);
		assertTrue(QuarryMotion.drop(66.8F, 10.0F) > 50.0F, "deep in the pit");
		assertFalse(QuarryMotion.drop(66.8F, 66.8F) > 0.0F);
	}
}
