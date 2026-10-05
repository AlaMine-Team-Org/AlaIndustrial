package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tone;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The cable grades' tooltip (MOD-716, ADR-040): the tier line follows the grade's own voltage class, the safety
 * line follows its sleeve, and the loss is the trimmed percentage the shipped tooltip has always printed.
 */
class CableTooltipTest {

	private static List<String> keys(List<MachineTooltipSpec.Line> lines) {
		List<String> keys = new ArrayList<>();
		for (MachineTooltipSpec.Line line : lines) {
			keys.add(line.key());
		}
		return keys;
	}

	@Test
	void bareCopperIsLvWithAShockWarning() {
		MachineTooltipSpec spec = CableTooltip.of(CableType.COPPER);
		assertEquals(List.of("tooltip.alaindustrial.tier_lv", "tooltip.alaindustrial.buffer",
				"tooltip.alaindustrial.cable_shock_warning", "tooltip.alaindustrial.hold_shift"),
				keys(spec.lines(false, true)));
		assertEquals(Tone.RED, spec.basic().get(2).tone());
		assertEquals(List.of("tooltip.alaindustrial.tier_lv", "tooltip.alaindustrial.cable_shock_warning"),
				keys(spec.lines(true, false)));
		assertEquals(List.of("tooltip.alaindustrial.hold_shift"), keys(spec.lines(false, false)));
	}

	@Test
	void eachGradeNamesItsOwnTier() {
		assertEquals("tooltip.alaindustrial.tier_mv", CableTooltip.of(CableType.GOLD).basic().get(0).key());
		assertEquals("tooltip.alaindustrial.tier_hv",
				CableTooltip.of(CableType.INSULATED_ELECTRUM).basic().get(0).key());
		assertEquals(Tone.LIGHT_PURPLE, CableTooltip.of(CableType.ELECTRUM).basic().get(0).tone());
		assertEquals(Tone.GREEN, CableTooltip.of(CableType.INSULATED_TIN).basic().get(2).tone());
		assertEquals("tooltip.alaindustrial.cable_safe",
				CableTooltip.of(CableType.INSULATED_TIN).basic().get(2).key());
	}

	@Test
	void bufferAndLossComeFromTheGrade() {
		MachineTooltipSpec spec = CableTooltip.of(CableType.TIN);
		assertEquals(CableType.TIN.segmentBuffer(), spec.basic().get(1).argValues()[0]);
		assertEquals(CableTooltip.lossPercent(CableType.TIN), spec.detailed().get(0).argValues()[0]);
		assertEquals("tooltip.alaindustrial.cable_loss", spec.detailed().get(0).key());
	}

	@Test
	void lossIsTrimmed() {
		String copper = CableTooltip.lossPercent(CableType.COPPER);
		assertEquals(false, copper.endsWith("0") && copper.contains("."), copper);
		assertEquals(false, copper.endsWith("."), copper);
		assertEquals(CableType.COPPER.lossPerBlock() * 100.0, Double.parseDouble(copper), 1e-3);
	}
}
