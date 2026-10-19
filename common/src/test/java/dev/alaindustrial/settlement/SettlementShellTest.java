package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1: the border shell the core's "show borders" button draws (MOD-843, MOD-848) has exactly the territory's shape —
 * a square or a structure's rectangle — and the same walls and dome from inside and from outside (epic decision R60):
 * filled with a floor from outside, only the grid of the walls and the dome from inside; and it stays cheap enough to
 * build every frame.
 *
 * @implements MOD-843-SHELL
 * @implements MOD-848-SHELL
 */
class SettlementShellTest {

	private static final int CX = 10;
	private static final int CY = 64;
	private static final int CZ = -5;
	private static final int R = 32;

	/** The LV square round the core. */
	private static final SettlementTerritory.Area SQUARE = SettlementTerritory.Area.square(CX, CZ, R);
	/** A desert town along its pipe: a structure's rectangle, much longer east than the square. */
	private static final SettlementTerritory.Area TOWN = SettlementTerritory.area(CX, CZ, R,
			Optional.of(new SettlementTerritory.Area(CX - 6, CZ - 20, CX + 66, CZ + 18)), 8);

	/** Collects what the shell emits. */
	private static final class Collect implements SettlementShell.Sink {
		final List<SettlementShell.Point[]> quads = new ArrayList<>();
		final List<SettlementShell.Point[]> lines = new ArrayList<>();

		@Override
		public void quad(SettlementShell.Point a, SettlementShell.Point b, SettlementShell.Point c,
				SettlementShell.Point d) {
			quads.add(new SettlementShell.Point[] {a, b, c, d});
		}

		@Override
		public void line(SettlementShell.Point a, SettlementShell.Point b) {
			lines.add(new SettlementShell.Point[] {a, b});
		}
	}

	/** A block is inside the walls exactly when the territory holds it, on every edge and corner, of either shape. */
	@Test
	void wallsStandOnTheTerritoryEdge() {
		for (SettlementTerritory.Area area : List.of(SQUARE, TOWN)) {
			SettlementShell.Box box = SettlementShell.box(area, CY);
			for (int x = area.minX() - 3; x <= area.maxX() + 3; x++) {
				for (int z = area.minZ() - 3; z <= area.maxZ() + 3; z++) {
					boolean between = x >= box.minX() && x + 1 <= box.maxX() && z >= box.minZ() && z + 1 <= box.maxZ();
					assertEquals(area.contains(x, z), between, "block " + x + ", " + z + " of " + area);
				}
			}
			assertEquals(CY - SettlementShell.FLOOR_DEPTH, box.minY());
			assertEquals(CY + 1 + SettlementShell.CEILING_HEIGHT, box.maxY());
		}
		SettlementShell.Box square = SettlementShell.box(SQUARE, CY);
		for (int x = CX - R - 3; x <= CX + R + 3; x++) {
			boolean between = x >= square.minX() && x + 1 <= square.maxX();
			assertEquals(SettlementTerritory.contains(CX, CZ, R, x, CZ), between, "a town without a structure, x " + x);
		}
	}

	/**
	 * R60: the dome is there from inside and from outside, rising to the same apex; there is no flat ceiling either
	 * way. From outside the shell is filled and has its floor; from inside it is the grid alone — no quad at all, so
	 * no floor and no fill.
	 */
	@Test
	void oneShapeFromInsideAndOutsideOnlyTheGridFromInside() {
		for (SettlementTerritory.Area area : List.of(SQUARE, TOWN)) {
			SettlementShell.Box box = SettlementShell.box(area, CY);
			Collect outside = new Collect();
			SettlementShell.emit(box, area.maxX() + 20.0, CY + 1.6, CZ, outside);
			assertEquals(box.maxY() + box.domeRise(), highest(outside.quads), 1.0e-9, "the dome's apex from outside");
			assertEquals(0, flatAt(outside.quads, box.maxY()), "no flat ceiling from outside");
			assertTrue(flatAt(outside.quads, box.minY()) > 0, "the floor shows from outside");

			Collect inside = new Collect();
			SettlementShell.emit(box, CX + 0.5, CY + 1.6, CZ + 0.5, inside);
			assertEquals(List.of(), inside.quads, "nothing filled from inside: no wall glass, no floor, no dome glass");
			assertEquals(box.maxY() + box.domeRise(), highest(inside.lines), 1.0e-9, "the dome's apex from inside");
			assertEquals(0, flatLinesAt(inside.lines, box.minY(), box), "no floor grid from inside");
			assertEquals(lineSet(outsideLinesOnlyWallsAndDome(box)), lineSet(inside.lines),
					"from inside the same walls and dome as from outside, as lines");
		}
	}

