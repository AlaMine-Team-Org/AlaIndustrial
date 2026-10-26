package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.ConstructionDroneWork;
import dev.alaindustrial.settlement.drone.DispatcherBuffer;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.Profession;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.SettlementWorks;
import dev.alaindustrial.settlement.works.Storekeeping;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.works.TownStorage;
import dev.alaindustrial.settlement.works.TownWorkplaces;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * L2 suite of the town warehouse and its storekeeper (MOD-888; epic MOD-824, decisions R150, R151, R152, R154,
 * R155): the warehouse is ordered only once the town knows its plan (MOD-892), and one an older save had work on
 * keeps its plan; an order on the warehouse ends with it restored; a restored warehouse
 * takes the oldest worker without work as its storekeeper, and a worker taken off work gives the place to the next
 * one; the storekeeper carries a missing material from a shelf to the dispatcher's buffer and the spoil back, while
 * without one nothing moves. Replayed by both loaders through the roster (ADR-038).
 */
public final class TownWarehouseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownWarehouseScenarios::anOrderRestoresTheWarehouse,
						"town_warehouse_order_restores_the_warehouse").ticks(3600),
				RosterEntry.of(TownWarehouseScenarios::theOldestWorkerBecomesTheStorekeeper,
						"town_warehouse_oldest_worker_becomes_storekeeper").ticks(40),
				RosterEntry.of(TownWarehouseScenarios::theStorekeeperCarriesWhatTheOrderLacks,
						"town_warehouse_storekeeper_carries_what_the_order_lacks").ticks(200),
				RosterEntry.of(TownWarehouseScenarios::theWarehouseWaitsForItsPlan,
						"town_warehouse_waits_for_its_plan").ticks(40),
				RosterEntry.of(TownWarehouseScenarios::anOrderFromAnOlderSaveKeepsItsPlan,
						"town_warehouse_order_from_an_older_save_keeps_its_plan").ticks(40));

		private Roster() {}
	}

	private TownWarehouseScenarios() {}

	private static final Identifier WAREHOUSE = Industrialization.id("ala_town/plains/warehouse");
	private static final Identifier WAREHOUSE_INTACT = Industrialization.id("ala_town/plains/intact/warehouse");
	private static final String NAME = "settlement.alaindustrial.building.warehouse";
	/** Where the warehouse stands from the rig's hall: south of it, inside the LV territory, in chunks of its own. */
	private static final BlockPos OFFSET = new BlockPos(0, 0, -20);

	/**
	 * @implements MOD-888-ORDER — a warehouse whose shelves are gone is no restored building; with the hall restored
	 *     and the warehouse plan learned (MOD-892), its order is placed, a drone builds it from the buffer and the
	 *     next pass finds it restored, every key point in place.
	 */
	public static void anOrderRestoresTheWarehouse(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = addWarehouse(helper, rig, false);
		TownWorks works = rig.works();
		TownBuilding before = works.buildings().get(1);
		helper.assertTrue(!before.everRestored() && before.state() == BuildingState.RUIN,
				"control: the warehouse without its shelves must be a ruin: " + before);
		works.learnHousePlan(WAREHOUSE_INTACT.toString()); // MOD-892: the plan is the restorer's to bring
		helper.assertTrue(ConstructionDroneWork.planKnown(works, 1, Optional.empty()),
				"control: with its plan learned the drones may build the warehouse");
		BuildMaterials.forBuilding(level, rig.town(), 1).flatMap(staged -> staged.progress(level)).orElseThrow()
				.needed().forEach(need -> stock(rig.dispatcher().buffer(), need));
		Settlement town = SettlementRegistry.get(level.getServer()).find(rig.town()).orElseThrow();
		Optional<String> refused = TownOrders.place(level, town, 1, "test");
		helper.assertTrue(refused.isEmpty(), "the order on the warehouse must be placed: " + refused);
		WorkJob job = works.queue().open(WorkJobType.BUILD, 1)
				.orElseThrow(() -> helper.assertionException("the order raised no building job"));
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the building job is " + now + ", nest "
					+ rig.nest(0).status());
			TownWorksScanner.measureNow(level, rig.town(), 1);
			TownBuilding after = rig.works().buildings().get(1);
			helper.assertTrue(after.everRestored() && after.state() == BuildingState.INTACT
					&& after.missingKeys().isEmpty(), "the warehouse must stand restored: " + after);
			hold.release();
			rig.release();
		});
	}

	/**
	 * @implements MOD-888-ASSIGN — a restored warehouse takes the oldest living worker without work by number as its
	 *     storekeeper and the chronicle says so once; taken off work by the restorer, the worker gives the place to
	 *     the next one by number and is not given it back by the town; sent back to work it waits for a free place.
	 */
	public static void theOldestWorkerBecomesTheStorekeeper(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = addWarehouse(helper, rig, true);
		helper.assertTrue(rig.works().buildings().get(1).everRestored(),
				"control: the intact warehouse must count as restored: " + rig.works().buildings().get(1));
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		List<UUID> bodies = new ArrayList<>();
		for (String number : List.of("Q-883", "Q-881", "Q-882")) {
			UUID entity = UUID.randomUUID();
			bodies.add(entity);
			book.put(ShiftWorkerRecord.issued(RecordNumber.parse(number).orElseThrow(), entity,
					Industrialization.id("test"), false, rig.town()));
		}
		int logBefore = book.log().size();
		TownWorkplaces.assign(level, rig.town());
		ShiftWorkerRecord oldest = record(book, "Q-881");
		helper.assertTrue(oldest.employed() && oldest.profession() == Profession.STOREKEEPER
				&& oldest.workplace() == 1, "the oldest worker must become the storekeeper: " + oldest);
		helper.assertTrue(!record(book, "Q-882").employed() && !record(book, "Q-883").employed(),
				"a warehouse takes one worker");
		long lines = book.log().subList(logBefore, book.log().size()).stream()
				.filter(line -> line.note().map(note -> note.key().equals(TownWorkplaces.CHRONICLE_BECAME
						+ "storekeeper")).orElse(false) && line.about(oldest.number())).count();
		helper.assertTrue(lines == 1, "the chronicle must say once that Q-881 became the storekeeper: " + book.log());
		TownWorkplaces.assign(level, rig.town());
		helper.assertTrue(book.log().size() == logBefore + 1, "a second pass must write nothing");

		helper.assertTrue(TownWorkplaces.takeOffWork(level, oldest), "the restorer must take Q-881 off work");
		ShiftWorkerRecord off = record(book, "Q-881");
		helper.assertTrue(!off.employed() && off.workplace() == ShiftWorkerRecord.OFF_WORK,
				"Q-881 must be off work: " + off);
		helper.assertTrue(record(book, "Q-882").employed(), "the place must go to the next worker, Q-882");
		TownWorkplaces.assign(level, rig.town());
		helper.assertTrue(record(book, "Q-881").workplace() == ShiftWorkerRecord.OFF_WORK,
				"the town must not give the place back to a worker taken off work");
		helper.assertTrue(TownWorkplaces.backToWork(level, record(book, "Q-881")), "back to work");
		ShiftWorkerRecord back = record(book, "Q-881");
		helper.assertTrue(!back.employed() && back.workplace() == ShiftWorkerRecord.NO_WORKPLACE,
				"with no free place Q-881 must wait without work: " + back);
		bodies.forEach(book::remove);
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-888-CARRY — the hall waits for one brick and a shelf of the restored warehouse holds ten: the
	 *     town ticking on its own moves nothing (no storekeeper, the buffer stays as the restorer left it, R155); the
	 *     storekeeper's trip takes the brick from the shelf to the buffer; with the buffer filling with dirt no order
	 *     wants, its next trip carries the dirt into the storage.
	 */
	public static void theStorekeeperCarriesWhatTheOrderLacks(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = addWarehouse(helper, rig, true);
		List<TownStorage.Box> boxes = TownStorage.boxes(level, rig.town());
		helper.assertTrue(!boxes.isEmpty() && boxes.getFirst().building() == 1,
				"control: the restored warehouse's shelves must be the town's storage first: " + boxes);
		TownStorage.Box shelf = boxes.getFirst();
		helper.assertTrue(level.getBlockState(shelf.pos()).is(ModContent.WAREHOUSE_SHELF.get()),
				"control: the first box must be a shelf: " + level.getBlockState(shelf.pos()));
		TownStorage.insert(shelf.container(), new ItemStack(Blocks.BRICKS, 10));
		TownDroneScenarios.takeOutAWallBrick(helper, rig);
		Settlement town = SettlementRegistry.get(level.getServer()).find(rig.town()).orElseThrow();
		TownOrders.place(level, town, 0, "test");
		DispatcherBuffer buffer = rig.dispatcher().buffer();
		for (int i = 0; i < 60; i++) {
			rig.tick();
		}
		helper.assertTrue(buffer.count("minecraft:bricks") == 0 && bricks(shelf) == 10,
				"without a storekeeper nothing may move: buffer " + buffer.count("minecraft:bricks") + ", shelf "
						+ bricks(shelf));
		List<BuildStageRules.Need> wanted = Storekeeping.wanted(level, rig.town(), buffer);
		helper.assertTrue(wanted.contains(new BuildStageRules.Need("minecraft:bricks", 1)),
				"control: the hall must lack one brick: " + wanted);

		Storekeeping.Trip fetch = Storekeeping.next(level, rig.town())
				.orElseThrow(() -> helper.assertionException("the storekeeper found no trip"));
		helper.assertTrue(fetch.kind() == Storekeeping.Kind.FETCH && fetch.from().equals(shelf.pos())
				&& fetch.to().equals(rig.dispatcherAt())
				&& fetch.batch().equals(List.of(new BuildStageRules.Need("minecraft:bricks", 1))),
				"the trip must fetch one brick from the shelf to the dispatcher: " + fetch);
		helper.assertTrue(bricks(shelf) == 10, "a trip picked up is a promise: the shelf still holds ten");
		helper.assertTrue(Storekeeping.deliver(level, rig.town(), fetch) == 1, "one brick must move");
		helper.assertTrue(buffer.count("minecraft:bricks") == 1 && bricks(shelf) == 9,
				"the brick must be in the buffer and out of the shelf: buffer " + buffer.count("minecraft:bricks")
						+ ", shelf " + bricks(shelf));

		while (Storekeeping.freeSlots(buffer) * 4 > buffer.size()) {
			buffer.insert(new ItemStack(Blocks.DIRT, 64));
		}
		int dirt = buffer.count("minecraft:dirt");
		Storekeeping.Trip haul = Storekeeping.next(level, rig.town())
				.orElseThrow(() -> helper.assertionException("a filling buffer must give a haul"));
		helper.assertTrue(haul.kind() == Storekeeping.Kind.HAUL && haul.from().equals(rig.dispatcherAt())
				&& haul.batch().stream().allMatch(need -> need.item().equals("minecraft:dirt")),
				"the haul must carry the dirt no order wants: " + haul);
		int moved = Storekeeping.deliver(level, rig.town(), haul);
		helper.assertTrue(moved > 0 && buffer.count("minecraft:dirt") == dirt - moved
				&& buffer.count("minecraft:bricks") == 1,
				"the dirt must leave the buffer and the brick stay: moved " + moved);
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-892-ORDER-GATE — the hall restored, a warehouse whose plan the town does not know cannot be
	 *     ordered: the order is refused with «no plan», its card names the warehouse plan, and the drones would not
	 *     take it; the warehouse plan put into the dispatcher's plan slot is learned and used up, and then the order
	 *     is free and the plans section lists the warehouse plan as learned.
	 */
	public static void theWarehouseWaitsForItsPlan(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = addWarehouse(helper, rig, false);
		TownWorks works = rig.works();
		Settlement town = SettlementRegistry.get(level.getServer()).find(rig.town()).orElseThrow();
		helper.assertTrue(works.buildings().getFirst().everRestored(), "control: the rig's hall must stand restored");
		helper.assertTrue(TownOrders.blocker(town, works, 1).equals(Optional.of(TownOrders.NO_PLAN)),
				"without its plan the warehouse must wait for it: " + TownOrders.blocker(town, works, 1));
		helper.assertTrue(TownOrders.place(level, town, 1, "test").equals(Optional.of(TownOrders.NO_PLAN))
				&& !TownOrders.ordered(works, 1), "the order on the warehouse must be refused without its plan");
		helper.assertFalse(ConstructionDroneWork.planKnown(works, 1, Optional.empty()),
				"the drones must not build a warehouse whose plan the town lacks");
		ConstructionRows.Context context = new ConstructionRows.Context(level, town, works, true);
		ConstructionRow card = ConstructionRows.buildingRow(context, 1);
		helper.assertTrue(card.detail().map(text -> text.key().equals(ConstructionRows.NO_WAREHOUSE_PLAN))
				.orElse(false), "the warehouse's card must name its missing plan: " + card);
		helper.assertTrue(planStatus(context).equals(Optional.of(ConstructionRows.PLAN_UNKNOWN)),
				"the plans section must list the warehouse plan as not found: " + planStatus(context));

		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		dispatcher.setItem(TownDispatcherBlockEntity.PLAN_SLOT,
				HousePlanItem.of(new RestorationPlan(Industrialization.id("plains"), WAREHOUSE_INTACT)));
		for (int i = 0; i < 3; i++) {
			rig.tickDispatcherAndNests();
		}
		helper.assertTrue(dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT).isEmpty(),
				"the warehouse plan must be used up, the slot holds "
						+ dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT));
		helper.assertTrue(works.learnedHousePlans().contains(WAREHOUSE_INTACT.toString()),
				"the town must know the warehouse plan now: " + works.learnedHousePlans());
		helper.assertTrue(TownOrders.blocker(town, works, 1).isEmpty()
				&& ConstructionDroneWork.planKnown(works, 1, Optional.empty()),
				"with its plan learned the warehouse may be ordered and built");
		helper.assertTrue(planStatus(context).equals(Optional.of(ConstructionRows.PLAN_KNOWN)),
				"the plans section must list the warehouse plan as learned: " + planStatus(context));
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-892-OLD-SAVE — a warehouse an older build let a job on without its plan (MOD-888) keeps going
	 *     after a save and a load: the town is taught its plan at load, so the drones still build it; a warehouse
	 *     with no job and never restored is taught nothing.
	 */
	public static void anOrderFromAnOlderSaveKeepsItsPlan(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = addWarehouse(helper, rig, false);
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		TownWorks untouched = roundTrip(ops, SettlementRegistry.get(level.getServer()).works()).town(rig.town());
		helper.assertFalse(untouched.learnedHousePlans().contains(WAREHOUSE_INTACT.toString()),
				"a warehouse nobody worked on must not be taught its plan: " + untouched.learnedHousePlans());
		TownWorks works = rig.works();
		works.queue().raise(WorkJobType.BUILD, 1); // as MOD-888 left it: an order on the warehouse with no plan
		helper.assertFalse(ConstructionDroneWork.planKnown(works, 1, Optional.empty()),
				"control: before a load the order has no plan");
		TownWorks loaded = roundTrip(ops, SettlementRegistry.get(level.getServer()).works()).town(rig.town());
		helper.assertTrue(loaded.learnedHousePlans().contains(WAREHOUSE_INTACT.toString()),
				"a loaded warehouse with work on it must know its plan: " + loaded.learnedHousePlans());
		helper.assertTrue(ConstructionDroneWork.planKnown(loaded, 1, Optional.empty()),
				"the loaded order on the warehouse must go on");
		hold.release();
		rig.release();
		helper.succeed();
	}

	private static SettlementWorks roundTrip(RegistryOps<Tag> ops, SettlementWorks works) {
		return SettlementWorks.CODEC.parse(ops, SettlementWorks.CODEC.encodeStart(ops, works).getOrThrow())
				.getOrThrow();
	}

	/** The status key of the warehouse plan's row in the plans section, if it has one. */
	private static Optional<String> planStatus(ConstructionRows.Context context) {
		return ConstructionRows.collect(context).stream()
				.filter(row -> row.section().equals(ConstructionRows.SECTION_HOUSE_PLANS)
						&& row.text().key().equals(HousePlanItem.WAREHOUSE_NAME))
				.findFirst().flatMap(ConstructionRow::status).map(ConstructionRow.Text::key);
	}

	private static int bricks(TownStorage.Box box) {
		return TownStorage.contents(box.container()).getOrDefault("minecraft:bricks", 0);
	}

	private static ShiftWorkerRecord record(ShiftWorkerBook book, String number) {
		return book.byNumber(RecordNumber.parse(number).orElseThrow()).orElseThrow();
	}

	/** One stack's worth of {@code need} in the buffer, enough for a few shelves and blocks. */
	private static void stock(DispatcherBuffer buffer, BuildStageRules.Need need) {
		BuiltInRegistries.ITEM.getOptional(Identifier.parse(need.item()))
				.ifPresent(item -> buffer.insert(new ItemStack(item, need.count())));
	}

	/**
	 * Stands the intact plains warehouse south of the rig's hall and makes it the town's second building, measured;
	 * without its shelves when {@code whole} is false — a building to restore.
	 */
	static ChunkHold addWarehouse(GameTestHelper helper, Rig rig, boolean whole) {
		ServerLevel level = rig.level();
		BlockPos at = rig.origin().offset(OFFSET);
		ChunkHold hold = ChunkHold.forTest(helper, level, at);
		LineStructures.templates(level).getOrCreate(WAREHOUSE_INTACT).placeInWorld(level, at, at,
				new StructurePlaceSettings().setRotation(Rotation.NONE)
						.addProcessor(JigsawReplacementProcessor.INSTANCE)
						.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK),
				RandomSource.create(888L), Block.UPDATE_CLIENTS);
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		TownBuilding hall = rig.works().buildings().getFirst();
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(
				TownBuilding.found(hall.template(), hall.intact(), hall.name(), hall.origin(), hall.rotation()),
				TownBuilding.found(WAREHOUSE, Optional.of(WAREHOUSE_INTACT), NAME, at, Rotation.NONE)));
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1)
				.orElseThrow(() -> helper.assertionException("the warehouse has no plan"));
		for (int i = 0; i < plan.size(); i++) {
			level.getChunkAt(plan.pos(i)); // a ticket loads in the background; the test reads the cells now
		}
		if (!whole) {
			for (int i = 0; i < plan.size(); i++) {
				// The shelves of the door's half: the far corner (x = 10) the drone's route search does not reach from
				// the hall's roof at this distance (NO_ROUTE, MOD-888 task.md, «open»), so the case leaves it standing.
				if (plan.expected(i).is(ModContent.WAREHOUSE_SHELF.get()) && plan.pos(i).getX() - at.getX() < 8) {
					level.setBlock(plan.pos(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		TownWorksScanner.measureNow(level, rig.town(), 0);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		return hold;
	}
}
