package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.WindMillClearance;
import dev.alaindustrial.core.environment.WindMillInterference;
import dev.alaindustrial.core.environment.WindMillOutput;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-altitude wind mill (T2, LV) — the height-focused evolution of {@link WindMillBlockEntity}.
 * Uses the same open-sky/height/weather model but gains base twice as fast
 * ({@link GeneratorConfig#highAltWindMillBlocksPerBase} = 8 vs the T1's 16) and caps higher
 * ({@link GeneratorConfig#highAltWindMillMaxBaseEuPerTick} = 8, {@link GeneratorConfig#highAltWindMillMaxEuPerTick} =
 * 16).
 * Rewards building tall: a mill on a high tower clearly outperforms T1, but at low altitude the
 * advantage vanishes (the zero-base gate still applies).
 *
 * <p>No inventory, no evolution (it is a leaf tier). A read-only energy GUI like the T2 solar panels.
 */
public class HighAltitudeWindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 32;
	public static final int ROTOR_SLOT = 0;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 1;

	/** Transient sampling state — recomputed from the world, never serialised. */
	private int sampleCounter = 0;
	private int cachedRate = 0;
	private int cachedMode = WindMillBlockEntity.MODE_NO_ROTOR;

	/**
	 * Effective EU/t for the GUI readout (MOD-356): {@link #cachedRate} after
	 * {@link Config#globalEuRateMultiplier}. Kept off channel 2, which stays the mechanical rate because
	 * the rotor renderer derives the blades' spin speed from it — see {@link WindMillBlockEntity}'s data
	 * javadoc for the full reasoning. Transient, never serialised, never read off a client block entity.
	 */
	private int effectiveRate = 0;

	public HighAltitudeWindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.HIGH_ALTITUDE_WIND_MILL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.t2WindMillBuffer, MAX_EXTRACT);
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

	private int sampleRate(Level level, BlockPos pos, float rotorFactor) {
		return WindMillOutput.euFor(pos.getY(), level.getSeaLevel(), openSky(level, pos),
				level.isRaining(), level.isThundering(),
				GeneratorConfig.highAltWindMillMaxBaseEuPerTick, GeneratorConfig.highAltWindMillBlocksPerBase,
				GeneratorConfig.highAltWindMillMaxEuPerTick,
				// Own weather factors since MOD-345 — this branch trades storm burst for a steady income.
				GeneratorConfig.highAltWindMillRainFactor, GeneratorConfig.highAltWindMillThunderFactor,
				GeneratorConfig.windCloudY, GeneratorConfig.windDeadY, GeneratorConfig.windRidgeFactor,
				GeneratorConfig.windTraceFactor,
				// Rotor grade (MOD-385) — folded in before euFor's cap, so it never lifts the ceiling.
				rotorFactor);
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
		// Rotor grade (MOD-385): read from the slot each tick so a swap takes effect at the next sample.
		ComponentTier rotorTier = tierOf(rotor, ComponentTier.WINDMILL_ROTOR);
		if (sampleCounter % GeneratorConfig.windMillSampleTicks == 0) {
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
			cachedRate = obstructed || interfered ? 0 : sampleRate(level, pos, rotorTier.outputMultiplier());
			cachedMode = sampleMode(level, pos, cachedRate, obstructed, interfered);
			// Push rate/mode changes to watching clients: the rotor renderer reads both off the BE
			// (spin speed + interference blade-hiding), so it cannot rely on an open menu to sync.
			if (cachedRate != previousRate || cachedMode != previousMode) {
				syncBlockEntityToClient();
			}
		}
		sampleCounter++;
		this.progress = cachedRate;
		this.maxProgress = cachedMode;
		// Rotor wear (MOD-189): same wear path as the T1 mill — proportional to output (so a tall,
		// high-output tower wears its rotor faster) with the shared storm-weather stress multiplier.
		if (cachedRate > 0) {
			float weather = (level.isThundering() || level.isRaining()) ? GeneratorConfig.windMillStormWearFactor
					: 1.0f;
			// Grade-specific EU-per-damage (MOD-385): cachedRate already carries the grade's multiplier.
			wearComponent(level, pos, ROTOR_SLOT, cachedRate, weather, rotorTier.euPerDamage());
		}
		return cachedRate;
	}

	/**
	 * The readout rides {@link Channel#RATE}, not channel 2: channel 2 stays the mechanical rate because
	 * the rotor renderer turns it into the blades' angular speed (MOD-356).
	 */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.effectiveRate = effectiveEuPerTick;
	}

	/**
	 * GUI sync channels (MOD-712, BE-7): the base four (PROGRESS the mechanical rate, MAX_PROGRESS the
	 * mode), then the effective generation rate, read-only — see {@link WindMillBlockEntity.Channel}.
	 */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, RATE }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.RATE, () -> effectiveRate)
				.build();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// MOD-385: accepts every rotor grade via the shared tag (see ModTags.Items.WINDMILL_ROTORS).
		return slot == ROTOR_SLOT && stack.is(ModTags.Items.WINDMILL_ROTORS);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new HighAltitudeWindMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
