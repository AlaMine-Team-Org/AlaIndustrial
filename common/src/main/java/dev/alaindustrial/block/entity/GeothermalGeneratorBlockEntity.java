package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.core.FluidPortHost;
import dev.alaindustrial.core.FluidTank;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 *   <li>a {@link #fluidTank} (10 buckets) exposed via a neutral {@link FluidPort} so adjacent blocks
 *       (e.g. a {@link PumpBlockEntity}) can insert lava directly; and</li>
 *   <li>the legacy lava-bucket item slot, kept as a compatibility filler.</li>
 * </ul>
 * Each tick, when {@code lavaTicks} is low and the tank holds ≥1 bucket, it drains 1 bucket from the
 * tank and adds {@link Config#geothermalBurnTicks} burn ticks. Produces {@link Config#geothermalEuPerTick}
 * EU/t while lava remains; buffer 4000, LV output (32). Built on {@link AbstractGeneratorBlockEntity};
 * {@code progress}/{@code maxProgress} expose the lava-tick level.
 *
 * <p><b>MOD-028 multiloader migration.</b> Moved from {@code fabric} to {@code common}: the Fabric Fluid
 * Transfer API ({@code FluidVariant}/{@code SingleVariantStorage}) is replaced by the neutral
 * {@link FluidTank}/{@link FluidPort}, so this class no longer imports any loader-specific fluid type.
 * Each loader supplies its own adapter ({@code FabricFluidPort}/{@code NeoForgeFluidPort}) — see
 * {@link FluidPort} class doc.
 */
public class GeothermalGeneratorBlockEntity extends AbstractGeneratorBlockEntity
		implements MenuProvider, FluidPortHost {
	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;

	/** Fluid tank capacity: 10 buckets, in mB. */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	private int lavaTicks;

	/** Lava-only tank, insertable from any side, never extractable (R-CON-08 — the generator burns its own fuel). */
	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			fluid -> fluid.is(Fluids.LAVA),
			fluid -> false,
			this::setChanged);

	public GeothermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GEOTHERMAL_GENERATOR_BE.get(), pos, state, EnergyTier.LV, 2, Config.geothermalBuffer,
				EnergyTier.LV.maxVoltage());
		this.maxProgress = tankCapacity();
	}

	/** Tank holds ~10 buckets' worth of burn ticks (config-driven). */
	private static int tankCapacity() {
		return 10 * Config.geothermalBurnTicks;
	}

	/** Every face exposes the same single tank — the generator has no per-face fluid restriction. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		int tank = tankCapacity();
		// Load lava from a filler item (vanilla lava bucket OR a lava-filled Vacuum Capsule) into the burn
		// buffer whenever there is room, returning the emptied container to the output slot. This is
		// independent of the energy buffer: lavaTicks is an intermediate store, not EU. EU generation is
		// gated separately below so lava is never wasted (R-NRG-11). MOD-077: capsule parity — same
		// all-or-nothing exchange as the bucket, returning an empty capsule instead of an empty bucket.
		if (lavaTicks + Config.geothermalBurnTicks <= tank) {
			ItemStack input = items.get(INPUT_SLOT);
			ItemStack container = fillerContainer(input);
			if (!container.isEmpty() && canReturn(container)) {
				input.shrink(1);
				putReturn(container);
				lavaTicks += Config.geothermalBurnTicks;
			}
		}

		// Drain lava from the fluid tank into the burn buffer when there is room.
		// This is the generator consuming its OWN fuel, so it bypasses the tank's canExtract guard
		// (which is false to stop neighbours pulling lava back out) by mutating the tank directly.
		// produce() runs outside any transaction, so a direct field update is the correct internal path.
		if (lavaTicks + Config.geothermalBurnTicks <= tank
				&& fluidTank.amount >= FluidAmounts.BUCKET
				&& fluidTank.fluid.is(Fluids.LAVA)) {
			fluidTank.amount -= FluidAmounts.BUCKET;
			if (fluidTank.amount == 0) {
				fluidTank.fluid = FluidHolder.EMPTY;
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

	/**
	 * The empty container an accepted lava filler leaves behind — a single empty bucket for a
	 * {@link Items#LAVA_BUCKET}, a single empty {@link ModContent#VACUUM_CAPSULE} for a lava-filled
	 * capsule — or {@link ItemStack#EMPTY} if {@code input} is not a lava filler this machine accepts.
	 */
	private static ItemStack fillerContainer(ItemStack input) {
		if (input.is(Items.LAVA_BUCKET)) {
			return new ItemStack(Items.BUCKET);
		}
		if (dev.alaindustrial.item.CapsuleFuel.isLavaCapsule(input)) {
			return new ItemStack(ModContent.VACUUM_CAPSULE.get());
		}
		return ItemStack.EMPTY;
	}

	/** Whether the output slot can accept one more {@code returned} container (empty slot or a matching stack). */
	private boolean canReturn(ItemStack returned) {
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty()
				|| (ItemStack.isSameItemSameComponents(out, returned) && out.getCount() < out.getMaxStackSize());
	}

	/** Place one {@code returned} container into the output slot (must have passed {@link #canReturn}). */
	private void putReturn(ItemStack returned) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, returned);
		} else {
			out.grow(1);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT
				&& (stack.is(Items.LAVA_BUCKET) || dev.alaindustrial.item.CapsuleFuel.isLavaCapsule(stack));
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
		// The tank only ever holds lava, so persist the amount alone (fluid identity is implicit).
		output.putLong("FluidTankMb", fluidTank.amount);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		lavaTicks = input.getIntOr("LavaTicks", 0);
		// MOD-028: prefer the new mB-valued key; fall back to the legacy Fabric v0.1.0 droplet-valued
		// "FluidTank" key, converting ÷81 (81000 droplets/bucket ÷ 81 = 1000 mB/bucket, exact — machine
		// transactions always move whole buckets, so legacy values are always bucket-multiples).
		long amount = input.getLong("FluidTankMb")
				.orElseGet(() -> input.getLong("FluidTank")
						.map(dr -> dr / FluidAmounts.FABRIC_DROPLETS_PER_MB).orElse(0L));
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		fluidTank.fluid = fluidTank.amount > 0 ? FluidHolder.of(Fluids.LAVA) : FluidHolder.EMPTY;
	}
}
