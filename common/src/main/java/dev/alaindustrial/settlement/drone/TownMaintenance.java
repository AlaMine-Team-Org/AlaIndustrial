package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.BuildingStateRules;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A restored building looks after itself (owner's round 4 of stage 6): once a building of a town has been restored,
 * damage to it — a creeper, a player breaking its blocks — queues a repair by itself, and the town's drones take it as
 * they take any job. Nobody presses a button: the dispatcher's «Start» is for the first restoration only.
 *
 * <p><b>When.</b> After a full pass over the building ({@link TownWorksScanner})
 * that finds it newly damaged ({@link BuildingStateRules#newlyDamaged}) and no clearing or building job open on it.
 * The repair is a clearing — only when the building's cells or the ring around it hold something to take away — and
 * a building job, both marked {@linkplain WorkJob#repair() repair}: their stages are not news. The town hall's restorer
 * hears «the town hall is damaged, the drones mend it» once, and «the town hall is mended» when a pass finds it whole
 * again ({@link HallBuildNews}); a repair job still waiting then is called off.
 *
 * <p>Materials come from the dispatcher's buffer as for any building job; a short buffer stops the job with the usual
 * reason.
 */
public final class TownMaintenance {

	private TownMaintenance() {
	}

	/**
	 * A full pass over building {@code index} of town {@code town} has just been applied: {@code before} is the
	 * building as it was, {@code verdict} and {@code measure} the pass.
	 */
	public static void afterPass(ServerLevel level, int town, int index, TownBuilding before,
			BuildingStateRules.Verdict verdict, BuildingStateRules.Measure measure) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		if (found.isEmpty()) {
			return;
		}
		TownWorks works = found.get();
		int previousMissing = before.measured() ? before.total() - before.matched() : before.allowedMissing();
		if (BuildingStateRules.newlyDamaged(before.before(), previousMissing, verdict, measure)
				&& works.queue().open(WorkJobType.CLEAR, index).isEmpty()
				&& works.queue().open(WorkJobType.BUILD, index).isEmpty()) {
			raiseRepair(level, town, index);
		} else if (BuildingStateRules.mended(before.before(), before.state(), verdict)) {
			// Only a repair that was announced is announced done: damage the first restoration's own job put right
			// meanwhile said nothing when it came, and says nothing when it goes.
			boolean repaired = works.queue().jobs().stream().filter(job -> job.building() == index)
					.reduce((a, b) -> b).map(WorkJob::repair).orElse(false);
			for (WorkJob job : List.copyOf(works.queue().jobs())) {
				if (job.building() == index && job.repair() && job.status().open()
						&& job.status() != WorkJobStatus.TAKEN) {
					works.queue().cancel(job.id());
				}
			}
			if (repaired) {
				HallBuildNews.hallMended(level, town, index);
			}
		}
	}

	/**
	 * Raises a repair of building {@code index}: a clearing when there is something to clear, a building job when there
	 * is something a drone can put back (a block with data where air belongs is not). The restorer hears of it once.
	 * Returns whether anything was raised.
	 */
	public static boolean raiseRepair(ServerLevel level, int town, int index) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, town, index);
		if (plan.isEmpty()) {
			return false;
		}
		boolean raised = false;
		if (needsClearing(level, plan.get())) {
			raised |= raise(works, town, index, WorkJobType.CLEAR);
		}
		if (needsBuilding(level, plan.get())) {
			raised |= raise(works, town, index, WorkJobType.BUILD);
		}
		if (raised) {
			HallBuildNews.hallDamaged(level, town, index);
		}
		return raised;
	}

	/**
	 * Raises one job of {@code type} on building {@code index} for the town's owner (the core's «Construction» tab): a
	 * repair when the building has been restored before — its stages are no news, and the first repair job tells the
	 * restorer the building is being mended.
	 */
	public static Optional<WorkJob> raiseByOwner(ServerLevel level, int town, int index, WorkJobType type) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		boolean repair = works.building(index).map(TownBuilding::everRestored).orElse(false);
		boolean repairOpen = works.queue().jobs().stream()
				.anyMatch(job -> job.building() == index && job.repair() && job.status().open());
		Optional<WorkJob> job = works.queue().raise(type, index, repair);
		if (job.isPresent() && repair && !repairOpen) {
			HallBuildNews.hallDamaged(level, town, index);
		}
		return job;
	}

	private static boolean raise(TownWorks works, int town, int index, WorkJobType type) {
		Optional<WorkJob> job = works.queue().raise(type, index, true);
		job.ifPresent(raised -> Industrialization.LOGGER.info(
				"[ala-towns] settlement {}: building {} damaged, {} repair job {} raised", town, index,
				type.serializedName(), raised.id()));
		return job.isPresent();
	}

	/**
	 * Whether a clearing of {@code plan} has anything to take away: a cell of the building a clearing drone would
	 * empty, or terrain in the ring around it. Cells in unloaded chunks are not looked at.
	 */
	public static boolean needsClearing(ServerLevel level, BuildingPlan plan) {
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (level.isLoaded(pos) && DroneCellRules.decide(Job.CLEAR,
					DroneCellSurvey.survey(plan, i, level.getBlockState(pos))) == Action.REMOVE) {
				return true;
			}
		}
		for (int r = 0; r < plan.ringSize(); r++) {
			BlockPos pos = plan.ringPos(r);
			if (level.isLoaded(pos) && DroneCellSurvey.ringClearable(level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a building job on {@code plan} has anything to do: a cell to fill, to empty, or stopped by something in
	 * the way. Cells in unloaded chunks are not looked at.
	 */
	public static boolean needsBuilding(ServerLevel level, BuildingPlan plan) {
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (level.isLoaded(pos) && DroneCellRules.decide(Job.BUILD,
					DroneCellSurvey.survey(plan, i, level.getBlockState(pos))) != Action.NONE) {
				return true;
			}
		}
		return false;
	}
}
