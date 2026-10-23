package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.TownRoadTrace.Cell;
import dev.alaindustrial.settlement.works.TownRoadTrace.Ground;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 for the course of a town road (MOD-861, R114; owner's round 1 of 0.1.219, R1-4): straight with one turn on open
 * flat ground, round a building, never more than one block of height between neighbours — cutting or filling where
 * the ground steps by more — and none where the end is walled in or a cliff is too high to cut through.
 */
class TownRoadTraceTest {

	private static final Cell START = new Cell(0, 0, 0);
	private static final Cell END = new Cell(4, 0, 3);

	/** Flat ground at height 0 everywhere. */
	private static Ground flat(int x, int z) {
		return ground(0);
	}

	private static Ground ground(int top) {
		return new Ground(top, top - TownRoadTrace.MAX_CUT, top + TownRoadTrace.MAX_FILL);
	}

	@Test
	void anOpenFieldGetsAStraightCourseWithOneTurn() {
		List<Cell> course = TownRoadTrace.route(START, END, TownRoadTraceTest::flat).orElseThrow();
		assertEquals(8, course.size(), "4 along X and 3 along Z, both ends included");
		assertEquals(START, course.get(0));
		assertEquals(END, course.get(course.size() - 1));
		assertTrue(course.stream().allMatch(c -> c.y() == 0), "flat ground keeps the road on it: " + course);
		assertWalkable(course);
		assertEquals(1, turns(course), "one turn on open ground");
	}

	@Test
	void aBuildingIsWalkedRound() {
		// A wall at x = 2 for z in -2..5 stands across the way; the road goes round it.
		TownRoadTrace.Survey wall = (x, z) -> x == 2 && z >= -2 && z <= 5 ? null : ground(0);
		List<Cell> course = TownRoadTrace.route(START, END, wall).orElseThrow();
		assertTrue(course.stream().noneMatch(c -> c.x() == 2 && c.z() >= -2 && c.z() <= 5), "no cell in the wall");
		assertEquals(END, course.get(course.size() - 1));
		assertWalkable(course);
	}

	@Test
	void aStepOfTwoBlocksIsCutOrFilledIntoSteps() {
		// The ground steps up by two at x = 3 across the whole box: no way round, the road must even it out.
		TownRoadTrace.Survey step = (x, z) -> ground(x >= 3 ? 2 : 0);
		Cell end = new Cell(6, 2, 0);
		List<Cell> course = TownRoadTrace.route(START, end, step).orElseThrow();
		assertWalkable(course);
		assertTrue(course.stream().anyMatch(c -> c.y() != (c.x() >= 3 ? 2 : 0)),
				"some cell must be cut into or filled over the ground: " + course);
	}

	@Test
	void anEndWalledInHasNoCourse() {
		TownRoadTrace.Survey ring = (x, z) -> Math.abs(x - END.x()) <= 1 && Math.abs(z - END.z()) <= 1
				&& !(x == END.x() && z == END.z()) ? null : ground(0);
		Optional<List<Cell>> course = TownRoadTrace.route(START, END, ring);
		assertTrue(course.isEmpty());
	}

	@Test
	void aCliffTooHighToCutHasNoCourse() {
		// Ten blocks of ground at x = 2..3 across the whole box: neither a cut nor a fill gets over it.
		TownRoadTrace.Survey cliff = (x, z) -> ground(x == 2 || x == 3 ? 10 : 0);
		assertTrue(TownRoadTrace.route(START, new Cell(6, 0, 0), cliff).isEmpty());
	}

	private static void assertWalkable(List<Cell> course) {
		for (int i = 1; i < course.size(); i++) {
			Cell a = course.get(i - 1);
			Cell b = course.get(i);
			assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), "cells " + a + " and " + b);
			assertTrue(Math.abs(a.y() - b.y()) <= 1, "a step of more than one block: " + a + " -> " + b);
		}
	}

	private static int turns(List<Cell> course) {
		int turns = 0;
		for (int i = 2; i < course.size(); i++) {
			int dx1 = course.get(i - 1).x() - course.get(i - 2).x();
			int dx2 = course.get(i).x() - course.get(i - 1).x();
			if (dx1 != dx2) {
				turns++;
			}
		}
		return turns;
	}
}
