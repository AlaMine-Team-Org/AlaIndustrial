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
 * <p><b>Why this exists.</b> The first two rules below were written down in the repository contributor
 * guides and enforced by nothing — but they are not equally exposed, and it is worth being
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

	/**
	 * Client-only Minecraft types stay inside the client packages (MOD-435).
	 *
	 * <p>A dedicated server ships without {@code net.minecraft.client} and {@code com.mojang.blaze3d}
	 * at all. A block, item or menu class that references one of them at its top level — a field type,
	 * a method signature, an {@code instanceof} — throws {@code NoClassDefFoundError} the moment the
	 * server classloads it, and nothing in the dev client, where those classes are always present, will
	 * show it. The mod's convention is an indirection: the item calls into a small class under
	 * {@code dev.alaindustrial.client..} from inside a {@code level.isClientSide()} guard, so the client
	 * class is only ever loaded on the logical client. {@code GuideBookClientAccess} was that indirection
	 * living in {@code item.misc}, one package away from where the guard is meaningful; this rule was
	 * red on it before the move and green after.
	 *
	 * <p><b>Deliberately NOT forbidden: depending on {@code dev.alaindustrial.client..}.</b> That is the
	 * indirection itself — {@code GuideBookItem} legitimately references the guarded client-package
	 * class. The rule guards the raw Minecraft client types, which is where the crash is.
	 * {@code mixin.client..} is exempt because its accessors and mixins target client classes by design
	 * and are only applied on the client.
	 *
	 * <p><b>Scope includes {@code common/src/gametest}.</b> The {@link AnalyzeClasses} import reads
	 * every {@code dev.alaindustrial} class on {@code :common}'s test runtime classpath, and
	 * {@link ImportOption.DoNotIncludeTests} excludes only {@code build/classes/<lang>/test/} — the
	 * {@code gametest} output directory stays in. So a loader-neutral scenario that calls
	 * {@code Minecraft.getInstance()} directly is flagged with the same advice; that is the intended
	 * answer — the loader-neutral scenarios run on a dedicated test server, and the L3 client lane
	 * (the stands that do touch client types) lives in {@code fabric/src/gametest}, outside this
	 * subproject and outside this rule.
	 *
	 * <p>A fluent rule, so the {@code satisfied}/{@code violated} inversion trap described on
	 * {@link #useUnorderedCollections()} does not apply here.
	 */
	@ArchTest
	static final ArchRule clientTypesStayInsideClientPackages = noClasses()
			.that().resideOutsideOfPackages("dev.alaindustrial.client..", "dev.alaindustrial.mixin.client..")
			.should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..")
			.because("a dedicated server has no client classes: a top-level reference to one from "
					+ "block/item/menu code is a NoClassDefFoundError there and invisible in the dev "
					+ "client. Put the client call in a class under dev.alaindustrial.client.. and reach "
					+ "it from inside a level.isClientSide() guard");

	/**
	 * The graphics-backend packages no production class may reference (MOD-497).
	 *
	 * <p>Kept as a named constant because {@code neoforge/src/test} carries the same list for its own
	 * zone — ArchUnit can only see the classes on the classpath of the module it runs in, and
	 * {@code :common}'s test classpath has no NeoForge output. The two lists are the same invariant in
	 * two places, so each names the other; widen one and widen the other in the same commit.
	 */
	static final String[] BACKEND_SPECIFIC_PACKAGES = {
		"org.lwjgl.opengl..",
		"org.lwjgl.vulkan..",
		"com.mojang.blaze3d.opengl..",
		"com.mojang.blaze3d.vulkan..",
	};

	/**
	 * Rendering goes through the Blaze3D abstraction, never through a graphics backend directly
	 * (MOD-497).
	 *
	 * <p><b>Why now.</b> 26.2 ships an experimental Vulkan renderer beside the OpenGL one, and Mojang
	 * has asked mods to stop calling OpenGL directly because it goes away once Vulkan stabilises. An
	 * audit on 2026-08-24 found zero direct calls here — the mod draws its GUIs through
	 * {@code RenderPipelines.GUI_TEXTURED} and the analyzer overlay through vanilla gizmo primitives.
	 * That is cleanliness by accident, though: nothing stops the next change from adding
	 * {@code GL11.glEnable(…)}, the compiler is happy either way, and the symptom would surface on the
	 * day the backend switches — the most expensive day to find it.
	 *
	 * <p><b>Both backends, not just OpenGL.</b> The mirror defect is real and would be easy to write
	 * while "preparing for Vulkan": a direct {@code com.mojang.blaze3d.vulkan..} reference breaks the
	 * mod for every player still on the OpenGL backend, which is today's default. The invariant is not
	 * "leave OpenGL" but "depend on no backend at all", so the list forbids both, in both the LWJGL
	 * bindings and Mojang's own backend implementations.
	 *
	 * <p><b>Deliberately NOT forbidden</b>, all verified present in the 26.2 client jar before this
	 * rule was written (the task's acceptance criterion: never forbid a symbol that does not exist, or
	 * the rule is a tautology that can never fail):
	 * <ul>
	 *   <li>{@code com.mojang.blaze3d.systems.RenderSystem} — the abstraction itself, and it lives in
	 *       {@code systems}, not in a backend package. {@code GlStateManager} needs no separate entry
	 *       either: in 26.2 it sits INSIDE {@code com.mojang.blaze3d.opengl}, so the package ban already
	 *       covers it.</li>
	 *   <li>{@code com.mojang.blaze3d.vertex..} ({@code PoseStack}, {@code VertexConsumer}) and
	 *       {@code com.mojang.blaze3d.platform.InputConstants} — the 22 legitimate uses in this mod.
	 *       Banning {@code com.mojang.blaze3d..} wholesale would take all of them with it.</li>
	 *   <li>{@code org.lwjgl.glfw..} — window and input bindings, not graphics. {@code ModKeyMappings}
	 *       uses {@code GLFW}; a ban on {@code org.lwjgl..} would be red on the existing tree.</li>
	 * </ul>
	 *
	 * <p><b>Known blind spot: inlined constants.</b> A {@code static final int} such as
	 * {@code GL11.GL_BLEND} is folded into the reading class's constant pool by javac, and the
	 * reference to its owner disappears from the bytecode — no bytecode tool can see it. Reading a
	 * number is harmless on its own; the CALL that would use it is what this rule catches, and a call
	 * cannot be inlined away.
	 *
	 * <p>A fluent rule, so the {@code satisfied}/{@code violated} inversion trap described on
	 * {@link #useUnorderedCollections()} does not apply here — and, being fluent, it has no fixture
	 * pair either. It could not have one: this lane's classpath is Minecraft-free, so a violator
	 * calling {@code GL11} does not compile here. What the rule needs proven instead is that a package
	 * ban can still SEE anything from this lane, and that is checked permanently by
	 * {@code ArchitectureRulesNegativeControl#aBackendPackageBanCanStillSeeBlaze3dFromThisLane}.
	 */
	@ArchTest
	static final ArchRule renderingStaysBackendAgnostic = noClasses()
			.should().dependOnClassesThat().resideInAnyPackage(BACKEND_SPECIFIC_PACKAGES)
			.because("a direct graphics-backend call pins the mod to one backend: OpenGL is being "
					+ "retired in favour of Vulkan, and a direct Vulkan call breaks every player still "
					+ "on OpenGL. Draw through the Blaze3D abstraction instead — RenderPipelines and a "
					+ "VertexConsumer from the MultiBufferSource, as the existing screens and block "
					+ "entity renderers do");

	/**
	 * The packages whose iteration order is load-bearing. Kept explicit so the rule cannot silently widen.
	 *
	 * <p>The first four are the network core of ADR-006, where the order decides who gets energy. The rest
	 * were added by MOD-313, where it decides what the player SEES: the analyzer overlay's tube geometry
	 * and the order of the rows in the dashboard. Same defect, different surface — an order that changed
	 * with the JVM run or with the base's absolute coordinates.
	 *
	 * <p><b>Deliberately NOT here: {@code dev.alaindustrial.client.render}.</b> Its two unordered sets
	 * ({@code NetworkOverlayRenderer}'s joints and endpoints) are only ever asked {@code contains}; the
	 * geometry that used to depend on them is now ordered inside {@link
	 * dev.alaindustrial.network.NetworkTopology#tubeRuns} instead, where the guarantee belongs. Listing a
	 * package whose sets are never iterated would spend the rule's credibility on non-defects.
	 */
	private static final String[] ORDER_SENSITIVE_PACKAGES = {
		"dev.alaindustrial.core.energy..",
		"dev.alaindustrial.core.fluid..",
		"dev.alaindustrial.core.item..",
		"dev.alaindustrial.core.net..",
		"dev.alaindustrial.network..",
		"dev.alaindustrial.stats..",
		"dev.alaindustrial.client.dashboard..",
	};

	/**
	 * Iteration order in order-sensitive code must not depend on a hash or on the JVM run (ADR-006).
	 *
	 * <p>{@code HashSet}/{@code HashMap} order follows the key's hash, and the key here is a
	 * {@code BlockPos} holding ABSOLUTE coordinates; {@code Set.of}/{@code copyOf} order follows a salt
	 * regenerated on every JVM start. That order leaks into game behaviour — which of two equidistant
	 * consumers is served first, which branch of a fork wins — and it has: the same layout behaved
	 * differently depending on where it was built, and a gametest was green alone and red in the full run.
	 *
	 * <p><b>Why this moved here from the text-scanning gate (MOD-404).</b> The Python rule matched source
	 * text, so it could be defeated by a line break inside the expression and had to carry a
	 * "did I scan enough files" floor to avoid silently passing on nothing. Bytecode has neither problem:
	 * a constructor call is a constructor call however it was written, and if the packages below vanish
	 * ArchUnit fails on an empty match rather than going quietly green.
	 *
	 * <p>An empty {@code Set.of()} / {@code Map.of()} is deliberately allowed: a collection with no
	 * elements has no order to be wrong about, and it is the idiom for "no seeds this tick".
	 */
	@ArchTest
	static final ArchRule orderSensitiveCodeUsesOrderedCollections = noClasses()
			.that().resideInAnyPackage(ORDER_SENSITIVE_PACKAGES)
			.should(useUnorderedCollections())
			.because("iteration order here decides who gets energy first and what the player sees; a hash- "
					+ "or salt-dependent order makes the same base behave differently between runs "
					+ "(see docs/adr/ADR-006-ordered-collections-in-core.md)");

	/**
	 * A machine must derive its drain and its operation length through the INSTANCE helpers
	 * ({@code effectiveEuPerTick} / {@code effectiveDuration}), never through the static
	 * {@code Config} shortcuts.
	 *
	 * <p>{@code Config.machineEuPerTickEffective()} and {@code Config.scaledDuration()} are static: they
	 * do not know which block asked, so they cannot see the overclocker chips in its upgrade panel
	 * (MOD-392). A machine calling them from its tick silently ignores the upgrade — the player spent
	 * the resources and nothing happened. This is not hypothetical: the assembler ignored the global
	 * speed multiplier entirely, and the distillation column's warm-up did not scale.
	 *
	 * <p><b>Why bytecode beats the text rule it replaces.</b> The Python version had to keep a
	 * nine-entry allow-list of files that call these legitimately from their CONSTRUCTOR (seeding
	 * {@code maxProgress} before any inventory exists). Bytecode sees the enclosing code unit, so the
	 * exception becomes a precise structural condition — "not from a constructor" — instead of a list
	 * of names that a tenth machine would have to be added to by hand.
	 */
	@ArchTest
	static final ArchRule machinesUseOverclockHelpers = noClasses()
			.that().resideInAPackage("dev.alaindustrial.block.entity..")
			.and().doNotHaveSimpleName("MachineBlockEntity")
			.should(callStaticRateShortcutOutsideConstructor())
			.because("the static Config shortcuts cannot see a machine's overclocker chips: call "
					+ "effectiveEuPerTick(base) / effectiveDuration(base) instead. Seeding from a "
					+ "constructor stays allowed — there is no inventory to read a chip from yet");

	/**
	 * Unordered-collection uses order-sensitive code may not contain.
	 *
	 * <p><b>References count, not only calls (MOD-313).</b> {@code new HashMap<>()} is a constructor CALL;
	 * {@code HashMap::new} handed to a factory parameter is a constructor REFERENCE, a different kind of
	 * access that {@code getConstructorCallsFromSelf()} does not report at all. The rule was blind to it,
	 * and the blind spot was not theoretical — {@code PlayerModStats}' packet codec was built on exactly
	 * that form, and the gate stayed green over it. A rule that cannot see the shape the defect actually
	 * takes is not a gate.
	 *
	 * <p><b>{@code satisfied}, not {@code violated} — and this is not a naming preference (MOD-313).</b>
	 * A condition handed to {@code noClasses().should(…)} is wrapped in {@code ArchConditions.never(…)},
	 * which INVERTS every event it produces. So the phrase to complete is "no class should <b>use</b>
	 * unordered collections": a class that does is one that SATISFIES this condition, and the inversion
	 * turns that into the failure. Reporting {@code violated} here reads naturally and is exactly
	 * backwards — it inverts to "no problem", and the rule then cannot fail on anything, which is how it
	 * was found: widening the zone to a package holding two plain {@code new HashSet<>()} left the build
	 * green. Probed both ways before and after the fix. The same trap applies to
	 * {@link #callStaticRateShortcutOutsideConstructor()}; it does NOT apply to
	 * {@link #notCallFromStaticInitializer}, which is consumed by {@code classes().should(…)} and is
	 * therefore not inverted.
	 *
	 * <p><b>Package-private on purpose (MOD-435).</b> The three condition factories are shared with
	 * {@link ArchitectureRulesNegativeControl}, which composes conditions from the same factories
	 * (same code, a fresh instance per call) into rules scoped to a fixture package of deliberate
	 * violators and demands that each of them fails there.
	 * That is what turns the paragraph above from a remembered lesson into a checked one: flip
	 * {@code satisfied} back to {@code violated} and the negative control goes red naming the fixture.
	 */
	static ArchCondition<JavaClass> useUnorderedCollections() {
		return new ArchCondition<>("use hash- or salt-ordered collections") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					for (var call : codeUnit.getConstructorCallsFromSelf()) {
						checkConstructor(item, events, codeUnit, call.getTargetOwner().getSimpleName(), "new ");
					}
					// `HashMap::new` — same constructor, reached through a method handle instead of a
					// `new` instruction, and reported under a different accessor.
					for (var reference : codeUnit.getConstructorReferencesFromSelf()) {
						checkConstructor(item, events, codeUnit,
								reference.getTargetOwner().getSimpleName(), "a reference to new ");
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						checkMethod(item, events, codeUnit, call.getTargetOwner().getSimpleName(),
								call.getName(), call.getTarget().getRawParameterTypes().isEmpty(), "");
					}
					// `Set::of` / `Map::copyOf` as a method reference. The "empty overload is fine"
					// exception carries over unchanged: a reference binds to ONE overload, and its
					// parameter list is the one that overload declares.
					for (var reference : codeUnit.getMethodReferencesFromSelf()) {
						checkMethod(item, events, codeUnit, reference.getTargetOwner().getSimpleName(),
								reference.getName(), reference.getTarget().getRawParameterTypes().isEmpty(),
								"a reference to ");
					}
				}
			}

			private void checkConstructor(JavaClass item, ConditionEvents events, JavaCodeUnit codeUnit,
					String owner, String shape) {
				if ("HashMap".equals(owner) || "HashSet".equals(owner)) {
					events.add(SimpleConditionEvent.satisfied(item,
							shape + owner + " in " + codeUnit.getFullName()
									+ " — take the Linked variant"));
				}
			}

			private void checkMethod(JavaClass item, ConditionEvents events, JavaCodeUnit codeUnit,
					String owner, String name, boolean noParameters, String shape) {
				boolean factory = ("Set".equals(owner) || "Map".equals(owner))
						&& ("of".equals(name) || "copyOf".equals(name));
				// `Set.of()` with no arguments has no order to get wrong; only the populated
				// overloads are salted, so the empty one stays allowed.
				if (factory && !noParameters) {
					events.add(SimpleConditionEvent.satisfied(item,
							shape + owner + "." + name + "(…) in " + codeUnit.getFullName()
									+ " — iteration order is salted per JVM run; take "
									+ "LinkedHashSet/LinkedHashMap or List.of"));
				}
				if ("Collectors".equals(owner) && ("toSet".equals(name) || "toMap".equals(name))) {
					events.add(SimpleConditionEvent.satisfied(item,
							shape + "Collectors." + name + " in " + codeUnit.getFullName()
									+ " — take toCollection(LinkedHashSet::new) / a toMap "
									+ "overload with LinkedHashMap::new"));
				}
			}
		};
	}

	/** The static {@code Config} rate shortcuts, called from anywhere but a constructor. */
	static ArchCondition<JavaClass> callStaticRateShortcutOutsideConstructor() {
		return new ArchCondition<>("call a static Config rate shortcut outside a constructor") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					if ("<init>".equals(codeUnit.getName())) {
						continue; // constructor seeding: no inventory exists yet, so no chip to read
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						if (!"Config".equals(call.getTargetOwner().getSimpleName())) {
							continue;
						}
						String name = call.getName();
						if ("machineEuPerTickEffective".equals(name) || "scaledDuration".equals(name)) {
							events.add(SimpleConditionEvent.satisfied(item,
									"Config." + name + "() in " + codeUnit.getFullName()
											+ " — static, so it cannot see the overclocker chips"));
						}
					}
				}
			}
		};
	}

	static ArchCondition<JavaClass> notCallFromStaticInitializer(String ownerSimpleName,
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
