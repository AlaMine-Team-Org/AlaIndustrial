package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.TownDispatcherBlock;
import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.item.misc.TownHallPlanSheetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherStatus;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.DroneNestRules.Status;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * L2 suite of the town dispatcher, the drone nests and the tier-1 builder drone (MOD-858; epic MOD-824, R85, R91,
 * R93–R95), replayed by both loaders through the roster (ADR-038). Every case stands an intact plains town hall far
 * from the other rigs, registers its town, claims it and keeps its core lit, then breaks a few cells on purpose:
 * cobwebs in the hall's debris cells for a clearing, a missing brick for a building.
 *
 * <p>The cases drive the core, the dispatcher and the nests themselves, one {@code serverTick} each per game tick
 * ({@link Rig#tick}): the hall's chunks are kept loaded but not simulated ({@link #keepLoaded}) — far from the
 * test's own structure, a forced chunk would start ticking only once its neighbours generated, hundreds of ticks
 * in, and then tick everything a second time.
 *
 * <p><b>What a rig costs, and why rigs stand packed.</b> A loading ticket keeps its chunk FULL and, through the
 * ticket level falling off one per chunk, every chunk eleven around it generated at a lower status and held in
 * memory: about 600 chunks for one hall's two by two. Rigs 300 blocks apart each paid that whole square, and a
 * batch of them took the Fabric game-test server past its heap (CI run 39691647588, MOD-858 research). So rigs
 * stand on a grid {@link #RIG_SPACING} apart, where those squares overlap, each attempt on a cell no other
 * attempt has used (a hall left from an earlier case answers its own core, already claimed), and the tickets
 * go away when the test ends however it ends ({@link ChunkHold}).
 */
public final class TownDroneScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDroneScenarios::dispatcherAndNestsFollowTheRules,
						"town_drone_dispatcher_and_nests_follow_the_rules").ticks(60),
				RosterEntry.of(TownDroneScenarios::clearingSparesProtectedBlocks,
						"town_drone_clearing_spares_protected_blocks").ticks(1600),
				RosterEntry.of(TownDroneScenarios::obstructionBlocksTheJob, "town_drone_obstruction_blocks_the_job")
						.ticks(200),
				RosterEntry.of(TownDroneScenarios::creatureInCellIsSkippedAndRetried,
						"town_drone_creature_in_cell_is_skipped_and_retried").ticks(1200),
				RosterEntry.of(TownDroneScenarios::unloadedChunkPausesTheJob,
						"town_drone_unloaded_chunk_pauses_the_job").ticks(200),
				RosterEntry.of(TownDroneScenarios::twoDronesNeverShareACell, "town_drone_two_drones_never_share_a_cell")
						.ticks(200),
				RosterEntry.of(TownDroneScenarios::saveAndLoadMidFlightNeitherDupesNorLoses,
						"town_drone_save_and_load_mid_flight").ticks(1200),
				RosterEntry.of(TownDroneScenarios::darkCoreLetsTheDroneFinishOnBattery,
						"town_drone_dark_core_finishes_on_battery").ticks(800),
				RosterEntry.of(TownDroneScenarios::onlyTheOwnerControlsTheDispatcher,
						"town_drone_only_the_owner_controls_the_dispatcher").ticks(40));

		private Roster() {}
	}

	private TownDroneScenarios() {}

	private static final Identifier HALL = Industrialization.id("ala_town/plains/town_hall");
	private static final Identifier HALL_INTACT = Industrialization.id("ala_town/plains/intact/town_hall");
	private static final Identifier STYLE = Industrialization.id("plains");
	/** Inside the intact plains hall, on its floor: air where the dispatcher stands (docs/structures/ala_town.md). */
	private static final BlockPos DISPATCHER_LOCAL = new BlockPos(6, 1, 7);
	private static final UUID OWNER = UUID.fromString("00000000-0000-4858-8000-000000000858");

	/** One case's town: the hall, its core, its dispatcher and its nests. */
	record Rig(ServerLevel level, int town, BuildingPlan plan, SettlementCoreBlockEntity core,
			BlockPos dispatcherAt, List<BlockPos> nests, List<BlockPos> carpets, BlockPos origin, ChunkHold hold) {

		/** The dispatcher as the level holds it now: a reload in the case puts a new block entity there. */
		TownDispatcherBlockEntity dispatcher() {
			return (TownDispatcherBlockEntity) level.getBlockEntity(dispatcherAt);
		}

		DroneNestBlockEntity nest(int i) {
			return (DroneNestBlockEntity) level.getBlockEntity(nests.get(i));
		}

		TownWorks works() {
			return SettlementRegistry.get(level.getServer()).works().town(town);
		}

		/**
		 * One game tick of the town: the core kept full and lit, the dispatcher, every nest. The hall's chunks are
		 * kept loaded but not simulated ({@link #keepLoaded}), so this is the only tick they get.
		 */
		void tick() {
			core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
			core.serverTick(level, core.getBlockPos(), level.getBlockState(core.getBlockPos()));
			tickDispatcherAndNests();
		}

		/** One tick of the dispatcher and every nest, the core left alone. */
		void tickDispatcherAndNests() {
			dispatcher().serverTick(level, dispatcherAt, level.getBlockState(dispatcherAt));
			for (BlockPos pos : nests) {
				if (level.getBlockEntity(pos) instanceof DroneNestBlockEntity nest) {
					nest.serverTick(level, pos, level.getBlockState(pos));
				}
			}
		}

		/** The debris cells of the hall's plan: air in the intact hall where the ruin had a block. */
		List<Integer> debris() {
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < plan.size(); i++) {
				if (plan.debris(i) && !plan.onLabShaft(i) && level.getBlockState(plan.pos(i)).isAir()) {
					out.add(i);
				}
			}
			return out;
		}

		/** Lets the hall's chunks go: the case is over. The test's end lets them go too, if this is never reached. */
		void release() {
			hold.release();
		}

		Optional<WorkJob> job(WorkJobType type) {
			return works().queue().jobs().stream().filter(job -> job.type() == type).reduce((a, b) -> b);
		}
	}

	/**
	 * Stands an intact hall on the next free cell of the rig grid ({@link #nextOrigin}), its town claimed and lit, a
	 * dispatcher holding the hall's restoration plan (no plan, no drone work — owner's round 2) and {@code nests}
	 * nests.
	 */
	static Rig rig(GameTestHelper helper, int nests) {
		return rig(helper, nests, OWNER, true);
	}

	static Rig rig(GameTestHelper helper, int nests, UUID owner, boolean withPlan) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = nextOrigin();
		// Kept loaded for the case: the hall lies far from the test's own structure, whose chunks alone the lane
		// force-loads, and a drone flies for hundreds of ticks. Held from here, so a setup that throws lets go too.
		ChunkHold hold = ChunkHold.forTest(helper, level, origin);
		StructureTemplate template = LineStructures.templates(level).getOrCreate(HALL_INTACT);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE)
				.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(858L), Block.UPDATE_CLIENTS);
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		BlockPos corePos = origin.offset(6, 1, 5);
		int town = registry.place(level.dimension(), corePos, 0).settlementId();
		registry.works().forget(town);
		registry.works().town(town).learnBuildings(List.of(TownBuilding.found(HALL, Optional.of(HALL_INTACT),
				"settlement.alaindustrial.building.town_hall", origin, Rotation.NONE)));
		registry.setStyle(town, STYLE.toString());
		if (!(level.getBlockEntity(corePos) instanceof SettlementCoreBlockEntity core)) {
			throw helper.assertionException("no settlement core in the intact hall at " + corePos);
		}
		core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
		core.serverTick(level, corePos, level.getBlockState(corePos));
		if (registry.claim(town, owner, "Owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			throw helper.assertionException("control: the lit town could not be claimed");
		}
		BuildingPlan plan = TownWorksScanner.plan(level, town, 0)
				.orElseThrow(() -> helper.assertionException("the hall has no plan"));
		TownWorksScanner.measureNow(level, town, 0);
		BlockPos at = origin.offset(DISPATCHER_LOCAL);
		level.setBlock(at, ModContent.TOWN_DISPATCHER.get().defaultBlockState()
				.setValue(TownDispatcherBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		List<BlockPos> carpets = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			if (BuiltInRegistries.BLOCK.getKey(plan.expected(i).getBlock()).equals(BuildingPlan.NEST_SPOT)) {
				carpets.add(plan.pos(i));
			}
		}
		List<BlockPos> placed = new ArrayList<>();
		for (int i = 0; i < nests; i++) {
			BlockPos spot = carpets.get(i);
			level.setBlock(spot, ModContent.DRONE_NEST.get().defaultBlockState(), Block.UPDATE_ALL);
			((DroneNestBlockEntity) level.getBlockEntity(spot)).insertDrone(DroneNestRules.batteryCapacity());
			placed.add(spot);
		}
		Rig rig = new Rig(level, town, plan, core, at, placed, carpets, origin, hold);
		if (withPlan) {
			rig.dispatcher().setItem(TownDispatcherBlockEntity.PLAN_SLOT, hallPlan());
		}
		rig.tick();
		return rig;
	}

	/** The restoration plan of the rig's hall, as the dispatcher's plan slot holds it. */
	static ItemStack hallPlan() {
		return RestorationPlanItem.of(new RestorationPlan(STYLE, HALL_INTACT));
	}

	/**
	 * Keeps the chunks under a hall at {@code origin} (13 x 11 blocks and a margin) loaded, or lets them go: a loading
	 * ticket, which loads a chunk without simulating it.
	 */
	private static void keepLoaded(ServerLevel level, BlockPos origin, boolean keep) {
		for (int cx = (origin.getX() - 4) >> 4; cx <= (origin.getX() + 17) >> 4; cx++) {
			for (int cz = (origin.getZ() - 4) >> 4; cz <= (origin.getZ() + 15) >> 4; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				if (keep) {
					level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, chunk, 0);
				} else {
					level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, chunk, 0);
				}
			}
		}
	}

	/**
	 * Blocks between two rigs on the grid: six chunks, so neither the loading tickets (two by two chunks) nor the
	 * LV territories (32 blocks round each core) of two rigs ever touch, while the squares of lower-status chunks
	 * around them mostly overlap.
	 */
	static final int RIG_SPACING = 96;
	/** Rigs in one row of the grid. */
	private static final int RIG_COLUMNS = 8;
	/** Rigs stood in this server so far, every attempt of every suite: the next rig's grid cell. */
	private static final AtomicInteger RIGS = new AtomicInteger();

	/**
	 * The origin of the next rig: a grid cell no rig of this server stood on, so no case finds a hall, a town or a
	 * claim another case left there (two suites once shared a slot, and the second could not claim its town).
	 */
	static BlockPos nextOrigin() {
		int cell = RIGS.getAndIncrement();
		return new BlockPos(91_000 + (cell % RIG_COLUMNS) * RIG_SPACING, 20,
				91_000 + (cell / RIG_COLUMNS) * RIG_SPACING);
	}

	/**
	 * The loading tickets of one rig, let go once: by the case when it is done, and by the test's end (passed,
	 * failed, timed out or retried) when the case never got there: a failed assertion, a setup that threw.
	 */
	static final class ChunkHold {
		private final ServerLevel level;
		private final BlockPos origin;
		private boolean held = true;

		private ChunkHold(ServerLevel level, BlockPos origin) {
			this.level = level;
			this.origin = origin;
		}

		static ChunkHold forTest(GameTestHelper helper, ServerLevel level, BlockPos origin) {
			ChunkHold hold = new ChunkHold(level, origin);
			ConfigOverrides.testInfo(helper).addListener(new GameTestListener() {
				@Override
				public void testStructureLoaded(GameTestInfo info) {}

				@Override
				public void testPassed(GameTestInfo info, GameTestRunner runner) {
					hold.release();
				}

				@Override
				public void testFailed(GameTestInfo info, GameTestRunner runner) {
					hold.release();
				}

				@Override
				public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) {
					hold.release();
				}
			});
			keepLoaded(level, origin, true);
			return hold;
		}

		void release() {
			if (held) {
				held = false;
				keepLoaded(level, origin, false);
			}
		}
	}

	/**
	 * @implements MOD-858-RULES — the dispatcher serves its town only inside the hall and only as the first one; a
	 *     second one idles and says so; at an LV core with a restored hall two nests work and the third is extra.
	 */
	public static void dispatcherAndNestsFollowTheRules(GameTestHelper helper) {
		Rig rig = rig(helper, 3);
		helper.assertTrue(rig.dispatcher().status() == DispatcherStatus.IDLE,
				"the hall's dispatcher must serve, it says " + rig.dispatcher().status());
		BlockPos second = rig.origin().offset(DISPATCHER_LOCAL).east();
		rig.level().setBlock(second, ModContent.TOWN_DISPATCHER.get().defaultBlockState(), Block.UPDATE_ALL);
		TownDispatcherBlockEntity other = (TownDispatcherBlockEntity) rig.level().getBlockEntity(second);
		other.serverTick(rig.level(), second, rig.level().getBlockState(second));
		helper.assertTrue(other.status() == DispatcherStatus.SECOND, "a second dispatcher must idle, it says "
				+ other.status());
		BlockPos outside = rig.origin().offset(-3, 1, -3);
		rig.level().setBlock(outside, ModContent.TOWN_DISPATCHER.get().defaultBlockState(), Block.UPDATE_ALL);
		TownDispatcherBlockEntity away = (TownDispatcherBlockEntity) rig.level().getBlockEntity(outside);
		away.serverTick(rig.level(), outside, rig.level().getBlockState(outside));
		helper.assertTrue(away.status() == DispatcherStatus.OUTSIDE_HALL, "a dispatcher outside the hall must say so, "
				+ "it says " + away.status());
		for (int t = 0; t < 3; t++) {
			rig.tick();
		}
		int limit = DroneNestRules.nestLimit(SettlementRegistry.get(rig.level().getServer()).find(rig.town())
				.orElseThrow().tier(), true);
		helper.assertTrue(limit == 2, "control: an LV core with a restored hall allows two nests, it allows " + limit);
		helper.assertTrue(rig.nest(0).status() == Status.IDLE && rig.nest(1).status() == Status.IDLE
				&& rig.nest(2).status() == Status.EXTRA, "nests: " + rig.nest(0).status() + ", "
				+ rig.nest(1).status() + ", " + rig.nest(2).status());
		rig.release();
		helper.succeed();
	}

	/**
	 * @implements MOD-858-CLEAR — a clearing takes the rubble out of the hall's debris cells, one flight a block, the
	 *     core paying, and never touches a block with data, the core, the capsule or the lab hatch's shaft.
	 */
	public static void clearingSparesProtectedBlocks(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		List<Integer> debris = rig.debris();
		helper.assertTrue(debris.size() >= 3, "control: the intact hall has too few debris cells: " + debris);
		BlockPos web1 = rig.plan().pos(debris.get(0));
		BlockPos web2 = rig.plan().pos(debris.get(1));
		BlockPos chest = rig.plan().pos(debris.get(2));
		rig.level().setBlock(web1, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		rig.level().setBlock(web2, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		rig.level().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockState core = rig.level().getBlockState(rig.core().getBlockPos());
		BlockState capsule = rig.level().getBlockState(rig.origin().offset(3, 1, 5));
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		long start = helper.getTick();
		long battery = rig.nest(0).battery();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the clearing is " + now);
			// The tier-1 numbers of MOD-858's research: how long two blocks of a hall take, and what the battery spent.
			Industrialization.LOGGER.info("[ala-towns] measure: tier-1 drone cleared 2 cells in {} ticks, "
					+ "battery {} -> {}", helper.getTick() - start, battery, rig.nest(0).battery());
			helper.assertTrue(rig.level().getBlockState(web1).isAir() && rig.level().getBlockState(web2).isAir(),
					"the cobwebs must be gone");
			helper.assertTrue(rig.level().getBlockState(chest).is(Blocks.CHEST), "the chest (a block with data) must "
					+ "stand");
			helper.assertTrue(rig.level().getBlockState(rig.core().getBlockPos()).equals(core)
					&& rig.level().getBlockState(rig.origin().offset(3, 1, 5)).equals(capsule),
					"the core and the capsule must stand untouched");
			helper.assertTrue(rig.dispatcher().journal().entries().stream()
					.anyMatch(e -> e.kind() == WorkJournal.Kind.CLEARED),
					"the journal must note the cleared rubble: " + rig.dispatcher().journal().entries());
			rig.release();
		});
	}

	/** @implements MOD-858-OBSTRUCTED — a block the drone may not take away where air belongs stops the job: «X». */
	public static void obstructionBlocksTheJob(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		BlockPos cell = rig.plan().pos(rig.debris().getFirst());
		rig.level().setBlock(cell, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().equals(Optional.of(
					BlockReason.obstructed(Blocks.DIAMOND_BLOCK.getDescriptionId(), cell.getX(), cell.getY(),
							cell.getZ()))), "the job must stop "
					+ "«obstructed by diamond block», it is " + now);
			helper.assertTrue(rig.level().getBlockState(cell).is(Blocks.DIAMOND_BLOCK), "the diamond block must stand");
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-CREATURE — a cell with a creature in it is passed and taken up once it is free. The job is
	 *     raised only once the level sees the stand: a far chunk's entities come into the world a few ticks late.
	 */
	public static void creatureInCellIsSkippedAndRetried(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		BlockPos cell = rig.plan().pos(rig.debris().getFirst());
		rig.level().setBlock(cell, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		ArmorStand stand = new ArmorStand(rig.level(), cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
		helper.assertTrue(rig.level().addFreshEntity(stand), "control: the armor stand could not be added");
		long[] raisedAt = {-1};
		boolean[] saw = {false};
		helper.onEachTick(() -> {
			rig.tick();
			if (raisedAt[0] < 0) {
				if (!rig.level().getEntitiesOfClass(ArmorStand.class, new AABB(cell)).isEmpty()) {
					rig.works().queue().raise(WorkJobType.CLEAR, 0);
					raisedAt[0] = helper.getTick();
				}
				return;
			}
			if (rig.nest(0).status() == Status.WAITING && rig.level().getBlockState(cell).is(Blocks.COBWEB)) {
				saw[0] = true;
			}
			if (helper.getTick() - raisedAt[0] == 300) {
				stand.discard();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(raisedAt[0] >= 0, "control: the armor stand never came into the world");
			helper.assertTrue(saw[0], "the drone must wait while the creature stands in the cell: nest "
					+ rig.nest(0).status() + ", job " + rig.job(WorkJobType.CLEAR) + ", cell "
					+ rig.level().getBlockState(cell));
			helper.assertTrue(helper.getTick() - raisedAt[0] > 300, "the creature still stands there");
			helper.assertTrue(rig.level().getBlockState(cell).isAir(), "the cobweb must go once the cell is free");
			rig.release();
		});
	}

	/** @implements MOD-858-CHUNK — a cell in a chunk nobody keeps loaded stops the job «chunk not loaded». */
	public static void unloadedChunkPausesTheJob(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		BlockPos cell = rig.plan().pos(rig.debris().getFirst());
		rig.level().setBlock(cell, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		rig.nest(0).useLoadedProbe(pos -> !pos.equals(cell));
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
					.equals(Optional.of(BlockReason.CHUNK_NOT_LOADED)), "the job must pause, it is " + now);
			helper.assertTrue(rig.level().getBlockState(cell).is(Blocks.COBWEB), "nothing must be done to the cell");
			rig.release();
		});
	}

	/** @implements MOD-858-ONE-CELL — two working nests: one takes the hall, the other waits; no cell is shared. */
	public static void twoDronesNeverShareACell(GameTestHelper helper) {
		Rig rig = rig(helper, 2);
		for (int i : rig.debris().subList(0, 3)) {
			rig.level().setBlock(rig.plan().pos(i), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			DroneNestBlockEntity a = rig.nest(0);
			DroneNestBlockEntity b = rig.nest(1);
			DroneNestBlockEntity flying = a.flight().flying() ? a : b;
			DroneNestBlockEntity other = flying == a ? b : a;
			helper.assertTrue(flying.flight().flying() && !other.flight().flying(),
					"exactly one drone must fly: " + a.status() + " / " + b.status() + ", job "
							+ rig.job(WorkJobType.CLEAR));
			BlockPos target = flying.flight().target();
			var roster = SettlementRegistry.get(rig.level().getServer()).drones();
			helper.assertTrue(flying.taker().equals(roster.holder(target.asLong()))
					&& !roster.reserve(target.asLong(), other.taker()), "the flying drone's cell must be its own");
			helper.assertTrue(other.status() == Status.IDLE, "the second drone must find no job, it says "
					+ other.status());
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-SAVE — a drone saved in the air and loaded again places its block once, and the buffer loses
	 *     exactly that block's item: none at take-off, none twice.
	 * @covers R-PER-01
	 */
	public static void saveAndLoadMidFlightNeitherDupesNorLoses(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		ServerLevel level = rig.level();
		BlockPos cell = takeOutAWallBrick(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 5));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			rig.tick();
			DroneNestBlockEntity nest = rig.nest(0);
			if (!reloaded[0] && nest.flight().flying() && !nest.flight().returning()) {
				helper.assertTrue(rig.dispatcher().buffer().count("minecraft:bricks") == 5,
						"nothing may leave the buffer at take-off");
				reload(level, rig.nests().getFirst());
				reload(level, rig.dispatcherAt());
				reloaded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "control: the drone never took off");
			helper.assertTrue(rig.works().queue().job(job.id()).orElseThrow().status() == WorkJobStatus.DONE,
					"the building job must be done");
			helper.assertTrue(level.getBlockState(cell).is(Blocks.BRICKS), "the brick must be back");
			helper.assertTrue(rig.dispatcher().buffer().count("minecraft:bricks") == 4,
					"exactly one brick must have left the buffer, it holds "
							+ rig.dispatcher().buffer().count("minecraft:bricks"));
			helper.assertTrue(rig.dispatcher().journal().entries().stream()
					.anyMatch(e -> e.kind() == WorkJournal.Kind.PLACED), "the reloaded journal must note the brick");
			rig.release();
		});
	}

	/** Takes one brick of the hall's walls out — a building job's one cell to fill — and returns where it was. */
	static BlockPos takeOutAWallBrick(GameTestHelper helper, Rig rig) {
		ServerLevel level = rig.level();
		BuildMaterials.StagedPlan staged = BuildMaterials.forBuilding(level, rig.town(), 0).orElseThrow();
		int brick = -1;
		for (int i = 0; i < rig.plan().size() && brick < 0; i++) {
			if (staged.stage(i).equals(Optional.of(BuildStage.WALLS)) && staged.item(i).equals("minecraft:bricks")
					&& level.getBlockState(rig.plan().pos(i)).is(Blocks.BRICKS)) {
				brick = i;
			}
		}
		helper.assertTrue(brick >= 0, "control: no brick of the walls in the intact hall");
		BlockPos cell = rig.plan().pos(brick);
		level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		return cell;
	}

	/** Saves the block entity at {@code pos} and loads it back into a new one, as a chunk reload does. */
	static void reload(ServerLevel level, BlockPos pos) {
		BlockEntity old = level.getBlockEntity(pos);
		CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
		BlockEntity fresh = BlockEntity.loadStatic(pos, level.getBlockState(pos), tag, level.registryAccess());
		level.setBlockEntity(fresh);
	}

	/**
	 * @implements MOD-858-BATTERY — the core goes dark while the drone is out: it takes its block away on its battery,
	 *     comes home, and the job waits «no energy».
	 */
	public static void darkCoreLetsTheDroneFinishOnBattery(GameTestHelper helper) {
		Rig rig = rig(helper, 1);
		BlockPos cell = rig.plan().pos(rig.debris().getFirst());
		rig.level().setBlock(cell, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		WorkJob job = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow();
		boolean[] dark = {false};
		long[] battery = {0};
		helper.onEachTick(() -> {
			DroneNestBlockEntity nest = rig.nest(0);
			if (!dark[0]) {
				rig.tick();
				if (nest.flight().flying() && !nest.flight().returning()) {
					dark[0] = true;
					battery[0] = nest.battery();
					// The core runs dry: with nothing in its buffer it goes dark after its grace ticks.
					rig.core().getEnergyStorage().setAmountUntracked(0L);
					AlaGameTestHelper.drive(rig.core(), helper, SettlementConfig.settlementCoreGraceTicks + 2);
					helper.assertTrue(!rig.core().isActive(), "control: the drained core is still lit");
				}
				return;
			}
			rig.tickDispatcherAndNests();
		});
		helper.succeedWhen(() -> {
			DroneNestBlockEntity nest = rig.nest(0);
			helper.assertTrue(dark[0], "control: the drone never took off");
			helper.assertTrue(rig.level().getBlockState(cell).isAir(), "the drone must finish its block on battery");
			helper.assertTrue(!nest.flight().flying(), "the drone must come home");
			helper.assertTrue(nest.battery() == battery[0] - DroneNestRules.blockPrice(), "the battery must pay the "
					+ "block: " + battery[0] + " -> " + nest.battery());
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
					.equals(Optional.of(BlockReason.NO_ENERGY)) && nest.status() == Status.CORE_DARK,
					"the job must wait for energy: " + now + ", nest " + nest.status());
			rig.release();
		});
	}

	/**
	 * @implements MOD-858-OWNER — only the town's owner assembles the plan and moves items (R95); anybody else opens
	 *     the screen and changes nothing.
	 */
	public static void onlyTheOwnerControlsTheDispatcher(GameTestHelper helper) {
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		helper.assertTrue(!owner.getUUID().equals(stranger.getUUID()), "control: two players with one id");
		Rig rig = rig(helper, 0, owner.getUUID(), false);
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		for (int i = 0; i < TownDispatcherBlockEntity.SHEET_SLOTS; i++) {
			dispatcher.setItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST + i, TownHallPlanSheetItem.of(STYLE, i + 1));
		}
		helper.assertTrue(!dispatcher.mayControl(stranger) && dispatcher.mayControl(owner),
				"only the owner may control the dispatcher");
		dispatcher.assemble(stranger);
		helper.assertTrue(dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT).isEmpty()
				&& dispatcher.getItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST).getCount() == 1,
				"a stranger's press must change nothing");
		dispatcher.assemble(owner);
		RestorationPlan plan = RestorationPlanItem.planOf(dispatcher.getItem(TownDispatcherBlockEntity.PLAN_SLOT));
		helper.assertTrue(plan != null && plan.template().equals(HALL_INTACT)
				&& dispatcher.getItem(TownDispatcherBlockEntity.SHEET_SLOT_FIRST).isEmpty(),
				"the owner's press must turn the three sheets into the hall's plan, the plan slot holds " + plan);
		rig.release();
		helper.succeed();
	}
}
