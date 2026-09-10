package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.SolarSky;
import dev.alaindustrial.core.environment.SolarSkyCache;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
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
 * LV Daylight Solar Panel — the day branch's second rung. Day, under open sky, in the Overworld:
 * {@link Config#daylightEuPerTick}; rain/thunder: 0, snow: a floored trickle (MOD-003). Buffer
 * {@link Config#solarBuffer}, LV output.
 *
 * <p>Evolution (MOD-602): with a {@linkplain ModContent#RESONANCE_CHIP resonance chip} in its slot,
 * the panel banks active day-time and, at {@link Config#solarEvolveTicks}, becomes the Mirror
 * Concentrator — carrying its stored energy and consuming one chip. The same chip serves the night
 * branch: by this rung the panel already knows which branch it is, so a second line of crafting
 * would buy the player nothing.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 production (EU/t), 3 mode, 4 evolution progress
 * (permille 0..1000), 5 denominator (constant 1000). Permille, not raw ticks, for the reason
 * {@link SolarPanelBlockEntity} spells out: a {@code DataSlot} is a 16-bit short and the raw counter
 * overflows it.
 */
public class DaylightSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 20;
	public static final int CHIP_SLOT = 0;
	/** One evolution-chip slot (MOD-602); the client menu stub sizes its container from this. */
	public static final int SLOT_COUNT = 1;

	/** Caches the sky/weather verdict for {@link Config#solarSkySampleTicks} ticks to avoid a per-tick column scan. */
	private final SolarSkyCache skyCache = new SolarSkyCache();

	/** Mode codes shared with the screen. */
	public static final int MODE_NIGHT = 0;
	public static final int MODE_DAY = 1;
	public static final int MODE_DAY_WEATHER = 2;
	public static final int MODE_DAY_PARTIAL = 3;
	public static final int MODE_DAY_SNOW = 4;

	private int evolveProgress;
	/** Which chip the counter above belongs to; see {@link MachineBlockEntity#saveEvolveChip}. */
	private int evolveChip = EVOLVE_CHIP_NONE;

	public DaylightSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.DAYLIGHT_SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, Config.solarBuffer, MAX_EXTRACT);
	}

	/**
	 * Top face is the working surface (captures sunlight) and must not emit EU; the other five sides are
	 * standard generator outputs (R-NRG-03) — mirrors {@link SolarPanelBlockEntity}. Without this override
	 * the evolved panel inherited all-faces-OUT and leaked power from its working surface (spec: 5-side).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		// Sample sky access + weather on a cadence (Config.solarSkySampleTicks); cached to avoid the
		// per-tick column scan above the panel.
		skyCache.sample(level, pos);
		SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? skyCache.sky()
				: SolarSky.Access.BLOCKED;
		boolean overworldSky = sky != SolarSky.Access.BLOCKED;
		boolean bright = level.isBrightOutside();

		// --- Evolution: bank day-time while the resonance chip is in the slot. ---
		ItemStack chip = items.get(CHIP_SLOT);
		boolean resonanceChip = chip.is(ModContent.RESONANCE_CHIP.get());
		// The counter belongs to the chip that earned it (MOD-601): pull the chip out and that run is
		// abandoned rather than banked for the next one.
		int chipNow = evolveChipOf(chip);
		if (chipNow != evolveChip) {
			// An UNATTRIBUTED counter is adopted, not cleared — that is the state of a save written
			// before the marker existed, and of a counter seeded by a test rig.
			if (evolveChip != EVOLVE_CHIP_NONE) {
				evolveProgress = 0;
			}
			evolveChip = chipNow;
			setChanged();
		}
		if (resonanceChip && overworldSky && bright) {
			evolveProgress++;
			if (evolveProgress >= Config.solarEvolveTicks) {
				evolveInto(level, pos, ModContent.RADIANT_SOLAR_PANEL.get());
				return 0; // this block entity is gone after the transform
			}
			setChanged();
		}

		// --- Production. Mode priority: WEATHER > SNOW > PARTIAL > DAY (night = 0). ---
		int production = 0;
		int mode = MODE_NIGHT;
		if (overworldSky && bright) {
			production = Config.daylightEuPerTick;
			mode = MODE_DAY;
			switch (skyCache.weather()) {
				case RAIN -> {
					// Rain/thunder blocks direct sunlight: no generation at all (MOD-003). Mode flag kept for GUI.
					production = 0;
					mode = MODE_DAY_WEATHER;
				}
				case SNOW -> {
					// Snow dims the panel to a floored trickle (≥1 EU/t), same rule as the T1 panel.
					production = Math.max(1, Math.round(production * Config.solarSnowFactor));
					mode = MODE_DAY_SNOW;
				}
				case NONE -> {
					if (sky == SolarSky.Access.PARTIAL) {
						// Light filtered through a translucent block (leaves, cobweb): reduced output (MOD-004).
						production = Math.round(production * Config.solarTransparentFactor);
						mode = MODE_DAY_PARTIAL;
					}
				}
			}
		} else if (overworldSky && SolarSky.isClockDaytime(level) && level.isThundering()) {
			// A daytime thunderstorm darkens the sky past isBrightOutside(), so the branch above is never
			// entered and the mode used to stay NIGHT — at noon (MOD-602). Output is honestly zero; only
			// the label was wrong, and a player reading "Night" out of a stormy midday window can see it.
			mode = MODE_DAY_WEATHER;
		}
		this.maxProgress = mode;
		return production;
	}

	/**
	 * Rate on channel 2 ({@code progress}), written after the global multiplier rather than inside
	 * {@link #produce} so the GUI shows the rate the buffer gains while it has room (MOD-356). Mirrors the
	 * T1 panel: no renderer or sound gate reads channel 2 on a panel, so it doubles as the readout channel.
	 */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.progress = effectiveEuPerTick;
	}

	/** Replace this panel with the concentrator, carrying stored energy; ONE chip is consumed. */
	private void evolveInto(Level level, BlockPos pos, Block target) {
		// Consume a single chip and carry the rest over (MOD-211), the way the T1 panel does.
		ItemStack remainder = items.get(CHIP_SLOT).copy();
		remainder.shrink(1);
		evolveInto(level, pos, target, java.util.Map.of(CHIP_SLOT, remainder));
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// One chip at a time and only the resonance chip: an alignment chip belongs one rung lower and
		// would sit here doing nothing, which reads as a bug rather than as a wrong choice (MOD-211
		// shape: the slot must be EMPTY, or automation stacks chips and the transform wipes the rest).
		return items.get(CHIP_SLOT).isEmpty()
				&& slot == CHIP_SLOT
				&& stack.is(ModContent.RESONANCE_CHIP.get());
	}

	/**
	 * Six-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so this name means THIS machine's
	 * width for the bridge below and for the menu's client stub.
	 */
	public static final int DATA_COUNT = 6;

	/**
	 * Six-wide data: base 0..3 plus evolution progress (4) and denominator (5), both on a permille
	 * scale. See {@link SolarPanelBlockEntity} for why raw ticks cannot travel on a {@code DataSlot}.
	 */
	private final ContainerData solarData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> evolveProgress <= 0 ? 0
						: Math.max(1, (int) Math.min((long) evolveProgress * 1000 / Config.solarEvolveTicks, 1000));
				case 5 -> 1000;
				default -> DaylightSolarPanelBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// Channels 4/5 are derived, server-authoritative projections; nothing writes them back.
			if (index != 4 && index != 5) {
				DaylightSolarPanelBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return solarData;
	}

	/** Raw accumulated evolution counter in ticks, persisted in NBT. Channel 4 carries only a permille projection. */
	public int getEvolveProgressTicks() {
		return evolveProgress;
	}

	/** Seed the raw evolution counter directly (tick scale). Test/setup helper — bypasses the sync channel. */
	public void setEvolveProgressTicks(int ticks) {
		this.evolveProgress = ticks;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.daylight_solar_panel");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new DaylightSolarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		saveEvolve(output, evolveProgress);
		saveEvolveChip(output, evolveChip);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		evolveProgress = loadEvolve(input);
		evolveChip = loadEvolveChip(input);
	}
}
