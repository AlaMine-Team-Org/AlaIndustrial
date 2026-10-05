package dev.alaindustrial.block;

import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tone;
import java.util.List;

/**
 * The hover tooltip of every cable grade (MOD-716), built from the grade itself so it can never drift from
 * the energy model: the tier line of its own voltage class (the gold cable is MV, the electrum one HV —
 * MOD-219), the segment buffer, the shock warning or the all-clear, and under [SHIFT] the loss per block.
 */
final class CableTooltip {

	private CableTooltip() {
	}

	static MachineTooltipSpec of(CableType type) {
		MachineTooltipSpec.Line safety = type.isInsulated()
				? MachineTooltipSpec.text("tooltip.alaindustrial.cable_safe", Tone.GREEN)
				: MachineTooltipSpec.text("tooltip.alaindustrial.cable_shock_warning", Tone.RED);
		return new MachineTooltipSpec(null,
				List.of(tierLine(type), MachineTooltipSpec.statValue("buffer", type::segmentBuffer), safety),
				List.of(MachineTooltipSpec.statValue("cable_loss", () -> lossPercent(type))));
	}

	private static MachineTooltipSpec.Line tierLine(CableType type) {
		return switch (type.tier()) {
			case LV -> MachineTooltipSpec.Tier.LV.line();
			case MV -> MachineTooltipSpec.Tier.MV.line();
			case HV -> MachineTooltipSpec.Tier.HV.line();
		};
	}

	/**
	 * The grade's loss as a percent-per-block string, read live from its {@link CableType}. Locale.ROOT plus a
	 * trailing-zero trim gives "2" for copper and "0.6" for tin (hence three decimals before trimming).
	 */
	static String lossPercent(CableType type) {
		double pct = type.lossPerBlock() * 100.0;
		String s = String.format(java.util.Locale.ROOT, "%.3f", pct);
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}
}
