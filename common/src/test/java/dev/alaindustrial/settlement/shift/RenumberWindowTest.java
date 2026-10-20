package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.RenumberWindow.Body;
import dev.alaindustrial.settlement.shift.RenumberWindow.Observer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the rule of R41 (MOD-834): when a shift worker marked for a new number may get it — only while no
 * player can see it.
 *
 * @implements MOD-834-WINDOW
 */
class RenumberWindowTest {

	private static final double RADIUS = 176.0;

	@Test
	void aBodyAPlayerCanSeeKeepsItsNumber() {
		assertFalse(RenumberWindow.isOpen(Body.SHOWN, 0, 0, List.of(new Observer(3, 4)), RADIUS));
		assertFalse(RenumberWindow.isOpen(Body.SHOWN, 100, -50, List.of(new Observer(100 + RADIUS, -50)), RADIUS),
				"a player right on the edge can see it");
	}

	@Test
	void aBodyNobodyCanSeeGetsItsNumber() {
		assertTrue(RenumberWindow.isOpen(Body.SHOWN, 0, 0, List.of(), RADIUS), "no player in the level");
		assertTrue(RenumberWindow.isOpen(Body.SHOWN, 0, 0, List.of(new Observer(RADIUS + 1, 0)), RADIUS));
		assertTrue(RenumberWindow.isOpen(Body.SHOWN, 0, 0, List.of(new Observer(130, 130)), RADIUS),
				"the distance is round, not square: 130, 130 is 184 away");
	}

	@Test
	void oneObserverInSightIsEnough() {
		List<Observer> players = List.of(new Observer(5000, 0), new Observer(10, 10), new Observer(-5000, 0));
		assertFalse(RenumberWindow.isOpen(Body.SHOWN, 0, 0, players, RADIUS));
	}

	@Test
	void aWorkerWithoutABodyOrWithANumberNotShownYetGetsItsNumberAtOnce() {
		List<Observer> nextToIt = List.of(new Observer(0, 1));
		assertTrue(RenumberWindow.isOpen(Body.NONE, 0, 0, nextToIt, RADIUS), "dead, or its chunk not loaded");
		assertTrue(RenumberWindow.isOpen(Body.JOINING, 0, 0, nextToIt, RADIUS), "loaded, shows no number yet");
	}

	/** The game sends an entity within its tracking range, cut to the view distance; the margin comes on top. */
	@Test
	void sightRadiusIsTheGamesOwnPlusTheMargin() {
		assertEquals(160 + RenumberWindow.MARGIN, RenumberWindow.sightRadius(160, 12));
		assertEquals(4 * 16 + RenumberWindow.MARGIN, RenumberWindow.sightRadius(160, 4));
	}
}
