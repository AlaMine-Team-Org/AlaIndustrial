package dev.alaindustrial.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The mod tab's composition, checked as text rather than at runtime (MOD-407).
 *
 * <p><b>Why text.</b> Filling a creative tab needs registered {@code Item} instances, which do not
 * exist without a running game — so the honest options were an L2 gametest or reading the one file
 * that decides the composition. Reading the file wins here because the failures this guards against
 * are all visible in it: an entry listed twice, an entry that quietly disappeared, a group that
 * stopped being called. None of those need a live registry to see, and catching them in
 * {@code :common:test} means they fail the build in seconds instead of minutes.
 *
 * <p><b>What it does NOT check</b>, deliberately: that every registered item appears somewhere. That
 * question belongs to the runtime registry — several items are registered and intentionally hidden
 * (pre-release content), and a text scan cannot tell "hidden on purpose" from "forgotten". The
 * loader-parity and registry validators already own that side.
 */
class CreativeTabOrderTest {

	private static final Path SOURCE = Path.of(
			"src/main/java/dev/alaindustrial/registry/CreativeTabContent.java");

	/**
	 * The groups a loader fills a tab from — the entry points every other group has to be reachable
	 * from. Hand-written, and therefore proven against the loaders by {@link #rootsAreCalledBySomeLoader()}
	 * rather than trusted.
	 */
	private static final Set<String> ROOTS = Set.of("main", "ingredients", "buildingBlocks",
			"naturalBlocks", "functionalBlocks", "combat", "toolsAndUtilities");

	/**
	 * Where a loader actually fills a creative tab. Relative to {@code common/}, which is this task's
	 * working directory (Gradle's default for {@code :common:test}) — the same assumption {@link #SOURCE}
	 * already makes.
	 */
	private static final List<Path> LOADER_SOURCES = List.of(
			Path.of("../fabric/src/main/java/dev/alaindustrial/registry/ModItems.java"),
			Path.of("../neoforge/src/main/java/dev/alaindustrial/registry/neoforge/"
					+ "ModCreativeTabEventsNeoForge.java"),
			Path.of("../neoforge/src/main/java/dev/alaindustrial/registry/neoforge/"
					+ "ModCreativeTabNeoForge.java"));

	/**
	 * {@code CreativeTabContent.groupName} as a loader writes it, in any of the call forms used.
	 *
	 * <p>The name must start LOWERCASE. A loader also NAMES a type from this class —
	 * {@code new CreativeTabContent.AnchoredSink() {…}} (MOD-555) — which is a member reference, not a
	 * group call. Matching it would put a type name in the called set and fail this test against a
	 * "group" that never existed.
	 */
	private static final Pattern LOADER_CALL = Pattern.compile("CreativeTabContent\\.([a-z]\\w*)\\s*\\(");

	/**
	 * Comments, stripped before the call scan. A javadoc line that merely MENTIONS a group by name reads
	 * exactly like a call to a regex, and the resulting failure would accuse the roots list of being
	 * wrong when nothing was. That is one sentence away, not hypothetical: all three loader sources
	 * already carry prose about this class. Stripping can only lose a real call, never invent one — and
	 * a lost call also fails loudly (its root would look uncalled), so the error direction stays safe.
	 */
	private static final Pattern JAVA_COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

	/** {@code show(out, ModContent.X)} — the current form — and the older {@code out.accept(...)}. */
	private static final Pattern ENTRY = Pattern.compile(
			"show\\(out, ModContent\\.(\\w+)\\)|out\\.accept\\(ModContent\\.(\\w+)\\.get\\(\\)\\)");
	private static final Pattern CALL = Pattern.compile("^\\t\\t(\\w+)\\(out\\);");
	/**
	 * A group head. {@code AnchoredSink} is a {@link CreativeTabContent.Sink} that can also place an entry
	 * after an anchor — the vanilla Combat and Tools &amp; Utilities groups take one (MOD-555). Without
	 * that alternative their bodies would be invisible here while {@link #ROOTS} already named them, and
	 * every group reachable only through them would look orphaned.
	 */
	private static final Pattern METHOD = Pattern.compile(
			"(?:private|public) static void (\\w+)\\((?:Anchored)?Sink out\\) \\{");

