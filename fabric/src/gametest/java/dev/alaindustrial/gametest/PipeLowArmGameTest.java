package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-540 low-arm scenarios. */
public final class PipeLowArmGameTest {

	@GameTest
	public void mod540ItemPipeDropsArmTowardHalfBlock(GameTestHelper helper) {
		PipeLowArmScenarios.itemPipeDropsArmTowardHalfBlock(helper);
	}

	@GameTest
	public void mod540ItemPipeKeepsArmLevelWithoutHalfBlocks(GameTestHelper helper) {
		PipeLowArmScenarios.itemPipeKeepsArmLevelWithoutHalfBlocks(helper);
	}

	@GameTest
	public void mod540ItemPipeLowArmKeepsFaceMode(GameTestHelper helper) {
		PipeLowArmScenarios.itemPipeLowArmKeepsFaceMode(helper);
	}

	@GameTest
	public void mod540ItemPipeVerticalFaceNeverDrops(GameTestHelper helper) {
		PipeLowArmScenarios.itemPipeVerticalFaceNeverDrops(helper);
	}

	@GameTest
	public void mod540ItemPipeRederivesStaleLowArm(GameTestHelper helper) {
		PipeLowArmScenarios.itemPipeRederivesStaleLowArm(helper);
	}

	@GameTest
	public void mod540FluidPipeLowArmDropsShape(GameTestHelper helper) {
		PipeLowArmScenarios.fluidPipeLowArmDropsShape(helper);
	}
}
