package dev.alaindustrial.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.gametest.CreativeTabSnapshotView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The mod tab's composition (MOD-407), split by what can answer each question (MOD-711, batch 0b).
 *
 * <p><b>Two sources, on purpose.</b> What the tabs SHOW — which entries, in which order, how many, none
 * twice — is a fact about runtime output, so it is read from {@link CreativeTabSnapshotView}: the reviewed
 * capture ({@code CreativeTabSnapshot}) that {@code CreativeTabSnapshotScenarios} holds both loaders'
 * real output against, line for line. A check on that capture is a check on what the player sees, and it
 * survives {@code CreativeTabContent} being cut into domain sections, which a scan of that one file does
 * not. The snapshot reaches this class through the {@code gametest} source set the L1 lane already
 * compiles against ({@code common/build.gradle}); nothing here parses it as text.
 *
 * <p>What the capture CANNOT say is about the code that produced it, so those tests still read source
 * text: whether a group method is reachable from a tab ({@link #everyGroupIsReachableFromSomeTab()} — a
 * group nobody calls leaves no trace in the output, which is exactly the failure), which groups a loader
 * calls ({@link #rootsAreCalledBySomeLoader()} — the capture is taken by calling the roots itself, so it
 * is blind to who else does), and whether an entry goes through the guarded accessor
 * ({@link #tabEntriesGoThroughTheGuardedAccessor()} — a style of call, not an outcome). Since batch 4 the
 * group bodies are section methods of the domain files ({@code registry/content/<Domain>Content.java}) and
 * {@code CreativeTabContent} is the table of contents that calls them by class name, so these three read
 * {@link #SOURCE} and every file of {@link #DOMAIN_DIR}. A group name is unique across them
 * ({@link #bodies()} fails otherwise): the Python readers of the tab look a group up by its name alone.
 *
 * <p><b>What it does NOT check</b>, deliberately: that every registered item appears somewhere. That
 * question belongs to the runtime registry — several items are registered and intentionally hidden
 * (pre-release content), and neither the capture nor a text scan can tell "hidden on purpose" from
 * "forgotten". The loader-parity and registry validators own that side.
 */
class CreativeTabOrderTest {

	private static final Path SOURCE = Path.of(
			"src/main/java/dev/alaindustrial/registry/CreativeTabContent.java");

	/** The domain files whose section methods hold the group bodies since MOD-711, batch 4. */
	private static final Path DOMAIN_DIR = Path.of("src/main/java/dev/alaindustrial/registry/content");

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

	/**
	 * A group call. The sink is normally the parameter {@code out}, but {@code main} buffers the tab
	 * through a {@code ShapeSorted} to group it by silhouette (MOD-574) and hands that buffer to
	 * {@code fill} instead — so the argument name has to be part of the pattern, not assumed.
	 *
	 * <p>Spelling the two names out rather than accepting any identifier is deliberate: {@code (\w+)}
	 * there would also match an ordinary one-argument statement and quietly invent a group that does
	 * not exist. If a third sink name appears, it belongs here, next to these two.
	 *
	 * <p>The table of contents names a section by its domain class ({@code FluidContent.fluids(out);},
	 * MOD-711 batch 4), so an optional {@code Class.} qualifier precedes the name.
	 */
	private static final Pattern CALL = Pattern.compile("^\\t\\t(?:\\w+\\.)?(\\w+)\\((?:out|sorted)\\);");
	/**
	 * A group head. {@code AnchoredSink} is a {@link CreativeTabContent.Sink} that can also place an entry
	 * after an anchor — the vanilla Combat and Tools &amp; Utilities groups take one (MOD-555). Without
	 * that alternative their bodies would be invisible here while {@link #ROOTS} already named them, and
	 * every group reachable only through them would look orphaned.
	 */
	private static final Pattern METHOD = Pattern.compile(
			"(?:private|public) static void (\\w+)\\((?:Anchored)?Sink out\\) \\{");

	/** {@link #SOURCE} first, then every domain file in name order. */
	private static List<Path> sources() throws IOException {
		List<Path> sources = new ArrayList<>(List.of(SOURCE));
		try (var files = Files.list(DOMAIN_DIR)) {
			files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
		}
		return sources;
	}

	private static Map<String, List<String>> bodies() throws IOException {
		Map<String, List<String>> bodies = new LinkedHashMap<>();
		for (Path source : sources()) {
			readBodies(source, bodies);
		}
		return bodies;
	}

	private static void readBodies(Path source, Map<String, List<String>> bodies) throws IOException {
		String current = null;
		for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
			Matcher head = METHOD.matcher(line.strip());
			if (head.lookingAt()) {
				current = head.group(1);
				if (bodies.put(current, new ArrayList<>()) != null) {
					fail("tab group '" + current + "' is declared twice across CreativeTabContent and the domain "
							+ "files (again in " + source.getFileName() + ") — the readers of the tab look a group "
							+ "up by its name alone, so a section needs a name of its own");
				}
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
	}

	/** Tab name as the capture writes it: the root's camelCase name in snake_case. */
	private static String tabName(String root) {
		return root.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

	/**
	 * The reference, per tab, as the ids each tab receives in order. An appended entry is one id; an anchored
	 * insertion ({@code <tab> after <anchor>: <ids>}) contributes the ids it inserts, never the anchor — the
	 * anchor is somebody else's entry (usually vanilla's), not a repeat.
	 */
	private static Map<String, List<String>> snapshotTabs() {
		assertTrue(CreativeTabSnapshotView.captured(), "the creative tab reference was never captured "
				+ "(CreativeTabSnapshot.CAPTURED is false) — these checks would read an empty reference and "
				+ "prove nothing; run the update command in CreativeTabSnapshot's javadoc");
		Map<String, List<String>> tabs = new LinkedHashMap<>();
		for (String line : CreativeTabSnapshotView.lines()) {
			int space = line.indexOf(' ');
			assertTrue(space > 0, "malformed reference line (want '<tab> <id>'): " + line);
			String tab = line.substring(0, space);
			String rest = line.substring(space + 1);
			List<String> ids = tabs.computeIfAbsent(tab, k -> new ArrayList<>());
			if (rest.startsWith("after ")) {
				int colon = rest.indexOf(": ");
				assertTrue(colon > 0, "malformed anchored reference line (want '<tab> after <anchor>: <ids>'): "
						+ line);
				ids.addAll(List.of(rest.substring(colon + 2).split(" ")));
			} else {
				ids.add(rest);
			}
		}
		return tabs;
	}

	/**
	 * One item, one cell. A duplicate is not cosmetic: the same icon appears twice in the tab, and the
	 * second copy pushes everything after it one place along, which is how a carefully ordered group
	 * turns into a shuffled one. This caught six real duplicates the moment the tab was regrouped —
	 * the fluid machines were listed both with the machines and with the fluid chain.
	 *
	 * <p>Read from the reviewed capture of what {@code main} really hands the loader, so a duplicate that
	 * arises from two groups both calling the same helper is seen as the player would see it.
	 */
	@Test
	void modTabListsEveryItemExactlyOnce() {
		assertNoDuplicates("main");
	}

	/**
	 * The same rule for the vanilla tabs the mod feeds — they were unguarded until MOD-574.
	 *
	 * <p>The mod's OWN tab is the one that crashes on a duplicate: the vanilla builder behind it throws
	 * {@code Accidentally adding the same item stack twice}. These tabs fail more quietly and therefore
	 * worse — NeoForge collects into a set and swallows the second copy, Fabric appends and shows the
	 * icon twice, so the same source file produces two different tabs and neither loader complains.
	 *
	 * <p><b>Wider than the text check it replaces.</b> {@code combat} and {@code tools_and_utilities} are
	 * in the list now. The text parser could not follow their anchored placement ({@code after(...)}) and
	 * left them to {@code CreativeTabAnchorSafetyTest} on the NeoForge side; the capture lists the ids each
	 * anchored insertion adds, so the same "one id, one cell" rule applies to them directly. That test still
	 * owns the OTHER question for those two tabs — whether an anchor exists in the vanilla tab.
	 */
	@Test
	void vanillaTabsListEveryItemExactlyOnce() {
		for (String root : List.of("functionalBlocks", "buildingBlocks", "naturalBlocks", "ingredients",
				"combat", "toolsAndUtilities")) {
			assertNoDuplicates(root);
		}
	}

	private void assertNoDuplicates(String root) {
		List<String> entries = snapshotTabs().getOrDefault(tabName(root), List.of());
		assertFalse(entries.isEmpty(), "tab '" + tabName(root) + "' has no entries in the reference at all — "
				+ "the capture stopped covering it, so this test proves nothing");
		Set<String> seen = new LinkedHashSet<>();
		List<String> duplicates = new ArrayList<>();
		for (String entry : entries) {
			if (!seen.add(entry)) {
				duplicates.add(entry);
			}
		}
		if (!duplicates.isEmpty()) {
			fail("tab '" + tabName(root) + "' lists these entries more than once: " + duplicates
					+ " — one item, one cell; a second copy also shifts every entry after it");
		}
	}

	/**
	 * The tab is not accidentally emptied. A floor rather than an exact count: content is added often,
	 * and a test that has to be edited on every addition gets edited without being read. What it does
	 * catch is the failure that matters — a refactor that drops a group call and silently halves the
	 * tab.
	 *
	 * <p>Against the capture this guards the REFERENCE: the scenario already fails a loader whose tab differs
	 * from it, so what is left to prevent is a reference re-captured from a run that had lost half the tab
	 * and then committed without anyone reading the diff.
	 */
	@Test
	void modTabIsNotSilentlyEmptied() {
		int shown = snapshotTabs().getOrDefault("main", List.of()).size();
		assertTrue(shown >= 150,
				"the reference shows " + shown + " entries in the mod tab, expected at least 150 — was it "
						+ "re-captured after a group stopped being called from main()?");
	}

	/**
	 * The capture covers every tab a loader fills — the check that stops the two duplicate tests above from
	 * going blind on a tab nobody captured.
	 *
	 * <p>The scenario records the output by calling the public group roots itself; {@link #ROOTS} is the set
	 * the loaders are proven (by {@link #rootsAreCalledBySomeLoader()}) to call. If a loader starts filling a
	 * new tab, ROOTS grows with it and this test then demands that tab in the reference, instead of letting
	 * it be filled with nothing characterizing what it gets.
	 */
	@Test
	void referenceCoversEveryRoot() {
		Set<String> captured = snapshotTabs().keySet();
		Set<String> expected = new LinkedHashSet<>();
		for (String root : ROOTS) {
			expected.add(tabName(root));
		}
		assertEquals(expected, new LinkedHashSet<>(captured),
				"the tabs in CreativeTabSnapshot must be exactly the roots a loader fills (ROOTS, snake_case) — "
						+ "extend CreativeTabSnapshotScenarios.capture() and re-capture, or fix ROOTS");
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
