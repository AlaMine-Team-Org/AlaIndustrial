package dev.alaindustrial.item.tool.neoforge;

import java.util.Set;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * The NeoForge half of the right-click seam on Minecraft 26.2 (MOD-704, batch 2): what the four
 * right-click tools declare to NeoForge, written once. The constants mirror
 * {@code dev.alaindustrial.compat.RightClickTransform}: {@link #HOE} and {@link #SHOVEL}.
 *
 * <h2>Why the tools still need NeoForge subclasses at all</h2>
 * NeoForge patches {@code HoeItem.useOn}/{@code ShovelItem.useOn} — which {@code RightClickTransform}
 * delegates to on this line — to ask the block ({@code IBlockStateExtension.getToolModifiedState}), and the
 * block opens with a gate on the HELD stack: {@code if (!itemStack.canPerformAction(ability)) return null}.
 * {@code ItemInstanceExtension.canPerformAction} answers from the item alone
 * ({@code typeHolder().value().canPerformAction(stack, ability)}), and the default
 * {@code IItemExtension.canPerformAction} is {@code false} for everything but {@code SWORD_SWEEP} — no data
 * component, tag or registry feeds it (verified in the NeoForge 26.2.0.67 sources and by javap of the
 * patched Minecraft jar). So the answer has to be an override on our item class, and the tools' classes are
 * shared {@code common/} classes that cannot name a NeoForge type: hence one thin subclass per tool. The
 * batch-2 spike (MOD-704 research.md) weighed and rejected the two ways around it: a
 * {@code BlockToolModificationEvent} listener (fired before the gate) would till and path, but other mods
 * asking {@code canPerformAction} about our tools would then get {@code false}; a mixin adding the override
 * to the common classes is a subclass list by another name.
 *
 * <p>What the subclasses no longer carry is logic: the ability set and the hoe's side-effect-free probe
 * live here, and each subclass is two one-line delegations.
 */
public enum RightClickAbility {

	/** The vanilla hoe's set ({@code HOE_TILL}), exactly what NeoForge's patched {@code HoeItem} answers. */
	HOE(ItemAbilities.DEFAULT_HOE_ACTIONS),

	/** The vanilla shovel's set ({@code SHOVEL_FLATTEN}, {@code SHOVEL_DOUSE}) — dousing rides on the same gate. */
	SHOVEL(ItemAbilities.DEFAULT_SHOVEL_ACTIONS);

	private final Set<ItemAbility> abilities;

	RightClickAbility(Set<ItemAbility> abilities) {
		this.abilities = abilities;
	}

	/** The tool's answer to {@code canPerformAction}: the vanilla tool's own set, nothing more. */
	public boolean canPerform(ItemAbility ability) {
		return abilities.contains(ability);
	}

	/**
	 * The NeoForge answer to "would a hoe convert this block?" (MOD-389), asked by the hoe before its charge
	 * gate. The shared {@code RightClickTransform.wouldTill} reads vanilla's {@code HoeItem.TILLABLES}, which
	 * this loader patches out of the flow — it would miss every block a mod contributes through
	 * {@code BlockToolModificationEvent}, and NeoForge's copy of the rules has no DOWN-face check, so the two
	 * would disagree on a click from below.
	 *
	 * <p>{@code simulate = true} is the whole point: with {@code false}, probing rooted dirt would pop a
	 * hanging root before the hoe has decided whether it can pay for the till. The call still routes through
	 * the held stack's {@code canPerformAction(HOE_TILL)} gate, so it answers for this item.
	 */
	public static boolean wouldTill(UseOnContext context) {
		return context.getLevel().getBlockState(context.getClickedPos())
				.getToolModifiedState(context, ItemAbilities.HOE_TILL, /*simulate*/ true) != null;
	}
}
