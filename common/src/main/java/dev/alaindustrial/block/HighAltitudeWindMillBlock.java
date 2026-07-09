package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.HighAltitudeWindMillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-altitude wind mill block — the height-focused T2 evolution of the wind mill. A full cube that
 * faces the player on placement; the front {@code facing} face is energy-inert, the other five emit
 * EU. Same physical block shape and placement rules as {@link WindMillBlock}; only the entity
 * (generation formula) and the front texture differ.
 */
public class HighAltitudeWindMillBlock extends HorizontalMachineBlock {
	public static final MapCodec<HighAltitudeWindMillBlock> CODEC = simpleCodec(HighAltitudeWindMillBlock::new);

	public HighAltitudeWindMillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HighAltitudeWindMillBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
