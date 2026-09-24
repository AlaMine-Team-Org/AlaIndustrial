package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * The reinforced fluid pipe (MOD-660): the ordinary fluid pipe in a shielding-alloy jacket, built to be
 * laid INSIDE a reactor room.
 *
 * <p><b>It differs in exactly one rule, and that rule lives in a tag, not here.</b> A running reactor
 * melts every ordinary fluid pipe in its room; this one is in {@code #alaindustrial:meltproof}, the
 * same tag that spares the reactor's own shell, so both hazards — a room at work and a room in
 * meltdown — pass it over without this class being named anywhere. The player's rule is "shielding
 * shields", and a pipe wrapped in shielding plate follows it like the chest built from the same plate.
 *
 * <p><b>Same throughput, same network, same block entity.</b> The reactor port it feeds carries
 * 50 mB/t, so a faster pipe inside would be a number with nothing to spend it on. Being a subclass
 * keeps every {@code instanceof FluidPipeBlock} test true, so an ordinary pipe outside and a reinforced
 * one inside join into one line wherever they touch.
 */
public final class ReinforcedFluidPipeBlock extends FluidPipeBlock {

	public static final MapCodec<ReinforcedFluidPipeBlock> CODEC = simpleCodec(ReinforcedFluidPipeBlock::new);

	public ReinforcedFluidPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
