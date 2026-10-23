package dev.alaindustrial.gametest;

import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.drone.DroneAirspace;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L2 suite of the drone's way in to a walled-up cell (epic MOD-824, owner's round 5 of 0.1.219): a cell of the hall no
 * route reaches no longer stops the building job «the drone cannot reach x y z» — the drone lifts a block out of its
 * way, puts the cell's block and puts the lifted block back. Replayed by both loaders through the roster (ADR-038).
 */
public final class TownDroneAccessScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownDroneAccessScenarios::aWalledUpCellIsBuiltAndTheWayClosedAgain,
						"town_drone_access_a_walled_up_cell_is_built_and_the_way_closed_again").ticks(3000));

		private Roster() {}
	}

	private TownDroneAccessScenarios() {}

	/**
	 * A floor cell of the hall's foundation is missing under a standing wall, every side of it shut: the wall over it,
	 * the floor beside it, earth outside and under it — the owner's «the drone cannot reach x y z». The building job
	 * never stops for it: it ends done, the floor block back, and every block round the cell as it was before.
	 * Before the fix the job stopped «the drone cannot reach» and stayed stopped.
	 */
	public static void aWalledUpCellIsBuiltAndTheWayClosedAgain(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 1);
		BuildMaterials.StagedPlan staged = BuildMaterials.forBuilding(rig.level(), rig.town(), 0).orElseThrow();
		int cell = -1;
		for (int i = 0; i < rig.plan().size() && cell < 0; i++) {
			BlockPos pos = rig.plan().pos(i);
			int over = rig.plan().indexOf(pos.above());
			if (staged.stage(i).equals(Optional.of(BuildStage.FOUNDATION)) && over >= 0
					&& !rig.plan().expected(over).isAir() && rig.level().getBlockState(pos.above()).isSolidRender()
					&& rig.level().getBlockState(pos).is(rig.plan().expected(i).getBlock())
					&& rig.plan().indexOf(pos.north()) < 0) {
				cell = i;
			}
		}
		helper.assertTrue(cell >= 0, "control: no foundation cell under a wall at the hall's edge");
		BlockPos walled = rig.plan().pos(cell);
		BlockState want = rig.plan().expected(cell);
		rig.level().setBlock(walled, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		Map<BlockPos, BlockState> around = new LinkedHashMap<>();
		for (Direction side : Direction.values()) {
			BlockPos next = walled.relative(side);
			if (DroneAirspace.open(rig.level(), next, rig.level()::isLoaded)) {
				helper.assertTrue(rig.plan().indexOf(next) < 0, "control: an open side of the cell is the hall's: "
						+ next);
				rig.level().setBlock(next, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			around.put(next, rig.level().getBlockState(next));
		}
		rig.dispatcher().buffer().insert(new ItemStack(want.getBlock().asItem(), 4));
		WorkJob job = rig.works().queue().raise(WorkJobType.BUILD, 0).orElseThrow();
		helper.onEachTick(() -> {
			rig.tick();
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.reason().map(BlockReason::key).filter(BlockReason.NO_ROUTE::equals).isEmpty(),
					"the job must not stop for the walled-up cell: " + now);
		});
		helper.succeedWhen(() -> {
			WorkJob now = rig.works().queue().job(job.id()).orElseThrow();
			helper.assertTrue(now.status() == WorkJobStatus.DONE, "the building job must be done, it is " + now
					+ ", nest " + rig.nest(0).status() + ", cell " + rig.level().getBlockState(walled));
			helper.assertTrue(rig.level().getBlockState(walled).is(want.getBlock()), "the walled-up cell must hold "
					+ want + ", it holds " + rig.level().getBlockState(walled));
			around.forEach((pos, was) -> helper.assertTrue(rig.level().getBlockState(pos).equals(was),
					"the block at " + pos + " must be back as " + was + ", it is " + rig.level().getBlockState(pos)));
			rig.release();
		});
	}
}
