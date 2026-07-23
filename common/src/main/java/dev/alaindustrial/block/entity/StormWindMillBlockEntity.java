package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.WindMillClearance;
import dev.alaindustrial.core.environment.WindMillInterference;
import dev.alaindustrial.core.environment.WindMillOutput;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storm wind mill (T2, LV) — the weather-focused evolution of {@link WindMillBlockEntity}.
 * Same height step as T1 (step 16) but a higher base cap ({@link Config#stormWindMillMaxBaseEuPerTick} = 6) and stronger
 * weather multipliers ({@link Config#stormWindMillRainFactor} = 2.0,
 * {@link Config#stormWindMillThunderFactor} = 3.0) and a higher cap
 * ({@link Config#stormWindMillMaxEuPerTick} = 16). In clear weather it barely beats T1; in a
 * thunderstorm it is the strongest LV wind option — a gamble on the weather.
 *
 * <p>No inventory, no evolution (it is a leaf tier). A read-only energy GUI like the T2 solar panels.
 */
public class StormWindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 32;
	public static final int ROTOR_SLOT = 0;

	/** Transient sampling state — recomputed from the world, never serialised. */
	private int sampleCounter = 0;
	private int cachedRate = 0;
	private int cachedMode = WindMillBlockEntity.MODE_NO_ROTOR;

	public StormWindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.STORM_WIND_MILL_BE.get(), pos, state, EnergyTier.LV, 1, Config.t2WindMillBuffer, MAX_EXTRACT);
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

	private int sampleMode(Level level, BlockPos pos, int rate, boolean obstructed, boolean interfered) {
		if (!openSky(level, pos)) {
			return WindMillBlockEntity.MODE_ROOFED;
		}
		if (obstructed) {
			return WindMillBlockEntity.MODE_OBSTRUCTED;
		}
		if (interfered) {
			return WindMillBlockEntity.MODE_INTERFERENCE;
		}
		if (level.isThundering() && rate > 0) {
			return WindMillBlockEntity.MODE_STORM;
		}
		if (level.isRaining() && rate > 0) {
			return WindMillBlockEntity.MODE_GALE;
		}
		if (rate <= 0) {
			return WindMillBlockEntity.MODE_CALM;
		}
		return WindMillBlockEntity.MODE_BREEZE;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		ItemStack rotor = items.get(ROTOR_SLOT);
		if (rotor.isEmpty()) {
			// Force a client sync when the mill drops to no-rotor so watchers see production fall to 0:
			// the rotor renderer (blade visibility) and the hum sound loop (MOD-143) both read the synced
			// production channel, NOT the inventory. Without this the T2 mill kept humming after the rotor
			// was pulled — the channel stayed at its last non-zero value client-side. Mirrors WindMillBlockEntity.
			if (this.progress != 0 || this.maxProgress != WindMillBlockEntity.MODE_NO_ROTOR) {
				syncBlockEntityToClient();
			}
			cachedRate = 0;
			cachedMode = WindMillBlockEntity.MODE_NO_ROTOR;
			sampleCounter = 0;
			this.progress = 0;
			this.maxProgress = WindMillBlockEntity.MODE_NO_ROTOR;
			return 0;
		}
		if (sampleCounter % Config.windMillSampleTicks == 0) {
			// Blade clearance: a solid block in the rotor disc stalls the blades (rate 0), regardless
			// of height or weather. Only meaningful under open sky — a roof above is already fatal.
			Direction facing = state.hasProperty(HorizontalMachineBlock.FACING)
					? state.getValue(HorizontalMachineBlock.FACING)
					: Direction.NORTH;
			boolean sky = openSky(level, pos);
			boolean obstructed = sky && WindMillClearance.hasObstruction(level, pos, facing);
			// Rotor interference (MOD-051): a neighbouring mill's rotor disc overlapping ours stalls
			// both mills. Only checked when the blades could otherwise turn — ROOFED/OBSTRUCTED mask it.
			boolean interfered = sky && !obstructed && WindMillInterference.hasInterference(level, pos, facing);
			int previousRate = cachedRate;
			int previousMode = cachedMode;
			cachedRate = obstructed || interfered ? 0 : sampleRate(level, pos);
			cachedMode = sampleMode(level, pos, cachedRate, obstructed, interfered);
			// Mirror the T1 mill: push rate/mode changes to watching clients so the rotor renderer
			// reacts (spin speed, and hiding the blades on MODE_INTERFERENCE) without a GUI open.
			if (cachedRate != previousRate || cachedMode != previousMode) {
				syncBlockEntityToClient();
			}
		}
		sampleCounter++;
		this.progress = cachedRate;
		this.maxProgress = cachedMode;
		// Rotor wear (MOD-189): same wear path as the T1 mill — proportional to output (so a thunderstorm's
		// high output wears the rotor fast) with the shared storm-weather stress multiplier on top.
		if (cachedRate > 0) {
			float weather = (level.isThundering() || level.isRaining()) ? Config.windMillStormWearFactor : 1.0f;
			wearComponent(level, pos, ROTOR_SLOT, cachedRate, weather, Config.windMillRotorEuPerDamage);
		}
		return cachedRate;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == ROTOR_SLOT && stack.is(ModContent.WINDMILL_ROTOR.get());
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
