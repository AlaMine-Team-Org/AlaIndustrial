package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-151 fluid-pipe scenarios. */
public final class FluidPipeGameTest {

	/** MOD-151: water crosses tank → pipe → pipe → tank. */
	@GameTest
	public void mod151TransfersBetweenTanks(GameTestHelper helper) {
		FluidPipeScenarios.transfersBetweenTanks(helper);
	}

	/** MOD-151: fluid occupies the segments while the line runs — it does not teleport past them. */
	@GameTest
	public void mod151SegmentsHoldFluidInTransit(GameTestHelper helper) {
		FluidPipeScenarios.segmentsHoldFluidInTransit(helper);
	}

	/** MOD-151: a re-enabled joint rejoins the halves (the MOD-282 defect, guarded up front). */
	@GameTest
	public void mod151ReEnabledPipeLinkRejoinsNetwork(GameTestHelper helper) {
		FluidPipeScenarios.reEnabledPipeLinkRejoinsNetwork(helper);
	}

	/** MOD-151: a filled segment refuses a different fluid. */
	@GameTest
	public void mod151SegmentRefusesASecondFluid(GameTestHelper helper) {
		FluidPipeScenarios.segmentRefusesASecondFluid(helper);
	}

	/** MOD-151: breaking a segment loses its contents without duplicating them. */
	@GameTest
	public void mod151BrokenSegmentLosesItsContents(GameTestHelper helper) {
		FluidPipeScenarios.brokenSegmentLosesItsContentsWithoutDuplicating(helper);
	}
}
