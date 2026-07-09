package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.SolarSky;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-altitude wind mill (T2, LV) — the height-focused evolution of {@link WindMillBlockEntity}.
 * Uses the same open-sky/height/weather model but gains base twice as fast
 * ({@link Config#highAltWindMillBlocksPerBase} = 8 vs the T1's 16) and caps higher
 * ({@link Config#highAltWindMillMaxBaseEuPerTick} = 8, {@link Config#highAltWindMillMaxEuPerTick} = 16).
 * Rewards building tall: a mill on a high tower clearly outperforms T1, but at low altitude the
 * advantage vanishes (the zero-base gate still applies).
 *
 * <p>No inventory, no evolution (it is a leaf tier). A read-only energy GUI like the T2 solar panels.
 */
public class HighAltitudeWindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 32;

	/** Transient sampling state — recomputed from the world, never serialised. */
	private int sampleCounter = 0;
	private int cachedRate = 0;

	public HighAltitudeWindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.HIGH_ALTITUDE_WIND_MILL_BE.get(), pos, state, EnergyTier.LV, 0, Config.t2WindMillBuffer, MAX_EXTRACT);
	}

	/**
	 * Mirrors the T1 wind mill's single back-face output: only the face opposite FACING emits EU;
	 * the front and the four sides stay inert. Keeps the energy-flow contract identical before and
	 * after evolution so a cable on the back keeps working.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (worldFace == facing.getOpposite()) {
			return EnergyRole.OUT;
		}
		return EnergyRole.NONE;
	}

	private static boolean openSky(Level level, BlockPos pos) {
		return level.dimension().equals(Level.OVERWORLD)
				&& SolarSky.classify(level, pos) == SolarSky.Access.CLEAR;
	}

	private int sampleRate(Level level, BlockPos pos) {
		return WindMillOutput.euFor(pos.getY(), level.getSeaLevel(), openSky(level, pos),
				level.isRaining(), level.isThundering(),
				Config.highAltWindMillMaxBaseEuPerTick, Config.highAltWindMillBlocksPerBase,
				Config.highAltWindMillMaxEuPerTick,
				Config.windMillRainFactor, Config.windMillThunderFactor);
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (sampleCounter % Config.windMillSampleTicks == 0) {
			cachedRate = sampleRate(level, pos);
		}
		sampleCounter++;
		this.progress = cachedRate;
		this.maxProgress = Config.highAltWindMillMaxEuPerTick;
		return cachedRate;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.high_altitude_wind_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new HighAltitudeWindMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
