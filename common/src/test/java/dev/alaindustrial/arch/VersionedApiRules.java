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
import java.util.Set;

/**
 * API that differs between the Minecraft lines is called only through its version facade (MOD-703, batch 13;
 * ADR-036) — against the compiled bytecode, like {@link ArchitectureRules}, of which it is the facade chapter
 * (kept in its own class: {@code ArchitectureRules} is at the size ceiling of coding.md §10). Same import as
 * there: every {@code dev.alaindustrial} class of {@code common/src/main} and {@code common/src/gametest}.
 *
 * <p><b>Naming.</b> No {@code Test} suffix, like {@link ArchitectureRules}: nothing here to mutate.
 */
@AnalyzeClasses(packages = "dev.alaindustrial", importOptions = ImportOption.DoNotIncludeTests.class)
public class VersionedApiRules {

	/**
	 * A Minecraft member whose signature differs between the game lines, which only its version facade may call
	 * (MOD-703, batch 13; ADR-036). {@code owners} empty means any owner — the name alone is the API (the
	 * bone-meal trio is called through a block's own class as often as through the interface);
	 * {@code firstParameter} {@code null} means every overload, otherwise only the overloads whose first
	 * parameter is that type.
	 */
	record FacadeOnlyMember(Set<String> owners, String name, String firstParameter, String facade) {

		boolean matches(JavaClass owner, String calledName, List<JavaClass> parameterTypes) {
			if (!name.equals(calledName)) {
				return false;
			}
			if (!owners.isEmpty() && owners.stream().noneMatch(owner::isAssignableTo)) {
				return false;
			}
			return firstParameter == null
					|| !parameterTypes.isEmpty() && parameterTypes.get(0).getName().equals(firstParameter);
		}
	}

	private static final String POSES = "dev.alaindustrial.compat.client.Poses";
	private static final String BONEMEAL = "dev.alaindustrial.compat.Bonemeal";
	private static final String SERVER_DROPS = "dev.alaindustrial.compat.ServerDrops";
	private static final Set<String> POSE_STACK = Set.of("com.mojang.blaze3d.vertex.PoseStack");
	private static final Set<String> ITEM_DROPPERS = Set.of("net.minecraft.world.entity.player.Player",
			"net.minecraft.server.level.ServerPlayer", "net.minecraft.world.entity.LivingEntity");

	/**
	 * The members {@link #versionedApiIsCalledOnlyThroughItsFacade} keeps inside the facades. The list names the
	 * members of BOTH lines, so its text is the same on both: 26.3 {@code PoseStack.rotate(Quaternionfc)}, its
	 * {@code rotate}/{@code rotateDegrees(Axis, float)} shortcuts and the three bone-meal methods with a
	 * {@code BonemealSource}; 26.2 {@code PoseStack.mulPose(Quaternionfc)} and the trio without it. Dropping an
	 * item is {@code drop(ItemStack, boolean, Prediction)} on 26.3 and {@code drop(ItemStack, boolean)} on 26.2,
	 * putting one back {@code placeItemBackInInventory(ItemStack, Prediction)} against
	 * {@code placeItemBackInInventory(ItemStack)} (javap of both {@code minecraft-merged.jar}). The overloads
	 * the lines share stay free: {@code mulPose(Matrix4fc)}, {@code mulPose(Transformation)},
	 * {@code ServerPlayer.drop(boolean)}.
	 */
	static final List<FacadeOnlyMember> FACADE_ONLY_MEMBERS = List.of(
			new FacadeOnlyMember(POSE_STACK, "rotate", null, POSES),
			new FacadeOnlyMember(POSE_STACK, "rotateDegrees", null, POSES),
			new FacadeOnlyMember(POSE_STACK, "mulPose", "org.joml.Quaternionfc", POSES),
			new FacadeOnlyMember(Set.of(), "isValidBonemealTarget", null, BONEMEAL),
			new FacadeOnlyMember(Set.of(), "isBonemealSuccess", null, BONEMEAL),
			new FacadeOnlyMember(Set.of(), "performBonemeal", null, BONEMEAL),
			new FacadeOnlyMember(ITEM_DROPPERS, "drop", "net.minecraft.world.item.ItemStack", SERVER_DROPS),
			new FacadeOnlyMember(Set.of("net.minecraft.world.entity.player.Inventory"), "placeItemBackInInventory",
					null, SERVER_DROPS));

