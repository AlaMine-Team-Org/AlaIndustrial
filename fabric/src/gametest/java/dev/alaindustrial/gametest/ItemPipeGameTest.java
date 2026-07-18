package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Fabric registration for the loader-neutral MOD-104 item-pipe scenarios. */
public final class ItemPipeGameTest {

	/** MOD-115: pipe under a VANILLA furnace pulls the smelted result out of its bottom into a chest. */
	@GameTest
	public void mod115ExtractsVanillaFurnaceResultFromBottom(GameTestHelper helper) {
		ItemPipeScenarios.extractsFurnaceResultFromBottom(helper, Blocks.FURNACE, "vanilla furnace bottom extract");
	}

	/** MOD-115: same, but our iron furnace — proves the mod block exposes its result on the DOWN face too. */
	@GameTest
	public void mod115ExtractsIronFurnaceResultFromBottom(GameTestHelper helper) {
		ItemPipeScenarios.extractsFurnaceResultFromBottom(helper, ModContent.IRON_FURNACE.get(), "iron furnace bottom extract");
	}

	/** MOD-115 repro: one chest → two furnaces; the round-robin must feed BOTH, not just the first. */
	@GameTest
	public void mod115DistributesOneSourceToTwoFurnaces(GameTestHelper helper) {
		ItemPipeScenarios.distributesOneSourceToTwoFurnaces(helper);
	}

	/** MOD-115 repro: same two furnaces, but the network rebuilds between transfers (furnace lit-flicker). */
	@GameTest
	public void mod115DistributesToTwoFurnacesAcrossRebuilds(GameTestHelper helper) {
		ItemPipeScenarios.distributesToTwoFurnacesAcrossRebuilds(helper);
	}

	/** MOD-115 exact player case: chest(ore+coal) → two IRON furnaces via top(input)+side(fuel); both must fill. */
	@GameTest
	public void mod115DistributesOreAndCoalToTwoIronFurnaces(GameTestHelper helper) {
		ItemPipeScenarios.distributesOreAndCoalToTwoIronFurnaces(helper);
	}

	/** MOD-115 parallel guard: both furnaces must be fed within ONE interval, not serialised one-by-one. */
	@GameTest
	public void mod115FeedsBothFurnacesWithinOneInterval(GameTestHelper helper) {
		ItemPipeScenarios.feedsBothFurnacesWithinOneInterval(helper);
	}
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
