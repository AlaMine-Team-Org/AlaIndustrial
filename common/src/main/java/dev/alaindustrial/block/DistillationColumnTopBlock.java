package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Distillation Column's top segment (MOD-251) — the diesel condenser: its faces expose the
 * base's diesel tank, and its {@code lit} state (set by the base only when the column is <b>hot and
 * distilling</b>, not merely warming) drives the steam plume — so from across the base "steam over
 * the tower" always means the refinery is actually running.
 */
public class DistillationColumnTopBlock extends DistillationColumnSegmentBlock {
	public static final MapCodec<DistillationColumnTopBlock> CODEC =
			simpleCodec(DistillationColumnTopBlock::new);

	public DistillationColumnTopBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public int offsetToBase() {
		return 2;
	}

	/**
	 * Steam over a hot, working column — vanilla campfire smoke, client-side, no custom particle
	 * registration. Sparse (roughly one puff every couple of seconds per column) so a refinery row
	 * reads as gentle haze, not a house fire. With a Rectification Section installed above, the
	 * plume moves up there (round 2) — this segment stays quiet.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (level.getBlockState(pos.above()).getBlock() instanceof RectificationSectionBlock) {
			return;
		}
		if (state.getValue(LIT) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
					pos.getX() + 0.35D + random.nextDouble() * 0.3D,
					pos.getY() + 1.05D,
					pos.getZ() + 0.35D + random.nextDouble() * 0.3D,
					0.0D, 0.06D + random.nextDouble() * 0.03D, 0.0D);
		}
	}
}
