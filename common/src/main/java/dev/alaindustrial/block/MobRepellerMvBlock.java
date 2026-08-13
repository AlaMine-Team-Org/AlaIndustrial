package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** MV Mob Repeller (MOD-278), evolved tier — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerMvBlock extends AbstractMobRepellerBlock {
	public static final MapCodec<MobRepellerMvBlock> CODEC = simpleCodec(MobRepellerMvBlock::new);

	public MobRepellerMvBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerMvBlockEntity(pos, state);
	}
}
