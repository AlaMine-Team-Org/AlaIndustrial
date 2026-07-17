package dev.alaindustrial.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

/** Loader-neutral fallback colours for the portable tank's item icon and fill bar. */
public final class FluidTankVisuals {
	private static final int WATER = 0x3F76E4;
	private static final int LAVA = 0xFF6A00;

	private FluidTankVisuals() {
	}

	public static int fallbackColor(Fluid fluid) {
		if (fluid == Fluids.WATER) {
			return WATER;
		}
		if (fluid == Fluids.LAVA) {
			return LAVA;
		}
		MapColor mapColor = fluid.defaultFluidState().createLegacyBlock().getMapColor(null, BlockPos.ZERO);
		if (mapColor != MapColor.NONE) {
			return mapColor.col;
		}
		var id = BuiltInRegistries.FLUID.getKey(fluid);
		float hue = Math.floorMod(id.hashCode(), 360) / 360.0F;
		return Mth.hsvToRgb(hue, 0.65F, 0.9F);
	}
}
