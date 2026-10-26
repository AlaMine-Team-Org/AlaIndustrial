package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.TownDispatcherBlock;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DispatcherSpot;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownBuildingDef;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L2 suite of the dispatcher's place in the town hall (MOD-891; epic decision R158), replayed by both loaders through
 * the roster (ADR-038). The intact hall of both styles keeps one cell for the dispatcher: a dispatcher there never
 * stops the restoration and counts as in place; put on a cell where the plan wants a block it tells the player at
 * once, and the building job stops «in the way» there as before (R143).
 */
public final class TownDispatcherSpotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDispatcherSpotScenarios::bothHallsKeepACellForTheDispatcher,
						"town_dispatcher_spot_both_halls_keep_a_cell").ticks(20),
				RosterEntry.of(TownDispatcherSpotScenarios::aDispatcherOnItsCellLetsTheHallRestore,
						"town_dispatcher_spot_on_its_cell_the_hall_restores").ticks(1600),
				RosterEntry.of(TownDispatcherSpotScenarios::aDispatcherOnAPlannedBlockWarnsAndStopsTheJob,
						"town_dispatcher_spot_on_a_planned_block_warns_and_stops").ticks(3000),
				RosterEntry.of(TownDispatcherSpotScenarios::onlyAPlannedBlockIsWarnedAbout,
						"town_dispatcher_spot_only_a_planned_block_is_warned_about").ticks(40));

		private Roster() {
		}
	}

	private TownDispatcherSpotScenarios() {
	}

	/** The dispatcher's cell in the intact hall of both styles, in the template's own coordinates. */
	private static final BlockPos SPOT_LOCAL = new BlockPos(2, 1, 1);
	/** A spruce trapdoor of the bench along the back wall of the intact plains hall (the owner's playtest cell). */
	private static final BlockPos BENCH_LOCAL = new BlockPos(8, 1, 9);
	/** On the plains hall's floor, where neither the intact hall nor the ruin has a block. */
	private static final BlockPos FREE_FLOOR_LOCAL = new BlockPos(4, 1, 7);
	/** West of the plains hall's wall, outside its footprint: still on the town's territory. */
	private static final BlockPos OUTSIDE_LOCAL = new BlockPos(-3, 1, 5);

	/**
	 * @implements MOD-891-SPOT — the intact hall of both styles, turned all four ways, keeps a cell for the
	 *     dispatcher: inside the hall, on its floor, off the lab's shaft, air in the plan with air over it for the
	 *     antenna; a dispatcher and air both match there and a dispatcher there is in nobody's way. Unturned, the cell
	 *     is the corner {@link #SPOT_LOCAL}, where the ruin has its machine casing.
	 */
	public static void bothHallsKeepACellForTheDispatcher(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState dispatcher = ModContent.TOWN_DISPATCHER.get().defaultBlockState();
		BlockPos origin = new BlockPos(1000, 64, 1000);
		for (String style : List.of("plains", "desert")) {
			Identifier hall = Industrialization.id("ala_town/" + style + "/town_hall");
			TownBuildingDef def = TownBuildingDef.byTemplate(level.registryAccess()).get(hall);
			helper.assertTrue(def != null, "no definition of " + hall);
			helper.assertTrue(def.dispatcherSpot().equals(Optional.of(SPOT_LOCAL)),
					hall + " must keep " + SPOT_LOCAL + " for the dispatcher, it keeps " + def.dispatcherSpot());
			for (Rotation rotation : Rotation.values()) {
				TownBuilding building = TownBuilding.found(hall, def.intact(), def.name(), origin, rotation);
				BuildingPlan plan = BuildingPlan.of(LineStructures.templates(level), building, def)
						.orElseThrow(() -> helper.assertionException("no plan of " + hall));
				String where = hall + " turned " + rotation;
				BlockPos spot = plan.dispatcherSpot()
						.orElseThrow(() -> helper.assertionException(where + ": the plan has no dispatcher's cell"));
				helper.assertTrue(plan.contains(spot) && !plan.onLabShaft(spot), where + ": the cell " + spot
						+ " must be inside the hall and off the lab's shaft");
				int index = plan.indexOf(spot);
				helper.assertTrue(index >= 0, where + ": control: the ruin's casing there makes the cell count");
				helper.assertTrue(plan.expected(index).isAir(), where + ": the plan must want air on the cell");
				helper.assertTrue(plan.matches(index, dispatcher)
						&& plan.matches(index, Blocks.AIR.defaultBlockState()),
						where + ": a dispatcher and air must both match on the cell");
				helper.assertTrue(!plan.matches(index, Blocks.CHEST.defaultBlockState()),
						where + ": only the dispatcher stands in for air there");
				helper.assertTrue(DispatcherSpot.inTheWay(plan, spot, dispatcher).isEmpty(),
						where + ": a dispatcher on its cell must be in nobody's way");
				int floor = plan.indexOf(spot.below());
				helper.assertTrue(floor >= 0 && !plan.expected(floor).isAir(),
						where + ": the cell must be on the floor");
				int over = plan.indexOf(spot.above());
				helper.assertTrue(over < 0 || plan.expected(over).isAir(), where + ": the antenna needs air over it");
				if (rotation == Rotation.NONE) {
					helper.assertTrue(spot.equals(origin.offset(SPOT_LOCAL)), where + ": the cell must be "
							+ origin.offset(SPOT_LOCAL) + ", it is " + spot);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-891-SPOT — a dispatcher on its cell counts as in place: every cell of the hall's plan matches,
	 *     and a building job takes a missing wall brick back with the dispatcher standing there and ends done.
	 */
	public static void aDispatcherOnItsCellLetsTheHallRestore(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1, TownDroneScenarios.OWNER, true, SPOT_LOCAL);
		helper.assertTrue(rig.plan().dispatcherSpot().equals(Optional.of(rig.dispatcherAt())),
				"control: the rig's dispatcher must stand on the hall's dispatcher cell "
						+ rig.plan().dispatcherSpot());
		helper.assertTrue(unmatched(rig).isEmpty(), "with the dispatcher on its cell every cell must match, these do "
				+ "not: " + unmatched(rig));
		BlockPos brick = TownDroneScenarios.takeOutAWallBrick(helper, rig);
		rig.dispatcher().buffer().insert(new ItemStack(Blocks.BRICKS, 4));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the building job must be done, it is " + now);
			helper.assertTrue(rig.level().getBlockState(brick).is(Blocks.BRICKS), "the brick must be back");
			helper.assertTrue(rig.level().getBlockState(rig.dispatcherAt()).is(ModContent.TOWN_DISPATCHER.get()),
					"the dispatcher must still stand on its cell");
			helper.assertTrue(unmatched(rig).isEmpty(), "the hall must be whole: " + unmatched(rig));
			helper.assertTrue(TownWorksScanner.measureNow(rig.level(), rig.town(), 0)
					.map(verdict -> verdict.state() == BuildingState.INTACT).orElse(false),
					"the hall must measure intact");
			rig.release();
		});
	}

	/**
	 * @implements MOD-891-WARN — a dispatcher put on a cell where the plan wants a block (the bench's trapdoor): the
	 *     player who puts it there hears one line naming the planned block and the dispatcher's cell, and the building
	 *     job stops «in the way: dispatcher at x y z» on it, as any block with data does (R143).
	 */
	public static void aDispatcherOnAPlannedBlockWarnsAndStopsTheJob(GameTestHelper helper) {
		ServerPlayer placer = AlaGameTestHelper.survivalPlayer(helper);
		Rig rig = TownDroneScenarios.rig(helper, 1, TownDroneScenarios.OWNER, true, BENCH_LOCAL);
		BlockPos at = rig.dispatcherAt();
		int bench = rig.plan().indexOf(at);
		helper.assertTrue(bench >= 0 && rig.plan().expected(bench).is(BlockTags.TRAPDOORS),
				"control: the plan must want a trapdoor at " + at);
		List<List<TranslatableContents>> heard = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town()) {
				heard.add(contents(line.getSiblings().getLast()));
			}
		});
		place(rig.level(), at, placer);
		deaf.run();
		List<List<TranslatableContents>> warnings = warnings(heard);
		helper.assertTrue(warnings.size() == 1, "one warning must be heard, heard " + keys(heard));
		BlockPos spot = rig.plan().dispatcherSpot().orElseThrow();
		List<TranslatableContents> warning = warnings.getFirst();
		helper.assertTrue(argKey(warning.getFirst()).equals(rig.plan().expected(bench).getBlock().getDescriptionId()),
				"the warning must name the planned block, it names " + argKey(warning.getFirst()));
		helper.assertTrue(warning.size() > 1 && warning.get(1).getKey().equals(HallBuildNews.DISPATCHER_SPOT)
				&& argText(warning.get(1)).equals(spot.getX() + ", " + spot.getY() + ", " + spot.getZ()),
				"the warning must say where the dispatcher's cell " + spot + " is: " + keys(heard));

		BuildMaterials.StagedPlan staged = BuildMaterials.forBuilding(rig.level(), rig.town(), 0).orElseThrow();
		rig.dispatcher().buffer().insert(new ItemStack(BuiltInRegistries.ITEM.getValue(
				Identifier.parse(staged.item(bench))), 4));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		BlockReason expected = BlockReason.obstructed(ModContent.TOWN_DISPATCHER.get().getDescriptionId(), at.getX(),
				at.getY(), at.getZ());
		helper.onEachTick(rig::tick);
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.BLOCKED && now.reason().equals(Optional.of(expected)),
					"the job must stop " + expected + ", it is " + now);
			helper.assertTrue(rig.level().getBlockState(at).is(ModContent.TOWN_DISPATCHER.get()),
					"the drone must never take the dispatcher away");
			rig.release();
		});
	}

	/**
	 * @implements MOD-891-WARN — no word when the dispatcher goes outside the hall, onto a free spot of its floor or
	 *     onto its own cell; one when it goes onto the bench's trapdoor (the control that the check speaks at all).
	 */
	public static void onlyAPlannedBlockIsWarnedAbout(GameTestHelper helper) {
		ServerPlayer placer = AlaGameTestHelper.survivalPlayer(helper);
		Rig rig = TownDroneScenarios.rig(helper, 0, TownDroneScenarios.OWNER, false);
		List<List<TranslatableContents>> heard = new ArrayList<>();
		Runnable deaf = HallBuildNews.listen((town, line) -> {
			if (town == rig.town()) {
				heard.add(contents(line.getSiblings().getLast()));
			}
		});
		for (BlockPos local : List.of(OUTSIDE_LOCAL, FREE_FLOOR_LOCAL, SPOT_LOCAL)) {
			BlockPos at = rig.origin().offset(local);
			helper.assertTrue(local == OUTSIDE_LOCAL ? !rig.plan().contains(at) : rig.plan().contains(at),
					"control: " + local + " must lie " + (local == OUTSIDE_LOCAL ? "outside" : "inside") + " the hall");
			place(rig.level(), at, placer);
			helper.assertTrue(warnings(heard).isEmpty(), "no warning for a dispatcher at " + local + ", heard "
					+ keys(heard));
			rig.level().setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		BlockPos bench = rig.origin().offset(BENCH_LOCAL);
		BlockState trapdoor = rig.level().getBlockState(bench);
		place(rig.level(), bench, placer);
		deaf.run();
		rig.level().setBlock(bench, trapdoor, Block.UPDATE_CLIENTS);
		helper.assertTrue(warnings(heard).size() == 1, "control: one warning for the bench's trapdoor, heard "
				+ keys(heard));
		rig.release();
		helper.succeed();
	}

	/** Puts a dispatcher at {@code at} as {@code placer} would: the block, then the block's placement hook. */
	private static void place(ServerLevel level, BlockPos at, ServerPlayer placer) {
		BlockState state = ModContent.TOWN_DISPATCHER.get().defaultBlockState()
				.setValue(TownDispatcherBlock.FACING, Direction.SOUTH);
		level.setBlock(at, state, Block.UPDATE_ALL);
		state.getBlock().setPlacedBy(level, at, state, placer, new ItemStack(ModContent.TOWN_DISPATCHER.get()));
	}

	/** The cells of the rig's hall plan the world does not match now, by their place in the hall. */
	private static List<BlockPos> unmatched(Rig rig) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < rig.plan().size(); i++) {
			BlockPos pos = rig.plan().pos(i);
			if (!rig.plan().matches(i, rig.level().getBlockState(pos))) {
				out.add(pos.subtract(rig.origin()));
			}
		}
		return out;
	}

	/** The heard lines that are the dispatcher's warning. */
	private static List<List<TranslatableContents>> warnings(List<List<TranslatableContents>> heard) {
		return heard.stream().filter(line -> !line.isEmpty()
				&& line.getFirst().getKey().equals(HallBuildNews.DISPATCHER_IN_THE_WAY)).toList();
	}

	/** The sentence of a line and what hangs off it. */
	private static List<TranslatableContents> contents(Component sentence) {
		List<TranslatableContents> out = new ArrayList<>();
		if (sentence.getContents() instanceof TranslatableContents t) {
			out.add(t);
		}
		for (Component part : sentence.getSiblings()) {
			if (part.getContents() instanceof TranslatableContents t) {
				out.add(t);
			}
		}
		return out;
	}

	private static List<List<String>> keys(List<List<TranslatableContents>> heard) {
		return heard.stream().map(line -> line.stream().map(TranslatableContents::getKey).toList()).toList();
	}

	private static String argKey(TranslatableContents line) {
		return line.getArgs().length > 0 && line.getArgs()[0] instanceof Component c
				&& c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static String argText(TranslatableContents line) {
		return line.getArgs().length > 0 && line.getArgs()[0] instanceof Component c ? c.getString() : "";
	}
}
