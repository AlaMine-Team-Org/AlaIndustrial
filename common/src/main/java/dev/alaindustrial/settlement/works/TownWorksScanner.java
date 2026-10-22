package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.HallBuildNews;
import dev.alaindustrial.settlement.drone.TownMaintenance;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Compares a town's buildings with their intact templates (MOD-857, epic decisions R79, R86) — a little every tick, so
 * a town's whole comparison never lands on one tick (ADR-050 §11: a town costs no more than 0.5–1 ms a tick). The
 * world is the source of truth: a building's state is the answer of the last full pass ({@link BuildingStateRules}),
 * and a block broken since shows on the next one.
 *
 * <p><b>The budget.</b> {@link #CELLS_PER_TICK} cells a tick for the whole town, one building after another, round
 * the list. A town hall counts about 880 cells, so its pass takes some seven ticks; a block read is one chunk lookup
 * and a palette read, so a portion costs a few microseconds. Only a lit core scans (its caller's rule): a hibernating
 * town costs nothing.
 *
 * <p><b>Unloaded chunks.</b> A cell in a chunk nobody keeps loaded ends the pass over its building without a result —
 * the building keeps its last state — and the scanner goes on with the next building (R-277: never load a chunk).
 *
 * <p>The builder drone (MOD-858) reads the same plan ({@link #plan}) and, after it finishes a job, asks for an
 * immediate pass ({@link #measureNow}) instead of waiting for the round.
 */
public final class TownWorksScanner {

	/** Cells one town compares per tick. */
	public static final int CELLS_PER_TICK = 128;

	private TownWorksScanner() {
	}

	/** One tick of town {@code settlementId}'s comparison — called by its lit core. */
	public static void tick(ServerLevel level, int settlementId) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> found = registry.works().find(settlementId);
		if (found.isEmpty() || found.get().buildings().isEmpty()) {
			return;
		}
		TownWorks works = found.get();
		int count = works.buildings().size();
		int budget = CELLS_PER_TICK;
		int skipped = 0;
		while (budget > 0 && skipped < count) {
			int index = Math.floorMod(works.scanBuilding, count);
			Optional<BuildingPlan> plan = plan(level, works, index);
			if (plan.isEmpty() || plan.get().size() == 0) {
				nextBuilding(works);
				skipped++;
				continue;
			}
			skipped = 0;
			BuildingPlan cells = plan.get();
			while (budget > 0 && works.scanCursor < cells.size()) {
				BlockPos pos = cells.pos(works.scanCursor);
				if (!level.isLoaded(pos)) {
					break;
				}
				if (cells.matches(works.scanCursor, level.getBlockState(pos))) {
					works.scanMatched++;
				} else {
					works.scanFailedKeys |= cells.keyMask(works.scanCursor);
				}
				works.scanCursor++;
				budget--;
			}
			if (works.scanCursor >= cells.size()) {
				finish(level, registry, settlementId, works, index, cells, works.scanMatched, works.scanFailedKeys);
				nextBuilding(works);
			} else if (budget > 0) {
				// Stopped on an unloaded chunk: no result for this building this round.
				nextBuilding(works);
				skipped++;
			}
		}
	}

	/**
	 * A full pass over building {@code index} of town {@code settlementId} right now, its result applied — what an
	 * executor asks for after it finished a job, and what a test reads. Empty when the town or the building is unknown,
	 * has nothing to compare with, or a cell lies in an unloaded chunk.
	 */
	public static Optional<BuildingStateRules.Verdict> measureNow(ServerLevel level, int settlementId, int index) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<TownWorks> found = registry.works().find(settlementId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		TownWorks works = found.get();
		Optional<BuildingPlan> plan = plan(level, works, index);
		if (plan.isEmpty()) {
			return Optional.empty();
		}
		BuildingPlan cells = plan.get();
		int matched = 0;
		int failedKeys = 0;
		for (int i = 0; i < cells.size(); i++) {
			BlockPos pos = cells.pos(i);
			if (!level.isLoaded(pos)) {
				return Optional.empty();
			}
			if (cells.matches(i, level.getBlockState(pos))) {
				matched++;
			} else {
				failedKeys |= cells.keyMask(i);
			}
		}
		return finish(level, registry, settlementId, works, index, cells, matched, failedKeys);
	}

	/**
	 * The plan of building {@code index} of town {@code settlementId}: the cells of its intact template in the world,
	 * or empty for a building with no intact template (or no definition any more).
	 */
	public static Optional<BuildingPlan> plan(ServerLevel level, int settlementId, int index) {
		return SettlementRegistry.get(level.getServer()).works().find(settlementId)
				.flatMap(works -> plan(level, works, index));
	}

	private static Optional<BuildingPlan> plan(ServerLevel level, TownWorks works, int index) {
		Optional<Optional<BuildingPlan>> cached = works.cachedPlan(index);
		if (cached.isPresent()) {
			return cached.get();
		}
		Optional<BuildingPlan> built = Optional.empty();
		Optional<TownBuilding> building = works.building(index);
		if (building.isPresent()) {
			Map<Identifier, TownBuildingDef> defs = TownBuildingDef.byTemplate(level.registryAccess());
			TownBuildingDef def = defs.get(building.get().template());
			if (def != null) {
				// The definition decides: a datapack that gave the building an intact template, or a new name, is
				// followed by the town's record.
				TownBuilding current = building.get().withDefinition(def.intact(), def.name());
				works.put(index, current);
				built = BuildingPlan.of(LineStructures.templates(level), current, def, others(level, works, index));
			}
		}
		works.cachePlan(index, built);
		return built;
	}

	/**
	 * Whether a cell lies inside one of the town's buildings other than {@code index} — the box of its structure piece.
	 * A clearing's ring around a building never reaches into its neighbour.
	 */
	private static Predicate<BlockPos> others(ServerLevel level, TownWorks works, int index) {
		List<BoundingBox> boxes = new ArrayList<>();
		StructureTemplateManager templates = LineStructures.templates(level);
		for (int i = 0; i < works.buildings().size(); i++) {
			if (i == index) {
				continue;
			}
			TownBuilding other = works.buildings().get(i);
			templates.get(other.template()).ifPresent(template -> boxes.add(template.getBoundingBox(
					new StructurePlaceSettings().setRotation(other.rotation()), other.origin())));
		}
		return pos -> boxes.stream().anyMatch(box -> box.isInside(pos));
	}

	private static Optional<BuildingStateRules.Verdict> finish(ServerLevel level, SettlementRegistry registry,
			int settlementId, TownWorks works, int index, BuildingPlan cells, int matched, int failedKeys) {
		List<String> missing = new ArrayList<>();
		for (int k = 0; k < cells.keyNames().size(); k++) {
			if ((failedKeys & (1 << k)) != 0) {
				missing.add(cells.keyNames().get(k));
			}
		}
		BuildingStateRules.Measure measure = new BuildingStateRules.Measure(matched, cells.size(), missing);
		Optional<TownBuilding> before = works.building(index);
		Optional<BuildingStateRules.Verdict> verdict = works.applyPass(index, measure, cells.restoredAt());
		if (verdict.isPresent() && verdict.get().justRestored()) {
			TownBuilding building = works.buildings().get(index);
			registry.workers().addToLog(ShiftWorkerBook.LogLine.restored(settlementId,
					new ShiftWorkerBook.BuildingRestored(building.template().toString(), building.name(),
							level.getGameTime())));
			// INFO on purpose (MOD-833): the playtest protocol of the first slice reads these lines from the log.
			Industrialization.LOGGER.info("[ala-towns] settlement {} building {} restored ({} of {} cells)",
					settlementId, building.template(), matched, cells.size());
			HallBuildNews.hallRestored(level, settlementId, index);
		}
		if (verdict.isPresent() && before.isPresent()) {
			TownMaintenance.afterPass(level, settlementId, index, before.get(), verdict.get(), measure);
		}
		return verdict;
	}

	private static void nextBuilding(TownWorks works) {
		works.scanBuilding++;
		works.scanCursor = 0;
		works.scanMatched = 0;
		works.scanFailedKeys = 0;
	}
}