	private static Map<String, List<String>> bodies() throws IOException {
		List<String> lines = Files.readAllLines(SOURCE, StandardCharsets.UTF_8);
		Map<String, List<String>> bodies = new LinkedHashMap<>();
		String current = null;
		for (String line : lines) {
			Matcher head = METHOD.matcher(line.strip());
			if (head.lookingAt()) {
				current = head.group(1);
				bodies.put(current, new ArrayList<>());
				continue;
			}
			if (current != null) {
				if (line.equals("\t}")) {
					current = null;
				} else {
					bodies.get(current).add(line);
				}
			}
		}
		return bodies;
	}

	/** Every entry the tab shows, in order, expanding the group calls the way the game will. */
	private static List<String> entriesOf(String method, Map<String, List<String>> bodies, int depth) {
		List<String> out = new ArrayList<>();
		if (depth > 6 || !bodies.containsKey(method)) {
			return out;
		}
		for (String line : bodies.get(method)) {
			Matcher entry = ENTRY.matcher(line);
			if (entry.find()) {
				out.add(entry.group(1) != null ? entry.group(1) : entry.group(2));
				continue;
			}
			Matcher call = CALL.matcher(line);
			if (call.find()) {
				out.addAll(entriesOf(call.group(1), bodies, depth + 1));
			}
		}
		return out;
	}

	/**
	 * One item, one cell. A duplicate is not cosmetic: the same icon appears twice in the tab, and the
	 * second copy pushes everything after it one place along, which is how a carefully ordered group
	 * turns into a shuffled one. This caught six real duplicates the moment the tab was regrouped —
	 * the fluid machines were listed both with the machines and with the fluid chain.
	 */
	@Test
	void modTabListsEveryItemExactlyOnce() throws IOException {
		List<String> entries = entriesOf("main", bodies(), 0);
		Set<String> seen = new LinkedHashSet<>();
		List<String> duplicates = new ArrayList<>();
		for (String entry : entries) {
			if (!seen.add(entry)) {
				duplicates.add(entry);
			}
		}
		if (!duplicates.isEmpty()) {
			fail("the mod tab lists these entries more than once: " + duplicates
					+ " — one item, one cell; a second copy also shifts every entry after it");
		}
	}

	/**
	 * The tab is not accidentally emptied. A floor rather than an exact count: content is added often,
	 * and a test that has to be edited on every addition gets edited without being read. What it does
	 * catch is the failure that matters — a refactor that drops a group call and silently halves the
	 * tab.
	 */
	@Test
	void modTabIsNotSilentlyEmptied() throws IOException {
		List<String> entries = entriesOf("main", bodies(), 0);
		assertTrue(entries.size() >= 150,
				"the mod tab shows " + entries.size() + " entries, expected at least 150 — did a group "
						+ "stop being called from main()?");
	}

	/**
	 * Every group declared in the file is actually reachable from a tab. A group that nobody calls is
	 * content the player cannot see, and it looks exactly like working code — which is why MOD-102
	 * (two chest tiers listed on one loader only) went unnoticed until a player asked.
	 *
	 * <p><b>The roots are the groups a LOADER calls, and nothing else</b> (MOD-477). This list used to
	 * carry {@code combat} and {@code toolsAndUtilities} as well — two groups no loader had called for
	 * six weeks. Naming them here made this very test declare them reachable by definition, so the one
	 * gate that exists to catch an unreachable group was structurally unable to report the two
	 * unreachable groups in front of it. Adding a name here is therefore not a way to silence this
	 * test: a name belongs in this set only after a loader calls that group, and
	 * {@link #rootsAreCalledBySomeLoader()} checks exactly that against the loader sources.
	 */
	@Test
	void everyGroupIsReachableFromSomeTab() throws IOException {
		Map<String, List<String>> bodies = bodies();
		Set<String> reached = new LinkedHashSet<>(ROOTS);
		boolean grew = true;
		while (grew) {
			grew = false;
			for (String name : new ArrayList<>(reached)) {
				for (String line : bodies.getOrDefault(name, List.of())) {
					Matcher call = CALL.matcher(line);
					if (call.find() && reached.add(call.group(1))) {
						grew = true;
					}
				}
			}
		}
		Set<String> orphans = new LinkedHashSet<>(bodies.keySet());
		orphans.removeAll(reached);
		assertEquals(Set.of(), orphans,
				"these groups are declared but never shown in any tab: " + orphans);
	}

