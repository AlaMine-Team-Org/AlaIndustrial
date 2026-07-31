package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Fluxweave armour set (MOD-127, suite TC-FLUX-001). Thin Fabric wrappers:
 * the bodies are loader-neutral in {@code common/.../gametest/FluxweaveArmorScenarios} and the SAME
 * bodies run on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests},
 * {@code fluxweave_*}), so both loaders exercise identical logic.
 */
public class FluxweaveArmorGameTest {

	/** @implements TC-FLUX-001-REG01 — a drained set still grants the material's armour and toughness. */
	@GameTest
	public void tcFlux001Reg01_drainedSetKeepsBaseProtection(GameTestHelper helper) {
		FluxweaveArmorScenarios.reg01DrainedSetKeepsBaseProtection(helper);
	}

	/** @implements TC-FLUX-001-FUN01 — charging switches the per-slot bonuses on. */
	@GameTest
	public void tcFlux001Fun01_chargeSwitchesBonusesOn(GameTestHelper helper) {
		FluxweaveArmorScenarios.fun01ChargeSwitchesBonusesOn(helper);
	}

	/** @implements TC-FLUX-001-CON01 — boots soften falls but never cancel them. */
	@GameTest
	public void tcFlux001Con01_bootsSoftenButNeverCancelFalls(GameTestHelper helper) {
		FluxweaveArmorScenarios.con01BootsSoftenButNeverCancelFalls(helper);
	}

	/** @implements TC-FLUX-001-FUN02 — step assist starts off and toggles both ways. */
	@GameTest
	public void tcFlux001Fun02_stepAssistIsOptIn(GameTestHelper helper) {
		FluxweaveArmorScenarios.fun02StepAssistIsOptIn(helper);
	}

	/** @implements TC-FLUX-001-CON02 — the set bonus applies once a second, not once per piece. */
	@GameTest(maxTicks = 200)
	public void tcFlux001Con02_setBonusAppliesOncePerSecond(GameTestHelper helper) {
		FluxweaveArmorScenarios.con02SetBonusAppliesOncePerSecond(helper);
	}

	/** @implements TC-FLUX-001-CON03 — one drained piece breaks the 4/4 bonus. */
	@GameTest(maxTicks = 200)
	public void tcFlux001Con03_deadPieceBreaksTheSet(GameTestHelper helper) {
		FluxweaveArmorScenarios.con03DeadPieceBreaksTheSet(helper);
	}

	/** @implements TC-FLUX-001-REG02 — the worn asset follows the charge and is never dropped. */
	@GameTest
	public void tcFlux001Reg02_wornAssetFollowsCharge(GameTestHelper helper) {
		FluxweaveArmorScenarios.reg02WornAssetFollowsCharge(helper);
	}
}
