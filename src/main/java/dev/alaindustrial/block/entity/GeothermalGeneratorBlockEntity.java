package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV geothermal generator: burns lava into EU. Two feeds, one burn model:
 * <ul>
 *   <li>a {@link #fluidTank} (10 buckets) exposed via {@code FluidStorage.SIDED} so adjacent blocks
 *       (e.g. a {@link PumpBlockEntity}) can insert lava directly; and</li>
 *   <li>the legacy lava-bucket item slot, kept as a compatibility filler.</li>
 * </ul>
 * Each tick, when {@code lavaTicks} is low and the tank holds ≥1 bucket, it drains 1 bucket from the
 * tank and adds {@link Config#geothermalBurnTicks} burn ticks. Produces {@link Config#geothermalEuPerTick}
 * EU/t while lava remains; buffer 4000, LV output (32). Built on {@link AbstractGeneratorBlockEntity};
 * {@code progress}/{@code maxProgress} expose the lava-tick level.
 */
public class GeothermalGeneratorBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;

	/** Fluid tank capacity: 10 buckets, in droplets. */
	public static final long TANK_CAPACITY = FluidConstants.BUCKET * 10;

	private int lavaTicks;

	/** Lava-only tank, insertable from any side via {@code FluidStorage.SIDED}. */
	public final SingleVariantStorage<FluidVariant> fluidTank = new SingleVariantStorage<>() {
		@Override
		protected FluidVariant getBlankVariant() {
			return FluidVariant.blank();
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return TANK_CAPACITY;
		}

		@Override
		protected boolean canInsert(FluidVariant variant) {
			return variant.isOf(Fluids.LAVA);
		}

		@Override
		protected boolean canExtract(FluidVariant variant) {
			// The generator consumes its own lava internally; do not let neighbours pull it back out.
			return false;
		}

		@Override
		protected void onFinalCommit() {
			setChanged();
		}
	};

	public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GEOTHERMAL_GENERATOR, pos, state, EnergyTier.LV, 2, Config.geothermalBuffer,
				EnergyTier.LV.maxVoltage());
		this.maxProgress = tankCapacity();
	}

	/** Tank holds ~10 buckets' worth of burn ticks (config-driven). */
	private static int tankCapacity() {
		return 10 * Config.geothermalBurnTicks;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		int tank = tankCapacity();
		// Load lava from a bucket into the burn buffer whenever there is room.
		// This is independent of the energy buffer: lavaTicks is an intermediate store, not EU.
		// EU generation is gated separately below so lava is never wasted (R-NRG-11).
		if (lavaTicks + Config.geothermalBurnTicks <= tank
				&& items.get(INPUT_SLOT).is(Items.LAVA_BUCKET) && canReturnBucket()) {
			items.get(INPUT_SLOT).shrink(1);
			returnBucket();
			lavaTicks += Config.geothermalBurnTicks;
		}

		// Drain lava from the fluid tank into the burn buffer when there is room.
		// This is the generator consuming its OWN fuel, so it bypasses the tank's canExtract guard
		// (which is false to stop neighbours pulling lava back out) by mutating the tank directly.
		// produce() runs outside any Transaction, so a direct field update is the correct internal path.
		if (lavaTicks + Config.geothermalBurnTicks <= tank
				&& fluidTank.amount >= FluidConstants.BUCKET
				&& fluidTank.variant.isOf(Fluids.LAVA)) {
			fluidTank.amount -= FluidConstants.BUCKET;
			if (fluidTank.amount == 0) {
				fluidTank.variant = FluidVariant.blank();
			}
			lavaTicks += Config.geothermalBurnTicks;
			setChanged();
		}

		// Convert lavaTicks → EU only when the energy buffer has room (R-NRG-11, TC-GEO-001-NEG04).
		// Lava is already in lavaTicks, so pausing here wastes nothing.
		int made = 0;
		if (lavaTicks > 0 && energy.amount < energy.getCapacity()) {
			lavaTicks--;
			made = Config.geothermalEuPerTick;
		}
		this.progress = lavaTicks;
		this.maxProgress = tank;
		return made;
	}

	private boolean canReturnBucket() {
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty() || (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize());
	}

	private void returnBucket() {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, new ItemStack(Items.BUCKET));
		} else {
			out.grow(1);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT && stack.is(Items.LAVA_BUCKET);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.geothermal_generator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GeothermalGeneratorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("LavaTicks", lavaTicks);
		// The tank only ever holds lava, so persist the amount alone (variant is implicit).
		output.putLong("FluidTank", fluidTank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lavaTicks = input.getIntOr("LavaTicks", 0);
		long amount = input.getLongOr("FluidTank", 0L);
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		fluidTank.variant = amount > 0 ? FluidVariant.of(Fluids.LAVA) : FluidVariant.blank();
	}
}
