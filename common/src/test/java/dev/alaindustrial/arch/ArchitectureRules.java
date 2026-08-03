package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Architectural boundaries of {@code common/}, enforced against the compiled bytecode (MOD-308).
 *
 * <p><b>Why this exists.</b> Both rules below were written down in the repository contributor guides
 * and enforced by nothing — but they are not equally exposed, and it is worth being
 * precise about which is which (both were probed by deliberately breaking them):
 * <ul>
 *   <li><b>Loader dependency.</b> A plain {@code import net.fabricmc…} in {@code common} already
 *       fails to COMPILE — the loader jars are genuinely absent from this subproject's classpath.
 *       So this rule is a backstop, not the primary gate: it covers what the compile classpath does
 *       not, i.e. a dependency that arrives through a shaded/transitive artifact, and it names the
 *       Team Reborn energy API explicitly so the Fabric energy seam cannot creep inward.</li>
 *   <li><b>Eager registration.</b> This one the compiler cannot see at all. A {@code Registry.register}
 *       in a {@code common} static field compiles and runs fine on Fabric, then throws
 *       {@code already frozen} on NeoForge, where registries are sealed before mod init — a defect
 *       that ships looking green on the loader you happened to test.</li>
 * </ul>
 *
 * <p><b>Why ArchUnit rather than a text search.</b> These rules are about <i>what the code does</i>,
 * not what it looks like. A grep for {@code net.fabricmc} misses a fully-qualified reference built
 * through a constant or reached transitively, and flags the same string inside a javadoc block. ArchUnit
 * reads the constant pool, so it sees the real dependency graph and nothing else.
 *
 * <p><b>Naming.</b> The class deliberately does NOT end in {@code Test}: {@code :common}'s pitest lane
 * targets {@code dev.alaindustrial.*Test}, and a mutation run over these rules would spend minutes
 * proving that architecture assertions survive arithmetic mutants. JUnit still discovers the class —
 * ArchUnit's JUnit 5 engine finds it by the {@link AnalyzeClasses} annotation, not by name.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRules {

	/**
	 * {@code common/} holds all game logic and must compile and run identically on both loaders, so
	 * nothing in it may reach into a loader API. The seam is the other way round: {@code common}
	 * declares neutral abstractions ({@code EnergyPort}, {@code FluidPort}), and each loader adapts
	 * its own API to them.
	 */
	@ArchTest
	static final ArchRule commonDoesNotDependOnLoaderApis = noClasses()
			.should().dependOnClassesThat().resideInAnyPackage(
					"net.fabricmc..", "net.neoforged..", "team.reborn..")
			.because("common/ must compile and run on BOTH loaders: a loader type here is an "
					+ "unsatisfied dependency on the other loader at runtime. Adapt the loader API "
					+ "to a neutral abstraction (EnergyPort/FluidPort) in that loader's subproject "
					+ "instead");

	/**
	 * The NeoForge failure mode this guards is not hypothetical: its registries are frozen before mod
	 * init, so any {@code Registry.register} reached from a {@code common} class's static initializer
	 * throws {@code already frozen} the moment that class is loaded there. Fabric, which registers
	 * eagerly, is perfectly happy — so the defect ships looking green.
	 *
	 * <p>Calls from ordinary methods are fine and stay allowed: {@code ModRecipes} and
	 * {@code ModCriteria} register from methods the loader entrypoint invokes at the right moment.
	 * The rule is specifically about {@code <clinit>} — which is where a {@code static final} field
	 * initializer ends up.
	 */
	@ArchTest
	static final ArchRule noEagerRegistrationFromStaticInitializers = classes()
			.should(notCallFromStaticInitializer("Registry", "register"))
			.because("NeoForge freezes its registries before mod init: a register reached from a "
					+ "static field initializer in common/ throws `already frozen` as soon as the "
					+ "class loads there, while Fabric shows no symptom at all");

	private static ArchCondition<JavaClass> notCallFromStaticInitializer(String ownerSimpleName,
			String methodName) {
		String description = "not call " + ownerSimpleName + "." + methodName
				+ " from a static initializer";
		return new ArchCondition<>(description) {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					// `<clinit>` is the JVM name of the static initializer; a `static final X Y = …`
					// field initializer is compiled into it, which is exactly what the rule targets.
					if (!"<clinit>".equals(codeUnit.getName())) {
						continue;
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						if (ownerSimpleName.equals(call.getTargetOwner().getSimpleName())
								&& methodName.equals(call.getName())) {
							events.add(SimpleConditionEvent.violated(item, call.getDescription()));
						}
					}
				}
			}
		};
	}
}
