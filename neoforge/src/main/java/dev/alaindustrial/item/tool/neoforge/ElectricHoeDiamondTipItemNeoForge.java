package dev.alaindustrial.item.tool.neoforge;

import dev.alaindustrial.item.tool.ElectricHoeDiamondTipItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.context.UseOnContext;
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
		return RightClickAbility.HOE.canPerform(itemAbility);
	}

	/**
	 * The NeoForge tillability probe (MOD-389), declared again here for the same reason
	 * {@code canPerformAction} above is: this class extends the <i>common</i> upgrade, so it inherits nothing
	 * from the NeoForge base class. Without it the upgrade would fall back to the common (vanilla-map)
	 * answer on a loader that does not read that map. The probe itself is {@link RightClickAbility#wouldTill}.
	 */
	@Override
	protected boolean wouldTill(UseOnContext context) {
		return RightClickAbility.wouldTill(context);
	}
}
