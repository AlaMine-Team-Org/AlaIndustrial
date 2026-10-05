package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every fluid machine's recipe-viewer click area is claimed by a {@link RecipeViewerForm} (audit 2026-10-04).
 *
 * <p>{@code RecipeViewerForm.clickAreas()} walks {@code MachineRecipeViewerTargets.FLUID_ALL} only for the
 * forms named in one {@code case} label, and keeps a target whose kind id equals the form's id. A third fluid
 * machine added to {@code FLUID_ALL} without a form of its own, or a form dropped from that label, would lose
 * its GUI click area in both viewers without an error: the list compiles, the screen draws, and only a player
 * clicking the arrow notices nothing opens.
 *
 * <p>Read from source, the same technique as {@link ReiTabOrderTest}: both files reference screen classes, and
 * loading them in L1 would pull the client and the registries in. A form claims a kind when its constant is
 * declared with {@code ModRecipes.<KIND>.id()} and it is named in the {@code case} that iterates
 * {@code FLUID_ALL}; that is exactly the match {@code clickAreas()} makes at run time.
 */
class RecipeViewerFluidClickAreaTest {

	private static final Path TARGETS = Path.of(
			"src/main/java/dev/alaindustrial/client/compat/MachineRecipeViewerTargets.java");

	private static final Path FORMS = Path.of("src/main/java/dev/alaindustrial/client/compat/RecipeViewerForm.java");

	private static final Pattern JAVA_COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

	/** The {@code FLUID_ALL = List.of(…);} literal. */
	private static final Pattern FLUID_ALL = Pattern.compile("FLUID_ALL\\s*=\\s*List\\.of\\((.*?)\\);",
			Pattern.DOTALL);

	/** One {@code new FluidTarget(…)} entry, up to the kind it names. */
	private static final Pattern FLUID_TARGET = Pattern.compile("new\\s+FluidTarget\\(");

	private static final Pattern KIND = Pattern.compile("ModRecipes\\.(\\w+)");

	/** A {@code case A, B ->} label of a switch; group 1 holds the constants. */
	private static final Pattern CASE = Pattern.compile("case\\s+([A-Z_][A-Z_,\\s]*)->");

	/** A form constant declared with a recipe family's id: {@code NAME(ModRecipes.KIND.id(), …}. */
	private static final Pattern FORM_KIND = Pattern.compile("(?m)^\\t([A-Z][A-Z_]+)\\(ModRecipes\\.(\\w+)\\.id\\(\\)");

	@Test
	void everyFluidTargetIsClaimedByAForm() throws IOException {
		List<String> targets = fluidTargetKinds(read(TARGETS));
		assertTrue(targets.size() >= 2, TARGETS + ": FLUID_ALL entries not found: " + targets);
		assertEquals(List.of(), unclaimed(targets, read(FORMS)), "a FluidTarget in MachineRecipeViewerTargets.FLUID_ALL"
				+ " that no RecipeViewerForm claims loses its GUI click area in JEI and REI without an error - give its"
				+ " kind a form and name the form in the FLUID_ALL case of RecipeViewerForm.clickAreas()");
	}

	@Test
	void aPlantedThirdTargetWithNoFormIsNamed() throws IOException {
		String targets = read(TARGETS).replace("FLUID_ALL = List.of(", "FLUID_ALL = List.of("
				+ "new FluidTarget(FermenterScreen.class, ModRecipes.FERMENTING, FermenterScreen.PROGRESS_AREA),");
		assertEquals(List.of("FERMENTING"), unclaimed(fluidTargetKinds(targets), read(FORMS)));
	}

	@Test
	void aFormDroppedFromTheFluidCaseIsNamed() throws IOException {
		String forms = read(FORMS).replaceFirst("case POLYMERIZING, DISTILLING ->", "case POLYMERIZING ->");
		assertEquals(List.of("DISTILLING"), unclaimed(fluidTargetKinds(read(TARGETS)), forms));
	}

	/** The kind of every {@code FluidTarget} in {@code FLUID_ALL}, in order; each entry must name exactly one. */
	static List<String> fluidTargetKinds(String targetsSource) {
		Matcher literal = FLUID_ALL.matcher(targetsSource);
		assertTrue(literal.find(), TARGETS + " has no FLUID_ALL = List.of(...)");
		String[] entries = FLUID_TARGET.split(literal.group(1));
		List<String> kinds = new ArrayList<>();
		for (int i = 1; i < entries.length; i++) {
			Matcher kind = KIND.matcher(entries[i]);
			List<String> named = new ArrayList<>();
			while (kind.find()) {
				named.add(kind.group(1));
			}
			assertEquals(1, named.size(), "a FluidTarget must name one ModRecipes kind: " + entries[i].strip());
			kinds.add(named.get(0));
		}
		return kinds;
	}

	/** The kinds of {@code targets} that no form named in the {@code FLUID_ALL} case of clickAreas() claims. */
	static List<String> unclaimed(List<String> targets, String formsSource) {
		Map<String, String> kindOfForm = new HashMap<>();
		Matcher form = FORM_KIND.matcher(formsSource);
		while (form.find()) {
			kindOfForm.put(form.group(1), form.group(2));
		}
		List<String> claimed = new ArrayList<>();
		for (String label : fluidCaseLabels(formsSource)) {
			String kind = kindOfForm.get(label);
			assertTrue(kind != null, FORMS + ": form " + label + " walks FLUID_ALL but is not declared with"
					+ " ModRecipes.<KIND>.id() - this test cannot tell which kind it claims");
			claimed.add(kind);
		}
		List<String> out = new ArrayList<>(targets);
		out.removeAll(claimed);
		return out;
	}

	/** The labels of the one {@code case} of clickAreas() whose block iterates {@code FLUID_ALL}. */
	private static List<String> fluidCaseLabels(String formsSource) {
		int start = formsSource.indexOf("public List<ClickArea> clickAreas()");
		assertTrue(start >= 0, FORMS + " has no clickAreas()");
		String body = formsSource.substring(start, formsSource.indexOf("public List<RecipeViewerInfo.Entry> pages()"));
		List<MatchResult> cases = CASE.matcher(body).results().toList();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < cases.size(); i++) {
			int end = i + 1 < cases.size() ? cases.get(i + 1).start() : body.length();
			if (body.substring(cases.get(i).end(), end).contains("MachineRecipeViewerTargets.FLUID_ALL")) {
				for (String name : cases.get(i).group(1).split(",")) {
					out.add(name.strip());
				}
			}
		}
		assertTrue(!out.isEmpty(), FORMS + ": no case of clickAreas() iterates MachineRecipeViewerTargets.FLUID_ALL");
		return out;
	}

	private static String read(Path path) throws IOException {
		return JAVA_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8)).replaceAll("");
	}
}
