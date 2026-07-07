package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.SolarSky;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV wind mill (spec: alaindustrial:wind_mill) — a passive, fuel-free generator driven by build height,
 * open sky and weather. Base grows with height ({@code (y − seaLevel) / 16}, 0–{@link Config#windMillMaxBaseEuPerTick}),
 * multiplied by weather (rain ×{@link Config#windMillRainFactor}, thunder ×{@link Config#windMillThunderFactor})
 * and capped at {@link Config#windMillMaxEuPerTick}. Requires the Overworld and an open sky column above
 * the block; roofed or below sea level → 0. No inventory; a read-only energy GUI (buffer/capacity).
 * Buffer {@link Config#windMillBuffer}, LV output.
 *
 * <p>Height/sky/weather are not sampled every tick: a transient tick counter recomputes the rate every
 * {@link Config#windMillSampleTicks} ticks (starting at 0, so the first tick samples immediately) and
 * {@link #produce} returns the cached rate in between. Neither the counter nor the cached rate is persisted —
 * both are recomputed from the world after a reload. Energy persists via {@link MachineBlockEntity}; the wind
 * mill adds no NBT of its own.
 *
 * <p>It reads the world directly ({@link SolarSky#classify}, {@code level.isRaining()/isThundering()},
 * {@code level.getSeaLevel()}) — the same sky/weather/sea-level APIs the solar panels use.
 */
public class WindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/** LV output packet cap (32 EU/t), shared with the other LV generators. */
	private static final int MAX_EXTRACT = 32;

	/** Transient sampling state — recomputed from the world, never serialised (see class doc). */
	private int sampleCounter = 0;
	private int cachedRate = 0;

	public WindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WIND_MILL_BE.get(), pos, state, EnergyTier.LV, 0, Config.windMillBuffer, MAX_EXTRACT);
	}

	/** Wind mills only work in the Overworld under an open (CLEAR) sky column. */
	private static boolean openSky(Level level, BlockPos pos) {
		return level.dimension().equals(Level.OVERWORLD)
				&& SolarSky.classify(level, pos) == SolarSky.Access.CLEAR;
	}

	/** Recompute the current EU/t rate from height, open sky and weather. */
	private int sampleRate(Level level, BlockPos pos) {
		return WindMillOutput.euFor(pos.getY(), level.getSeaLevel(), openSky(level, pos),
				level.isRaining(), level.isThundering(),
				Config.windMillMaxBaseEuPerTick, Config.windMillMaxEuPerTick,
				Config.windMillRainFactor, Config.windMillThunderFactor);
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		// Sample on tick 0, then every windMillSampleTicks; return the cached rate in between.
		if (sampleCounter % Config.windMillSampleTicks == 0) {
			cachedRate = sampleRate(level, pos);
		}
		sampleCounter++;
		// progress/maxProgress carry the current rate for the energy GUI / debug readout.
		this.progress = cachedRate;
		this.maxProgress = Config.windMillMaxEuPerTick;
		return cachedRate;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.wind_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new WindMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
