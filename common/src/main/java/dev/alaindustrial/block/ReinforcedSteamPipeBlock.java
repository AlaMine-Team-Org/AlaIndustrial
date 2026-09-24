package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The reinforced steam pipe (MOD-662): the steam pipe in a shielding-alloy jacket, for the stretch of a
 * steam line that runs INSIDE a reactor room.
 *
 * <p>Like the {@link ReinforcedFluidPipeBlock reinforced fluid pipe} it differs in one rule, and that
 * rule is a tag: it is in {@code #alaindustrial:meltproof}, so a working room — which melts every
 * ordinary pipe inside its shell — passes it over. It joins ordinary steam pipes wherever they touch.
 */
public final class ReinforcedSteamPipeBlock extends SteamPipeBlock {

	public static final MapCodec<ReinforcedSteamPipeBlock> CODEC = simpleCodec(ReinforcedSteamPipeBlock::new);

	public ReinforcedSteamPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
