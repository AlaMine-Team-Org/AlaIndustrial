package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.WaterMillOutput;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV water mill (spec: alaindustrial:water_mill) — a passive, fuel-free generator. Each tick it counts
 * vanilla water (source or flowing) on its four horizontal faces (N/S/E/W) and produces
 * {@link Config#waterMillEuPerTick} EU/t per water face: 0–4 EU/t, continuous. A crafted
 * {@code water_mill_wheel} must be installed in the single component slot; it is not consumed.
 * The remaining production calculation is a stateless world read. Buffer
 * {@link Config#waterMillBuffer}, LV output.
 *
 * <p>It never touches the fluid-tank/{@code FluidStorage} system; it reads {@code level.getFluidState}
 * directly. Energy persists via {@link MachineBlockEntity}; the water mill adds no NBT of its own.
 */
public class WaterMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	public static final int WHEEL_SLOT = 0;

	/** LV output packet cap (32 EU/t), shared with the other LV generators. */
	private static final int MAX_EXTRACT = 32;

	public WaterMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WATER_MILL_BE.get(), pos, state, EnergyTier.LV, 1, Config.waterMillBuffer, MAX_EXTRACT);
	}

	/** Count the four horizontal faces (N/S/E/W) whose neighbour is vanilla water (source or flowing). */
	private static int waterSides(Level level, BlockPos pos) {
		int sides = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
				sides++;
			}
		}
		return sides;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (items.get(WHEEL_SLOT).isEmpty()) {
			this.progress = 0;
			this.maxProgress = 4;
			return 0;
		}
		int sides = waterSides(level, pos);
		int made = WaterMillOutput.euFor(sides, Config.waterMillEuPerTick);
		// progress/maxProgress carry the water-face count for the energy GUI / debug readout.
		this.progress = sides;
		this.maxProgress = 4;
		return made;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == WHEEL_SLOT && stack.is(ModContent.WATER_MILL_WHEEL.get());
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.water_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new WaterMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
