package dev.alaindustrial.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MOD-762 / MOD-793: every input method of {@code MachineScreen} is {@code final}, so the overlays are asked
 * before a screen's own controls on every path — tooltip, click, wheel, drag and release. A screen answers
 * through the hooks ({@code controlTooltip}, {@code controlClicked}, {@code controlScrolled},
 * {@code controlDragged}, {@code controlReleased}). Read from the compiled bytecode.
 *
 * <p>The compiler already refuses a subclass override of a final method; what it cannot notice is the
 * {@code final} itself being dropped — the subclasses would simply stop breaking. This rule pins the modifier.
 *
 * <p>{@code keyPressed} is deliberately absent (MOD-793 decision): the base does not override it, no
 * {@code MachineScreen} subclass does, and no overlay takes keyboard input. A keyboard overlay would bring a
 * final {@code keyPressed} with a {@code controlKeyPressed} hook, and its name joins {@link #FINAL_INPUT}.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: {@code :common}'s pitest lane
 * targets {@code dev.alaindustrial.*Test}, and there is nothing to mutate in a bytecode check.
 */
class MachineScreenInputRules {

	private static final String SCREEN_PACKAGE = "dev.alaindustrial.client.screen";
	private static final String BASE = SCREEN_PACKAGE + ".MachineScreen";

	/** The input methods the base routes through its overlays first; each must be final. */
	private static final Set<String> FINAL_INPUT =
			Set.of("extractTooltip", "mouseClicked", "mouseScrolled", "mouseDragged", "mouseReleased");

	private static JavaClasses screens;

	@BeforeAll
	static void importScreens() {
		screens = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages(SCREEN_PACKAGE);
		// Floor: an empty import would make the check below pass by finding nothing.
		assertTrue(screens.contain(BASE),
				"screen classes must be on the test classpath, or this rule is vacuous");
	}

	@Test
	void everyInputMethodOfTheBaseIsFinal() {
		JavaClass base = screens.get(BASE);
		List<String> found = new ArrayList<>();
		List<String> notFinal = new ArrayList<>();
		for (JavaMethod method : base.getMethods()) {
			Set<JavaModifier> modifiers = method.getModifiers();
			if (!FINAL_INPUT.contains(method.getName())
					|| modifiers.contains(JavaModifier.BRIDGE) || modifiers.contains(JavaModifier.SYNTHETIC)) {
				continue;
			}
			found.add(method.getName());
			if (!modifiers.contains(JavaModifier.FINAL)) {
				notFinal.add(method.getFullName());
			}
		}
		// Floor: a renamed or removed override would leave nothing to check.
		assertTrue(found.containsAll(FINAL_INPUT),
				"MachineScreen must override every routed input method " + FINAL_INPUT + ", found " + found);
		assertTrue(notFinal.isEmpty(), "MachineScreen input methods must be final so a screen answers through "
				+ "the control* hooks, after the overlays (MOD-762, MOD-793): " + notFinal);
	}
}
