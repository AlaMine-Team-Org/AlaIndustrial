package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.menu.BatteryBoxMenu;
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
 * LV BatteryBox (spec: alaindustrial:battery_box) — the first energy store. Buffers up to 20 000 EU,
 * accepts LV in and pushes LV out, stabilising the early network. Since MOD-052 it also has the
 * charge slot the spec deferred "until a portable EU item exists": slot 0 accepts any powered item —
 * the Battery Pouch (MOD-052) and the Energy Pack (MOD-065) today — and refills its
 * {@code pouch_energy} from the buffer at the lower of the LV ceiling (32 EU/t) and the item's own
 * intake. GUI-only — hoppers can neither feed nor drain the slot. The discharge slot stays future work.
 */
public class BatteryBoxBlockEntity extends MachineBlockEntity implements MenuProvider {
	/** Slot 0 — the pouch charge slot (MOD-052). */
	public static final int CHARGE_SLOT = 0;

	public BatteryBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.BATTERY_BOX_BE.get(), pos, state, EnergyTier.LV, 1,
				Config.batteryBoxBuffer, EnergyTier.LV.maxVoltage(), EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Direct push to cable-less adjacent machines only; the cabled path is owned by the
		// EnergyNetwork, which treats the battery_box as both a producer and a consumer endpoint.
		DirectAdjacencyDistributor.distribute(level, pos, this, true);
		chargePouch();
		// Storage keeps pushing every tick (a neighbour may appear with no wake event), so never sleeps.
		return 0;
	}

	/**
	 * Refill the powered item in the charge slot from the buffer. The rate is the lower of the box's
	 * own LV ceiling and the item's intake ({@link ItemEnergy#inputRate}) — a pouch charges at 32 EU/t,
	 * an Energy Pack at its own rate, and neither can be force-fed faster than it accepts.
	 */
	private void chargePouch() {
		ItemStack target = getItem(CHARGE_SLOT);
		if (target.isEmpty() || energy.amount <= 0) {
			return;
		}
		long rate = Math.min(EnergyTier.LV.maxVoltage(), ItemEnergy.inputRate(target));
		long move = Math.min(Math.min(ItemEnergy.room(target), energy.amount), rate);
		if (move <= 0) {
			return;
		}
		energy.amount -= move;
		ItemEnergy.add(target, move);
		setChanged();
	}

	/**
	 * The charge slot takes any powered item — a Battery Pouch (MOD-052), an Energy Pack (MOD-065),
	 * and whatever gains a buffer later; {@code capacity > 0} is the single test for "this holds EU"
	 * (manual/GUI path; hoppers are cut off below).
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == CHARGE_SLOT && ItemEnergy.capacity(stack) > 0;
	}

	/**
	 * GUI-only slot: the base class delegates hopper insertion to {@link #canPlaceItem}, which would
	 * let hoppers push powered items in — cut that path off entirely. Extraction is already blocked by
	 * the default {@code isOutputSlot() == false}.
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	/**
	 * Single-axis IO (MOD-006, author decision A): the BatteryBox accepts charge ONLY on its {@code FACING}
	 * face and emits ONLY on the opposite face; the other four faces are inert. Input on the front lets
	 * the player aim the BatteryBox at a producer; output exits the back.
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

	/** BatteryBox is a storage sink: the network charges it only after working machines (MOD-009). */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	// R-BRK-07: carry the buffered EU on the dropped item so a charged BatteryBox keeps its charge through
	// break -> place. The block's loot table copies STORED_ENERGY from this block entity onto the drop;
	// placement applies it back here. Machines do NOT override these, so they lose their buffer on break.
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (energy.amount > 0) {
			builder.set(ModDataComponents.STORED_ENERGY.get(), energy.amount);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		energy.amount = Math.min(getter.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L), energy.getCapacity());
	}

	/** Five-wide data: base 0..3 plus the per-tick output cap (4) for the GUI readout. */
	private final ContainerData batteryBoxData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == 4
					? (int) Math.min(Integer.MAX_VALUE, energy.maxExtract)
					: BatteryBoxBlockEntity.this.dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index != 4) {
				BatteryBoxBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return batteryBoxData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.battery_box");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new BatteryBoxMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
