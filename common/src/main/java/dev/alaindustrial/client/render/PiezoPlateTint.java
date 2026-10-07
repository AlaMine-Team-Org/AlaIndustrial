package dev.alaindustrial.client.render;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Passes the floor block's tint on to a camouflaged piezo plate (MOD-764): a plate on grass is the grass's
 * biome green, not the grey of the untinted texture. Vanilla takes a quad's tint from the RENDERED block's own
 * tint list, so the plate needs a source per tint index that answers with the floor's colour at that index.
 */
public final class PiezoPlateTint implements BlockTintSource {
	/** Tint indices a floor block may use; vanilla full cubes use index 0 only. */
	public static final List<BlockTintSource> SOURCES = List.of(new PiezoPlateTint(0), new PiezoPlateTint(1));

	private final int index;

	private PiezoPlateTint(int index) {
		this.index = index;
	}

	@Override
	public int color(BlockState state) {
		return -1;
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		BlockPos floor = pos.below();
		BlockState below = level.getBlockState(floor);
		BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(below, index);
		return tint == null ? -1 : tint.colorInWorld(below, level, floor);
	}
}
