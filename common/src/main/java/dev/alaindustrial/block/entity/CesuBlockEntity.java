package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.menu.CesuMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MV Reinforced Energy Storage (spec: alaindustrial:cesu) — the second step of the storage line.
 * Buffers {@link Config#cesuBuffer} EU (100 000 by default, five times the Battery Box) and moves it
 * at the MV ceiling, 128 EU/t in and out.
 *
 * <p>Everything about the network behaviour is inherited from the shared store contract and is
 * deliberately identical to {@link BatteryBoxBlockEntity}: single-axis IO, storage-sink priority
 * (machines are served first, MOD-009) and the fill-fraction cascade (MOD-314). The two differences
 * are the tier numbers above and the <b>discharge slot</b> below.
 *
 * <p><b>Discharge slot.</b> Slot 1 drains a powered item back into the buffer — the first stationary
 * store in the mod to do so; the Battery Box only charges. It is the mirror of the charge slot and is
 * rate-limited the same way, by the lower of the MV ceiling and the item's own throughput.
 */
public class CesuBlockEntity extends MachineBlockEntity implements MenuProvider {
	/** Slot 0 — charge a powered item from the buffer (same role as the Battery Box's only slot). */
	public static final int CHARGE_SLOT = 0;
	/** Slot 1 — drain a powered item into the buffer. New at this tier. */
	public static final int DISCHARGE_SLOT = 1;

	public CesuBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CESU_BE.get(), pos, state, EnergyTier.MV, 2,
				Config.cesuBuffer, EnergyTier.MV.maxVoltage(), EnergyTier.MV.maxVoltage());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Direct push to cable-less adjacent machines only; the cabled path belongs to the
		// EnergyNetwork, which treats a store as both a producer and a consumer endpoint.
		DirectAdjacencyDistributor.distribute(level, pos, this, true);
		chargeItem();
		dischargeItem();
		// A store keeps pushing every tick (a neighbour may appear with no wake event), so never sleeps.
		return 0;
	}

	/**
	 * Refill the powered item in the charge slot. Rate is the lower of this block's MV ceiling and the
	 * item's own intake, so nothing can be force-fed faster than it accepts.
	 */
	private void chargeItem() {
		ItemStack target = getItem(CHARGE_SLOT);
		if (target.isEmpty() || energy.getAmount() <= 0) {
			return;
		}
		long rate = Math.min(EnergyTier.MV.maxVoltage(), ItemEnergy.inputRate(target) * target.getCount());
		long budget = Math.min(Math.min(ItemEnergy.stackRoom(target), energy.getAmount()), rate);
		long moved = ItemEnergy.stackAdd(target, budget);
		if (moved <= 0) {
			return;
		}
		energy.drainInternal(moved);
		setChanged();
	}

	/**
	 * Drain the powered item in the discharge slot into the buffer. Bounded by three things at once —
	 * the MV ceiling, what the item still holds, and the room left in the buffer — so a full store or a
	 * flat item simply stops instead of spinning a no-op transaction every tick.
	 *
	 * <p>Moves the charge with {@link ItemEnergy#add} rather than {@code spend}: {@code spend} carries
	 * the creative guard (EU is treated as tool wear, and creative does not wear tools down), which is
	 * right for an item being <em>used</em> and wrong here. This is a transfer the player asked for by
	 * putting the item in the slot, so it happens in creative too — the same rule the Charging Station
	 * already follows in the other direction.
	 */
	private void dischargeItem() {
		ItemStack source = getItem(DISCHARGE_SLOT);
		if (source.isEmpty()) {
			return;
		}
		long room = energy.getCapacity() - energy.getAmount();
		if (room <= 0) {
			return;
		}
		long budget = Math.min(Math.min(ItemEnergy.stackGet(source), room), EnergyTier.MV.maxVoltage());
		long moved = -ItemEnergy.stackAdd(source, -budget);
		if (moved <= 0) {
			return;
		}
		energy.produceInternal(moved);
		setChanged();
	}

	/**
	 * Both slots take any powered item ({@code capacity > 0} is the single test for "this holds EU").
	 * The charge slot is filtered the same way as the Battery Box's; the discharge slot accepts the
	 * same items because "can hold EU" and "can give EU back" are the same set here.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return (slot == CHARGE_SLOT || slot == DISCHARGE_SLOT) && ItemEnergy.capacity(stack) > 0;
	}

	/**
	 * GUI-only slots: the base class delegates hopper insertion to {@link #canPlaceItem}, which would
	 * otherwise let hoppers feed powered items in. Same manual-only rule as the Battery Box.
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	/**
	 * Single-axis IO, identical to the Battery Box (MOD-006): charge enters on the {@code FACING} face,
	 * energy leaves through the opposite one, the other four faces are inert.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (worldFace == facing) {
			return EnergyRole.IN;
		}
		if (worldFace == facing.getOpposite()) {
			return EnergyRole.OUT;
		}
		return EnergyRole.NONE;
	}

	/** A store is a storage sink: the network charges it only after working machines (MOD-009). */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	/**
	 * Takes part in the storage→storage cascade (MOD-314). Balancing is by fill <em>fraction</em>, which
	 * is what makes a mixed bank read correctly: a 20 000 Battery Box and a 100 000 store settle at the
	 * same percentage, not the same charge.
	 */
	@Override
	public boolean acceptsCascade() {
		return true;
	}

	// R-BRK-07: carry the buffered EU on the dropped item so a charged store keeps its charge through
	// break -> place. The loot table copies STORED_ENERGY off this block entity onto the drop.
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (energy.getAmount() > 0) {
			builder.set(ModDataComponents.STORED_ENERGY.get(), energy.getAmount());
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		energy.setAmountUntracked(Math.min(getter.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L), energy.getCapacity()));
	}

	/**
	 * Scale for the two energy channels below: EU are divided by this before crossing the wire.
	 *
	 * <p><b>Why this exists.</b> A vanilla {@code DataSlot} serialises as a signed 16-bit short, so
	 * anything above 32 767 arrives negative on the client. This buffer is 100 000 EU, and the raw
	 * value landed as −31 072 (100 000 truncated to its low 16 bits) — the GUI read
	 * "0 / −31072 EU (0%)". The Battery Box never hit this because 20 000 fits.
	 *
	 * <p>At ÷100 the capacity travels as 1 000, which leaves room up to ~3.2 million EU. The cost is
	 * a 100 EU display granularity on a 100 000 EU buffer — a tenth of a percent, invisible next to a
	 * block that moves 128 EU every tick.
	 *
	 * <p>The same class of bug already bit the solar panel's evolution bar and is why the Teleporter
	 * sends permille instead of its 500 000 EU fund. The rule for this mod: never send a raw value
	 * that can exceed a short — send a scaled projection and keep the exact number server-side.
	 */
	public static final int SYNC_SCALE = 100;

	/** Channel 0 — stored EU divided by {@link #SYNC_SCALE}. */
	public static final int DATA_ENERGY_SCALED = 0;
	/** Channel 1 — capacity divided by {@link #SYNC_SCALE}. */
	public static final int DATA_CAPACITY_SCALED = 1;

	/**
	 * Five-wide data — hides {@link MachineBlockEntity#DATA_COUNT} on purpose so
	 * {@code CesuBlockEntity.DATA_COUNT} always names <em>this</em> machine's width, for the block entity
	 * below and for {@code CesuMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = 5;

	/**
	 * Five-wide data: base 0..3 plus the per-tick output cap (4).
	 *
	 * <p>The two energy channels are <b>scaled in place</b> rather than duplicated into extra channels.
	 * Every reader goes through {@code CesuMenu.getEnergy()/getCapacity()}, which multiply the scale
	 * back in, so nothing sees a wrong number — and, critically, no channel on this block can carry a
	 * value outside the short range. Leaving the raw EU in channels 0/1 "for compatibility" would have
	 * kept sending an overflowing value down the wire that merely nobody read.
	 */
	private final ContainerData cesuData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> (int) Math.min(Integer.MAX_VALUE, energy.maxExtract);
				case DATA_ENERGY_SCALED -> (int) (energy.getAmount() / SYNC_SCALE);
				case DATA_CAPACITY_SCALED -> (int) (energy.getCapacity() / SYNC_SCALE);
				default -> CesuBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != DATA_ENERGY_SCALED && index != DATA_CAPACITY_SCALED) {
				CesuBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return cesuData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.cesu");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new CesuMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
