package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.ElectricShovelDiamondTipItem;
import net.minecraft.world.item.ItemInstance;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * NeoForge {@link ElectricShovelDiamondTipItem}: the upgrade's half of the
 * {@code SHOVEL_FLATTEN}/{@code SHOVEL_DOUSE} declaration.
 *
 * <p>Identical in purpose to {@link ElectricShovelItemNeoForge} — see that class for the full
 * explanation of why the abilities have to be declared at all on this loader (patched
 * {@code ShovelItem.useOn} asks the block through {@code getToolModifiedState}, which opens with a hard
 * gate on the <i>held</i> stack, and delegation cannot clear it because the held stack is still ours).
 * The upgrade cannot simply inherit that fix: it extends the <i>common</i>
 * {@code ElectricShovelDiamondTipItem}, not the NeoForge subclass, Java has no multiple inheritance, and
 * the shared Silk Touch logic must stay in {@code common/} where both loaders run it — the same split the
 * hoe upgrade already lives with ({@link ElectricHoeDiamondTipItemNeoForge}).
 *
 * <p>This is a known-recurring defect class, not a hypothetical: the missing declaration shipped to
 * players twice, on the hoe (MOD-378) and on the base shovel (MOD-379), and both times it was a player
 * who found it. Declaring both abilities in one set is deliberate —
 * {@code DEFAULT_SHOVEL_ACTIONS} is exactly <code>{SHOVEL_FLATTEN, SHOVEL_DOUSE}</code>, and naming only
 * the flatten ability would leave campfire dousing broken, which is the half no path-only test catches.
 *
 * <p>Extending the common class keeps every {@code instanceof} dispatch intact: energy
 * ({@code ItemEnergy}), the tooltip charge lines and the Silk Touch toggle all continue to resolve.
 */
public class ElectricShovelDiamondTipItemNeoForge extends ElectricShovelDiamondTipItem {

	public ElectricShovelDiamondTipItemNeoForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
		return ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility);
	}
}
