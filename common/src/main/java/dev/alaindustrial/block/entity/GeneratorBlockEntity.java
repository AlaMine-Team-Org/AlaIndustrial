package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.skill.SkillMachine;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.GeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV fuel generator: burns a furnace fuel item into EU. Built on {@link AbstractGeneratorBlockEntity};
 * {@code progress}/{@code maxProgress} expose the current burn for the GUI.
 */
public class GeneratorBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	public static final int FUEL_SLOT = 0;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 1;

	private int burnTime;
	private int burnDuration;

	public GeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.GENERATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, Config.generatorBuffer,
				EnergyTier.LV.maxVoltage());
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		int made = 0;
		boolean room = energy.getAmount() < energy.getCapacity();
		// Burn only while there is room to store the EU — pause when the buffer is full so fuel is
		// not wasted with no consumer drawing.
		if (burnTime > 0 && room) {
			burnTime--;
			made = Config.fuelEuPerTick;
		}
		if (burnTime <= 0 && room) {
			ItemStack fuel = items.get(FUEL_SLOT);
			// MOD-498 — FuelValues#burnDuration(ItemStack) is deprecated by NeoForge only; vanilla does not
			// deprecate it. The replacement NeoForge names, ItemStack#getBurnTime, is a NeoForge addition
			// absent from vanilla, and this class is compiled for Fabric too, so it must use the vanilla form.
			@SuppressWarnings("deprecation")
			int duration = level.fuelValues().burnDuration(fuel);
			if (duration > 0) {
				// MOD-483 Steady Hands: fuel lasts longer. Applied to the burn LENGTH rather than to
				// EU/t, because a solar panel makes 1 EU/t and a percentage of one is zero.
				duration = SkillMachine.burnDuration(duration, level, getOwner());
				burnTime = duration;
				burnDuration = duration;
				// Return the fuel's crafting remainder (e.g. empty bucket from a lava bucket).
				// getCraftingRemainder() is null for fuels with no remainder (coal, planks, ...).
				// MOD-498 — Item#getCraftingRemainder() is deprecated by NeoForge only, in favour of its own
				// getCraftingRemainder(ItemStack) overload, which vanilla does not declare. This class is
				// compiled for Fabric too, where the no-arg form is the only one that exists.
				@SuppressWarnings("deprecation")
				net.minecraft.world.item.ItemStackTemplate rem = fuel.getItem().getCraftingRemainder();
				ItemStack remainder = rem == null ? ItemStack.EMPTY : rem.create();
				fuel.shrink(1);
				if (items.get(FUEL_SLOT).isEmpty() && !remainder.isEmpty()) {
					items.set(FUEL_SLOT, remainder);
				}
			}
		}
		this.maxProgress = burnDuration;
		this.progress = burnTime;
		return made;
	}

	// MOD-498 — FuelValues#burnDuration(ItemStack) is deprecated by NeoForge only (vanilla does not
	// deprecate it); its replacement, ItemStack#getBurnTime, is a NeoForge extension that does not exist
	// in vanilla, and this class is shared code compiled for Fabric as well.
	@SuppressWarnings("deprecation")
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot != FUEL_SLOT) {
			return false;
		}
		// Lava is handled by the dedicated geothermal generator (a lava bucket here would out-produce
		// it ~10×); the fuel generator is solid/furnace fuel only.
		if (stack.is(net.minecraft.world.item.Items.LAVA_BUCKET)) {
			return false;
		}
		// Reject non-fuel (R-GUI-02). Burn values are per-level; level can be null on the client /
		// before placement — stay permissive there (the server re-validates).
		Level level = getLevel();
		return level == null || level.fuelValues().burnDuration(stack) > 0;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.generator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new GeneratorMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BurnTime", burnTime);
		output.putInt("BurnDuration", burnDuration);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		burnTime = input.getIntOr("BurnTime", 0);
		burnDuration = input.getIntOr("BurnDuration", 0);
	}
}
