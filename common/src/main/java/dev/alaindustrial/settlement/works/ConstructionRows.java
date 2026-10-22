package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownMaintenance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * What the settlement core's «Construction» tab shows (MOD-857, epic decision R88): the rows of every
 * {@link Source}, in the order of {@link #SOURCES}. This task gives the town's buildings and its work queue; a later
 * task adds its own section by putting one more source in the list — the plan sheets found and missing (MOD-859), the
 * material stages (MOD-860) — and neither the packet nor the screen changes.
 */
public final class ConstructionRows {

	/** The buildings section. */
	public static final String SECTION_BUILDINGS = "settlement.alaindustrial.works.section.buildings";
	/** The queue section. */
	public static final String SECTION_QUEUE = "settlement.alaindustrial.works.section.queue";
	/** A building's state word, by {@link BuildingState#serializedName()}. */
	public static final String STATE = "settlement.alaindustrial.works.state.";
	/** A job's status word, by {@link WorkJobStatus#serializedName()}. */
	public static final String STATUS = "settlement.alaindustrial.works.status.";
	/** A job's type, by {@link WorkJobType#serializedName()}. */
	public static final String JOB_TYPE = "settlement.alaindustrial.works.job.";
	/** A job's line: «%1$s: %2$s» — its type and its building. */
	public static final String JOB_LINE = "settlement.alaindustrial.works.job_line";
	/** A building's missing key points, joined into the one placeholder. */
	public static final String MISSING_KEYS = "settlement.alaindustrial.works.missing_keys";
	/** The town knows no building yet. */
	public static final String NO_BUILDINGS = "settlement.alaindustrial.works.no_buildings";
	/** The queue holds no job. */
	public static final String NO_JOBS = "settlement.alaindustrial.works.no_jobs";
	/** The name of a building whose definition names none. */
	public static final String UNNAMED = "settlement.alaindustrial.building.unnamed";

	/** Finished and called-off jobs the tab lists, newest first, after the open ones. */
	public static final int HISTORY_SHOWN = 6;

	/**
	 * What a source knows about the town whose tab is asked for.
	 *
	 * @param level  the level the town's core stands in
	 * @param town   the town
	 * @param works  its buildings and queue
	 * @param manage whether the viewer is the town's owner, who may press the controls (R95)
	 */
	public record Context(ServerLevel level, Settlement town, TownWorks works, boolean manage) {
	}

	/** One section of the tab: adds its rows for a town. */
	@FunctionalInterface
	public interface Source {
		void add(Context context, List<ConstructionRow> rows);
	}

	/** The sections of the tab, top to bottom. A later task appends its own. */
	public static final List<Source> SOURCES = List.of(ConstructionRows::buildings, PlanSheetRows::add,
			StageRows::add, ConstructionRows::queue);

	private ConstructionRows() {
	}

	/** Every row of the tab for {@code context}. */
	public static List<ConstructionRow> collect(Context context) {
		List<ConstructionRow> rows = new ArrayList<>();
		for (Source source : SOURCES) {
			source.add(context, rows);
		}
		return rows;
	}

	/**
	 * The town's buildings: name, state, matching share, missing key points; the owner may raise jobs on them, and
	 * anybody may show the unfinished town hall's schematic.
	 */
	static void buildings(Context context, List<ConstructionRow> rows) {
		TownWorks works = context.works();
		if (!works.buildingsKnown() || works.buildings().isEmpty()) {
			rows.add(line(SECTION_BUILDINGS, ConstructionRow.Text.of(NO_BUILDINGS)));
			return;
		}
		int hall = TownHall.index(works).orElse(-1);
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			boolean restorable = building.intact().isPresent();
			boolean intact = building.state() == BuildingState.INTACT || building.state() == BuildingState.UPGRADED;
			int actions = 0;
			if (context.manage() && restorable && !intact) {
				// A button only for work there is (owner's round 4): nothing to clear, no «Clear»; nothing to build, no
				// «Build».
				Optional<BuildingPlan> plan = TownWorksScanner.plan(context.level(), context.town().id(), i);
				if (works.queue().open(WorkJobType.CLEAR, i).isEmpty()
						&& plan.map(p -> TownMaintenance.needsClearing(context.level(), p)).orElse(false)) {
					actions |= ConstructionRow.Action.RAISE_CLEAR.bit();
				}
				if (works.queue().open(WorkJobType.BUILD, i).isEmpty()
						&& plan.map(p -> TownMaintenance.needsBuilding(context.level(), p)).orElse(false)) {
					actions |= ConstructionRow.Action.RAISE_BUILD.bit();
				}
			}
			if (i == hall && restorable && !intact) {
				actions |= ConstructionRow.Action.SCHEMATIC.bit(); // visual only: every viewer (round 2, point 7)
			}
			Optional<ConstructionRow.Text> detail = !intact && !building.missingKeys().isEmpty()
					? Optional.of(new ConstructionRow.Text(MISSING_KEYS, building.missingKeys(), true))
					: Optional.empty();
			ConstructionRow.Tone tone = intact ? ConstructionRow.Tone.GOOD
					: restorable ? ConstructionRow.Tone.NORMAL : ConstructionRow.Tone.DIM;
			rows.add(new ConstructionRow(SECTION_BUILDINGS, ConstructionRow.Text.of(name(building)),
					Optional.of(ConstructionRow.Text.of(STATE + building.state().serializedName())),
					building.percent(), tone, detail, i, actions));
		}
	}

	/** The queue: open jobs in number order, then the newest of the history; the owner may cancel and restart. */
	static void queue(Context context, List<ConstructionRow> rows) {
		TownWorks works = context.works();
		List<WorkJob> history = new ArrayList<>();
		int before = rows.size();
		for (WorkJob job : works.queue().jobs()) {
			if (job.status().open()) {
				rows.add(jobRow(context, job));
			} else {
				history.add(job);
			}
		}
		Collections.reverse(history);
		for (WorkJob job : history.subList(0, Math.min(HISTORY_SHOWN, history.size()))) {
			rows.add(jobRow(context, job));
		}
		if (rows.size() == before) {
			rows.add(line(SECTION_QUEUE, ConstructionRow.Text.of(NO_JOBS)));
		}
	}

	private static ConstructionRow jobRow(Context context, WorkJob job) {
		String building = context.works().building(job.building()).map(ConstructionRows::name).orElse(UNNAMED);
		int actions = 0;
		if (context.manage() && job.status().open()) {
			actions |= ConstructionRow.Action.CANCEL.bit();
		}
		if (context.manage() && job.status() == WorkJobStatus.BLOCKED) {
			actions |= ConstructionRow.Action.START.bit();
		}
		ConstructionRow.Tone tone = switch (job.status()) {
			case BLOCKED -> ConstructionRow.Tone.BAD;
			case DONE -> ConstructionRow.Tone.GOOD;
			case CANCELLED -> ConstructionRow.Tone.DIM;
			default -> ConstructionRow.Tone.NORMAL;
		};
		return new ConstructionRow(SECTION_QUEUE,
				ConstructionRow.Text.of(JOB_LINE, JOB_TYPE + job.type().serializedName(), building),
				Optional.of(ConstructionRow.Text.of(STATUS + job.status().serializedName())), -1, tone,
				job.reason().map(ConstructionRow.Text::of), job.id(), actions);
	}

	private static ConstructionRow line(String section, ConstructionRow.Text text) {
		return new ConstructionRow(section, text, Optional.empty(), -1, ConstructionRow.Tone.DIM, Optional.empty(), 0,
				0);
	}

	static String name(TownBuilding building) {
		return building.name().isEmpty() ? UNNAMED : building.name();
	}
}
