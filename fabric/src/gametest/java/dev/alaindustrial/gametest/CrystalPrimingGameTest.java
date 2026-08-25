package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wiring for the EU crystal scenarios (MOD-504). Bodies live in
 * {@link CrystalPrimingScenarios} so the NeoForge world lane runs exactly the same code.
 */
public class CrystalPrimingGameTest {

	/**
	 * @implements TC-CRYSTAL-001-FUN01 — a full blank becomes the finished crystal in the charge slot.
	 */
	@GameTest
	public void tcCrystal001Fun01_fullBlankBecomesCrystal(GameTestHelper helper) {
		CrystalPrimingScenarios.crystal01FullBlankBecomesCrystal(helper);
	}

	/**
	 * @implements TC-CRYSTAL-001-FUN02 — a part-filled blank is never drained by a discharge slot.
	 */
	@GameTest
	public void tcCrystal001Fun02_blankRefusesToDischarge(GameTestHelper helper) {
		CrystalPrimingScenarios.crystal02BlankRefusesToDischarge(helper);
	}

	/**
	 * @implements TC-CRYSTAL-001-FUN03 — every tier finishes into its own crystal, and none carries EU.
	 */
	@GameTest
	public void tcCrystal001Fun03_everyTierFinishesIntoItsOwn(GameTestHelper helper) {
		CrystalPrimingScenarios.crystal03EveryTierFinishesIntoItsOwn(helper);
	}
}
