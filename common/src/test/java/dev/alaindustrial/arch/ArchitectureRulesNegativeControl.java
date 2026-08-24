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
import com.tngtech.archunit.core.importer.ImportOption;
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

	/**
	 * The real {@code common/} classes, imported the same way {@link ArchitectureRules} imports them
	 * (MOD-497) — {@code DoNotIncludeTests} keeps the fixture package out, so this is production only.
	 */
	private static JavaClasses productionClasses;

	@BeforeAll
	static void importFixtures() {
		fixtures = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);
		assertTrue(fixtures.contain(FIXTURE_PACKAGE + ".UnorderedCollectionViolator"),
				"the fixture package must be on the test classpath, or every check below is vacuous");

		productionClasses = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("dev.alaindustrial");
		// Floor. An empty or test-only import would make the capability probe below pass by finding
		// nothing to complain about — the exact failure mode it exists to rule out.
		assertTrue(productionClasses.contain("dev.alaindustrial.client.screen.MachineScreen"),
				"production classes must be on the test classpath, or the capability probe is vacuous");
		assertFalse(productionClasses.contain(FIXTURE_PACKAGE + ".UnorderedCollectionViolator"),
				"DoNotIncludeTests must exclude the fixture package from the production import");
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

	/**
	 * MOD-497: proof that {@code renderingStaysBackendAgnostic} is CAPABLE of failing — established
	 * without a fixture, because on this lane a fixture cannot exist.
	 *
	 * <p><b>Why no violator class.</b> The other controls here import a hand-written violator, and the
	 * obvious move would be a class calling {@code GL11.glEnable}. It does not compile: {@code :common}'s
	 * test classpath is deliberately Minecraft-free, so {@code org.lwjgl..} and {@code com.mojang.blaze3d..}
	 * are absent from it (this was tried first — 17 "package does not exist" errors). Faking them by
	 * declaring {@code org.lwjgl.opengl.GL11} inside our own test tree is worse than no control at all:
	 * {@code common/src} is a published path (docs/publishing/sync_paths.txt), so counterfeit LWJGL
	 * classes would ship to the public repository.
	 *
	 * <p><b>Why the rule works anyway, and what this test actually proves.</b> ArchUnit reads the
	 * PRODUCTION bytecode, where those names sit in the constant pool; it never needs to resolve them,
	 * which is why the neighbouring {@code clientTypesStayInsideClientPackages} guards
	 * {@code net.minecraft.client..} from this same Minecraft-free lane and has no fixture either. The
	 * open question is therefore not "is the rule written correctly" — it is fluent ArchUnit, not a
	 * custom condition, so the {@code satisfied}/{@code violated} inversion trap cannot apply — but
	 * "can a package ban of this exact shape still SEE anything from here". This test answers that
	 * empirically and permanently: it runs the same construction against
	 * {@code com.mojang.blaze3d.vertex..}, the sibling package the real rule deliberately allows and
	 * that 22 production classes depend on, and demands a violation.
	 *
	 * <p>So if the import ever goes blind — Minecraft dropped from the production classpath, the
	 * package renamed, ArchUnit changing what {@code dependOnClassesThat} reports — this goes red
	 * instead of the real rule going quietly, permanently green.
	 */
	@Test
	void aBackendPackageBanCanStillSeeBlaze3dFromThisLane() {
		EvaluationResult result = noClasses()
				.should().dependOnClassesThat().resideInAnyPackage("com.mojang.blaze3d.vertex..")
				.evaluate(productionClasses);

		assertTrue(result.hasViolation(),
				"a package ban shaped exactly like renderingStaysBackendAgnostic reported nothing "
						+ "against com.mojang.blaze3d.vertex.., which the renderers demonstrably use — "
						+ "so the real rule is blind too, and its green means nothing");
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
