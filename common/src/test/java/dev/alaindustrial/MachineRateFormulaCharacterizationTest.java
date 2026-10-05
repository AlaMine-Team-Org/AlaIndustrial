package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.core.upgrade.OverclockMath;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The machine tariff formula pinned on a grid of speed multipliers (MOD-710, batch 0), written before it
 * was moved into one place ({@code MachineRates}, batch 4) and kept through the removal of the
 * {@code Config} delegates that called it (batch 4b): the calls below are the call-site form those
 * delegates were inlined into, the expected numbers are the ones they returned.
 *
 * <p>The same arithmetic is {@link OverclockMath} with no chips. Every literal below is computed by hand
 * in float32, the way the JVM does it: {@code round(base * m)} and {@code round(ticks / m)}, each clamped
 * to at least 1, the two factors of the vanilla smelt rounded separately. The grid is
 * {@code m ∈ {0.05, 0.1, 1, 2, 3}}.
 */
class MachineRateFormulaCharacterizationTest {

	private static final float[] MULTIPLIERS = {0.05f, 0.1f, 1.0f, 2.0f, 3.0f};

	/** Effective EU/t for bases 1, 2, 6, 8, 24 — one row per multiplier. */
	private static final int[][] EU_PER_TICK = {
		{1, 1, 1, 1, 1},
		{1, 1, 1, 1, 2},
		{1, 2, 6, 8, 24},
		{2, 4, 12, 16, 48},
		{3, 6, 18, 24, 72},
	};
	private static final int[] EU_BASES = {1, 2, 6, 8, 24};

	/** Scaled duration for bases 1, 100, 130, 400 ticks — one row per multiplier. */
	private static final int[][] DURATION = {
		{20, 2000, 2600, 8000},
		{10, 1000, 1300, 4000},
		{1, 100, 130, 400},
		{1, 50, 65, 200},
		{1, 33, 43, 133},
	};
	private static final int[] DURATION_BASES = {1, 100, 130, 400};

	/** EU of one vanilla smelt at the shipped 100 ticks and 2 EU/t — one per multiplier. */
	private static final int[] VANILLA_SMELT_EU = {2000, 1000, 200, 200, 198};

	/**
	 * The zero-argument helpers on two sets of their own knobs (machine EU/t, heater EU/t, furnace
	 * duration), one row per multiplier: machine EU/t, heater EU/t, scaled furnace duration, scaled 1 tick,
	 * EU of one vanilla smelt. Captured from the formula as it stands (MOD-710 batch 4b, before the
	 * helpers are inlined into their call sites); 7 ticks at x2 is the 3.5 that pins the rounding mode.
	 */
	private static final int[][] KNOB_SETS = {{1, 3, 7}, {5, 7, 130}};
	private static final int[][][] LIVE_HELPERS = {
		{
			{1, 1, 140, 20, 140},
			{1, 1, 70, 10, 70},
			{1, 3, 7, 1, 7},
			{2, 6, 4, 1, 8},
			{3, 9, 2, 1, 6},
		},
		{
			{1, 1, 2600, 20, 2600},
			{1, 1, 1300, 10, 1300},
			{5, 7, 130, 1, 650},
			{10, 14, 65, 1, 650},
			{15, 21, 43, 1, 645},
		},
	};

	private float savedMultiplier;
	private int savedMachineEu;
	private int savedHeaterEu;
	private int savedFurnaceDuration;

	@BeforeEach
	void save() {
		savedMultiplier = Config.globalMachineSpeedMultiplier;
		savedMachineEu = Config.machineEuPerTick;
		savedHeaterEu = Config.electricHeaterEuPerTick;
		savedFurnaceDuration = Config.electricFurnaceDuration;
	}

	@AfterEach
	void restore() {
		Config.globalMachineSpeedMultiplier = savedMultiplier;
		Config.machineEuPerTick = savedMachineEu;
		Config.electricHeaterEuPerTick = savedHeaterEu;
		Config.electricFurnaceDuration = savedFurnaceDuration;
	}

	@Test
	void effectiveEuPerTickOnTheGrid() {
		for (int row = 0; row < MULTIPLIERS.length; row++) {
			for (int col = 0; col < EU_BASES.length; col++) {
				int expected = EU_PER_TICK[row][col];
				String at = "base " + EU_BASES[col] + " x" + MULTIPLIERS[row];
				assertEquals(expected, MachineRates.euPerTick(EU_BASES[col], MULTIPLIERS[row]), at);
				assertEquals(expected, OverclockMath.euPerTick(EU_BASES[col], MULTIPLIERS[row], 1.5f, 0),
						"OverclockMath with no chips, " + at);
			}
		}
	}

