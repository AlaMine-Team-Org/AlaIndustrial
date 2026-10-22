package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.SettlementRegistry;
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
						"town_hall_stage_news_tells_stages_and_the_restoration").ticks(4000));

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
	 * @implements MOD-860-NEWS — on a hall restored before, the walls line says the next stage is stocked and the
	 *     fittings line says the hall stands whole.
	 */
	public static void interiorBuildsOnAStockedBuffer(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		TownDroneScenarios.takeOutAWallBrick(helper, rig);
		List<BlockPos> cells = takeOutFittings(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 1));
		fill(rig);
		List<TranslatableContents> heard = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town() && line.getSiblings().getLast().getContents() instanceof TranslatableContents t) {
				heard.add(t);
			}
		});
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			assertBuilt(helper, rig, job, cells);
			deaf.run();
			// The hall was restored before (the rig measured it whole): the walls say the fittings are stocked, the
			// fittings say the hall stands whole.
			TranslatableContents ready = only(helper, heard, HallBuildNews.STAGE_READY);
			helper.assertTrue(key(ready, 2).equals(BuildStage.WALLS.key()) && key(ready, 3)
					.equals(BuildStage.INTERIOR.key()), "the walls line must name the fittings next: " + args(ready));
			TranslatableContents last = only(helper, heard, HallBuildNews.STAGE_LAST);
			helper.assertTrue(key(last, 2).equals(BuildStage.INTERIOR.key()), "the last line must name the fittings: "
					+ args(last));
			helper.assertTrue(heard.size() == 2, "two lines expected, heard " + keys(heard));
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
		List<TranslatableContents> heard = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town() && line.getSiblings().getLast().getContents() instanceof TranslatableContents t) {
				heard.add(t);
			}
		});
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 1));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		boolean[] filled = {false};
		helper.onEachTick(() -> {
			rig.tick();
			if (!filled[0] && heard.stream().anyMatch(t -> t.getKey().equals(HallBuildNews.STAGE_NEEDS))) {
				fill(rig);
				filled[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(rig.works().queue().job(job.id()).orElseThrow().status() == WorkJobStatus.DONE,
					"the building job must be done, heard " + keys(heard));
			deaf.run();
			String core = rig.core().getBlockPos().getX() + ", " + rig.core().getBlockPos().getY() + ", "
					+ rig.core().getBlockPos().getZ();
			TranslatableContents needs = only(helper, heard, HallBuildNews.STAGE_NEEDS);
			helper.assertTrue(text(needs, 0).equals(String.valueOf(rig.town())) && text(needs, 1).equals(core)
					&& key(needs, 2).equals(BuildStage.WALLS.key()) && key(needs, 3).equals(BuildStage.INTERIOR.key()),
					"the walls line must name the settlement, its core and the fittings next: " + args(needs));
			only(helper, heard, HallBuildNews.HALL_RESTORED);
			// The drone pad's carpet is a key point: the hall is restored with the fittings' last block, and that
			// one line says it. No «stands whole» line beside it, no «ready» line: the fittings waited.
			helper.assertTrue(heard.size() == 2, "two lines expected, heard " + keys(heard));
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
