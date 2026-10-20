package dev.alaindustrial.settlement.shift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rule that writes a shift worker's biography (MOD-854, epic decision R26a): one fragment per slot, chosen by the
 * world seed and the worker's record number, so the same number in the same world always reads the same four lines —
 * between sessions and for every player — whatever order the datapack listed the fragments in.
 *
 * <p><b>Order and compatibility.</b> The slots are filled in reading order (origin, occupation, habit, memory). A
 * fragment fits when none of its tags is forbidden by a fragment already chosen and none of the chosen tags is in
 * its own {@code forbid}. When nothing of a slot fits, the slot falls back to its fragments without that check, so a
 * datapack with too strict a forbid list still gets four lines. A fragment whose substitutions the town cannot supply
 * ({@link TownFacts}) is never chosen.
 *
 * <p><b>Avoided ids.</b> A caller that knows the memories already given in a town may pass them: they are skipped
 * while another memory fits. The result then depends on that set, so a caller that passes one must keep what it got.
 *
 * <p>Minecraft-free on purpose: the L1 lane tests it ({@code BiographyPickTest}). {@link ShiftWorkerBiography} feeds it
 * from the datapack registry and turns its lines into text.
 */
public final class BiographyPick {

	/** The number of slots: origin, occupation, habit, memory — the ordinals of {@code Slot}. */
	public static final int SLOTS = 4;
	/** The memory slot's index. */
	public static final int MEMORY = 3;

	/** Mixed into the key so a biography never walks in step with a look. ASCII {@code ALA-BIOG}. */
	private static final long DOMAIN = 0x414C412D42494F47L;
	/** Mixed in for the choice of the plaque a line quotes. */
	private static final long PLAQUE_DOMAIN = 0x504C415155453031L;

	private BiographyPick() {
	}

	/**
	 * One candidate fragment.
	 *
	 * @param id            its registry id as text
	 * @param slot          0 to {@link #SLOTS} - 1
	 * @param weight        0 or less — never chosen
	 * @param tags          what it says about the worker
	 * @param forbid        tags no other chosen fragment may carry
	 * @param substitutions the serialized names of its substitutions ({@code plaque}, {@code lab},
	 *                      {@code lab_digit_1}…)
	 */
	public record Candidate(String id, int slot, int weight, List<String> tags, List<String> forbid,
			List<String> substitutions) {
	}

	/**
	 * One chosen line.
	 *
	 * @param id   the chosen fragment's id
	 * @param args its substitutions filled in, in argument order: digits as text, written by hand
	 */
	public record Line(String id, List<String> args) {
	}

	/**
	 * The lines of the biography of the worker whose number is {@code numberIndex} in the world with
	 * {@code worldSeed}, in slot order — one per slot that has a candidate the town can fill, so four with the mod's
	 * data.
	 */
	public static List<Line> pick(List<Candidate> candidates, long worldSeed, int numberIndex, TownFacts facts,
			Set<String> avoid) {
		List<Line> lines = new ArrayList<>(SLOTS);
		Set<String> chosenTags = new HashSet<>();
		Set<String> chosenForbid = new HashSet<>();
		for (int slot = 0; slot < SLOTS; slot++) {
			List<Candidate> fillable = new ArrayList<>();
			for (Candidate candidate : candidates) {
				if (candidate.slot() == slot && candidate.weight() > 0 && canFill(candidate, facts)) {
					fillable.add(candidate);
				}
			}
			List<Candidate> fitting = fillable.stream()
					.filter(c -> c.tags().stream().noneMatch(chosenForbid::contains)
							&& c.forbid().stream().noneMatch(chosenTags::contains))
					.toList();
			List<Candidate> pool = fitting.isEmpty() ? fillable : fitting;
			if (slot == MEMORY) {
				List<Candidate> fresh = pool.stream().filter(c -> !avoid.contains(c.id())).toList();
				pool = fresh.isEmpty() ? pool : fresh;
			}
			if (pool.isEmpty()) {
				continue;
			}
			Candidate chosen = roll(pool, key(worldSeed, slot, numberIndex));
			chosenTags.addAll(chosen.tags());
			chosenForbid.addAll(chosen.forbid());
			lines.add(new Line(chosen.id(), args(chosen, worldSeed, numberIndex, facts)));
		}
		return List.copyOf(lines);
	}

	/**
	 * Whether {@code facts} hold every substitution of {@code candidate} — a fragment chosen once and kept by its id
	 * may stop fitting when the town cannot be read any more.
	 */
	public static boolean canFill(Candidate candidate, TownFacts facts) {
		for (String substitution : candidate.substitutions()) {
			boolean known = switch (substitution) {
				case "plaque" -> !facts.plaques().isEmpty();
				case "lab", "lab_digit_1", "lab_digit_2", "lab_digit_3" -> facts.lab().isPresent();
				default -> false;
			};
			if (!known) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The arguments of {@code candidate}'s text for the worker {@code numberIndex} in the world with {@code worldSeed}:
	 * the same digits {@link #pick} gives that line, so a line kept by its fragment id reads the same when rendered
	 * again. Call only when {@link #canFill} holds.
	 */
	public static List<String> args(Candidate candidate, long worldSeed, int numberIndex, TownFacts facts) {
		long key = key(worldSeed, candidate.slot(), numberIndex);
		List<String> args = new ArrayList<>(candidate.substitutions().size());
		for (String substitution : candidate.substitutions()) {
			args.add(switch (substitution) {
				case "plaque" -> digits(facts.plaques().get(
						(int) Math.floorMod(mix(key ^ PLAQUE_DOMAIN), (long) facts.plaques().size())));
				case "lab" -> digits(facts.lab().getAsInt());
				case "lab_digit_1" -> digits(facts.lab().getAsInt()).substring(0, 1);
				case "lab_digit_2" -> digits(facts.lab().getAsInt()).substring(1, 2);
				case "lab_digit_3" -> digits(facts.lab().getAsInt()).substring(2, 3);
				default -> throw new IllegalStateException("unknown substitution " + substitution);
			});
		}
		return List.copyOf(args);
	}

	private static long key(long worldSeed, int slot, int numberIndex) {
		return mix(mix(mix(worldSeed ^ DOMAIN) ^ slot) ^ numberIndex);
	}

	private static Candidate roll(List<Candidate> pool, long key) {
		List<Candidate> sorted = new ArrayList<>(pool);
		sorted.sort(Comparator.comparing(Candidate::id));
		long total = 0;
		for (Candidate candidate : sorted) {
			total += candidate.weight();
		}
		long roll = Math.floorMod(key, total);
		for (Candidate candidate : sorted) {
			roll -= candidate.weight();
			if (roll < 0) {
				return candidate;
			}
		}
		throw new IllegalStateException("the roll ran past the total weight " + total);
	}

	/**
	 * Three digits with leading zeros, written by hand as {@code RecordNumber} does: {@code String.format} would use
	 * the default locale and could print them in another script.
	 */
	static String digits(int number) {
		return new String(new char[] {
				(char) ('0' + number / 100),
				(char) ('0' + number / 10 % 10),
				(char) ('0' + number % 10),
		});
	}

	/** SplitMix64's output function (Stafford's variant 13), the mixer {@code LookPick} uses too. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