	@Test
	void scaledDurationOnTheGrid() {
		for (int row = 0; row < MULTIPLIERS.length; row++) {
			for (int col = 0; col < DURATION_BASES.length; col++) {
				int expected = DURATION[row][col];
				String at = "base " + DURATION_BASES[col] + " t x" + MULTIPLIERS[row];
				assertEquals(expected, MachineRates.duration(DURATION_BASES[col], MULTIPLIERS[row]), at);
				assertEquals(expected, OverclockMath.duration(expected, 0.7f, 0),
						"OverclockMath with no chips keeps the scaled duration, " + at);
			}
		}
	}

	@Test
	void vanillaSmeltEuOnTheGrid() {
		for (int row = 0; row < MULTIPLIERS.length; row++) {
			assertEquals(VANILLA_SMELT_EU[row], MachineRates.vanillaSmeltEu(100, 2, MULTIPLIERS[row]),
					"x" + MULTIPLIERS[row]);
		}
	}

	/**
	 * The electric heater bills its heat tick from ITS OWN knob. It is the one machine whose EU/t is not
	 * {@code machineEuPerTick}, and the formula above cannot tell which knob a call site passes in; an earlier
	 * form of this test compared {@code MachineRates.euPerTick(knob, m)} with itself (audit 2026-10-04).
	 * Read from the bytecode with ArchUnit, so no Minecraft class is loaded: both places that price a heat tick
	 * read {@code Config.electricHeaterEuPerTick}, never {@code Config.machineEuPerTick}, and hand it to the formula.
	 */
	@Test
	void theHeaterPricesItsHeatTickFromItsOwnKnob() {
		String heater = "dev.alaindustrial.block.entity.ElectricHeaterBlockEntity";
		URL bytecode = getClass().getClassLoader().getResource(heater.replace('.', '/') + ".class");
		assertNotNull(bytecode, heater + " bytecode not on the test classpath");
		JavaClass heaterClass = new ClassFileImporter().importUrl(bytecode).get(heater);
		for (JavaCodeUnit unit : List.of(heaterClass.getCodeUnitWithParameterTypeNames("heatTickCost", "int"),
				heaterClass.getMethod("canSupplyHeatTick"))) {
			Set<String> knobs = new TreeSet<>();
			for (JavaFieldAccess access : unit.getFieldAccesses()) {
				if (access.getTargetOwner().getFullName().equals(Config.class.getName())
						&& access.getName().endsWith("EuPerTick")) {
					knobs.add(access.getName());
				}
			}
			assertEquals(Set.of("electricHeaterEuPerTick"), knobs, unit.getFullName() + " must price a heat tick"
					+ " from Config.electricHeaterEuPerTick alone");
			assertTrue(unit.getMethodCallsFromSelf().stream().anyMatch(call -> call.getName().equals("euPerTick")
					&& call.getTargetOwner().getFullName().equals(MachineRates.class.getName())),
					unit.getFullName() + " must scale the knob through MachineRates.euPerTick");
		}
	}

	/** The call-site form on a spread of its own knobs, against numbers the delegates returned (4b). */
	@Test
	void liveHelpersOnAGridOfKnobs() {
		for (int set = 0; set < KNOB_SETS.length; set++) {
			Config.machineEuPerTick = KNOB_SETS[set][0];
			Config.electricHeaterEuPerTick = KNOB_SETS[set][1];
			Config.electricFurnaceDuration = KNOB_SETS[set][2];
			for (int row = 0; row < MULTIPLIERS.length; row++) {
				Config.globalMachineSpeedMultiplier = MULTIPLIERS[row];
				int[] expected = LIVE_HELPERS[set][row];
				String at = "knobs " + Arrays.toString(KNOB_SETS[set]) + " x" + MULTIPLIERS[row];
				assertEquals(expected[0], MachineRates.euPerTick(Config.machineEuPerTick,
						Config.globalMachineSpeedMultiplier), "machine EU/t, " + at);
				assertEquals(expected[1], MachineRates.euPerTick(Config.electricHeaterEuPerTick,
						Config.globalMachineSpeedMultiplier), "heater EU/t, " + at);
				assertEquals(expected[2], MachineRates.duration(Config.electricFurnaceDuration,
						Config.globalMachineSpeedMultiplier), "furnace, " + at);
				assertEquals(expected[3], MachineRates.duration(1,
						Config.globalMachineSpeedMultiplier), "one tick, " + at);
				assertEquals(expected[4], MachineRates.vanillaSmeltEu(Config.electricFurnaceDuration,
						Config.machineEuPerTick, Config.globalMachineSpeedMultiplier), "vanilla smelt, " + at);
			}
		}
	}

	/** The shipped defaults the vanilla-smelt row assumes; a rebalance fails here, not silently above. */
	@Test
	void shippedDefaultsBehindTheTable() {
		assertEquals(2, Config.machineEuPerTick);
		assertEquals(6, Config.electricHeaterEuPerTick);
		assertEquals(100, Config.electricFurnaceDuration);
	}
}
