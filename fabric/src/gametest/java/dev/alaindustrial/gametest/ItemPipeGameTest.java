package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-104 item-pipe scenarios. */
public final class ItemPipeGameTest {
	@GameTest
	public void mod104TransfersBetweenChests(GameTestHelper helper) {
		ItemPipeScenarios.transfersBetweenChests(helper);
	}

	@GameTest
	public void mod104DisabledFaceBlocksTransfer(GameTestHelper helper) {
		ItemPipeScenarios.disabledFaceBlocksTransfer(helper);
	}

	@GameTest
	public void mod104DisabledPipeLinkBlocksTransfer(GameTestHelper helper) {
		ItemPipeScenarios.disabledPipeLinkBlocksTransfer(helper);
	}

	/** MOD-108: chest → pipe → machine — the machine-automation path the chest-to-chest cases never touch. */
	@GameTest
	public void mod108InsertsIntoMachine(GameTestHelper helper) {
		ItemPipeScenarios.insertsIntoMachine(helper);
	}

	/** MOD-108 balance guard: one batch per interval, not one per tick (MOD-104 shipped 20 items/s). */
	@GameTest
	public void mod108RespectsTransferInterval(GameTestHelper helper) {
		ItemPipeScenarios.respectsTransferInterval(helper);
	}

	/** MOD-108: vanilla chests, not the mod's iron chest — the loader-registered capability seam. */
	@GameTest
	public void mod108TransfersBetweenVanillaChests(GameTestHelper helper) {
		ItemPipeScenarios.transfersBetweenVanillaChests(helper);
	}

	/** MOD-108: shift-click with the wrench dismantles our block, and leaves vanilla blocks alone. */
	@GameTest
	public void mod108WrenchDismantlesOwnBlocks(GameTestHelper helper) {
		ItemPipeScenarios.wrenchDismantlesOwnBlocks(helper);
	}
}
