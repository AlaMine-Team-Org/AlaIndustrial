package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric wiring for the Reinforced Energy Storage scenarios (MOD-351). Bodies live in
 * {@link CesuScenarios} so the NeoForge world lane runs exactly the same code.
 */
public class CesuGameTest {

	/**
	 * @implements TC-CESU-001-FUN01 — no sync channel overflows the 16-bit wire. Regression guard for
	 *     the shipped bug where the 100 000 EU buffer read "0 / −31072 EU (0%)" in the GUI.
	 */
	@GameTest
	public void tcCesu001Fun01_syncChannelsFitShort(GameTestHelper helper) {
		CesuScenarios.cesu01SyncChannelsFitShort(helper);
	}

	/**
	 * @implements TC-CESU-001-FUN02 — the scaled channels still describe the real buffer, so FUN01
	 *     cannot be satisfied by sending zeroes.
	 */
	@GameTest
	public void tcCesu001Fun02_scaledChannelsMatchBuffer(GameTestHelper helper) {
		CesuScenarios.cesu02ScaledChannelsMatchBuffer(helper);
	}

	/**
	 * @implements TC-CESU-001-FUN03 — the discharge slot drains a powered item into the buffer, capped
	 *     at the MV ceiling per tick, conserving EU.
	 */
	@GameTest
	public void tcCesu001Fun03_dischargeSlotFillsBuffer(GameTestHelper helper) {
		CesuScenarios.cesu03DischargeSlotFillsBuffer(helper);
	}
}
