package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
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
 * LV Daylight Solar Panel — T2 day branch. Day, under open sky, in the Overworld: 4 EU/t;
 * rain/thunder and night: 0 (MOD-003). Buffer 8000, LV output (20). The stronger day-mirror of
 * {@link SolarPanelBlockEntity} (1 EU/t neutral) — the evolved Solar Path form.
 *
 * <p>Sync channels: {@code progress} = production (EU/t); {@code maxProgress} = mode
 * (0 inactive/night, 1 day clear, 2 day weather).
 */
public class DaylightSolarPanelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	private static final int MAX_EXTRACT = 20;

	/** Mode codes shared with the screen. */
	public static final int MODE_NIGHT = 0;
	public static final int MODE_DAY = 1;
	public static final int MODE_DAY_WEATHER = 2;
	public static final int MODE_DAY_PARTIAL = 3;
	public static final int MODE_DAY_SNOW = 4;

	public DaylightSolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DAYLIGHT_SOLAR_PANEL, pos, state, EnergyTier.LV, 0, Config.solarBuffer, MAX_EXTRACT);
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
		int production = 0;
		int mode = MODE_NIGHT;
		dev.alaindustrial.core.SolarSky.Access sky = level.dimension().equals(Level.OVERWORLD)
				? dev.alaindustrial.core.SolarSky.classify(level, pos)
				: dev.alaindustrial.core.SolarSky.Access.BLOCKED;
		if (sky != dev.alaindustrial.core.SolarSky.Access.BLOCKED && level.isBrightOutside()) {
			production = Config.daylightEuPerTick;
			mode = MODE_DAY;
			switch (dev.alaindustrial.core.SolarSky.classifyWeather(level, pos)) {
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
					if (sky == dev.alaindustrial.core.SolarSky.Access.PARTIAL) {
						// Light filtered through a translucent block (leaves, cobweb): reduced output (MOD-004).
						production = Math.round(production * Config.solarTransparentFactor);
						mode = MODE_DAY_PARTIAL;
					}
				}
			}
		}
		this.progress = production;
		this.maxProgress = mode;
		return production;
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
}
