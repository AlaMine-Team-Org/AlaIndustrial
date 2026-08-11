package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the machine upgrade slots + mute chip (MOD-080).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link MuteChipScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation,
 * so the SAME scenario runs on BOTH loaders (NeoForge registers it in {@code NeoForgeGameTests}).
 */
public class MuteChipGameTest {

	@GameTest
	public void muteChip_isMutedReflectsActiveSlot(GameTestHelper helper) {
		MuteChipScenarios.muteChip_isMutedReflectsActiveSlot(helper);
	}

	@GameTest
	public void muteChip_persistsAcrossNbtRoundTrip(GameTestHelper helper) {
		MuteChipScenarios.muteChip_persistsAcrossNbtRoundTrip(helper);
	}

	@GameTest
	public void muteChip_hoppersCannotReachUpgradeSlots(GameTestHelper helper) {
		MuteChipScenarios.muteChip_hoppersCannotReachUpgradeSlots(helper);
	}

	@GameTest
	public void muteChip_dropsOnBreak(GameTestHelper helper) {
		MuteChipScenarios.muteChip_dropsOnBreak(helper);
	}

	@GameTest
	public void muteChip_dropsFromFormerlySlotlessMachine(GameTestHelper helper) {
		MuteChipScenarios.muteChip_dropsFromFormerlySlotlessMachine(helper);
	}

	@GameTest
	public void muteChip_menuSlotFilterAndQuickMove(GameTestHelper helper) {
		MuteChipScenarios.muteChip_menuSlotFilterAndQuickMove(helper);
	}

	@GameTest
	public void muteChip_craftingRecipesResolve(GameTestHelper helper) {
		MuteChipScenarios.muteChip_craftingRecipesResolve(helper);
	}

	@GameTest
	public void overclocker_speedsUpAndCostsMore(GameTestHelper helper) {
		MuteChipScenarios.overclocker_speedsUpAndCostsMore(helper);
	}

	@GameTest
	public void overclocker_tierCapsTheChips(GameTestHelper helper) {
		MuteChipScenarios.overclocker_tierCapsTheChips(helper);
	}

	@GameTest
	public void overclocker_generatorIsNotOverclockable(GameTestHelper helper) {
		MuteChipScenarios.overclocker_generatorIsNotOverclockable(helper);
	}
}
