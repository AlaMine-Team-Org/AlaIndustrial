package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.shift.BiographyPick.Candidate;
import dev.alaindustrial.settlement.shift.BiographyPick.Line;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the biography rule (MOD-854, epic decision R26a) over the mod's real fragment files: deterministic
 * from the seed and the number, blind to the datapack's order, one line per slot, the forbid rule kept, the town's
 * digits quoted as they are and a fragment never chosen for a town that cannot fill it; and every fragment has both
 * forms in the English lang file with as many arguments as it has substitutions.
 *
 * @implements MOD-854-BIO — a worker's biography follows from its number and the seed; quoted digits are its town's.
 */
class BiographyPickTest {

	private static final String DATA = "data/alaindustrial/alaindustrial/shift_worker_biography";
	private static final String EN_US = "assets/alaindustrial/lang/en_us.json";
	private static final String KEY_PREFIX = "settlement.alaindustrial.bio.";
	private static final List<String> SLOTS = List.of("origin", "occupation", "habit", "memory");

	private static final Pattern STRING_FIELD = Pattern.compile("\"(slot|key|clue)\"\\s*:\\s*\"([^\"]*)\"");
	private static final Pattern LIST_FIELD = Pattern.compile("\"(tags|forbid|substitutions)\"\\s*:\\s*\\[([^]]*)]");
	private static final Pattern WEIGHT = Pattern.compile("\"weight\"\\s*:\\s*(-?\\d+)");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
	private static final Pattern INDEXED_ARG = Pattern.compile("%(\\d+)\\$s");

	/** A fragment file as read: its candidate view and its translation key. */
	private record Fragment(Candidate candidate, String key, boolean clue) {
	}

	private static final List<Fragment> FRAGMENTS = readFragments();
	private static final List<Candidate> BUILT_IN = FRAGMENTS.stream().map(Fragment::candidate).toList();
	private static final TownFacts FULL = new TownFacts(List.of(214, 37, 905), OptionalInt.of(305));

