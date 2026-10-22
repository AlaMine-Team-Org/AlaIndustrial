package dev.alaindustrial.settlement.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The rules of the town hall plan sheets (MOD-859, decisions R83 and R92 of MOD-824), with no Minecraft types so a
 * plain unit test runs them and the dispatcher (MOD-858) only calls them and shows the answer.
 *
 * <p><b>A plan is three sheets of one style.</b> Sheets 1, 2 and 3, each exactly once, all of one settlement style.
 * The plan belongs to the style, not to the town: sheets from different towns of the same style put together the
 * same plan. A plan is assembled in a settlement's dispatcher, so the sheets must also be of that settlement's
 * style — a plains hall plan does not build a desert hall.
 *
 * <p><b>Ids as strings.</b> A style is the id of a {@code alaindustrial:settlement_style} entry, {@code ns:path}; the
 * plan names the intact template of its style's building, {@code ns:ala_town/<path>/intact/<building>} (MOD-856),
 * and nothing else — never block contents (research of stage 6, §7: a plan that carried contents duplicated items).
 */
public final class PlanSheetRules {

	/** Sheets a town hall plan is put together from. */
	public static final int SHEETS = 3;

	/**
	 * The settlement styles the mod ships ({@code data/alaindustrial/alaindustrial/settlement_style/}), in the creative
	 * tab's order: their sheets and plans are listed there (owner's round 2). A datapack's style is found in its towns
	 * and crafted, not listed. {@code PlanSheetRulesTest} keeps this list equal to the shipped files.
	 */
	public static final List<String> BUILT_IN_STYLES = List.of("alaindustrial:plains", "alaindustrial:desert");

	/** The building the hall plan restores: its intact template's last path segment. */
	public static final String TOWN_HALL = "town_hall";

	/** Prefix of every reason key; the full key is this plus the reason's own suffix. */
	public static final String REASON_PREFIX = "settlement.alaindustrial.plan.reason.";

	/** Why a set of sheets does not make a plan; each has its translation key. */
	public enum Reason {
		/** No sheet at all. */
		NO_SHEETS("no_sheets"),
		/** A sheet with no style, or with a number outside 1..{@value #SHEETS}: a blank from the creative tab. */
		UNMARKED_SHEET("unmarked_sheet"),
		/** Sheets of different styles. */
		MIXED_STYLES("mixed_styles"),
		/** The same sheet number more than once. */
		DUPLICATE_SHEET("duplicate_sheet"),
		/** A sheet number is missing; {@link Result#missing()} names which. */
		MISSING_SHEET("missing_sheet"),
		/** The sheets are of another style than the settlement whose dispatcher assembles them. */
		WRONG_STYLE("wrong_style");

		private final String suffix;

		Reason(String suffix) {
			this.suffix = suffix;
		}

		/** The translation key of this reason. */
		public String key() {
			return REASON_PREFIX + suffix;
		}
	}

	/** One sheet as the rules see it: its style id ({@code null} for a blank) and its number. */
	public record Sheet(String style, int number) {
	}

	/** The plan the sheets make: the style and the intact template it points at. */
	public record Plan(String style, String template) {
	}

	/**
	 * What {@link #assemble} answers: a plan, or the reason there is none. {@code missing} lists the sheet numbers
	 * that are not there (filled for {@link Reason#MISSING_SHEET} only), in ascending order.
	 */
	public record Result(Plan plan, Reason reason, List<Integer> missing) {

		public Result {
			missing = List.copyOf(missing);
		}

		static Result of(Plan plan) {
			return new Result(plan, null, List.of());
		}

		static Result refused(Reason reason) {
			return new Result(null, reason, List.of());
		}

		/** Whether the sheets made a plan. */
		public boolean ok() {
			return plan != null;
		}

		/** The reason's translation key, or {@code null} when the sheets made a plan. */
		public String reasonKey() {
			return reason == null ? null : reason.key();
		}
	}

	private PlanSheetRules() {
	}

