package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import net.minecraft.core.Direction;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
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
 * LV solar panel (spec: alaindustrial:solar_panel) — the neutral T1 generator. Day, under open
 * sky, in the Overworld: 1 EU/t default; rain/thunder and night: 0 (MOD-003). Buffer 8000, LV output.
 *
 * <p>Evolution: with an {@linkplain ModContent#ALIGNMENT_CHIP_DAY evolution chip} in its slot, the
 * panel accumulates active sky-time at the chip's time of day; once {@link Config#solarEvolveTicks}
 * is reached it transforms into the matching T2 branch (daylight / moonlit), carrying its stored
 * energy and consuming the chip.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 production (EU/t), 3 mode, 4 evolution progress
 * (permille 0..1000), 5 denominator (constant 1000). Permille avoids the 16-bit DataSlot overflow
 * that {@code solarEvolveTicks} = 33 600 would otherwise cause (see {@code solarData}).
 */
public class SolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	public static final int CHIP_SLOT = 0;
	private static final int MAX_EXTRACT = 20;

	/** Mode codes shared with the screen. */
	public static final int MODE_NIGHT = 0;
	public static final int MODE_DAY = 1;
	public static final int MODE_WEATHER = 2;
	public static final int MODE_PARTIAL = 3;
	public static final int MODE_SNOW = 4;

	/**
	 * The top face is the working surface (the panel face that captures sunlight) — it must not
	 * emit EU so a cable or machine placed above does not accidentally receive power from it.
	 * The other five sides behave as standard generator output faces (R-NRG-03).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	private int evolveProgress;

	public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SOLAR_PANEL_BE.get(), pos, state, EnergyTier.LV, 1, Config.solarBuffer, MAX_EXTRACT);
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		dev.alaindustrial.core.SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? dev.alaindustrial.core.SolarSky.classify(level, pos)
				: dev.alaindustrial.core.SolarSky.Access.BLOCKED;
		boolean overworldSky = sky != dev.alaindustrial.core.SolarSky.Access.BLOCKED;
		boolean bright = level.isBrightOutside();

		// --- Evolution: advance while the right chip sees the right sky-time. ---
		ItemStack chip = items.get(CHIP_SLOT);
		boolean dayChip = chip.is(ModContent.ALIGNMENT_CHIP_DAY.get());
		boolean nightChip = chip.is(ModContent.ALIGNMENT_CHIP_NIGHT.get());
		if ((dayChip || nightChip) && overworldSky && (dayChip == bright)) {
			evolveProgress++;
			if (evolveProgress >= Config.solarEvolveTicks) {
				evolveInto(level, pos, dayChip ? ModContent.DAYLIGHT_SOLAR_PANEL.get() : ModContent.MOONLIT_SOLAR_PANEL.get());
				return 0; // this block entity is gone after the transform
			}
			setChanged();
		}

		// --- Production: neutral day generation. Mode priority: WEATHER > SNOW > PARTIAL > DAY (night = 0). ---
		int production = 0;
		int mode = MODE_NIGHT;
		if (overworldSky && bright) {
			production = Config.solarEuPerTick;
			mode = MODE_DAY;
			switch (dev.alaindustrial.core.SolarSky.classifyWeather(level, pos)) {
				case RAIN -> {
					// Rain/thunder blocks direct sunlight: no generation (MOD-003). Mode flag kept for GUI.
					production = 0;
					mode = MODE_WEATHER;
				}
				case SNOW -> {
					// Snow dims the panel to a floored trickle (≥1 EU/t) — round(base × factor) alone would
					// truncate the T1 base of 1 to 0, so the panel appears dead in snow. Beats PARTIAL/DAY.
					production = Math.max(1, Math.round(production * Config.solarSnowFactor));
					mode = MODE_SNOW;
				}
				case NONE -> {
					if (sky == dev.alaindustrial.core.SolarSky.Access.PARTIAL) {
						// Light filtered through a translucent block (leaves, cobweb): reduced output (MOD-004).
						production = Math.round(production * Config.solarTransparentFactor);
						mode = MODE_PARTIAL;
					}
				}
			}
		}
		this.progress = production;
		this.maxProgress = mode;
		return production;
	}

	/** Replace this panel with its evolved branch, carrying stored energy; the chip is consumed. */
	private void evolveInto(Level level, BlockPos pos, Block target) {
		long saved = energy.amount;
		items.set(CHIP_SLOT, ItemStack.EMPTY);
		level.setBlockAndUpdate(pos, target.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity evolved) {
			evolved.getEnergyStorage().amount = Math.min(saved, evolved.getEnergyStorage().getCapacity());
			evolved.setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == CHIP_SLOT
				&& (stack.is(ModContent.ALIGNMENT_CHIP_DAY.get()) || stack.is(ModContent.ALIGNMENT_CHIP_NIGHT.get()));
	}

	/**
	 * Six-wide data: base 0..3 plus evolution progress (4) and denominator (5).
	 *
	 * <p>The evolution channels are scaled to <b>permille (0..1000)</b>, not raw ticks. Vanilla
	 * {@code DataSlot} serialises each value as a 16-bit short (max 32767), and
	 * {@link Config#solarEvolveTicks} (33 600 by default) overflows that — sending the raw counter made
	 * the target arrive <em>negative</em> on the client, so the screen's {@code evoMax > 0} guard failed
	 * and the progress bar never rendered. Channel 4 is the completion permille (≥1 as soon as any
	 * progress accrues, preserving the min-1px feedback); channel 5 is the constant denominator 1000.
	 * The screen renders from the 4/5 ratio, so no client change is needed.
	 */
	private final ContainerData solarData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> evolveProgress <= 0 ? 0
						: Math.max(1, (int) Math.min((long) evolveProgress * 1000 / Config.solarEvolveTicks, 1000));
				case 5 -> 1000;
				default -> SolarPanelBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			// Channels 4/5 are derived, server-authoritative projections: evolveProgress lives in NBT and
			// is advanced only in produce(); nothing writes them back through the ContainerData.
			if (index != 4 && index != 5) {
				SolarPanelBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 6;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return solarData;
	}

	/**
	 * Raw accumulated evolution counter in ticks (0..{@link Config#solarEvolveTicks}), persisted in NBT.
	 * Sync channel 4 exposes only a permille projection of this (see {@code solarData}), so callers that
	 * need the exact tick count — NBT round-trip tests, future logic — must use this accessor, not the
	 * ContainerData channel.
	 */
	public int getEvolveProgressTicks() {
		return evolveProgress;
	}

	/** Seed the raw evolution counter directly (tick scale). Test/setup helper — bypasses the sync channel. */
	public void setEvolveProgressTicks(int ticks) {
		this.evolveProgress = ticks;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.solar_panel");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new SolarPanelMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
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
