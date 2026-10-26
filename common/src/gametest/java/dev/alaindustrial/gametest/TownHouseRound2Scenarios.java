package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ChargingBedBlock;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.HallSchematic;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHousing;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * L2 of the owner's round 2 of 0.1.219 on the capsule houses: a second house of a restored type waits for its own
 * order, and its schematic shows that house alone (Q1, Q5, Q6), and the drone puts a door up whole, with one door
 * item (R2-4).
 * Replayed by both loaders through the roster (ADR-038).
 */
public final class TownHouseRound2Scenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownHouseRound2Scenarios::secondHouseWaitsForItsOrder,
						"town_house_second_waits_for_its_order").ticks(40),
				RosterEntry.of(TownHouseRound2Scenarios::droneHangsADoorWhole,
						"town_house_drone_hangs_a_door_whole").ticks(2400),
				RosterEntry.of(TownHouseRound2Scenarios::droneLaysAChargingBedWhole,
						"town_house_drone_lays_a_charging_bed_whole").ticks(2400),
				RosterEntry.of(TownHouseRound2Scenarios::dispatcherKeepsAKnownHousePlan,
						"town_dispatcher_keeps_a_known_house_plan").ticks(40));

		private Roster() {}
	}

	private TownHouseRound2Scenarios() {}

	private static final Identifier STYLE = Industrialization.id("plains");
	private static final Identifier HOUSE_1_INTACT = Industrialization.id("ala_town/plains/intact/capsule_house_1");
	private static final Identifier HOUSE_1 = Industrialization.id("ala_town/plains/capsule_house_1");

	/**
	 * @implements Q1 — two houses of one type: with the first restored nothing starts on the second by itself and the
	 *     dispatcher asks for nothing; the order on the second starts it — of a type the restored hall taught (Q5),
	 *     with nothing in the plan slot — and the schematic of that order shows no cell of any other building (Q6).
	 */
	public static void secondHouseWaitsForItsOrder(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = TownHouseScenarios.addIntactHouse(helper, rig, true);
		TownWorks works = rig.works();
		helper.assertTrue(works.buildings().get(0).everRestored() && works.buildings().get(1).everRestored(),
				"control: the hall and the wired intact house must count as restored: " + works.buildings());
		BlockPos at = rig.origin().offset(0, 0, 40);
		ChunkHold second = ChunkHold.forTest(helper, level, at);
		int other = works.lay(TownBuilding.found(HOUSE_1, Optional.of(HOUSE_1_INTACT),
				"settlement.alaindustrial.building.capsule_house", at, Rotation.NONE));
		BuildingPlan ruin = TownWorksScanner.plan(level, rig.town(), other).orElseThrow();
		for (int i = 0; i < ruin.size(); i++) {
			level.getChunkAt(ruin.pos(i)); // a ticket loads in the background; the test reads the cells now
		}
		TownWorksScanner.measureNow(level, rig.town(), other);
		rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT, ItemStack.EMPTY);
		for (int i = 0; i < 5; i++) {
			rig.tick();
		}
		helper.assertTrue(works.orders().isEmpty() && works.queue().jobs().stream()
				.noneMatch(job -> job.building() == other && job.status().open()),
				"nothing may start on the second house by itself: " + works.queue().jobs());
		DispatcherView view = view(rig);
		helper.assertTrue(view.bill().isEmpty() && view.orders().isEmpty()
				&& keys(view.materials()).equals(List.of(DispatcherViews.NO_ORDERS)),
				"with no order the dispatcher must ask for nothing: " + view.materials() + " / " + view.bill());

		Settlement town = SettlementRegistry.get(level.getServer()).find(rig.town()).orElseThrow();
		Optional<String> refused = TownOrders.place(level, town, other, "test");
		helper.assertTrue(refused.isEmpty(), "the order on the second house must be placed: " + refused);
		helper.assertTrue(works.orders().equals(List.of(other))
				&& works.queue().open(WorkJobType.BUILD, other).isPresent(),
				"the order must hold the second house's jobs: " + works.queue().jobs());
		view = view(rig);
		helper.assertTrue(view.orders().size() == 1 && view.orders().getFirst().building() == other
				&& view.orders().getFirst().selected() && view.checklist().planKnown(),
				"the dispatcher must show the second house's order, its plan known: " + view.orders() + " / "
						+ view.checklist());

		BuildingPlan first = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		HallSchematicPayload schematic = HallSchematic.reply(level, rig.core().getBlockPos(), rig.town(),
				Vec3.atCenterOf(at), other);
		helper.assertTrue(schematic.available() && !schematic.cells().isEmpty(),
				"the second house's schematic must show its cells: " + schematic.missing());
		for (HallSchematicPayload.Ghost ghost : schematic.cells()) {
			BlockPos pos = BlockPos.of(ghost.pos());
			helper.assertTrue(!first.contains(pos) && !rig.plan().contains(pos),
					"the schematic of one house shows a cell of another building at " + pos);
		}
		second.release();
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements R2-4 — the drone puts a door's two halves up in one go: no tick finds the lower half without the
	 *     upper one, and the door takes one door item out of the buffer.
	 */
	public static void droneHangsADoorWhole(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = TownHouseScenarios.addIntactHouse(helper, rig, true);
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		int lower = -1;
		for (int i = 0; i < plan.size() && lower < 0; i++) {
			BlockState want = plan.expected(i);
			if (want.getBlock() instanceof DoorBlock
					&& want.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
				lower = i;
			}
		}
		helper.assertTrue(lower >= 0, "control: the house's plan has no door");
		BlockPos lowerPos = plan.pos(lower);
		BlockPos upperPos = lowerPos.above();
		BlockState door = plan.expected(lower);
		level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT, HousePlanItem.of(new RestorationPlan(STYLE,
				HOUSE_1_INTACT)));
		dispatcher.buffer().insert(new ItemStack(door.getBlock().asItem(), 2));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 1).orElseThrow();
		helper.onEachTick(() -> {
			rig.tick();
			boolean lowerUp = level.getBlockState(lowerPos).is(door.getBlock());
			boolean upperUp = level.getBlockState(upperPos).is(door.getBlock());
			if (lowerUp != upperUp) {
				throw helper.assertionException("half a door stands at tick " + helper.getTick() + ": lower "
						+ lowerUp + ", upper " + upperUp);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBlockState(lowerPos).is(door.getBlock())
					&& level.getBlockState(upperPos).is(door.getBlock()), "waiting for the door: "
					+ rig.works().queue().job(job.id()));
			helper.assertTrue(countOf(dispatcher, door) == 1, "the door must take one item, the buffer holds "
					+ countOf(dispatcher, door));
			helper.assertTrue(rig.works().queue().job(job.id()).map(j -> j.status() == WorkJobStatus.DONE)
					.orElse(false), "waiting for the job to close: " + rig.works().queue().job(job.id()));
			hold.release();
			rig.release();
		});
	}

	/**
	 * @implements R4-9 — the charging bed is two cells, as a bed: the house's plan holds its head and its foot, the
	 *     drone lays both in one go with one bed item, and the house's bed is the head cell a worker lies down on.
	 */
	public static void droneLaysAChargingBedWhole(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = TownHouseScenarios.addIntactHouse(helper, rig, true);
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		int foot = -1;
		int head = -1;
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (want.is(ModContent.CHARGING_BED.get())) {
				if (want.getValue(ChargingBedBlock.PART) == BedPart.FOOT) {
					foot = i;
				} else {
					head = i;
				}
			}
		}
		helper.assertTrue(foot >= 0 && head >= 0, "the house's plan must hold the bed's foot and head");
		BlockPos footPos = plan.pos(foot);
		BlockPos headPos = plan.pos(head);
		BlockState footWant = plan.expected(foot);
		BlockState headWant = plan.expected(head);
		helper.assertTrue(ChargingBedBlock.otherHalf(footPos, plan.expected(foot)).equals(headPos),
				"the plan's foot must point at its head");
		helper.assertTrue(TownHousing.bed(level, rig.town(), 1).filter(headPos::equals).isPresent(),
				"the house's bed must be the head cell, got " + TownHousing.bed(level, rig.town(), 1));
		Block bed = ModContent.CHARGING_BED.get();
		level.setBlock(headPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(footPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT, HousePlanItem.of(new RestorationPlan(STYLE,
				HOUSE_1_INTACT)));
		dispatcher.buffer().insert(new ItemStack(bed.asItem(), 2));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 1).orElseThrow();
		helper.onEachTick(() -> {
			rig.tick();
			boolean footUp = level.getBlockState(footPos).is(bed);
			boolean headUp = level.getBlockState(headPos).is(bed);
			if (footUp != headUp) {
				throw helper.assertionException("half a bed stands at tick " + helper.getTick() + ": foot "
						+ footUp + ", head " + headUp);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBlockState(footPos).equals(footWant)
					&& level.getBlockState(headPos).equals(headWant), "waiting for the bed: "
					+ rig.works().queue().job(job.id()));
			helper.assertTrue(countOf(dispatcher, bed.defaultBlockState()) == 1,
					"the bed must take one item, the buffer holds " + countOf(dispatcher, bed.defaultBlockState()));
			hold.release();
			rig.release();
		});
	}

	/**
	 * @implements Q4 — a house plan put into the dispatcher's plan slot is learned and used up only while the town does
	 *     not know it: a plan of a known type stays in the slot, a new one is learned and taken.
	 */
	public static void dispatcherKeepsAKnownHousePlan(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		TownWorks works = rig.works();
		Identifier known = Industrialization.id("ala_town/plains/intact/capsule_house_2");
		Identifier fresh = Industrialization.id("ala_town/plains/intact/capsule_house_3");
		works.learnHousePlan(known.toString());
		helper.assertFalse(works.learnedHousePlans().contains(fresh.toString()),
				"control: the town must not know house plan 3 yet: " + works.learnedHousePlans());
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT, HousePlanItem.of(new RestorationPlan(STYLE, known)));
		for (int i = 0; i < 3; i++) {
			rig.tickDispatcherAndNests();
		}
		helper.assertTrue(dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT).getItem() instanceof HousePlanItem,
				"a house plan the town knows must stay in the plan slot, it holds "
						+ dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT));
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT, HousePlanItem.of(new RestorationPlan(STYLE, fresh)));
		for (int i = 0; i < 3; i++) {
			rig.tickDispatcherAndNests();
		}
		helper.assertTrue(dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT).isEmpty(),
				"a new house plan must be used up, the slot holds "
						+ dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT));
		helper.assertTrue(works.learnedHousePlans().contains(fresh.toString()),
				"the town must know house plan 3 now: " + works.learnedHousePlans());
		rig.release();
		helper.succeed();
	}

	private static int countOf(TownDispatcherBlockEntity dispatcher, BlockState door) {
		return dispatcher.buffer().count(BuiltInRegistries.ITEM.getKey(door.getBlock().asItem()).toString());
	}

	private static DispatcherView view(Rig rig) {
		return DispatcherViews.of(rig.level(), rig.dispatcher(),
				SettlementRegistry.get(rig.level().getServer()).find(rig.town()));
	}

	private static List<String> keys(List<ConstructionRow.Text> lines) {
		return lines.stream().map(ConstructionRow.Text::key).toList();
	}
}