	/** Inside is under the dome as well as between the walls: a player on a roof under the dome still sees lines. */
	@Test
	void underTheDomeIsInsideAboveItIsOutside() {
		SettlementShell.Box box = SettlementShell.box(TOWN, CY);
		double cx = (box.minX() + box.maxX()) / 2.0;
		double cz = (box.minZ() + box.maxZ()) / 2.0;
		assertTrue(box.encloses(cx, box.maxY() + box.domeRise() - 0.5, cz), "just under the apex");
		assertFalse(box.encloses(cx, box.maxY() + box.domeRise() + 0.5, cz), "just over the apex");
		assertFalse(box.encloses(box.minX() + 0.1, box.maxY() + 3.0, cz), "over the dome's foot at a wall");
		assertTrue(box.encloses(box.minX() + 0.5, box.maxY() - 1.0, cz), "under the wall's top");
		assertFalse(box.encloses(cx, box.minY() - 1.0, cz), "under the floor");
		assertFalse(box.encloses(box.maxX() + 1.0, CY, cz), "past a wall");
		Collect above = new Collect();
		SettlementShell.emit(box, cx, box.maxY() + 200.0, cz, above);
		assertFalse(above.quads.isEmpty(), "seen from above the shell is filled glass");
	}

	/** The dome's foot is the top edge of the walls, its apex the centre: one shell with no seam, over a rectangle. */
	@Test
	void domeMeetsTheWallsAndStaysInsideTheRectangle() {
		for (SettlementTerritory.Area area : List.of(SQUARE, TOWN)) {
			SettlementShell.Box box = SettlementShell.box(area, CY);
			Collect outside = new Collect();
			SettlementShell.emit(box, area.maxX() + 1000.0, CY, CZ, outside);
			for (SettlementShell.Point[] quad : outside.quads) {
				for (SettlementShell.Point p : quad) {
					assertTrue(p.x() >= box.minX() - 1.0e-9 && p.x() <= box.maxX() + 1.0e-9, "x " + p);
					assertTrue(p.z() >= box.minZ() - 1.0e-9 && p.z() <= box.maxZ() + 1.0e-9, "z " + p);
					if (Math.abs(p.y() - box.maxY()) < 1.0e-9) {
						boolean onEdge = Math.abs(p.x() - box.minX()) < 1.0e-9
								|| Math.abs(p.x() - box.maxX()) < 1.0e-9
								|| Math.abs(p.z() - box.minZ()) < 1.0e-9 || Math.abs(p.z() - box.maxZ()) < 1.0e-9;
						assertTrue(onEdge, "a dome point at the foot must lie on the wall top: " + p);
					}
				}
			}
			SettlementShell.Point apex = SettlementShell.ring((box.minX() + box.maxX()) / 2,
					(box.minZ() + box.maxZ()) / 2, box.maxY(), box.domeRise(), box.minX(), box.minZ(), 10, 10);
			assertEquals((box.minX() + box.maxX()) / 2, apex.x(), 1.0e-9);
			assertEquals((box.minZ() + box.maxZ()) / 2, apex.z(), 1.0e-9);
		}
		SettlementShell.Box town = SettlementShell.box(TOWN, CY);
		assertEquals(Math.min(town.maxX() - town.minX(), town.maxZ() - town.minZ()) / 2.0 * SettlementShell.DOME_RISE,
				town.domeRise(), 1.0e-9, "a rectangle's dome rises a share of its shorter half-side");
	}

