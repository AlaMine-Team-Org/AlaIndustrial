package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.SolarSky;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.menu.StormWindMillMenu;
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
 * Storm wind mill (T2, LV) — the weather-focused evolution of {@link WindMillBlockEntity}.
 * Same base shape as T1 ({@link Config#stormWindMillMaxBaseEuPerTick} = 4, step 16) but stronger
 * weather multipliers ({@link Config#stormWindMillRainFactor} = 2.0,
 * {@link Config#stormWindMillThunderFactor} = 3.0) and a higher cap
 * ({@link Config#stormWindMillMaxEuPerTick} = 16). In clear weather it barely beats T1; in a
 * thunderstorm it is the strongest LV wind option — a gamble on the weather.
 *
 * <p>No inventory, no evolution (it is a leaf tier). A read-only energy GUI like the T2 solar panels.
 */
public class StormWindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 32;

	/** Transient sampling state — recomputed from the world, never serialised. */
	private int sampleCounter = 0;
	private int cachedRate = 0;

	public StormWindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.STORM_WIND_MILL_BE.get(), pos, state, EnergyTier.LV, 0, Config.t2WindMillBuffer, MAX_EXTRACT);
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
				Config.stormWindMillMaxBaseEuPerTick, Config.stormWindMillMaxEuPerTick,
				Config.stormWindMillRainFactor, Config.stormWindMillThunderFactor);
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (sampleCounter % Config.windMillSampleTicks == 0) {
			cachedRate = sampleRate(level, pos);
		}
		sampleCounter++;
		this.progress = cachedRate;
		this.maxProgress = Config.stormWindMillMaxEuPerTick;
		return cachedRate;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.storm_wind_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new StormWindMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
