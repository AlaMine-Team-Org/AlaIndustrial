package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;

/**
 * Loader-neutral world-based gametest bodies (MOD-022) — world-content seams outside the
 * energy-network core: the pump fluid chain. Suite contract and shared helpers:
 * {@link EnergyScenarioSupport}.
 *
 * <p>MOD-310 — the two ore tier-gate bodies that used to live here were mirrors of the Fabric
 * {@code OreGameTest} written by hand for the NeoForge lane. The real bodies now live in
 * {@link OreScenarios} and run on both loaders, so the mirrors were removed rather than kept
 * as a second copy.
 */
public final class WorldContentScenarios {

	private WorldContentScenarios() {}

	// ── scenario 19: pump source → tank → sink (fluid transport) ───────────────────────────────────

	private static final BlockPos PUMP = new BlockPos(2, 2, 1);
	private static final BlockPos PUMP_SOURCE = new BlockPos(3, 2, 1);
	private static final BlockPos PUMP_SINK = new BlockPos(1, 2, 1);

	/**
	 * Pump moves a lava source into an adjacent geothermal generator's tank, which then burns it for EU.
	 * Proves the FluidPort/FluidTank abstraction works end-to-end on the NeoForge lane with a real fluid
	 * source (Capabilities.Fluid.BLOCK resolves, acquire+push commit, geo burns).
	 * Mirrors: FluidGameTest.tcFluidPump_lavaSourceToGeothermal (compressed)
	 */
	public static void pumpSourceToTankToSinkToEu(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST)); // face the source
		helper.setBlock(PUMP_SOURCE, Blocks.LAVA);
		// Geo sink on the pump's west face
		helper.setBlock(PUMP_SINK, ModContent.GEOTHERMAL_GENERATOR.get());
		if (be(helper, PUMP) instanceof dev.alaindustrial.block.entity.PumpBlockEntity pump) {
			pump.getEnergyStorage().setAmountUntracked(Config.pumpEuPerBucket * 4); // ample supply
			for (int i = 0; i < 40; i++) {
				pump.serverTick(level, helper.absolutePos(PUMP),
						level.getBlockState(helper.absolutePos(PUMP)));
				if (be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity geo) {
					geo.serverTick(level, helper.absolutePos(PUMP_SINK),
							level.getBlockState(helper.absolutePos(PUMP_SINK)));
				}
			}
			// The geo either burned the acquired lava to EU or has lava in its tank; either way the
			// pump moved fluid across. Assert the source was consumed OR the geo buffer grew.
			boolean sourceGone = !level.getFluidState(helper.absolutePos(PUMP_SOURCE))
					.isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA);
			long geoEu = be(helper, PUMP_SINK) instanceof dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity g
					? g.getEnergyStorage().getAmount() : -1;
			if (!sourceGone && geoEu <= 0) {
				helper.fail("pump moved no lava: source still present=" + !sourceGone + " geoEu=" + geoEu);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("pump block entity missing");
	}
}