	/**
	 * The roots are what the loaders really call — the check that stops this file from grading itself
	 * (MOD-477).
	 *
	 * <p><b>The hole this closes.</b> {@link #everyGroupIsReachableFromSomeTab()} promises to catch a
	 * group nobody shows to the player, and it decides "shown" from a list written by hand right above
	 * it. For six weeks that list named {@code combat} and {@code toolsAndUtilities}, which no loader
	 * had called since the tempered-gear anchoring change — so the two groups the test existed to find
	 * were the two it was defined not to see. A whole armour set was added to one of them and nothing
	 * anywhere went red. A list that grants reachability has to be checked against the thing that
	 * actually grants it.
	 *
	 * <p><b>Both directions matter.</b> A root nobody calls is the six-week bug. A called group missing
	 * from the roots is the opposite failure: everything reachable only through it looks orphaned, and
	 * the next person "fixes" that by deleting live content.
	 *
	 * <p><b>Why text, and why here.</b> Same reason as the rest of this class — filling a tab needs a
	 * running game, and the question ("does a loader name this group?") is answerable from the source.
	 * It lives in this file rather than in a Python validator so the list and its proof cannot drift
	 * apart: one edit, one place.
	 */
	@Test
	void rootsAreCalledBySomeLoader() throws IOException {
		Set<String> called = new LinkedHashSet<>();
		for (Path source : LOADER_SOURCES) {
			if (!Files.isRegularFile(source)) {
				fail("loader source not found: " + source.toAbsolutePath().normalize()
						+ " — it moved or was renamed. Point LOADER_SOURCES at it again; leaving the list "
						+ "stale would silently make this check pass on nothing.");
			}
			String code = JAVA_COMMENT.matcher(Files.readString(source, StandardCharsets.UTF_8))
					.replaceAll("");
			Matcher call = LOADER_CALL.matcher(code);
			while (call.find()) {
				called.add(call.group(1));
			}
		}
		if (called.isEmpty()) {
			fail("no CreativeTabContent.<group>(...) call found in any loader source — the call form "
					+ "changed and this check went blind, which is not the same as the tabs being empty");
		}
		Set<String> declaredButUncalled = new LinkedHashSet<>(ROOTS);
		declaredButUncalled.removeAll(called);
		Set<String> calledButNotDeclared = new LinkedHashSet<>(called);
		calledButNotDeclared.removeAll(ROOTS);
		assertEquals(Set.of(), declaredButUncalled,
				"these groups are listed as tab roots but no loader calls them — they are dead code, and "
						+ "listing them here makes everyGroupIsReachableFromSomeTab unable to say so: "
						+ declaredButUncalled);
		assertEquals(Set.of(), calledButNotDeclared,
				"a loader fills a tab from these groups but ROOTS does not list them — everything they "
						+ "reach will look unreachable to everyGroupIsReachableFromSomeTab: "
						+ calledButNotDeclared);
	}

	/**
	 * Entries are read through {@code show(...)}, which survives an unresolvable handle. A bare
	 * {@code ModContent.X.get()} inside a tab callback throws there, and the throw takes the whole tab
	 * with it — the player opens creative and the mod's tab is gone, with nothing naming the culprit.
	 */
	@Test
	void tabEntriesGoThroughTheGuardedAccessor() throws IOException {
		List<String> offenders = new ArrayList<>();
		for (Map.Entry<String, List<String>> group : bodies().entrySet()) {
			for (String line : group.getValue()) {
				if (line.contains("out.accept(ModContent.")) {
					offenders.add(group.getKey() + ": " + line.strip());
				}
			}
		}
		if (!offenders.isEmpty()) {
			fail("these entries bypass show(...) and would crash the whole tab if their handle is "
					+ "unresolvable: " + offenders);
		}
	}
}
