package dev.alaindustrial.client.render;

import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The loader installs its immutable chunk-meshing snapshot reader before models are baked. */
public final class RootSoilAppearance implements BlockTintSource {
	public static BiFunction<BlockAndTintGetter, BlockPos, BlockState> reader = (level, pos) -> Blocks.DIRT.defaultBlockState();
	public static final RootSoilAppearance INSTANCE = new RootSoilAppearance();
	private RootSoilAppearance() { }
	@Override public int color(BlockState state) { return -1; }
	@Override public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		BlockState soil = reader.apply(level, pos);
		BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(soil, 0);
		return tint == null ? -1 : tint.colorInWorld(soil, level, pos);
	}
}
