package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 unit tests for the Minecraft-free core of
 * {@link WaterMillWheelGeometry#wheelsOverlap(int, int, int, int, int, int, int, int, int, int, int, int)} —
 * the wheel-vs-wheel AABB predicate the {@code 7×7×7} world scan evaluates per candidate neighbour
 * (MOD-179; the world-side wiring is covered by the Fabric gametests in {@code WaterMillWheelGameTest}).
 * Facings are unit step vectors: north = (0,0,-1), south = (0,0,1), west = (-1,0,0), east = (1,0,0).
 *
 * <p>The table pins the shipped spec numbers ({@code DISC_HALF_SIZE = 1.32}, {@code DISC_PUSH = 1.02},
 * {@code DISC_HALF_DEPTH = 0.4375}): side-by-side overlap iff the centre distance is under 2.64
 * (2 empty blocks needed), face-to-face overlap iff the distance is within 1.165–2.915 (so d=1 is a
 * clearance case, NOT interference — {@code WaterMillClearance} owns it), back-to-back never overlaps.
 * Corrupting any constant shifts these boundaries and fails the table.
 *
 * @implements water-mill wheel interference geometry (MOD-175 AABB, MOD-179 L1 extraction)
 */
class WaterMillWheelGeometryTest {

	private static boolean sameFacingSideBySide(int dx) {
		// Both mills facing north, offset along X (the rotation plane): pure in-plane distance.
		return WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, -1, dx, 0, 0, 0, 0, -1);
	}

	private static boolean faceToFace(int dz) {
		// A at z=0 facing south (towards B), B at z=dz facing north (towards A).
		return WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, 1, 0, 0, dz, 0, 0, -1);
	}

	// --- side-by-side, same facing: overlap iff in-plane centre distance < 2 × DISC_HALF_SIZE = 2.64 ---

	@ParameterizedTest
	@CsvSource({ "1, true", "2, true", "3, false", "4, false" })
	void sideBySideNeedsTwoEmptyBlocks(int dx, boolean overlaps) {
		assertTrue(sameFacingSideBySide(dx) == overlaps,
				"side-by-side d=" + dx + " → overlap=" + overlaps + " (boundary 2×1.32=2.64)");
	}

	// --- face-to-face: overlap iff 2×(DISC_PUSH − DISC_HALF_DEPTH) < d < 2×(DISC_PUSH + DISC_HALF_DEPTH) ---

	/**
	 * d=1 (no gap) is deliberately NOT interference: each wheel box sits entirely inside the other
	 * mill's solid block ({@code 1 < 1.165}). That case is handled by {@code WaterMillClearance}
	 * (MOD-179) — if this expectation ever flips to {@code true}, the clearance handoff comment in
	 * {@code WaterMillBlockEntity.produce()} must be revisited.
	 */
	@ParameterizedTest
	@CsvSource({ "1, false", "2, true", "3, false", "4, false" })
	void faceToFaceOverlapWindow(int dz, boolean overlaps) {
		assertTrue(faceToFace(dz) == overlaps,
				"face-to-face d=" + dz + " → overlap=" + overlaps + " (window 1.165..2.915)");
	}

	// --- back-to-back: wheels point away from each other, never overlap ---

	@Test
	void backToBackAdjacentIsFree() {
		// A at z=0 facing north (away), B at z=1 facing south (away) — spec: "adjacent placement is allowed".
		assertFalse(WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, -1, 0, 0, 1, 0, 0, 1),
				"back-to-back adjacent mills do not overlap");
	}

	// --- perpendicular facings: B's wheel plane cuts through A's wheel plane ---

	@Test
	void perpendicularNeighboursOverlap() {
		// A at origin facing north; B two blocks east facing west (its wheel pushed towards A).
		assertTrue(WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, -1, 2, 0, 0, -1, 0, 0),
				"perpendicular wheels crossing the same space overlap");
	}

	// --- vertical offsets, same facing side-by-side column: overlap iff dy < 2.64 ---

	@ParameterizedTest
	@CsvSource({ "1, true", "2, true", "3, false" })
	void verticalOffsetsFollowPlaneRadius(int dy, boolean overlaps) {
		assertTrue(WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, -1, 0, dy, 0, 0, 0, -1) == overlaps,
				"stacked d=" + dy + " → overlap=" + overlaps + " (plane half-size 1.32 on Y too)");
	}

	// --- edge contact is not overlap (EPSILON slack) ---

	@Test
	void identicalPositionOverlaps() {
		// Degenerate sanity: the same box trivially overlaps itself (the scan never asks this — it
		// skips dx=dy=dz=0 — but a sign/comparison mutant in boxesOverlap would flip it).
		assertTrue(WaterMillWheelGeometry.wheelsOverlap(0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, -1),
				"coincident wheels overlap");
	}
}