	/** The version-facade packages of ADR-036 — {@code compat} itself and {@code compat.client}, no subpackage. */
	static final String[] FACADE_PACKAGES = {"dev.alaindustrial.compat", "dev.alaindustrial.compat.client"};

	/**
	 * API whose signature differs between the Minecraft lines is called only through its version facade
	 * (MOD-703, batch 13; ADR-036). The facade has the same name and signature on both lines and its own body
	 * on each, so a renderer, a block entity or a scenario that goes through it is the same source on 26.3 and
	 * 26.2 and is carried to the other line by a plain {@code cherry-pick -x}. A direct call is the very
	 * conflict the facades exist to remove: {@code poseStack.rotate(q)} does not compile on 26.2, its
	 * {@code mulPose(q)} not on 26.3.
	 *
	 * <p>The ban could not be a tautology on either line: {@code ArchitectureRulesNegativeControl} proves on
	 * the production classes that each facade really calls one of its members on the line it runs on, and on
	 * a fixture that the condition reports a call, a method reference and a matching overload while letting
	 * the facade itself and a shared overload through. Overrides are not calls and stay allowed — a block
	 * that grows from bone meal must spell its line's signature (documented seams, {@code docs/BRANCHES.md}).
	 * A {@code noClasses().should(…)} rule: the condition reports {@code satisfied}.
	 */
	@ArchTest
	static final ArchRule versionedApiIsCalledOnlyThroughItsFacade = noClasses()
			.that().resideOutsideOfPackages(FACADE_PACKAGES)
			.should(callAFacadeOnlyMember(FACADE_ONLY_MEMBERS))
			.because("this API differs between the Minecraft lines (ADR-036): call its facade in "
					+ "dev.alaindustrial.compat (Poses.rotate, Bonemeal.*, ServerDrops.*), whose signature is the same "
					+ "on 26.3 and 26.2, so this source is the same on both lines");

	/**
	 * A call to, or a method reference of, one of {@code members}. Reports {@code satisfied}:
	 * {@code noClasses().should(…)} inverts it (see {@link ArchitectureRules#useUnorderedCollections()}).
	 * Parametrised so the negative control can aim it at stand-ins: the real owners are Minecraft classes this
	 * lane's classpath does not carry.
	 */
	static ArchCondition<JavaClass> callAFacadeOnlyMember(List<FacadeOnlyMember> members) {
		return new ArchCondition<>("call an API member that differs between the Minecraft lines") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
					for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
						report(item, events, codeUnit, call.getTargetOwner(), call.getName(),
								call.getTarget().getRawParameterTypes(), "");
					}
					for (JavaMethodReference reference : codeUnit.getMethodReferencesFromSelf()) {
						report(item, events, codeUnit, reference.getTargetOwner(), reference.getName(),
								reference.getTarget().getRawParameterTypes(), "a reference to ");
					}
				}
			}

			private void report(JavaClass item, ConditionEvents events, JavaCodeUnit codeUnit, JavaClass owner,
					String name, List<JavaClass> parameterTypes, String shape) {
				for (FacadeOnlyMember member : members) {
					if (member.matches(owner, name, parameterTypes)) {
						events.add(SimpleConditionEvent.satisfied(item, shape + owner.getSimpleName() + "." + name
								+ " in " + codeUnit.getFullName() + " — call " + member.facade() + " instead"));
						return;
					}
				}
			}
		};
	}
}
