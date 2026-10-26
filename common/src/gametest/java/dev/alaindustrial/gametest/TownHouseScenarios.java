package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.EngravedLetterPlateBlock;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.TownRoadBlockEntity;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.network.HallSchematicRequestPayload;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.HallSchematic;
import dev.alaindustrial.settlement.works.PlanSheetNews;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHouses;
import dev.alaindustrial.settlement.works.TownHousing;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite of the capsule houses of an ALA town (MOD-862; epic MOD-824, decisions R40, R100, R102, R103, R110):
 * a house that matches its intact template with every key point is fit to live in, the first homeless worker moves in
 * and its digits go on the plaque; breaking the house inlet takes the house off the count and the resident out; the
 * reception capsule refuses a new worker without a free house and serves once there is one. Replayed by both loaders
 * through the roster (ADR-038).
 */
public final class TownHouseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownHouseScenarios::restoredHouseTakesAResidentAndLosesItWithItsInlet,
						"town_house_restored_house_takes_a_resident").ticks(40),
				RosterEntry.of(TownHouseScenarios::roadGapShowsInTheNetworkSchematic,
						"town_house_road_gap_shows_in_network_schematic").ticks(40),
				RosterEntry.of(TownHouseScenarios::capsuleRefusesWithoutHousing,
						"town_house_capsule_refuses_without_housing").ticks(40),
				RosterEntry.of(TownHouseScenarios::clearingTakesRubbleRoundTheHouse,
						"town_house_clearing_takes_rubble_round_the_house").ticks(2400),
				RosterEntry.of(TownHouseScenarios::houseOnASlopeGetsItsFoundation,
						"town_house_on_a_slope_gets_its_foundation").ticks(3600));

		private Roster() {}
	}

	private TownHouseScenarios() {}

	private static final Identifier HOUSE = Industrialization.id("ala_town/plains/capsule_house_1");
	private static final Identifier HOUSE_INTACT = Industrialization.id("ala_town/plains/intact/capsule_house_1");
	/** Where the house stands from the rig's hall: south of it, inside the LV territory, in chunks of its own. */
	static final BlockPos HOUSE_OFFSET = new BlockPos(0, 0, -20);

	/**
	 * @implements MOD-862-SETTLE — an intact capsule house is fit to live in; settling moves the homeless worker in
	 *     and puts the ruin's former resident into the house's history; the plaque then asks a drone for the
	 *     resident's full number, the letter whole (R4-12), and for the letter broken once the house stands empty.
	 * @implements MOD-862-INLET — a broken house inlet takes the house off the count, and the resident out.
	 * @implements MOD-884-INLET-LOG — the broken inlet writes one line to the town hall's log naming the resident
	 *     homeless; the next pass over the house writes none.
	 */
	public static void restoredHouseTakesAResidentAndLosesItWithItsInlet(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = addIntactHouse(helper, rig, false);
		TownWorks works = rig.works();
		helper.assertTrue(!TownHouses.fitToLive(works.buildings().get(1))
				&& works.buildings().get(1).missingKeys().contains(TownPowerLinks.NOT_CONNECTED_KEY),
				"MOD-861: an intact house whose inlet is not wired to the core must not be fit to live in: "
						+ works.buildings().get(1));
		wire(helper, rig, 1);
		helper.assertTrue(TownHouses.fitToLive(works.buildings().get(1)),
				"the intact house must be fit to live in: " + works.buildings().get(1));
		RecordNumber number = RecordNumber.parse("Q-862").orElseThrow();
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		UUID entity = UUID.randomUUID();
		book.put(ShiftWorkerRecord.issued(number, entity, Industrialization.id("test"), false, rig.town()));
		TownHousing.settle(level, rig.town());
		ShiftWorkerRecord settled = book.byEntity(entity).orElseThrow();
		helper.assertTrue(settled.home() == 1, "the homeless worker must move into the house: " + settled.home());
		helper.assertTrue(!works.buildings().get(1).formerResidents().isEmpty(),
				"the ruin's former resident must go into the house's history");
		// What a drone engraves on its visit (R4-12), done here by hand: the letter whole, then the digits.
		List<TownHousing.PlaquePlate> engrave = TownHousing.plaqueToEngrave(level, rig.town(), 1);
		helper.assertTrue(!engrave.isEmpty() && engrave.getFirst().slot() == 0
				&& engrave.getFirst().wanted().is(ModContent.ENGRAVED_LETTER_PLATE.get())
				&& engrave.getFirst().wanted().getValue(EngravedLetterPlateBlock.LETTER)
						== ArchiveRecord.ALPHABET.indexOf('Q')
				&& !engrave.getFirst().wanted().getValue(EngravedLetterPlateBlock.BROKEN),
				"the plaque must ask for the resident's letter Q, whole: " + engrave);
		engrave.forEach(plate -> level.setBlock(plate.pos(), plate.wanted(), Block.UPDATE_CLIENTS));
		helper.assertTrue(TownHousing.plaqueToEngrave(level, rig.town(), 1).isEmpty(),
				"an engraved plaque must ask for nothing more");
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		List<Block> plates = new ArrayList<>();
		BlockPos inlet = null;
		for (int i = 0; i < plan.size(); i++) {
			if (plan.expected(i).is(ModTags.Blocks.TOWN_PLAQUE_PLATES)) {
				plates.add(level.getBlockState(plan.pos(i)).getBlock());
			}
			if (plan.expected(i).is(ModContent.HOUSE_INLET.get())) {
				inlet = plan.pos(i);
			}
		}
		helper.assertTrue(plates.contains(ModContent.ENGRAVED_PLATE_8.get())
				&& plates.contains(ModContent.ENGRAVED_PLATE_6.get())
				&& plates.contains(ModContent.ENGRAVED_PLATE_2.get()), "the plaque must show 862: " + plates);
		helper.assertTrue(inlet != null, "control: the house's plan has no inlet");
		int logBefore = book.log().size();
		level.setBlock(inlet, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		List<ShiftWorkerBook.LogLine> added = List.copyOf(book.log().subList(logBefore, book.log().size()));
		helper.assertTrue(added.size() == 1 && added.getFirst().settlement() == rig.town()
				&& added.getFirst().about(number) && added.getFirst().note()
						.map(note -> TownHousing.JOURNAL_INLET_BROKEN.equals(note.key())).orElse(false),
				"MOD-884: a broken house inlet must write one hall log line naming its resident: " + added);
		Component text = PlanSheetNews.logLineText(added.getFirst()).orElseThrow();
		helper.assertTrue(text.getContents() instanceof TranslatableContents words
						&& words.getArgs().length == 1 && number.toString().equals(words.getArgs()[0]),
				"MOD-884: the line must read with the resident's number: " + text);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		helper.assertTrue(book.log().size() == logBefore + 1,
				"MOD-884: a second pass over the house without its inlet must write nothing more: " + book.log());
		TownHousing.settle(level, rig.town());
		helper.assertTrue(!TownHouses.counted(works, 1), "a house without its inlet must be off the count");
		helper.assertTrue(!book.byEntity(entity).orElseThrow().housed(), "the resident must be homeless again");
		helper.assertTrue(works.buildings().get(1).formerResidents().contains(number.toString()),
				"the resident's number must go into the house's history");
		List<TownHousing.PlaquePlate> empty = TownHousing.plaqueToEngrave(level, rig.town(), 1);
		helper.assertTrue(empty.size() == 1 && empty.getFirst().slot() == 0
				&& empty.getFirst().wanted().getValue(EngravedLetterPlateBlock.BROKEN),
				"an emptied house must ask for its letter broken, the digits staying: " + empty);
		book.remove(entity);
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-884-ROAD-GAP — a road cell cut out of a restored house's way to the hall takes the house off
	 *     the core's network; the core's row names the cell, and the network schematic — and the house's own —
	 *     draws a road ghost marked as the gap there, where the plan, laid once, draws nothing else; with the road
	 *     put back no gap is drawn.
	 */
	public static void roadGapShowsInTheNetworkSchematic(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = addIntactHouse(helper, rig, true);
		TownWorks works = rig.works();
		helper.assertTrue(TownHouses.fitToLive(works.buildings().get(1)) && works.buildings().get(1).everRestored(),
				"control: the wired house must be restored and fit to live in: " + works.buildings().get(1));
		BlockPos core = rig.core().getBlockPos();
		helper.assertTrue(gaps(level, rig, core, HallSchematicRequestPayload.NETWORK).isEmpty(),
				"a linked town must show no gap in its network schematic");
		List<BlockPos> course = TownRoadNetwork.course(level, rig.town(), 1).orElseThrow();
		BlockPos cut = null;
		for (int i = course.size() / 2; i < course.size() && cut == null; i++) {
			if (level.getBlockState(course.get(i)).is(ModContent.TOWN_ROAD.get())) {
				cut = course.get(i);
			}
		}
		helper.assertTrue(cut != null, "control: the house's way to the hall holds no road block: " + course);
		level.setBlock(cut, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		helper.assertTrue(works.buildings().get(1).missingKeys().contains(TownPowerLinks.NOT_CONNECTED_KEY),
				"control: a cut road must take the house off the core's network: " + works.buildings().get(1));
		helper.assertTrue(ConstructionRows.gapCell(level, rig.town(), works.buildings().get(1), 1)
				.equals(Optional.of(cut)), "the house's gap must be the cut cell " + cut);
		List<HallSchematicPayload.Ghost> network = gaps(level, rig, core, HallSchematicRequestPayload.NETWORK);
		helper.assertTrue(network.size() == 1 && network.getFirst().pos() == cut.asLong()
				&& network.getFirst().state().is(ModContent.TOWN_ROAD.get()),
				"the network schematic must mark the cut cell " + cut + " as its one gap: " + network);
		List<HallSchematicPayload.Ghost> house = gaps(level, rig, core, 1);
		helper.assertTrue(house.size() == 1 && house.getFirst().pos() == cut.asLong(),
				"the house's schematic must mark the cut cell " + cut + " too: " + house);
		level.setBlock(cut, ModContent.TOWN_ROAD.get().defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(cut) instanceof TownRoadBlockEntity segment) {
			segment.ensureRegistered();
		}
		TownWorksScanner.measureNow(level, rig.town(), 1);
		helper.assertTrue(gaps(level, rig, core, HallSchematicRequestPayload.NETWORK).isEmpty(),
				"with the road put back the network schematic must show no gap");
		hold.release();
		rig.release();
		helper.succeed();
	}

	/** The ghosts marked as a gap in the reply for {@code building} of the rig's town, seen from the core. */
	private static List<HallSchematicPayload.Ghost> gaps(ServerLevel level, Rig rig, BlockPos core, int building) {
		return HallSchematic.reply(level, core, rig.town(), Vec3.atCenterOf(core), building).cells().stream()
				.filter(HallSchematicPayload.Ghost::gap).toList();
	}

	/**
	 * @implements MOD-862-NO-HOUSING — with everything else in place the hall's reception capsule says «no housing»
	 *     while the town has no free house fit to live in, and is ready once it has one.
	 */
	public static void capsuleRefusesWithoutHousing(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ReceptionCapsuleBlockEntity capsule = null;
		for (int i = 0; i < rig.plan().size() && capsule == null; i++) {
			if (level.getBlockEntity(rig.plan().pos(i)) instanceof ReceptionCapsuleBlockEntity found) {
				capsule = found;
			}
		}
		helper.assertTrue(capsule != null, "control: the intact hall has no reception capsule");
		stock(capsule);
		tickCapsule(rig, capsule);
		helper.assertTrue(capsule.verdict() == ReceptionCapsuleRules.Verdict.NO_HOUSING,
				"without a house the capsule must say «no housing», it says " + capsule.verdict());
		ChunkHold hold = addIntactHouse(helper, rig, true);
		stock(capsule);
		tickCapsule(rig, capsule);
		helper.assertTrue(capsule.verdict() == ReceptionCapsuleRules.Verdict.READY,
				"with a free house the capsule must be ready, it says " + capsule.verdict());
		hold.release();
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-858-RING-RUBBLE — a clearing of a house frees the ring its definition names (three blocks)
	 *     of the rubble a ruined town scatters (owner's round 1 of 0.1.219): a chain lying three blocks off the
	 *     wall and a spruce slab two blocks off go; a player's oak planks in the same ring stay.
	 */
	public static void clearingTakesRubbleRoundTheHouse(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = addIntactHouse(helper, rig, false);
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		BoundingBox box = plan.box().orElseThrow();
		int y = plan.groundLevel();
		BlockPos chain = new BlockPos(box.minX() - 3, y, box.minZ() + 2);
		BlockPos slab = new BlockPos(box.minX() - 2, y, box.minZ() + 3);
		BlockPos planks = new BlockPos(box.minX() - 3, y, box.minZ() + 4);
		Set<BlockPos> ring = new HashSet<>();
		for (int r = 0; r < plan.ringSize(); r++) {
			ring.add(plan.ringPos(r));
		}
		helper.assertTrue(ring.contains(chain) && ring.contains(slab) && ring.contains(planks),
				"control: the house's ring must reach three blocks off its wall");
		for (BlockPos pos : List.of(chain, slab, planks)) {
			level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		level.setBlock(chain, Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(slab, Blocks.SPRUCE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT,
				RestorationPlanItem.of(new RestorationPlan(Industrialization.id("plains"), HOUSE_INTACT)));
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 1).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the clearing is " + now + ", nest "
					+ rig.nest(0).status());
			helper.assertTrue(level.getBlockState(chain).isAir() && level.getBlockState(slab).isAir(),
					"the chain and the slab must go: " + level.getBlockState(chain) + ", " + level.getBlockState(slab));
			helper.assertTrue(level.getBlockState(planks).is(Blocks.OAK_PLANKS), "the player's planks must stay");
			hold.release();
			rig.release();
		});
	}

	/**
	 * @implements MOD-824-R3-5-FOUNDATION — a house's building job fills the pit under its floor from the dispatcher's
	 *     buffer before its blocks (owner's round 3 of 0.1.219, R3-5): on a slope the row of the house over the low
	 *     ground gets earth up to its floor; a player's block in the pit and the hole under it stay, and so does a
	 *     cave under solid ground.
	 */
	public static void houseOnASlopeGetsItsFoundation(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		ServerLevel level = rig.level();
		ChunkHold hold = addIntactHouse(helper, rig, true);
		BuildingPlan plan = TownWorksScanner.plan(level, rig.town(), 1).orElseThrow();
		BoundingBox box = plan.box().orElseThrow();
		int floor = box.minY();
		BlockState stone = Blocks.STONE.defaultBlockState();
		List<BlockPos> pit = new ArrayList<>();
		List<BlockPos> cave = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				if (z > box.minZ()) { // the row on the side away from the door is open to the air
					level.setBlock(new BlockPos(x, floor - 1, z), stone, Block.UPDATE_CLIENTS);
					level.setBlock(new BlockPos(x, floor - 3, z), stone, Block.UPDATE_CLIENTS);
					cave.add(new BlockPos(x, floor - 2, z)); // a cave under the high ground is no pit
				} else {
					level.setBlock(new BlockPos(x, floor - 3, z), stone, Block.UPDATE_CLIENTS);
					pit.add(new BlockPos(x, floor - 1, z));
					pit.add(new BlockPos(x, floor - 2, z));
				}
			}
		}
		BlockPos player = new BlockPos(box.maxX(), floor - 1, box.minZ());
		level.setBlock(player, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		pit.remove(player);
		pit.remove(player.below());
		cave.add(player.below());
		cave.add(new BlockPos(box.minX(), floor - 4, box.minZ())); // under the pit's bottom
		helper.assertTrue(plan.fillSize() > 0, "control: a house must have a foundation to fill");
		rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT,
				RestorationPlanItem.of(new RestorationPlan(Industrialization.id("plains"), HOUSE_INTACT)));
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.DIRT, 64));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 1).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the building job is " + now + ", nest "
					+ rig.nest(0).status());
			for (BlockPos pos : pit) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT), "the pit under the floor must be filled at "
						+ pos + ": " + level.getBlockState(pos));
			}
			helper.assertTrue(level.getBlockState(player).is(Blocks.OAK_PLANKS), "the player's planks must stay");
			for (BlockPos pos : cave) {
				helper.assertTrue(level.getBlockState(pos).isAir(), "no earth under solid ground at " + pos);
			}
			hold.release();
			rig.release();
		});
	}

	/**
	 * Lays the town road of building {@code index} (MOD-861) the way the drone would — on ground built between the two
	 * inlets first, the test world having none of its own there — every segment joined to the line at once, and
	 * measures the building again: its inlet is then wired to the core through the hall's inlet.
	 */
	private static void wire(GameTestHelper helper, Rig rig, int index) {
		ServerLevel level = rig.level();
		BlockPos from = TownPowerLinks.hallInlet(level, rig.town()).orElseThrow().front();
		BlockPos to = TownWorksScanner.plan(level, rig.town(), index).flatMap(TownPowerLinks::inletOf).orElseThrow()
				.front();
		TownRoadCourseScenarios.ground(level, rig.works(), from, to, false);
		rig.works().retryRoadsNow();
		RoadWork.Course course = RoadWork.course(level, rig.town(), index);
		helper.assertTrue(course.problem().isEmpty(), "control: no road course from the hall's inlet to the house's: "
				+ course.problem());
		for (RoadWork.Slot slot : RoadWork.slots(course.cells())) {
			if (RoadWork.want(level, slot) != RoadWork.Want.WORK) {
				continue;
			}
			BlockState put = switch (slot.kind()) {
				case CUT -> Blocks.AIR.defaultBlockState();
				case FILL -> Blocks.DIRT.defaultBlockState();
				case ROAD -> ModContent.TOWN_ROAD.get().defaultBlockState();
			};
			level.setBlock(slot.pos(), put, Block.UPDATE_ALL);
			if (level.getBlockEntity(slot.pos()) instanceof TownRoadBlockEntity segment) {
				segment.ensureRegistered();
			}
		}
		TownWorksScanner.measureNow(level, rig.town(), index);
	}

	/**
	 * Stands the intact plains house 1 south of the rig's hall and makes it the town's second building, measured —
	 * wired to the core by its road when {@code wired}.
	 */
	static ChunkHold addIntactHouse(GameTestHelper helper, Rig rig, boolean wired) {
		ServerLevel level = rig.level();
		BlockPos at = rig.origin().offset(HOUSE_OFFSET);
		ChunkHold hold = ChunkHold.forTest(helper, level, at);
		LineStructures.templates(level).getOrCreate(HOUSE_INTACT).placeInWorld(level, at, at,
				new StructurePlaceSettings().setRotation(Rotation.NONE)
						.addProcessor(JigsawReplacementProcessor.INSTANCE)
						.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK),
				RandomSource.create(862L), Block.UPDATE_CLIENTS);
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		TownBuilding hall = rig.works().buildings().getFirst();
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(
				TownBuilding.found(hall.template(), hall.intact(), hall.name(), hall.origin(), hall.rotation()),
				TownBuilding.found(HOUSE, Optional.of(HOUSE_INTACT), "settlement.alaindustrial.building.capsule_house",
						at, Rotation.NONE)));
		TownWorksScanner.measureNow(level, rig.town(), 0);
		TownWorksScanner.measureNow(level, rig.town(), 1);
		if (wired) {
			wire(helper, rig, 1);
		}
		return hold;
	}

	/** The capsule's buffer full and one worker's worth of every material in its slots. */
	private static void stock(ReceptionCapsuleBlockEntity capsule) {
		capsule.getEnergyStorage().setAmountUntracked(capsule.getEnergyStorage().getCapacity());
		for (Material material : Material.values()) {
			capsule.setItem(ReceptionCapsuleBlockEntity.slotOf(material),
					new ItemStack(ReceptionCapsuleBlockEntity.itemOf(material), Math.max(1, material.perWorker())));
		}
		capsule.wake();
	}

	private static void tickCapsule(Rig rig, ReceptionCapsuleBlockEntity capsule) {
		rig.tick();
		capsule.serverTick(rig.level(), capsule.getBlockPos(), rig.level().getBlockState(capsule.getBlockPos()));
	}
}
