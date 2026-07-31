package dev.alaindustrial.client.render;

import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Colours a fluid pipe's inner core with whatever it is carrying (MOD-151).
 *
 * <p><b>Why a tint source and not a block-entity renderer.</b> Pipes are placed in the hundreds. A BER
 * runs per visible segment per frame and needs the segment's contents pushed to the client whenever
 * they change, which on a running line is every tick. Tinting instead costs nothing per frame — the
 * geometry is baked into the chunk mesh like any other block — and the only thing the client needs to
 * know is WHICH fluid, which changes rarely.
 *
 * <p><b>Where the colour comes from.</b> The same fluid model set the world renderer uses, so every
 * fluid a loader registers — vanilla, ours, or another mod's — is coloured correctly with no
 * per-loader or per-mod code. {@code tintSource()} is nullable (lava, notably, has no tint), and a
 * fluid can be missing a model entirely, so both fall back to untinted rather than crashing a chunk
 * rebuild.
 */
public final class FluidPipeTint implements BlockTintSource {

	/** Opaque white: the multiply leaves the core texture as it is. */
	public static final int UNTINTED = 0xFFFFFFFF;

	public static final FluidPipeTint INSTANCE = new FluidPipeTint();

	private FluidPipeTint() {
	}

	/** Out-of-world (inventory icon): the item model has no tinted layer, so nothing to colour. */
	@Override
	public int color(BlockState state) {
		return UNTINTED;
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)) {
			return UNTINTED;
		}
		Fluid fluid = pipe.fluidBuffer.fluid.fluid();
		return fluid == Fluids.EMPTY ? UNTINTED : colorOf(fluid, level, pos);
	}

	/** The world-renderer colour for {@code fluid}, or untinted when it declares none. */
	private static int colorOf(Fluid fluid, BlockAndTintGetter level, BlockPos pos) {
		var model = Minecraft.getInstance().getModelManager()
				.getFluidStateModelSet().get(fluid.defaultFluidState());
		if (model == null) {
			return UNTINTED;
		}
		var tintSource = model.tintSource();
		if (tintSource == null) {
			// Lava and anything else that paints itself from its texture rather than a tint.
			return UNTINTED;
		}
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		return 0xFF000000 | (tintSource.colorInWorld(legacy, level, pos) & 0x00FFFFFF);
	}
}
