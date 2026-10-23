package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.HousePlacement;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHouses;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * L2 suite of laying a new house on free ground of an ALA town (MOD-877; epic MOD-824, decisions R61, R108–R110): an
 * inlet on free ground inside the territory gives a site with no problem, and laying it adds a new house to the town
 * with its clearing and its building queued, in that order; an inlet whose house would reach past the border is
 * refused with the reason. Replayed by both loaders through the roster (ADR-038).
 */
public final class TownNewHouseScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownNewHouseScenarios::freeSiteIsLaidAndPastTheBorderIsRefused,
						"town_new_house_free_site_is_laid").ticks(40));

		private Roster() {}
	}

	private TownNewHouseScenarios() {}

	private static final Identifier STYLE = Industrialization.id("plains");
	private static final Identifier HOUSE_INTACT = Industrialization.id("ala_town/plains/intact/capsule_house_1");
	/**
	 * The inlet of the free site, from the rig's hall: north of it with seven blocks between, inside the LV territory.
	 * The plains house 1 has its inlet in its south wall, so unturned it stands north of the inlet.
	 */
	private static final BlockPos FREE_INLET = new BlockPos(2, 1, -8);
	/** The inlet of the site past the border: the LV square ends 27 blocks north of the hall, the house would not. */
	private static final BlockPos BORDER_INLET = new BlockPos(2, 1, -25);

	/**
	 * @implements MOD-877-SITE — an inlet on free ground inside the territory gives a site with no problem; laying it
	 *     adds a laid house that counts against the limit, with a clearing and then a building queued.
	 * @implements MOD-877-BORDER — a house that would reach past the town's border is refused with «outside the
	 *     town's border».
	 */
	public static void freeSiteIsLaidAndPastTheBorderIsRefused(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = ChunkHold.forTest(helper, level, rig.origin().offset(0, 0, -20));
		Settlement town = SettlementRegistry.get(level.getServer()).find(rig.town()).orElseThrow();
		RestorationPlan plan = new RestorationPlan(STYLE, HOUSE_INTACT);
		clearSite(level, rig.origin());

		BlockPos border = putInlet(level, rig.origin().offset(BORDER_INLET));
		Optional<HousePlacement.Site> outside = HousePlacement.survey(level, town, border, plan, Rotation.NONE);
		helper.assertTrue(outside.isPresent(), "control: the plains house 1 plan must give a site");
		helper.assertTrue(has(outside.get().problems(), HousePlacement.OUTSIDE),
				"a house past the border must be refused for it: " + outside.get().problems());
		helper.assertTrue(HousePlacement.lay(level, town, border, plan, Rotation.NONE)
				.map(refused -> refused.index().isEmpty()).orElse(true), "a refused site must not be laid");

		BlockPos inlet = putInlet(level, rig.origin().offset(FREE_INLET));
		Optional<HousePlacement.Laid> laid = HousePlacement.lay(level, town, inlet, plan, Rotation.NONE);
		helper.assertTrue(laid.isPresent() && laid.get().site().problems().isEmpty(),
				"a free site inside the territory must have no problem: " + laid.map(l -> l.site().problems()));
		helper.assertTrue(laid.get().index().isPresent(), "a free site must be laid");
		int index = laid.get().index().getAsInt();
		TownWorks works = rig.works();
		TownBuilding house = works.buildings().get(index);
		helper.assertTrue(house.laidAt().map(inlet::equals).orElse(false) && TownHouses.isHouse(house),
				"the town must know a new house laid at the inlet: " + house);
		helper.assertTrue(TownHouses.counted(works, index), "a laid house counts against the house limit");
		Optional<WorkJob> clear = works.queue().open(WorkJobType.CLEAR, index);
		Optional<WorkJob> build = works.queue().open(WorkJobType.BUILD, index);
		helper.assertTrue(clear.isPresent() && build.isPresent() && clear.get().id() < build.get().id(),
				"a clearing and then a building must be queued: " + works.queue().jobs());
		helper.assertTrue(HousePlacement.cancellable(works, index), "a house not started may be called off");
		works.withdraw(index);
		helper.assertTrue(!TownHouses.counted(works, index) && works.queue().open(WorkJobType.BUILD, index).isEmpty(),
				"a house called off counts as nothing and keeps no job");
		hold.release();
		rig.release();
		helper.succeed();
	}

	/** Air over the ground north of the hall, where both sites stand. */
	private static void clearSite(ServerLevel level, BlockPos origin) {
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -34), origin.offset(10, 10, -2))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static BlockPos putInlet(ServerLevel level, BlockPos at) {
		level.setBlock(at, ModContent.HOUSE_INLET.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		return at.immutable();
	}

	private static boolean has(List<ConstructionRow.Text> problems, String key) {
		return problems.stream().anyMatch(problem -> problem.key().equals(key));
	}
}
