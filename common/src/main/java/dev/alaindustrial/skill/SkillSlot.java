package dev.alaindustrial.skill;

/**
 * One position in a branch's lattice: entry, fork, node, fork, capstone (MOD-483).
 *
 * <p>Every branch has the same shape, so a skill is addressed as (branch, slot) rather than by a flat
 * id per node — that is what lets the fork rule, the cost ladder and the radial layout be written once
 * instead of four times.
 *
 * <pre>
 *            IN            row 0   1 point
 *          /    \
 *        A1      B1        row 1   2 points   ← hard fork
 *          \    /
 *           MID            row 2   3 points
 *          /    \
 *        A2      B2        row 3   4 points   ← hard fork
 *          \    /
 *           CAP            row 4   8 points
 * </pre>
 *
 * <p><b>The fork is hard</b> (owner decision, 2026-09-04): taking one side closes the other forever, so
 * a finished branch costs 18 points rather than 24, the whole tree costs 72, and a player who reaches
 * level 40 has 40 — exactly two capstones per run plus four points to look into a third branch.
 */
public enum SkillSlot {
	/** Entry — always reachable, cheapest, and the node that makes the branch felt at all. */
	IN(0, 1),
	/** First fork, side A. Closes {@link #B1} forever. */
	A1(1, 2),
	/** First fork, side B. Closes {@link #A1} forever. */
	B1(1, 2),
	/** The node both first-fork sides lead into — it must be worth taking whichever side was chosen. */
	MID(2, 3),
	/** Second fork, side A. Closes {@link #B2} forever. */
	A2(3, 4),
	/** Second fork, side B. Closes {@link #A2} forever. */
	B2(3, 4),
	/** Capstone — resolves the tension the branch is built on, and priced so two per run is the ceiling. */
	CAP(4, 8);

	/** Points a full path through a branch costs: entry + one side + node + one side + capstone. */
	public static final int BRANCH_PATH_COST = 18;

	private final int row;
	private final int cost;

	SkillSlot(int row, int cost) {
		this.row = row;
		this.cost = cost;
	}

	/** Lattice row, 0 at the entry — the radial layout turns this into distance from the centre. */
	public int row() {
		return row;
	}

	/** Skill points this slot costs. */
	public int cost() {
		return cost;
	}

	/**
	 * The slot on the other side of this one's fork, or {@code null} for a slot that is not a fork.
	 *
	 * <p>This single method is the whole hard-fork rule: buying a slot with a sibling closes that
	 * sibling permanently, and every other part of the system reads the answer from here.
	 */
	public SkillSlot sibling() {
		return switch (this) {
			case A1 -> B1;
			case B1 -> A1;
			case A2 -> B2;
			case B2 -> A2;
			default -> null;
		};
	}

	/**
	 * The slots that unlock this one. Reaching a slot needs <b>any</b> parent, not all of them —
	 * the two fork sides are alternatives, so demanding both would make the branch impossible.
	 */
	public SkillSlot[] parents() {
		return switch (this) {
			case IN -> NO_PARENTS;
			case A1, B1 -> new SkillSlot[] {IN};
			case MID -> new SkillSlot[] {A1, B1};
			case A2, B2 -> new SkillSlot[] {MID};
			case CAP -> new SkillSlot[] {A2, B2};
		};
	}

	private static final SkillSlot[] NO_PARENTS = {};

	/** The slot with this {@link #name()}, or {@code null} for anything unknown (old save, bad packet). */
	public static SkillSlot byKey(String key) {
		for (SkillSlot slot : values()) {
			if (slot.name().equals(key)) {
				return slot;
			}
		}
		return null;
	}
}
