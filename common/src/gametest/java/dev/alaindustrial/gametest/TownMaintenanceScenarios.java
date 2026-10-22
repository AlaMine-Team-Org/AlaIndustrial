package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.drone.StartReadiness;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L2 suite of a restored town hall that looks after itself (owner's round 4 of stage 6): damage to it queues a repair
 * by itself and the drones mend it, the restorer hearing «damaged» and «mended» rather than stages; a clearing frees a
 * ring round the hall of terrain; the core's tab offers «Clear» and «Build» only for work there is. Replayed by both
 * loaders through the roster (ADR-038).
 */
public final class TownMaintenanceScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownMaintenanceScenarios::damagedHallIsMendedByItself,
						"town_maintenance_damaged_hall_is_mended_by_itself").ticks(2400),
				RosterEntry.of(TownMaintenanceScenarios::clearingFreesARingAroundTheHall,
						"town_maintenance_clearing_frees_a_ring_around_the_hall").ticks(2400),
				RosterEntry.of(TownMaintenanceScenarios::tabOffersOnlyTheWorkThereIs,
						"town_maintenance_tab_offers_only_the_work_there_is").ticks(40));

		private Roster() {}
	}

	private TownMaintenanceScenarios() {}

	private static final String ROOF = "settlement.alaindustrial.building.key.roof";

	/**
	 * @implements MOD-858-REPAIR — a brick broken out of a restored hall's wall queues a repair by itself, with no
	 *     press: a building job marked a repair, which a drone takes and finishes from the dispatcher's buffer; the
	 *     dispatcher's «Start» stays shut («the drones look after it themselves»).
	 * @implements MOD-860-REPAIR-NEWS — the restorer hears «damaged, the drones mend it» once and «mended» once, and no
	 *     stage line; the dispatcher's journal notes both and no closed stage.
	 */
	public static void damagedHallIsMendedByItself(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 4));
		List<String> heard = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town() && line.getSiblings().getLast().getContents() instanceof TranslatableContents t) {
				heard.add(t.getKey());
			}
		});
		int journalBefore = rig.dispatcher().journal().entries().size();
		BlockPos cell = TownDroneScenarios.takeOutAWallBrick(helper, rig);
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			TownWorks works = rig.works();
			List<WorkJob> repairs = works.queue().jobs().stream().filter(WorkJob::repair).toList();
			helper.assertTrue(repairs.size() == 1 && repairs.getFirst().type() == WorkJobType.BUILD,
					"one repair building job must be queued by itself, the queue holds " + works.queue().jobs());
			helper.assertTrue(repairs.getFirst().status() == WorkJobStatus.DONE, "the repair must be done, it is "
					+ repairs.getFirst() + ", nest " + rig.nest(0).status());
			helper.assertTrue(rig.level().getBlockState(cell).is(Blocks.BRICKS), "the brick must be back");
			helper.assertTrue(works.buildings().getFirst().state() == BuildingState.INTACT,
					"the hall must stand whole again: " + works.buildings().getFirst());
			helper.assertTrue(rig.dispatcher().buffer().count("minecraft:bricks") == 3,
					"one brick must have left the buffer");
			helper.assertTrue(heard.equals(List.of(HallBuildNews.HALL_DAMAGED, HallBuildNews.HALL_MENDED)),
					"the restorer must hear «damaged» and «mended», nothing else: " + heard);
			List<WorkJournal.Kind> journal = rig.dispatcher().journal().entries()
					.subList(journalBefore, rig.dispatcher().journal().entries().size()).stream()
					.map(WorkJournal.Entry::kind).toList();
			helper.assertTrue(journal.contains(WorkJournal.Kind.DAMAGED) && journal.contains(WorkJournal.Kind.MENDED)
					&& !journal.contains(WorkJournal.Kind.STAGE_DONE), "the journal must note the damage and the "
					+ "mending and no stage: " + journal);
			Optional<Settlement> town = SettlementRegistry.get(rig.level().getServer()).find(rig.town());
			DispatcherView view = DispatcherViews.of(rig.level(), rig.dispatcher(), town);
			List<StartReadiness.Blocker> blockers = StartReadiness.blockers(true, view.checklist(),
					view.checklist().hall(), 1);
			helper.assertTrue(blockers.equals(List.of(StartReadiness.Blocker.RESTORED)),
					"«Start» must stay shut on a restored hall: " + blockers);
			deaf.run();
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-RING — a clearing takes the terrain out of a one-block ring round the hall at and above its
	 *     ground level: a sandstone bump against the wall and a dirt block over it go; a chest in the ring and the
	 *     ground under the ground level stay.
	 */
	public static void clearingFreesARingAroundTheHall(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 0).orElseThrow();
		helper.assertTrue(plan.ringSize() > 0, "control: the hall has no ring");
		BlockPos bump = againstTheWall(level, plan);
		helper.assertTrue(bump != null, "control: no ring cell at ground level against a wall");
		BlockPos over = bump.above();
		Set<BlockPos> ring = ringCells(plan);
		helper.assertTrue(ring.contains(over), "control: the cell over the bump is not in the ring: " + over);
		BlockPos chest = null;
		for (BlockPos pos : ring) {
			if (pos.getY() == plan.groundLevel() && pos.distManhattan(bump) > 4) {
				chest = pos;
				break;
			}
		}
		helper.assertTrue(chest != null, "control: no second ring cell at ground level");
		BlockPos ground = bump.below();
		helper.assertTrue(!ring.contains(ground), "the ground under the ground level must not be in the ring");
		level.setBlock(bump, Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(over, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(ground, Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		BlockPos chestAt = chest;
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the clearing is " + now + ", nest "
					+ rig.nest(0).status());
			helper.assertTrue(level.getBlockState(bump).isAir() && level.getBlockState(over).isAir(),
					"the bump against the wall and the dirt over it must go: " + level.getBlockState(bump) + ", "
							+ level.getBlockState(over));
			helper.assertTrue(level.getBlockState(chestAt).is(Blocks.CHEST),
					"the chest (a block with data) must stand");
			helper.assertTrue(level.getBlockState(ground).is(Blocks.SANDSTONE),
					"the ground under the hall's ground level must stay");
			helper.assertTrue(rig.dispatcher().journal().entries().stream()
					.anyMatch(e -> e.kind() == WorkJournal.Kind.CLEARED
							&& e.subject().equals(Blocks.SANDSTONE.getDescriptionId())),
					"the journal must note the cleared sandstone: " + rig.dispatcher().journal().entries());
			rig.release();
		});
	}

	/**
	 * @implements MOD-857-BUTTONS — the core's «Construction» tab offers «Build» on a hall with a hole in its roof
	 *     and no «Clear» while nothing is in the way; a cobweb in the hall's debris brings «Clear».
	 */
	public static void tabOffersOnlyTheWorkThereIs(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		// The town forgets the hall was ever restored: a building never restored keeps its buttons.
		TownBuilding hall = rig.works().buildings().getFirst();
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(TownBuilding.found(hall.template(), hall.intact(),
				hall.name(), hall.origin(), hall.rotation())));
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 0).orElseThrow();
		int roofKey = plan.keyNames().indexOf(ROOF);
		helper.assertTrue(roofKey >= 0, "control: the hall has no roof key point: " + plan.keyNames());
		int roof = -1;
		for (int i = 0; i < plan.size() && roof < 0; i++) {
			if ((plan.keyMask(i) & (1 << roofKey)) != 0 && !plan.expected(i).isAir()) {
				roof = i;
			}
		}
		level.setBlock(plan.pos(roof), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		TownWorksScanner.measureNow(level, rig.town(), 0);
		TownWorks works = registry.works().town(rig.town());
		helper.assertTrue(works.buildings().getFirst().state() == BuildingState.RUIN,
				"control: the roofless hall never restored must read as a ruin: " + works.buildings().getFirst());
		ConstructionRow row = hallRow(level, registry, rig.town(), works);
		helper.assertTrue(row.allows(ConstructionRow.Action.RAISE_BUILD)
				&& !row.allows(ConstructionRow.Action.RAISE_CLEAR),
				"a hole in the roof and nothing in the way: «Build» only, the row offers " + row.actions());
		BlockPos web = rig.plan().pos(rig.debris().getFirst());
		level.setBlock(web, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		row = hallRow(level, registry, rig.town(), works);
		helper.assertTrue(row.allows(ConstructionRow.Action.RAISE_CLEAR), "a cobweb in the debris must bring «Clear»");
		rig.release();
		helper.succeed();
	}

	private static ConstructionRow hallRow(ServerLevel level, SettlementRegistry registry, int town, TownWorks works) {
		Settlement settlement = registry.find(town).orElseThrow();
		return ConstructionRows.collect(new ConstructionRows.Context(level, settlement, works, true)).stream()
				.filter(r -> r.section().equals(ConstructionRows.SECTION_BUILDINGS) && r.target() == 0).findFirst()
				.orElseThrow();
	}

	/** Every ring cell of {@code plan}. */
	private static Set<BlockPos> ringCells(BuildingPlan plan) {
		Set<BlockPos> out = new HashSet<>();
		for (int r = 0; r < plan.ringSize(); r++) {
			out.add(plan.ringPos(r));
		}
		return out;
	}

	/** A ring cell at the hall's ground level whose side touches a wall block of the hall, or null. */
	private static BlockPos againstTheWall(ServerLevel level, BuildingPlan plan) {
		for (int r = 0; r < plan.ringSize(); r++) {
			BlockPos pos = plan.ringPos(r);
			if (pos.getY() != plan.groundLevel() || !level.getBlockState(pos).isAir()) {
				continue;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockState next = level.getBlockState(pos.relative(side));
				if (!next.isAir() && plan.contains(pos.relative(side))) {
					return pos;
				}
			}
		}
		return null;
	}
}
