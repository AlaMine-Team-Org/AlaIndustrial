package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** HV Mob Repeller (MOD-278), the top evolved tier — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerHvBlock extends AbstractMobRepellerBlock {
	public static final MapCodec<MobRepellerHvBlock> CODEC = simpleCodec(MobRepellerHvBlock::new);

	public MobRepellerHvBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerHvBlockEntity(pos, state);
	}
}
