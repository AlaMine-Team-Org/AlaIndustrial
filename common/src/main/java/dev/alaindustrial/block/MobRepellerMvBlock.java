package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** MV Mob Repeller (MOD-278), evolved tier — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerMvBlock extends AbstractMobRepellerBlock {
	public MobRepellerMvBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerMvBlockEntity(pos, state);
	}
}
