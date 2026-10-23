package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.DispatcherViews;
import dev.alaindustrial.settlement.drone.StalledOrders;
import dev.alaindustrial.settlement.drone.StartReadiness;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownOrders;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * L2 suite of the town hall's last stage, the fittings (owner's round 3 of stage 6): a building job that stopped «no
 * materials» goes on by itself once the dispatcher's buffer holds what the stage needs, whether the buffer was filled
 * before the job started or after it stopped. Replayed by both loaders through the roster (ADR-038).
 */
public final class TownHallStageScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownHallStageScenarios::interiorResumesOnceTheBufferIsFilled,
						"town_hall_stage_interior_resumes_once_the_buffer_is_filled").ticks(3000),
				RosterEntry.of(TownHallStageScenarios::interiorBuildsOnAStockedBuffer,
						"town_hall_stage_interior_builds_on_a_stocked_buffer").ticks(3000),
				RosterEntry.of(TownHallStageScenarios::interiorResumesAfterAClearingRaisedMeanwhile,
						"town_hall_stage_interior_resumes_after_a_clearing_raised_meanwhile").ticks(3000),
				RosterEntry.of(TownHallStageScenarios::hallNewsTellsStagesAndTheRestoration,
						"town_hall_stage_news_tells_stages_and_the_restoration").ticks(4000),
				RosterEntry.of(TownHallStageScenarios::aBlockWithDataStopsTheJobNamingItself,
						"town_hall_stage_a_block_with_data_stops_the_job_naming_itself").ticks(3000),
				RosterEntry.of(TownHallStageScenarios::anOrderThatEndedUnfinishedIsPlacedAgain,
						"town_hall_stage_an_order_that_ended_unfinished_is_placed_again").ticks(3000),
				RosterEntry.of(TownHallStageScenarios::aCalledOffOrderWaitsForStart,
						"town_hall_stage_a_called_off_order_waits_for_start").ticks(3000));

		private Roster() {}
	}

	private TownHallStageScenarios() {}

	/** The fittings the owner's hall waited for: one cell of each is taken out, a stack of each fills the buffer. */
	private static final List<String> FITTINGS = List.of("minecraft:torch", "minecraft:chest",
			"minecraft:yellow_carpet");

	/**
	 * @implements MOD-860-RESUME — the fittings stage waits «no materials» on an empty buffer; filled afterwards with a
	 *     stack of torches, chests and yellow carpets, the job goes on by itself and finishes.
	 */
	public static void interiorResumesOnceTheBufferIsFilled(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		boolean[] filled = {false};
		helper.onEachTick(() -> {
			rig.tick();
			if (!filled[0]) {
				WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
				if (now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
						.equals(Optional.of(BlockReason.NO_MATERIALS))) {
					fill(rig);
					filled[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filled[0], "control: the job never stopped «no materials»: "
					+ rig.works().queue().job(job.id()));
			assertBuilt(helper, rig, job, cells);
		});
	}

	/**
	 * @implements MOD-860-RESUME — the owner's round 3 save: the fittings stage stopped «no materials», a clearing of
	 *     the same hall was raised meanwhile (the core's «Construction» tab offers it while no clearing is open) and
	 *     done; then the buffer is filled. The building job must not stay stopped with its old reason: the drone
	 *     takes it up again by itself.
	 */
	public static void interiorResumesAfterAClearingRaisedMeanwhile(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		int[] clearing = {0};
		boolean[] filled = {false};
		helper.onEachTick(() -> {
			rig.tick();
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			boolean waiting = now.status() == WorkJobStatus.BLOCKED && now.reason().map(BlockReason::key)
					.equals(Optional.of(BlockReason.NO_MATERIALS));
			if (clearing[0] == 0 && waiting) {
				clearing[0] = rig.works().queue().raise(WorkJobType.CLEAR, 0).orElseThrow().id();
			} else if (clearing[0] > 0 && !filled[0] && rig.works().queue().job(clearing[0]).orElseThrow().status()
					== WorkJobStatus.DONE) {
				fill(rig);
				filled[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filled[0], "control: the clearing raised meanwhile was never done: "
					+ rig.works().queue().jobs());
			assertBuilt(helper, rig, job, cells);
		});
	}

	/**
	 * @implements MOD-860-RESUME — the same fittings, and a wall brick, with the buffer stocked before the job starts.
	 * @implements MOD-860-NEWS — a hall that met its restoration share before its fittings were in (the rig measured it
	 *     restored; its first building job is still open): the walls line says the next stage is stocked; the hall's
	 *     order stays in the dispatcher's list and nothing says «restored» while the drones are at it; when the job is
	 *     done the order is gone and one line says the hall is restored and the house plan it teaches (owner's round 3
	 *     of 0.1.219, R3-1, R3-2).
	 */
	public static void interiorBuildsOnAStockedBuffer(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		TownDroneScenarios.takeOutAWallBrick(helper, rig);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 1));
		fill(rig);
		List<Component> lines = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town()) {
				lines.add(line.getSiblings().getLast());
			}
		});
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		List<String> early = new ArrayList<>();
		helper.onEachTick(() -> {
			rig.tick();
			boolean open = rig.works().queue().job(job.id()).orElseThrow().status().open();
			if (open && !rig.works().orders().contains(0)) {
				early.add("the hall's order left the list while its job was open");
			}
			if (open && keys(contents(lines)).contains(HallBuildNews.HALL_RESTORED)) {
				early.add("«restored» was said while the job was open");
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, rig, job, cells);
			deaf.run();
			helper.assertTrue(early.isEmpty(), String.valueOf(early));
			helper.assertTrue(rig.works().orders().isEmpty(), "a finished hall keeps no order: "
					+ rig.works().orders());
			List<TranslatableContents> heard = contents(lines);
			TranslatableContents done = only(helper, heard, HallBuildNews.STAGE_DONE);
			TranslatableContents ready = only(helper, heard, HallBuildNews.NEXT_READY);
			helper.assertTrue(key(done, 0).equals(BuildStage.WALLS.key()) && key(ready, 0)
					.equals(BuildStage.INTERIOR.key()), "the walls line must name the fittings next: " + args(done)
					+ " " + args(ready));
			only(helper, heard, HallBuildNews.HALL_RESTORED);
			assertRewardSaid(helper, lines);
			helper.assertTrue(lines.size() == 2, "two lines expected, heard " + keys(heard));
		});
	}

	/**
	 * @implements MOD-860-NEWS — the restorer hears the hall's restoration in chat: a stage done names the settlement's
	 *     number, its core and the next stage, and says whether that stage still waits for its materials; the first
	 *     restoration says so once, and the last stage that restores the hall says nothing beside it. The dispatcher's
	 *     journal notes the closed stages and the restoration.
	 */
	public static void hallNewsTellsStagesAndTheRestoration(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		TownDroneScenarios.takeOutAWallBrick(helper, rig);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		// The town forgets its hall was ever restored: the first measuring pass restores it again, as a ruin built
		// up to its share would be.
		TownBuilding hall = rig.works().buildings().getFirst();
		SettlementRegistry registry = SettlementRegistry.get(rig.level().getServer());
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(TownBuilding.found(hall.template(), hall.intact(),
				hall.name(), hall.origin(), hall.rotation())));
		List<Component> lines = new ArrayList<>();
		List<Component> headings = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town()) {
				lines.add(line.getSiblings().getLast());
				line.getSiblings().stream().filter(part -> part.getContents() instanceof TranslatableContents t
						&& t.getKey().equals(HallBuildNews.HEADER)).forEach(headings::add);
			}
		});
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 1));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		boolean[] filled = {false};
		helper.onEachTick(() -> {
			rig.tick();
			if (!filled[0] && keys(contents(lines)).contains(HallBuildNews.NEXT_NEEDS)) {
				fill(rig);
				filled[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			List<TranslatableContents> heard = contents(lines);
			helper.assertTrue(rig.works().queue().job(job.id()).orElseThrow().status() == WorkJobStatus.DONE,
					"the building job must be done, heard " + keys(heard));
			deaf.run();
			String core = rig.core().getBlockPos().getX() + ", " + rig.core().getBlockPos().getY() + ", "
					+ rig.core().getBlockPos().getZ();
			TranslatableContents done = only(helper, heard, HallBuildNews.STAGE_DONE);
			TranslatableContents needs = only(helper, heard, HallBuildNews.NEXT_NEEDS);
			TranslatableContents heading = contents(headings).getFirst();
			helper.assertTrue(headings.size() == lines.size() && text(heading, 0).equals(String.valueOf(rig.town()))
					&& text(heading, 1).equals(core) && key(done, 0).equals(BuildStage.WALLS.key())
					&& key(needs, 0).equals(BuildStage.INTERIOR.key()),
					"the walls line must name the settlement, its core and the fittings next: " + args(heading) + " "
							+ args(done) + " " + args(needs));
			only(helper, heard, HallBuildNews.HALL_RESTORED);
			// The restored hall teaches the plainest house of its style (round 2 of 0.1.219, Q5): said in the same
			// line (R3-2).
			assertRewardSaid(helper, lines);
			// The drone pad's carpet is a key point: the hall is restored with the fittings' last block, and that
			// one line says it. No «ready» line: the fittings waited.
			helper.assertTrue(lines.size() == 2, "two lines expected, heard " + keys(heard));
			List<WorkJournal.Kind> journal = rig.dispatcher().journal().entries().stream().map(WorkJournal.Entry::kind)
					.toList();
			helper.assertTrue(journal.contains(WorkJournal.Kind.RESTORED)
					&& journal.stream().filter(k -> k == WorkJournal.Kind.STAGE_DONE).count() == 2,
					"the journal must note two closed stages and the restoration: " + journal);
			for (BlockPos pos : cells) {
				helper.assertTrue(!rig.level().getBlockState(pos).isAir(), "the fitting at " + pos + " must be back");
			}
			rig.release();
		});
	}

	/**
	 * @implements MOD-860-RESUME — the owner's round 5 dead end: a block with data (a chest here; a creative energy
	 *     source in the owner's world) stands in the walls where the plan wants iron bars, iron bars lie in the buffer.
	 *     The building job never ends with the hall half built and never passes the block silently: it stops «in the
	 *     way: chest at x y z», the restorer hears it in chat once; the chest taken away, the job goes on by itself,
	 *     puts the iron bars and the fittings, and is done. Before the fix the job ended at once, fittings unbuilt.
	 */
	public static void aBlockWithDataStopsTheJobNamingItself(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		BuildMaterials.StagedPlan staged = BuildMaterials.forBuilding(rig.level(), rig.town(), 0).orElseThrow();
		int bars = -1;
		for (int i = 0; i < rig.plan().size() && bars < 0; i++) {
			if (staged.stage(i).equals(Optional.of(BuildStage.WALLS)) && staged.item(i).equals("minecraft:iron_bars")
					&& rig.level().getBlockState(rig.plan().pos(i)).is(Blocks.IRON_BARS)) {
				bars = i;
			}
		}
		helper.assertTrue(bars >= 0, "control: no iron bars in the walls of the intact hall");
		BlockPos chest = rig.plan().pos(bars);
		rig.level().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.IRON_BARS, 8));
		fill(rig);
		List<String> told = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town()) {
				told.addAll(keys(contents(List.of(line.getSiblings().getLast()))));
			}
		});
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		BlockReason expected = BlockReason.obstructed(Blocks.CHEST.getDescriptionId(), chest.getX(), chest.getY(),
				chest.getZ());
		int[] stoppedTicks = {0};
		helper.onEachTick(() -> {
			rig.tick();
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(stoppedTicks[0] > 0 || now.status() != WorkJobStatus.DONE,
					"the job must not end while the chest stands in the walls");
			if (rig.level().getBlockState(chest).is(Blocks.CHEST) && now.status() == WorkJobStatus.BLOCKED
					&& now.reason().equals(Optional.of(expected)) && ++stoppedTicks[0] >= 300) {
				// Long enough for several retries: the stop is told once, then the player takes the chest away.
				rig.level().setBlock(chest, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stoppedTicks[0] >= 300, "control: the job never stopped " + expected + ": "
					+ rig.works().queue().job(job.id()));
			helper.assertTrue(rig.level().getBlockState(chest).is(Blocks.IRON_BARS), "the iron bars must be back");
			assertBuilt(helper, rig, job, cells);
			deaf.run();
			helper.assertTrue(told.stream().filter(HallBuildNews.JOB_STOPPED::equals).count() == 1,
					"the stop must be told once, heard " + told);
		});
	}

	/**
	 * @implements MOD-860-RESUME — no «all ticked, nothing happens» (owner's round 5): the hall's order ended — its
	 *     building job done — while a door of the hall is still missing, and no job is open. The town places the order
	 *     again by itself and the drone puts the door back.
	 */
	public static void anOrderThatEndedUnfinishedIsPlacedAgain(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		freshHall(rig); // a hall never restored: its order is the first restoration's, not a repair
		List<BlockPos> door = takeOutADoor(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.DARK_OAK_DOOR, 4));
		WorkJob ended = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		rig.works().queue().take(ended.id(), "ended");
		rig.works().queue().complete(ended.id(), "ended");
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			List<WorkJob> again = rig.works().queue().jobs().stream()
					.filter(j -> j.building() == 0 && j.id() > ended.id() && j.type() == WorkJobType.BUILD).toList();
			helper.assertTrue(!again.isEmpty() && again.getLast().status() == WorkJobStatus.DONE,
					"the hall's order must be placed again and done: " + rig.works().queue().jobs());
			for (BlockPos pos : door) {
				helper.assertTrue(rig.level().getBlockState(pos).is(Blocks.DARK_OAK_DOOR), "the door at " + pos
						+ " must be back, the world holds " + rig.level().getBlockState(pos));
			}
			rig.release();
		});
	}

	/**
	 * @implements MOD-860-RESUME — an order the owner called off is the player's intent (round 5 of 0.1.219): the
	 *     town never places it again by itself, the «Task» tab says it was called off and «Start» may be pressed, the
	 *     building's card says so with «Resume»; placed again, it is built.
	 */
	public static void aCalledOffOrderWaitsForStart(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		freshHall(rig);
		List<BlockPos> door = takeOutADoor(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.DARK_OAK_DOOR, 4));
		Settlement town = SettlementRegistry.get(rig.level().getServer()).find(rig.town()).orElseThrow();
		helper.assertTrue(TownOrders.place(rig.level(), town, 0, "test").isEmpty(), "control: the order is placed");
		TownOrders.cancel(rig.works(), 0);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			rig.tick();
			if (++ticks[0] == StalledOrders.RESTORE_AFTER * 2) {
				helper.assertTrue(rig.works().queue().jobs().stream().noneMatch(j -> j.status().open()),
						"a called-off order must not be placed again: " + rig.works().queue().jobs());
				DispatcherView view = rig.dispatcher().view();
				helper.assertTrue(view.task().stream().anyMatch(t -> t.key().equals(DispatcherViews.ORDER_CALLED_OFF)),
						"the Task tab must say the order was called off: " + view.task());
				helper.assertTrue(StartReadiness.blockers(true, view.checklist(), view.checklist().hall(), 1).isEmpty(),
						"Start must be free: " + view.checklist());
				helper.assertTrue(ConstructionRows.stop(rig.works(), 0, TownOrders.button(rig.works(), 0))
						.map(ConstructionRow.Text::key).equals(Optional.of(ConstructionRows.CALLED_OFF)),
						"the card must say the order was called off");
				TownOrders.place(rig.level(), town, 0, "test");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ticks[0] > StalledOrders.RESTORE_AFTER * 2, "control: the order was not placed again");
			for (BlockPos pos : door) {
				helper.assertTrue(rig.level().getBlockState(pos).is(Blocks.DARK_OAK_DOOR), "the door at " + pos
						+ " must be back, the world holds " + rig.level().getBlockState(pos));
			}
			rig.release();
		});
	}

	/** The town forgets its hall was ever restored: a never restored hall, whose order is no repair. */
	private static void freshHall(Rig rig) {
		TownBuilding hall = rig.works().buildings().getFirst();
		SettlementRegistry registry = SettlementRegistry.get(rig.level().getServer());
		registry.works().forget(rig.town());
		registry.works().town(rig.town()).learnBuildings(List.of(TownBuilding.found(hall.template(), hall.intact(),
				hall.name(), hall.origin(), hall.rotation())));
	}

	/** Takes both halves of one dark oak door of the hall out, and returns where they were. */
	private static List<BlockPos> takeOutADoor(GameTestHelper helper, Rig rig) {
		for (int i = 0; i < rig.plan().size(); i++) {
			BlockPos pos = rig.plan().pos(i);
			if (rig.plan().expected(i).is(Blocks.DARK_OAK_DOOR)
					&& rig.level().getBlockState(pos).is(Blocks.DARK_OAK_DOOR)
					&& rig.level().getBlockState(pos.above()).is(Blocks.DARK_OAK_DOOR)) {
				rig.level().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				rig.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				return List.of(pos, pos.above());
			}
		}
		throw helper.assertionException("control: no dark oak door in the intact hall");
	}

	/** The restored line carries the house plan the hall teaches after its body, in the same message (R3-2). */
	private static void assertRewardSaid(GameTestHelper helper, List<Component> lines) {
		Component restored = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents t
				&& t.getKey().equals(HallBuildNews.HALL_RESTORED)).findFirst().orElseThrow();
		boolean reward = restored.getSiblings().stream().anyMatch(part -> part.getContents()
				instanceof TranslatableContents t && t.getKey().equals(HallBuildNews.HALL_REWARD));
		helper.assertTrue(reward, "the restored line must name the house plan the hall teaches: " + restored);
	}

	/** The sentences of {@code lines} and the parts said after each in the same message (R4-7). */
	private static List<TranslatableContents> contents(List<Component> lines) {
		List<TranslatableContents> out = new ArrayList<>();
		for (Component line : List.copyOf(lines)) {
			if (line.getContents() instanceof TranslatableContents t) {
				out.add(t);
			}
			for (Component part : line.getSiblings()) {
				if (part.getContents() instanceof TranslatableContents t) {
					out.add(t);
				}
			}
		}
		return out;
	}

	private static TranslatableContents only(GameTestHelper helper, List<TranslatableContents> heard, String key) {
		List<TranslatableContents> found = heard.stream().filter(t -> t.getKey().equals(key)).toList();
		helper.assertTrue(found.size() == 1, "one line " + key + " expected, heard " + keys(heard));
		return found.getFirst();
	}

	private static List<String> keys(List<TranslatableContents> heard) {
		return heard.stream().map(TranslatableContents::getKey).toList();
	}

	private static String text(TranslatableContents line, int arg) {
		return ((Component) line.getArgs()[arg]).getString();
	}

	private static String key(TranslatableContents line, int arg) {
		return ((Component) line.getArgs()[arg]).getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static List<String> args(TranslatableContents line) {
		List<String> out = new ArrayList<>();
		for (Object arg : line.getArgs()) {
			out.add(arg instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey()
					: String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}

	/** A stack of each fitting the hall takes, put into the dispatcher's buffer. */
	private static void fill(Rig rig) {
		for (String item : FITTINGS) {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)), 64);
			rig.dispatcher().buffer().insert(stack);
		}
	}

	/**
	 * Takes one torch, one chest and one yellow carpet of the hall's fittings out — the cells of the last stage — and
	 * returns where they were.
	 */
	private static List<BlockPos> takeOutFittings(GameTestHelper helper, Rig rig) {
		BuildMaterials.StagedPlan staged = BuildMaterials.forBuilding(rig.level(), rig.town(), 0).orElseThrow();
		List<BlockPos> out = new ArrayList<>();
		for (String item : FITTINGS) {
			int found = -1;
			for (int i = 0; i < rig.plan().size() && found < 0; i++) {
				BlockPos pos = rig.plan().pos(i);
				if (staged.stage(i).equals(Optional.of(BuildStage.INTERIOR)) && staged.item(i).equals(item)
						&& rig.level().getBlockState(pos).is(rig.plan().expected(i).getBlock())
						&& !rig.nests().contains(pos)) {
					found = i;
				}
			}
			helper.assertTrue(found >= 0, "control: no " + item + " of the fittings in the intact hall");
			BlockPos pos = rig.plan().pos(found);
			rig.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			out.add(pos);
		}
		return out;
	}

	private static void assertBuilt(GameTestHelper helper, Rig rig, WorkJob job, List<BlockPos> cells) {
		WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
		List<String> left = new ArrayList<>();
		for (BlockPos pos : cells) {
			left.add(pos.subtract(rig.origin()) + "=" + rig.level().getBlockState(pos));
		}
		helper.assertTrue(now.status() == WorkJobStatus.DONE, "the building job must be done, it is " + now
				+ ", nest " + rig.nest(0).status() + ", cells " + left);
		for (BlockPos pos : cells) {
			helper.assertTrue(!rig.level().getBlockState(pos).isAir(), "the fitting at " + pos + " must be back");
		}
		rig.release();
	}
}
