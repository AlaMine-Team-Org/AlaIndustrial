package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** LV Mob Repeller (MOD-278) — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerBlock extends AbstractMobRepellerBlock {
	public static final MapCodec<MobRepellerBlock> CODEC = simpleCodec(MobRepellerBlock::new);

	public MobRepellerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerBlockEntity(pos, state);
	}
}
