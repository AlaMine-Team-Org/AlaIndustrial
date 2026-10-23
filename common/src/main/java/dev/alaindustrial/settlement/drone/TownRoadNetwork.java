package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownRoadPlan;
import dev.alaindustrial.settlement.works.TownRoadTrace;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

/**
 * The road network of an ALA town in the world (round 2 of 0.1.219, R2-7; epic MOD-824, R126–R132, ADR-051): the
 * town's general plan of streets ({@link TownRoadPlan}) grown over its buildings, and what the rest of the town reads
 * from it.
 *
 * <p><b>When it grows.</b> On the lit core's tick ({@link #tick}) and whenever a road is asked for: every building
 * with a house inlet that is not joined yet — every ruin, every laid house — gets its branch, nearest to the network
 * first. A laid stretch never moves (R129). An attempt that leaves buildings out (a chunk not loaded, no way through)
 * is not repeated for {@link #RETRY_TICKS} ticks. What is not laid yet is traced again after the world is loaded and
 * after a clearing ({@link #groundMoved}), unless a drone is laying a road of the town at that moment (R3-5).
 *
 * <p><b>The ground.</b> A column's ground is its top natural block under plants, leaves and a lying slab — earth,
 * stone, sand, rubble, or an old street's paving ({@code #alaindustrial:town_paving}, half the price of a step, R130);
 * a building's box, water, a tree or a block a player put is gone round. A road on the ground takes the top block's
 * place, flush with it.
 *
 * <p><b>What it gives the rest of the town</b> — the one interface to the network:
 * <ul>
 *   <li>{@link #cells} — every cell of the network with its «laid» mark: the «Town network» view (R126);</li>
 *   <li>{@link #branch} — the cells still to lay between one building's inlet and the hall: that building's road
 *   step and its schematic (R127);</li>
 *   <li>{@link #course} — the whole way from the hall's inlet to a building's, laid cells included: the road job
 *   walks it and passes what is already there;</li>
 *   <li>{@link #reserved} — whether a column belongs to a planned street (R131).</li>
 * </ul>
 */
public final class TownRoadNetwork {

	/** How long an attempt that left buildings out holds before the network is grown again, in ticks. */
	public static final long RETRY_TICKS = 600;
	/** How often a lit core looks at its town's road network, in ticks. */
	public static final int TICK_EVERY = 40;
	/** How far under a column's height map the survey looks for its ground. */
	private static final int SCAN_DEPTH = 8;

	/**
	 * One cell of the network.
	 *
	 * @param pos   where its road block stands
	 * @param built whether its road is laid
	 */
	public record PlanCell(BlockPos pos, boolean built) {
	}

	private TownRoadNetwork() {
	}

	/** One lit tick of town {@code town}'s core: every {@link #TICK_EVERY} ticks grows the network if due. */
	public static void tick(ServerLevel level, int town) {
		if (level.getGameTime() % TICK_EVERY == 0) {
			ensure(level, town);
		}
	}

	/** Every cell of town {@code town}'s network, grown first if due; empty when the hall has no inlet. */
	public static List<PlanCell> cells(ServerLevel level, int town) {
		Optional<TownRoadPlan> plan = ensure(level, town);
		List<PlanCell> out = new ArrayList<>();
		plan.ifPresent(p -> p.nodes().forEach(node -> out.add(new PlanCell(pos(node.cell()), node.built()))));
		return out;
	}

	/**
	 * The cells still to lay between building {@code building}'s inlet and the hall's, the hall's end first: its own
	 * branch and the part of the main street it hangs on that is not laid yet. Empty when the building is not joined.
	 */
	public static List<BlockPos> branch(ServerLevel level, int town, int building) {
		Optional<TownRoadPlan> plan = ensure(level, town);
		List<BlockPos> out = new ArrayList<>();
		plan.flatMap(p -> p.course(building)).ifPresent(course -> {
			for (TownRoadTrace.Cell cell : course) {
				if (plan.get().at(cell.column()).map(node -> !node.built()).orElse(false)) {
					out.add(pos(cell));
				}
			}
		});
		return out;
	}

	/** The way from the hall's inlet to building {@code building}'s, the hall's end first, if it is joined. */
	public static Optional<List<BlockPos>> course(ServerLevel level, int town, int building) {
		return ensure(level, town).flatMap(plan -> plan.course(building))
				.map(course -> course.stream().map(TownRoadNetwork::pos).toList());
	}

	/** Whether column ({@code x}, {@code z}) holds a cell of town {@code town}'s planned streets (R131). */
	public static boolean reserved(ServerLevel level, int town, int x, int z) {
		return SettlementRegistry.get(level.getServer()).works().find(town)
				.map(works -> works.roadPlan().at(new TownRoadTrace.Column(x, z)).isPresent()).orElse(false);
	}

