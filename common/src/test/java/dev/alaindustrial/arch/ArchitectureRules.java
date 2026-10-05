package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.NoUpgradePanel;
import dev.alaindustrial.config.Knob;
import dev.alaindustrial.core.machine.MachineRates;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
	 * The packages allowed to depend on client types (see {@link #clientTypesStayInsideClientPackages}):
	 * the client tree, the client mixins, and the client half of the version facades (MOD-703, ADR-036).
	 */
	static final String[] CLIENT_TYPE_HOSTS = {
		"dev.alaindustrial.client..",
		"dev.alaindustrial.mixin.client..",
		"dev.alaindustrial.compat.client..",
	};

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
	 * <p><b>The version facades of {@code compat.client} are hosts too (MOD-703, ADR-036).</b> A facade
	 * whose signature carries a client type ({@code PoseStack}, {@code SubmitNodeCollector}, {@code Screen},
	 * {@code InputConstants.Type}) lives in {@code dev.alaindustrial.compat.client} — inside the one facade
	 * package, and still client-only: it is reached only from client code. The rest of
	 * {@code dev.alaindustrial.compat} stays server-safe; {@code ArchitectureRulesNegativeControl} proves the
	 * host list admits {@code compat.client} and nothing else under {@code compat}.
	 *
	 * <p>A fluent rule, so the {@code satisfied}/{@code violated} inversion trap described on
	 * {@link #useUnorderedCollections()} does not apply here.
	 */
	@ArchTest
	static final ArchRule clientTypesStayInsideClientPackages = noClasses()
			.that().resideOutsideOfPackages(CLIENT_TYPE_HOSTS)
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
	 * ({@code NetworkOverlayState}'s joints and endpoints) are only ever asked {@code contains}; the
	 * geometry is drawn edge by edge from {@link
	 * dev.alaindustrial.network.NetworkTopology#connectedAdjacency}, which is ordered itself. Listing a
	 * package whose sets are never iterated would spend the rule's credibility on non-defects.
	 */
	private static final String[] ORDER_SENSITIVE_PACKAGES = {
		"dev.alaindustrial.core.energy..",
		"dev.alaindustrial.core.fluid..",
		"dev.alaindustrial.core.item..",
		"dev.alaindustrial.core.net..",
		// MOD-715: the monitor wall's node bookkeeping joins the shared network frame; its panel and
		// container order decides which panels go dark when the cards run out (ADR-006).
		"dev.alaindustrial.core.monitor..",
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
	 * The packages the core layer may not reach (ADR-039). {@code registry} is the root of the build —
	 * everything may be reached from it, it from nothing but the loader entry points and the client
	 * manifests; {@code block} is the layer above the core.
	 */
	static final String[] ABOVE_THE_CORE = {"dev.alaindustrial.block..", "dev.alaindustrial.registry.."};

	/**
	 * The core layer rule itself, unfrozen: no class in {@code core} depends on a block or the registry.
	 * Kept separate from its frozen form so the negative control can prove it sees violations at all.
	 */
	static final ArchRule CORE_LAYER = noClasses()
			.that().resideInAPackage("dev.alaindustrial.core..")
			.should().dependOnClassesThat().resideInAnyPackage(ABOVE_THE_CORE)
			.because("core is the lowest layer of game logic (coding.md §3, ADR-039): a core class that "
					+ "names a block or the registry has to change for every new block, and drags the block "
					+ "layer into everything that only wanted the core. Declare an interface in core "
					+ "(CableNode, FluidPipeNode, StorageEndpoint, EnergyPortHost) and implement it in the block");

	/**
	 * {@link #CORE_LAYER} under {@link FreezingArchRule} (MOD-715, ADR-039): today's violations are a
	 * baseline in {@code common/src/test/archunit-store}, a new one fails, and — because the store refuses
	 * updates ({@code archunit.properties}) — so does a fixed one until the baseline is shrunk on purpose:
	 * <pre>
	 * JAVA_TOOL_OPTIONS="-Darchunit.freeze.store.default.allowStoreUpdate=true"
	 *   ./gradlew :common:test --tests dev.alaindustrial.arch.ArchitectureRules
	 * </pre>
	 * The baseline only shrinks; the network kernels are not in it (pinned by the negative control). The
	 * store is keyed by this rule's description, {@code because} text included: rewording it means writing
	 * the store anew, with {@code allowStoreCreation=true} next to {@code allowStoreUpdate=true}.
	 */
	@ArchTest
	static final ArchRule coreDoesNotDependOnBlocksOrRegistry = FreezingArchRule.freeze(CORE_LAYER);

	/**
	 * A machine must derive its drain and its operation length through the INSTANCE helpers
	 * ({@code effectiveEuPerTick} / {@code effectiveDuration}), never through the static tariff formula
	 * ({@code MachineRates}, MOD-710; its {@code Config} delegates are gone since batch 4b).
	 *
	 * <p>{@code MachineRates.euPerTick}/{@code duration} are static: they
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
			.because("the static tariff shortcuts cannot see a machine's overclocker chips: call "
					+ "effectiveEuPerTick(base) / effectiveDuration(base) instead. Seeding from a "
					+ "constructor stays allowed — there is no inventory to read a chip from yet");

	/**
	 * Client code reads the balance through {@code ServerBalance}, never from {@code Config} (MOD-695).
	 *
	 * <p>On a dedicated server the client's {@code Config} holds the PLAYER's own file, which is usually
	 * the untouched default; the server's numbers arrive in {@code ConfigSyncPayload} and live in
	 * {@code ServerBalance}. A tooltip or a screen that reads {@code Config.someKnob} shows a number the
	 * server does not play by — the mastery level, the teleporter's free points, every tooltip figure
	 * did exactly that until MOD-695. {@code ServerBalance} itself is the one exemption: its accessors
	 * fall back to the local value while no snapshot has arrived.
	 *
	 * <p>Two shapes count as a read, both seen in the bytecode: a GET of a field carrying {@code @Knob} in
	 * ANY holder ({@code Config} or a per-subsystem holder of ADR-034, MOD-710), and a call to a holder
	 * method that itself reads one. No holder declares such a shortcut since MOD-710 batch 4b inlined the
	 * tariff delegates into {@code MachineRates}; the arm stays as the guard against a new one. A holder
	 * method that reads no knob — a pure formula — stays allowed: fed the server's numbers, it is the one
	 * formula both sides share.
	 */
	@ArchTest
	static final ArchRule clientReadsBalanceThroughServerBalance = noClasses()
			.that().resideInAPackage("dev.alaindustrial.client..")
			.and().doNotHaveFullyQualifiedName("dev.alaindustrial.client.ServerBalance")
			.should(readBalanceKnobsFromConfig())
			.because("on a dedicated server Config holds the player's own file, not the server's balance: "
					+ "read the knob through dev.alaindustrial.client.ServerBalance, which holds the "
					+ "server's snapshot (MOD-695)");

	/**
	 * An item's own tooltip reads the balance through {@code ServerBalance} too (MOD-695).
	 * {@code Item#appendHoverText} lives in item code, outside {@code client..}, but it runs on the client:
	 * a knob read there from {@code Config} is the player's own file on a dedicated server, and the tooltip
	 * disagreed with the machine tooltips and screens (the mutation chip's cost, the soul vessel's
	 * thresholds). Scoped to the tooltip bodies — the method and the lambdas javac lifts out of it — so the
	 * same class may keep reading {@code Config} in its server-side logic.
	 */
	@ArchTest
	static final ArchRule itemTooltipsReadBalanceThroughServerBalance = noClasses()
			.that().resideOutsideOfPackage("dev.alaindustrial.client..")
			.should(readBalanceKnobsFromConfigIn(ArchitectureRules::isTooltipBody, "in an item tooltip"))
			.because("appendHoverText runs on the client, whose Config is the player's own file on a "
					+ "dedicated server: read the knob through dev.alaindustrial.client.ServerBalance (MOD-695)");

	/** The registry classes whose recipe-family declarations the recipe viewers read, nested classes included. */
	static final String RECIPE_FAMILY_DECLARATIONS = "dev\\.alaindustrial\\.registry\\."
			+ "(ModRecipes|MachineRecipeFamily)(\\$.*)?";

	/**
	 * Recipe data and the recipe families' draw per tick read no balance knob (MOD-743).
	 *
	 * <p>{@link #clientReadsBalanceThroughServerBalance} sees only a read made BY client code. The recipe
	 * viewers read the balance one step removed: a JEI card printed the cost a {@code recipe..} class had
	 * computed from {@code Config} (the vanilla-smelt mirror), and the time a family's rate lambda in
	 * {@code ModRecipes} read from {@code Config} — both the player's own file on a dedicated server. These
	 * classes are drawn on the client, so a rate is read through {@code ServerBalance} (a family's rate is
	 * viewer-only — no machine calls it) and a cost is left to the viewer, which resolves it when it draws.
	 */
	@ArchTest
	static final ArchRule recipeDataReadsNoBalanceKnob = noClasses()
			.that().resideInAPackage("dev.alaindustrial.recipe..")
			.or().haveNameMatching(RECIPE_FAMILY_DECLARATIONS)
			.should(readBalanceKnobsFromConfig())
			.because("recipe data is drawn on the client, whose Config is the player's own file on a dedicated "
					+ "server: a family's rate reads ServerBalance, and a cost is resolved by the viewer when it "
					+ "draws the card (RecipeViewerCost, MOD-743)");

	/**
	 * {@code appendHoverText} itself, or a lambda javac lifted out of it. ArchUnit 1.x already attributes
	 * a lambda's accesses to the declaring method, so the second arm is a guard for an importer that does
	 * not; the negative control proves a read inside the lambda is reported either way.
	 *
	 * <p>MOD-716 (ADR-040): a block's {@code machineTooltip()} and a powered item's {@code toolTooltip()} are
	 * tooltip bodies as well — declared in block and item code, read on the client.
	 */
	static boolean isTooltipBody(JavaCodeUnit codeUnit) {
		String name = codeUnit.getName();
		for (String body : TOOLTIP_BODIES) {
			if (body.equals(name) || name.startsWith("lambda$" + body + "$")) {
				return true;
			}
		}
		return false;
	}

	/** Methods whose body builds a tooltip that is shown on the client. */
	private static final List<String> TOOLTIP_BODIES = List.of("appendHoverText", "machineTooltip", "toolTooltip");

	/** Name of the knob annotation, taken from the class so a move is a compile error, not a blind rule. */
	private static final String KNOB_ANNOTATION = Knob.class.getName();

	/**
	 * A GET of a {@code @Knob} field of {@code Config}, or a call/reference to a knob holder's method
	 * that performs one. Reports {@code satisfied}: consumed by {@code noClasses().should(…)}, so the
	 * inversion described on {@link #useUnorderedCollections()} applies, and
	 * {@code ArchitectureRulesNegativeControl} proves it on a fixture pair.
	 */
	static ArchCondition<JavaClass> readBalanceKnobsFromConfig() {
		return readBalanceKnobsFromConfigIn(codeUnit -> true, "");
	}

	/** {@link #readBalanceKnobsFromConfig()} restricted to the code units {@code scope} accepts. */
	static ArchCondition<JavaClass> readBalanceKnobsFromConfigIn(Predicate<JavaCodeUnit> scope, String where) {
		return new ArchCondition<>(("read a balance knob from Config " + where).strip()) {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					if (!scope.test(codeUnit)) {
						continue;
					}
					for (JavaFieldAccess access : codeUnit.getFieldAccesses()) {
						if (readsKnob(access)) {
							events.add(SimpleConditionEvent.satisfied(item, access.getTargetOwner().getSimpleName()
									+ "." + access.getName() + " read in " + codeUnit.getFullName()
									+ " — use ServerBalance."
									+ access.getName() + "()"));
						}
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						call.getTarget().resolveMember().filter(ArchitectureRules::readsKnobItself)
								.ifPresent(target -> events.add(SimpleConditionEvent.satisfied(item,
										target.getOwner().getSimpleName() + "." + target.getName() + "() called in "
												+ codeUnit.getFullName()
												+ " — it reads a knob; use the ServerBalance twin")));
					}
					for (JavaMethodReference reference : codeUnit.getMethodReferencesFromSelf()) {
						reference.getTarget().resolveMember().filter(ArchitectureRules::readsKnobItself)
								.ifPresent(target -> events.add(SimpleConditionEvent.satisfied(item,
										"a reference to " + target.getOwner().getSimpleName() + "." + target.getName()
												+ " in " + codeUnit.getFullName() + " — it reads a knob")));
					}
				}
			}
		};
	}

	/**
	 * A GET of a field annotated {@code @Knob}, whichever holder declares it. The annotation, not the owner,
	 * decides: a knob moved out of {@code Config} by the per-subsystem split (ADR-034) must stay covered
	 * without an edit here — an owner test against the string {@code "dev.alaindustrial.Config"} would have
	 * gone blind on the first slice.
	 */
	private static boolean readsKnob(JavaFieldAccess access) {
		return access.getAccessType() == JavaFieldAccess.AccessType.GET
				&& access.getTarget().resolveMember().map(field -> field.isAnnotatedWith(KNOB_ANNOTATION))
						.orElse(false);
	}

	/** Class names of every knob holder the registry scans ({@code Config} and the per-subsystem holders). */
	private static final Set<String> KNOB_HOLDERS = Config.REGISTRY.holders().stream()
			.map(Class::getName).collect(Collectors.toCollection(LinkedHashSet::new));

	/** A class the knob registry scans ({@code Config} or a per-subsystem holder, ADR-034). */
	static DescribedPredicate<JavaClass> isKnobHolder() {
		return DescribedPredicate.describe("are knob holders", javaClass -> KNOB_HOLDERS.contains(javaClass.getName()));
	}

	/** The classes {@link #energyKernelsReadNoKnobHolder} covers, nested classes included. */
	static final String ENERGY_KERNELS = "dev\\.alaindustrial\\.core\\.energy\\."
			+ "(EnergyNetwork|DischargePlan|DirectAdjacencyDistributor|EnergyLineDistributor|LineView)(\\$.*)?";

	/**
	 * The energy kernels take the balance they read as a value (MOD-710 batch 5, CORE-10): {@code NetworkBalance},
	 * read from {@code Config} by {@code NetworkManager.balance()} and handed in. A kernel that reads a knob
	 * holder itself is back to a hidden global: an L1 test of it would have to mutate {@code Config}, and a
	 * tick pass could see two values of one knob. {@code NetworkManager} is the boundary and stays outside.
	 */
	@ArchTest
	static final ArchRule energyKernelsReadNoKnobHolder = noClasses()
			.that().haveNameMatching(ENERGY_KERNELS)
			.should().dependOnClassesThat(isKnobHolder())
			.because("the energy algorithms get their knobs in NetworkBalance, built by NetworkManager once per "
					+ "tick pass (MOD-710, CORE-10): read a new one there and pass it in");

	/**
	 * A knob holder: a class the registry scans, or any class that declares a {@code @Knob} field — the
	 * annotation decides here too, so the negative control can prove the shortcut arm on a fixture holder.
	 */
	private static boolean declaresKnobs(JavaClass owner) {
		return KNOB_HOLDERS.contains(owner.getFullName())
				|| owner.getFields().stream().anyMatch(field -> field.isAnnotatedWith(KNOB_ANNOTATION));
	}

	/** A method of a knob holder whose own body reads a knob. */
	private static boolean readsKnobItself(JavaMethod method) {
		if (!declaresKnobs(method.getOwner())) {
			return false;
		}
		for (JavaFieldAccess access : method.getFieldAccesses()) {
			if (readsKnob(access)) {
				return true;
			}
		}
		// One level of delegation: a shortcut that reads its knobs itself is caught above, but one could
		// delegate to another shortcut instead.
		for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
			if (call.getTarget().resolveMember().filter(target -> !target.equals(method)
					&& declaresKnobs(target.getOwner())
					&& target.getFieldAccesses().stream().anyMatch(ArchitectureRules::readsKnob)).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** Prefix of the loader-neutral gametest scenarios' class names, {@code ConfigOverrides} included (MOD-710). */
	private static final String GAMETEST_CLASSES = "dev.alaindustrial.gametest.";

	/** That package and its subpackages, as an ArchUnit package identifier. */
	static final String GAMETEST_PACKAGE = GAMETEST_CLASSES + ".";

	/** The one class through which a gametest may change a balance knob. */
	static final String CONFIG_OVERRIDES = GAMETEST_CLASSES + "ConfigOverrides";

	/**
	 * A gametest changes a balance knob only through {@code ConfigOverrides} (MOD-710, CFG-3).
	 *
	 * <p>A scenario that writes {@code Config.<knob> = …} itself has no owner and no guaranteed restore:
	 * five hand-written save/finally idioms existed, a timeout skipped every one of them, and two
	 * scenarios fighting over one knob restored each other's values in the wrong order (the MOD-469
	 * leak). {@code ConfigOverrides.sync()} restores in {@code close()}; {@code forTest(helper)} restores
	 * when the test ends however it ends, and its owner registry refuses a second holder of one key.
	 *
	 * <p>The rule reads the SET of a field annotated {@code @Knob}, whatever class holds it, so a knob
	 * that moves to a per-subsystem holder (ADR-034) stays covered without an edit here. A compound
	 * {@code Config.x += 1} is a GET and a SET and is caught by the SET. {@code ConfigOverrides} itself
	 * is inside the package and NOT exempted: it writes through the knob's reflected {@code Field}, so a
	 * bytecode write from it would be a defect too.
	 *
	 * <p><b>Scope.</b> {@code :common}'s test runtime classpath carries the {@code gametest} source set's
	 * output, so every class under {@code dev.alaindustrial.gametest..} of {@code common/src/gametest}
	 * is imported here, and {@code ArchitectureRulesNegativeControl} floors that the import is not blind.
	 * The loaders' own gametest source sets ({@code fabric/src/gametest}, {@code neoforge/src/gametest})
	 * are outside this subproject and review-only; neither holds a direct write today. L1 tests that set
	 * a knob are outside the rule too ({@code DoNotIncludeTests}).
	 *
	 * <p>A {@code noClasses().should(…)} rule: the condition reports {@code satisfied} per write, see
	 * {@link #useUnorderedCollections()} for the inversion, and the negative control proves it on a
	 * violator and a clean twin.
	 */
	@ArchTest
	static final ArchRule gametestsWriteKnobsOnlyThroughConfigOverrides = noClasses()
			.that().resideInAPackage(GAMETEST_PACKAGE)
			.should(writeAKnobField())
			.because("a direct write to a Config knob has no owner and no restore on a timeout, and two "
					+ "scenarios on one knob restore each other's value in the wrong order: use "
					+ "ConfigOverrides.sync() inside one call or ConfigOverrides.forTest(helper) across ticks "
					+ "(MOD-710)");

	/**
	 * The scenarios that may hold a knob ACROSS TICKS with {@code ConfigOverrides.forTest(helper)}, each
	 * with the gametest environment it runs in and why (MOD-710; modelled on the lane-parity gate's
	 * {@code LOADER_ONLY}). Key: {@code <declaring class>.<method>}; value: environment, then the reason.
	 *
	 * <p>A held override is visible to every scenario ticking in the same gametest batch, and the owner
	 * registry stops a second WRITER but never a reader (the MOD-469 incident was a reader). So a
	 * multi-tick holder runs in a {@code alaindustrial:config_overrides[_N]} environment: the game batches
	 * tests by environment and runs the batches one after another. Two holders of ONE knob may not share a
	 * batch, hence the numbered environments. The environment is named here and wired by hand in both
	 * lanes — Fabric {@code @GameTest(environment = …)} with {@code data/alaindustrial/test_environment/
	 * <name>.json}, NeoForge {@code NeoForgeGameTests.registerInEnvironment}.
	 *
	 * <p>Adding an entry is a decision: prefer {@code ConfigOverrides.sync()} inside one synchronous call.
	 * {@code ArchitectureRulesNegativeControl} fails on an entry nobody calls any more.
	 */
	static final Map<String, String> FOR_TEST_USERS = Map.ofEntries(
			Map.entry(GAMETEST_CLASSES + "OilScenarios.fun05BurnSpreadsAcrossPool",
					"alaindustrial:config_overrides — holds oilBurns for a 120-tick burn; shares its knob with "
							+ "neg02, which therefore sits in config_overrides_2"),
			Map.entry(GAMETEST_CLASSES + "OilScenarios.neg02LavaNeighbourNeverIgnites",
					"alaindustrial:config_overrides_2 — holds oilBurns for a 90-tick observation window"),
			Map.entry(GAMETEST_CLASSES + "OilScenarios.fun11SootOnlyWhereOilBurntOut",
					"alaindustrial:config_overrides — holds oilSootChance until the fires burn out"),
			Map.entry(GAMETEST_CLASSES + "SprinklerScenarios.fun01SpraysOncePerIntervalAndPays",
					"alaindustrial:config_overrides — pins sprinklerRange while a real-tick sequence runs"),
			Map.entry(GAMETEST_CLASSES + "SprinklerScenarios.fun02HangingReachesTheFieldBelow",
					"alaindustrial:config_overrides_2 — pins sprinklerRange; one sprinkler scenario per batch"),
			Map.entry(GAMETEST_CLASSES + "SprinklerScenarios.con01TankBelowPriceNeverFires",
					"alaindustrial:config_overrides_3 — pins sprinklerRange; one sprinkler scenario per batch"),
			Map.entry(GAMETEST_CLASSES + "SprinklerScenarios.con02NothingToWaterCostsNothing",
					"alaindustrial:config_overrides_4 — pins sprinklerRange; one sprinkler scenario per batch"),
			Map.entry(GAMETEST_CLASSES + "ConfigOverridesScenarios.restoredWhenTheOwningTestEnds",
					"alaindustrial:config_overrides — self-test of the end-of-test restore; holds a knob no other "
							+ "scenario reads for five ticks"),
			Map.entry(GAMETEST_CLASSES + "ConfigOverridesScenarios.secondOwnerIsRefusedNamingTheFirst",
					"the lane's default environment (Fabric minecraft:default, NeoForge alaindustrial:empty_env) — "
							+ "self-test, one synchronous body on knobs no other scenario reads"),
			Map.entry(GAMETEST_CLASSES + "ConfigOverridesScenarios.restoredAfterATimeout",
					"the lane's default environment (Fabric minecraft:default, NeoForge alaindustrial:empty_env) — "
							+ "self-test, one synchronous body on a knob no other scenario reads"));

	/**
	 * Only the scenarios of {@link #FOR_TEST_USERS} may call {@code ConfigOverrides.forTest}: a held
	 * override outside a dedicated environment is a reader trap (see the list's javadoc).
	 */
	@ArchTest
	static final ArchRule forTestIsHeldOnlyByTheListedScenarios = noClasses()
			.that().resideInAPackage(GAMETEST_PACKAGE)
			.and().doNotHaveFullyQualifiedName(CONFIG_OVERRIDES)
			.should(callForTestOutside(CONFIG_OVERRIDES, FOR_TEST_USERS.keySet()))
			.because("a knob held across ticks is visible to every scenario in the same gametest batch: only the "
					+ "scenarios of ArchitectureRules.FOR_TEST_USERS, each in its own alaindustrial:config_overrides "
					+ "environment, may use ConfigOverrides.forTest — prefer ConfigOverrides.sync() (MOD-710)");

	/** The content manifest's aggregator and the package of its domain files (MOD-711, batch 3). */
	static final String CONTENT_MANIFEST = "dev.alaindustrial.registry.ContentManifest";
	static final String CONTENT_DOMAINS = "dev.alaindustrial.registry.content";

	/**
	 * A content domain initialises on its own (MOD-711, batch 3): no class of {@code registry.content}
	 * touches a static member of the aggregator {@code ContentManifest}, and no domain file
	 * ({@code *Content}) touches a member of another domain file.
	 *
	 * <p>{@code ContentManifest} builds its four lists by reading every domain's {@code DOMAIN} field, so it
	 * initialises the domains from inside its own static initialiser. A domain that read
	 * {@code ContentManifest.ITEMS} back — or called a method that does — would re-enter a class whose
	 * initialisation is in progress on the same thread: the JVM hands out the half-built class and the list
	 * is {@code null}, a crash on the first lane at best. A domain that named another domain's constant
	 * would start that domain's initialiser inside its own, while its own block collection is open
	 * ({@code ContentDeclarations.beginBlocks()} refuses that at run time; this rule refuses it at build
	 * time). The nested records ({@code ContentManifest.BlockDef} and the like) are separate classes and stay
	 * allowed: building one does not initialise the aggregator.
	 */
	@ArchTest
	static final ArchRule contentDomainsInitialiseOnTheirOwn = noClasses()
			.that().resideInAPackage(CONTENT_DOMAINS + "..")
			.should().accessTargetWhere(startsAnotherManifestInitialiser(CONTENT_MANIFEST, CONTENT_DOMAINS))
			.because("ContentManifest initialises the domains while building its lists: a domain that reads the "
					+ "aggregator back sees a half-built class, and one that names another domain starts its "
					+ "initialiser inside its own (MOD-711). Declare the entry in its own domain file");

	/**
	 * An access to a member of {@code aggregator}, or of a domain class ({@code *Content} directly in
	 * {@code domainPackage}) other than the accessing class itself. Parametrised so the negative control
	 * can aim the same predicate at a fixture package.
	 */
	static DescribedPredicate<JavaAccess<?>> startsAnotherManifestInitialiser(String aggregator,
			String domainPackage) {
		return DescribedPredicate.describe("a member of " + aggregator + " or of another domain file of "
				+ domainPackage, access -> {
					JavaClass target = access.getTargetOwner();
					if (target.getFullName().equals(aggregator)) {
						return true;
					}
					return target.getPackageName().equals(domainPackage) && target.getSimpleName().endsWith("Content")
							&& !target.equals(access.getOriginOwner());
				});
	}

	/**
	 * A SET of a {@code @Knob}-annotated field, in a method, a constructor, an initializer or a lambda.
	 * Reports {@code satisfied} — consumed by {@code noClasses().should(…)}, so the inversion described on
	 * {@link #useUnorderedCollections()} applies.
	 */
	static ArchCondition<JavaClass> writeAKnobField() {
		return new ArchCondition<>("write a @Knob field") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					for (JavaFieldAccess access : codeUnit.getFieldAccesses()) {
						if (access.getAccessType() == JavaFieldAccess.AccessType.SET && access.getTarget()
								.resolveMember().map(field -> field.isAnnotatedWith(KNOB_ANNOTATION)).orElse(false)) {
							events.add(SimpleConditionEvent.satisfied(item, access.getTargetOwner().getSimpleName()
									+ "." + access.getName() + " written in " + codeUnit.getFullName()
									+ " — hold it with ConfigOverrides.sync() or forTest(helper)"));
						}
					}
				}
			}
		};
	}

	/**
	 * A call to, or a method reference of, {@code <ownerFullName>.forTest} from a code unit whose
	 * {@code <class>.<method>} is not in {@code allowed}. A lambda javac lifted out of a method is
	 * attributed to that method. Reports {@code satisfied}: {@code noClasses().should(…)} inverts it.
	 */
	static ArchCondition<JavaClass> callForTestOutside(String ownerFullName, Set<String> allowed) {
		return new ArchCondition<>("call " + ownerFullName + ".forTest outside the allow-list") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					String caller = forTestCaller(codeUnit);
					if (allowed.contains(caller)) {
						continue;
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						if (isForTest(call.getTargetOwner().getFullName(), call.getName(), ownerFullName)) {
							events.add(SimpleConditionEvent.satisfied(item, call.getDescription()
									+ " — " + caller + " is not in ArchitectureRules.FOR_TEST_USERS"));
						}
					}
					for (JavaMethodReference reference : codeUnit.getMethodReferencesFromSelf()) {
						if (isForTest(reference.getTargetOwner().getFullName(), reference.getName(), ownerFullName)) {
							events.add(SimpleConditionEvent.satisfied(item, reference.getDescription()
									+ " — " + caller + " is not in ArchitectureRules.FOR_TEST_USERS"));
						}
					}
				}
			}
		};
	}

	private static boolean isForTest(String targetOwner, String name, String ownerFullName) {
		return ownerFullName.equals(targetOwner) && "forTest".equals(name);
	}

	/** {@code <declaring class>.<method>}, with a javac-lifted lambda attributed to the method it came from. */
	static String forTestCaller(JavaCodeUnit codeUnit) {
		String name = codeUnit.getName();
		if (name.startsWith("lambda$")) {
			name = name.substring("lambda$".length(), name.lastIndexOf('$'));
		}
		return codeUnit.getOwner().getFullName() + "." + name;
	}

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

	/**
	 * The static tariff formula, called from anywhere but a constructor: {@code MachineRates.euPerTick} /
	 * {@code MachineRates.duration} (MOD-710 batch 4, the one place the formula lives; batch 4b removed the
	 * {@code Config} delegates that called it). Owners are compared by class NAME taken from the class
	 * itself, so moving the formula again is a compile error here, not a rule that silently matches nothing
	 * (CFG-6: the old condition compared the owner with the string {@code "Config"}).
	 *
	 * <p>A class implementing {@code NoUpgradePanel} is not checked: it has no chips to ignore. The electric
	 * heater bills its heat tick from the static formula on purpose, with the chip count of the machine
	 * above passed in (MOD-392); it called the delegate {@code electricHeaterEuPerTickEffective}, which this
	 * condition never listed, until batch 4b inlined it into {@code MachineRates.euPerTick}. The marker is
	 * the structural reason, so the exemption needs no list of names (MOD-710 batch 4b).
	 */
	static ArchCondition<JavaClass> callStaticRateShortcutOutsideConstructor() {
		return new ArchCondition<>("call a static tariff shortcut (MachineRates) outside a constructor") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				if (item.isAssignableTo(NoUpgradePanel.class)) {
					return; // no upgrade panel, so no overclocker chip a static call could miss
				}
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					if ("<init>".equals(codeUnit.getName())) {
						continue; // constructor seeding: no inventory exists yet, so no chip to read
					}
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						String owner = call.getTargetOwner().getFullName();
						String name = call.getName();
						if (RATE_SHORTCUTS.getOrDefault(owner, Set.of()).contains(name)) {
							String shortcut = call.getTargetOwner().getSimpleName() + "." + name + "()";
							events.add(SimpleConditionEvent.satisfied(item, shortcut + " in " + codeUnit.getFullName()
									+ " — static, so it cannot see the overclocker chips"));
						}
					}
				}
			}
		};
	}

	/** Owner class name → the static tariff methods {@link #callStaticRateShortcutOutsideConstructor()} reports. */
	private static final Map<String, Set<String>> RATE_SHORTCUTS = Map.of(
			MachineRates.class.getName(), Set.of("euPerTick", "duration"));

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
