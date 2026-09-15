package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 unit tests for {@link WindMillRotorGeometry#discsOverlap} — the disc-vs-disc predicate the
 * wind mill interference scan evaluates per candidate neighbour (MOD-634; the world-side wiring is
 * covered by the wind mill gametests). Facings are unit step vectors: north = (0,0,-1),
 * south = (0,0,1), west = (-1,0,0), east = (1,0,0).
 *
 * <p>The table pins where the disc is DRAWN: 0.58 from the block centre, 0.08 in front of the face.
 * Face-to-face mills overlap iff their centres are within {@code 2 × (DISC_PUSH ± DISC_HALF_DEPTH)},
 * that is 0.96–1.36 blocks — so adjacent mills overlap and mills across one air block do not. The
 * shipped check used 1.08 (the disc centre measured from the block corner, added to the block centre),
 * which moved that window to 1.96–2.36 and stalled mills across one air block whose drawn discs are
 * 0.84 apart; every row marked "moved" below goes red on that value.
 *
 * @implements wind-mill rotor interference geometry (MOD-051 AABB, MOD-634 drawn disc position)
 */
class WindMillRotorGeometryTest {

	private static boolean sameFacingSideBySide(int dx) {
		// Both mills facing north, offset along X (the rotation plane): pure in-plane distance.
		return WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, dx, 0, 0, 0, 0, -1);
	}

	private static boolean faceToFace(int dz) {
		// A at z=0 facing south (towards B), B at z=dz facing north (towards A).
		return WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, 1, 0, 0, dz, 0, 0, -1);
	}

	// --- side-by-side, same facing: overlap iff in-plane centre distance < 2 × DISC_HALF_SIZE = 2 ---

	@ParameterizedTest
	@CsvSource({ "1, true", "2, false", "3, false" })
	void sideBySideNeedsOneEmptyBlock(int dx, boolean overlaps) {
		assertTrue(sameFacingSideBySide(dx) == overlaps,
				"side-by-side d=" + dx + " → overlap=" + overlaps + " (boundary 2×1.0=2, edge contact is free)");
	}

	// --- face-to-face: overlap iff 2×(DISC_PUSH − DISC_HALF_DEPTH) < d < 2×(DISC_PUSH + DISC_HALF_DEPTH) ---

	/**
	 * d=1 overlaps (moved: 1.08 said it did not) but never shows up as interference in-world: each disc
	 * sits inside the other mill's block, so {@code WindMillClearance} reports both mills obstructed first.
	 * d=2 — one air block between — is free (moved: 1.08 said it overlapped).
	 */
	@ParameterizedTest
	@CsvSource({ "1, true", "2, false", "3, false" })
	void faceToFaceNeedsOneEmptyBlock(int dz, boolean overlaps) {
		assertTrue(faceToFace(dz) == overlaps,
				"face-to-face d=" + dz + " → overlap=" + overlaps + " (window 0.96..1.36)");
	}

	// --- back-to-back: discs point away from each other, never overlap ---

	@Test
	void backToBackAdjacentIsFree() {
		// A at z=0 facing north (away), B at z=1 facing south (away) — spec: back to back may touch.
		assertFalse(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 0, 0, 1, 0, 0, 1),
				"back-to-back adjacent mills do not overlap");
	}

	// --- perpendicular facings ---

	@Test
	void perpendicularNeighbourTwoBlocksAsideIsFree() {
		// Moved. A at origin facing north; B two blocks east facing west. B's disc plane is at x=1.92,
		// past the east edge of A's disc (x=1.5); the 1.08 box put it at x=1.42, inside A's disc.
		assertFalse(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 2, 0, 0, -1, 0, 0),
				"perpendicular disc two blocks aside stays clear of the drawn disc");
	}

	@Test
	void perpendicularNeighbourDiagonallyInFrontOverlaps() {
		// A at origin facing north; B one block east and one north, facing west: its disc cuts A's.
		assertTrue(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 1, 0, -1, -1, 0, 0),
				"perpendicular discs crossing the same cell overlap");
	}

	// --- face-to-face, one block up: the parallel planes sit 0.16 apart, inside the box tolerance ---

	@Test
	void faceToFaceOneBlockUpOverlaps() {
		// Moved. A at origin facing north; B one block up and one north, facing south. Discs 0.16 apart
		// with a 1×1 block overlap in the plane; the 1.08 boxes were 1.16 apart and missed it.
		assertTrue(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 0, 1, -1, 0, 0, 1),
				"face-to-face discs one block apart vertically overlap");
	}

	// --- vertical offsets, same facing: overlap iff dy < 2 ---

	@ParameterizedTest
	@CsvSource({ "1, true", "2, false" })
	void verticalOffsetsFollowPlaneHalfSize(int dy, boolean overlaps) {
		assertTrue(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 0, dy, 0, 0, 0, -1) == overlaps,
				"stacked d=" + dy + " → overlap=" + overlaps + " (plane half-size 1.0 on Y too)");
	}

	@Test
	void identicalPositionOverlaps() {
		// Degenerate sanity: the scan never asks this (it skips its own cell), but a sign or comparison
		// mutant in boxesOverlap would flip it.
		assertTrue(WindMillRotorGeometry.discsOverlap(0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, -1),
				"coincident discs overlap");
	}
}
