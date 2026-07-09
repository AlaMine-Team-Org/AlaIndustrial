package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.SolarSky;
import dev.alaindustrial.core.WindMillOutput;
import dev.alaindustrial.menu.WindMillMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV wind mill (spec: alaindustrial:wind_mill) — a generator driven by build height, open sky and
 * weather. Base grows with height ({@code (y − seaLevel) / 16}, 0–{@link Config#windMillMaxBaseEuPerTick}),
 * multiplied by weather (rain ×{@link Config#windMillRainFactor}, thunder ×{@link Config#windMillThunderFactor})
 * and capped at {@link Config#windMillMaxEuPerTick}. Requires the Overworld and an open sky column above
 * the block; roofed or below sea level → 0.
 *
 * <p><b>Rotor gate.</b> A {@code windmill_rotor} must be installed in {@link #ROTOR_SLOT} or the mill
 * produces nothing — the rotor is a progression gate (and a hook for future wear). It does not decay.
 *
 * <p><b>Evolution.</b> The wind mill shares the solar-panel evolution chips: an
 * {@linkplain ModContent#ALIGNMENT_CHIP_DAY day chip} evolves it into the high-altitude branch, a
 * {@linkplain ModContent#ALIGNMENT_CHIP_NIGHT night chip} into the storm branch. With a chip in
 * {@link #CHIP_SLOT} the mill accumulates active open-sky ticks (rotor installed, clear sky above);
 * once {@link Config#windMillEvolveTicks} is reached it transforms, carrying its stored energy and
 * consuming the chip.
 *
 * <p>Height/sky/weather are not sampled every tick: a transient tick counter recomputes the rate every
 * {@link Config#windMillSampleTicks} ticks and {@link #produce} returns the cached rate in between.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 production (EU/t), 3 mode, 4 evolution progress
 * (permille 0..1000), 5 denominator (constant 1000). Permille avoids the 16-bit DataSlot overflow
 * that {@code windMillEvolveTicks} = 33 600 would otherwise cause (mirrors SolarPanelBlockEntity).
 */
public class WindMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	/** Slot indices shared with the menu. */
	public static final int ROTOR_SLOT = 0;
	public static final int CHIP_SLOT = 1;

	private static final int MAX_EXTRACT = 32;

	/** Mode codes shared with the screen. */
	public static final int MODE_NO_ROTOR = 0;
	public static final int MODE_ROOFED = 1;
	public static final int MODE_CALM = 2;
	public static final int MODE_BREEZE = 3;
	public static final int MODE_GALE = 4;
	public static final int MODE_STORM = 5;

	/** Transient sampling state — recomputed from the world, never serialised. */
	private int sampleCounter = 0;
	private int cachedRate = 0;
	private int cachedMode = MODE_NO_ROTOR;

	private int evolveProgress;

	public WindMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WIND_MILL_BE.get(), pos, state, EnergyTier.LV, 2, Config.windMillBuffer, MAX_EXTRACT);
	}

	/**
	 * Unlike the default generator (5-face output), the wind mill emits EU only from its back face
	 * (opposite of FACING) — mirroring the BatteryBox single-axis layout but output-only. The front
	 * (FACING) is the rotor/working face and stays inert; the four sides are also inert so a cable
	 * must connect to the back to draw power. This makes the energy flow directional and readable.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (worldFace == facing.getOpposite()) {
			return EnergyRole.OUT;
		}
		return EnergyRole.NONE;
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
		// Rotor gate: no rotor → no generation.
		ItemStack rotor = items.get(ROTOR_SLOT);
		if (rotor.isEmpty()) {
			if (this.progress != 0 || this.maxProgress != MODE_NO_ROTOR) {
				syncBlockEntityToClient();
			}
			this.progress = 0;
			this.maxProgress = MODE_NO_ROTOR;
			// Invalidate the sampling cache so a rotor re-install forces an immediate resample +
			// sync on the next tick (sampleCounter % sampleTicks == 0 at 0). Otherwise the cached
			// rate survives the empty window and, being unchanged, skips the sync — the client keeps
			// production at 0 and the blades freeze even though the rotor is back in place.
			this.sampleCounter = 0;
			this.cachedRate = 0;
			this.cachedMode = MODE_NO_ROTOR;
			return 0;
		}

		boolean sky = openSky(level, pos);

		// --- Evolution: advance while a chip sees open sky (rotor already installed). ---
		// The wind mill shares the solar-panel evolution chips: day → high-altitude, night → storm.
		// One chip family covers both generator families so a player's chip investment transfers.
		ItemStack chip = items.get(CHIP_SLOT);
		boolean dayChip = chip.is(ModContent.ALIGNMENT_CHIP_DAY.get());
		boolean nightChip = chip.is(ModContent.ALIGNMENT_CHIP_NIGHT.get());
		if ((dayChip || nightChip) && sky) {
			evolveProgress++;
			if (evolveProgress >= Config.windMillEvolveTicks) {
				evolveInto(level, pos, dayChip ? ModContent.HIGH_ALTITUDE_WIND_MILL.get() : ModContent.STORM_WIND_MILL.get());
				return 0; // this block entity is gone after the transform
			}
			setChanged();
		}

		// --- Production: sample on tick 0, then every windMillSampleTicks; cache in between. ---
		if (sampleCounter % Config.windMillSampleTicks == 0) {
			int previousRate = cachedRate;
			int previousMode = cachedMode;
			cachedRate = sampleRate(level, pos);
			cachedMode = modeFor(sky, cachedRate, level.isRaining(), level.isThundering());
			if (cachedRate != previousRate || cachedMode != previousMode) {
				syncBlockEntityToClient();
			}
		}
		sampleCounter++;
		this.progress = cachedRate;
		this.maxProgress = cachedMode;
		return cachedRate;
	}

	/** Map the current sky/rate/weather state to a GUI mode code. */
	private static int modeFor(boolean sky, int rate, boolean raining, boolean thundering) {
		if (!sky) {
			return MODE_ROOFED;
		}
		if (thundering) {
			return MODE_STORM;
		}
		if (raining) {
			return MODE_GALE;
		}
		if (rate <= 0) {
			return MODE_CALM;
		}
		return MODE_BREEZE;
	}

	/** Replace this mill with its evolved branch, carrying stored energy; the chip is consumed. */
	private void evolveInto(Level level, BlockPos pos, Block target) {
		long saved = energy.amount;
		items.set(CHIP_SLOT, ItemStack.EMPTY);
		BlockState oldState = getBlockState();
		BlockState newState = target.defaultBlockState();
		if (oldState.hasProperty(HorizontalMachineBlock.FACING)
				&& newState.hasProperty(HorizontalMachineBlock.FACING)) {
			newState = newState.setValue(HorizontalMachineBlock.FACING, oldState.getValue(HorizontalMachineBlock.FACING));
		}
		level.setBlockAndUpdate(pos, newState);
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity evolved) {
			evolved.getEnergyStorage().amount = Math.min(saved, evolved.getEnergyStorage().getCapacity());
			evolved.setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case ROTOR_SLOT -> stack.is(ModContent.WINDMILL_ROTOR.get());
			case CHIP_SLOT -> stack.is(ModContent.ALIGNMENT_CHIP_DAY.get())
					|| stack.is(ModContent.ALIGNMENT_CHIP_NIGHT.get());
			default -> false;
		};
	}

	/**
	 * Six-wide data: base 0..3 plus evolution progress (4) and denominator (5), mirroring
	 * SolarPanelBlockEntity. The evolution channels are scaled to <b>permille (0..1000)</b> to stay
	 * 16-bit-DataSlot-safe.
	 */
	private final ContainerData windMillData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> evolveProgress <= 0 ? 0
						: Math.max(1, (int) Math.min((long) evolveProgress * 1000 / Config.windMillEvolveTicks, 1000));
				case 5 -> 1000;
				default -> WindMillBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != 5) {
				WindMillBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 6;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return windMillData;
	}

	/** Raw accumulated evolution counter in ticks (0..{@link Config#windMillEvolveTicks}), persisted in NBT. */
	public int getEvolveProgressTicks() {
		return evolveProgress;
	}

	/** Seed the raw evolution counter directly (tick scale). Test/setup helper. */
	public void setEvolveProgressTicks(int ticks) {
		this.evolveProgress = ticks;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.wind_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new WindMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("EvolveProgress", evolveProgress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		evolveProgress = input.getIntOr("EvolveProgress", 0);
	}
}
