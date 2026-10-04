package dev.alaindustrial.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MOD-693 (rule R-GUI-14, origin MOD-031): a machine screen that draws the energy bar also answers a
 * hover over it with "X / max EU". Read from the compiled bytecode.
 *
 * <p>Since MOD-716 (CLI-3) a screen no longer draws the bar itself: it declares it by overriding
 * {@code MachineScreen#energyBar()}, and the base draws the bar and its tooltip. So the rule has two
 * halves: only {@code MachineScreen} calls {@code renderEnergyBar}, and it calls
 * {@code renderEnergyTooltip} too; and a screen that opts out of the base's tooltip by overriding
 * {@code energyTooltip()} must call {@code renderEnergyTooltip} itself (the assembler does, on its Work
 * tab), or the bar goes quiet again.
 *
 * <p>The Mob Repeller family drew the bar and never the tooltip; that is the defect this pins. The
 * four solar panel screens were in the same state and waited on the owner, who decided the bar behaves
 * the same on every screen (MOD-693, decision 13): {@link #AWAITING_OWNER} is empty. The list stays as
 * the one place an exception would go, and it is still checked in the other direction, so an entry
 * cannot outlive its reason.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: {@code :common}'s pitest
 * lane targets {@code dev.alaindustrial.*Test}, and there is nothing to mutate in a bytecode check.
 */
class EnergyBarTooltipRules {

	private static final String SCREEN_PACKAGE = "dev.alaindustrial.client.screen";
	private static final String BASE = SCREEN_PACKAGE + ".MachineScreen";

	/**
	 * Screens that opt out of the base's tooltip without drawing their own, pending an owner's answer.
	 * Empty since MOD-693 (decision 13 gave the four solar panel screens their tooltip).
	 */
	private static final Set<String> AWAITING_OWNER = Set.of();

	private static JavaClasses screens;

	@BeforeAll
	static void importScreens() {
		screens = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages(SCREEN_PACKAGE);
		// Floor: an empty import would make every check below pass by finding nothing.
		assertTrue(screens.contain(BASE),
				"screen classes must be on the test classpath, or this rule is vacuous");
	}

	private static boolean calls(JavaClass javaClass, String methodName) {
		for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
			if (call.getTarget().getName().equals(methodName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code javaClass} or one of its superclasses below {@code MachineScreen} calls
	 * {@code methodName} — the base's own call is the default the opt-out switched off, so it does not count.
	 */
	private static boolean callsBelowBase(JavaClass javaClass, String methodName) {
		Optional<JavaClass> current = Optional.of(javaClass);
		while (current.isPresent() && current.get().getPackageName().startsWith("dev.alaindustrial.")
				&& !current.get().getName().equals(BASE)) {
			if (calls(current.get(), methodName)) {
				return true;
			}
			current = current.get().getRawSuperclass();
		}
		return false;
	}

	private static boolean declares(JavaClass javaClass, String methodName) {
		return javaClass.getMethods().stream().anyMatch(method -> method.getName().equals(methodName));
	}

	@Test
	void onlyTheBaseDrawsTheBarAndItDrawsTheTooltipToo() {
		JavaClass base = screens.get(BASE);
		assertTrue(calls(base, "renderEnergyBar") && calls(base, "renderEnergyTooltip"),
				"MachineScreen must draw the declared bar and its tooltip (R-GUI-14)");
		List<String> byHand = new ArrayList<>();
		for (JavaClass screen : screens) {
			if (!screen.getName().equals(BASE) && calls(screen, "renderEnergyBar")) {
				byHand.add(screen.getName());
			}
		}
		assertTrue(byHand.isEmpty(), "declare the bar with energyBar() instead of drawing it by hand: " + byHand);
	}

	@Test
	void everyEnergyBarHasItsTooltip() {
		List<String> declarers = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (JavaClass screen : screens) {
			if (screen.getName().equals(BASE)) {
				continue;
			}
			if (declares(screen, "energyBar")) {
				declarers.add(screen.getName());
			}
			if (declares(screen, "energyTooltip") && !callsBelowBase(screen, "renderEnergyTooltip")
					&& !AWAITING_OWNER.contains(screen.getName())) {
				missing.add(screen.getName());
			}
		}
		// Floor: two dozen screens declare a bar; none found means the method name changed.
		assertTrue(declarers.size() > 10, "found too few energyBar() declarations to trust: " + declarers);
		assertTrue(missing.isEmpty(), "these screens switch the base's energy tooltip off and never draw their "
				+ "own (R-GUI-14): " + missing);
	}

	@Test
	void awaitingOwnerListIsNotStale() {
		List<String> stale = new ArrayList<>();
		for (String name : AWAITING_OWNER) {
			if (!screens.contain(name)) {
				stale.add(name + " (class gone)");
				continue;
			}
			JavaClass screen = screens.get(name);
			if (!declares(screen, "energyTooltip") || callsBelowBase(screen, "renderEnergyTooltip")) {
				stale.add(name + " (no longer draws a bar without its tooltip)");
			}
		}
		assertTrue(stale.isEmpty(), "remove from AWAITING_OWNER: " + stale);
	}
}
