package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The «Stages» section of the settlement core's «Construction» tab (MOD-860, epic decisions R84, R88): for every
 * building with an intact template, the stage its restoration is on — «Town hall: stage 2 of 5, Walls» — with the
 * stage's cells standing, and under it what is missing. When the town has a material source (its dispatcher's buffer,
 * MOD-858) the second line names what the buffer lacks for the stage; without one, what the stage still takes.
 *
 * <p>Read-only for everybody: the controls of a building sit on its row in the buildings section (R95).
 */
public final class StageRows {

	/** The section heading. */
	public static final String SECTION = "settlement.alaindustrial.works.section.stages";
	/** «%1$s: stage %2$s of %3$s, %4$s» — the building, N, M and the stage's name. */
	public static final String LINE = "settlement.alaindustrial.works.stage_line";
	/** «%1$s: every stage stands». */
	public static final String LINE_DONE = "settlement.alaindustrial.works.stage_line_done";
	/** «%1$s/%2$s» — cells of the stage standing and in all. */
	static final String COUNT = "settlement.alaindustrial.works.stage_count";
	/** «Still takes: %s» — the stage's needs, joined. */
	static final String NEEDS = "settlement.alaindustrial.works.stage_needs";
	/** «Materials in stock». */
	static final String STOCKED = "settlement.alaindustrial.works.stage_stocked";
	/** «%1$s blocks with no item» — appended to a stage whose missing cells include such blocks. */
	static final String NO_ITEM = "settlement.alaindustrial.works.stage_no_item";

	/** The keys this section words. */
	static final List<String> KEYS = List.of(SECTION, LINE, LINE_DONE, COUNT, NEEDS, STOCKED, NO_ITEM);

	private StageRows() {
	}

	static void add(ConstructionRows.Context context, List<ConstructionRow> rows) {
		TownWorks works = context.works();
		int settlement = context.town().id();
		Optional<MaterialSource> source = BuildMaterials.source(context.level(), settlement);
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (building.intact().isEmpty()) {
				continue;
			}
			Optional<BuildMaterials.StagedPlan> staged = BuildMaterials.forBuilding(context.level(), settlement, i);
			if (staged.isEmpty()) {
				continue;
			}
			String name = ConstructionRows.name(building);
			Optional<BuildStageRules.Progress> progress = staged.get().progress(context.level());
			if (progress.isEmpty()) {
				rows.add(new ConstructionRow(SECTION, ConstructionRow.Text.of(name),
						Optional.of(ConstructionRow.Text.of(BlockReason.CHUNK_NOT_LOADED)), -1,
						ConstructionRow.Tone.DIM, Optional.empty(), i, 0));
				continue;
			}
			rows.add(row(name, i, progress.get(), source));
		}
	}

	/** The row of one building, from its progress and the town's source. Package-private for the game tests. */
	static ConstructionRow row(String name, int index, BuildStageRules.Progress progress,
			Optional<MaterialSource> source) {
		Optional<BuildStageRules.StageProgress> current = progress.current();
		if (current.isEmpty()) {
			return new ConstructionRow(SECTION, ConstructionRow.Text.of(LINE_DONE, name), Optional.empty(), 100,
					ConstructionRow.Tone.GOOD, Optional.empty(), index, 0);
		}
		BuildStageRules.StageProgress stage = current.get();
		ConstructionRow.Text line = ConstructionRow.Text.of(LINE, name, String.valueOf(progress.currentNumber()),
				String.valueOf(progress.count()), stage.stage().key());
		ConstructionRow.Text count = ConstructionRow.Text.of(COUNT, String.valueOf(stage.standing()),
				String.valueOf(stage.total()));
		int percent = stage.total() == 0 ? 100 : (int) ((long) stage.standing() * 100 / stage.total());
		ConstructionRow.Tone tone = ConstructionRow.Tone.NORMAL;
		Optional<ConstructionRow.Text> detail;
		if (source.isPresent()) {
			List<BuildStageRules.Need> missing = BuildStageRules.shortage(stage, source.get());
			if (missing.isEmpty()) {
				detail = Optional.of(ConstructionRow.Text.of(STOCKED));
				tone = ConstructionRow.Tone.GOOD;
			} else {
				detail = Optional.of(ConstructionRow.Text.of(BuildMaterials.reason(missing)));
				tone = ConstructionRow.Tone.BAD;
			}
		} else if (!stage.needed().isEmpty()) {
			detail = Optional.of(new ConstructionRow.Text(NEEDS, listed(stage.needed()), true));
		} else if (stage.withoutItem() > 0) {
			detail = Optional.of(ConstructionRow.Text.of(NO_ITEM, String.valueOf(stage.withoutItem())));
		} else {
			detail = Optional.empty();
		}
		return new ConstructionRow(SECTION, line, Optional.of(count), percent, tone, detail, index, 0);
	}

	private static List<String> listed(List<BuildStageRules.Need> needs) {
		List<String> args = new ArrayList<>();
		for (int i = 0; i < needs.size() && i < BuildStageRules.MAX_LISTED; i++) {
			args.add(ConstructionRow.Text.counted(needs.get(i).count(), BuildMaterials.describe(needs.get(i).item())));
		}
		if (needs.size() > BuildStageRules.MAX_LISTED) {
			args.add(BuildStageRules.MORE);
		}
		return args;
	}
}
