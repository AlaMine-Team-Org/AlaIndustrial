package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import dev.alaindustrial.registry.ModBlockEntities;
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
 * LV Moonlit (Lunar) Solar Panel — produces EU from moonlight. Night, under open sky, in the
 * Overworld: 2 EU/t; rain/thunder and day: 0 (MOD-003). Buffer 8000, LV output (20).
 *
 * <p>The night-mirror of {@link SolarPanelBlockEntity}: where the day panel uses
 * {@code level.isBrightOutside()} as its sunlight test, the moonlit panel negates it.
 *
 * <p>Sync channels (inherited via {@code progress}/{@code maxProgress}):
 * <ul>
 *   <li>{@code progress} = current production (EU/t)</li>
 *   <li>{@code maxProgress} = mode: 0 inactive/day, 1 night clear, 2 night weather</li>
 * </ul>
 */
public class MoonlitSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 20;

	/** Mode codes shared with the screen. */
	public static final int MODE_DAY = 0;
	public static final int MODE_NIGHT = 1;
	public static final int MODE_NIGHT_WEATHER = 2;
	public static final int MODE_NIGHT_PARTIAL = 3;
	public static final int MODE_NIGHT_SNOW = 4;

	public MoonlitSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MOONLIT_SOLAR_PANEL, pos, state, EnergyTier.LV, 0, Config.solarBuffer, MAX_EXTRACT);
	}

	/**
	 * Top face is the working surface (captures moonlight) and must not emit EU; the other five sides are
	 * standard generator outputs (R-NRG-03) — mirrors {@link SolarPanelBlockEntity}. Without this override
	 * the evolved panel inherited all-faces-OUT and leaked power from its working surface (spec: 5-side).
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == Direction.UP ? EnergyRole.NONE : EnergyRole.OUT;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		int production = 0;
		int mode = MODE_DAY;
		dev.alaindustrial.core.SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? dev.alaindustrial.core.SolarSky.classify(level, pos)
				: dev.alaindustrial.core.SolarSky.Access.BLOCKED;
		if (sky != dev.alaindustrial.core.SolarSky.Access.BLOCKED && !level.isBrightOutside()) {
			production = Config.moonlitEuPerTick;
			mode = MODE_NIGHT;
			switch (dev.alaindustrial.core.SolarSky.classifyWeather(level, pos)) {
				case RAIN -> {
					// Unlike the day panels, the moonlit panel keeps a small weather trickle in rain/thunder
					// instead of going dark — the moon is still faintly effective.
					production = Config.moonlitWeatherEuPerTick;
					mode = MODE_NIGHT_WEATHER;
				}
				case SNOW -> {
					// Snow dims moonlight to a floored trickle (≥1 EU/t), same rule as the other tiers.
					production = Math.max(1, Math.round(production * Config.solarSnowFactor));
					mode = MODE_NIGHT_SNOW;
				}
				case NONE -> {
					if (sky == dev.alaindustrial.core.SolarSky.Access.PARTIAL) {
						// Moonlight filtered through a translucent block (leaves, cobweb): reduced output (MOD-004).
						production = Math.round(production * Config.solarTransparentFactor);
						mode = MODE_NIGHT_PARTIAL;
					}
				}
			}
		}
		// Surface production rate + mode to the GUI via the generic sync channels.
		this.progress = production;
		this.maxProgress = mode;
		return production;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.moonlit_solar_panel");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new MoonlitSolarPanelMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
