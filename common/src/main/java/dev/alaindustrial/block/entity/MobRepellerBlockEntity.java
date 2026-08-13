package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.entity.MobRepellerField;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mob Repeller (MOD-278), LV tier: a guard field that expels actively hostile mobs from a cube
 * around the block for a constant EU upkeep — the way a creeper flees a cat. The sweep itself lives
 * in {@link MobRepellerField}; this entity owns the billing, the lit state and the tier ladder.
 *
 * <p><b>Upkeep, not per-expulsion.</b> Unlike the demand-driven heater, the field pays
 * {@link Config#mobRepellerEuPerTick} every tick while powered: the useful work is the base
 * STAYING clean, not the individual shove. At 0 EU the field goes dark, the block sleeps, and the
 * energy buffer's commit hook wakes it on the next delivery. No mastery XP is credited — like the
 * pump, a repeller runs unattended (MOD-264's reasoning applies unchanged).
 *
 * <p><b>Tier ladder.</b> A full {@link SoulVesselItem Soul Vessel} in the single slot evolves this
 * block into the next tier on the following server tick, consuming the vessel whole —
 * {@link MachineBlockEntity#evolveInto} carries energy and ownership exactly like the solar panel's
 * chip evolution (MOD-211). The MV/HV subclasses override the four tier hooks and nothing else.
 */
public class MobRepellerBlockEntity extends MachineBlockEntity implements MenuProvider {
	/** The single vessel slot; upgrade slots do not exist here ({@link #hasUpgradePanel()}). */
	public static final int VESSEL_SLOT = 0;

	/** Ticks until the next zone sweep; the per-tick upkeep drain is independent of this. */
	private int sweepCooldown;

	public MobRepellerBlockEntity(BlockPos pos, BlockState state) {
		this(ModContent.MOB_REPELLER_BE.get(), pos, state, EnergyTier.LV, Config.mobRepellerBuffer);
	}

	protected MobRepellerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, long capacity) {
		// One machine slot (the vessel); consumer contract: maxInsert = tier voltage, no extraction.
		super(type, pos, state, tier, 1, capacity, tier.maxVoltage(), 0L);
	}

	// --- tier hooks: the ONLY thing the MV/HV subclasses change ---

	/** Zone radius in blocks (a cube, matching the highlight dome). */
	public int fieldRange() {
		return Config.mobRepellerRange;
	}

	/** Field upkeep in EU/t while powered. */
	public int fieldEuPerTick() {
		return Config.mobRepellerEuPerTick;
	}

	/** Souls a vessel must hold to evolve this block, or 0 when this is the top tier. */
	public int evolveKillsNeeded() {
		return Config.mobRepellerEvolveKillsMv;
	}

	/** The next tier's block, or null when this is the top tier. */
	protected Block evolveTarget() {
		return ModContent.MOB_REPELLER_MV.get();
	}

	// --- machine contract ---

	/** No upgrade panel: overclockers/mute chips would be a dead promise on a field block. */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** Pure consumer on every face — the field is radial, so no face is special. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// 1) Tier evolution: a full vessel in the slot replaces this block with the next tier. The
		//    override map clears the slot, so the vessel is consumed whole (spec: surplus souls burn).
		Block target = evolveTarget();
		if (target != null) {
			ItemStack vessel = items.get(VESSEL_SLOT);
			if (!vessel.isEmpty() && SoulVesselItem.kills(vessel) >= evolveKillsNeeded()) {
				evolveInto(level, pos, target, Map.of(VESSEL_SLOT, ItemStack.EMPTY));
				return 0; // this block entity is gone after the transform
			}
		}

		// 2) Redstone: a signal SWITCHES THE FIELD OFF, the vanilla convention for a machine that runs
		//    by itself (a lever on the block is the simplest off switch a player can build). This is the
		//    mod's first redstone-controlled block — nothing else reads a signal — so the polarity is
		//    set here rather than copied: "powered = paused" matches the hopper and every furnace-like
		//    contraption players already know, and matches what `redstone_inverter_upgrade` is
		//    documented to INVERT should it ever be built.
		if (level.hasNeighborSignal(pos)) {
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}

		// 3) Field upkeep + sweep.
		int cost = fieldEuPerTick();
		boolean active = energy.getAmount() >= cost;
		updateLit(active);
		if (!active) {
			// Starved: sleep — energy delivery wakes this entity through the buffer's commit hook,
			// same contract the electric heater relies on.
			return IDLE_SLEEP_TICKS;
		}
		energy.drainInternal(cost);
		setChanged();
		if (--sweepCooldown <= 0) {
			sweepCooldown = Math.max(1, Config.mobRepellerScanIntervalTicks);
			if (level instanceof ServerLevel serverLevel) {
				MobRepellerField.sweep(serverLevel, pos, fieldRange());
			}
		}
		return 0;
	}

	/**
	 * Any Soul Vessel may enter an empty slot while a next tier exists — a HALF-FULL one included.
	 *
	 * <p>Rejecting an under-filled vessel was the first design and it read as a bug: the slot simply
	 * refused the item with no reason given, and the player had no way to learn how many souls were
	 * missing. Now the vessel goes in, the screen states what it still needs, and the evolution waits
	 * for the count (see {@link #onServerTick}). The empty-slot guard stays — that one is the solar
	 * panel's MOD-211 lesson: without it automation stacks vessels and the evolution wipes the surplus.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == VESSEL_SLOT
				&& items.get(VESSEL_SLOT).isEmpty()
				&& evolveTarget() != null
				&& stack.is(ModContent.SOUL_VESSEL.get());
	}

	// --- menu ---

	@Override
	public Component getDisplayName() {
		return Component.translatable(getBlockState().getBlock().getDescriptionId());
	}

	/**
	 * Each tier builds its OWN menu class (LV/MV/HV), because a menu type and its class are a pair the
	 * compiler must be able to see — a single shared menu class would make the menu↔screen pairing
	 * indistinguishable (enforced by {@code docs/tools/menu_screen_parity_check.py}).
	 */
	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		announceTo(player);
		return new MobRepellerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(level, worldPosition));
	}

	/**
	 * Tell the opening player which block this screen belongs to (a non-toggling dome message), so the
	 * dome button can show its real on/off state. Shared by every tier.
	 */
	protected final void announceTo(Player player) {
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
				&& level instanceof ServerLevel serverLevel) {
			dev.alaindustrial.network.NetworkDispatcher.get().sendToPlayer(serverPlayer,
					new dev.alaindustrial.network.RepellerDomePayload(
							serverLevel.dimension(), worldPosition, fieldRange(), false));
		}
	}
}
