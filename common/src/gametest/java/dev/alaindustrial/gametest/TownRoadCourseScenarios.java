package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.TownRoadBlockEntity;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * L2 of the road before a house (MOD-861; owner's round 1 of 0.1.219, R1-4 and R1-5): over ground that steps by two
 * blocks the drone's road keeps every step to one block, cutting into the ground and filling over it; with no road
 * blocks in the dispatcher the road job stops and the dispatcher's view says why, what to do, and asks for the road
 * blocks as the first stage; once they are in, the road is laid, the house's inlet is on the town's line and the house
 * gets built. Replayed by both loaders through the roster (ADR-038).
 */
public final class TownRoadCourseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownRoadCourseScenarios::roadWaitsForItsBlocksThenTheHouseIsBuilt,
						"town_road_course_waits_for_its_blocks_then_the_house").ticks(6000),
				RosterEntry.of(TownRoadCourseScenarios::secondHouseJoinsTheStreetOfTheFirst,
						"town_road_network_second_house_joins_the_first_street").ticks(40));

		private Roster() {}
	}

	private TownRoadCourseScenarios() {}

	private static final Identifier STYLE = Industrialization.id("plains");
	private static final Identifier HOUSE_INTACT = Industrialization.id("ala_town/plains/intact/capsule_house_1");
	/** Ground built under the course: this many blocks under its top. */
	private static final int GROUND_DEPTH = 4;
	/** Air cleared over the built ground. */
	private static final int AIR_OVER = 7;
	/** Ground built round the box of the two ends. */
	private static final int GROUND_MARGIN = 6;

	/**
	 * @implements MOD-861-WALKABLE — the drone's road keeps every step to one block over ground that steps by two,
	 *     its road blocks stand on ground with air over them, and the house's inlet ends on the town's line.
	 * @implements MOD-861-ROAD-FIRST — with no road blocks the road job stops «no materials», the dispatcher's
	 *     «Construction» step is not done and names the stop and its hint, the house's building waits for the road,
	 *     and the «Materials» tab asks for the road blocks; with them in, the road is laid and the house built.
	 */
	public static void roadWaitsForItsBlocksThenTheHouseIsBuilt(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold house = TownHouseScenarios.addIntactHouse(helper, rig, false);
		BlockPos hallFront = TownPowerLinks.hallInlet(level, rig.town()).orElseThrow().front();
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		BlockPos houseFront = TownPowerLinks.inletOf(plan).orElseThrow().front();
		ChunkHold middle = ChunkHold.forTest(helper, level, new BlockPos((hallFront.getX() + houseFront.getX()) / 2,
				hallFront.getY(), (hallFront.getZ() + houseFront.getZ()) / 2).offset(-6, 0, -6));
		ground(level, rig.works(), hallFront, houseFront, true);
		rig.works().retryRoadsNow();

		// One block of the house's lowest layer goes: the house has building work, and its item is in the buffer.
		int gone = lowestBlock(plan);
		BlockState want = plan.expected(gone);
		BlockPos gonePos = plan.pos(gone);
		level.setBlock(gonePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT,
				RestorationPlanItem.of(new RestorationPlan(STYLE, HOUSE_INTACT)));
		Identifier item = Identifier.parse(BuildMaterials.itemOf(want));
		dispatcher.buffer().insert(new ItemStack(BuiltInRegistries.ITEM.getValue(item), 4));
		TownWorksScanner.measureNow(level, rig.town(), 1);
		WorkJob build = rig.works().queue().raise(WorkJobType.BUILD, 1).orElseThrow();

		List<BlockPos> course = new ArrayList<>();
		int[] phase = {0};
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			TownWorks works = rig.works();
			Optional<WorkJob> road = works.queue().jobs().stream()
					.filter(job -> job.type() == WorkJobType.ROAD && job.building() == 1).findFirst();
			if (phase[0] == 0) {
				helper.assertTrue(road.isPresent() && road.get().status() == WorkJobStatus.BLOCKED
						&& road.get().reason().map(r -> r.key().equals(BlockReason.NO_MATERIALS)).orElse(false),
						"waiting for the road job to stop «no materials»: " + works.queue().jobs());
				checkStoppedView(helper, rig, build);
				course.addAll(RoadWork.course(level, rig.town(), 1).cells());
				RoadWork.Bill bill = RoadWork.bill(level, course);
				Industrialization.LOGGER.info("[ala-towns] measure: road of {} cells stopped for its blocks at "
						+ "tick {}, bill {}, course {}", course.size(), helper.getTick(), bill, course);
				dispatcher.buffer().insert(new ItemStack(ModContent.TOWN_ROAD.get().asItem(), bill.roads() + 4));
				dispatcher.buffer().insert(new ItemStack(Items.DIRT, 64));
				phase[0] = 1;
				throw helper.assertionException("the road blocks are in: the road may be laid now");
			}
			if (phase[0] == 1) {
				helper.assertTrue(road.isPresent() && road.get().status() == WorkJobStatus.DONE,
						"waiting for the road to be laid: " + road);
				checkLaidRoad(helper, level, rig, course, plan);
				Industrialization.LOGGER.info("[ala-towns] measure: road laid at tick {}", helper.getTick());
				phase[0] = 2;
			}
			helper.assertTrue(level.getBlockState(gonePos).equals(want),
					"waiting for the house's block to be built after its road: " + works.queue().jobs());
			house.release();
			middle.release();
			rig.release();
		});
	}

	private static final Identifier HOUSE = Industrialization.id("ala_town/plains/capsule_house_1");
	/** Where the two houses of the network case stand from the hall's origin: side by side, south of the hall. */
	private static final List<BlockPos> TWO_HOUSES = List.of(new BlockPos(0, 0, -20), new BlockPos(16, 0, -22));

	/**
	 * @implements MOD-881-SECOND-HOUSE — with two houses the town's road network is one tree: the two houses' ways to
	 *     the hall share more than the hall's door, the network is shorter than two separate roads, and once the first
	 *     house's road is laid the second one's road step asks only for its own cells; laying them puts its inlet on
	 *     the town's line.
	 */
	public static void secondHouseJoinsTheStreetOfTheFirst(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		List<ChunkHold> holds = new ArrayList<>();
		List<TownBuilding> buildings = new ArrayList<>();
		buildings.add(rig.works().buildings().getFirst());
		for (BlockPos offset : TWO_HOUSES) {
			BlockPos at = rig.origin().offset(offset);
			holds.add(ChunkHold.forTest(helper, level, at));
			LineStructures.templates(level).getOrCreate(HOUSE_INTACT).placeInWorld(level, at, at,
					new StructurePlaceSettings().setRotation(Rotation.NONE)
							.addProcessor(JigsawReplacementProcessor.INSTANCE)
							.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK),
					RandomSource.create(881L), Block.UPDATE_CLIENTS);
			buildings.add(TownBuilding.found(HOUSE, Optional.of(HOUSE_INTACT),
					"settlement.alaindustrial.building.capsule_house", at, Rotation.NONE));
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(buildings);
		BlockPos hallFront = TownPowerLinks.hallInlet(level, rig.town()).orElseThrow().front();
		List<BlockPos> fronts = new ArrayList<>();
		for (int i = 1; i <= TWO_HOUSES.size(); i++) {
			BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), i).orElseThrow();
			fronts.add(TownPowerLinks.inletOf(plan).orElseThrow().front());
		}
		for (BlockPos front : fronts) {
			holds.add(ChunkHold.forTest(helper, level, new BlockPos((hallFront.getX() + front.getX()) / 2,
					hallFront.getY(), (hallFront.getZ() + front.getZ()) / 2).offset(-6, 0, -6)));
			ground(level, rig.works(), hallFront, front, false);
		}
		rig.works().retryRoadsNow();
		List<BlockPos> first = RoadWork.course(level, rig.town(), 1).cells();
		List<BlockPos> second = RoadWork.course(level, rig.town(), 2).cells();
		helper.assertTrue(!first.isEmpty() && !second.isEmpty(), "both houses must be joined to the network: "
				+ RoadWork.course(level, rig.town(), 1).problem() + " / " + RoadWork.course(level, rig.town(), 2)
				.problem());
		int shared = 0;
		while (shared < Math.min(first.size(), second.size()) && first.get(shared).equals(second.get(shared))) {
			shared++;
		}
		helper.assertTrue(shared > 1, "the two houses' roads must share a street, not only the hall's door: "
				+ first + " / " + second);
		int network = rig.works().roadPlan().nodes().size();
		helper.assertTrue(network < first.size() + second.size(),
				"one network must be shorter than two roads: " + network);
		lay(level, first);
		RoadWork.Bill left = RoadWork.bill(level, second);
		helper.assertTrue(left.roads() > 0 && left.roads() <= second.size() - shared,
				"the second road step must ask only for its own cells: " + left + " of " + second.size());
		lay(level, second);
		TownPowerLinks.Socket inlet = TownPowerLinks.inletOf(TownWorksScanner.plan(level, rig.town(), 2)
				.orElseThrow()).orElseThrow();
		helper.assertTrue(TownPowerLinks.link(level, rig.town(), inlet) == TownPowerLinks.Link.CONNECTED,
				"the second house's road must put its inlet on the town's line through the first one's street");
		Industrialization.LOGGER.info("[ala-towns] measure: network of {} cells, roads of {} and {}, {} shared",
				network, first.size(), second.size(), shared);
		holds.forEach(ChunkHold::release);
		rig.release();
		helper.succeed();
	}

	/** Lays every slot of {@code course} that still wants work, as a drone would, and joins its segments up. */
	private static void lay(ServerLevel level, List<BlockPos> course) {
		for (RoadWork.Slot slot : RoadWork.slots(course)) {
			if (RoadWork.want(level, slot) != RoadWork.Want.WORK) {
				continue;
			}
			BlockState put = switch (slot.kind()) {
				case CUT -> Blocks.AIR.defaultBlockState();
				case FILL -> Blocks.DIRT.defaultBlockState();
				case ROAD -> ModContent.TOWN_ROAD.get().defaultBlockState();
			};
			level.setBlock(slot.pos(), put, Block.UPDATE_ALL);
		}
		for (RoadWork.Slot slot : RoadWork.slots(course)) {
			if (level.getBlockEntity(slot.pos()) instanceof TownRoadBlockEntity segment) {
				segment.ensureRegistered();
			}
		}
	}

	/** The dispatcher's view while the road job stands «no materials» (R1-5). */
	private static void checkStoppedView(GameTestHelper helper, Rig rig, WorkJob build) {
		DispatcherView view = DispatcherViews.of(rig.level(), rig.dispatcher(),
				SettlementRegistry.get(rig.level().getServer()).find(rig.town()));
		helper.assertTrue(view.checklist().jobs() && view.checklist().stalled(),
				"a road stopped for materials must leave the «Construction» step not done: " + view.checklist());
		List<String> keys = view.task().stream().map(ConstructionRow.Text::key).toList();
		helper.assertTrue(keys.contains(BlockReason.NO_MATERIALS)
				&& keys.contains(BlockReason.NO_MATERIALS + BlockReason.HINT),
				"the task must name the stop and what to do about it: " + keys);
		helper.assertTrue(keys.contains(DispatcherViews.WAITS_FOR_ROAD),
				"the house's building must say it waits for the road: " + keys);
		helper.assertTrue(!view.materials().isEmpty()
				&& view.materials().getFirst().key().equals(DispatcherViews.ROAD_STAGE),
				"the «Materials» tab must show the road as the first stage: " + view.materials());
		Optional<DispatcherView.BillLine> roads = view.bill().stream()
				.filter(line -> line.item().equals(RoadWork.ROAD_ITEM)).findFirst();
		helper.assertTrue(roads.isPresent() && roads.get().need() > 0 && !roads.get().covered(),
				"the bill must ask for the road blocks: " + view.bill());
		helper.assertTrue(rig.works().queue().job(build.id()).map(job -> job.status() == WorkJobStatus.QUEUED)
				.orElse(false), "the house's building must wait queued behind its road");
	}

	/** The road as laid: walkable, standing on ground with air over it, and the house's inlet on the line (R1-4). */
	private static void checkLaidRoad(GameTestHelper helper, ServerLevel level, Rig rig, List<BlockPos> course,
			BuildingPlan plan) {
		helper.assertTrue(course.size() > 2, "control: the course is too short: " + course);
		boolean earthwork = false;
		for (int i = 0; i < course.size(); i++) {
			BlockPos cell = course.get(i);
			helper.assertTrue(level.getBlockState(cell).is(ModContent.TOWN_ROAD.get()), "no road at " + cell);
			BlockState under = level.getBlockState(cell.below());
			helper.assertTrue(!under.isAir() && !under.canBeReplaced(), "the road at " + cell + " hangs in the air");
			if (i > 0) {
				int step = Math.abs(cell.getY() - course.get(i - 1).getY());
				helper.assertTrue(step <= 1, "a step of " + step + " blocks at " + cell);
			}
			if (i > 0 && i < course.size() - 1) {
				for (int h = 1; h <= 2; h++) {
					BlockState over = level.getBlockState(cell.above(h));
					helper.assertTrue(over.isAir() || over.canBeReplaced(), "no headroom over the road at " + cell);
				}
				earthwork |= cell.getY() != course.getFirst().getY();
			}
		}
		helper.assertTrue(earthwork, "control: the ground never made the road leave its height: " + course);
		for (RoadWork.Slot slot : RoadWork.slots(course)) {
			if (slot.kind() == RoadWork.Kind.ROAD
					&& level.getBlockEntity(slot.pos()) instanceof TownRoadBlockEntity segment) {
				segment.ensureRegistered();
			}
		}
		TownPowerLinks.Socket inlet = TownPowerLinks.inletOf(plan).orElseThrow();
		helper.assertTrue(TownPowerLinks.link(level, rig.town(), inlet) == TownPowerLinks.Link.CONNECTED,
				"the laid road must put the house's inlet on the town's line");
	}

	/** The plan cell of the lowest counted block with an item: the house's first stage. */
	private static int lowestBlock(BuildingPlan plan) {
		int best = -1;
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (plan.debris(i) || want.isAir() || want.hasBlockEntity()
					|| BuildMaterials.itemOf(want).equals(BuildStageRules.NO_ITEM)) {
				continue;
			}
			if (best < 0 || plan.pos(i).getY() < plan.pos(best).getY()) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * Builds ground between two inlet fronts (the road's ends) the way a slope does, round the town's buildings: grass
	 * on dirt with its top at the hall front's height and air over it; with {@code steps}, a ditch two blocks deep and
	 * a ridge two blocks high across the whole way, so the road must cut and fill. The ends' own columns get ground up
	 * to their height and nothing over it.
	 */
	static void ground(ServerLevel level, TownWorks works, BlockPos from, BlockPos to, boolean steps) {
		List<BoundingBox> boxes = new ArrayList<>();
		for (TownBuilding building : works.buildings()) {
			LineStructures.templates(level).get(building.shapeTemplate()).ifPresent(template -> boxes.add(
					template.getBoundingBox(new StructurePlaceSettings().setRotation(building.rotation()),
							building.origin())));
		}
		int base = from.getY();
		boolean alongZ = Math.abs(to.getZ() - from.getZ()) >= Math.abs(to.getX() - from.getX());
		int a = alongZ ? from.getZ() : from.getX();
		int b = alongZ ? to.getZ() : to.getX();
		for (int x = Math.min(from.getX(), to.getX()) - GROUND_MARGIN;
				x <= Math.max(from.getX(), to.getX()) + GROUND_MARGIN; x++) {
			for (int z = Math.min(from.getZ(), to.getZ()) - GROUND_MARGIN;
					z <= Math.max(from.getZ(), to.getZ()) + GROUND_MARGIN; z++) {
				BlockPos end = x == from.getX() && z == from.getZ() ? from
						: x == to.getX() && z == to.getZ() ? to : null;
				if (end == null && inside(boxes, x, z)) {
					continue;
				}
				int top = end != null ? end.getY() : base;
				if (steps && end == null && b != a) {
					double along = ((alongZ ? z : x) - a) / (double) (b - a);
					if (along >= 0.3 && along < 0.4) {
						top = base - 2;
					} else if (along >= 0.6 && along < 0.9) { // long enough that the road climbs it (R3-5)
						top = base + 2;
					}
				}
				for (int y = top - GROUND_DEPTH; y <= top; y++) {
					level.setBlock(new BlockPos(x, y, z), y == top ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				if (end == null) {
					for (int y = top + 1; y <= base + AIR_OVER; y++) {
						level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	private static boolean inside(List<BoundingBox> boxes, int x, int z) {
		for (BoundingBox box : boxes) {
			if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
				return true;
			}
		}
		return false;
	}
}
