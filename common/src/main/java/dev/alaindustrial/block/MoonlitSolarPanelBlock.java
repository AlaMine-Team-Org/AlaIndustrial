package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.SolarSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moonlit Solar Panel — night-mirror of {@link SolarPanelBlock}. Passive LV generator driven by
 * {@link SolarSky#isMoonlitActive}. Half-block slab, hum while producing, EU from five faces (the
 * {@code UP} face is the working surface; see {@link AbstractSolarPanelBlock}).
 */
public class MoonlitSolarPanelBlock extends AbstractSolarPanelBlock {
	public MoonlitSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MoonlitSolarPanelBlockEntity(pos, state);
	}

	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isMoonlitActive(level, pos);
	}
}
