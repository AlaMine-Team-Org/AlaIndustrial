package dev.alaindustrial.settlement.drone;

import java.util.ArrayList;
import java.util.List;

/**
 * When the dispatcher's «Start» may be pressed (MOD-858, owner's round 2), with no Minecraft types: the screen draws
 * the button disabled and lists {@link #blockers}, the server asks the same method again before it raises a job.
 *
 * <p><b>Hard requirements:</b> the town's owner, a hall the dispatcher serves that can be restored and has no open job,
 * the hall's restoration plan in the plan slot and at least one nest with a drone over the hall. <b>Materials are not
 * one</b>: the first job is the clearing, which takes none, so a short buffer is a {@linkplain #materialsShort
 * warning} under the button, never a blocker (decision of round 2, recorded in MOD-858's task).
 */
public final class StartReadiness {

	private static final String PREFIX = "settlement.alaindustrial.dispatcher.start.";

	/** Why «Start» cannot be pressed now; each has its short line. */
	public enum Blocker {
		/** The dispatcher serves no town's hall (its status line says why). */
		NO_TOWN("no_town"),
		/** The hall has been restored: the drones look after it by themselves, nothing to start (owner's round 4). */
		RESTORED("restored"),
		/** A clearing or building job is already open. */
		STARTED("started"),
		/** The viewer is not the town's owner. */
		NOT_OWNER("not_owner"),
		/** The hall has no intact template to restore to. */
		NO_HALL("no_hall"),
		/** No plan in the plan slot, or a plan of another hall. */
		NO_PLAN("no_plan"),
		/** No nest with a drone stands over the hall. */
		NO_DRONE("no_drone"),
		/** The building is a house and the town hall is not restored yet (MOD-862, R81). */
		HALL_FIRST("hall_first"),
		/** The building is a house and the tier's house limit is reached (MOD-862, R5, R110). */
		HOUSE_LIMIT("house_limit"),
		/**
		 * The building is not restored, yet the drones have nothing left to do on it: a block they never touch stands
		 * where the plan wants another (round 5 of 0.1.219). The «Task» tab names it.
		 */
		NOTHING_LEFT("nothing_left");

		private final String suffix;

		Blocker(String suffix) {
			this.suffix = suffix;
		}

		/** The suffix a checklist's {@code waitFor} names this blocker by. */
		public String suffix() {
			return suffix;
		}

		/** The translation key of this blocker's line. */
		public String key() {
			return PREFIX + suffix;
		}
	}

	/** The key of the warning that the buffer does not hold the stage's materials yet. */
	public static final String MATERIALS_SHORT_KEY = PREFIX + "materials_short";

	private StartReadiness() {
	}

	/**
	 * What stands between the player and «Start», in the order the player meets it; empty when it may be pressed.
	 * The hall's own state ({@link Blocker#NO_TOWN}, {@link Blocker#RESTORED}, {@link Blocker#STARTED}) comes alone;
	 * otherwise every missing requirement is listed.
	 *
	 * @param owner      the viewer controls the dispatcher
	 * @param list       the hall's state as the dispatcher last saw it
	 * @param plan       the intact template the plan in the plan slot names, or {@code null} for none
	 * @param readyNests nests with a drone over the hall that work
	 */
	public static List<Blocker> blockers(boolean owner, DispatcherView.Checklist list, String plan, int readyNests) {
		if (!list.town()) {
			return List.of(Blocker.NO_TOWN);
		}
		if (list.restored()) {
			return List.of(Blocker.RESTORED);
		}
		if (list.jobs()) {
			return List.of(Blocker.STARTED);
		}
		List<Blocker> out = new ArrayList<>();
		if (!owner) {
			out.add(Blocker.NOT_OWNER);
		}
		if (!list.startable()) {
			out.add(Blocker.NO_HALL);
		}
		if (plan == null || !list.fits(plan)) {
			out.add(Blocker.NO_PLAN);
		}
		if (readyNests <= 0) {
			out.add(Blocker.NO_DRONE);
		}
		for (Blocker blocker : Blocker.values()) {
			if (blocker.suffix.equals(list.waitFor())) {
				out.add(blocker);
			}
		}
		return List.copyOf(out);
	}

	/** Whether the hall has a stage left whose materials the buffer does not hold yet: a warning, not a blocker. */
	public static boolean materialsShort(DispatcherView.Checklist list, boolean billCovered) {
		return list.town() && !list.restored() && list.stage() && !billCovered;
	}

	/** Every key this class words, for the language gate's sake. */
	public static List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (Blocker blocker : Blocker.values()) {
			keys.add(blocker.key());
		}
		keys.add(MATERIALS_SHORT_KEY);
		return List.copyOf(keys);
	}
}
