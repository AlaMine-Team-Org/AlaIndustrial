package dev.alaindustrial.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import dev.alaindustrial.config.Knob;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Client code reaches no balance knob, however many calls away (MOD-761). Read from the compiled bytecode
 * (ADR-014): what the code DOES.
 *
 * <p><b>Why the one-step rules were not enough.</b> {@code ArchitectureRules.clientReadsBalanceThroughServerBalance}
 * and {@code itemTooltipsReadBalanceThroughServerBalance} see a knob read made by the client code itself.
 * A client that asks an ordinary class — an enum of cable grades, a block, a tier — for a number, and that
 * class reads {@code Config}, was invisible to both: on a dedicated server the player saw his own file's
 * numbers and no gate said so (the vanilla-smelt mirror, MOD-743; the incubator's chance, MOD-759; the
 * cable, tank, pipe and magnet tooltips, MOD-761). This rule walks the call graph from the client.
 *
 * <p><b>Roots.</b> Every method of a class in the client packages ({@code ArchitectureRules.CLIENT_TYPE_HOSTS}:
 * the client tree, the client mixins, the client half of the version facades), {@code ServerBalance} excepted;
 * and, outside them, the item and block methods drawn on the client — the tooltip bodies, the tooltip image and
 * the item bar ({@link #ITEM_DISPLAY_HOOKS}).
 *
 * <p><b>Edges.</b> A call or a method reference to the member it resolves to, plus three bridges without which
 * the rule would be blind to exactly the defects it was written for:
 * <ul>
 *   <li><b>Implementations in subclasses</b> (class hierarchy analysis): a call to a method of one of the mod's
 *       classes or interfaces also reaches every override of it in a subclass —
 *       {@code ItemEnergy.capacity} reaches the knobs only through {@code PoweredItem.energyCapacity}.</li>
 *   <li><b>A supplier field to its declaring class's initialisers</b>: code that GETs a field of a JDK
 *       functional type ({@code java.util.function..}, {@code Runnable}, {@code Callable}) declared in one of the
 *       mod's classes and calls that type's method reaches the class's static initializer and constructors,
 *       where the lambdas were written — ArchUnit attributes a lambda's accesses to the code that declares it.
 *       That is the shape of every grade enum ({@code () -> Config.knob}).</li>
 *   <li><b>None into the mod's own functional interfaces.</b> Bridging those too reached, from a pipe's tint,
 *       the block entity ticker and the whole server tick (230 knobs of noise); and an enum used only for
 *       its identity reads no number.</li>
 * </ul>
 * The walk never enters {@code ServerBalance} (it IS the fix) nor another root (that root reports its own
 * reach). Not followed, and so not seen: registry and event dispatch (a menu factory, a block entity ticker),
 * reflection, a functional value handed out by a getter rather than read from its field, and a value behind the
 * mod's own functional interface (which is why a grade enum holds a JDK supplier, not one of ours). Only this
 * lane's classes are read: {@code common/src/main} and {@code common/src/gametest}. Client code in
 * {@code fabric/src/main} and {@code neoforge/src/main} (the loaders' client entry points, the REI plugin) is not
 * checked — review-only, like the rest of the loader trees.
 *
 * <p><b>Report line.</b> {@code <root> -> <first method outside the roots> reads <Holder.knob>}, or
 * {@code <root> reads <Holder.knob>} for a read in the root itself: stable while the path between the entry
 * and the knob is refactored, so the frozen baseline does not churn.
 *
 * <p><b>Pending.</b> A method listed in {@link #PENDING} is the LAST link of a known path that has a task of its
 * own — the method that asks the knob holder, not the first method outside the roots. The walk stops there and
 * reports {@code <root> -> <entry> reaches <pending method>, pending MOD-nnn}, with no knob name: a new knob of
 * that class (another powered item's buffer behind {@code ItemEnergy.capacity}) adds no line and does not block
 * the change that adds it. Everything else on the path is walked as usual, so a knob read creeping into a
 * method between the root and the pending one (a tooltip builder reverted from {@code ServerBalance} to
 * {@code ToolConfig}) is reported by name. A new ROOT reaching a pending method is still a new line, on purpose.
 *
 * <p><b>Frozen.</b> Today's violations are a baseline in {@code common/src/test/archunit-store} (the mechanism of
 * {@code coreDoesNotDependOnBlocksOrRegistry}, ADR-039): a new one fails, a fixed one fails too until the
 * baseline is shrunk on purpose —
 * <pre>
 * JAVA_TOOL_OPTIONS="-Darchunit.freeze.store.default.allowStoreUpdate=true"
 *   ./gradlew :common:test --tests dev.alaindustrial.arch.ClientBalanceReachRules
 * </pre>
 * The store is keyed by the rule's description, {@code because} text included: rewording it means writing the
 * store anew, with {@code allowStoreCreation=true} next to {@code allowStoreUpdate=true}.
 *
 * <p><b>Rebuilding after a change of the line format</b> (a {@link #PENDING} row added or removed, this report
 * line reworded): every line of the affected paths changes, which the ratchet reads as fixed plus new. Rebuild
 * with {@code -Darchunit.freeze.refreeze=true} next to {@code allowStoreUpdate=true}, then read the store's
 * {@code git diff} before committing it: it may only re-spell paths the old store already held (same root, same
 * entry). {@code refreeze} accepts ANY current violation, a new path included — never run it to make a red rule
 * green, and never commit its result unread. When a pending task lands, drop its row from {@link #PENDING} and
 * rebuild: what still reaches a knob comes back as per-knob lines, what is fixed is gone.
 *
 * <p>{@code ArchitectureRulesNegativeControl} proves the condition, both bridges, the stop at another root, the
 * pending form and the accepted paths on fixtures.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class ClientBalanceReachRules {

	/** The one class the walk never enters: it holds the server's numbers and is the fix itself. */
	static final String SERVER_BALANCE = "dev.alaindustrial.client.ServerBalance";

	/** Item methods drawn on the client besides the tooltip bodies of {@code ArchitectureRules}. */
	static final List<String> ITEM_DISPLAY_HOOKS = List.of("getTooltipImage", "isBarVisible", "getBarWidth",
			"getBarColor");

	/**
	 * {@code <renderer>.extractRenderState(<entity>, <renderer>$State, …) -> <entity>.<entry>}: a renderer under
	 * {@code client.render} reading an animation figure of its block entity, as the report spells the pair.
	 */
	private static String renderAnimation(String renderer, String entity, String entry) {
		String r = "dev.alaindustrial.client.render." + renderer;
		String e = "dev.alaindustrial.block.entity." + entity;
		return r + ".extractRenderState(" + e + ", " + r + "$State" + RENDER_STATE_TAIL + " -> " + e + "." + entry;
	}

	/** The common tail of a block entity renderer's {@code extractRenderState} signature. */
	private static final String RENDER_STATE_TAIL = ", float, net.minecraft.world.phys.Vec3, "
			+ "net.minecraft.client.renderer.feature.ModelFeatureRenderer$CrumblingOverlay)";

	/**
	 * Paths accepted on purpose, keyed by root and entry with their parameter types
	 * ({@code <root full name> -> <entry full name>}, as ArchUnit prints them), each with its reason. Not a
	 * baseline: an entry here is a decision about one root and one overload, so a new screen that reaches the same
	 * entry to print a number is still reported. {@code acceptedAndPendingPathsAreStillReached} fails once a path is
	 * gone; one whose entry class this lane does not import is kept as a record and not checked.
	 */
	static final Map<String, String> ACCEPTED = Map.of(
			renderAnimation("EnergyCondenserBlockEntityRenderer", "EnergyCondenserBlockEntity", "tierForBank()"),
			"renderer animation: picks which clot model to draw, shows no number (owner, 2026-10-08)",
			renderAnimation("ReactorDoorBlockEntityRenderer", "ReactorDoorBlockEntity", "slideProgress(long, float)"),
			"renderer animation: the door's slide in ticks, shows no number (owner, 2026-10-08)",
			renderAnimation("TeleporterCapsuleDoorRenderer", "TeleporterBlockEntity", "doorOpenness(long, float)"),
			"renderer animation: the capsule door's slide in ticks, shows no number (owner, 2026-10-08)",
			renderAnimation("ThermalCentrifugeBlockEntityRenderer", "ThermalCentrifugeBlockEntity", "spinPermille()"),
			"renderer animation: the drum's spin-up in ticks, shows no number (owner, 2026-10-08)",
			"dev.alaindustrial.compat.rei.AlaReiCommonPlugin.registerDisplays("
					+ "me.shedaniel.rei.api.common.registry.display.ServerDisplayRegistry)"
					+ " -> dev.alaindustrial.compat.rei.AlaReiCommonPlugin.vanillaSmeltEu()",
			"runs on the server: REI's common plugin fills the displays there (MOD-743); lives in fabric/src/main, "
					+ "outside this lane, and compat.rei is not a client package anyway");

	/**
	 * The last links of known paths that have a task of their own, by full name with parameter types: the walk
	 * stops at them and reports the task instead of the knobs (see the class javadoc). A row goes when its task
	 * lands, by the rebuild procedure above.
	 */
	static final Map<String, String> PENDING = Map.of(
			"dev.alaindustrial.item.energy.ItemEnergy.capacity(net.minecraft.world.item.ItemStack)", "MOD-790",
			"dev.alaindustrial.item.wearable.JetpackItem.isPowered(net.minecraft.world.item.ItemStack, "
					+ "net.minecraft.world.entity.player.Player)", "MOD-791");

	/** The client packages, {@code ServerBalance} and its nested classes excepted. */
	static final DescribedPredicate<JavaClass> PRODUCTION_CLIENT_CODE = DescribedPredicate.describe(
			"client code", (JavaClass c) -> JavaClass.Predicates.resideInAnyPackage(
					ArchitectureRules.CLIENT_TYPE_HOSTS).test(c) && !isServerBalance(c));

	/**
	 * The rule unfrozen, so the negative control can prove it sees the baseline at all (a condition matching
	 * nothing would freeze an empty store and stay green forever).
	 */
	static final ArchRule CLIENT_REACH = noClasses()
			.should(reachABalanceKnob(PRODUCTION_CLIENT_CODE, ACCEPTED, PENDING))
			.because("on a dedicated server Config holds the player's own file: a number the client shows comes "
					+ "from dev.alaindustrial.client.ServerBalance, handed to the class that formats it — never "
					+ "from a class that reads Config on the client's behalf (MOD-761)");

	@ArchTest
	static final ArchRule clientCodeReachesNoBalanceKnob = FreezingArchRule.freeze(CLIENT_REACH);

	private static boolean isServerBalance(JavaClass c) {
		return c.getName().equals(SERVER_BALANCE) || c.getName().startsWith(SERVER_BALANCE + "$");
	}

	/** A tooltip body of {@code ArchitectureRules}, or an item hook drawn on the client. */
	static boolean isDisplayBody(JavaCodeUnit codeUnit) {
		if (ArchitectureRules.isTooltipBody(codeUnit)) {
			return true;
		}
		String name = codeUnit.getName();
		for (String hook : ITEM_DISPLAY_HOOKS) {
			if (hook.equals(name) || name.startsWith("lambda$" + hook + "$")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reports a balance knob reached from a root. {@code clientCode} decides which classes are client code
	 * (the negative control hands in its stand-in package); {@code accepted} names the root-and-entry pairs let
	 * through, {@code pending} the methods where the walk stops and reports the task instead of the knobs.
	 * Reports {@code satisfied}: consumed by {@code noClasses().should(…)}, which inverts it.
	 */
	static ArchCondition<JavaClass> reachABalanceKnob(DescribedPredicate<JavaClass> clientCode,
			Map<String, String> accepted, Map<String, String> pending) {
		return new ArchCondition<>("reach a balance knob from client code") {
			private Reach reach;

			@Override
			public void init(Collection<JavaClass> allObjectsToTest) {
				reach = new Reach(clientCode, pending);
			}

			@Override
			public void check(JavaClass item, ConditionEvents events) {
				if (reach == null) {
					reach = new Reach(clientCode, pending);
				}
				for (JavaCodeUnit root : item.getCodeUnits()) {
					if (!reach.isRoot(root)) {
						continue;
					}
					for (String knob : Reach.ownKnobs(root)) {
						events.add(SimpleConditionEvent.satisfied(item, root.getFullName() + " reads " + knob));
					}
					for (JavaCodeUnit entry : reach.successors(root)) {
						if (reach.isRoot(entry)
								|| accepted.containsKey(root.getFullName() + " -> " + entry.getFullName())) {
							continue;
						}
						for (String what : reach.reachedFrom(entry)) {
							events.add(SimpleConditionEvent.satisfied(item,
									root.getFullName() + " -> " + entry.getFullName() + " " + what));
						}
					}
				}
			}
		};
	}

	/** The call graph walk, memoised per strongly connected component. */
	private static final class Reach {

		private static final String KNOB = Knob.class.getName();

		private final DescribedPredicate<JavaClass> clientCode;
		private final Map<String, String> pending;
		private final Map<JavaCodeUnit, List<JavaCodeUnit>> successors = new HashMap<>();
		private final Map<JavaCodeUnit, Set<String>> reached = new HashMap<>();

		Reach(DescribedPredicate<JavaClass> clientCode, Map<String, String> pending) {
			this.clientCode = clientCode;
			this.pending = pending;
		}

		boolean isRoot(JavaCodeUnit codeUnit) {
			JavaClass owner = codeUnit.getOwner();
			return !isServerBalance(owner) && (clientCode.test(owner) || isDisplayBody(codeUnit));
		}

		/** Knob GETs in the code unit itself (its lambdas included), as {@code Holder.knob}. */
		static Set<String> ownKnobs(JavaCodeUnit codeUnit) {
			Set<String> knobs = new TreeSet<>();
			for (JavaFieldAccess access : codeUnit.getFieldAccesses()) {
				if (access.getAccessType() == JavaFieldAccess.AccessType.GET) {
					access.getTarget().resolveMember().filter(field -> field.isAnnotatedWith(KNOB))
							.ifPresent(field -> knobs.add(field.getOwner().getSimpleName() + "." + field.getName()));
				}
			}
			return knobs;
		}

		/** The code units {@code codeUnit} reaches in one step, ServerBalance left out. */
		List<JavaCodeUnit> successors(JavaCodeUnit codeUnit) {
			List<JavaCodeUnit> known = successors.get(codeUnit);
			if (known != null) {
				return known;
			}
			Set<JavaCodeUnit> out = new LinkedHashSet<>();
			List<JavaAccess<?>> invocations = new ArrayList<>(codeUnit.getCallsFromSelf());
			invocations.addAll(codeUnit.getCodeUnitReferencesFromSelf());
			for (JavaAccess<?> invocation : invocations) {
				CodeUnitAccessTarget target = (CodeUnitAccessTarget) invocation.getTarget();
				target.resolveMember().ifPresent(out::add);
				overridesOf(target, out);
			}
			suppliersRead(codeUnit, out);
			List<JavaCodeUnit> result = out.stream().filter(next -> !isServerBalance(next.getOwner())).toList();
			successors.put(codeUnit, result);
			return result;
		}

		/** Class hierarchy analysis: the overrides of {@code target} in subclasses of a mod class. */
		private static void overridesOf(CodeUnitAccessTarget target, Set<JavaCodeUnit> out) {
			JavaClass owner = target.getOwner();
			if (!isModClass(owner) || target.getName().equals("<init>")) {
				return;
			}
			String[] parameters = target.getRawParameterTypes().stream().map(JavaClass::getName)
					.toArray(String[]::new);
			for (JavaClass subclass : owner.getAllSubclasses()) {
				subclass.tryGetMethod(target.getName(), parameters).ifPresent(out::add);
			}
		}

		/** The supplier bridge: a JDK functional field read and invoked reaches its class's initialisers. */
		private static void suppliersRead(JavaCodeUnit codeUnit, Set<JavaCodeUnit> out) {
			Set<String> invokedTypes = new HashSet<>();
			for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
				invokedTypes.add(call.getTargetOwner().getName());
			}
			for (JavaFieldAccess access : codeUnit.getFieldAccesses()) {
				String type = access.getTarget().getRawType().getName();
				if (access.getAccessType() != JavaFieldAccess.AccessType.GET || !isJdkFunctional(type)
						|| !invokedTypes.contains(type)) {
					continue;
				}
				JavaClass declaring = access.getTarget().resolveMember().map(JavaField::getOwner)
						.orElse(access.getTargetOwner());
				if (isModClass(declaring)) {
					declaring.getStaticInitializer().ifPresent(out::add);
					out.addAll(declaring.getConstructors());
				}
			}
		}

		private static boolean isJdkFunctional(String type) {
			return type.startsWith("java.util.function.") || type.equals("java.lang.Runnable")
					|| type.equals("java.util.concurrent.Callable");
		}

		private static boolean isModClass(JavaClass javaClass) {
			return javaClass.getName().startsWith("dev.alaindustrial.");
		}

		/** Where the walk may go on: not into a root (it reports its own reach), not past a pending method. */
		private List<JavaCodeUnit> onward(JavaCodeUnit codeUnit) {
			if (pending.containsKey(codeUnit.getFullName())) {
				return List.of();
			}
			return successors(codeUnit).stream().filter(next -> !isRoot(next)).toList();
		}

		/** What a code unit contributes itself: its knob reads, or the pending marker if the walk stops at it. */
		private Set<String> own(JavaCodeUnit codeUnit) {
			String task = pending.get(codeUnit.getFullName());
			if (task != null) {
				return Set.of("reaches " + codeUnit.getFullName() + ", pending " + task);
			}
			Set<String> reads = new TreeSet<>();
			for (String knob : ownKnobs(codeUnit)) {
				reads.add("reads " + knob);
			}
			return reads;
		}

		/**
		 * Every knob reachable from {@code start}, itself included, as {@code reads <Holder.knob>}, and every pending
		 * method as {@code reaches <method>, pending <task>}. Tarjan's algorithm, iterative so a deep
		 * call chain cannot overflow the stack: one component's knobs are its members' own plus those of the
		 * components it calls, which are finished before it.
		 */
		Set<String> reachedFrom(JavaCodeUnit start) {
			Set<String> known = reached.get(start);
			if (known != null) {
				return known;
			}
			Map<JavaCodeUnit, Integer> index = new HashMap<>();
			Map<JavaCodeUnit, Integer> low = new HashMap<>();
			Deque<JavaCodeUnit> stack = new ArrayDeque<>();
			Set<JavaCodeUnit> onStack = new HashSet<>();
			Deque<Map.Entry<JavaCodeUnit, Iterator<JavaCodeUnit>>> work = new ArrayDeque<>();
			enter(start, index, low, stack, onStack, work);
			while (!work.isEmpty()) {
				Map.Entry<JavaCodeUnit, Iterator<JavaCodeUnit>> frame = work.peek();
				JavaCodeUnit node = frame.getKey();
				if (frame.getValue().hasNext()) {
					JavaCodeUnit next = frame.getValue().next();
					if (reached.containsKey(next)) {
						continue;
					}
					if (!index.containsKey(next)) {
						enter(next, index, low, stack, onStack, work);
					} else if (onStack.contains(next)) {
						low.put(node, Math.min(low.get(node), index.get(next)));
					}
					continue;
				}
				work.pop();
				if (!work.isEmpty()) {
					JavaCodeUnit parent = work.peek().getKey();
					low.put(parent, Math.min(low.get(parent), low.get(node)));
				}
				if (low.get(node).equals(index.get(node))) {
					finish(node, stack, onStack);
				}
			}
			return reached.get(start);
		}

		private void enter(JavaCodeUnit node, Map<JavaCodeUnit, Integer> index, Map<JavaCodeUnit, Integer> low,
				Deque<JavaCodeUnit> stack, Set<JavaCodeUnit> onStack,
				Deque<Map.Entry<JavaCodeUnit, Iterator<JavaCodeUnit>>> work) {
			index.put(node, index.size());
			low.put(node, index.get(node));
			stack.push(node);
			onStack.add(node);
			work.push(Map.entry(node, onward(node).iterator()));
		}

		/** Pops the component rooted at {@code node} and records its knobs for every member. */
		private void finish(JavaCodeUnit node, Deque<JavaCodeUnit> stack, Set<JavaCodeUnit> onStack) {
			List<JavaCodeUnit> members = new ArrayList<>();
			JavaCodeUnit member;
			do {
				member = stack.pop();
				onStack.remove(member);
				members.add(member);
			} while (member != node);
			SortedSet<String> knobs = new TreeSet<>();
			for (JavaCodeUnit each : members) {
				knobs.addAll(own(each));
				for (JavaCodeUnit next : onward(each)) {
					Set<String> theirs = reached.get(next);
					if (theirs != null) {
						knobs.addAll(theirs);
					}
				}
			}
			Set<String> shared = Collections.unmodifiableSortedSet(knobs);
			for (JavaCodeUnit each : members) {
				reached.put(each, shared);
			}
		}
	}
}
