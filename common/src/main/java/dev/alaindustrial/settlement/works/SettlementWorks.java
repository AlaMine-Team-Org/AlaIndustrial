package dev.alaindustrial.settlement.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The buildings and work queues of every ALA town of the save (MOD-857, epic decision R79), kept inside the towns'
 * {@code SettlementRegistry} as its optional field {@code works}: a save written before this reads as one in which no
 * town knows its buildings yet, and each town learns them from its structure on its core's first tick.
 *
 * <p><b>Why not in the town's record.</b> {@code Settlement} is an immutable record the registry replaces on every
 * change; a building's state changes after every pass of the scanner and a job on every step of a drone. A book of
 * its own, like the shift workers' ({@code ShiftWorkerBook}), keeps those changes out of the town's record. Saved in
 * town order, so the same towns always write the same file.
 *
 * <p>Server thread only. Every change calls the hook the registry installs ({@link #onChange}).
 */
public final class SettlementWorks {

	/** A job as the save holds it: its type by name, so a type this build does not know is dropped, not fatal. */
	private record SavedJob(int id, String type, int building, String status, String taker, int progress,
			Optional<BlockReason> reason, boolean repair) {
	}

	private static final Codec<BlockReason> REASON_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(BlockReason::key),
			Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(BlockReason::args),
			Codec.BOOL.optionalFieldOf("joined", false).forGetter(BlockReason::joined)
	).apply(i, BlockReason::new));

	private static final Codec<SavedJob> JOB_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(SavedJob::id),
			Codec.STRING.fieldOf("type").forGetter(SavedJob::type),
			Codec.INT.fieldOf("building").forGetter(SavedJob::building),
			Codec.STRING.optionalFieldOf("status", "queued").forGetter(SavedJob::status),
			Codec.STRING.optionalFieldOf("taker", "").forGetter(SavedJob::taker),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(SavedJob::progress),
			REASON_CODEC.optionalFieldOf("reason").forGetter(SavedJob::reason),
			Codec.BOOL.optionalFieldOf("repair", false).forGetter(SavedJob::repair)
	).apply(i, SavedJob::new));

	/**
	 * One cell of a town's road network (round 2 of 0.1.219, R129): where its road block stands, the column of the
	 * next cell towards the hall (none for the hall's own), and whether it is laid. The kept per-building courses of
	 * the dev builds before it ({@code roads}) are not read: the network is grown again (R2-7).
	 */
	private record SavedRoadCell(int x, int y, int z, Optional<List<Integer>> parent, boolean built) {

		static SavedRoadCell of(TownRoadPlan.Node node) {
			Optional<List<Integer>> parent = node.parent() == null ? Optional.empty()
					: Optional.of(List.of(node.parent().x(), node.parent().z()));
			return new SavedRoadCell(node.cell().x(), node.cell().y(), node.cell().z(), parent, node.built());
		}

		Optional<TownRoadPlan.Node> node() {
			if (parent.isPresent() && parent.get().size() != 2) {
				return Optional.empty();
			}
			return Optional.of(new TownRoadPlan.Node(new TownRoadTrace.Cell(x, y, z),
					parent.map(p -> new TownRoadTrace.Column(p.get(0), p.get(1))).orElse(null), built));
		}
	}

	private static final Codec<SavedRoadCell> ROAD_CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(SavedRoadCell::x),
			Codec.INT.fieldOf("y").forGetter(SavedRoadCell::y),
			Codec.INT.fieldOf("z").forGetter(SavedRoadCell::z),
			Codec.INT.listOf().optionalFieldOf("parent").forGetter(SavedRoadCell::parent),
			Codec.BOOL.optionalFieldOf("built", false).forGetter(SavedRoadCell::built)
	).apply(i, SavedRoadCell::new));

	/** Where a building's inlet joins the road network: the column of the cell in front of it. */
	private record SavedRoadFront(int building, int x, int z) {
	}

	private static final Codec<SavedRoadFront> ROAD_FRONT_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("building").forGetter(SavedRoadFront::building),
			Codec.INT.fieldOf("x").forGetter(SavedRoadFront::x),
			Codec.INT.fieldOf("z").forGetter(SavedRoadFront::z)
	).apply(i, SavedRoadFront::new));

	/** One town's part of the file. */
	private record SavedTown(int settlement, boolean known, List<TownBuilding> buildings, List<SavedJob> jobs,
			int nextJob, int planSheets, List<SavedRoadCell> roadCells, List<SavedRoadFront> roadFronts,
			List<Integer> orders, List<String> housePlans) {
	}

	private static final Codec<SavedTown> TOWN_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("settlement").forGetter(SavedTown::settlement),
			Codec.BOOL.optionalFieldOf("known", false).forGetter(SavedTown::known),
			TownBuilding.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(SavedTown::buildings),
			JOB_CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(SavedTown::jobs),
			Codec.INT.optionalFieldOf("next_job", 1).forGetter(SavedTown::nextJob),
			Codec.INT.optionalFieldOf("plan_sheets", 0).forGetter(SavedTown::planSheets),
			ROAD_CELL_CODEC.listOf().optionalFieldOf("road_cells", List.of()).forGetter(SavedTown::roadCells),
			ROAD_FRONT_CODEC.listOf().optionalFieldOf("road_fronts", List.of()).forGetter(SavedTown::roadFronts),
			Codec.INT.listOf().optionalFieldOf("orders", List.of()).forGetter(SavedTown::orders),
			Codec.STRING.listOf().optionalFieldOf("house_plans", List.of()).forGetter(SavedTown::housePlans)
	).apply(i, SavedTown::new));

	public static final Codec<SettlementWorks> CODEC = TOWN_CODEC.listOf()
			.xmap(SettlementWorks::new, SettlementWorks::saved);

	private final Map<Integer, TownWorks> byTown = new TreeMap<>();
	private Runnable onChange = () -> { };

	/** No town knows anything yet. */
	public SettlementWorks() {
	}

	private SettlementWorks(List<SavedTown> saved) {
		for (SavedTown town : saved) {
			if (town.settlement() <= 0 || byTown.containsKey(town.settlement())) {
				continue;
			}
			List<WorkJob> jobs = new ArrayList<>();
			for (SavedJob job : town.jobs()) {
				Optional<WorkJobType> type = WorkJobType.byName(job.type());
				if (type.isEmpty()) {
					Industrialization.LOGGER.warn("[ala-towns] settlement {} job {} of an unknown type {} dropped",
							town.settlement(), job.id(), job.type());
					continue;
				}
				WorkJobStatus status = WorkJobStatus.byName(job.status());
				// A taken job keeps its taker through a save: the executor finds its job again on its next call. One
				// saved without a taker (a hand-edited file) waits for a new one instead.
				if (status == WorkJobStatus.TAKEN && job.taker().isEmpty()) {
					status = WorkJobStatus.QUEUED;
				}
				jobs.add(new WorkJob(job.id(), type.get(), job.building(), status, job.taker(), job.progress(),
						job.reason(), job.repair()));
			}
			List<TownRoadPlan.Node> cells = new ArrayList<>();
			town.roadCells().forEach(cell -> cell.node().ifPresent(cells::add));
			Map<Integer, TownRoadTrace.Column> fronts = new TreeMap<>();
			town.roadFronts().forEach(front -> fronts.put(front.building(),
					new TownRoadTrace.Column(front.x(), front.z())));
			TownRoadPlan roads = new TownRoadPlan(cells, fronts);
			TownWorks works = new TownWorks(town.buildings(), town.known(), new WorkQueue(jobs, town.nextJob()),
					town.planSheets(), roads, town.orders(), town.housePlans());
			install(town.settlement(), works);
		}
	}

	private List<SavedTown> saved() {
		List<SavedTown> out = new ArrayList<>();
		for (Map.Entry<Integer, TownWorks> entry : byTown.entrySet()) {
			TownWorks works = entry.getValue();
			if (works.isEmpty()) {
				continue;
			}
			List<SavedJob> jobs = new ArrayList<>();
			for (WorkJob job : works.queue().jobs()) {
				jobs.add(new SavedJob(job.id(), job.type().serializedName(), job.building(),
						job.status().serializedName(), job.taker(), job.progress(), job.reason(), job.repair()));
			}
			List<SavedRoadCell> cells = works.roadPlan().nodes().stream().map(SavedRoadCell::of).toList();
			List<SavedRoadFront> fronts = new ArrayList<>();
			works.roadPlan().fronts().forEach((building, column) -> fronts.add(
					new SavedRoadFront(building, column.x(), column.z())));
			out.add(new SavedTown(entry.getKey(), works.buildingsKnown(), works.buildings(), jobs,
					works.queue().nextId(), works.foundSheets(), cells, fronts, works.orderList(),
					List.copyOf(works.learnedHousePlans())));
		}
		return out;
	}

	/** The owner's dirty hook: called after every change of any town. */
	public void onChange(Runnable hook) {
		this.onChange = hook;
	}

	/** Whether no town holds anything worth saving. */
	public boolean isEmpty() {
		for (TownWorks works : byTown.values()) {
			if (!works.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The buildings and queue of town {@code settlement}, made empty on first use. */
	public TownWorks town(int settlement) {
		TownWorks works = byTown.get(settlement);
		if (works == null) {
			works = new TownWorks();
			install(settlement, works);
		}
		return works;
	}

	/** The buildings and queue of town {@code settlement}, if it has any record here. */
	public Optional<TownWorks> find(int settlement) {
		return Optional.ofNullable(byTown.get(settlement));
	}

	/** Every town that has a record here, by number. */
	public Map<Integer, TownWorks> all() {
		return Collections.unmodifiableMap(byTown);
	}

	/** Forgets everything town {@code settlement} knew — a test, or an operator starting the town's works over. */
	public void forget(int settlement) {
		if (byTown.remove(settlement) != null) {
			onChange.run();
		}
	}

	private void install(int settlement, TownWorks works) {
		works.onChange(() -> onChange.run());
		byTown.put(settlement, works);
	}
}
