package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.environment.WindMillOutput;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the wind mill — the passive height/sky/weather-driven LV generator. Mirrors the
 * structure of {@link WaterMillWheelGameTest} / {@link SolarPanelGameTest}: each method is one case, traced via
 * {@code @implements}, driving {@code serverTick} directly (deterministic, no waiting).
 *
 * <p><b>Height note.</b> A Fabric gametest structure sits near world Y = 0, well below sea level
 * ({@code level.getSeaLevel()} = 63), so the wind mill's height base is 0 in-world. The FUN/weather cases
 * therefore assert the mill's per-tick output equals {@link WindMillOutput#euFor} evaluated against the
 * <em>real</em> world state (absolute Y, sea level, sky, weather) — the same pure function {@code produce()}
 * calls — so the wiring (sky gate, weather read, sampling, buffering) is verified end-to-end regardless of
 * the region's altitude. The full height→base scaling and weather-multiplier arithmetic (0 at sea level,
 * +1/16 blocks, cap 4, rain ×1.5, thunder ×2, cap 8) is covered numerically at L1 in {@code WindMillOutputTest}.
 *
 * <p>Weather is set synchronously the way the solar suite does it — {@code WeatherData} plus the interpolated
 * rain level ({@code isRaining()} reads the latter). Numbers come from {@link Config} (canon), never hard-coded.
 *
 * <p><b>MOD-445/446.</b> All bodies live in {@link WindMillScenarios} (common); these wrappers keep only the
 * {@code @GameTest} wiring and the {@code @implements}/{@code @covers} tracing. The one exception is
 * {@link #tcWindmill001Phy01_backFaceOnlyOutput}, whose body checks the port via the Fabric-only
 * {@code EnergyStorage.SIDED} capability seam and therefore stays here.
 */
public class WindMillGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * @implements TC-WINDMILL-001-FUN01 — under open sky the wind mill produces the height/weather rate
	 *     {@link WindMillOutput#euFor} yields for the region, sampled every {@code windMillSampleTicks}. Driven
	 *     for more than one sample window; the accumulated EU equals the per-tick rate × ticks.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun01_generatesSampledRate(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Fun01_generatesSampledRate(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-STA01 — a thunderstorm multiplies the base rate (×windMillThunderFactor,
	 *     capped at windMillMaxEuPerTick): the storm rate is ≥ the clear-sky rate for the same block. The
	 *     per-tick output matches {@link WindMillOutput#euFor} evaluated with thunder active.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Sta01_thunderMultipliesRate(GameTestHelper helper) {
		GeneratorEnergyScenarios.windMillThunderMultipliesRate(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG01 — a solid roof (no open sky column) forces mode ROOFED. The
	 *     mill sits below sea level in the region (so EU/t is 0 from height regardless), which makes the
	 *     accumulated-EU check alone indistinguishable from "always 0" — so the case asserts the MODE code
	 *     on the maxProgress sync channel (3) as well, which is the only signal that distinguishes "dead from
	 *     height" from "dead from roof". Drives past a sample window under a thunderstorm for coverage.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg01_roofedYieldsZero(GameTestHelper helper) {
		GeneratorEnergyScenarios.windMillRoofedYieldsZero(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG02 — the spinning 2×2 rotor lives in the FRONT neighbour's block
	 *     space (the renderer pushes the quad 0.58 forward, past the mill's boundary). FACING = NORTH by
	 *     default, so the front is one block north. A solid block there stalls the blades: mode OBSTRUCTED.
	 *     The mode assertion is what catches the regression — EU is 0 from height here anyway.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg02_frontObstructionYieldsZero(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg02_frontObstructionYieldsZero(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG03 — the blade tips reach one block left/right of the FRONT block
	 *     (not the mill body). FACING = NORTH, so the front is north; its east neighbour is POS.north().east().
	 *     A solid block there stalls a blade tip: mode OBSTRUCTED.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg03_sideObstructionYieldsZero(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg03_sideObstructionYieldsZero(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG04 — the lower blade arc dips into the pit below the FRONT block.
	 *     A solid block directly beneath the front (centre of the pit) stalls the blades: mode OBSTRUCTED.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg04_pitObstructionYieldsZero(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg04_pitObstructionYieldsZero(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG05 — control case: with open sky and all clearance positions free,
	 *     the mill is NOT obstructed. On the region's low altitude EU/t is 0 from height, so the mode is
	 *     CALM — which is distinct from OBSTRUCTED and proves the clearance check does not fire on empty
	 *     space. Guards against false positives in {@code WindMillClearance}.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg05_clearAreaNotObstructed(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg05_clearAreaNotObstructed(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-PRF01 — the buffer caps at {@code Config.windMillBuffer}; excess EU is
	 *     discarded (use-it-or-lose-it), even if the mill is producing.
	 * @covers R-NRG-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Prf01_bufferCapsAtMax(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Prf01_bufferCapsAtMax(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-PER01 — the stored EU buffer survives an NBT save/load round-trip (energy
	 *     persists via the base MachineBlockEntity; the mill's sampling state is transient and recomputed).
	 * @covers R-PER-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Per01_energySurvivesNbtRoundTrip(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Per01_energySurvivesNbtRoundTrip(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-CON01 — the mill pushes EU into a BatteryBox placed against its BACK face
	 *     (the only output face, opposite of FACING). FACING defaults to NORTH, so the back is SOUTH.
	 *     The mill buffer is pre-filled so there is always EU to push.
	 * @covers R-NRG-03, R-CON-01
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Con01_pushesToAdjacentBattery(GameTestHelper helper) {
		GeneratorEnergyScenarios.windMillChargesAdjacentBox(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-PHY01 — the mill emits EU only from its BACK face (opposite of FACING);
	 *     the front and the four sides are inert (single-output contract, R-NRG-03). FACING = NORTH by
	 *     default, so SOUTH is the sole OUT face; NORTH/EAST/WEST/UP/DOWN must not extract.
	 * @covers R-NRG-03
	 */
	@GameTest
	public void tcWindmill001Phy01_backFaceOnlyOutput(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.WIND_MILL.defaultBlockState()
				.setValue(dev.alaindustrial.block.HorizontalMachineBlock.FACING, Direction.NORTH));
		// Only the back face (SOUTH) should support extraction.
		EnergyStorage back = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.SOUTH);
		if (back == null || !back.supportsExtraction()) {
			helper.fail("wind mill BACK (south) face must emit EU");
		}
		// The front and all four sides must be inert.
		for (Direction d : new Direction[]{
				Direction.NORTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
			EnergyStorage p = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (p != null && p.supportsExtraction()) {
				helper.fail("wind mill face " + d + " must NOT emit EU (only the back face does)");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WINDMILL-001-FUN02 — with no rotor in the slot the mill produces nothing, even under open
	 *     sky and storm. The rotor is a generation gate (progression), not just cosmetic.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun02_noRotorProducesNothing(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Fun02_noRotorProducesNothing(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG06 — two mills side by side (directly adjacent, same FACING) with
	 *     rotors in both: the 2×2 rotor discs are coplanar and overlap by a full block, so BOTH mills
	 *     report MODE_INTERFERENCE and produce nothing — there is no tie-break (MOD-051). Even a storm
	 *     does not override interference.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg06_sideBySideInterference(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg06_sideBySideInterference(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG07 — two mills facing each other across a one-block gap: both discs
	 *     live in front of their mills and overlap inside the gap column, so both report
	 *     MODE_INTERFERENCE (MOD-051). (Directly adjacent face-to-face mills are OBSTRUCTED instead —
	 *     each disc sits inside the other mill's solid block, which WindMillClearance already catches.)
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg07_faceToFaceInterference(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg07_faceToFaceInterference(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG08 — a mill running clean flips to MODE_INTERFERENCE within one
	 *     sample window after a rotor is installed in an adjacent mill (the disc appears only with a
	 *     rotor). Guards the "player builds a second mill next to a working one" path (MOD-051).
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 160)
	public void tcWindmill001Neg08_lateRotorTriggersInterference(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg08_lateRotorTriggersInterference(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG09 — control: mills two blocks apart (one air block between, same
	 *     FACING) have discs meeting exactly edge-to-edge, which is NOT interference — both keep running.
	 *     Guards against false positives that would outlaw legitimate compact wind farms (MOD-051).
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg09_spacedMillsNotInterfering(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg09_spacedMillsNotInterfering(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-NEG10 — control: directly adjacent mills facing AWAY from each other
	 *     (opposite FACING) put their discs on opposite sides — no overlap, no interference (MOD-051).
	 *     Turning mills apart is the documented way to pack them tightly.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Neg10_backToBackNotInterfering(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Neg10_backToBackNotInterfering(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-FUN04 — evolution freezes under interference: with a chip, a rotor and
	 *     an interfering neighbour, the evolve counter does not advance (blades that cannot turn do not
	 *     evolve — same rule as obstruction, MOD-051).
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun04_interferenceFreezesEvolution(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Fun04_interferenceFreezesEvolution(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-FUN03 — with an altitude chip and a rotor installed, the evolve counter
	 *     advances one tick per server-tick under open sky; once {@link Config#windMillEvolveTicks} is
	 *     reached the block transforms into {@code high_altitude_wind_mill} carrying its stored EU.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun03_dayChipEvolvesToHighAltitude(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Fun03_dayChipEvolvesToHighAltitude(helper);
	}

	/**
	 * MOD-211 — automation may insert a chip only while the slot is EMPTY. The container declares no
	 * max stack size and the chip is a plain 64-stack item, so before the guard a hopper or item pipe
	 * fed the slot one chip at a time up to 64. Regression guard: without the {@code isEmpty()} check
	 * in {@code WindMillBlockEntity.canPlaceItem} the second assertion fails.
	 */
	@GameTest
	public void windMill_automationCannotStackSecondChip(GameTestHelper helper) {
		WindMillScenarios.windMill_automationCannotStackSecondChip(helper);
	}

	/**
	 * MOD-211 — evolution consumes exactly ONE chip and carries the rest across. A world saved before
	 * the guard above can still hold a stack here, and the old code handed the shared helper a bare
	 * EMPTY, destroying all 64. Regression guard: without the {@code shrink(1)} the evolved mill's chip
	 * slot comes out empty and the count assertion fails.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void windMill_evolutionConsumesOneChipNotTheStack(GameTestHelper helper) {
		WindMillScenarios.windMill_evolutionConsumesOneChipNotTheStack(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-FUN05 — with a night (storm) chip and a rotor installed, the evolve
	 *     counter advances one tick per server-tick under open sky; once {@link Config#windMillEvolveTicks}
	 *     is reached the block transforms into {@code storm_wind_mill} carrying its stored EU and rotor,
	 *     and consuming the chip. Mirror of {@link #tcWindmill001Fun03_dayChipEvolvesToHighAltitude} for
	 *     the night branch — closes the Tempest-evolution test gap identified in MOD-172.
	 * @covers R-NRG-04
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Fun05_nightChipEvolvesToStorm(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Fun05_nightChipEvolvesToStorm(helper);
	}

	// ── MOD-189: rotor wear — the rotor is a durability component that wears out and breaks ───────────

	/**
	 * @implements TC-WINDMILL-001-WEAR01 — a producing T1 wind mill wears its rotor down and, once its
	 *     durability is spent, breaks it: the slot empties, generation halts and the mode drops to
	 *     MODE_NO_ROTOR. Config override (1 EU per durability point) makes wear fast and deterministic —
	 *     the wear RATE is read live — plus a rotor pre-damaged to one point from death. Regression guard:
	 *     without the wear code the rotor never breaks and the first assertion fails.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Wear01_rotorWearsOutAndBreaks(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Wear01_rotorWearsOutAndBreaks(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-WEAR02 — the rotor wear path is the SHARED
	 *     {@code AbstractGeneratorBlockEntity#wearComponent}, so it fires on the T2 evolutions too: a
	 *     producing high-altitude mill breaks a spent rotor exactly like the T1 mill (the storm mill uses
	 *     the identical shared call). Proves the wear call site in the T2 {@code produce()}.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Wear02_t2HighAltitudeRotorBreaks(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Wear02_t2HighAltitudeRotorBreaks(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-WEAR03 — a rotor in an idle mill (produces 0 EU) does NOT wear even at
	 *     the aggressive 1-EU-per-point rate: wear accrues only while the mill produces EU. The rotor is
	 *     pre-damaged to one point from death, so any spurious idle wear would break it.
	 *
	 *     <p>Idleness is forced with {@code windMillMaxBaseEuPerTick = 0} rather than by relying on the
	 *     rig sitting below the first height step. It used to rely on that, and MOD-347 broke it: the
	 *     stepped ramp became the smooth {@code WindProfile} curve, which yields a base of 1 only eight
	 *     blocks above sea level, so the "idle" mill started generating and chewed through its rotor.
	 *     Pinning the rate to zero at the source keeps this test about the wear gate
	 *     ({@code cachedRate > 0}) and immune to future retunes of the altitude curve.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Wear03_noWearWhileIdle(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Wear03_noWearWhileIdle(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-WEAR04 — evolution must NOT repair the rotor: a partially-worn rotor keeps
	 *     its exact damage when the mill evolves T1 → T2 (the shared {@code evolveInto} copies the stack). A
	 *     free repair on evolution would break the wear economy. Uses the low-Y region (rate 0) so no wear
	 *     accrues during the single evolution tick and the damage assertion is exact.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Wear04_wearSurvivesEvolution(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Wear04_wearSurvivesEvolution(helper);
	}

	/**
	 * @implements TC-WINDMILL-001-WEAR05 — wear tracks mechanical spinning, NOT delivered EU: a mill with a
	 *     FULL buffer and no downstream consumer still wears its rotor (the blades turn in the wind whether or
	 *     not the EU is stored). Pins the deliberate design decision (wear is not gated on the buffer-room
	 *     check) — a buffer-gated wear model would leave the rotor at full durability here and this test would
	 *     fail. Mirrors the "active tick = rate > 0" definition, distinct from the fuel generator's R-NRG-11
	 *     "full buffer pauses burn" (fuel is a consumed input; the free wind is not).
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void tcWindmill001Wear05_wearsAtFullBufferWithNoConsumer(GameTestHelper helper) {
		WindMillScenarios.tcWindmill001Wear05_wearsAtFullBufferWithNoConsumer(helper);
	}

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * MOD-356 — the wind mill's readout equals the buffer gain AND channel 2 stays the mechanical rate the
	 * rotor renderer spins on; the {@code max(1, ...)} floor holds under a tiny multiplier. Body: {@link
	 * GeneratorEnergyScenarios#windMillReadoutMatchesBufferGain}.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void mod356_windMillReadoutMatchesBufferGain(GameTestHelper helper) {
		GeneratorEnergyScenarios.windMillReadoutMatchesBufferGain(helper);
	}

	/**
	 * MOD-356 — the same readout contract on both T2 mills (high-altitude, storm), which got a
	 * {@code ContainerData} bridge of their own. Body: {@link GeneratorEnergyScenarios#t2WindMillReadoutsMatchBufferGain}.
	 */
	@GameTest(skyAccess = true, maxTicks = 120)
	public void mod356_t2WindMillReadoutsMatchBufferGain(GameTestHelper helper) {
		GeneratorEnergyScenarios.t2WindMillReadoutsMatchBufferGain(helper);
	}
}
