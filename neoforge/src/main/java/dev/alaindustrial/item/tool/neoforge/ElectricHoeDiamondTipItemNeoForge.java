package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import net.minecraft.world.item.ItemInstance;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * NeoForge {@link ElectricHoeDiamondTipItem}: the upgrade's half of the {@code HOE_TILL} declaration.
 *
 * <p>Identical in purpose to {@link ElectricHoeItemNeoForge} — see that class for the full explanation of
 * why the ability has to be declared at all on this loader. It cannot simply inherit that fix, because the
 * upgrade extends the <i>common</i> {@code ElectricHoeDiamondTipItem}, not the NeoForge subclass; Java has
 * no multiple inheritance and the shared irrigation logic must stay in {@code common/} where both loaders
 * run it.
 *
 * <p>Without this the upgrade would be strictly worse than useless on NeoForge: its whole selling point is
 * that a tilled plot comes out watered, and the till itself would never happen (TC-HOE-001-FUN02 caught
 * exactly that). Extending the common class keeps every {@code instanceof} dispatch intact — energy
 * ({@code ItemEnergy}), the tooltip line, and the irrigation in {@code useOn} all continue to resolve.
 */
public class ElectricHoeDiamondTipItemNeoForge extends ElectricHoeDiamondTipItem {

	public ElectricHoeDiamondTipItemNeoForge(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
		return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility);
	}
}
