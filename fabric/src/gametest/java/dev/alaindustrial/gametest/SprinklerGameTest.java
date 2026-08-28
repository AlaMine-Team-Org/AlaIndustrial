package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Sprinkler (MOD-525, suite TC-SPRINK-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/SprinklerScenarios} and the SAME bodies run
 * on the NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}, {@code sprinkler_*}).
 *
 * <p>Every body here runs on real level ticks rather than {@code AlaGameTestHelper.drive}, because
 * what they check is scheduling — see the note on {@code SprinklerScenarios}.
 */
public class SprinklerGameTest {

	/**
	 * @implements TC-SPRINK-001-FUN01 — the spray happens about once per configured interval and is
	 * charged to the tank each time. The one test that can see a machine which sleeps between
	 * attempts and so honours 41× its configured interval.
	 */
	@GameTest(maxTicks = 400)
	public void tcSprink001Fun01_spraysOncePerIntervalAndPays(GameTestHelper helper) {
		SprinklerScenarios.fun01SpraysOncePerIntervalAndPays(helper);
	}

	/**
	 * @implements TC-SPRINK-001-FUN02 — a hanging sprinkler reaches three blocks down and a standing
	 * one does not, which is the whole point of the ceiling mount.
	 */
	@GameTest(maxTicks = 300)
	public void tcSprink001Fun02_hangingReachesTheFieldBelow(GameTestHelper helper) {
		SprinklerScenarios.fun02HangingReachesTheFieldBelow(helper);
	}

	/**
	 * @implements TC-SPRINK-001-CON01 — a tank one millibucket short of a spray never fires. Asserted
	 * on the tank rather than on a crop: vanilla grows wheat on its own random tick, so "the crop did
	 * not grow" was a coin flip that went red in CI minutes after passing here.
	 */
	@GameTest(maxTicks = 300)
	public void tcSprink001Con01_tankBelowPriceNeverFires(GameTestHelper helper) {
		SprinklerScenarios.con01TankBelowPriceNeverFires(helper);
	}

	/**
	 * @implements TC-SPRINK-001-CON02 — a zone with nothing to grow costs nothing, so a finished
	 * field does not quietly drain the tank.
	 */
	@GameTest(maxTicks = 300)
	public void tcSprink001Con02_nothingToWaterCostsNothing(GameTestHelper helper) {
		SprinklerScenarios.con02NothingToWaterCostsNothing(helper);
	}
}
