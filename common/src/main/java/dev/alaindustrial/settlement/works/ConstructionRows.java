package dev.alaindustrial.settlement.works;

import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
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
	/** Why a house may not be started yet (MOD-862): the dispatcher's own «Start» lines, by blocker suffix. */
	public static final String WAIT_PREFIX = "settlement.alaindustrial.dispatcher.start.";
	/** The town knows no building yet. */
	public static final String NO_BUILDINGS = "settlement.alaindustrial.works.no_buildings";
	/** The queue holds no job. */
	public static final String NO_JOBS = "settlement.alaindustrial.works.no_jobs";
	/** «Order called off. Press Resume to go on» — a building card's line (round 5 of 0.1.219). */
	public static final String CALLED_OFF = "settlement.alaindustrial.works.called_off";
	/** «The town's road network» — the row whose schematic switch shows every road the town knows (Q6). */
	public static final String ROAD_NETWORK = "settlement.alaindustrial.works.road_network";
	/** «No house plan of type %s yet: look for the sheet in this house's ruin» — a house the town cannot order (Q4). */
	public static final String NO_HOUSE_PLAN = "settlement.alaindustrial.works.wait.no_house_plan";
	/** «No warehouse plan yet: look for it in the warehouse ruin or craft it» (MOD-892). */
	public static final String NO_WAREHOUSE_PLAN = "settlement.alaindustrial.works.wait.no_warehouse_plan";
	/** «No link to the core: the road is broken at (%1$s, %2$s)» (R132). */
	public static final String NETWORK_GAP = "settlement.alaindustrial.works.network_gap";
	/** The target of the road network's row: no building. */
	public static final int NETWORK_TARGET = -1;
	/** The plans section (Q4, MOD-892): the house types and the warehouse, and whether the town knows each. */
	public static final String SECTION_HOUSE_PLANS = "settlement.alaindustrial.works.section.house_plans";
	/** A house plan's status words. */
	public static final String PLAN_KNOWN = "settlement.alaindustrial.works.house_plan.known";
	public static final String PLAN_UNKNOWN = "settlement.alaindustrial.works.house_plan.unknown";

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
	public static final List<Source> SOURCES = List.of(ConstructionRows::buildings, ConstructionRows::housePlans,
			PlanSheetRows::add, StageRows::add, ConstructionRows::queue);

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
	 * The town's buildings: name, state, matching share, missing key points; the owner gets one button by the
	 * building's state — restore, build, resume, repair or cancel its order (round 2 of 0.1.219, Q1, Q2) — and anybody
	 * may show one building's schematic with its road (Q6). Last, the road network's row with its own schematic switch.
	 */
	static void buildings(Context context, List<ConstructionRow> rows) {
		TownWorks works = context.works();
		if (!works.buildingsKnown() || works.buildings().isEmpty()) {
			rows.add(line(SECTION_BUILDINGS, ConstructionRow.Text.of(NO_BUILDINGS)));
			return;
		}
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (building.withdrawn()) {
				continue; // a laid house called off (MOD-877)
			}
			rows.add(buildingRow(context, i));
		}
		rows.add(new ConstructionRow(SECTION_BUILDINGS, ConstructionRow.Text.of(ROAD_NETWORK), Optional.empty(), -1,
				ConstructionRow.Tone.NORMAL, Optional.empty(), NETWORK_TARGET, ConstructionRow.Action.SCHEMATIC.bit()));
	}

	/** The row of building {@code i}: the same the building's card shows (Q2). */
	public static ConstructionRow buildingRow(Context context, int i) {
		TownWorks works = context.works();
		TownBuilding building = works.buildings().get(i);
		boolean restorable = building.intact().isPresent();
		boolean intact = building.state() == BuildingState.INTACT || building.state() == BuildingState.UPGRADED;
		int actions = 0;
		Optional<OrderButton> button = TownOrders.button(works, i);
		Optional<String> wait = button.isPresent() && button.get() != OrderButton.CANCEL
				? TownOrders.blocker(context.town(), works, i) : Optional.empty();
		if (context.manage() && button.isPresent() && wait.isEmpty()) {
			actions |= ConstructionRow.Action.of(button.get()).bit();
		}
		if (restorable && (!intact || TownOrders.ordered(works, i))) {
			actions |= ConstructionRow.Action.SCHEMATIC.bit(); // visual only: every viewer (round 2, point 7)
		}
		Optional<ConstructionRow.Text> gap = networkGap(context, i).or(() -> stop(works, i, button));
		Optional<ConstructionRow.Text> detail = gap.isPresent() ? gap : wait.isPresent()
				? wait.map(suffix -> TownOrders.NO_PLAN.equals(suffix) ? noPlan(building)
						: ConstructionRow.Text.of(WAIT_PREFIX + suffix))
				: !intact && !building.missingKeys().isEmpty()
						? Optional.of(new ConstructionRow.Text(MISSING_KEYS, building.missingKeys(), true))
						: Optional.empty();
		ConstructionRow.Tone tone = intact ? ConstructionRow.Tone.GOOD
				: restorable ? ConstructionRow.Tone.NORMAL : ConstructionRow.Tone.DIM;
		return new ConstructionRow(SECTION_BUILDINGS, ConstructionRow.Text.of(TownHouses.displayName(building)),
				Optional.of(ConstructionRow.Text.of(STATE + building.state().serializedName())),
				building.percent(), tone, detail, i, actions);
	}

	/** The line of a building whose plan the town lacks: a house names its type (Q4), the warehouse (MOD-892). */
	public static ConstructionRow.Text noPlan(TownBuilding building) {
		return TownHouses.isHouse(building)
				? ConstructionRow.Text.of(NO_HOUSE_PLAN, String.valueOf(TownHouses.type(building)))
				: ConstructionRow.Text.of(NO_WAREHOUSE_PLAN);
	}

	/**
	 * Why building {@code i}'s work stands, for its card (round 5 of 0.1.219): the reason of its stopped job — what is
	 * in the way and where — or, for an order the owner called off, that «Resume» takes it up again.
	 */
	public static Optional<ConstructionRow.Text> stop(TownWorks works, int i, Optional<OrderButton> button) {
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() == i && job.status() == WorkJobStatus.BLOCKED && job.reason().isPresent()) {
				return Optional.of(ConstructionRow.Text.of(job.reason().get()));
			}
		}
		return button.equals(Optional.of(OrderButton.RESUME)) ? Optional.of(ConstructionRow.Text.of(CALLED_OFF))
				: Optional.empty();
	}

	/**
	 * Where building {@code i}'s link to the core is broken (round 2 of 0.1.219, R132): a building restored once whose
	 * inlet is cut off names the first cell of its way to the hall that holds neither road nor cable. Empty for a
	 * building linked, never restored, or whose way is not known or not loaded.
	 */
	static Optional<ConstructionRow.Text> networkGap(Context context, int i) {
		return gapCell(context.level(), context.town().id(), context.works().buildings().get(i), i)
				.map(pos -> ConstructionRow.Text.of(NETWORK_GAP, String.valueOf(pos.getX()),
						String.valueOf(pos.getZ())));
	}

	/**
	 * The cell where building {@code i} (that is {@code building}) of town {@code town} is cut off from the core
	 * (R132): the first cell of its way to the hall holding neither road nor cable — what its row names and the
	 * network schematic highlights (MOD-884). Empty for a building linked, never restored, or whose way is not known
	 * or not loaded.
	 */
	public static Optional<BlockPos> gapCell(ServerLevel level, int town, TownBuilding building, int i) {
		if (!building.everRestored() || !building.missingKeys().contains(TownPowerLinks.NOT_CONNECTED_KEY)) {
			return Optional.empty();
		}
		for (BlockPos pos : TownRoadNetwork.course(level, town, i).orElse(List.of())) {
			if (!level.isLoaded(pos)) {
				return Optional.empty();
			}
			if (!RoadWork.done(level, pos, level.getBlockState(pos))) {
				return Optional.of(pos);
			}
		}
		return Optional.empty();
	}

	/**
	 * The town's library of plans (round 2 of 0.1.219, Q4): each house type of its style, known or not, then the
	 * warehouse's when the town has a warehouse or knows its plan (MOD-892).
	 */
	static void housePlans(Context context, List<ConstructionRow> rows) {
		TownWorks works = context.works();
		Set<String> known = HousePlans.known(works, TownHall.index(works));
		Set<Integer> types = new TreeSet<>();
		for (TownBuilding building : works.buildings()) {
			if (TownHouses.isHouse(building)) {
				types.add(TownHouses.type(building));
			}
		}
		for (String id : known) {
			types.add(PlanSheetRules.houseType(id.substring(id.lastIndexOf('/') + 1)));
		}
		types.remove(0);
		for (int type : types) {
			boolean has = known.stream().anyMatch(id -> id.endsWith("/" + PlanSheetRules.house(type)));
			rows.add(new ConstructionRow(SECTION_HOUSE_PLANS,
					ConstructionRow.Text.of("item.alaindustrial.house_plan.typed", String.valueOf(type)),
					Optional.of(ConstructionRow.Text.of(has ? PLAN_KNOWN : PLAN_UNKNOWN)), -1,
					has ? ConstructionRow.Tone.GOOD : ConstructionRow.Tone.DIM, Optional.empty(), 0, 0));
		}
		String warehouse = "/" + PlanSheetRules.WAREHOUSE;
		boolean hasWarehouse = works.buildings().stream()
				.anyMatch(b -> HousePlans.needsPlan(b) && !TownHouses.isHouse(b));
		boolean knowsWarehouse = known.stream().anyMatch(id -> id.endsWith(warehouse));
		if (hasWarehouse || knowsWarehouse) {
			rows.add(new ConstructionRow(SECTION_HOUSE_PLANS, ConstructionRow.Text.of(HousePlanItem.WAREHOUSE_NAME),
					Optional.of(ConstructionRow.Text.of(knowsWarehouse ? PLAN_KNOWN : PLAN_UNKNOWN)), -1,
					knowsWarehouse ? ConstructionRow.Tone.GOOD : ConstructionRow.Tone.DIM, Optional.empty(), 0, 0));
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