	@Property
	void sameNumberAndSeedGiveTheSameBiography(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		assertEquals(BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of()),
				BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of()));
	}

	@Property
	void listingOrderDoesNotMatter(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index,
			@ForAll long shuffle) {
		List<Candidate> shuffled = new ArrayList<>(BUILT_IN);
		Collections.shuffle(shuffled, new Random(shuffle));
		assertEquals(BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of()),
				BiographyPick.pick(shuffled, seed, index, FULL, Set.of()));
	}

	/** Four lines, one per slot, in reading order — with the facts of a town and without them. */
	@Property
	void oneLinePerSlotInReadingOrder(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index,
			@ForAll boolean known) {
		List<Line> lines = BiographyPick.pick(BUILT_IN, seed, index, known ? FULL : TownFacts.UNKNOWN, Set.of());
		assertEquals(BiographyPick.SLOTS, lines.size());
		for (int slot = 0; slot < lines.size(); slot++) {
			assertEquals(slot, candidate(lines.get(slot).id()).slot(), "line " + slot + " of " + lines);
		}
	}

	/** No chosen fragment carries a tag another chosen fragment forbids. */
	@Property(tries = 2000)
	void forbiddenTagsNeverMeet(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		List<Candidate> chosen = BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of()).stream()
				.map(line -> candidate(line.id())).toList();
		for (Candidate a : chosen) {
			for (Candidate b : chosen) {
				for (String tag : b.tags()) {
					assertFalse(a != b && a.forbid().contains(tag), a.id() + " forbids " + tag + " of " + b.id());
				}
			}
		}
	}

	/** A deliberately conflicting pool: the forbid rule decides the second slot, not the roll. */
	@Test
	void forbidRuleSteersTheChoice() {
		List<Candidate> pool = List.of(
				new Candidate("t:sand", 0, 1, List.of("desert"), List.of("snow"), List.of()),
				new Candidate("t:sled", 1, 1, List.of("snow"), List.of("desert"), List.of()),
				new Candidate("t:dune", 1, 1, List.of("desert"), List.of(), List.of()));
		for (int index = 0; index < 200; index++) {
			List<Line> lines = BiographyPick.pick(pool, 42L, index, TownFacts.UNKNOWN, Set.of());
			assertEquals(List.of("t:sand", "t:dune"), lines.stream().map(Line::id).toList());
		}
	}

	/** When nothing of a slot fits, the slot still gets a line rather than vanishing. */
	@Test
	void slotFallsBackWhenNothingFits() {
		List<Candidate> pool = List.of(
				new Candidate("t:sand", 0, 1, List.of("desert"), List.of(), List.of()),
				new Candidate("t:sled", 1, 1, List.of(), List.of("desert"), List.of()));
		assertEquals(List.of("t:sand", "t:sled"),
				BiographyPick.pick(pool, 1L, 7, TownFacts.UNKNOWN, Set.of()).stream().map(Line::id).toList());
	}

	/** The digits a line quotes are the town's own, three digits with leading zeros, or one digit of the lab. */
	@Property(tries = 3000)
	void quotedDigitsAreTheTownsOwn(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		for (Line line : BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of())) {
			List<String> substitutions = candidate(line.id()).substitutions();
			assertEquals(substitutions.size(), line.args().size());
			for (int i = 0; i < substitutions.size(); i++) {
				String arg = line.args().get(i);
				switch (substitutions.get(i)) {
					case "plaque" -> assertTrue(Set.of("214", "037", "905").contains(arg), arg);
					case "lab" -> assertEquals("305", arg);
					case "lab_digit_1" -> assertEquals("3", arg);
					case "lab_digit_2" -> assertEquals("0", arg);
					case "lab_digit_3" -> assertEquals("5", arg);
					default -> throw new AssertionError("unknown substitution " + substitutions.get(i));
				}
			}
		}
	}

	/** A town nothing is known about never gets a line that quotes it. */
	@Property(tries = 3000)
	void unknownTownGetsNoQuotingLine(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		for (Line line : BiographyPick.pick(BUILT_IN, seed, index, TownFacts.UNKNOWN, Set.of())) {
			assertTrue(candidate(line.id()).substitutions().isEmpty(), line.id());
			assertTrue(line.args().isEmpty());
		}
	}

	/** A town with plaques but no known lab gets plaque lines and never a lab line. */
	@Test
	void labLinesNeedTheLab() {
		TownFacts plaquesOnly = new TownFacts(List.of(214), OptionalInt.empty());
		boolean sawPlaque = false;
		for (int index = 0; index < 5000; index++) {
			for (Line line : BiographyPick.pick(BUILT_IN, 9L, index, plaquesOnly, Set.of())) {
				List<String> subs = candidate(line.id()).substitutions();
				assertTrue(subs.stream().noneMatch(s -> s.startsWith("lab")), line.id());
				sawPlaque |= subs.contains("plaque");
			}
		}
		assertTrue(sawPlaque, "no plaque line in 5000 workers");
	}

	/** {@code args} of a kept fragment reproduce what the pick gave that line. */
	@Property
	void argsOfAKeptFragmentMatchThePick(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		for (Line line : BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of())) {
			assertEquals(line.args(), BiographyPick.args(candidate(line.id()), seed, index, FULL));
		}
	}

	/** An avoided memory is skipped while another fits, and only memories are affected. */
	@Property
	void avoidedMemoryIsSkipped(@ForAll long seed, @ForAll @IntRange(min = 0, max = 20_999) int index) {
		List<Line> plain = BiographyPick.pick(BUILT_IN, seed, index, FULL, Set.of());
		Set<String> avoid = Set.of(plain.get(BiographyPick.MEMORY).id());
		List<Line> avoiding = BiographyPick.pick(BUILT_IN, seed, index, FULL, avoid);
		assertEquals(plain.subList(0, BiographyPick.MEMORY), avoiding.subList(0, BiographyPick.MEMORY));
		assertNotEquals(plain.get(BiographyPick.MEMORY).id(), avoiding.get(BiographyPick.MEMORY).id());
	}

	/** Every fragment of the mod is reachable: a town of a thousand workers reads each at least once. */
	@Test
	void everyFragmentIsUsed() {
		Set<String> seen = new HashSet<>();
		for (int index = 0; index < 4000; index++) {
			BiographyPick.pick(BUILT_IN, 123_456_789L, index, FULL, Set.of()).forEach(line -> seen.add(line.id()));
		}
		Set<String> all = new HashSet<>();
		BUILT_IN.forEach(c -> all.add(c.id()));
		all.removeAll(seen);
		assertTrue(all.isEmpty(), "never chosen: " + all);
	}

	/** The starting set of the task: 10 origins, 12 of each other slot, about a quarter of the memories clues. */
	@Test
	void startingSetHasItsSize() {
		Map<Integer, Integer> perSlot = new TreeMap<>();
		int memoryClues = 0;
		for (Fragment fragment : FRAGMENTS) {
			perSlot.merge(fragment.candidate().slot(), 1, Integer::sum);
			if (fragment.clue() && fragment.candidate().slot() == BiographyPick.MEMORY) {
				memoryClues++;
			}
		}
		assertEquals(Map.of(0, 10, 1, 12, 2, 12, 3, 12), perSlot);
		assertEquals(3, memoryClues);
	}

	/**
	 * Both forms of every fragment are in the English file, with exactly the arguments its substitutions supply; no
	 * biography key is left there without a fragment. That the other languages hold the same keys and arguments is
	 * the lang gate's job ({@code lang_check.py}).
	 */
	@Test
	void everyFragmentHasBothFormsWithItsArguments() throws IOException, URISyntaxException {
		Map<String, String> lang = langEntries();
		Set<String> expected = new HashSet<>();
		for (Fragment fragment : FRAGMENTS) {
			assertTrue(fragment.key().startsWith(KEY_PREFIX + SLOTS.get(fragment.candidate().slot()) + "."),
					fragment.key());
			for (String form : List.of(".male", ".female")) {
				String key = fragment.key() + form;
				expected.add(key);
				String text = lang.get(key);
				assertTrue(text != null, "missing in en_us: " + key);
				Set<Integer> indices = new HashSet<>();
				Matcher arg = INDEXED_ARG.matcher(text);
				while (arg.find()) {
					indices.add(Integer.parseInt(arg.group(1)));
				}
				Set<Integer> wanted = new HashSet<>();
				for (int i = 1; i <= fragment.candidate().substitutions().size(); i++) {
					wanted.add(i);
				}
				assertEquals(wanted, indices, key + ": " + text);
				assertFalse(text.contains("!"), "an archive line never exclaims: " + key);
			}
		}
		Set<String> orphans = new HashSet<>(lang.keySet());
		orphans.removeIf(k -> !k.startsWith(KEY_PREFIX));
		orphans.removeAll(expected);
		assertTrue(orphans.isEmpty(), "biography keys with no fragment: " + orphans);
	}

	private static Candidate candidate(String id) {
		return BUILT_IN.stream().filter(c -> c.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no fragment " + id));
	}

	private static List<Fragment> readFragments() {
		try {
			Path root = Path.of(BiographyPickTest.class.getClassLoader().getResource(DATA).toURI());
			List<Fragment> out = new ArrayList<>();
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					String rel = root.relativize(file).toString().replace('\\', '/');
					out.add(parse("alaindustrial:" + rel.substring(0, rel.length() - ".json".length()),
							Files.readString(file, StandardCharsets.UTF_8)));
				}
			}
			return List.copyOf(out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Fragment parse(String id, String json) {
		Map<String, String> strings = new HashMap<>();
		Matcher field = STRING_FIELD.matcher(json);
		while (field.find()) {
			strings.put(field.group(1), field.group(2));
		}
		Map<String, List<String>> lists = new HashMap<>();
		Matcher list = LIST_FIELD.matcher(json);
		while (list.find()) {
			List<String> items = new ArrayList<>();
			Matcher item = QUOTED.matcher(list.group(2));
			while (item.find()) {
				items.add(item.group(1));
			}
			lists.put(list.group(1), List.copyOf(items));
		}
		Matcher weight = WEIGHT.matcher(json);
		int slot = SLOTS.indexOf(strings.get("slot"));
		assertTrue(slot >= 0, id + ": unknown slot " + strings.get("slot"));
		assertTrue(id.startsWith("alaindustrial:" + strings.get("slot") + "/"), id + " is not in its slot's folder");
		Candidate candidate = new Candidate(id, slot, weight.find() ? Integer.parseInt(weight.group(1)) : 1,
				lists.getOrDefault("tags", List.of()), lists.getOrDefault("forbid", List.of()),
				lists.getOrDefault("substitutions", List.of()));
		return new Fragment(candidate, strings.get("key"), strings.containsKey("clue"));
	}

	private static Map<String, String> langEntries() throws IOException, URISyntaxException {
		String json = Files.readString(Path.of(BiographyPickTest.class.getClassLoader().getResource(EN_US).toURI()),
				StandardCharsets.UTF_8);
		Map<String, String> out = new HashMap<>();
		Matcher entry = Pattern.compile(
				"\"(settlement\\.alaindustrial\\.bio\\.[^\"]*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
		while (entry.find()) {
			out.put(entry.group(1), entry.group(2));
		}
		return out;
	}
}
