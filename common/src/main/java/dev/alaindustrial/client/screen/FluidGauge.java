package dev.alaindustrial.client.screen;

import dev.alaindustrial.item.fluid.FluidDisplayNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Draws a fluid level inside a GUI trough, and names the fluid for its tooltip. Shared by every machine
 * screen with a tank gauge — the Pump and the Polymerizer today (MOD-019 pulled this out of
 * {@code PumpScreen}, where it started as MOD-099's any-fluid rendering, so the two screens cannot drift).
 *
 * <p>The texture and tint come from vanilla's baked fluid model ({@code ModelManager#getFluidStateModelSet}),
 * which is the same lookup the world renderer uses — so any fluid a loader registers a model for (both
 * Fabric and NeoForge feed their modded fluids into this very set) renders correctly with no per-loader
 * code and no asset of ours. {@code get} returns a missing-texture model rather than null for an unknown
 * fluid, so there is no null path to guard.
 */
public final class FluidGauge {
	private FluidGauge() {
	}

	/** Block textures are 16×16 and are tiled at that native size (see {@link #draw}). */
	private static final int TILE = 16;

	/**
	 * Multiplicative identity for {@code blitSprite}'s colour argument: draw the sprite exactly as-is,
	 * used for fluids that declare no tint source (e.g. lava).
	 */
	private static final int NO_TINT = 0xFFFFFFFF;

	/**
	 * Draw {@code fluid} filling a {@code width}-wide trough from {@code topY} down to {@code topY + height},
	 * using the fluid's own still texture tiled at its native 16×16 and clipped to the trough.
	 *
	 * <p>Tiling rather than stretching is deliberate: a trough is narrower and taller than one tile, and
	 * stretching a single tile to fit visibly smears it — fluid textures are regular patterns, and the
	 * distortion reads as a rendering bug. The fill height is rarely a multiple of 16, so tiles are laid
	 * from the bottom edge upward and the top one is cut off mid-texture by the scissor.
	 */
	public static void draw(GuiGraphicsExtractor graphics, Fluid fluid, int leftX, int topY, int width, int height) {
		Minecraft minecraft = Minecraft.getInstance();
		FluidState state = fluid.defaultFluidState();
		FluidModel model = minecraft.getModelManager().getFluidStateModelSet().get(state);
		TextureAtlasSprite sprite = model.stillMaterial().sprite();
		int tint = tint(model.tintSource(), state, minecraft);

		int bottomY = topY + height;
		graphics.enableScissor(leftX, topY, leftX + width, bottomY);
		for (int tileY = bottomY - TILE; tileY > topY - TILE; tileY -= TILE) {
			for (int tileX = leftX; tileX < leftX + width; tileX += TILE) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tileX, tileY, TILE, TILE, tint);
			}
		}
		graphics.disableScissor();
	}

	/**
	 * The opaque ARGB to multiply a fluid's texture by: {@link #NO_TINT} when the fluid declares no tint
	 * source, otherwise its biome-sampled colour, falling back to the world-less variant when there is no
	 * level/player to sample against (e.g. a screen opened outside a world).
	 *
	 * <p>The tint is what makes water blue: {@code water_still} is a greyscale texture. {@code tintSource()}
	 * is <b>nullable</b> and is null for exactly the fluids that need no tint (lava — its texture is already
	 * red); dereferencing it unconditionally crashed the pump screen with an NPE the moment a lava-filled
	 * pump was opened, which no compile or server test can catch.
	 *
	 * <p>The tint must be sampled <em>in the world</em>: vanilla's water tint returns {@code -1} ("no tint",
	 * leaving the trough grey) from the world-less {@code color(BlockState)} and only yields a real colour
	 * from {@code colorInWorld}, which reads the biome. It is sampled at the player's position — they are
	 * standing at the machine to have this screen open — because the client menu does not know the block's
	 * own position ({@code ContainerLevelAccess.NULL} client-side). A machine right on a biome border can
	 * therefore show the neighbouring biome's shade; harmless, and invisible without a side-by-side compare.
	 */
	private static int tint(BlockTintSource tintSource, FluidState state, Minecraft minecraft) {
		if (tintSource == null) {
			return NO_TINT;
		}
		BlockState legacy = state.createLegacyBlock();
		int rgb = minecraft.level != null && minecraft.player != null
				? tintSource.colorInWorld(legacy, minecraft.level, minecraft.player.blockPosition())
				: tintSource.color(legacy);
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/**
	 * A player-facing name for {@code fluid}, for the gauge's tooltip. The naming itself is
	 * {@link FluidDisplayNames#of} — one implementation shared with the Vacuum Capsule, which prints
	 * the very same name for the very same fluid.
	 */
	public static Component displayName(Fluid fluid) {
		return FluidDisplayNames.of(fluid);
	}
}
