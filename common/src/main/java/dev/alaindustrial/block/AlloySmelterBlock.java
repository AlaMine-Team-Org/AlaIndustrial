package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Three-input LV machine that melts metals into alloys (MOD-064).
 *
 * <p>Deliberately does not implement {@code MachineHumProvider}: the smelter is silent, following the
 * sawmill's precedent. It ships no new sound assets, so it cannot conflict with the separate
 * sound-coverage work.
 */
public class AlloySmelterBlock extends LitMachineBlock {
	public static final MapCodec<AlloySmelterBlock> CODEC = simpleCodec(AlloySmelterBlock::new);

	public AlloySmelterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AlloySmelterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
