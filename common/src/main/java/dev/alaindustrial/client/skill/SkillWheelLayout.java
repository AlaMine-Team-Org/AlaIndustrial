package dev.alaindustrial.client.skill;

import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillSlot;
import java.util.ArrayList;
import java.util.List;

/**
 * Where every node of the radial upgrade tree sits, and what is under the cursor (MOD-483).
 *
 * <p>Free of Minecraft types on purpose, for two reasons that both come down to arithmetic being worth
 * testing without a game.
 *
 * <p><b>Hit-testing.</b> The wheel's one real weakness is that nodes sit on diagonals: a rectangle per
 * node would leave dead corners between neighbours and overlap where spokes crowd near the hub.
 * {@link #nodeAt} takes the nearest centre within a radius instead — no dead zones, never two answers.
 *
 * <p><b>Pan and zoom.</b> Positions here are in <em>board</em> coordinates, measured from the hub and
 * independent of where the board is drawn or how far it is zoomed. The screen turns them into pixels
 * with {@link View}, and the same {@link View} turns a mouse position back into board space. One
 * conversion, used by both drawing and clicking, is what keeps the cursor honest at every zoom level —
 * two would drift apart the first time one of them was tweaked.
 */
public final class SkillWheelLayout {

	/** Half-width of a node diamond at 1× zoom, and the radius its click target uses. */
	public static final int NODE_RADIUS = 9;

	/** Radius of the hub plate at 1× zoom. */
	public static final int HUB_RADIUS = 18;

	/** Distance from the hub to each lattice row, at 1× zoom. */
	private static final int[] ROW_RADIUS = {30, 54, 78, 102, 122};

	/** How far a fork's two sides step off their spoke, perpendicular to it. */
	private static final int FORK_OFFSET = 15;

	/** Where a branch caption sits, measured from the hub along the spoke. */
	private static final int LABEL_RADIUS = 137;

	/** One node placed in board coordinates — {@code (0, 0)} is the hub. */
	public record Placed(SkillBranch branch, SkillSlot slot, double x, double y) {
	}

	/** One drawn connection between two placed nodes. */
	public record Edge(Placed from, Placed to) {
	}

	/**
	 * How board coordinates map to the screen right now: where the hub is drawn, how far the player has
	 * dragged, and how far they have zoomed.
	 *
	 * <p>A record rather than three loose fields on the screen so the mapping can only ever be applied
	 * one way. {@link #toScreenX}/{@link #toScreenY} go one direction, {@link #toBoardX}/
	 * {@link #toBoardY} the other, and they are exact inverses — which is the whole reason clicking
	 * still lands on the node the player sees after they have dragged and zoomed.
	 */
	public record View(double originX, double originY, double panX, double panY, double zoom) {

		public double toScreenX(double boardX) {
			return originX + panX + boardX * zoom;
		}

		public double toScreenY(double boardY) {
			return originY + panY + boardY * zoom;
		}

		public double toBoardX(double screenX) {
			return (screenX - originX - panX) / zoom;
		}

		public double toBoardY(double screenY) {
			return (screenY - originY - panY) / zoom;
		}
	}

	private final List<Placed> nodes = new ArrayList<>();
	private final List<Edge> edges = new ArrayList<>();

	/** Lay the wheel out once. The result never changes — only the {@link View} onto it does. */
	public SkillWheelLayout() {
		for (SkillBranch branch : SkillBranch.values()) {
			place(branch);
		}
	}

	/**
	 * Unit vector of a branch's spoke. The four branches take the four diagonals in enum order, which
	 * is what makes the layout symmetric without a table of angles — and what lets a fifth branch be
	 * added later by changing this one method rather than every coordinate.
	 */
	private static double[] direction(SkillBranch branch) {
		double dx = (branch.ordinal() == 0 || branch.ordinal() == 2) ? -1 : 1;
		double dy = branch.ordinal() < 2 ? -1 : 1;
		double inv = 1.0 / Math.sqrt(2.0);
		return new double[] {dx * inv, dy * inv};
	}

	private void place(SkillBranch branch) {
		double[] dir = direction(branch);
		double ux = dir[0];
		double uy = dir[1];
		// Perpendicular to the spoke — the axis the two fork sides step along.
		double px = -uy;
		double py = ux;

		Placed[] placed = new Placed[SkillSlot.values().length];
		for (SkillSlot slot : SkillSlot.values()) {
			double radius = ROW_RADIUS[slot.row()];
			double offset = switch (slot) {
				case A1, A2 -> FORK_OFFSET;
				case B1, B2 -> -FORK_OFFSET;
				default -> 0.0;
			};
			Placed node = new Placed(branch, slot,
					ux * radius + px * offset, uy * radius + py * offset);
			placed[slot.ordinal()] = node;
			nodes.add(node);
		}
		for (SkillSlot slot : SkillSlot.values()) {
			for (SkillSlot parent : slot.parents()) {
				edges.add(new Edge(placed[parent.ordinal()], placed[slot.ordinal()]));
			}
		}
	}

	/** Every node, in enum order: branch by branch, entry to capstone. */
	public List<Placed> nodes() {
		return nodes;
	}

	/** Every connection to draw, parent → child. */
	public List<Edge> edges() {
		return edges;
	}

	/** Where a branch's caption goes, in board coordinates. */
	public double[] labelPos(SkillBranch branch) {
		double[] dir = direction(branch);
		return new double[] {dir[0] * LABEL_RADIUS, dir[1] * LABEL_RADIUS};
	}

	/**
	 * The node under the cursor, or {@code null}.
	 *
	 * <p>The comparison happens in board space, so the click radius scales with the zoom exactly as the
	 * drawn diamond does: at 2× the node looks twice as big and is twice as easy to hit, which is what
	 * a player expects and what a fixed pixel radius would get wrong in both directions.
	 */
	public Placed nodeAt(double mouseX, double mouseY, View view) {
		double bx = view.toBoardX(mouseX);
		double by = view.toBoardY(mouseY);
		Placed best = null;
		double bestDistance = (double) NODE_RADIUS * NODE_RADIUS;
		for (Placed node : nodes) {
			double dx = bx - node.x();
			double dy = by - node.y();
			double distance = dx * dx + dy * dy;
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = node;
			}
		}
		return best;
	}
}
