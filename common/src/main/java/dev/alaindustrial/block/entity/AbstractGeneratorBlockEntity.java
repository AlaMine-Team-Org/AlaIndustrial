package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Common base for every EU producer. Generators never accept external energy; each tick they ask
 * the subclass how much EU to {@link #produce}, add it to the buffer (capped, use-it-or-lose-it),
 * then push to neighbours. Subclasses only express their generation rule (fuel, sunlight, ...).
 */
public abstract class AbstractGeneratorBlockEntity extends MachineBlockEntity {
	protected AbstractGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, int slots, long capacity, long maxExtract) {
		super(type, pos, state, tier, slots, capacity, 0L, maxExtract);
	}

	/** EU generated this tick (before buffer capping). Subclasses may also update progress here. */
	protected abstract int produce(Level level, BlockPos pos, BlockState state);

	/**
	 * Generators are producers: every face but FACING emits, none accepts (R-NRG-03). FACING is
	 * energy-inert — no cable/hopper connects through the front (D-FACING).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.OUT);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int made = produce(level, pos, state);
		made = (made > 0) ? Math.max(1, Math.round(made * Config.globalEuRateMultiplier)) : 0;
		boolean changed = false;
		if (made > 0) {
			// MOD-156: "active in the mod" time is credited whenever the generator is RUNNING, before and
			// independently of the buffer-room check below. A mature base saturates its network as a normal
			// steady state, and gating the clock on `room > 0` froze the dashboard's uptime readout there
			// indefinitely — production attribution needs that gate, elapsed time does not.
			if (getOwner() != null && level instanceof ServerLevel activeLevel) {
				PlayerStatsTracker.get().recordActive(activeLevel.getServer(), getOwner());
			}
			long room = energy.getCapacity() - energy.amount;
			if (room > 0) {
				long credited = Math.min(room, (long) made);
				energy.amount += credited;
				changed = true;
				// MOD-133: attribute the EU actually credited (not `made` before the cap) to the owner —
				// career statistics + per-generator breakdown. No-op without an owner or off-server; the
				// tracker additionally drops offline/creative owners.
				if (getOwner() != null && level instanceof ServerLevel serverLevel) {
					Identifier generatorId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
					PlayerStatsTracker.get()
							.recordProduction(serverLevel.getServer(), getOwner(), generatorId, credited);
				}
			}
		}
		// Direct push to a cable-less adjacent machine. The cabled path is owned by the EnergyNetwork
		// (which pulls from this generator's buffer), so skip cable neighbours to avoid double delivery.
		DirectAdjacencyDistributor.distribute(level, pos, this, true);
		updateLit(made > 0);
		if (changed) {
			setChanged();
		}
		// Generators never sleep: production depends on world state (sunlight, fuel, lava) with no
		// wake event, and they must keep pushing to neighbours/network every tick (R-29).
		return 0;
	}

	/**
	 * Replace this generator with its evolved branch — shared by the solar panel and wind mill
	 * evolution paths. Carries stored EU (clamped to the evolved block's capacity), preserves the
	 * FACING blockstate when both the old and new blocks have one, and consumes the chip slot (the
	 * caller passes a {@code slotClearer} that does both jobs specific to this generator's slot
	 * layout — e.g. clearing {@code CHIP_SLOT} on both, and snapshotting the wind-mill rotor so it
	 * can be re-placed on the evolved mill).
	 *
	 * @param target the block to evolve into (e.g. {@code ModContent.DAYLIGHT_SOLAR_PANEL.get()})
	 * @param slotOverrides additional slots to copy from this generator into the evolved block BEFORE
	 *     the energy transfer (e.g. the wind mill's rotor slot). Map of slot index → stack to place.
	 *     Empty for the solar panel.
	 */
	protected void evolveInto(Level level, BlockPos pos, Block target, Map<Integer, net.minecraft.world.item.ItemStack> slotOverrides) {
		long saved = energy.amount;
		for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : slotOverrides.entrySet()) {
			// Caller has already snapshotted these into the overrides map; clear the source slot so the
			// block's inventory reads empty before the swap (the chip slot is always cleared here too —
			// see callers).
			items.set(entry.getKey(), net.minecraft.world.item.ItemStack.EMPTY);
		}
		BlockState oldState = getBlockState();
		BlockState newState = target.defaultBlockState();
		// Preserve FACING when both old and new blocks have it (wind mill family); the solar panels
		// have no FACING, so this is a no-op for them.
		if (oldState.hasProperty(HorizontalMachineBlock.FACING)
				&& newState.hasProperty(HorizontalMachineBlock.FACING)) {
			newState = newState.setValue(HorizontalMachineBlock.FACING, oldState.getValue(HorizontalMachineBlock.FACING));
		}
		level.setBlockAndUpdate(pos, newState);
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity evolved) {
			evolved.getEnergyStorage().amount = Math.min(saved, evolved.getEnergyStorage().getCapacity());
			for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : slotOverrides.entrySet()) {
				int slot = entry.getKey();
				net.minecraft.world.item.ItemStack stack = entry.getValue();
				if (!stack.isEmpty() && slot >= 0 && slot < evolved.getContainerSize()) {
					evolved.setItem(slot, stack);
				}
			}
			evolved.setChanged();
		}
	}
}
