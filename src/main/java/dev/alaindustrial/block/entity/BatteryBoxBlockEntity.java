package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyNet;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModBlockEntities;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV BatteryBox (spec: alaindustrial:battery_box) — the first energy store. Buffers up to 20 000 EU,
 * accepts LV in and pushes LV out, stabilising the early network. v0.1 is a buffer node; the
 * charge/discharge item slots (and charge-on-drop NBT) are deferred until a portable EU item
 * exists (see the spec's v0.1 note). No inventory.
 */
public class BatteryBoxBlockEntity extends MachineBlockEntity implements MenuProvider {
	public BatteryBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BATTERY_BOX, pos, state, EnergyTier.LV, 0,
				Config.batteryBoxBuffer, EnergyTier.LV.maxVoltage(), EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Direct push to cable-less adjacent machines only; the cabled path is owned by the
		// EnergyNetwork, which treats the battery_box as both a producer and a consumer endpoint.
		EnergyNet.distribute(level, pos, this, true);
		// Storage keeps pushing every tick (a neighbour may appear with no wake event), so never sleeps.
		return 0;
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
			builder.set(ModDataComponents.STORED_ENERGY, energy.amount);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter getter) {
		super.applyImplicitComponents(getter);
		energy.amount = Math.min(getter.getOrDefault(ModDataComponents.STORED_ENERGY, 0L), energy.getCapacity());
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
