package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The Distillation Column's middle segment (MOD-251) — the crude-oil intake: its faces expose the
 * base's oil tank to pumps and buckets. Structural in every other respect; see
 * {@link DistillationColumnSegmentBlock}.
 */
public class DistillationColumnMiddleBlock extends DistillationColumnSegmentBlock {
	public static final MapCodec<DistillationColumnMiddleBlock> CODEC =
			simpleCodec(DistillationColumnMiddleBlock::new);

	public DistillationColumnMiddleBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public int offsetToBase() {
		return 1;
	}
}
