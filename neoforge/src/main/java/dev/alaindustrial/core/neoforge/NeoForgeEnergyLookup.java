package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.energy.EnergyRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * NeoForge implementation of the neutral {@link EnergyLookup} SPI (MOD-022 Phase 2): resolves the
 * per-face energy port through {@code Capabilities.Energy.BLOCK} (verified against 26.2.0.8-beta:
 * {@code BlockCapability<EnergyHandler, @Nullable Direction>}) and wraps the result as a neutral
 * {@link EnergyPort}. This is the per-loader capability-lookup seam.
 *
 * <p><b>Role preservation (this is load-bearing).</b> The network classifies each endpoint as producer
 * and/or consumer from its port's {@link EnergyPort#supportsInsertion()}/{@link EnergyPort#supportsExtraction()}.
 * On Fabric those survive the {@code EnergyStorage.SIDED} round-trip because Team Reborn's storage exposes
 * {@code supports*}. NeoForge's {@link EnergyHandler} has <em>no</em> {@code supports*} predicate, so routing
 * our own blocks through {@code Capabilities.Energy.BLOCK} would erase the per-face {@link EnergyRole} and
 * every face would read as {@link EnergyRole#BOTH} — the network would then treat every consumer as a
 * producer too, {@code computeConsumerDistances} would seed distance 1 everywhere, and MOD-021 cable loss
 * would floor to 0 (diverging from Fabric). Since all our machines implement {@link EnergyPortHost}, we take
 * the face-scoped {@link EnergyPort} straight from the block entity (role intact) and only fall back to the
 * capability — wrapped as {@link EnergyRole#BOTH}, the safe default for a foreign handler — for blocks that
 * are not ours. Caught by the NeoForge world gametest {@code mod021_loss_over_ten_cables}.
 */
public final class NeoForgeEnergyLookup implements EnergyLookup {

	@Override
	public EnergyPort find(Level level, BlockPos pos, Direction side) {
		if (level.getBlockEntity(pos) instanceof EnergyPortHost host) {
			return host.energyPort(side); // role-carrying face port (null on a NONE face)
		}
		EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
		return NeoForgeEnergyPort.of(handler, EnergyRole.BOTH);
	}
}