	/**
	 * Whether {@code sheets} make a town hall plan in a settlement of {@code settlementStyle}. A {@code null}
	 * settlement style (a settlement whose style is not known) skips only the last check. The checks run in a fixed
	 * order, so the reason shown is the first that applies: no sheets, a blank sheet, mixed styles, a repeated
	 * number, a missing number, the settlement's style.
	 */
	public static Result assemble(List<Sheet> sheets, String settlementStyle) {
		if (sheets.isEmpty()) {
			return Result.refused(Reason.NO_SHEETS);
		}
		for (Sheet sheet : sheets) {
			if (sheet.style() == null || sheet.style().isEmpty() || sheet.number() < 1 || sheet.number() > SHEETS) {
				return Result.refused(Reason.UNMARKED_SHEET);
			}
		}
		String style = sheets.getFirst().style();
		for (Sheet sheet : sheets) {
			if (!style.equals(sheet.style())) {
				return Result.refused(Reason.MIXED_STYLES);
			}
		}
		TreeSet<Integer> numbers = new TreeSet<>();
		for (Sheet sheet : sheets) {
			if (!numbers.add(sheet.number())) {
				return Result.refused(Reason.DUPLICATE_SHEET);
			}
		}
		List<Integer> missing = new ArrayList<>();
		for (int n = 1; n <= SHEETS; n++) {
			if (!numbers.contains(n)) {
				missing.add(n);
			}
		}
		if (!missing.isEmpty()) {
			return new Result(null, Reason.MISSING_SHEET, missing);
		}
		if (settlementStyle != null && !style.equals(settlementStyle)) {
			return Result.refused(Reason.WRONG_STYLE);
		}
		return Result.of(new Plan(style, intactTemplate(style, TOWN_HALL)));
	}

	/** Every sheet found: one bit per sheet number, bit 0 for sheet 1. */
	public static final int ALL_FOUND = (1 << SHEETS) - 1;

	/**
	 * The sheets {@code sheets} show as found for a town of {@code townStyle} (MOD-859): one bit per sheet number, bit
	 * 0 for sheet 1. A sheet counts when it is marked and of the town's style; a blank or a sheet of another style
	 * finds nothing. A {@code null} or empty town style counts a marked sheet of any style.
	 */
	public static int foundMask(List<Sheet> sheets, String townStyle) {
		boolean anyStyle = townStyle == null || townStyle.isEmpty();
		int mask = 0;
		for (Sheet sheet : sheets) {
			if (sheet.style() == null || sheet.style().isEmpty() || sheet.number() < 1 || sheet.number() > SHEETS) {
				continue;
			}
			if (anyStyle || sheet.style().equals(townStyle)) {
				mask |= 1 << (sheet.number() - 1);
			}
		}
		return mask;
	}

	/** Whether sheet {@code number} is among the found ones of {@code mask}. */
	public static boolean found(int mask, int number) {
		return number >= 1 && number <= SHEETS && (mask & (1 << (number - 1))) != 0;
	}

	/**
	 * The id of the intact template of {@code building} in {@code style}: {@code ns:ala_town/<path>/intact/<building>}
	 * for the style {@code ns:path} (MOD-856). A style with no namespace is the mod's.
	 */
	public static String intactTemplate(String style, String building) {
		String[] id = split(style);
		return id[0] + ":ala_town/" + id[1] + "/intact/" + building;
	}

	/**
	 * The id of the loot table that holds sheet {@code sheet} of {@code style}:
	 * {@code ns:chests/ala_town/<path>/plan_sheet_<sheet>}. A style from a datapack brings its three tables in its own
	 * namespace.
	 */
	public static String lootTable(String style, int sheet) {
		String[] id = split(style);
		return id[0] + ":chests/ala_town/" + id[1] + "/plan_sheet_" + sheet;
	}

	/**
	 * Which of {@code houses} candidate houses holds sheet 2, in [0, houses): a hash of the world seed and the town's
	 * anchor cell, so every chunk of the town picks the same house and two towns of one world pick independently.
	 *
	 * @throws IllegalArgumentException when there is no house to pick
	 */
	public static int houseIndex(long worldSeed, int anchorX, int anchorY, int anchorZ, int houses) {
		if (houses <= 0) {
			throw new IllegalArgumentException("no house to put sheet 2 in");
		}
		long hash = mix(worldSeed ^ HOUSE_DOMAIN);
		hash = mix(hash ^ anchorX);
		hash = mix(hash ^ anchorY);
		hash = mix(hash ^ anchorZ);
		return (int) Math.floorMod(hash, (long) houses);
	}

	/** Keeps the house pick apart from every other hash of the world seed. */
	private static final long HOUSE_DOMAIN = 0x5048_4545_5432_4C4EL;

	/** SplitMix64's finaliser. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static String[] split(String style) {
		Objects.requireNonNull(style, "style");
		int colon = style.indexOf(':');
		return colon < 0 ? new String[] {"alaindustrial", style}
				: new String[] {style.substring(0, colon), style.substring(colon + 1)};
	}
}
