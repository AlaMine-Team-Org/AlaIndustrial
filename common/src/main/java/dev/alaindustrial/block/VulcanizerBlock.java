package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/** Externally heated two-input LV machine that vulcanizes raw rubber with sulfur dust. */
public final class VulcanizerBlock extends LitMachineBlock {
	public static final MapCodec<VulcanizerBlock> CODEC = simpleCodec(VulcanizerBlock::new);

	public VulcanizerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VulcanizerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VulcanizerBlockEntity vulcanizer) {
			vulcanizer.onHeatNeighbourChanged();
		}
	}

	/** Steam and an occasional hiss make the active state visible without opening the GUI. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
		level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, pos.getY() + 1.02, z, 0.0, 0.025, 0.0);
		if (random.nextDouble() < 0.08) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
					SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F,
					0.9F + random.nextFloat() * 0.2F, false);
		}
	}
}
