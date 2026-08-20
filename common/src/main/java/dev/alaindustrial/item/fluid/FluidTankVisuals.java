package dev.alaindustrial.item.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

/**
 * Loader-neutral fallback colours for a fluid that declares no tint source of its own — the mod's fluid
 * colour table. Read by the pipe core in-world ({@code FluidPipeTint}), the portable tank's item icon
 * ({@code FluidTankItemTintSource}) and its fill bar ({@code FluidTankBlockItem#getBarColor}).
 */
public final class FluidTankVisuals {
	private static final int WATER = 0x3F76E4;
	private static final int LAVA = 0xFF6A00;

	/**
	 * The three mod fluids (MOD-450), each the average colour of its own {@code block/<name>_still}
	 * texture. Named here rather than left to the {@link MapColor} branch below because a map colour is
	 * a 60-entry palette meant for cartography, not a match for a fluid: diesel's
	 * {@code MapColor.COLOR_YELLOW} is a lemon {@code #E5E533}, three quarters of the way to green from
	 * the golden ochre the fluid actually is, and a pipe carrying it read wrong next to the fluid it
	 * came from.
	 *
	 * <p>The same three values are mirrored in {@code tools/textures_work/vacuum_capsule/
	 * gen_vacuum_capsule.py}, which bakes them into the capsule's item textures (a baked sprite cannot
	 * read this table). Change one, change both — nothing checks the pair.
	 */
	private static final int OIL = 0x14100E;
	private static final int DIESEL = 0xB78515;
	private static final int FUEL_OIL = 0x362614;

	/**
	 * Steam (MOD-468) — the average colour of {@code block/steam_still}, like the three above. It has to
	 * be named here for a stronger reason than they do: steam has no block, so the {@link MapColor}
	 * branch below cannot see it either ({@code createLegacyBlock()} is air, whose map colour is
	 * {@code NONE}), and it would fall all the way through to the id-hash — a colour that is stable but
	 * arbitrary, and nothing to do with vapour.
	 */
	private static final int STEAM = 0xD7E6F3;

	private FluidTankVisuals() {
	}

	public static int fallbackColor(Fluid fluid) {
		if (fluid == Fluids.WATER) {
			return WATER;
		}
		if (fluid == Fluids.LAVA) {
			return LAVA;
		}
		// isSame covers each fluid's flowing form too: a pipe or tank can hold either, and the two must
		// not read as different liquids. Reading the ModContent facade here is runtime-only, as required.
		if (ModContent.OIL.get().isSame(fluid)) {
			return OIL;
		}
		if (ModContent.DIESEL.get().isSame(fluid)) {
			return DIESEL;
		}
		if (ModContent.FUEL_OIL.get().isSame(fluid)) {
			return FUEL_OIL;
		}
		if (ModContent.STEAM.get().isSame(fluid)) {
			return STEAM;
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
