package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ReactorPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The reactor inlet (MOD-468, stage 3) — the one place fluid may cross a sealed shell.
 *
 * <p><b>It is a shell block first.</b> It extends {@link ReactorShellBlock} rather than standing on
 * its own so it keeps everything the wall around it has: the {@code formed}/{@code edge} states, the
 * seamless art when the room snaps together, and the shared-face culling. A room-scan sees it as
 * casing, so putting one in a wall is not a breach — which is the entire reason it exists.
 *
 * <p><b>Why it needed a block entity at all.</b> Through stage 2 this was a decorative cube: it looked
 * like a bulkhead penetration and did nothing, so a pipe run up to it simply refused to connect —
 * pipes only join neighbours that publish a fluid port. Stage 3 gives it the port, and with it the one
 * number that makes the water loop a design problem rather than a formality: an inlet passes
 * {@code Config.reactorPortThroughput} mB a tick and no more. A reactor at full power wants more than
 * one inlet can carry, so a serious core needs several crossings — and every crossing is a hole the
 * player has to fit into a wall that still has to seal.
 */
public class ReactorPortBlock extends ReactorShellBlock implements EntityBlock {

	public static final MapCodec<ReactorPortBlock> CODEC = simpleCodec(ReactorPortBlock::new);

	public ReactorPortBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends ReactorShellBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorPortBlockEntity(pos, state);
	}
}
