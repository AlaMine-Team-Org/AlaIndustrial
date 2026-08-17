package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static dev.alaindustrial.arch.ArchitectureRules.callStaticRateShortcutOutsideConstructor;
import static dev.alaindustrial.arch.ArchitectureRules.notCallFromStaticInitializer;
import static dev.alaindustrial.arch.ArchitectureRules.useUnorderedCollections;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Negative control for the custom conditions in {@link ArchitectureRules} (MOD-435): proof that each
 * of them CAN fail, on a fixture package of deliberate violators.
 *
 * <p><b>Why.</b> The production rules run against {@code common/} and are green there — and green is
 * exactly what a rule that cannot fail looks like. Two of them were in that state from MOD-404 to
 * MOD-313: a condition handed to {@code noClasses().should(…)} is inverted by
 * {@code ArchConditions.never}, so reporting {@code violated} inside it reads as "no problem", and
 * the rule waved through the very shape it was written for. That was found by hand, by widening the
 * zone over a package known to be dirty. This class makes that probe permanent: it composes the SAME
 * condition objects the production rules use into rules scoped to
 * {@code dev.alaindustrial.arch.fixture}, evaluates them there, and requires a violation that names
 * the violator. Flip {@code satisfied} back to {@code violated} in a condition and the matching test
 * here goes red — probed on 2026-08-17 (see the task log of MOD-435).
 *
 * <p><b>Why a clean counterpart next to every violator.</b> A control that only demands "some
 * violation" would also pass if the condition flagged every class it saw. So each fixture pair has a
 * clean class using the idiom the rule points to as the fix, and the test asserts that one is ABSENT
 * from the report. Both halves are needed: the violator proves the rule can fail, the clean class
 * proves it fails for the right reason.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, on purpose: {@code :common}'s pitest lane targets
 * {@code dev.alaindustrial.*Test}, and there is nothing to mutate here. JUnit discovers the class by
 * its {@code @Test} methods, not by name; {@code gen_test_coverage.py} counts it the same way.
 *
 * <p><b>Fixtures stay out of the production run.</b> {@link ArchitectureRules} imports with
 * {@code ImportOption.DoNotIncludeTests}, whose Gradle pattern excludes {@code build/classes/…/test/}
 * — where the fixture package is compiled to. That is what lets a deliberate {@code new HashSet<>()}
 * live in the test tree without turning the production rules red.
 */
class ArchitectureRulesNegativeControl {

	private static final String FIXTURE_PACKAGE = "dev.alaindustrial.arch.fixture";

	private static JavaClasses fixtures;

	@BeforeAll
	static void importFixtures() {
		fixtures = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);
		assertTrue(fixtures.contain(FIXTURE_PACKAGE + ".UnorderedCollectionViolator"),
				"the fixture package must be on the test classpath, or every check below is vacuous");
	}

	private static String evaluateExpectingViolation(ArchRule rule) {
		EvaluationResult result = rule.evaluate(fixtures);
		assertTrue(result.hasViolation(),
				"rule '" + rule.getDescription() + "' must fail on the fixture package");
		return result.getFailureReport().toString();
	}

	private static void assertNoViolation(ArchRule rule) {
		EvaluationResult result = rule.evaluate(fixtures);
		assertFalse(result.hasViolation(), () -> "rule '" + rule.getDescription()
				+ "' must stay green on the clean fixture, but reported:\n"
				+ result.getFailureReport());
	}

	@Test
	void unorderedCollectionsConditionFailsOnEveryShapeItClaimsToSee() {
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAPackage(FIXTURE_PACKAGE)
				.should(useUnorderedCollections()));

		assertTrue(report.contains("UnorderedCollectionViolator"), report);
		// One line per accessor the condition reads. Losing one of them (as MOD-313 found for
		// constructor references) is a silent partial blindness a plain hasViolation() would hide.
		assertTrue(report.contains("UnorderedCollectionViolator.constructorCall("), report);
		assertTrue(report.contains("UnorderedCollectionViolator.constructorReference("), report);
		assertTrue(report.contains("UnorderedCollectionViolator.populatedFactory("), report);
		assertTrue(report.contains("UnorderedCollectionViolator.collectorToSet("), report);
		assertFalse(report.contains("OrderedCollectionUser"), report);
	}

	@Test
	void unorderedCollectionsConditionStaysGreenOnOrderedIdioms() {
		assertNoViolation(noClasses()
				.that().haveSimpleName("OrderedCollectionUser")
				.should(useUnorderedCollections()));
	}

	@Test
	void staticRateShortcutConditionFailsOutsideConstructorOnly() {
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAPackage(FIXTURE_PACKAGE)
				.should(callStaticRateShortcutOutsideConstructor()));

		assertTrue(report.contains("StaticRateShortcutViolator.drainPerTick("), report);
		// The constructor of the SAME class calls a shortcut too, and must not be reported: seeding
		// from <init> is the allowed case, and a rule that flagged it would be red on every machine.
		assertFalse(report.contains("StaticRateShortcutViolator.<init>("), report);
		assertFalse(report.contains("ConstructorSeededMachine"), report);
	}

	@Test
	void staticRateShortcutConditionStaysGreenOnConstructorSeeding() {
		assertNoViolation(noClasses()
				.that().haveSimpleName("ConstructorSeededMachine")
				.should(callStaticRateShortcutOutsideConstructor()));
	}

	@Test
	void eagerRegistrationConditionFailsOnStaticInitializerOnly() {
		String report = evaluateExpectingViolation(classes()
				.that().resideInAPackage(FIXTURE_PACKAGE)
				.should(notCallFromStaticInitializer("Registry", "register")));

		assertTrue(report.contains("EagerRegistrationViolator"), report);
		assertFalse(report.contains("LazyRegistrationUser"), report);
	}

	@Test
	void eagerRegistrationConditionStaysGreenOnMethodCalls() {
		assertNoViolation(classes()
				.that().haveSimpleName("LazyRegistrationUser")
				.should(notCallFromStaticInitializer("Registry", "register")));
	}
}
