package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The backend-agnostic rendering rule, extended over NeoForge's own classes (MOD-497).
 *
 * <p><b>Why a second copy of the rule exists at all.</b> ArchUnit can only see the classpath of the
 * module it runs in, and {@code :common}'s test classpath has no NeoForge output — so
 * {@code ArchitectureRules.renderingStaysBackendAgnostic} guards {@code common/} and nothing else.
 * The classes it cannot reach are exactly the ones most likely to draw: the JEI compatibility layer
 * renders recipe categories, tooltips and GUI overlays of its own.
 *
 * <p><b>Why plain {@code @Test} instead of {@code @ArchTest}.</b> This lane is ModDevGradle's
 * {@code unitTest}, which runs under FML on a transformed classloader. ArchUnit's JUnit 5 integration
 * contributes its own {@code TestEngine} and discovers rules by annotation; if that discovery were to
 * come up empty here, the result is not a failure but a green run over zero rules — the silent-pass
 * failure mode this repository has been bitten by before. A plain {@code @Test} runs on the Jupiter
 * engine that this lane already proves works, and the floor assertion below makes an empty import
 * loud instead of green.
 *
 * <p><b>The package list is duplicated, on purpose and under guard.</b> It cannot be shared: a test
 * class in {@code :common} is not on this module's classpath. So the list is restated here and
 * {@code docs/tools/arch_check.py} (rule {@code backend-package-lists-agree}) fails the build if the
 * two ever differ — the drift would otherwise be silent, leaving this zone open while the other looks
 * closed.
 */
class NeoForgeBackendAgnosticRenderingTest {

	/** Must stay identical to {@code ArchitectureRules.BACKEND_SPECIFIC_PACKAGES}. */
	private static final String[] BACKEND_SPECIFIC_PACKAGES = {
		"org.lwjgl.opengl..",
		"org.lwjgl.vulkan..",
		"com.mojang.blaze3d.opengl..",
		"com.mojang.blaze3d.vulkan..",
	};

	private static JavaClasses productionClasses;

	@BeforeAll
	static void importProductionClasses() {
		productionClasses = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("dev.alaindustrial");

		// Floor. Without this, an import that resolved nothing would make every check below pass by
		// having nothing to complain about.
		assertTrue(productionClasses.contain("dev.alaindustrial.registry.neoforge.ModBlocksNeoForge"),
				"NeoForge production classes must be on this lane's classpath, or the rule below is vacuous");
		assertTrue(productionClasses.contain("dev.alaindustrial.compat.jei.MachineInfoJeiCategory"),
				"the JEI compat layer is the drawing code this rule exists for — if it is not imported, "
						+ "the zone that most needs guarding is the one being skipped");
	}

	@Test
	void neoForgeCodeDependsOnNoGraphicsBackend() {
		noClasses()
				.should().dependOnClassesThat().resideInAnyPackage(BACKEND_SPECIFIC_PACKAGES)
				.because("a direct graphics-backend call pins the mod to one backend: OpenGL is being "
						+ "retired in favour of Vulkan, and a direct Vulkan call breaks every player "
						+ "still on OpenGL. Draw through the Blaze3D abstraction instead")
				.check(productionClasses);
	}

	/**
	 * Same capability probe as on the {@code :common} lane: proof that a package ban of this shape can
	 * still SEE something from here. {@code com.mojang.blaze3d.vertex..} is the sibling package the
	 * real rule deliberately allows.
	 *
	 * <p>The classes that satisfy it are the {@code common/} renderers, not NeoForge ones — this module
	 * compiles {@code common/src/main/java} into its own output (see {@code multiloader-loader.gradle}),
	 * so they are legitimately part of what this lane scans. Nothing under {@code neoforge/src}
	 * references Blaze3D at all today. Should that wiring ever change to consuming {@code :common} as a
	 * plain jar, this probe would start measuring a different set than it reads like — the floor
	 * assertions above are what keep the NeoForge-specific half honest regardless.
	 */
	@Test
	void aBackendPackageBanCanStillSeeBlaze3dFromThisLane() {
		EvaluationResult result = noClasses()
				.should().dependOnClassesThat().resideInAnyPackage("com.mojang.blaze3d.vertex..")
				.evaluate(productionClasses);

		assertTrue(result.hasViolation(),
				"a package ban shaped exactly like the rule above reported nothing against "
						+ "com.mojang.blaze3d.vertex.., which the renderers demonstrably use — so the "
						+ "real rule is blind on this lane too, and its green means nothing");
	}
}
