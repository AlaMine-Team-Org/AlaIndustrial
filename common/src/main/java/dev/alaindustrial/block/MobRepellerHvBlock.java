package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** HV Mob Repeller (MOD-278), the top evolved tier — see {@link AbstractMobRepellerBlock}. */
public class MobRepellerHvBlock extends AbstractMobRepellerBlock {
	public MobRepellerHvBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobRepellerHvBlockEntity(pos, state);
	}
}
