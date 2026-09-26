package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.item.PipeFaceRender;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The advanced fluid pipe (MOD-675): a segment twice as large as the basic grade's, and so twice the
 * throughput, in a visibly thicker body.
 *
 * <p><b>The grade is the segment's size and nothing else.</b> Fluid moves through a pipe by filling
 * its buffer one hop per tick, so a bigger buffer IS a faster pipe; the network, the block entity, the
 * wrench modes and the family rules are the basic pipe's, inherited unchanged. A basic segment left
 * in an advanced line slows it for free, because it holds, and so hands on, less per hop.
 *
 * <p><b>The thickness is not decoration.</b> It is the item pipe's advanced body (MOD-581), and for
 * the same reason: a line slowed by one forgotten segment has to let the player find it by looking.
 *
 * <p>Not reactor-proof — only the {@link ReinforcedFluidPipeBlock reinforced} pipe is.
 */
public final class AdvancedFluidPipeBlock extends FluidPipeBlock {

	public static final MapCodec<AdvancedFluidPipeBlock> CODEC = simpleCodec(AdvancedFluidPipeBlock::new);

	public AdvancedFluidPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public int segmentCapacity() {
		return Math.max(1, Config.fluidPipeAdvancedSegmentBuffer);
	}

	@Override
	protected VoxelShape shapeFor(PipeFaceRender down, PipeFaceRender up, PipeFaceRender north,
			PipeFaceRender south, PipeFaceRender west, PipeFaceRender east) {
		return PipeShapes.ofThick(down, up, north, south, west, east);
	}
}
