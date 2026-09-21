package dev.alaindustrial.block;

/**
 * The Distillation Column's middle segment (MOD-251) — the crude-oil intake: its faces expose the
 * base's oil tank to pumps and buckets. Structural in every other respect; see
 * {@link DistillationColumnSegmentBlock}.
 */
public class DistillationColumnMiddleBlock extends DistillationColumnSegmentBlock {
	public DistillationColumnMiddleBlock(Properties properties) {
		super(properties);
	}

	@Override
	public int offsetToBase() {
		return 1;
	}
}
