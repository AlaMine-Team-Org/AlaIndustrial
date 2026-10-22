package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The rules of building in stages (MOD-860, epic decision R84): which stage a building is on, what that stage still
 * needs, whether the builder may go on, and when an item leaves the buffer.
 *
 * <p><b>Progress is the difference between the plan and the world,</b> worked out again each time: a cell counts as
 * standing when the world holds its block. A block broken since simply comes back into the work, and a save keeps no
 * stage number to drift from the world.
 *
 * <p><b>The rules.</b>
 * <ul>
 *   <li>A building's stages are the {@link BuildStage}s that hold at least one of its cells, in that order: «stage N
 *       of M».</li>
 *   <li>The current stage is the first one with a cell still missing; a broken block in an earlier stage takes the
 *       building back to it.</li>
 *   <li>A stage closes as a whole: the builder starts and goes on with the current stage only while the source holds
 *       every item that stage still needs ({@link #decide}). Short of anything, the job waits with
 *       {@link BlockReason#noMaterials} naming what is missing, and goes on by itself the next time it asks and the
 *       items are there.</li>
 *   <li>An item leaves the source at the moment its block is placed ({@link #consumeOnPlace}).</li>
 *   <li>A cell whose block has no item (a liquid, a block the game hands out no item for) costs nothing: the stage
 *       counts it and the builder places it free ({@link StageProgress#withoutItem()}). A cell whose item is paid by
 *       its other half (a door's upper half, a bed's head) costs nothing either and is not noted.</li>
 * </ul>
 *
 * <p>Minecraft-free on purpose: the L1 lane checks every rule ({@code BuildStageRulesTest}).
 */
public final class BuildStageRules {

	/** The item of a cell whose block has none. */
	public static final String NO_ITEM = "";
	/** Most missing items a reason names; the rest show as {@link #MORE}. */
	public static final int MAX_LISTED = 5;
	/** What stands for the items a reason does not name. */
	public static final String MORE = "…";

	private BuildStageRules() {
	}

	/**
	 * One cell of a building's plan, as the stage rules see it.
	 *
	 * @param stage    the stage it belongs to
	 * @param item     the registry id of the item its block takes, or {@link #NO_ITEM}
	 * @param count    how many of that item (two for a double slab, none for a door's upper half)
	 * @param standing whether the world already holds its block
	 */
	public record Cell(BuildStage stage, String item, int count, boolean standing) {

		public Cell {
			Objects.requireNonNull(stage, "stage");
			Objects.requireNonNull(item, "item");
			count = item.isEmpty() ? 0 : Math.max(0, count);
		}

		/** Whether placing it takes anything out of the source. */
		public boolean costs() {
			return count > 0;
		}
	}

	/**
	 * Items of one kind.
	 *
	 * @param item  the registry id
	 * @param count how many
	 */
	public record Need(String item, int count) {

		public Need {
			Objects.requireNonNull(item, "item");
		}
	}

	/**
	 * Where one stage stands.
	 *
	 * @param stage       the stage
	 * @param total       its cells
	 * @param standing    those the world already holds
	 * @param needed      the items its missing cells take, most first
	 * @param withoutItem missing cells whose block takes no item
	 */
	public record StageProgress(BuildStage stage, int total, int standing, List<Need> needed, int withoutItem) {

		public StageProgress {
			needed = List.copyOf(needed);
		}

		/** Whether every cell of the stage stands. */
		public boolean done() {
			return standing >= total;
		}

		/** The items its missing cells take, all kinds together. */
		public int neededItems() {
			int sum = 0;
			for (Need need : needed) {
				sum += need.count();
			}
			return sum;
		}
	}

	/**
	 * Where a whole building stands, stage by stage.
	 *
	 * @param stages its stages that hold any cell, in the order of the work
	 */
	public record Progress(List<StageProgress> stages) {

		public Progress {
			stages = List.copyOf(stages);
		}

		/** M of «stage N of M». */
		public int count() {
			return stages.size();
		}

		/** What every missing cell of the building takes, all stages together, most first: its material list. */
		public List<Need> needed() {
			Map<String, Integer> all = new LinkedHashMap<>();
			for (StageProgress stage : stages) {
				for (Need need : stage.needed()) {
					all.merge(need.item(), need.count(), Integer::sum);
				}
			}
			return sorted(all);
		}

		/** The first stage with a cell missing, or empty once all stand. */
		public Optional<StageProgress> current() {
			for (StageProgress stage : stages) {
				if (!stage.done()) {
					return Optional.of(stage);
				}
			}
			return Optional.empty();
		}

		/** N of «stage N of M», counted from 1; {@link #count()} once everything stands. */
		public int currentNumber() {
			for (int i = 0; i < stages.size(); i++) {
				if (!stages.get(i).done()) {
					return i + 1;
				}
			}
			return stages.size();
		}

		/** Whether every stage stands — a building with no cells at all counts as done. */
		public boolean done() {
			return current().isEmpty();
		}
	}

	/** What the builder does next. */
	public enum Verdict {
		/** Place the next missing cell of the current stage. */
		BUILD,
		/** Wait: the source lacks items the current stage needs. */
		WAIT,
		/** Nothing is missing. */
		DONE
	}

	/**
	 * The answer of {@link #decide}.
	 *
	 * @param verdict what to do
	 * @param stage   the current stage, empty when done
	 * @param missing what the source lacks for it, most first; empty unless waiting
	 */
	public record Decision(Verdict verdict, Optional<BuildStage> stage, List<Need> missing) {

		public Decision {
			missing = List.copyOf(missing);
		}
	}

	/** Where a building with {@code cells} stands. */
	public static Progress progress(Collection<Cell> cells) {
		Map<BuildStage, int[]> counts = new EnumMap<>(BuildStage.class);
		Map<BuildStage, Map<String, Integer>> needs = new EnumMap<>(BuildStage.class);
		for (Cell cell : cells) {
			int[] c = counts.computeIfAbsent(cell.stage(), s -> new int[3]);
			c[0]++;
			if (cell.standing()) {
				c[1]++;
			} else if (cell.costs()) {
				needs.computeIfAbsent(cell.stage(), s -> new LinkedHashMap<>()).merge(cell.item(), cell.count(),
						Integer::sum);
			} else if (cell.item().isEmpty()) {
				c[2]++;
			}
		}
		List<StageProgress> stages = new ArrayList<>();
		for (BuildStage stage : BuildStage.values()) {
			int[] c = counts.get(stage);
			if (c == null) {
				continue;
			}
			stages.add(new StageProgress(stage, c[0], c[1], sorted(needs.getOrDefault(stage, Map.of())), c[2]));
		}
		return new Progress(stages);
	}

	/** What {@code source} lacks of what {@code stage} still needs, most missing first. */
	public static List<Need> shortage(StageProgress stage, MaterialSource source) {
		Map<String, Integer> missing = new LinkedHashMap<>();
		for (Need need : stage.needed()) {
			int lack = need.count() - Math.max(0, source.count(need.item()));
			if (lack > 0) {
				missing.put(need.item(), lack);
			}
		}
		return sorted(missing);
	}

	/**
	 * What the builder does next: build the current stage when the source holds all it still needs, wait naming what is
	 * missing when it does not, nothing once every stage stands.
	 */
	public static Decision decide(Progress progress, MaterialSource source) {
		Optional<StageProgress> current = progress.current();
		if (current.isEmpty()) {
			return new Decision(Verdict.DONE, Optional.empty(), List.of());
		}
		List<Need> missing = shortage(current.get(), source);
		return new Decision(missing.isEmpty() ? Verdict.BUILD : Verdict.WAIT, Optional.of(current.get().stage()),
				missing);
	}

	/** Whether the builder may place {@code cell} now: missing, of the current stage, and the stage is stocked. */
	public static boolean mayPlace(Progress progress, MaterialSource source, Cell cell) {
		if (cell.standing()) {
			return false;
		}
		Decision decision = decide(progress, source);
		return decision.verdict() == Verdict.BUILD && decision.stage().equals(Optional.of(cell.stage()));
	}

	/**
	 * Takes what {@code cell} costs out of {@code source}, at the moment its block is placed — all or nothing. Returns
	 * whether the block may stand: true for a cell that costs nothing, false when the source no longer holds the items
	 * (somebody took them since the check); the caller then puts the cell back to what it was.
	 */
	public static boolean consumeOnPlace(MaterialSource source, Cell cell) {
		return !cell.costs() || source.consume(cell.item(), cell.count());
	}

	/**
	 * «No materials: Glass ×6, Bricks ×12» — the reason a waiting job shows. {@code describe} turns an item's registry
	 * id into its translation key; at most {@link #MAX_LISTED} kinds are named, the rest stand as {@link #MORE}.
	 */
	public static BlockReason reason(List<Need> missing, UnaryOperator<String> describe) {
		List<String> args = new ArrayList<>();
		for (int i = 0; i < missing.size() && i < MAX_LISTED; i++) {
			Need need = missing.get(i);
			args.add(ConstructionRow.Text.counted(need.count(), describe.apply(need.item())));
		}
		if (missing.size() > MAX_LISTED) {
			args.add(MORE);
		}
		return BlockReason.noMaterials(args);
	}

	private static List<Need> sorted(Map<String, Integer> byItem) {
		List<Need> out = new ArrayList<>();
		byItem.forEach((item, count) -> out.add(new Need(item, count)));
		out.sort(Comparator.comparingInt(Need::count).reversed().thenComparing(Need::item));
		return out;
	}
}
