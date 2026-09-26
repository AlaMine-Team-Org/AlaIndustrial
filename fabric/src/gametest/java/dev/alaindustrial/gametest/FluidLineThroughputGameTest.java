package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the MOD-677 fluid-line throughput scenarios. */
public final class FluidLineThroughputGameTest {

	@GameTest
	public void mod677BasicLineOf3(GameTestHelper helper) {
		FluidLineThroughputScenarios.basicLineOf3(helper);
	}

	@GameTest
	public void mod677BasicLineOf10(GameTestHelper helper) {
		FluidLineThroughputScenarios.basicLineOf10(helper);
	}

	@GameTest
	public void mod677BasicLineOf30(GameTestHelper helper) {
		FluidLineThroughputScenarios.basicLineOf30(helper);
	}

	@GameTest
	public void mod677AdvancedLineOf30(GameTestHelper helper) {
		FluidLineThroughputScenarios.advancedLineOf30(helper);
	}

	@GameTest
	public void mod677ThinSegmentCapsAThickLine(GameTestHelper helper) {
		FluidLineThroughputScenarios.thinSegmentCapsAThickLine(helper);
	}

	@GameTest
	public void mod677FullNearTankDoesNotStarveAFarOne(GameTestHelper helper) {
		FluidLineThroughputScenarios.fullNearTankDoesNotStarveAFarOne(helper);
	}
}
