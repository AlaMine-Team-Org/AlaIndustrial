package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.works.BuildingPlan;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a town's dispatcher may stand in its hall (MOD-891; epic decision R158). The intact hall keeps one cell for it
 * ({@link BuildingPlan#dispatcherSpot}): the plan wants air there and a dispatcher there counts as in place, so it
 * never stops the hall's restoration. Anywhere the plan wants air it stands in nobody's way either. On a cell where
 * the plan wants a block the building job stops «in the way» once the drones reach that stage (R93, R143) — so the
 * player who puts it there hears it at once, with where its place is, instead of finding the hall stuck at 97 %.
 */
public final class DispatcherSpot {

	private DispatcherSpot() {
	}

	/**
	 * The plan cell a dispatcher in {@code dispatcher} at {@code pos} stands in the way of: one the building job would
	 * stop at, by the drone's own rule ({@link DroneCellRules}). Empty off the plan, on air the plan keeps, on the
	 * dispatcher's cell, and on a cell whose block no drone puts (the core, the capsule, a nest).
	 */
	public static OptionalInt inTheWay(BuildingPlan plan, BlockPos pos, BlockState dispatcher) {
		int index = plan.indexOf(pos);
		if (index < 0 || DroneCellRules.decide(DroneCellRules.Job.BUILD,
				DroneCellSurvey.survey(plan, index, dispatcher)) != DroneCellRules.Action.OBSTRUCTED) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(index);
	}

	/**
	 * {@code placer} has just put the dispatcher {@code dispatcher} at {@code pos} in town {@code town}: on a cell of
	 * the town's hall where the plan wants a block they hear one chat line, what the plan puts there and where the
	 * dispatcher's place is. Nothing outside the hall, nothing on a free cell.
	 */
	public static void placed(ServerLevel level, int town, BlockPos pos, BlockState dispatcher, ServerPlayer placer) {
		Optional<BuildingPlan> plan = TownHall.plan(level, town);
		if (plan.isEmpty()) {
			return;
		}
		OptionalInt index = inTheWay(plan.get(), pos, dispatcher);
		if (index.isPresent()) {
			HallBuildNews.dispatcherInTheWay(level, town, placer, plan.get().expected(index.getAsInt()),
					plan.get().dispatcherSpot());
		}
	}
}
