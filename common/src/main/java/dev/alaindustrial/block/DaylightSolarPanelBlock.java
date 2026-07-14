package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.core.SolarSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Daylight Solar Panel — T2 day branch, the stronger day-mirror of {@link SolarPanelBlock}. Passive
 * LV generator driven by {@link SolarSky#isDaylitActive}. Half-block slab, hum while producing, EU
 * from five faces (the {@code UP} face is the working surface; see {@link AbstractSolarPanelBlock}).
 */
public class DaylightSolarPanelBlock extends AbstractSolarPanelBlock {
	public static final MapCodec<DaylightSolarPanelBlock> CODEC = simpleCodec(DaylightSolarPanelBlock::new);

	public DaylightSolarPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
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
