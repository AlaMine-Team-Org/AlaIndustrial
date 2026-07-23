package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.ComponentWear;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
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

	/**
	 * Transient sub-point wear progress (MOD-189): EU counted toward the next durability point of the
	 * installed consumable (rotor / wheel). The bulk wear lives on the {@code ItemStack}'s damage
	 * component (persisted with the inventory); only this fractional remainder is kept here, and it resets
	 * to 0 on chunk unload — an always-lenient rounding worth far less than one durability point, negligible
	 * against a component's multi-hundred-thousand-EU life. Never serialised.
	 */
	private int wearAccumulatorEu;

	/** EU generated this tick (before buffer capping). Subclasses may also update progress here. */
	protected abstract int produce(Level level, BlockPos pos, BlockState state);

	/**
	 * Wear the consumable component in {@code slot} by one active tick (MOD-189) — call only when the
	 * generator actually produced EU this tick ({@code producedEu > 0}). Wear is proportional to the EU
	 * produced (times {@code weatherFactor} for adverse-weather stress); each {@link Config} EU-per-damage
	 * of accumulated production spends one durability point, and when the component hits its max durability
	 * it breaks: the slot is emptied, the vanilla item-break sound plays at the block, and the next tick's
	 * "no component" branch halts generation.
	 *
	 * <p><b>"Produced EU" == generation rate, not delivered EU.</b> {@code producedEu} is the rate the
	 * generator's {@code produce()} computed for this tick (blades/wheel genuinely turning); wear is charged
	 * on that mechanical work, so a mill with a FULL buffer or no downstream consumer still wears — its rotor
	 * spins in the wind regardless of whether the EU is stored or discarded. This matches the task's
	 * definition of an "active tick" (rotor + open sky + no obstruction + wind → rate > 0) and the
	 * "generators never sleep" model; it is deliberately NOT gated on the buffer-room check in
	 * {@link #onServerTick}. Note the rate is the value before {@link Config#globalEuRateMultiplier} is
	 * applied (that multiplier is a global EU-economy knob applied later, not mechanical output).
	 *
	 * <p><b>Soft migration.</b> An old rotor/wheel saved before MOD-189 re-resolves to the now-durable item
	 * on load (durability is a property of the item type, not the stack), so it reads as damage 0 (fully
	 * intact) and wears normally from then on — never retroactively broken. The {@code !isDamageableItem()}
	 * guard below only skips a genuinely non-damageable stack (e.g. a {@code max_damage=0} override) or a
	 * mis-configured {@code euPerDamage <= 0}.
	 *
	 * @param slot the component slot (rotor / wheel)
	 * @param producedEu EU produced this tick (> 0)
	 * @param weatherFactor adverse-weather wear multiplier (>= 1.0; 1.0 = none)
	 * @param euPerDamage EU of production per one durability point (from {@link Config}; guarded > 0)
	 */
	protected void wearComponent(Level level, BlockPos pos, int slot, int producedEu, float weatherFactor,
			int euPerDamage) {
		if (producedEu <= 0 || euPerDamage <= 0) {
			return;
		}
		ItemStack stack = items.get(slot);
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return; // only a genuinely non-damageable stack is skipped (see "Soft migration" above)
		}
		ComponentWear.Result result = ComponentWear.step(wearAccumulatorEu, producedEu, weatherFactor,
				euPerDamage, stack.getDamageValue(), stack.getMaxDamage());
		wearAccumulatorEu = result.accumulatorEu();
		if (result.broken()) {
			items.set(slot, ItemStack.EMPTY);
			if (level instanceof ServerLevel) {
				level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 0.9f);
			}
			setChanged();
			syncBlockEntityToClient();
		} else if (result.newDamage() != stack.getDamageValue()) {
			stack.setDamageValue(result.newDamage());
			setChanged();
		}
	}

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
		// Carry ownership across the evolution. The evolved block is created via setBlockAndUpdate — NOT a
		// player placement — so setPlacedBy never runs and the new block entity would default to a null
		// owner. Without this, an evolved T2 generator (wind mills, solar panels) attributes none of its
		// production to the player: it silently vanished from the profile's per-generator breakdown (MOD-133).
		java.util.UUID savedOwner = getOwner();
		String savedOwnerName = getOwnerName();
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
			evolved.setOwner(savedOwner, savedOwnerName);
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
