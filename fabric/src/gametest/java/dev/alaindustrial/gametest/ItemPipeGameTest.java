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
}
