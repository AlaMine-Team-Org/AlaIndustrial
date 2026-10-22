package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingState;
import dev.alaindustrial.settlement.works.ConstructionRow.Text;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;

/**
 * Works out a dispatcher's {@link DispatcherView} (MOD-858, MOD-860; owner's round 1): the «Task» tab's checklist and
 * the notes of its last step — the town hall's open jobs and why they stand still — and the «Materials» tab's stage
 * header and bill against what the buffer holds.
 */
public final class DispatcherViews {

	private static final String PREFIX = "settlement.alaindustrial.dispatcher.";
	public static final String NO_JOB = PREFIX + "task.no_job";
	public static final String NO_STAGE = PREFIX + "materials.none";

	/** Every key this class words, for the language gate's sake. */
	public static final List<String> KEYS = List.of(NO_JOB, NO_STAGE);

	private DispatcherViews() {
	}

	/** The view of {@code dispatcher}, serving {@code town} or none. */
	public static DispatcherView of(ServerLevel level, TownDispatcherBlockEntity dispatcher,
			Optional<Settlement> town) {
		if (town.isEmpty()) {
			return DispatcherView.EMPTY;
		}
		int id = town.get().id();
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(id);
		OptionalInt hall = TownHall.index(works);
		if (hall.isEmpty()) {
			return DispatcherView.EMPTY;
		}
		int index = hall.getAsInt();
		TownBuilding building = works.buildings().get(index);
		// Restored once is enough (owner's round 4): from then on the drones look after the hall by themselves, so
		// «Start» has nothing left to start.
		boolean restored = building.everRestored() || building.state() == BuildingState.INTACT
				|| building.state() == BuildingState.UPGRADED;
		List<Text> task = new ArrayList<>();
		boolean jobs = false;
		for (WorkJob job : works.queue().jobs()) {
			if (job.building() != index || !job.status().open()) {
				continue;
			}
			jobs = true;
			task.add(Text.of(ConstructionRows.JOB_LINE, ConstructionRows.JOB_TYPE + job.type().serializedName(),
					ConstructionRows.STATUS + job.status().serializedName()));
			job.reason().ifPresent(reason -> task.add(Text.of(reason)));
		}
		if (!jobs && !restored) {
			task.add(Text.of(NO_JOB));
		}
		boolean startable = !restored && building.intact().isPresent()
				&& works.queue().open(WorkJobType.CLEAR, index).isEmpty()
				&& works.queue().open(WorkJobType.BUILD, index).isEmpty();
		String hallTemplate = building.intact().map(Object::toString).orElse("");
		String name = building.name().isEmpty() ? ConstructionRows.UNNAMED : building.name();
		List<Text> materials = new ArrayList<>();
		List<DispatcherView.BillLine> bill = new ArrayList<>();
		materialsOf(level, id, index, name, dispatcher.buffer(), materials, bill);
		return new DispatcherView(task, materials,
				new DispatcherView.Checklist(true, restored, !bill.isEmpty(), jobs, startable, hallTemplate), bill);
	}

	private static void materialsOf(ServerLevel level, int town, int hall, String name, DispatcherBuffer buffer,
			List<Text> header, List<DispatcherView.BillLine> bill) {
		Optional<BuildStageRules.Progress> progress = BuildMaterials.forBuilding(level, town, hall)
				.flatMap(staged -> staged.progress(level));
		Optional<BuildStageRules.StageProgress> current = progress.flatMap(BuildStageRules.Progress::current);
		if (current.isEmpty()) {
			header.add(Text.of(NO_STAGE));
			return;
		}
		header.add(Text.of("settlement.alaindustrial.works.stage_line", name,
				String.valueOf(progress.get().currentNumber()), String.valueOf(progress.get().count()),
				current.get().stage().key()));
		for (BuildStageRules.Need need : current.get().needed()) {
			bill.add(new DispatcherView.BillLine(need.item(), need.count(), buffer.count(need.item())));
		}
	}
}
