package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.gametest.framework.GameTestHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric entry point for the MOD-433 capability/port parity sweep. The body is loader-neutral
 * ({@link BlockCapabilityParityScenarios}); what lives here is the one loader-specific part — the
 * three probes that ask Fabric's own lookups ({@code EnergyStorage.SIDED}, {@code FluidStorage.SIDED},
 * {@code ItemStorage.SIDED}) exactly the way another mod would. The NeoForge lane supplies the
 * {@code Capabilities.*.BLOCK} probes in {@code NeoForgeGameTests}.
 */
public class BlockCapabilityParityGameTest {

	private static final BlockCapabilityParityScenarios.Probes PROBES = new BlockCapabilityParityScenarios.Probes(
			(level, pos, side) -> EnergyStorage.SIDED.find(level, pos, side) != null,
			(level, pos, side) -> FluidStorage.SIDED.find(level, pos, side) != null,
			(level, pos, side) -> ItemStorage.SIDED.find(level, pos, side) != null);

	@GameTest
	public void mod433_capabilitiesMatchPorts(GameTestHelper helper) {
		BlockCapabilityParityScenarios.capabilitiesMatchPorts(helper, PROBES);
	}
}
