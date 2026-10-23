package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.drone.DroneCellRules;
import dev.alaindustrial.settlement.drone.DroneCellSurvey;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Laying a new house on free ground of a town (MOD-877; epic MOD-824, decisions R61, R95, R108–R110), after the build
 * tool of MineColonies but through the town's own house inlet: the restorer puts an inlet down on the town's
 * territory, picks a house plan and one of four turns, sees the house as a ghost, and lays it once the core finds the
 * site free. The inlet is the anchor: the house's intact template is placed so that its inlet cell is the block the
 * player put down.
 *
 * <p><b>The plans the town knows</b> are the house plans it learned and the one its restored hall teaches (round 2
 * of 0.1.219, Q4, Q5; {@link HousePlans}); the drones build a house of a known type with nothing in the plan slot.
 *
 * <p><b>The site check</b> names every problem in one sentence: the town hall not restored yet (R81), no room under
 * the tier's house limit (R110), a cell outside the town's border, a chunk not loaded, a building it overlaps or
 * comes within two blocks of (R61), and every player's block, machine or chest standing in the house's cells — nature
 * the drones may take away is no problem ({@link dev.alaindustrial.registry.ModTags.Blocks#TOWN_SITE_CLEAR}).
 *
 * <p><b>Laying</b> adds a new building to the town's list ({@link TownWorks#lay}), the full price of a new house — no
 * ruin, every block to build — and queues its clearing, its road from the hall (MOD-861) and its building, in that
 * order; the house counts only once its inlet shares the core's network (R112).
 * The house counts and is lived in by the same rules as a restored one ({@link TownHouses}).
 *
 * <p><b>Calling it off</b> — the window's button, or the inlet broken — is open until the building job has started;
 * after that the house is a house, and breaking its inlet takes it off the count (R110).
 */
public final class HousePlacement {

	/** How close to the inlet a player must stand for the window to answer. */
	public static final double VIEW_DISTANCE = 10.0;
	/** The gap a new house keeps to every other building of its town (R61). */
	public static final int MIN_GAP = 2;
	/** At most this many different blocks in the way are named. */
	static final int MAX_NAMED_OBSTACLES = 3;

	private static final String KEY = "settlement.alaindustrial.house_site.";
	public static final String NOT_HERE = KEY + "not_here";
	public static final String NOT_RESTORER = KEY + "not_restorer";
	public static final String NO_PLANS = KEY + "no_plans";
	public static final String UNKNOWN_TOWN = KEY + "unknown_town";
	public static final String OUTSIDE = KEY + "outside";
	public static final String OVERLAPS = KEY + "overlaps";
	public static final String TOO_CLOSE = KEY + "too_close";
	/** A cell of the house on a planned street of the town (round 2 of 0.1.219, R131). */
	public static final String TOWN_STREET = KEY + "town_street";
	/** No way from the house's inlet to the town's road network (round 2 of 0.1.219, step 8). */
	public static final String NO_WAY_TO_NETWORK = KEY + "no_way_to_network";
	/** «Branch to the town's road: %s blocks» — the window's line on the road the house takes. */
	public static final String BRANCH = KEY + "branch";
	/** The phrases the town hall's and the limit's waits already have (MOD-862). */
	public static final String HALL_FIRST = "settlement.alaindustrial.dispatcher.start." + TownHouses.HALL_FIRST;
	public static final String LIMIT = "settlement.alaindustrial.dispatcher.start." + TownHouses.LIMIT;

	private HousePlacement() {
	}

	/**
	 * A site as the core sees it.
	 *
	 * @param building the house it would lay
	 * @param plan     the house's cells in the world
	 * @param problems every problem, one sentence each; empty — the house may be laid
	 * @param branch   the road cells still to lay to join the town's network (round 2 of 0.1.219, step 8), when
	 *                 there is a way; empty for none (or a town whose hall has no inlet)
	 */
	public record Site(TownBuilding building, BuildingPlan plan, List<ConstructionRow.Text> problems,
			Optional<List<BlockPos>> branch) {

		public Site {
			problems = List.copyOf(problems);
			branch = branch.map(List::copyOf);
		}
	}

	// ── the rules ───────────────────────────────────────────────────────────────────────────────────

	/**
	 * The house plan {@code plan} laid from the inlet at {@code inlet} of town {@code town}, turned by
	 * {@code rotation}, and what is in its way. Empty when the plan names no house this build knows.
	 */
	public static Optional<Site> survey(ServerLevel level, Settlement town, BlockPos inlet, RestorationPlan plan,
			Rotation rotation) {
		Optional<TownBuildingDef> def = definition(level, plan);
		StructureTemplateManager templates = LineStructures.templates(level);
		Optional<Map<BlockPos, BlockState>> cells = BuildingPlan.cellsOf(templates, plan.template());
		Optional<BlockPos> inletLocal = cells.flatMap(HousePlacement::inletCell);
		if (def.isEmpty() || inletLocal.isEmpty()) {
			return Optional.empty();
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		BlockPos origin = inlet.subtract(StructureTemplate.calculateRelativePosition(settings, inletLocal.get()));
		TownBuilding house = TownBuilding.laid(def.get().template(), plan.template(), def.get().name(), origin,
				rotation, inlet);
		Optional<BuildingPlan> built = BuildingPlan.of(templates, house, def.get());
		if (built.isEmpty()) {
			return Optional.empty();
		}
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		List<ConstructionRow.Text> problems = new ArrayList<>();
		if (!works.buildingsKnown()) {
			problems.add(ConstructionRow.Text.of(UNKNOWN_TOWN));
		}
		OptionalInt hall = TownHall.index(works);
		if (hall.isPresent() && !works.buildings().get(hall.getAsInt()).everRestored()) {
			problems.add(ConstructionRow.Text.of(HALL_FIRST));
		}
		if (TownHouses.countedHouses(works) >= town.tier().houseLimit()) {
			problems.add(ConstructionRow.Text.of(LIMIT));
		}
		cellProblems(level, town, built.get(), inlet, problems);
		neighbourProblems(templates, works, built.get(), problems);
		streetProblem(works, built.get(), inlet, problems);
		// The branch to the town's network (round 2 of 0.1.219, step 8): its cells, or none — then the window warns
		// «no way to the network». A warning, not a problem: a way through a chunk not loaded yet is no way now, and
		// the network grows to the house once it is laid and the ground is there.
		List<BlockPos> branch = List.of();
		boolean way = false;
		Optional<TownPowerLinks.Socket> socket = TownPowerLinks.inletOf(built.get());
		Optional<BoundingBox> box = built.get().box();
		if (socket.isPresent() && box.isPresent() && TownPowerLinks.hallInlet(level, town.id()).isPresent()) {
			Optional<List<BlockPos>> road = TownRoadNetwork.previewBranch(level, town.id(), socket.get().front(),
					box.get());
			if (road.isPresent()) {
				branch = road.get();
				way = true;
			}
		}
		return Optional.of(new Site(house, built.get(), problems, way ? Optional.of(branch) : Optional.empty()));
	}

	/** The border, unloaded chunks and the blocks in the way, over every cell of {@code plan}. */
	private static void cellProblems(ServerLevel level, Settlement town, BuildingPlan plan, BlockPos inlet,
			List<ConstructionRow.Text> problems) {
		boolean outside = false;
		boolean unloaded = false;
		Set<String> obstacles = new LinkedHashSet<>();
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (!town.territoryContains(level.dimension(), pos)) {
				outside = true;
				continue;
			}
			if (!level.isLoaded(pos)) {
				unloaded = true;
				continue;
			}
			BlockState world = level.getBlockState(pos);
			if (pos.equals(inlet) || plan.matches(i, world)) {
				continue;
			}
			DroneCellRules.Cell cell = DroneCellSurvey.survey(plan, i, world);
			if (!cell.worldAir() && !cell.worldLiquid() && (cell.worldProtected() || !cell.worldRemovable())
					&& obstacles.size() < MAX_NAMED_OBSTACLES) {
				obstacles.add(world.getBlock().getDescriptionId());
			}
		}
		if (outside) {
			problems.add(ConstructionRow.Text.of(OUTSIDE));
		}
		if (unloaded) {
			problems.add(ConstructionRow.Text.of(BlockReason.CHUNK_NOT_LOADED));
		}
		obstacles.forEach(block -> problems.add(ConstructionRow.Text.of(BlockReason.OBSTRUCTED, block)));
	}

	/**
	 * A cell of the house on a column of the town's road network (R131): the streets are reserved, a house is not laid
	 * over the main street or another house's branch. Named once.
	 */
	private static void streetProblem(TownWorks works, BuildingPlan plan, BlockPos inlet,
			List<ConstructionRow.Text> problems) {
		TownRoadPlan roads = works.roadPlan();
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (!pos.equals(inlet) && roads.at(new TownRoadTrace.Column(pos.getX(), pos.getZ())).isPresent()) {
				problems.add(ConstructionRow.Text.of(TOWN_STREET));
				return;
			}
		}
	}

	/** Every building of the town the house overlaps, or comes closer to than {@link #MIN_GAP} (R61). */
	private static void neighbourProblems(StructureTemplateManager templates, TownWorks works, BuildingPlan plan,
			List<ConstructionRow.Text> problems) {
		Optional<BoundingBox> box = plan.box();
		if (box.isEmpty()) {
			return;
		}
		for (TownBuilding other : works.buildings()) {
			if (other.withdrawn()) {
				continue;
			}
			Optional<StructureTemplate> template = templates.get(other.shapeTemplate());
			if (template.isEmpty()) {
				continue;
			}
			BoundingBox theirs = template.get().getBoundingBox(
					new StructurePlaceSettings().setRotation(other.rotation()), other.origin());
			int gap = gap(box.get(), theirs);
			if (gap < 0) {
				problems.add(ConstructionRow.Text.of(OVERLAPS, ConstructionRows.name(other)));
			} else if (gap < MIN_GAP) {
				problems.add(ConstructionRow.Text.of(TOO_CLOSE, ConstructionRows.name(other)));
			}
		}
	}

	/**
	 * The free blocks between two buildings' boxes seen from above, along the axis where it is wider; negative when
	 * the boxes overlap (the same measure as the town layout's own R61 check, {@code tools/ala_town_rules.py}).
	 */
	static int gap(BoundingBox a, BoundingBox b) {
		int alongX = Math.max(b.minX() - a.maxX(), a.minX() - b.maxX()) - 1;
		int alongZ = Math.max(b.minZ() - a.maxZ(), a.minZ() - b.maxZ()) - 1;
		return Math.max(alongX, alongZ);
	}

	/**
	 * Lays the house the survey of the same arguments finds free: adds it to the town, turns the inlet with it and
	 * queues its clearing and its building. Returns the survey and, when the house was laid, its index.
	 */
	public static Optional<Laid> lay(ServerLevel level, Settlement town, BlockPos inlet, RestorationPlan plan,
			Rotation rotation) {
		Optional<Site> site = survey(level, town, inlet, plan, rotation);
		if (site.isEmpty() || !site.get().problems().isEmpty() || laidAt(level, town.id(), inlet).isPresent()) {
			return site.map(found -> new Laid(found, OptionalInt.empty()));
		}
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		int index = works.lay(site.get().building());
		turnInlet(level, inlet, site.get().plan());
		works.queue().raise(WorkJobType.CLEAR, index);
		if (RoadWork.needsRoad(level, town.id(), index)) {
			// MOD-861 (R108, R114): the road from the hall's inlet to this one, between the clearing and the building;
			// the building job waits for it. A town whose hall has no inlet gets no road job, as for a ruin.
			works.queue().raise(WorkJobType.ROAD, index);
		}
		works.queue().raise(WorkJobType.BUILD, index);
		HallBuildNews.houseLaid(level, town.id(), index);
		Industrialization.LOGGER.info("[ala-towns] settlement {}: house {} laid at {} as building {}", town.id(),
				plan.template(), inlet, index);
		return Optional.of(new Laid(site.get(), OptionalInt.of(index)));
	}

	/** What {@link #lay} came to: the survey, and the index of the house laid, if it was. */
	public record Laid(Site site, OptionalInt index) {
	}

	/** The index of the laid house whose inlet stands at {@code inlet} in town {@code town}, if one does. */
	public static OptionalInt laidAt(ServerLevel level, int town, BlockPos inlet) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (!building.withdrawn() && building.laidAt().map(inlet::equals).orElse(false)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	/**
	 * Whether the laid house {@code index} may still be called off: it was never restored, and no building job on it
	 * has been taken or got anywhere.
	 */
	public static boolean cancellable(TownWorks works, int index) {
		Optional<TownBuilding> building = works.building(index);
		if (building.isEmpty() || !building.get().isLaid() || building.get().withdrawn()
				|| building.get().everRestored()) {
			return false;
		}
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() == index && job.type() == WorkJobType.BUILD && (job.progress() > 0
					|| job.status() == WorkJobStatus.TAKEN || job.status() == WorkJobStatus.DONE)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Town {@code town}'s laid houses whose inlet is gone before their building started are called off (R110 for a
	 * house not built yet). Run with the town's settling, by its lit core; a chunk not loaded is not looked at.
	 */
	public static void tick(ServerLevel level, int town) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		if (found.isEmpty()) {
			return;
		}
		TownWorks works = found.get();
		for (int i = 0; i < works.buildings().size(); i++) {
			Optional<BlockPos> inlet = works.buildings().get(i).laidAt();
			if (inlet.isPresent() && cancellable(works, i) && level.isLoaded(inlet.get())
					&& !level.getBlockState(inlet.get()).is(ModContent.HOUSE_INLET.get())) {
				works.withdraw(i);
				Industrialization.LOGGER.info("[ala-towns] settlement {}: laid house {} called off, its inlet is gone",
						town, i);
			}
		}
	}

	/**
	 * The house plans town {@code town} knows (round 2 of 0.1.219, Q4, Q5), by template: the plans it learned and the
	 * one its restored hall teaches. A plan carried in the inventory is not known until the town learns it — put into
	 * the dispatcher's plan slot or right-clicked onto the core.
	 */
	public static List<RestorationPlan> knownPlans(ServerLevel level, Settlement town, ServerPlayer player) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		Map<String, RestorationPlan> plans = new TreeMap<>();
		for (String id : HousePlans.known(works, TownHall.index(works))) {
			Identifier template = Identifier.tryParse(id);
			if (template == null) {
				continue;
			}
			Identifier style = town.style().isEmpty() ? null : Identifier.tryParse(town.style());
			keep(plans, new RestorationPlan(style != null ? style : styleOf(template), template), town);
		}
		return List.copyOf(plans.values());
	}

	/** The style a template's folder names: {@code ns:ala_town/plains/intact/x} — {@code ns:plains}. */
	private static Identifier styleOf(Identifier template) {
		String[] parts = template.getPath().split("/");
		return Identifier.fromNamespaceAndPath(template.getNamespace(), parts.length > 1 ? parts[1] : parts[0]);
	}

	private static void keep(Map<String, RestorationPlan> plans, RestorationPlan plan, Settlement town) {
		if (plan != null && PlanSheetRules.houseType(plan.building()) > 0
				&& (town.style().isEmpty() || town.style().equals(plan.style().toString()))) {
			plans.putIfAbsent(plan.template().toString(), plan);
		}
	}

	private static Optional<TownBuildingDef> definition(ServerLevel level, RestorationPlan plan) {
		for (TownBuildingDef def : TownBuildingDef.byTemplate(level.registryAccess()).values()) {
			if (def.intact().map(plan.template()::equals).orElse(false)) {
				return Optional.of(def);
			}
		}
		return Optional.empty();
	}

	private static Optional<BlockPos> inletCell(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().is(ModContent.HOUSE_INLET.get()))
				.map(Map.Entry::getKey).findFirst();
	}

	/** Turns the inlet the player put down the way its house's template has it, its outer face looking out. */
	private static void turnInlet(ServerLevel level, BlockPos inlet, BuildingPlan plan) {
		BlockState world = level.getBlockState(inlet);
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (plan.pos(i).equals(inlet) && want.hasProperty(HorizontalMachineBlock.FACING)
					&& world.hasProperty(HorizontalMachineBlock.FACING)) {
				level.setBlock(inlet, world.setValue(HorizontalMachineBlock.FACING,
						want.getValue(HorizontalMachineBlock.FACING)), Block.UPDATE_ALL);
				return;
			}
		}
	}

	/** The turn of the house that has its inlet face the way the inlet the player put down faces. */
	static Rotation facingRotation(ServerLevel level, RestorationPlan plan, BlockState inlet) {
		Optional<Map<BlockPos, BlockState>> cells = BuildingPlan.cellsOf(LineStructures.templates(level),
				plan.template());
		Optional<BlockState> want = cells.flatMap(map -> inletCell(map).map(map::get));
		if (want.isEmpty() || !want.get().hasProperty(HorizontalMachineBlock.FACING)
				|| !inlet.hasProperty(HorizontalMachineBlock.FACING)) {
			return Rotation.NONE;
		}
		Direction template = want.get().getValue(HorizontalMachineBlock.FACING);
		Direction placed = inlet.getValue(HorizontalMachineBlock.FACING);
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(template) == placed) {
				return rotation;
			}
		}
		return Rotation.NONE;
	}
}
