package dev.alaindustrial.settlement.works;

import dev.alaindustrial.settlement.drone.TownHall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The cards of the core's «Construction» tab (owner's round 3 of 0.1.219, R3-3): one {@link BuildingTile} per
 * building the tab lists, in the order of its rows — the section, the picture, the status chip, the resident and the
 * building's open jobs.
 */
public final class BuildingTiles {

	/** Most jobs a card lists: as many as the packet carries ({@code TownWorksPayload.MAX_JOBS}). */
	static final int MAX_JOBS = 32;

	private BuildingTiles() {
	}

	/** The tiles of every building of {@code context}'s town the buildings section lists. */
	public static List<BuildingTile> collect(ConstructionRows.Context context) {
		TownWorks works = context.works();
		List<BuildingTile> out = new ArrayList<>();
		if (!works.buildingsKnown()) {
			return out;
		}
		OptionalInt hall = TownHall.index(works);
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (building.withdrawn()) {
				continue;
			}
			BuildingTile.Group group = hall.isPresent() && hall.getAsInt() == i ? BuildingTile.Group.HALL
					: TownHouses.isHouse(building) ? BuildingTile.Group.HOUSE : BuildingTile.Group.OTHER;
			List<Integer> jobs = new ArrayList<>();
			for (WorkJob job : works.queue().jobs()) {
				if (job.building() == i && job.status().open() && jobs.size() < MAX_JOBS) {
					jobs.add(job.id());
				}
			}
			out.add(new BuildingTile(i, BuildingTile.iconOf(building.intact().orElse(building.template()).getPath()),
					group, chip(context, i),
					BuildingCard.residentLine(context.level(), context.town().id(), works, i), jobs));
		}
		return out;
	}

	/** The status chip of building {@code i}. */
	static BuildingTile.Chip chip(ConstructionRows.Context context, int i) {
		TownWorks works = context.works();
		TownBuilding building = works.buildings().get(i);
		if (TownOrders.ordered(works, i)) {
			return stalled(context, i) ? BuildingTile.Chip.WAITING : BuildingTile.Chip.WORKING;
		}
		if (building.state() == BuildingState.INTACT || building.state() == BuildingState.UPGRADED) {
			return BuildingTile.Chip.DONE;
		}
		if (building.everRestored()) {
			return BuildingTile.Chip.DAMAGED;
		}
		if (building.intact().isPresent()) {
			Optional<String> wait = TownOrders.blocker(context.town(), works, i);
			if (wait.isPresent()) {
				return TownOrders.NO_PLAN.equals(wait.get()) ? BuildingTile.Chip.NO_PLAN : BuildingTile.Chip.LOCKED;
			}
		}
		return BuildingTile.Chip.RUIN;
	}

	/** Whether the order of building {@code i} stands still: a blocked job, or its stage short of materials. */
	static boolean stalled(ConstructionRows.Context context, int i) {
		boolean blocked = context.works().queue().jobs().stream()
				.anyMatch(job -> job.building() == i && job.status() == WorkJobStatus.BLOCKED);
		return blocked || !shortage(context, i).isEmpty();
	}

	/**
	 * What the town's material source lacks for the stage building {@code i} is on — empty when nothing is missing,
	 * the town has no source, or the building has no stages (or none loaded).
	 */
	static List<BuildStageRules.Need> shortage(ConstructionRows.Context context, int i) {
		Optional<MaterialSource> source = BuildMaterials.source(context.level(), context.town().id());
		if (source.isEmpty()) {
			return List.of();
		}
		return BuildMaterials.forBuilding(context.level(), context.town().id(), i)
				.flatMap(staged -> staged.progress(context.level()))
				.flatMap(BuildStageRules.Progress::current)
				.map(stage -> BuildStageRules.shortage(stage, source.get()))
				.orElse(List.of());
	}
}
