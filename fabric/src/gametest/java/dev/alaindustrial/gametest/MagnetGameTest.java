package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 functional suite for the Electromagnet (MOD-132, suite TC-MAGNET-001). Thin Fabric wrappers: the
 * bodies are loader-neutral in {@code common/.../gametest/MagnetScenarios} and the SAME bodies run on the
 * NeoForge {@code gameTestServer} lane ({@code NeoForgeGameTests}) — both loaders exercise identical pull
 * logic (velocity toward the player, per-item EU tariff, toggle, pickup-delay/range guards).
 */
public class MagnetGameTest {

	/** @implements TC-MAGNET-001-FUN01 — a charged, enabled magnet pulls a nearby drop and spends one item's EU. */
	@GameTest
	public void tcMagnet001Fun01_pullsNearbyDrop(GameTestHelper helper) {
		MagnetScenarios.fun01PullsNearbyDrop(helper);
	}

	/** @implements TC-MAGNET-001-FUN02 — a flat magnet (0 EU) moves nothing. */
	@GameTest
	public void tcMagnet001Fun02_flatMagnetInert(GameTestHelper helper) {
		MagnetScenarios.fun02FlatMagnetInert(helper);
	}

	/** @implements TC-MAGNET-001-FUN03 — a disabled magnet moves nothing and spends no EU. */
	@GameTest
	public void tcMagnet001Fun03_disabledMagnetInert(GameTestHelper helper) {
		MagnetScenarios.fun03DisabledMagnetInert(helper);
	}

	/** @implements TC-MAGNET-001-FUN04 — a drop still on its pickup delay is left alone. */
	@GameTest
	public void tcMagnet001Fun04_respectsPickupDelay(GameTestHelper helper) {
		MagnetScenarios.fun04RespectsPickupDelay(helper);
	}

	/** @implements TC-MAGNET-001-FUN05 — the spherical range refine (cube corner outside the sphere) is not pulled. */
	@GameTest
	public void tcMagnet001Fun05_outOfRangeIgnored(GameTestHelper helper) {
		MagnetScenarios.fun05OutOfRangeIgnored(helper);
	}

	/** @implements TC-MAGNET-001-FUN06 — use() toggles only on sneak; non-sneak passes through. */
	@GameTest
	public void tcMagnet001Fun06_toggleViaUse(GameTestHelper helper) {
		MagnetScenarios.fun06ToggleViaUse(helper);
	}

	/** @implements TC-MAGNET-001-PER01 — the on/off flag round-trips (absent = on). */
	@GameTest
	public void tcMagnet001Per01_toggleRoundTrip(GameTestHelper helper) {
		MagnetScenarios.per01ToggleRoundTrip(helper);
	}
}
