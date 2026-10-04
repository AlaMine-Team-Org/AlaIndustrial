package dev.alaindustrial.item.tool;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.item.energy.PoweredToolTooltip;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The electric drills' tooltip (MOD-716), one shape for the three tiers: the usage line naming the tier's own
 * reference pickaxe, the Silk Touch state of the two tipped tiers, the column bore once installed, then the
 * charge. Each tier passes its own key prefix, so the netherite drill — a subclass of the diamond one — names
 * itself rather than the tier below it (MOD-482, MOD-534).
 */
final class DrillTooltips {
	private static final String PREFIX = "tooltip.alaindustrial.";

	private DrillTooltips() {
	}

	/** {@code tip} is the tier's key ({@code electric_drill_diamond_tip}), null for the plain drill. */
	static PoweredToolTooltip of(@Nullable String tip) {
		PoweredToolTooltip tooltip = PoweredToolTooltip.of("electric_drill",
				List.of(ServerBalance::electricDrillEuPerBlock));
		if (tip != null) {
			tooltip = tooltip.withUsageKey(PREFIX + tip + ".usage")
					.withBeforeCharge(SilkModeToggle.tooltipLine(PREFIX + tip));
		}
		return tooltip.withBeforeCharge(DrillTooltips::columnLine);
	}

	/**
	 * MOD-482: the column bore, on or off. Installed rather than inherent, so a drill that has never been to the
	 * Upgrade Table says nothing — a stat that cannot happen is noise.
	 */
	private static MachineTooltipSpec.@Nullable Line columnLine(net.minecraft.world.item.ItemStack stack) {
		if (!DrillUpgrades.has(stack, DrillUpgrades.COLUMN_BORE)) {
			return null;
		}
		return ElectricDrillItem.isColumnEnabled(stack)
				? MachineTooltipSpec.text(PREFIX + "electric_drill.column_on", MachineTooltipSpec.Tone.AQUA)
				: MachineTooltipSpec.text(PREFIX + "electric_drill.column_off", MachineTooltipSpec.Tone.GRAY);
	}
}
