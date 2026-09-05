package dev.alaindustrial.client.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The board's arithmetic (MOD-483), checked without a client.
 *
 * <p>This is the half of the wheel that cannot be eyeballed: a cursor that lands one node off, or lands
 * right at 1× zoom and wrong at 2×, looks like a rendering glitch and is actually a broken coordinate
 * conversion. The layout was kept free of Minecraft types precisely so these could be assertions.
 */
class SkillWheelLayoutTest {

	private static final double EPS = 1e-9;

	private final SkillWheelLayout layout = new SkillWheelLayout();

	@Test
	@DisplayName("Every branch gets every slot, and every edge joins two of them")
	void treeIsComplete() {
		int expected = SkillBranch.values().length * SkillSlot.values().length;
		assertEquals(expected, layout.nodes().size());

		// One edge per parent link: A1/B1 hang off IN, MID off both, A2/B2 off MID, CAP off both.
		int edges = 0;
		for (SkillSlot slot : SkillSlot.values()) {
			edges += slot.parents().length;
		}
		assertEquals(edges * SkillBranch.values().length, layout.edges().size());
	}

	@Test
	@DisplayName("Screen and board coordinates are exact inverses at any zoom")
	void conversionRoundTrips() {
		for (double zoom : new double[] {0.6, 1.0, 1.75, 2.2}) {
			SkillWheelLayout.View view = new SkillWheelLayout.View(300, 200, -37, 11, zoom);
			for (double board : new double[] {-122, -15, 0, 43, 137}) {
				assertEquals(board, view.toBoardX(view.toScreenX(board)), EPS,
						"x must survive the round trip at zoom " + zoom);
				assertEquals(board, view.toBoardY(view.toScreenY(board)), EPS,
						"y must survive the round trip at zoom " + zoom);
			}
		}
	}

	@Test
	@DisplayName("Clicking a node's drawn centre finds that node, dragged and zoomed")
	void clickFindsTheNodeUnderIt() {
		// Panned and zoomed hard: this is the state the naive "compare in pixels" version got wrong.
		SkillWheelLayout.View view = new SkillWheelLayout.View(320, 180, 64, -48, 1.9);
		for (SkillWheelLayout.Placed node : layout.nodes()) {
			double sx = view.toScreenX(node.x());
			double sy = view.toScreenY(node.y());
			assertSame(node, layout.nodeAt(sx, sy, view),
					"the node drawn at its own centre must be the one found there");
		}
	}

	@Test
	@DisplayName("Empty board returns nothing — a drag must not be read as a purchase")
	void emptySpaceFindsNothing() {
		SkillWheelLayout.View view = new SkillWheelLayout.View(300, 200, 0, 0, 1.0);
		// Far outside the outermost ring, where the player grabs the board to pan it.
		assertNull(layout.nodeAt(view.toScreenX(400), view.toScreenY(400), view));
	}

	@Test
	@DisplayName("The click target grows with the zoom, exactly as the drawn node does")
	void clickTargetScalesWithZoom() {
		SkillWheelLayout.Placed node = layout.nodes().getFirst();
		// A point one node-radius away in board space sits on the node's edge at every zoom, so it must
		// hit at every zoom — this is what a fixed pixel radius would get wrong in both directions.
		for (double zoom : new double[] {0.6, 1.0, 2.2}) {
			SkillWheelLayout.View view = new SkillWheelLayout.View(300, 200, 0, 0, zoom);
			double sx = view.toScreenX(node.x() + SkillWheelLayout.NODE_RADIUS - 0.5);
			double sy = view.toScreenY(node.y());
			assertNotNull(layout.nodeAt(sx, sy, view), "edge of the node must be clickable at zoom " + zoom);
		}
	}

	@Test
	@DisplayName("Each branch runs out on its own diagonal, and captions follow it")
	void branchesTakeSeparateDiagonals() {
		for (SkillBranch branch : SkillBranch.values()) {
			double[] label = layout.labelPos(branch);
			SkillWheelLayout.Placed entry = layout.nodes().stream()
					.filter(n -> n.branch() == branch && n.slot() == SkillSlot.IN)
					.findFirst().orElseThrow();
			// Caption and entry share a quadrant: the corner a caption is pinned to must be the corner
			// its branch actually points at, or the legend lies.
			assertEquals(Math.signum(entry.x()), Math.signum(label[0]), branch + " caption x side");
			assertEquals(Math.signum(entry.y()), Math.signum(label[1]), branch + " caption y side");
			assertTrue(Math.abs(label[0]) > Math.abs(entry.x()), branch + " caption sits beyond the entry");
		}
	}

	@Test
	@DisplayName("The hub is empty space: clicking the centre buys nothing")
	void hubIsNotANode() {
		SkillWheelLayout.View view = new SkillWheelLayout.View(300, 200, 0, 0, 1.0);
		assertNull(layout.nodeAt(view.toScreenX(0), view.toScreenY(0), view));
	}
}
