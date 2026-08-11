package dev.alaindustrial.block.entity;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Loader seam for the double-chest pair mechanic (MOD-391), same pattern as
 * {@link dev.alaindustrial.sound.MachineHum}: {@code common} cannot reference loader APIs, so the
 * loader that needs a callback installs it here at init.
 *
 * <p>The one current hook: NeoForge caches block capabilities ({@code BlockCapabilityCache}), and a
 * <em>property-only</em> state change — a chest pairing up or falling back to single — does not
 * auto-invalidate them (only a block change does). Vanilla NeoForge patches
 * {@code ChestBlockEntity.setBlockState} to call {@code invalidateCapabilities()} for exactly this
 * reason; our {@link AbstractChestBlockEntity#setBlockState} mirrors that through this seam. On
 * Fabric the hook stays {@code null} — its transfer API queries the block each time.
 */
public final class ChestPairHooks {

	/** Installed by the NeoForge entrypoint; invoked when a chest's TYPE/FACING changed in place. */
	public interface CapabilityInvalidator {
		void invalidate(BlockEntity chest);
	}

	public static volatile CapabilityInvalidator CAP_INVALIDATOR;

	private ChestPairHooks() {}
}
