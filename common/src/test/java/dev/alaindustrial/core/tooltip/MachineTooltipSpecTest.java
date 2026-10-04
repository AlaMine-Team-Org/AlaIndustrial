package dev.alaindustrial.core.tooltip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Line;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tier;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The four tooltip modes of an owner-declared description (MOD-716, ADR-040): [SHIFT] up/down x EU numbers
 * on/off, decided by each line's {@link MachineTooltipSpec.Shown} flag. These are the rules the four parallel
 * lists of the old {@code MachineTooltips} encoded by hand; the golden {@code MachineTooltipsGoldenTest} proves
 * the shipped descriptions reproduce them for every item, this pins the rules themselves.
 */
class MachineTooltipSpecTest {

	private static final String HOLD = MachineTooltipSpec.HOLD_SHIFT_KEY;

	private static List<String> keys(List<Line> lines) {
		List<String> keys = new ArrayList<>();
		for (Line line : lines) {
			keys.add(line.key());
		}
		return keys;
	}

	/** The standard machine: numbers before [SHIFT], tier and more numbers under it. */
	private static final MachineTooltipSpec MACHINE = MachineTooltipSpec.processing(() -> 2, () -> 150, () -> 800);

	@Test
	void processingMachineInAllFourModes() {
		assertEquals(List.of("tooltip.alaindustrial.energy_input", "tooltip.alaindustrial.duration_ticks", HOLD),
				keys(MACHINE.lines(false, true)));
		assertEquals(List.of("tooltip.alaindustrial.energy_input", "tooltip.alaindustrial.duration_ticks",
				"tooltip.alaindustrial.tier_lv", "tooltip.alaindustrial.buffer", "tooltip.alaindustrial.energy_per_op"),
				keys(MACHINE.lines(true, true)));
		assertEquals(List.of(HOLD), keys(MACHINE.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.tier_lv"), keys(MACHINE.lines(true, false)));
	}

	@Test
	void perOperationEnergyIsDrawTimesDurationReadLive() {
		AtomicInteger draw = new AtomicInteger(2);
		MachineTooltipSpec spec = MachineTooltipSpec.processing(draw::get, () -> 150, () -> 800);
		Line perOp = spec.detailed().get(1);
		assertArrayEquals(new Object[] {300}, perOp.argValues());
		draw.set(3);
		assertArrayEquals(new Object[] {450}, perOp.argValues(), "the value must be read at draw time");
	}

	@Test
	void labelLinesShowWithNumbersAndOnlyUnderShiftWithout() {
		// The battery box shape: the tier is a basic line, a word line under [SHIFT].
		MachineTooltipSpec box = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.stat("capacity", () -> 40000), Tier.LV.line()),
				List.of(MachineTooltipSpec.text("tooltip.alaindustrial.battery_box_io", Tone.GRAY)));
		assertEquals(List.of("tooltip.alaindustrial.capacity", "tooltip.alaindustrial.tier_lv", HOLD),
				keys(box.lines(false, true)));
		assertEquals(List.of("tooltip.alaindustrial.capacity", "tooltip.alaindustrial.tier_lv",
				"tooltip.alaindustrial.battery_box_io"), keys(box.lines(true, true)));
		assertEquals(List.of(HOLD), keys(box.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.tier_lv", "tooltip.alaindustrial.battery_box_io"),
				keys(box.lines(true, false)));
	}

	@Test
	void alwaysLinesShowInEveryMode() {
		// The sprinkler shape: no tier, two sizes that are not energy figures.
		MachineTooltipSpec sprinkler = new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.plain("tank_mb", () -> 4000)),
				List.of(MachineTooltipSpec.plain("range", () -> 4)));
		assertEquals(List.of("tooltip.alaindustrial.tank_mb", HOLD), keys(sprinkler.lines(false, false)));
		assertEquals(List.of("tooltip.alaindustrial.tank_mb", "tooltip.alaindustrial.range"),
				keys(sprinkler.lines(true, false)));
		assertEquals(keys(sprinkler.lines(true, false)), keys(sprinkler.lines(true, true)));
	}

	@Test
	void theHintAndTheTiersKeepTheirColours() {
		Line hold = MACHINE.lines(false, true).get(2);
		assertEquals(Tone.DARK_GRAY, hold.tone());
		assertEquals(Tone.GREEN, Tier.LV.line().tone());
		assertEquals(Tone.GREEN, Tier.MV.line().tone());
		assertEquals(Tone.LIGHT_PURPLE, Tier.HV.line().tone());
		assertEquals("tooltip.alaindustrial.tier_hv", Tier.HV.line().key());
		assertEquals(Tone.GRAY, MachineTooltipSpec.stat("buffer", () -> 1).tone());
	}

	@Test
	void labelledLineKeepsItsLabelAndItsStat() {
		Line mode = MachineTooltipSpec.labelled("gui.alaindustrial.incubator.mode.transform", "duration_ticks",
				() -> 300);
		assertEquals("gui.alaindustrial.incubator.mode.transform", mode.label());
		assertEquals("tooltip.alaindustrial.duration_ticks", mode.key());
		assertArrayEquals(new Object[] {300}, mode.argValues());
		assertEquals(MachineTooltipSpec.Shown.NUMBERS, mode.shown());
	}

	@Test
	void valueLinesKeepTheTypeOfTheirArgument() {
		assertEquals(Long.class, MachineTooltipSpec.statValue("buffer", () -> 12L).argValues()[0].getClass());
		assertEquals(String.class, MachineTooltipSpec.statValue("cable_loss", () -> "0.6").argValues()[0].getClass());
		assertEquals(Integer.class, MachineTooltipSpec.stat("buffer", () -> 12).argValues()[0].getClass());
	}

	@Test
	void listsAreCopiedNotShared() {
		List<Line> basic = new ArrayList<>(List.of(MachineTooltipSpec.stat("buffer", () -> 1)));
		MachineTooltipSpec spec = new MachineTooltipSpec(Tier.LV, basic, List.of());
		basic.clear();
		assertEquals(1, spec.basic().size());
		assertThrows(UnsupportedOperationException.class, () -> spec.basic().add(Tier.LV.line()));
	}
}
