package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Jetpack (MOD-148, suite TC-JET-001). Thin Fabric wrappers: the bodies
 * are loader-neutral in {@code common/.../gametest/JetpackScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical
 * flight logic (EU burn per thrust tick, the powerless glide, the grounded/ceiling gates, charging).
 */
public class JetpackGameTest {

	/** @implements TC-JET-001-FUN01 — a held jump while airborne burns jetpackEuPerTick per tick
	 *     and zeroes the fall distance. */
	@GameTest
	public void tcJet001Fun01_thrustBurnsEu(GameTestHelper helper) {
		JetpackScenarios.fun01ThrustBurnsEuAndZeroesFall(helper);
	}

	/** @implements TC-JET-001-FUN02 — releasing jump stops the engine: no burn, the fall accumulates. */
	@GameTest
	public void tcJet001Fun02_releasedJumpBurnsNothing(GameTestHelper helper) {
		JetpackScenarios.fun02ReleasedJumpBurnsNothing(helper);
	}

	/** @implements TC-JET-001-FUN03 — a drained jetpack glides: no burn, but no fall damage either. */
	@GameTest
	public void tcJet001Fun03_drainedGlide(GameTestHelper helper) {
		JetpackScenarios.fun03DrainedGlideStillZeroesFall(helper);
	}

	/** @implements TC-JET-001-FUN04 — the jetpack charges in the Battery Box slot at
	 *     min(LV ceiling, its own intake). */
	@GameTest
	public void tcJet001Fun04_chargeInBatteryBox(GameTestHelper helper) {
		JetpackScenarios.fun04ChargeInBatteryBox(helper);
	}

	/** @implements TC-JET-001-FUN05 — a worn Energy Pack tops up a carried jetpack. */
	@GameTest
	public void tcJet001Fun05_wornPackChargesJetpack(GameTestHelper helper) {
		JetpackScenarios.fun05WornPackChargesJetpack(helper);
	}

	/** @implements TC-JET-001-FUN06 — the worn asset follows the charge (lit / drained, both explicit). */
	@GameTest
	public void tcJet001Fun06_wornAssetFollowsCharge(GameTestHelper helper) {
		JetpackScenarios.fun06WornAssetFollowsCharge(helper);
	}

	/** @implements TC-JET-001-FUN07 — a sub-cost tail (e.g. 16 EU at a 50 EU/t engine) burns to a
	 *     clean 0, never a dead 1% remainder. */
	@GameTest
	public void tcJet001Fun07_tailChargeDrainsToZero(GameTestHelper helper) {
		JetpackScenarios.fun07TailChargeDrainsToZero(helper);
	}

	/** @implements TC-JET-001-NEG04 — a jetpack that never fired this flight gives no glide: an
	 *     empty backpack falls exactly like no backpack. */
	@GameTest
	public void tcJet001Neg04_emptyJetpackFallsNormally(GameTestHelper helper) {
		JetpackScenarios.neg04EmptyJetpackFallsNormally(helper);
	}

	/** @implements TC-JET-001-FUN08 — a thrusting jetpack casts a moving light block; landing and the
	 *     once-per-tick sweep both clear it (the leak-free logout/death cleanup). */
	@GameTest
	public void tcJet001Fun08_flightGlowPlacedAndSwept(GameTestHelper helper) {
		JetpackScenarios.fun08FlightGlowPlacedAndSwept(helper);
	}

	/** @implements TC-JET-001-NEG01 — creative burns no EU but keeps the flight safety. */
	@GameTest
	public void tcJet001Neg01_creativeBurnsNothing(GameTestHelper helper) {
		JetpackScenarios.neg01CreativeBurnsNothing(helper);
	}

	/** @implements TC-JET-001-NEG02 — on the ground the engine is off. */
	@GameTest
	public void tcJet001Neg02_onGroundBurnsNothing(GameTestHelper helper) {
		JetpackScenarios.neg02OnGroundBurnsNothing(helper);
	}

	/** @implements TC-JET-001-NEG03 — above jetpackMaxY the engine refuses to thrust. */
	@GameTest
	public void tcJet001Neg03_aboveCeilingRefusesThrust(GameTestHelper helper) {
		JetpackScenarios.neg03AboveCeilingRefusesThrust(helper);
	}
}
