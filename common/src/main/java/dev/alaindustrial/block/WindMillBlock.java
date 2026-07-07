package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wind mill block — a full-cube LV generator that faces the player on placement. The {@code facing}
 * front is energy-inert (via {@link dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity}); the
 * other five faces emit EU. No {@code lit} state (production is passive, not fuel-driven), so it extends
 * {@link HorizontalMachineBlock} rather than {@link LitMachineBlock}. The default full-cube shape is
 * inherited (no {@code getShape} override).
 */
public class WindMillBlock extends HorizontalMachineBlock {
	public static final MapCodec<WindMillBlock> CODEC = simpleCodec(WindMillBlock::new);

	public WindMillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WindMillBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
