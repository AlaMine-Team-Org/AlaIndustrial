package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a town needs, most pressing first (MOD-886, plan 0.1.220 block B2 «the town's pulse»): worked out from the
 * town's own facts — no world is read here. The player sees ONE line «The town needs: …» on the core's tab, the first
 * need of the list, and the short list below it: the town speaks with one voice, not with a request from each worker.
 *
 * <p><b>Order.</b> By {@link Kind}, in its declaration order: energy first (without it the town goes dark), then the
 * materials an order waits for, then a home for a worker, then a building that can be restored, then a workplace.
 * Within a kind, in the order the facts list them.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the ranking and the merged material list ({@code TownNeedsTest});
 * {@code TownPulse} gathers the facts on the server.
 */
public final class TownNeeds {

	private static final String KEY = "settlement.alaindustrial.pulse.";
	/** «The town needs: %s» — the one line on the core's tab. */
	public static final String TOP = KEY + "top";
	/** The core's tab when the town needs nothing. */
	public static final String NOTHING = KEY + "nothing";
	/** «Energy: the core cannot pay its upkeep». */
	public static final String ENERGY = KEY + "need.energy";
	/** «Materials: %s» — joined, the counted items an order lacks. */
	public static final String MATERIALS = KEY + "need.materials";
	/** «A home: %s workers have none». */
	public static final String HOME = KEY + "need.home";
	/** «A home for the next worker». */
	public static final String HOME_FOR_NEXT = KEY + "need.home_next";
	/** «Can be restored: %s» — joined, the buildings' name keys. */
	public static final String RESTORE = KEY + "need.restore";
	/** «Needs mending: %s» — joined, the buildings' name keys. */
	public static final String REPAIR = KEY + "need.repair";
	/** «A workplace: %s workers have none». */
	public static final String WORKPLACE = KEY + "need.workplace";
	/** Every key this class words. */
	public static final List<String> KEYS = List.of(TOP, NOTHING, ENERGY, MATERIALS, HOME, HOME_FOR_NEXT, RESTORE,
			REPAIR, WORKPLACE);

	/** Most items a material need names; the rest wait for the next look. */
	public static final int MAX_ITEMS = 8;

	/** What a need is about; the declaration order is the priority. */
	public enum Kind {
		/** The core cannot pay its upkeep (or holds less than its reserve). */
		ENERGY,
		/** An order of the town waits for materials its dispatcher's buffer lacks. */
		MATERIALS,
		/** A living worker has no home, or the next one would have none. */
		HOME,
		/** A building the town knows the plan of can be restored, or one restored before is to be mended. */
		RESTORE,
		/** A worker has no workplace — only once workplaces exist (block B4); today never. */
		WORKPLACE
	}

	/**
	 * One need: its kind and its sentence.
	 *
	 * @param kind what it is about
	 * @param text the sentence the core's tab shows
	 */
	public record Need(Kind kind, ConstructionRow.Text text) {

		public Need {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(text, "text");
		}
	}

	/**
	 * The town's facts, as the pulse gathered them.
	 *
	 * @param energyShort      the core owes upkeep, or holds less than its reserve
	 * @param missing          the arguments of every «no materials» reason of the town's blocked jobs
	 *                         ({@link ConstructionRow.Text#counted(int, String)} or a plain key), job by job
	 * @param homeless         living workers of the town with no home
	 * @param noHomeForNext    the town may hold more workers but has no free home for the next one
	 * @param restorable       name keys of the buildings that can be ordered restored (or built) now
	 * @param repairable       name keys of the buildings restored before that can be ordered mended now
	 * @param withoutWorkplace living workers with no workplace — 0 until workplaces exist (block B4)
	 */
	public record Facts(boolean energyShort, List<List<String>> missing, int homeless, boolean noHomeForNext,
			List<String> restorable, List<String> repairable, int withoutWorkplace) {

		public Facts {
			missing = missing.stream().map(List::copyOf).toList();
			restorable = List.copyOf(restorable);
			repairable = List.copyOf(repairable);
		}

		/** A town that needs nothing. */
		public static final Facts NONE = new Facts(false, List.of(), 0, false, List.of(), List.of(), 0);
	}

	private TownNeeds() {
	}

	/** The needs of a town with {@code facts}, most pressing first; empty when it needs nothing. */
	public static List<Need> of(Facts facts) {
		List<Need> needs = new ArrayList<>();
		if (facts.energyShort()) {
			needs.add(new Need(Kind.ENERGY, ConstructionRow.Text.of(ENERGY)));
		}
		List<String> items = mergeMaterials(facts.missing());
		if (!items.isEmpty()) {
			needs.add(new Need(Kind.MATERIALS, new ConstructionRow.Text(MATERIALS, items, true)));
		}
		if (facts.homeless() > 0) {
			needs.add(new Need(Kind.HOME, ConstructionRow.Text.of(HOME, String.valueOf(facts.homeless()))));
		} else if (facts.noHomeForNext()) {
			needs.add(new Need(Kind.HOME, ConstructionRow.Text.of(HOME_FOR_NEXT)));
		}
		List<String> restorable = distinct(facts.restorable());
		if (!restorable.isEmpty()) {
			needs.add(new Need(Kind.RESTORE, new ConstructionRow.Text(RESTORE, restorable, true)));
		}
		List<String> repairable = distinct(facts.repairable());
		if (!repairable.isEmpty()) {
			needs.add(new Need(Kind.RESTORE, new ConstructionRow.Text(REPAIR, repairable, true)));
		}
		if (facts.withoutWorkplace() > 0) {
			needs.add(new Need(Kind.WORKPLACE, ConstructionRow.Text.of(WORKPLACE,
					String.valueOf(facts.withoutWorkplace()))));
		}
		return rank(needs);
	}

	/** {@code needs} by priority ({@link Kind} order), the order within a kind kept. */
	public static List<Need> rank(List<Need> needs) {
		List<Need> ranked = new ArrayList<>(needs);
		ranked.sort((a, b) -> Integer.compare(a.kind().ordinal(), b.kind().ordinal()));
		return List.copyOf(ranked);
	}

	/**
	 * The items of several «no materials» reasons as one list: a counted argument ({@code 6*block.minecraft.glass})
	 * of the same item is summed, first seen first; a plain argument is kept once. At most {@link #MAX_ITEMS}.
	 */
	public static List<String> mergeMaterials(List<List<String>> reasons) {
		Map<String, Integer> counted = new LinkedHashMap<>();
		for (List<String> reason : reasons) {
			for (String argument : reason) {
				ConstructionRow.Text.counted(argument).ifPresentOrElse(
						c -> counted.merge(c.key(), c.count(), (was, more) -> was < 0 ? more : was + more),
						() -> counted.putIfAbsent(argument, -1));
			}
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counted.entrySet()) {
			if (out.size() >= MAX_ITEMS) {
				break;
			}
			out.add(entry.getValue() < 0 ? entry.getKey()
					: ConstructionRow.Text.counted(entry.getValue(), entry.getKey()));
		}
		return out;
	}

	private static List<String> distinct(List<String> keys) {
		Set<String> seen = new LinkedHashSet<>(keys);
		return List.copyOf(seen);
	}
}
