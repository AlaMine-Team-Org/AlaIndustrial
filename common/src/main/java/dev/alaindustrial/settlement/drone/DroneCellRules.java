package dev.alaindustrial.settlement.drone;

/**
 * What a builder drone does with one cell of a building's plan (MOD-858; epic MOD-824, decisions R85, R93): nothing,
 * take the block away, put the plan's block there, or stop because something it may not touch is in the way. The
 * world-reading half ({@code DroneCellSurvey}) turns a cell into a {@link Cell}; the answer is decided here.
 *
 * <p><b>Clearing (R85, R93)</b> takes away only rubble — a block the survey calls removable (on the debris list, the
 * ruin's own block where the intact building has another, or a block placing replaces anyway, grass and the like) —
 * and liquid where the plan wants air. A block it may not take away where the plan wants air stops the job: «obstructed
 * by X». A wrong block where the plan wants a block is the building job's business, not the clearing's.
 *
 * <p><b>Building</b> puts the plan's block on air, on liquid or on something removable, and takes a removable block
 * away where the plan wants air; anything else in the way stops the job.
 *
 * <p><b>Never touched.</b> A cell whose block is protected — a block with data, the core, the capsule, the dispatcher,
 * a nest, the lab and its hatch — is passed by in both jobs, and so is a cell whose plan block is one the drone does
 * not make (the core, the capsule, the dispatcher, a nest) or has no item to be built from. Passing by is not a stop:
 * the job goes on, and the building's comparison simply never counts that cell.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the whole table ({@code DroneCellRulesTest}).
 */
public final class DroneCellRules {

	private DroneCellRules() {
	}

	/** The job a drone works on: the two kinds of the town's work queue it takes. */
	public enum Job {
		CLEAR,
		BUILD
	}

	/** What to do with a cell. */
	public enum Action {
		/** Nothing: the cell is right, passed by, or not this job's business. */
		NONE,
		/** Take the block away; it is gone (R85). */
		REMOVE,
		/** Put the plan's block there. */
		PLACE,
		/** Something the drone may not touch is in the way: the job stops with «obstructed by X». */
		OBSTRUCTED
	}

	/**
	 * One cell as the survey saw it.
	 *
	 * @param matches        the world already holds the plan's block (or air where air belongs)
	 * @param worldAir       the world holds air
	 * @param worldLiquid    the world holds a liquid block (source or flowing)
	 * @param worldProtected the world's block is one the drone never touches
	 * @param worldRemovable the world's block is rubble the drone may take away
	 * @param wantAir        the plan wants air here (the ruin's debris)
	 * @param wantSkipped    the plan's block is one the drone does not put: protected, or no item builds it
	 */
	public record Cell(boolean matches, boolean worldAir, boolean worldLiquid, boolean worldProtected,
			boolean worldRemovable, boolean wantAir, boolean wantSkipped) {
	}

	/** What a drone on {@code job} does with {@code cell}; see the class comment for the rules. */
	public static Action decide(Job job, Cell cell) {
		if (cell.matches() || cell.worldProtected()) {
			return Action.NONE;
		}
		if (!cell.wantAir() && cell.wantSkipped()) {
			return Action.NONE;
		}
		boolean clearable = cell.worldRemovable() || cell.worldLiquid();
		if (job == Job.CLEAR) {
			if (cell.worldAir()) {
				return Action.NONE;
			}
			if (cell.worldLiquid()) {
				return cell.wantAir() ? Action.REMOVE : Action.NONE;
			}
			if (cell.worldRemovable()) {
				return Action.REMOVE;
			}
			return cell.wantAir() ? Action.OBSTRUCTED : Action.NONE;
		}
		if (cell.wantAir()) {
			if (cell.worldAir()) {
				return Action.NONE;
			}
			return clearable ? Action.REMOVE : Action.OBSTRUCTED;
		}
		return cell.worldAir() || clearable ? Action.PLACE : Action.OBSTRUCTED;
	}

	/**
	 * Whether a placement on this cell takes a block away first — the core pays a removal on top of the placement
	 * (R94). Air and liquid cost nothing to take away.
	 */
	public static boolean placementRemoves(Cell cell) {
		return !cell.worldAir() && !cell.worldLiquid() && cell.worldRemovable();
	}
}