	/** Marks the network's cell at {@code pos} laid, if the network has one in its column. */
	public static void markLaid(ServerLevel level, int town, BlockPos pos) {
		SettlementRegistry.get(level.getServer()).works().find(town)
				.ifPresent(works -> works.roadPlan().markBuilt(new TownRoadTrace.Column(pos.getX(), pos.getZ())));
	}

	/**
	 * Town {@code town}'s network, grown over every building not joined yet when an attempt is due. Empty when the
	 * town has no record or its hall no inlet.
	 */
	static Optional<TownRoadPlan> ensure(ServerLevel level, int town) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		if (found.isEmpty() || !found.get().buildingsKnown()) {
			return Optional.empty();
		}
		TownWorks works = found.get();
		Optional<TownPowerLinks.Socket> hallInlet = TownPowerLinks.hallInlet(level, town);
		if (hallInlet.isEmpty()) {
			return Optional.empty();
		}
		TownRoadPlan plan = works.roadPlan();
		if (plan.stale() && level.isLoaded(hallInlet.get().front()) && !roadUnderWay(works)) {
			plan.retraceUnlaid(); // what is not laid follows the ground as it is now (R3-5)
			works.retryRoadsNow();
		}
		List<TownRoadPlan.Site> sites = sites(level, town, works);
		for (Integer joined : List.copyOf(plan.fronts().keySet())) {
			if (sites.stream().noneMatch(site -> site.building() == joined)) {
				plan.detach(joined); // a house called off, or a building that lost its inlet
			}
		}
		boolean waiting = plan.isEmpty() || sites.stream().anyMatch(site -> !plan.fronts()
				.getOrDefault(site.building(), new TownRoadTrace.Column(Integer.MIN_VALUE, 0))
				.equals(site.front().column()));
		long now = level.getGameTime();
		if (!waiting || !works.roadGrowDue(now)) {
			return Optional.of(plan);
		}
		BlockPos hallFront = hallInlet.get().front();
		if (!level.isLoaded(hallFront)) {
			return Optional.of(plan);
		}
		TownRoadPlan.Hall hall = new TownRoadPlan.Hall(cell(hallFront), hallInlet.get().outward().getStepX(),
				hallInlet.get().outward().getStepZ());
		List<BoundingBox> boxes = buildingBoxes(level, works);
		Set<Integer> failed = plan.grow(hall, sites, (x, z) -> ground(level, boxes, x, z));
		if (!failed.isEmpty()) {
			works.deferRoadGrowth(now + RETRY_TICKS);
			Industrialization.LOGGER.info("[ala-towns] settlement {}: buildings {} not joined to the road network yet",
					town, failed);
		}
		return Optional.of(plan);
	}

	/**
	 * Town {@code town}'s ground moved — a clearing took terrain or rubble away: the next look at its network traces
	 * the cells not laid yet again, once no road is being laid (R3-5).
	 */
	public static void groundMoved(ServerLevel level, int town) {
		SettlementRegistry.get(level.getServer()).works().find(town).ifPresent(works -> works.roadPlan().groundMoved());
	}

	/** Whether a drone is laying a road of the town right now: its course must not move under it. */
	private static boolean roadUnderWay(TownWorks works) {
		return works.queue().jobs().stream()
				.anyMatch(job -> job.type() == WorkJobType.ROAD && job.status() == WorkJobStatus.TAKEN);
	}

	/** The building number a laying preview stands for: no building of a town ever has it. */
	private static final int PREVIEW = Integer.MAX_VALUE - 1;

	/**
	 * The road cells a new house would take to join town {@code town}'s network (MOD-877; round 2 of 0.1.219,
	 * step 8): its inlet's front at {@code front}, its box {@code box}. The network is grown on a copy — the town's
	 * own plan does not change. Empty when no way reaches the network, or the hall has no inlet.
	 */
	public static Optional<List<BlockPos>> previewBranch(ServerLevel level, int town, BlockPos front,
			BoundingBox box) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		Optional<TownPowerLinks.Socket> hallInlet = TownPowerLinks.hallInlet(level, town);
		if (found.isEmpty() || hallInlet.isEmpty() || !level.isLoaded(hallInlet.get().front())) {
			return Optional.empty();
		}
		TownWorks works = found.get();
		TownRoadPlan copy = new TownRoadPlan(works.roadPlan().nodes(), works.roadPlan().fronts());
		List<TownRoadPlan.Site> sites = new ArrayList<>(sites(level, town, works));
		sites.add(new TownRoadPlan.Site(PREVIEW, cell(front)));
		List<BoundingBox> boxes = buildingBoxes(level, works);
		boxes.add(box);
		TownRoadPlan.Hall hall = new TownRoadPlan.Hall(cell(hallInlet.get().front()),
				hallInlet.get().outward().getStepX(), hallInlet.get().outward().getStepZ());
		if (copy.grow(hall, sites, (x, z) -> ground(level, boxes, x, z)).contains(PREVIEW)) {
			return Optional.empty();
		}
		return copy.course(PREVIEW).map(course -> course.stream()
				.filter(cell -> copy.at(cell.column()).map(node -> !node.built()).orElse(false))
				.map(TownRoadNetwork::pos).toList());
	}

	/** Every building of the town but the hall with a house inlet: where its road joins. */
	private static List<TownRoadPlan.Site> sites(ServerLevel level, int town, TownWorks works) {
		OptionalInt hall = TownHall.index(works);
		List<TownRoadPlan.Site> out = new ArrayList<>();
		for (int i = 0; i < works.buildings().size(); i++) {
			if (hall.isPresent() && hall.getAsInt() == i || works.buildings().get(i).withdrawn()) {
				continue;
			}
			int index = i;
			TownWorksScanner.plan(level, town, i).flatMap(TownPowerLinks::inletOf)
					.ifPresent(inlet -> out.add(new TownRoadPlan.Site(index, cell(inlet.front()))));
		}
		return out;
	}

	private static TownRoadTrace.Cell cell(BlockPos pos) {
		return new TownRoadTrace.Cell(pos.getX(), pos.getY(), pos.getZ());
	}

	private static BlockPos pos(TownRoadTrace.Cell cell) {
		return new BlockPos(cell.x(), cell.y(), cell.z());
	}

	/**
	 * The ground of column ({@code x}, {@code z}), or null where a road may not go: inside a building's box, in an
	 * unloaded chunk, over water, or where the top under plants and leaves is no natural block nor paving.
	 */
	private static TownRoadTrace.@Nullable Ground ground(ServerLevel level, List<BoundingBox> boxes, int x, int z) {
		for (BoundingBox box : boxes) {
			if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
				return null;
			}
		}
		if (!level.isLoaded(new BlockPos(x, level.getMinY(), z))) {
			return null;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x,
				level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		int top = Integer.MIN_VALUE;
		boolean paved = false;
		for (int i = 0; i <= SCAN_DEPTH && top == Integer.MIN_VALUE; i++) {
			BlockState state = level.getBlockState(at);
			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			if (lowSlab(state)) {
				at.setY(at.getY() - 1); // a slab lying on a street: the road takes the block under it, flush (R3-5)
			} else if (RoadWork.natural(state) || level.getBlockEntity(at) instanceof CableNode) {
				top = at.getY();
				paved = state.is(ModTags.Blocks.TOWN_PAVING);
			} else if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES)
					|| DroneCellSurvey.litter(state)) {
				at.setY(at.getY() - 1);
			} else {
				return null;
			}
		}
		if (top == Integer.MIN_VALUE) {
			return null;
		}
		int lowest = top;
		for (int k = 1; k <= TownRoadTrace.MAX_CUT; k++) {
			BlockState over = level.getBlockState(new BlockPos(x, top - k + 1, z));
			BlockState under = level.getBlockState(new BlockPos(x, top - k, z));
			if (!RoadWork.cuttable(over) || !RoadWork.fillable(under) || !under.getFluidState().isEmpty()) {
				break;
			}
			lowest = top - k;
		}
		int highest = top;
		for (int k = 1; k <= TownRoadTrace.MAX_FILL; k++) {
			BlockState above = level.getBlockState(new BlockPos(x, top + k, z));
			if (!RoadWork.open(above) || !above.getFluidState().isEmpty()) {
				break;
			}
			highest = top + k;
		}
		return new TownRoadTrace.Ground(top, lowest, highest, paved);
	}

	/** A natural slab in the lower half of its cell: no ground of its own, a road goes in under it. */
	private static boolean lowSlab(BlockState state) {
		return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
				&& RoadWork.natural(state);
	}

	/** The boxes of the town's buildings, as their structure placed them: a road goes round them. */
	private static List<BoundingBox> buildingBoxes(ServerLevel level, TownWorks works) {
		List<BoundingBox> boxes = new ArrayList<>();
		StructureTemplateManager templates = LineStructures.templates(level);
		for (TownBuilding building : works.buildings()) {
			if (building.withdrawn()) {
				continue; // a laid house called off (MOD-877)
			}
			templates.get(building.shapeTemplate()).ifPresent(template -> boxes.add(template.getBoundingBox(
					new StructurePlaceSettings().setRotation(building.rotation()), building.origin())));
		}
		return boxes;
	}
}
