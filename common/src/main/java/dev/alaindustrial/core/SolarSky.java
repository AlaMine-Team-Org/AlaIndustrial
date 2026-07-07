package dev.alaindustrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Three-level sky-access classification for solar panels (MOD-004). Replaces the old binary
 * {@code canSeeSkyFromBelowWater} check so a panel under a translucent block (leaves, cobweb) still
 * generates at a reduced rate instead of nothing — matching the player's expectation that light
 * filters through foliage.
 *
 * <p>The column directly above the panel is scanned upward; the first block that is not fully
 * sky-transparent decides the verdict:
 * <ul>
 *   <li>{@link Access#BLOCKED} — an opaque block ({@code canOcclude()}) or a fluid: no light → 0 EU.</li>
 *   <li>{@link Access#PARTIAL} — a light-dimming translucent block (leaves, cobweb): reduced EU.</li>
 *   <li>{@link Access#CLEAR} — only sky-transparent blocks (air, glass) up to the build height: full EU.</li>
 * </ul>
 * Glass is fully transparent ({@code propagatesSkylightDown()}), so it does not reduce output.
 */
public final class SolarSky {
	private SolarSky() {
	}

	public enum Access {
		CLEAR, PARTIAL, BLOCKED
	}

	/**
	 * Active precipitation over a panel, shared by all three solar tiers so they classify weather
	 * identically:
	 * <ul>
	 *   <li>{@link Weather#NONE} — clear, or rain over a no-precipitation biome (desert): panel works.</li>
	 *   <li>{@link Weather#RAIN} — rainfall, or a thunderstorm over any precipitation (thunder beats snow).</li>
	 *   <li>{@link Weather#SNOW} — snowfall in a cold biome without thunder, or a {@code minecraft:snow}
	 *       layer resting on the panel.</li>
	 * </ul>
	 * Day panels treat RAIN as a blackout and SNOW as a dimmed trickle; the moonlit panel keeps a small
	 * trickle in both. Biome-based precipitation avoids the self-column heightmap trap of
	 * {@code Level#isRainingAt}.
	 */
	public enum Weather {
		NONE, RAIN, SNOW
	}

	/** Classify active precipitation over {@code panelPos} (see {@link Weather}). Time of day is the caller's concern. */
	public static Weather classifyWeather(Level level, BlockPos panelPos) {
		boolean snowLayer = snowLayerAbove(level, panelPos);
		if (!level.isRaining() && !snowLayer) {
			return Weather.NONE; // clear sky (a resting snow layer still counts as snow below)
		}
		Biome.Precipitation precip = level.isRaining()
				? level.getBiome(panelPos).value().getPrecipitationAt(panelPos, level.getSeaLevel())
				: Biome.Precipitation.NONE;
		boolean rainHere = precip == Biome.Precipitation.RAIN;
		boolean snowHere = precip == Biome.Precipitation.SNOW;
		if (rainHere || (snowHere && level.isThundering())) {
			return Weather.RAIN; // rain, or thunder over snow (thunder overrides snow, WEATHER > SNOW)
		}
		if ((snowHere && !level.isThundering()) || snowLayer) {
			return Weather.SNOW; // snowfall without thunder, or an accumulated snow layer
		}
		return Weather.NONE; // no-precipitation biome (desert): panel keeps working in any weather
	}

	/** Classify sky access for a panel at {@code panelPos} by scanning the column above it. */
	public static Access classify(Level level, BlockPos panelPos) {
		int top = level.getMaxY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = panelPos.getY() + 1; y <= top; y++) {
			cursor.set(panelPos.getX(), y, panelPos.getZ());
			BlockState st = level.getBlockState(cursor);
			if (st.propagatesSkylightDown()) {
				continue; // air, glass — skylight passes straight through, keep scanning up
			}
			if (st.canOcclude() || !st.getFluidState().isEmpty()) {
				return Access.BLOCKED; // opaque block or fluid: no direct light
			}
			return Access.PARTIAL; // first light-dimming translucent block (leaves, cobweb) decides
		}
		return Access.CLEAR; // nothing but sky-transparent blocks all the way up
	}

	/**
	 * True when a thin snow layer ({@code minecraft:snow}, {@link Blocks#SNOW}) sits directly on top of the
	 * panel. This is the accumulated-snow trigger for MODE_SNOW (distinct from live snowfall, which the
	 * caller detects via biome precipitation). The full snow block ({@code snow_block}) is opaque and is
	 * handled as BLOCKED by {@link #classify}, not here.
	 */
	public static boolean snowLayerAbove(Level level, BlockPos panelPos) {
		return level.getBlockState(panelPos.above()).getBlock() == Blocks.SNOW;
	}
}