	/** No piece is longer than {@link SettlementShell#PIECE}: the gizmo pass sorts quads by distance (MOD-665). */
	@Test
	void piecesAreShortAndTheFrameIsCheap() {
		List<SettlementTerritory.Area> areas = new ArrayList<>();
		for (int radius : new int[] {32, 48, 64, 96}) {
			areas.add(SettlementTerritory.Area.square(CX, CZ, radius));
		}
		areas.add(TOWN);
		for (SettlementTerritory.Area area : areas) {
			SettlementShell.Box box = SettlementShell.box(area, CY);
			for (boolean in : new boolean[] {true, false}) {
				Collect sink = new Collect();
				SettlementShell.emit(box, in ? CX : area.maxX() + 500.0, CY, CZ, sink);
				for (SettlementShell.Point[] quad : sink.quads) {
					for (int i = 0; i < 4; i++) {
						assertTrue(distance(quad[i], quad[(i + 1) % 4]) <= SettlementShell.PIECE + 1.0e-6,
								"a quad edge longer than a piece of " + area);
					}
				}
				for (SettlementShell.Point[] line : sink.lines) {
					assertTrue(line[0].y() >= box.minY() - 1.0e-9, "a line under the floor");
				}
				if (area.equals(SQUARE) || area.equals(TOWN)) {
					// The LV town and a structure's town: walls, floor and dome — hundreds of quads, not thousands.
					assertTrue(sink.quads.size() <= 1400, area + " shell quads: " + sink.quads.size());
					assertFalse(sink.lines.isEmpty());
				}
			}
		}
	}

	/** The lines the shell draws from outside, less the floor's own grid: the walls' and the dome's. */
	private static List<SettlementShell.Point[]> outsideLinesOnlyWallsAndDome(SettlementShell.Box box) {
		Collect outside = new Collect();
		SettlementShell.emit(box, box.maxX() + 1000.0, CY, CZ, outside);
		List<SettlementShell.Point[]> kept = new ArrayList<>();
		for (SettlementShell.Point[] line : outside.lines) {
			boolean floorGrid = Math.abs(line[0].y() - box.minY()) < 1.0e-9
					&& Math.abs(line[1].y() - box.minY()) < 1.0e-9 && !sameWall(line[0], line[1], box);
			if (!floorGrid) {
				kept.add(line);
			}
		}
		return kept;
	}

	/** Whether both ends lie in the plane of one wall. */
	private static boolean sameWall(SettlementShell.Point a, SettlementShell.Point b, SettlementShell.Box box) {
		return Math.abs(a.x() - b.x()) < 1.0e-9 && (Math.abs(a.x() - box.minX()) < 1.0e-9
				|| Math.abs(a.x() - box.maxX()) < 1.0e-9)
				|| Math.abs(a.z() - b.z()) < 1.0e-9 && (Math.abs(a.z() - box.minZ()) < 1.0e-9
				|| Math.abs(a.z() - box.maxZ()) < 1.0e-9);
	}

	/** Lines as comparable strings, both ends rounded, in any order. */
	private static List<String> lineSet(List<SettlementShell.Point[]> lines) {
		List<String> out = new ArrayList<>();
		for (SettlementShell.Point[] line : lines) {
			out.add(key(line[0]) + " " + key(line[1]));
		}
		out.sort(String::compareTo);
		return out;
	}

	private static String key(SettlementShell.Point p) {
		return Math.round(p.x() * 1000) + "," + Math.round(p.y() * 1000) + "," + Math.round(p.z() * 1000);
	}

	private static double highest(List<SettlementShell.Point[]> shapes) {
		double top = Double.NEGATIVE_INFINITY;
		for (SettlementShell.Point[] shape : shapes) {
			for (SettlementShell.Point p : shape) {
				top = Math.max(top, p.y());
			}
		}
		return top;
	}

	/** Quads lying flat (all four corners) at height {@code y}. */
	private static int flatAt(List<SettlementShell.Point[]> quads, double y) {
		int count = 0;
		for (SettlementShell.Point[] quad : quads) {
			boolean flat = true;
			for (SettlementShell.Point p : quad) {
				flat &= Math.abs(p.y() - y) < 1.0e-9;
			}
			count += flat ? 1 : 0;
		}
		return count;
	}

	/** Lines at height {@code y} that run across the box's interior, as a floor's grid does — not along a wall. */
	private static int flatLinesAt(List<SettlementShell.Point[]> lines, double y, SettlementShell.Box box) {
		int count = 0;
		for (SettlementShell.Point[] line : lines) {
			boolean flat = Math.abs(line[0].y() - y) < 1.0e-9 && Math.abs(line[1].y() - y) < 1.0e-9;
			count += flat && !sameWall(line[0], line[1], box) ? 1 : 0;
		}
		return count;
	}

	private static double distance(SettlementShell.Point a, SettlementShell.Point b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
