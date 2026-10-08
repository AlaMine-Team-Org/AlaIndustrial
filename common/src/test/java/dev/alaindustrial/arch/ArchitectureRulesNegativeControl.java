package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static dev.alaindustrial.arch.ArchitectureRules.callForTestOutside;
import static dev.alaindustrial.arch.ArchitectureRules.callStaticRateShortcutOutsideConstructor;
import static dev.alaindustrial.arch.ArchitectureRules.forTestCaller;
import static dev.alaindustrial.arch.ArchitectureRules.notCallFromStaticInitializer;
import static dev.alaindustrial.arch.ArchitectureRules.readBalanceKnobsFromConfig;
import static dev.alaindustrial.arch.ArchitectureRules.readBalanceKnobsFromConfigIn;
import static dev.alaindustrial.arch.ArchitectureRules.startsAnotherManifestInitialiser;
import static dev.alaindustrial.arch.ArchitectureRules.useUnorderedCollections;
import static dev.alaindustrial.arch.ArchitectureRules.writeAKnobField;
import static dev.alaindustrial.arch.TranslucentSheetRules.callTheDepthWritingSheet;
import static dev.alaindustrial.arch.VersionedApiRules.callAFacadeOnlyMember;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.alaindustrial.Config;
import dev.alaindustrial.arch.TranslucentSheetRules.AllowedSite;
import dev.alaindustrial.arch.VersionedApiRules.FacadeOnlyMember;
import dev.alaindustrial.client.ServerBalance;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

	/**
	 * MOD-711: {@code contentDomainsInitialiseOnTheirOwn} can fail, and for the right reason. In the fixture
	 * package {@code manifest}, {@code AggregatorReaderContent} reads the aggregator's list back and
	 * {@code NeighbourReaderContent} names another domain's constant — both reported; {@code CleanContent}
	 * builds the aggregator's nested record and reads only its own constants — not reported, and neither is
	 * {@code Aggregator} itself, whose own members are not an access to "another" class.
	 */
	@Test
	void contentDomainRuleFailsOnAggregatorAndNeighbourReadsOnly() {
		String manifest = FIXTURE_PACKAGE + ".manifest";
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAPackage(manifest)
				.should().accessTargetWhere(startsAnotherManifestInitialiser(manifest + ".Aggregator", manifest)));

		assertTrue(report.contains("AggregatorReaderContent"), report);
		assertTrue(report.contains("NeighbourReaderContent"), report);
		assertFalse(report.contains("CleanContent"), report);
	}

	/**
	 * MOD-703, batch 13 — {@code versionedApiIsCalledOnlyThroughItsFacade}: outside the facade package the
	 * condition reports the overload listed by its first parameter, the member listed by name alone and a method
	 * reference to it ({@code ApiCallViolator}); the facade's own calls ({@code compat.StandInFacade}), work done
	 * through the facade and the overload both lines share ({@code FacadeUser}) are not reported. The stand-in
	 * plays the Minecraft owners, which this lane's classpath does not carry; the rule is composed the way the
	 * production one is — outside the facade package, the same condition.
	 */
	@Test
	void facadeOnlyMemberConditionFailsOutsideTheFacadeOnly() {
		String facade = FIXTURE_PACKAGE + ".facade";
		String api = facade + ".StandInApi";
		String standInFacade = facade + ".compat.StandInFacade";
		List<FacadeOnlyMember> members = List.of(
				new FacadeOnlyMember(Set.of(api), "turn", api + "$Quaternion", standInFacade),
				new FacadeOnlyMember(Set.of(), "grow", null, standInFacade));
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAnyPackage(facade + "..")
				.and().resideOutsideOfPackages(facade + ".compat")
				.should(callAFacadeOnlyMember(members)));

		assertTrue(report.contains("StandInApi.turn in " + facade + ".ApiCallViolator.listedOverload("), report);
		assertTrue(report.contains("StandInApi.grow in " + facade + ".ApiCallViolator.listedByName("), report);
		assertTrue(report.contains("a reference to StandInApi.grow in " + facade + ".ApiCallViolator.reference("),
				report);
		assertFalse(report.contains("FacadeUser"), report);
		assertFalse(report.contains("StandInFacade."), report);
	}

	/**
	 * MOD-703, batch 13 — the production list of {@code versionedApiIsCalledOnlyThroughItsFacade} is no
	 * tautology on the line this lane runs on: every facade it names exists and calls at least one of the
	 * members listed for it (26.3: {@code PoseStack.rotate(Quaternionfc)}, the trio with a {@code BonemealSource},
	 * {@code drop(ItemStack, boolean, Prediction)}; 26.2: {@code mulPose(Quaternionfc)}, the trio without it,
	 * {@code drop(ItemStack, boolean)}). A member renamed by the next Minecraft version, or an owner spelled
	 * wrong, leaves its facade calling nothing on the list, and this goes red naming the facade.
	 */
	@Test
	void everyFacadeOfTheVersionedApiRuleCallsOneOfItsMembers() {
		Map<String, List<FacadeOnlyMember>> byFacade = new TreeMap<>();
		for (FacadeOnlyMember member : VersionedApiRules.FACADE_ONLY_MEMBERS) {
			byFacade.computeIfAbsent(member.facade(), facade -> new java.util.ArrayList<>()).add(member);
		}
		assertEquals(Set.of("dev.alaindustrial.compat.Bonemeal", "dev.alaindustrial.compat.ServerDrops",
				"dev.alaindustrial.compat.client.Poses"), byFacade.keySet());
		for (Map.Entry<String, List<FacadeOnlyMember>> entry : byFacade.entrySet()) {
			assertTrue(productionClasses.contain(entry.getKey()), entry.getKey() + " is not a production class");
			JavaClass facade = productionClasses.get(entry.getKey());
			boolean callsOne = facade.getCodeUnits().stream()
					.flatMap(codeUnit -> codeUnit.getMethodCallsFromSelf().stream())
					.anyMatch(call -> entry.getValue().stream().anyMatch(member -> member.matches(
							call.getTargetOwner(), call.getName(), call.getTarget().getRawParameterTypes())));
			assertTrue(callsOne, entry.getKey() + " calls none of the members listed for it in "
					+ "VersionedApiRules.FACADE_ONLY_MEMBERS — the ban on them would be a tautology on this line");
		}
	}

	/**
	 * MOD-780 — {@code renderersDrawGlassWithoutDepthWrites}: the condition reports the three shapes in which a
	 * renderer reaches the depth-writing sheet ({@code DepthWritingSheetViolator}: a direct call, a call inside a
	 * lambda — which ArchUnit files under the method that declares the lambda, here the static initialiser, not
	 * under the synthetic {@code lambda$...} method — and a method reference),
	 * and lets an allowed site and the cutout sheet through ({@code LiquidSheetUser}). The stand-in plays
	 * {@code Sheets}, which this lane's classpath does not carry.
	 */
	@Test
	void depthWritingSheetConditionFailsOutsideTheAllowedSitesOnly() {
		String sheet = FIXTURE_PACKAGE + ".sheet";
		List<AllowedSite> allowed = List.of(new AllowedSite(sheet + ".LiquidSheetUser", "submitLiquid", "a liquid"));
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAPackage(sheet)
				.should(callTheDepthWritingSheet(sheet + ".StandInSheets", "translucentBlockItemSheet", allowed)));

		String violator = sheet + ".DepthWritingSheetViolator.";
		assertTrue(report.contains("StandInSheets.translucentBlockItemSheet in " + violator + "direct("), report);
		assertTrue(report.contains("StandInSheets.translucentBlockItemSheet in " + violator + "<clinit>("), report);
		assertTrue(report.contains("a reference to StandInSheets.translucentBlockItemSheet in " + violator
				+ "reference("), report);
		assertFalse(report.contains("LiquidSheetUser"), report);
		assertFalse(report.contains("cutoutBlockItemSheet"), report);
	}

	/**
	 * MOD-780 — every allowed site of {@code renderersDrawGlassWithoutDepthWrites} still calls the depth-writing
	 * sheet in the production classes. A liquid moved to another method, or a site renamed, would otherwise
	 * leave a stale exception behind; and a {@code Sheets} owner or method name spelled wrong in the rule makes
	 * every site fail here instead of making the ban silently empty.
	 */
	@Test
	void everyAllowedLiquidSiteStillCallsTheDepthWritingSheet() {
		for (AllowedSite site : TranslucentSheetRules.LIQUID_SITES) {
			assertTrue(productionClasses.contain(site.owner()), site.owner() + " is not a production class");
			boolean calls = productionClasses.get(site.owner()).getCodeUnits().stream()
					.filter(site::matches)
					.flatMap(codeUnit -> codeUnit.getMethodCallsFromSelf().stream())
					.anyMatch(call -> call.getTargetOwner().getName().equals(TranslucentSheetRules.SHEETS)
							&& call.getName().equals(TranslucentSheetRules.DEPTH_WRITING_SHEET));
			assertTrue(calls, site.owner() + "." + site.method() + " no longer calls Sheets."
					+ TranslucentSheetRules.DEPTH_WRITING_SHEET + "() — drop its entry from "
					+ "TranslucentSheetRules.LIQUID_SITES");
		}
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

		assertTrue(report.contains("MachineRates.euPerTick() in"), report);
		assertTrue(report.contains("StaticRateShortcutViolator.drainPerTick("), report);
		assertTrue(report.contains("MachineRates.duration() in"), report);
		assertTrue(report.contains("StaticRateShortcutViolator.durationTicks("), report);
		// The constructor of the SAME class calls a shortcut too, and must not be reported: seeding
		// from <init> is the allowed case, and a rule that flagged it would be red on every machine.
		assertFalse(report.contains("StaticRateShortcutViolator.<init>("), report);
		assertFalse(report.contains("ConstructorSeededMachine"), report);
		// A machine without an upgrade panel has no chip to miss (MOD-710 batch 4b, the electric heater).
		assertFalse(report.contains("NoPanelRateReader"), report);
	}

	@Test
	void staticRateShortcutConditionStaysGreenOnConstructorSeeding() {
		assertNoViolation(noClasses()
				.that().haveSimpleName("ConstructorSeededMachine")
				.should(callStaticRateShortcutOutsideConstructor()));
	}

	@Test
	void staticRateShortcutConditionStaysGreenWithoutAnUpgradePanel() {
		assertNoViolation(noClasses()
				.that().haveSimpleName("NoPanelRateReader")
				.should(callStaticRateShortcutOutsideConstructor()));
	}

	/**
	 * MOD-703: the host list of {@code clientTypesStayInsideClientPackages} admits the client facades of
	 * {@code dev.alaindustrial.compat.client} and nothing else under {@code dev.alaindustrial.compat}.
	 *
	 * <p>The rule's real targets ({@code net.minecraft.client..}, {@code com.mojang.blaze3d..}) are not on
	 * this lane's classpath, so a fixture cannot reference them (see the backend probe below for why a
	 * counterfeit is worse than none). What this control pins is the other half of the rule — WHO may hold a
	 * client type — with the real {@link ArchitectureRules#CLIENT_TYPE_HOSTS} and a stand-in target: a class
	 * in {@code compat.fixture} (inside the facade package, outside its client half) must be reported, the
	 * same use from {@code compat.client.fixture} must not. Widen the host list to all of {@code compat} and
	 * the first assertion goes red; drop {@code compat.client} and the second does.
	 */
	@Test
	void clientTypeHostsAdmitCompatClientAndNotTheRestOfCompat() {
		// The stand-in lives OUTSIDE compat, so the rule always has a class to check even if the host list
		// were widened to all of compat — the failure is then the assertion below, not an empty rule.
		String standIn = FIXTURE_PACKAGE + ".clientstandin";
		JavaClasses facadeFixtures = new ClassFileImporter().importPackages(standIn,
				"dev.alaindustrial.compat.fixture", "dev.alaindustrial.compat.client.fixture");
		assertTrue(facadeFixtures.contain("dev.alaindustrial.compat.client.fixture.ClientFacadeFixture"),
				"the compat fixtures must be on the test classpath, or this control is vacuous");
		EvaluationResult result = noClasses()
				.that().resideOutsideOfPackages(ArchitectureRules.CLIENT_TYPE_HOSTS)
				.should().dependOnClassesThat().resideInAnyPackage(standIn + "..")
				.evaluate(facadeFixtures);
		assertTrue(result.hasViolation(), "a client type used under compat outside compat.client must be "
				+ "a violation of the host list, and nothing was reported");
		String report = result.getFailureReport().toString();
		assertTrue(report.contains("FacadeOutsideClientSubpackage"), report);
		assertFalse(report.contains("ClientFacadeFixture"), report);
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

	/**
	 * MOD-715 — the frozen core layer rule is only as good as its unfrozen condition. It must SEE the
	 * violations its baseline holds (a condition that matched nothing would freeze an empty store and stay
	 * green forever), and it must find none in the network kernels batch 1 cleaned: those are out of the
	 * baseline for good, so a block reference creeping back into one of them is red here even before the
	 * store notices it as "new".
	 */
	@Test
	void theCoreLayerRuleSeesTheBaselineAndTheNetworkKernelsAreClean() {
		assertTrue(ArchitectureRules.CORE_LAYER.evaluate(productionClasses).hasViolation(),
				"the core layer rule found no core class depending on block/registry — today's baseline is"
						+ " not empty, so the rule is blind and its frozen green means nothing");
		JavaClasses kernels = productionClasses.that(DescribedPredicate.describe("network kernels",
				(JavaClass c) -> NETWORK_KERNELS.contains(topLevelName(c))));
		assertEquals(NETWORK_KERNELS, new TreeSet<>(kernels.stream().map(c -> topLevelName(c)).toList()),
				"every network kernel must be imported, or the check below is vacuous");
		EvaluationResult result = ArchitectureRules.CORE_LAYER.evaluate(kernels);
		assertFalse(result.hasViolation(), () -> "a network kernel names a block or the registry again "
				+ "(MOD-715 batch 1 took them out): " + result.getFailureReport());
	}

	/** The name of {@code c}'s top-level class, so a kernel's nested classes count as the kernel. */
	private static String topLevelName(JavaClass c) {
		String name = c.getName();
		int nested = name.indexOf('$');
		return nested < 0 ? name : name.substring(0, nested);
	}

	/** The network kernels batch 1 of MOD-715 cut loose from {@code block}/{@code registry}. */
	private static final Set<String> NETWORK_KERNELS = new TreeSet<>(Set.of(
			"dev.alaindustrial.core.energy.NetworkManager",
			"dev.alaindustrial.core.energy.EnergyNetwork",
			"dev.alaindustrial.core.energy.EnergyTopologyCache",
			"dev.alaindustrial.core.energy.DirectAdjacencyDistributor",
			"dev.alaindustrial.core.fluid.FluidNetwork",
			"dev.alaindustrial.core.fluid.FluidNetworkManager",
			"dev.alaindustrial.core.item.ItemNetwork",
			"dev.alaindustrial.core.item.ItemNetworkManager"));

	/**
	 * The fixture package together with {@code Config} itself (MOD-695): the knob condition reads the
	 * {@code @Knob} annotation on the target field and the field reads INSIDE the target method, which
	 * needs {@code Config}'s own bytecode in the import rather than a bare classpath stub.
	 */
	private static JavaClasses fixturesWithConfig() {
		Set<Location> locations = new LinkedHashSet<>(Locations.ofPackage(FIXTURE_PACKAGE));
		locations.addAll(Locations.ofClass(Config.class));
		return new ClassFileImporter().importLocations(locations);
	}

	@Test
	void balanceKnobConditionFailsOnEveryShapeItClaimsToSee() {
		ArchRule rule = noClasses().that().resideInAPackage(FIXTURE_PACKAGE).should(readBalanceKnobsFromConfig());
		EvaluationResult result = rule.evaluate(fixturesWithConfig());
		assertTrue(result.hasViolation(), "rule '" + rule.getDescription() + "' must fail on the fixture package");
		String report = result.getFailureReport().toString();

		assertTrue(report.contains("ConfigKnobReader.fieldRead("), report);
		// a knob of a per-subsystem holder (ADR-034) is a balance knob too: the annotation decides, not the owner
		assertTrue(report.contains("SubsystemKnobHolder.fixtureKnob read in"), report);
		assertTrue(report.contains("ConfigKnobReader.holderFieldRead("), report);
		assertTrue(report.contains("ConfigKnobReader.shortcutCall("), report);
		assertTrue(report.contains("ConfigKnobReader.shortcutReference("), report);
		// Reading the knob through ServerBalance and handing it to a pure holder formula is the fix.
		assertFalse(report.contains("ServerBalanceKnobReader"), report);
	}

	/**
	 * MOD-710 batch 5: the predicate behind {@code energyKernelsReadNoKnobHolder} sees a dependency on a knob
	 * holder — on the fixture that reads {@code Config} — and the kernel name pattern matches the real kernels.
	 */
	@Test
	void knobHolderPredicateSeesAConfigReader() {
		EvaluationResult result = noClasses().that().haveSimpleName("ConfigKnobReader")
				.should().dependOnClassesThat(ArchitectureRules.isKnobHolder()).evaluate(fixturesWithConfig());
		assertTrue(result.hasViolation(), "a class that reads Config must depend on a knob holder");
		for (String kernel : List.of("EnergyNetwork", "DischargePlan", "DirectAdjacencyDistributor",
				"EnergyLineDistributor", "LineView")) {
			assertTrue(("dev.alaindustrial.core.energy." + kernel).matches(ArchitectureRules.ENERGY_KERNELS), kernel);
		}
	}

	@Test
	void balanceKnobConditionStaysGreenOnServerBalanceReads() {
		EvaluationResult result = noClasses().that().haveSimpleName("ServerBalanceKnobReader")
				.should(readBalanceKnobsFromConfig()).evaluate(fixturesWithConfig());
		assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
	}

	@Test
	void tooltipKnobConditionFailsOnTheTooltipBodyOnly() {
		ArchRule rule = noClasses().that().resideInAPackage(FIXTURE_PACKAGE)
				.should(readBalanceKnobsFromConfigIn(ArchitectureRules::isTooltipBody, "in an item tooltip"));
		EvaluationResult result = rule.evaluate(fixturesWithConfig());
		assertTrue(result.hasViolation(), "rule '" + rule.getDescription() + "' must fail on the fixture package");
		String report = result.getFailureReport().toString();

		assertTrue(report.contains("TooltipKnobReader.appendHoverText("), report);
		// The read inside the lambda must be caught too. ArchUnit 1.x attributes a lambda's accesses to the
		// method that declares it (the server run showed no lambda$ code unit in the report), so the lambda's
		// knob is reported against appendHoverText itself.
		assertTrue(report.contains("Config.teleporterMaxPoints read in "
				+ FIXTURE_PACKAGE + ".TooltipKnobReader.appendHoverText("), report);
		assertTrue(report.contains("Config.euPerXp read in "
				+ FIXTURE_PACKAGE + ".TooltipKnobReader.appendHoverText("), report);
		// MOD-716: the owner-declared block tooltip is scoped in too.
		assertTrue(report.contains("Config.euPerXp read in "
				+ FIXTURE_PACKAGE + ".TooltipKnobReader.machineTooltip("), report);
		assertTrue(report.contains("Config.teleporterMaxPoints read in "
				+ FIXTURE_PACKAGE + ".TooltipKnobReader.toolTooltip("), report);
		// The clean class reads Config too, but outside its tooltip; and the other fixtures have no tooltip.
		assertFalse(report.contains("TooltipBalanceReader"), report);
		assertFalse(report.contains("ConfigKnobReader"), report);
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

	/**
	 * MOD-710 — {@code gametestsWriteKnobsOnlyThroughConfigOverrides}: the condition fails on a plain
	 * assignment, a compound assignment and a write in a lambda, and stays quiet on a class that only
	 * reads a knob or writes through a reflected field (how {@code ConfigOverrides} writes).
	 */
	@Test
	void knobWriteConditionFailsOnEveryShapeItClaimsToSee() {
		ArchRule rule = noClasses().that().resideInAPackage(FIXTURE_PACKAGE).should(writeAKnobField());
		EvaluationResult result = rule.evaluate(fixturesWithConfig());
		assertTrue(result.hasViolation(), "rule '" + rule.getDescription() + "' must fail on the fixture package");
		String report = result.getFailureReport().toString();

		assertTrue(report.contains("Config.euPerXp written in " + FIXTURE_PACKAGE + ".KnobWriter.assign("), report);
		assertTrue(report.contains("SubsystemKnobHolder.fixtureKnob written in " + FIXTURE_PACKAGE
				+ ".KnobWriter.assignInHolder("), report);
		// A compound assignment is a GET and a SET; the SET is what is reported.
		assertTrue(report.contains("Config.euPerXp written in " + FIXTURE_PACKAGE + ".KnobWriter.compound("), report);
		// ArchUnit attributes a lambda's accesses to the declaring method or to the lifted lambda; either way the
		// write inside it must be reported.
		assertTrue(report.contains("Config.teleporterMaxPoints written in " + FIXTURE_PACKAGE + ".KnobWriter."),
				report);
		assertFalse(report.contains("KnobReaderOnly"), report);
	}

	@Test
	void knobWriteConditionStaysGreenOnReadsAndReflectedWrites() {
		EvaluationResult result = noClasses().that().haveSimpleName("KnobReaderOnly")
				.should(writeAKnobField()).evaluate(fixturesWithConfig());
		assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
	}

	/**
	 * MOD-710 — {@code forTestIsHeldOnlyByTheListedScenarios}: a call or a method reference of {@code forTest}
	 * from a method outside the allow-list is reported, the same call from a listed method is not. The real
	 * owner takes a Minecraft type the test classpath lacks, so the fixture's {@code ForTestHolder} stands in
	 * for it and the owner name is a parameter of the condition.
	 */
	@Test
	void forTestConditionFailsOffTheAllowListOnly() {
		Set<String> allowed = Set.of(FIXTURE_PACKAGE + ".ForTestCallerOnList.hold");
		String report = evaluateExpectingViolation(noClasses()
				.that().resideInAPackage(FIXTURE_PACKAGE)
				.and().doNotHaveSimpleName("ForTestHolder")
				.should(callForTestOutside(FIXTURE_PACKAGE + ".ForTestHolder", allowed)));

		assertTrue(report.contains("ForTestCallerOffList.hold("), report);
		assertTrue(report.contains("ForTestCallerOffList.reference(")
				|| report.contains("ForTestCallerOffList.lambda$reference"), report);
		assertFalse(report.contains("ForTestCallerOnList"), report);
	}

	@Test
	void forTestConditionStaysGreenForAListedCaller() {
		assertNoViolation(noClasses()
				.that().haveSimpleName("ForTestCallerOnList")
				.should(callForTestOutside(FIXTURE_PACKAGE + ".ForTestHolder",
						Set.of(FIXTURE_PACKAGE + ".ForTestCallerOnList.hold"))));
	}

	/**
	 * MOD-710 — the two gametest rules are only as good as their import. Floor: the production import
	 * contains the gametest source set's classes (the test runtime classpath carries them), so a write there
	 * WOULD be seen; and the allow-list is exactly the set of real {@code ConfigOverrides.forTest} callers —
	 * an entry nobody calls any more (the scenario moved or was deleted) is stale and fails here, the way a
	 * stale {@code LOADER_ONLY} entry fails the lane-parity gate. A new caller outside the list is already
	 * red in {@code forTestIsHeldOnlyByTheListedScenarios}.
	 */
	@Test
	void theForTestAllowListIsExactlyTheRealCallers() {
		assertTrue(productionClasses.contain(ArchitectureRules.CONFIG_OVERRIDES),
				"the gametest source set must be on the test classpath, or both gametest rules are vacuous");
		assertTrue(productionClasses.contain("dev.alaindustrial.gametest.OilScenarios"),
				"the scenario classes must be imported, or the knob-write rule is vacuous");
		Set<String> callers = new TreeSet<>();
		for (JavaClass javaClass : productionClasses) {
			for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
				for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
					if (ArchitectureRules.CONFIG_OVERRIDES.equals(call.getTargetOwner().getFullName())
							&& "forTest".equals(call.getName())) {
						callers.add(forTestCaller(codeUnit));
					}
				}
			}
		}
		assertEquals(new TreeSet<>(ArchitectureRules.FOR_TEST_USERS.keySet()), callers,
				"ArchitectureRules.FOR_TEST_USERS must list exactly the methods that call ConfigOverrides.forTest");
	}

	/**
	 * The fixture package with {@code Config} and {@code ServerBalance} (MOD-761): the reach condition must see
	 * that {@code ServerBalance} reads {@code Config} itself, or the clean twin would prove nothing about the
	 * boundary.
	 */
	private static JavaClasses fixturesWithBalance() {
		Set<Location> locations = new LinkedHashSet<>(Locations.ofPackage(FIXTURE_PACKAGE));
		locations.addAll(Locations.ofClass(Config.class));
		locations.addAll(Locations.ofClass(ServerBalance.class));
		return new ClassFileImporter().importLocations(locations);
	}

	private static String clientReachReport(Map<String, String> accepted, Map<String, String> pending) {
		ArchRule rule = noClasses().should(ClientBalanceReachRules.reachABalanceKnob(
				JavaClass.Predicates.resideInAnyPackage(FIXTURE_PACKAGE + ".clientstandin.."), accepted, pending));
		EvaluationResult result = rule.evaluate(fixturesWithBalance());
		assertTrue(result.hasViolation(), "rule '" + rule.getDescription() + "' must fail on the fixture package");
		return result.getFailureReport().toString();
	}

	/**
	 * MOD-761 — {@code clientCodeReachesNoBalanceKnob}: a knob two calls away through a helper, one behind a
	 * JDK supplier lambda (the supplier bridge) and one behind an interface (the overrides in subclasses) are
	 * reported, from client code and from an item's bar and tooltip image alike. The walk stops at
	 * {@code ServerBalance}, does not cross the mod's own functional interfaces, and lets an accepted entry
	 * through — and only because it is accepted.
	 */
	@Test
	void clientReachConditionSeesEveryBridgeAndStopsAtServerBalance() {
		String screen = FIXTURE_PACKAGE + ".clientstandin.IndirectKnobScreen.";
		String report = clientReachReport(Map.of(screen + "acceptedRead() -> "
				+ FIXTURE_PACKAGE + ".KnobHelper.accepted()", "fixture"), Map.of());

		assertTrue(report.contains(screen + "helperRead() -> " + FIXTURE_PACKAGE
				+ ".KnobHelper.twice() reads Config.euPerXp"), report);
		assertTrue(report.contains(screen + "suppliedRead() -> " + FIXTURE_PACKAGE
				+ ".SuppliedGrade.amount() reads Config.euPerXp"), report);
		assertTrue(report.contains(screen + "virtualRead(" + FIXTURE_PACKAGE + ".KnobShape) -> " + FIXTURE_PACKAGE
				+ ".KnobShapeImpl.amount() reads Config.euPerXp"), report);
		assertTrue(report.contains(screen + "abstractRead(" + FIXTURE_PACKAGE + ".AbstractKnobBase) -> "
				+ FIXTURE_PACKAGE + ".KnobBaseImpl.amount() reads Config.euPerXp"), report);
		assertTrue(report.contains(FIXTURE_PACKAGE + ".DisplayHookItem.getBarWidth() -> "), report);
		// another root reports its own reach: the stand-in that calls it is not reported for it
		assertFalse(report.contains("viaAnotherRoot"), report);
		assertTrue(report.contains(FIXTURE_PACKAGE + ".DisplayHookItem.getTooltipImage() -> "), report);
		assertFalse(report.contains("acceptedRead"), report);
		assertFalse(report.contains("BalanceScreen"), report);
		assertFalse(report.contains("DisplayHookItem.getBarColor"), report);

		assertTrue(clientReachReport(Map.of(), Map.of()).contains(screen + "acceptedRead() -> "),
				"the accepted path must be seen when nothing accepts it, or its absence above proves nothing");
		// accepted for one root only: another root reaching the same entry is still reported
		String otherRoot = clientReachReport(Map.of(screen + "helperRead() -> "
				+ FIXTURE_PACKAGE + ".KnobHelper.accepted()", "fixture"), Map.of());
		assertTrue(otherRoot.contains(screen + "acceptedRead() -> "),
				"an acceptance must name its root, not only its entry");
	}

	/**
	 * MOD-761 — the walk stops at a pending method (the last link of a path with a task of its own) and names the
	 * task instead of the knobs; a knob read on the way to it is still named. The fixture plays a tooltip builder
	 * reverted from ServerBalance to the holder, next to its pending capacity call.
	 */
	@Test
	void clientReachConditionStopsAtAPendingMethodButNamesReadsBeforeIt() {
		String screen = FIXTURE_PACKAGE + ".clientstandin.IndirectKnobScreen.pendingTooltip() -> " + FIXTURE_PACKAGE
				+ ".PendingTooltipBuilder.of() ";
		String report = clientReachReport(Map.of(), Map.of(FIXTURE_PACKAGE + ".PendingCapacity.capacity()", "MOD-000"));
		String pendingLine = "reaches " + FIXTURE_PACKAGE + ".PendingCapacity.capacity(), pending MOD-000";
		assertTrue(report.contains(screen + pendingLine), report);
		assertTrue(report.contains(screen + "reads Config.teleporterMaxPoints"), report);
		assertFalse(report.contains(screen + "reads Config.euPerXp"), report);
		assertTrue(clientReachReport(Map.of(), Map.of()).contains(screen + "reads Config.euPerXp"),
				"without the pending row the knob behind it must be named, or its absence above proves nothing");
	}

	/**
	 * MOD-761 — the frozen reach rule is only as good as its unfrozen condition: on the production classes it
	 * must SEE today's baseline (an empty store would stay green forever). When the baseline is empty, replace
	 * the freeze by the plain rule and drop this check.
	 */
	@Test
	void theClientReachRuleSeesTheBaseline() {
		assertTrue(ClientBalanceReachRules.CLIENT_REACH.evaluate(productionClasses).hasViolation(),
				"the client reach rule found nothing on the production classes — today's baseline is not empty,"
						+ " so the rule is blind and its frozen green means nothing");
	}

	/**
	 * MOD-761 — an accepted path and a pending entry are decisions about paths that exist: once a path is gone,
	 * its row goes too. Accepted rows whose entry class this lane does not import (the REI plugin of
	 * fabric/src/main) are records, not checked.
	 */
	@Test
	void acceptedAndPendingPathsAreStillReached() {
		List<String> lines = noClasses()
				.should(ClientBalanceReachRules.reachABalanceKnob(ClientBalanceReachRules.PRODUCTION_CLIENT_CODE,
						Map.of(), ClientBalanceReachRules.PENDING))
				.evaluate(productionClasses).getFailureReport().getDetails();
		Set<String> stale = new TreeSet<>();
		for (String pair : ClientBalanceReachRules.ACCEPTED.keySet()) {
			String root = pair.substring(0, pair.indexOf(" -> "));
			String entry = pair.substring(pair.indexOf(" -> ") + 4);
			String entryOwner = entry.substring(0, entry.lastIndexOf('.', entry.indexOf('(')));
			if (productionClasses.contain(entryOwner)
					&& lines.stream().noneMatch(line -> line.contains(root + " -> " + entry + " "))) {
				stale.add(pair);
			}
		}
		for (String method : ClientBalanceReachRules.PENDING.keySet()) {
			if (lines.stream().noneMatch(line -> line.contains(" reaches " + method + ", pending "))) {
				stale.add(method);
			}
		}
		assertTrue(stale.isEmpty(), "ClientBalanceReachRules.ACCEPTED/PENDING name paths no client code reaches any"
				+ " more — remove them: " + stale);
	}
}
