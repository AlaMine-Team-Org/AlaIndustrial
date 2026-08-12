package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
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
import net.minecraft.world.level.storage.ValueInput;

/**
 * LV BatteryBox (spec: alaindustrial:battery_box) — the first energy store. Buffers up to 20 000 EU,
 * accepts LV in and pushes LV out, stabilising the early network. Since MOD-052 it has the
 * charge slot the spec deferred "until a portable EU item exists": slot 0 accepts any powered item —
 * the Battery Pouch (MOD-052), the Energy Pack (MOD-065), the Battery (MOD-083) — and refills its
 * {@code pouch_energy} from the buffer at the lower of the LV ceiling (32 EU/t) and the item's own
 * intake.
 *
 * <p>Since MOD-083 it also has a <b>discharge slot</b> (slot 1), the mirror of the charge one. The
 * Battery is available at LV, so without it the only way to get charge back out of a battery would be
 * the MV store — a whole tier away from the item that needs it. Both slots are GUI-only: hoppers can
 * neither feed nor drain them.
 */
public class BatteryBoxBlockEntity extends MachineBlockEntity implements MenuProvider {
	/** Slot 0 — the pouch charge slot (MOD-052). */
	public static final int CHARGE_SLOT = 0;
	/** Slot 1 — drain a powered item into the buffer (MOD-083). */
	public static final int DISCHARGE_SLOT = 1;
	/** Machine slots (charge + discharge); the upgrade slots are appended after these by the base. */
	public static final int MACHINE_SLOTS = 2;

	public BatteryBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.BATTERY_BOX_BE.get(), pos, state, EnergyTier.LV, MACHINE_SLOTS,
				Config.batteryBoxBuffer, EnergyTier.LV.maxVoltage(), EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Direct push to cable-less adjacent machines only; the cabled path is owned by the
		// EnergyNetwork, which treats the battery_box as both a producer and a consumer endpoint.
		DirectAdjacencyDistributor.distribute(level, pos, this, true);
		chargeItem();
		dischargeItem();
		// Storage keeps pushing every tick (a neighbour may appear with no wake event), so never sleeps.
		return 0;
	}

	/**
	 * Refill the powered item in the charge slot from the buffer. The rate is the lower of the box's
	 * own LV ceiling and what the stack accepts ({@link ItemEnergy#inputRate} per item × its count) — a
	 * pouch charges at 32 EU/t, an Energy Pack at its own rate, and neither can be force-fed faster
	 * than it accepts.
	 *
	 * <p>All arithmetic goes through the stack-aware helpers (MOD-083): the Battery stacks, and its
	 * charge is per item, so a stack of sixteen costs sixteen times as much to fill. For every
	 * {@code stacksTo(1)} item those helpers are the identity of their per-item twins.
	 */
	private void chargeItem() {
		ItemStack target = getItem(CHARGE_SLOT);
		if (target.isEmpty() || energy.getAmount() <= 0) {
			return;
		}
		long rate = Math.min(EnergyTier.LV.maxVoltage(), ItemEnergy.inputRate(target) * target.getCount());
		long budget = Math.min(Math.min(ItemEnergy.stackRoom(target), energy.getAmount()), rate);
		long moved = ItemEnergy.stackAdd(target, budget);
		if (moved <= 0) {
			return;
		}
		energy.drainInternal(moved);
		setChanged();
	}

	/**
	 * Drain the powered item in the discharge slot into the buffer (MOD-083) — the mirror of
	 * {@link #chargeItem()}, bounded by the LV ceiling, what the stack still holds and the room left in
	 * the buffer, so a flat item or a full store simply stops instead of spinning a no-op every tick.
	 *
	 * <p>Moves the charge with the stack-aware {@code add}, never {@code spend}: spending carries the
	 * creative guard (EU is treated as tool wear, and creative does not wear tools down), which is right
	 * for an item being <em>used</em> and wrong here — this is a transfer the player asked for by putting
	 * the item in the slot. Same rule the CESU discharge slot follows.
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
		long budget = Math.min(Math.min(ItemEnergy.stackGet(source), room), EnergyTier.LV.maxVoltage());
		long moved = -ItemEnergy.stackAdd(source, -budget);
		if (moved <= 0) {
			return;
		}
		energy.produceInternal(moved);
		setChanged();
	}

	/**
	 * Both slots take any powered item — a Battery Pouch (MOD-052), an Energy Pack (MOD-065), a Battery
	 * (MOD-083), and whatever gains a buffer later; {@code capacity > 0} is the single test for "this
	 * holds EU" (manual/GUI path; hoppers are cut off below). The discharge slot accepts the same set,
	 * because "can hold EU" and "can give EU back" are the same thing here.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return (slot == CHARGE_SLOT || slot == DISCHARGE_SLOT) && ItemEnergy.capacity(stack) > 0;
	}

	/**
	 * Reads the container, then migrates a pre-MOD-083 save (C-20).
	 *
	 * <p>Upgrade slots are numbered <em>after</em> the machine slots, so adding the discharge slot moved
	 * them from 1…4 to 2…5. A box saved before this change therefore loads its first upgrade chip into
	 * what is now the discharge slot. The tell is unambiguous, because {@link #canPlaceItem} has always
	 * refused anything without an EU buffer: a non-powered item sitting in the discharge slot can only
	 * have come from the old numbering. When that is what we see, shift the whole run one slot up.
	 */
	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ItemStack inDischarge = getItem(DISCHARGE_SLOT);
		if (inDischarge.isEmpty() || ItemEnergy.capacity(inDischarge) > 0) {
			return;
		}
		for (int slot = getContainerSize() - 1; slot > DISCHARGE_SLOT; slot--) {
			setItem(slot, getItem(slot - 1));
		}
		setItem(DISCHARGE_SLOT, ItemStack.EMPTY);
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

	/**
	 * BatteryBox takes part in the storage→storage cascade (MOD-314): "put down a second box to extend the
	 * bank" is its whole point, and boxes share one capacity, so balancing by fill fraction reads to the
	 * player as the two simply levelling out.
	 */
	@Override
	public boolean acceptsCascade() {
		return true;
	}

	// R-BRK-07: carry the buffered EU on the dropped item so a charged BatteryBox keeps its charge through
	// break -> place. The block's loot table copies STORED_ENERGY from this block entity onto the drop;
	// placement applies it back here. Machines do NOT override these, so they lose their buffer on break.
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
	 * Five-wide data — hides {@link MachineBlockEntity#DATA_COUNT} on purpose so
	 * {@code BatteryBoxBlockEntity.DATA_COUNT} always names <em>this</em> machine's width, for the block
	 * entity below and for {@code BatteryBoxMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = 5;

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
			return DATA_COUNT;
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
