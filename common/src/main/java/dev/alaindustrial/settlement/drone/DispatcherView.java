package dev.alaindustrial.settlement.drone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.settlement.works.ConstructionRow;
import java.util.List;

/**
 * What a dispatcher's screen shows (MOD-858, MOD-860; owner's round 1): the first-time player's checklist on the
 * «Task» tab, the notes under its last step, the current stage's header and bill on the «Materials» tab. Worked out by
 * the dispatcher on its tick and carried to the clients in its update tag only — never saved, a load works it out
 * again.
 *
 * @param task      what the construction's step says: the town hall's open jobs and why each one stands still
 * @param materials the «Materials» tab's header: the hall's current stage, or that nothing is left to build
 * @param checklist the four steps' state
 * @param bill      the current stage's bill, item by item: what it still takes against what the buffer holds
 */
public record DispatcherView(List<ConstructionRow.Text> task, List<ConstructionRow.Text> materials, Checklist checklist,
		List<BillLine> bill) {

	/** Most lines a text list carries. */
	public static final int MAX_LINES = 16;
	/** Most items the bill carries: far more than a stage ever names. */
	public static final int MAX_BILL = 64;

	public static final DispatcherView EMPTY = new DispatcherView(List.of(), List.of(), Checklist.NONE, List.of());

	public DispatcherView {
		task = List.copyOf(task.subList(0, Math.min(MAX_LINES, task.size())));
		materials = List.copyOf(materials.subList(0, Math.min(MAX_LINES, materials.size())));
		bill = List.copyOf(bill.subList(0, Math.min(MAX_BILL, bill.size())));
	}

	/** Whether every item of the bill is in the buffer: the current stage may be built. */
	public boolean billCovered() {
		return bill.stream().allMatch(BillLine::covered);
	}

	/**
	 * The state of the «Task» tab's steps a client cannot see for itself (the plan slot and the nests come from the
	 * slots and the menu's channels).
	 *
	 * @param town      the dispatcher serves a town whose town hall it knows
	 * @param restored  the town hall has been restored: the drones look after it by themselves, «Start» has nothing
	 *                  left to start (owner's round 4) — a repair of it shows as its open jobs
	 * @param stage     the hall has a stage left to build (there is a bill)
	 * @param jobs      the hall has an open clearing or building job in the core's queue
	 * @param startable the owner may start the construction: the hall can be restored and no job is open
	 * @param hall      the id of the hall's intact template, or empty: a plan fits when it names this one (owner's
	 *                  round 2; the drones build by no other plan), and a client compares the plan slot with it at once
	 */
	public record Checklist(boolean town, boolean restored, boolean stage, boolean jobs, boolean startable,
			String hall) {

		public static final Checklist NONE = new Checklist(false, false, false, false, false, "");

		/** Whether a plan naming intact template {@code template} is this hall's. */
		public boolean fits(String template) {
			return !hall.isEmpty() && hall.equals(template);
		}
	}

	/**
	 * One item of the current stage's bill.
	 *
	 * @param item the item's id, «minecraft:bricks»
	 * @param need how many the stage still takes
	 * @param have how many the buffer holds
	 */
	public record BillLine(String item, int need, int have) {

		/** Whether the buffer holds all the stage still takes of this item. */
		public boolean covered() {
			return have >= need;
		}

		public static final Codec<BillLine> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("item").forGetter(BillLine::item),
				Codec.INT.optionalFieldOf("need", 0).forGetter(BillLine::need),
				Codec.INT.optionalFieldOf("have", 0).forGetter(BillLine::have)
		).apply(i, BillLine::new));
	}

	/** Kept out of {@link Checklist} itself, so the plain rules ({@link StartReadiness}) load it without the codecs. */
	private static final Codec<Checklist> CHECKLIST = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("town", false).forGetter(Checklist::town),
			Codec.BOOL.optionalFieldOf("restored", false).forGetter(Checklist::restored),
			Codec.BOOL.optionalFieldOf("stage", false).forGetter(Checklist::stage),
			Codec.BOOL.optionalFieldOf("jobs", false).forGetter(Checklist::jobs),
			Codec.BOOL.optionalFieldOf("startable", false).forGetter(Checklist::startable),
			Codec.STRING.optionalFieldOf("hall", "").forGetter(Checklist::hall)
	).apply(i, Checklist::new));

	private static final Codec<ConstructionRow.Text> TEXT = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(ConstructionRow.Text::key),
			Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(ConstructionRow.Text::args),
			Codec.BOOL.optionalFieldOf("joined", false).forGetter(ConstructionRow.Text::joined)
	).apply(i, ConstructionRow.Text::new));

	public static final Codec<DispatcherView> CODEC = RecordCodecBuilder.create(i -> i.group(
			TEXT.listOf().optionalFieldOf("task", List.of()).forGetter(DispatcherView::task),
			TEXT.listOf().optionalFieldOf("materials", List.of()).forGetter(DispatcherView::materials),
			CHECKLIST.optionalFieldOf("checklist", Checklist.NONE).forGetter(DispatcherView::checklist),
			BillLine.CODEC.listOf().optionalFieldOf("bill", List.of()).forGetter(DispatcherView::bill)
	).apply(i, DispatcherView::new));
}
