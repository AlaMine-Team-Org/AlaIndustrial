package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * MOD-661 — flowing fluid washes away the mod's transport lines. Thin Fabric wrapper: the body is
 * loader-neutral in {@code common/.../gametest/FluidWashScenarios} and the NeoForge lane runs the same.
 */
public class FluidWashGameTest {

	/** @implements MOD-661 — every cable, pipe and the monitoring wire is washed away, a full-height pipe too. */
	@GameTest(maxTicks = 200)
	public void mod661WaterWashesAwayEveryTransportLine(GameTestHelper helper) {
		FluidWashScenarios.waterWashesAwayEveryTransportLine(helper);
	}
}
