package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The order of the REI recipe tabs of the special recipe forms (MOD-716, batches 11–12), read from the
 * source of the REI adapter — and the rule that each viewer shows every form of {@link RecipeViewerForm}.
 *
 * <p><b>Why source text and not a run.</b> REI has no build for Minecraft 26.3 (`fabric/build.gradle`): the
 * plugin compiles against the older REI API, but no lane of this line can load it — the Fabric world lane
 * has no REI on its runtime classpath, and the Fabric module has no unit-test lane at all. So the tab order
 * REI registers is pinned where it is written: {@code ReiRecipeForms.TAB_ORDER}, which
 * {@code AlaReiPlugin.registerCategories} replays after the processing families. On {@code mc/26.2} REI
 * runs, and the dev client with {@code -Pviewer=rei} shows the tabs themselves.
 *
 * <p>The owner decided (2026-10-04) that each viewer keeps its own tab order: REI files the distillation
 * column before the alloy smelter and the evolution pages before the machine pages; JEI does neither. The
 * JEI order is pinned by running its plugin ({@code AlaJeiPluginRegistrationGoldenTest}, NeoForge L1.5).
 *
 * <p>Until batch 11 the order was read from the hand-written {@code registry.add(new …Category(…))} calls
 * of the plugin; {@link #SHIPPED_ORDER} is the list that reading produced, unchanged.
 */
class ReiTabOrderTest {

	private static final Path PLUGIN = Path.of(
			"../fabric/src/main/java/dev/alaindustrial/compat/rei/AlaReiPlugin.java");

	private static final Path REI_FORMS = Path.of(
			"../fabric/src/main/java/dev/alaindustrial/compat/rei/ReiRecipeForms.java");

	private static final Path JEI_FORMS = Path.of(
			"src/main/java/dev/alaindustrial/client/compat/jei/JeiRecipeForms.java");

	private static final Path FORMS = Path.of("src/main/java/dev/alaindustrial/client/compat/RecipeViewerForm.java");

	/** REI's tab order as shipped: the processing families first, then the special forms. */
	static final List<String> SHIPPED_ORDER = List.of("kinds", "polymerizing", "distilling", "alloying",
			"canning", "evolution_info", "machine_info", "plant_info");

	private static final Pattern JAVA_COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

	/** The {@code TAB_ORDER = List.of(…);} literal of an adapter. */
	private static final Pattern TAB_ORDER = Pattern.compile("TAB_ORDER\\s*=\\s*List\\.of\\(([^;]*)\\);");

	private static final Pattern FORM = Pattern.compile("RecipeViewerForm\\.(\\w+)");

	/** An enum constant of {@link RecipeViewerForm}: an upper-case name opening a line, then its arguments. */
	private static final Pattern CONSTANT = Pattern.compile("(?m)^\\t([A-Z][A-Z_]+)\\(");

	@Test
	void reiRegistersTheSpecialFormsInItsShippedTabOrder() throws IOException {
		assertEquals(SHIPPED_ORDER, registeredOrder(), "MOD-716: REI's recipe tabs keep the order they shipped in"
				+ " (owner decision 2026-10-04) - a change of tab order is a change of behaviour");
	}

	@Test
	void eachViewerShowsEveryFormExactlyOnce() throws IOException {
		List<String> forms = new ArrayList<>();
		Matcher constant = CONSTANT.matcher(read(FORMS));
		while (constant.find()) {
			forms.add(constant.group(1).toLowerCase(Locale.ROOT));
		}
		assertTrue(forms.size() >= 7, "RecipeViewerForm constants not found in " + FORMS + ": " + forms);
		for (Path adapter : List.of(REI_FORMS, JEI_FORMS)) {
			List<String> order = tabOrder(adapter);
			assertEquals(forms.stream().sorted().toList(), order.stream().sorted().toList(), adapter
					+ ": TAB_ORDER must name every RecipeViewerForm exactly once, or one viewer shows a form the"
					+ " other does not");
		}
	}

	/** The categories REI registers, in order: the processing families, then the special forms. */
	static List<String> registeredOrder() throws IOException {
		String body = method(read(PLUGIN), "registerCategories");
		int kinds = body.indexOf("new AlaProcessingCategory(");
		int forms = body.indexOf("ReiRecipeForms.TAB_ORDER");
		assertTrue(kinds >= 0 && forms > kinds, PLUGIN + ": registerCategories must add the processing families,"
				+ " then replay ReiRecipeForms.TAB_ORDER");
		List<String> order = new ArrayList<>();
		order.add("kinds");
		order.addAll(tabOrder(REI_FORMS));
		return order;
	}

	/** The form ids of an adapter's {@code TAB_ORDER}, in order. */
	private static List<String> tabOrder(Path adapter) throws IOException {
		Matcher literal = TAB_ORDER.matcher(read(adapter));
		assertTrue(literal.find(), adapter + " has no TAB_ORDER = List.of(...)");
		List<String> order = new ArrayList<>();
		Matcher form = FORM.matcher(literal.group(1));
		while (form.find()) {
			order.add(form.group(1).toLowerCase(Locale.ROOT));
		}
		return order;
	}

	private static String read(Path path) throws IOException {
		return JAVA_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8)).replaceAll("");
	}

	/** The body of {@code name}, found by brace counting from its signature. */
	private static String method(String source, String name) {
		int start = source.indexOf("public void " + name + "(");
		assertTrue(start >= 0, PLUGIN + " has no method " + name);
		int open = source.indexOf('{', start);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				return source.substring(open, i + 1);
			}
		}
		throw new AssertionError(PLUGIN + ": unbalanced braces in " + name);
	}
}
