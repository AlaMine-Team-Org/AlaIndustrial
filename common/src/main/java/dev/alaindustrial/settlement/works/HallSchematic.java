package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.TownRoadBlock;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.network.HallSchematicRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.DroneCellSurvey;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of the town hall's schematic (MOD-857, owner's round 2, point 7): the cells of the hall that do not
 * match its intact template yet, each with the block that belongs there, for the player who turned the schematic on
 * in the core's «Construction» tab. The client draws them as ghost blocks; the server keeps nothing about who looks.
 *
 * <p><b>Which cells.</b> Every cell of the hall's plan ({@link BuildingPlan}) that wants a block and does not hold it,
 * in a loaded chunk. Debris (a cell the intact hall keeps empty) is not drawn: a ghost of air shows nothing. A cell on
 * the shaft of the lab under the hall is left out too: the builder drone never fills it (R93), and the schematic shows
 * what the drone will build. The schematic shows one building with its road still to lay — the one the player
 * switched it on for, by default the order the dispatcher shows (MOD-861; owner's round 1 of 0.1.219, R1-5; round 2,
 * R2-5, Q6) — or the town's road network ({@link TownRoadNetwork}). More than
 * {@link HallSchematicPayload#MAX_CELLS}: the nearest to the player
 * ({@link SchematicSelection}).
 *
 * <p>Visual only, so any member of the town near the core may ask, restorer or not; a stranger to the town gets no
 * reply (R98). The plan is the cached one
 * ({@link TownWorksScanner#plan}); a reply reads at most one block state per plan cell.
 */
public final class HallSchematic {

	/** How far from the core, along the farther horizontal axis, a request is still answered. */
	public static final double VIEW_DISTANCE = 192.0;

	private HallSchematic() {
	}

	/** Answers {@code player}'s request for the schematic of the town whose core stands at {@code corePos}. */
	public static void request(ServerPlayer player, BlockPos corePos) {
		request(player, corePos, HallSchematicRequestPayload.DEFAULT);
	}

	/**
	 * Answers {@code player}'s request for the schematic of building {@code building} — or the road network, or the
	 * order the dispatcher shows ({@link HallSchematicRequestPayload}) — of the town whose core stands at
	 * {@code corePos}.
	 */
	public static void request(ServerPlayer player, BlockPos corePos, int building) {
		ServerLevel level = player.level();
		if (!level.isLoaded(corePos)
				|| !(level.getBlockEntity(corePos) instanceof SettlementCoreBlockEntity core)
				|| core.isMisplaced() || core.isUnbound() || !core.admits(player)
				|| Math.max(Math.abs(player.getX() - (corePos.getX() + 0.5)),
						Math.abs(player.getZ() - (corePos.getZ() + 0.5))) > VIEW_DISTANCE) {
			return;
		}
		Optional<Settlement> town = core.settlement();
		if (town.isEmpty()) {
			return;
		}
		HallSchematicPayload reply = reply(level, corePos, town.get().id(), player.position(), building);
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, HallSchematicPayload.TYPE)) {
			dispatcher.sendToPlayer(player, reply);
		}
	}

	/** The reply for the order town {@code town}'s dispatcher shows, as seen from {@code viewer}; for the L2 suite. */
	public static HallSchematicPayload reply(ServerLevel level, BlockPos corePos, int town, Vec3 viewer) {
		return reply(level, corePos, town, viewer, HallSchematicRequestPayload.DEFAULT);
	}

	/**
	 * The reply for building {@code building} of town {@code town} as seen from {@code viewer} (round 2 of 0.1.219,
	 * Q6): that one building's cells still to build and its road still to lay, nothing of any other building; or,
	 * for {@link HallSchematicRequestPayload#NETWORK}, every road cell the town knows still to lay. Public for the L2
	 * suite.
	 */
	public static HallSchematicPayload reply(ServerLevel level, BlockPos corePos, int town, Vec3 viewer,
			int building) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		List<HallSchematicPayload.Ghost> missing;
		if (building == HallSchematicRequestPayload.NETWORK) {
			missing = network(level, town);
		} else {
			int target = building >= 0 && works.building(building).isPresent() ? building : target(level, town, works);
			Optional<BuildingPlan> plan = target < 0 ? Optional.empty() : TownWorksScanner.plan(level, town, target);
			if (plan.isEmpty()) {
				return new HallSchematicPayload(level.dimension(), corePos, false, 0, List.of());
			}
			missing = missing(level, plan.get());
			missing.addAll(road(level, town, works, target));
			Optional<BlockPos> gap = ConstructionRows.gapCell(level, town, works.buildings().get(target), target);
			if (gap.isPresent()) {
				markGap(missing, gap.get());
			}
		}
		int[] x = new int[missing.size()];
		int[] y = new int[missing.size()];
		int[] z = new int[missing.size()];
		for (int i = 0; i < missing.size(); i++) {
			long pos = missing.get(i).pos();
			x[i] = BlockPos.getX(pos);
			y[i] = BlockPos.getY(pos);
			z[i] = BlockPos.getZ(pos);
		}
		List<HallSchematicPayload.Ghost> sent = new ArrayList<>();
		for (int index : SchematicSelection.pick(x, y, z, viewer.x, viewer.y, viewer.z,
				HallSchematicPayload.MAX_CELLS)) {
			sent.add(missing.get(index));
		}
		return new HallSchematicPayload(level.dimension(), corePos, true, missing.size(), sent);
	}

	/**
	 * The road still to lay to building {@code index} (MOD-861, R114; owner's round 1 of 0.1.219, R1-4): its road
	 * blocks and the earth a raised stretch stands on, while its road job is open or it still needs its road; the
	 * joints hidden in the ground are left out, but not the road at a door under an inlet's riser, and the riser shows
	 * as the wire it is, not a block (R3-5).
	 */
	static List<HallSchematicPayload.Ghost> road(ServerLevel level, int town, TownWorks works, int index) {
		List<HallSchematicPayload.Ghost> out = new ArrayList<>();
		boolean planned = works.queue().open(WorkJobType.ROAD, index).isPresent()
				|| works.building(index).map(TownHouses::isHouse).orElse(false)
						&& RoadWork.needsRoad(level, town, index);
		if (!planned) {
			return out;
		}
		BlockState road = ModContent.TOWN_ROAD.get().defaultBlockState();
		BlockState riser = road.setValue(TownRoadBlock.RISER, true).setValue(PipeBlock.DOWN, true);
		BlockState fill = Blocks.DIRT.defaultBlockState();
		List<BlockPos> course = RoadWork.course(level, town, index).cells();
		for (RoadWork.Slot slot : RoadWork.slots(course)) {
			// A joint under an inlet's riser is the road at the door, flush with the floor, not hidden in the ground.
			boolean doorstep = slot.joint() && RoadWork.riser(course, slot.pos().above());
			if (slot.kind() == RoadWork.Kind.CUT || slot.joint() && !doorstep || !level.isLoaded(slot.pos())
					|| RoadWork.want(level, slot) == RoadWork.Want.NONE) {
				continue;
			}
			BlockState ghost = slot.kind() != RoadWork.Kind.ROAD ? fill
					: RoadWork.riser(course, slot.pos()) ? riser : road;
			out.add(new HallSchematicPayload.Ghost(slot.pos().asLong(), ghost));
		}
		return out;
	}

	/**
	 * Every road cell of town {@code town}'s network still to lay ({@link TownRoadNetwork#cells}), in a loaded chunk;
	 * the laid ones are not drawn.
	 */
	static List<HallSchematicPayload.Ghost> network(ServerLevel level, int town) {
		List<HallSchematicPayload.Ghost> out = new ArrayList<>();
		BlockState road = ModContent.TOWN_ROAD.get().defaultBlockState();
		for (TownRoadNetwork.PlanCell cell : TownRoadNetwork.cells(level, town)) {
			if (!cell.built() && level.isLoaded(cell.pos())) {
				out.add(new HallSchematicPayload.Ghost(cell.pos().asLong(), road));
			}
		}
		// R132 (MOD-884): where a restored building is cut off, its first missing cell — laid once, so the plan
		// counts it built and the loop above passes it — is drawn as a highlighted road ghost.
		List<TownBuilding> buildings = SettlementRegistry.get(level.getServer()).works().town(town).buildings();
		for (int i = 0; i < buildings.size(); i++) {
			ConstructionRows.gapCell(level, town, buildings.get(i), i).ifPresent(gap -> markGap(out, gap));
		}
		return out;
	}

	/**
	 * Puts a road ghost marked as the network's gap at {@code gap} into {@code ghosts}, in place of the ghost drawn
	 * there already, if any (R132, MOD-884).
	 */
	static void markGap(List<HallSchematicPayload.Ghost> ghosts, BlockPos gap) {
		long packed = gap.asLong();
		ghosts.removeIf(ghost -> ghost.pos() == packed);
		ghosts.add(new HallSchematicPayload.Ghost(packed, ModContent.TOWN_ROAD.get().defaultBlockState(), true));
	}

	/**
	 * The building a schematic of no building in particular shows: the order the town's dispatcher shows
	 * ({@link DispatcherViews#target}), else the first order, else the hall; -1 for a town that knows none.
	 */
	public static int target(ServerLevel level, int town, TownWorks works) {
		int selected = TownDispatcherBlockEntity.of(level, town).map(TownDispatcherBlockEntity::selected).orElse(-1);
		return DispatcherViews.target(works, selected).orElse(-1);
	}

	/** Every cell of {@code plan} still to build, in the plan's order. */
	static List<HallSchematicPayload.Ghost> missing(ServerLevel level, BuildingPlan plan) {
		List<HallSchematicPayload.Ghost> out = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (want.isAir() || plan.onLabShaft(i)) {
				continue;
			}
			BlockPos pos = plan.pos(i);
			if (!level.isLoaded(pos) || plan.matches(i, level.getBlockState(pos))) {
				continue;
			}
			out.add(new HallSchematicPayload.Ghost(plan.packedPos(i), want));
		}
		BlockState earth = Blocks.DIRT.defaultBlockState();
		for (int f = 0; f < plan.fillSize(); f++) {
			BlockPos pos = plan.fillPos(f);
			if (level.isLoaded(pos) && DroneCellSurvey.needsFoundation(level, plan, f)) {
				out.add(new HallSchematicPayload.Ghost(pos.asLong(), earth)); // the foundation still to fill (R3-5)
			}
		}
		return out;
	}
}
