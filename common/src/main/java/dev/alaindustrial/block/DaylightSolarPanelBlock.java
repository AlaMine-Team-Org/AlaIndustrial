package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.SolarSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Daylight Solar Panel — T2 day branch, the stronger day-mirror of {@link SolarPanelBlock}. Passive
 * LV generator driven by {@link SolarSky#isDaylitActive}. Half-block slab, hum while producing, EU
 * from five faces (the {@code UP} face is the working surface; see {@link AbstractSolarPanelBlock}).
 */
public class DaylightSolarPanelBlock extends AbstractSolarPanelBlock {
	public DaylightSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DaylightSolarPanelBlockEntity(pos, state);
	}

	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isDaylitActive(level, pos);
	}
}
