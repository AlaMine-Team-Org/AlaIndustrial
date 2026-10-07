package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/**
 * A block-entity renderer draws glass without depth writes (MOD-780) — against the compiled bytecode, like
 * {@link ArchitectureRules}, of which it is the translucency chapter (kept in its own class:
 * {@code ArchitectureRules} is at the size ceiling of coding.md §10). Same import as there.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class TranslucentSheetRules {

	/**
	 * A method allowed to draw with the depth-writing translucent sheet, and why. {@code method} is the name
	 * of the code unit ArchUnit files the call under: a call inside a lambda counts for the method that
	 * declares the lambda (the static initialiser for {@code SPRITE.renderType(ignored -> ...)} in a field),
	 * not for the synthetic {@code lambda$...} method the compiler emits — so an entry exempts the lambdas
	 * written in its method too. Each entry names a method whose only such call is the liquid.
	 */
	record AllowedSite(String owner, String method, String reason) {

		boolean matches(JavaCodeUnit codeUnit) {
			return owner.equals(codeUnit.getOwner().getName()) && method.equals(codeUnit.getName());
		}
	}

	/** The banned call: vanilla's translucent item sheet on the block atlas, which writes depth. */
	static final String SHEETS = "net.minecraft.client.renderer.Sheets";
	static final String DEPTH_WRITING_SHEET = "translucentBlockItemSheet";

	/** The renderers the rule covers: every block-entity renderer of the mod lives here. */
	static final String RENDER_PACKAGE = "dev.alaindustrial.client.render..";

	private static final String RENDER = "dev.alaindustrial.client.render.";
	private static final String LIQUID = "a liquid writes depth, or water behind it would be blended over it; "
			+ "at its opacity the water behind is hardly visible anyway (MOD-780, owner decision 2)";

	private static final String GLASS_OVER_LIQUID = "glass in front of, or inside, the mod's own liquid shares the "
			+ "liquid's sheet while the liquid is drawn: translucent custom geometry is batched per render type in "
			+ "no fixed order, and only one shared, sorted buffer keeps the liquid from being blended over the "
			+ "glass (MOD-780, refined decision 1)";

	/**
	 * The sites that keep the depth-writing sheet (MOD-780). The three liquids (owner decision 2): the fluid
	 * prism in the portable tank, the incubator's bath and the water in its sight glass. And the glass that
	 * encloses them, which takes the liquid's sheet only while the liquid is drawn (refined decision 1) — the
	 * tank's panes, the incubator's ring and sight glass, each chosen in its renderer's {@code glassType}.
	 * Each is a direct call in the named method; {@code ArchitectureRulesNegativeControl} fails if an entry
	 * stops calling the sheet, so an exception cannot outlive its site.
	 */
	static final List<AllowedSite> LIQUID_SITES = List.of(
			new AllowedSite(RENDER + "FluidTankBlockEntityRenderer", "submit", LIQUID),
			new AllowedSite(RENDER + "IncubatorBlockEntityRenderer", "submitBath", LIQUID),
			new AllowedSite(RENDER + "IncubatorBlockEntityRenderer", "submitGauge", LIQUID),
			new AllowedSite(RENDER + "FluidTankBlockEntityRenderer", "glassType", GLASS_OVER_LIQUID),
			new AllowedSite(RENDER + "IncubatorBlockEntityRenderer", "glassType", GLASS_OVER_LIQUID));

	/**
	 * A renderer does not draw with {@code Sheets.translucentBlockItemSheet()} (MOD-780). A block entity's
	 * translucent custom geometry is drawn before the translucent terrain layer, and that sheet writes depth,
	 * so water, stained glass, ice and slime behind such a surface fail the depth test and vanish — the defect
	 * of the capsule door (MOD-777) and of six more glass surfaces. Glass is drawn with
	 * {@code TranslucentTypes.blockSheetNoDepthWrite()}, preceded by {@code blockSheetSolidTexels()} when its
	 * texture has solid texels. The liquids of {@link #LIQUID_SITES}, and the glass that shares their sheet
	 * while they are drawn, are the named exceptions.
	 *
	 * <p>The ban could not be a tautology: {@code ArchitectureRulesNegativeControl} proves on a fixture that
	 * the condition reports a direct call, a call inside a lambda and a method reference while letting an
	 * allowed site through, and on the production classes that every allowed site really calls the sheet —
	 * which also pins the owner and the method name spelled here. A {@code noClasses().should(...)} rule: the
	 * condition reports {@code satisfied}.
	 */
	@ArchTest
	static final ArchRule renderersDrawGlassWithoutDepthWrites = noClasses()
			.that().resideInAPackage(RENDER_PACKAGE)
			.should(callTheDepthWritingSheet(SHEETS, DEPTH_WRITING_SHEET, LIQUID_SITES))
			.because("a depth-writing translucent surface of a block entity hides the water behind it (MOD-777, "
					+ "MOD-780): draw glass with TranslucentTypes.blockSheetNoDepthWrite(), after "
					+ "blockSheetSolidTexels() when the texture has solid texels; only the liquids listed in "
					+ "TranslucentSheetRules.LIQUID_SITES (and the glass sharing their sheet) keep "
					+ "Sheets.translucentBlockItemSheet()");

	/**
	 * A call to, or a method reference of, {@code owner.method} outside the {@code allowed} sites. Reports
	 * {@code satisfied}: {@code noClasses().should(...)} inverts it. Parametrised so the negative control can
	 * aim it at a stand-in: {@code Sheets} is a Minecraft class this lane's classpath does not carry.
	 */
	static ArchCondition<JavaClass> callTheDepthWritingSheet(String owner, String method, List<AllowedSite> allowed) {
		return new ArchCondition<>("draw with the depth-writing translucent sheet") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					if (allowed.stream().anyMatch(site -> site.matches(codeUnit))) {
						continue;
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						report(item, events, codeUnit, call.getTargetOwner(), call.getName(), "");
					}
					for (JavaMethodReference reference : codeUnit.getMethodReferencesFromSelf()) {
						report(item, events, codeUnit, reference.getTargetOwner(), reference.getName(),
								"a reference to ");
					}
				}
			}

			private void report(JavaClass item, ConditionEvents events, JavaCodeUnit codeUnit, JavaClass target,
					String name, String shape) {
				if (owner.equals(target.getName()) && method.equals(name)) {
					events.add(SimpleConditionEvent.satisfied(item, shape + target.getSimpleName() + "." + name
							+ " in " + codeUnit.getFullName()
							+ " — draw glass with TranslucentTypes.blockSheetNoDepthWrite()"));
				}
			}
		};
	}
}
