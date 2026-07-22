package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.WaterMillInterference;
import dev.alaindustrial.core.environment.WaterMillOutput;
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

	/**
	 * Status carried by the {@code maxProgress} sync channel (slot 3) — the wheel renderer hides the
	 * wheel on {@link #MODE_INTERFERENCE} and the GUI shows the matching label. Kept minimal: the water
	 * mill has none of the wind mill's weather/altitude modes, only "running normally" vs "wheels clash".
	 */
	public static final int MODE_OK = 0;
	public static final int MODE_INTERFERENCE = 1;

	/** LV output packet cap (32 EU/t), shared with the other LV generators. */
	private static final int MAX_EXTRACT = 32;

	/**
	 * Ticks between wheel-interference scans. The 7×7×7 neighbour sweep is too heavy to run every tick,
	 * and interference only changes when a player places/breaks a nearby mill or wheel, so a coarse
	 * cadence is plenty; the cached result is reused in between.
	 */
	private static final int SCAN_INTERVAL = 20;

	private int scanCounter;
	private boolean cachedInterfered;

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
			// No wheel → nothing rendered, nothing to clash with. Force a rescan when a wheel returns.
			cachedInterfered = false;
			scanCounter = 0;
			setState(0, MODE_OK);
			return 0;
		}
		// Wheel interference (MOD-175): a neighbouring mill's wheel overlapping ours makes both look
		// broken (z-fighting) and stalls both. The 7×7×7 scan is too heavy per tick — sample it on a
		// cadence and cache between. Only meaningful once a wheel is installed (checked above).
		if (scanCounter % SCAN_INTERVAL == 0) {
			Direction facing = state.hasProperty(HorizontalMachineBlock.FACING)
					? state.getValue(HorizontalMachineBlock.FACING)
					: Direction.NORTH;
			cachedInterfered = WaterMillInterference.hasInterference(level, pos, facing);
		}
		scanCounter++;
		if (cachedInterfered) {
			// Wheel hidden by the renderer, generation halted — both interfering mills stall (symmetric).
			setState(0, MODE_INTERFERENCE);
			return 0;
		}
		int sides = waterSides(level, pos);
		int made = WaterMillOutput.euFor(sides, Config.waterMillEuPerTick);
		setState(sides, MODE_OK);
		return made;
	}

	/**
	 * Store the wheel's spin input ({@code progress} = water-face count, the value the wheel renderer
	 * reads via {@code dataAccess} slot 2) and the status {@code mode} (the {@code maxProgress} channel,
	 * slot 3), and push a block-entity update to watching clients whenever either changes.
	 *
	 * <p>The water mill has no {@code lit} blockstate (it is a passive generator), so the base generator's
	 * {@code updateLit} is a no-op and never syncs. Without this explicit sync the server would refresh
	 * these every tick but never notify the client, leaving the wheel renderer reading the stale
	 * chunk-load value (usually 0) — the wheel would freeze or stay visible during interference. Sync only
	 * on change, so a steady water setup does not spam block updates (mirrors the wind-mill rotor fix).
	 */
	private void setState(int sides, int mode) {
		if (this.progress != sides || this.maxProgress != mode) {
			this.progress = sides;
			this.maxProgress = mode;
			syncBlockEntityToClient();
		}
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
