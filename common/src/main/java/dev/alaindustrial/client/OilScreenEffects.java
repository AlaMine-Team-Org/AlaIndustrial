package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.OilPhysics;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;

/**
 * What the player sees while submerged in oil (MOD-248): the screen overlay and the shared
 * "are my eyes in oil" test the fog environment uses too.
 *
 * <p><b>Why any of this is hand-rolled.</b> Vanilla's own overlay is hard-coded to
 * {@code isEyeInFluid(FluidTags.WATER)} inside {@code ScreenEffectRenderer#submit}, and the camera's
 * fluid classification ({@code Camera#getFluidInCamera} → {@link net.minecraft.world.level.material.FogType})
 * is a five-constant enum that only knows water, lava and powder snow. NeoForge does declare an
 * extension point — {@code IClientFluidTypeExtensions#getRenderOverlayTexture} / {@code renderOverlay}
 * — but in 26.2.0.8-beta nothing in NeoForge ever calls it (the only occurrence in the whole
 * distribution is the declaration itself), and Fabric ships no fog or screen-effect API at all.
 * A client mixin in {@code common/} is therefore the only route, and it has the side benefit of
 * being one implementation for both loaders.
 *
 * <p>The mixin lives in {@code alaindustrial.compat-optional.mixins.json}, not the required config:
 * a screen tint is cosmetic, and a conflict with a rendering overhaul must degrade the effect rather
 * than refuse to launch the game.
 */
public final class OilScreenEffects {

	/** Full-screen overlay drawn while the camera is inside oil. */
	public static final Identifier OVERLAY_TEXTURE =
			Industrialization.id("textures/misc/oil_overlay.png");

	/**
	 * Master opacity of the overlay, on top of the per-pixel alpha the texture already carries.
	 *
	 * <p>The first version was 0.94 over a fully opaque texture, which in game meant the screen went
	 * black the instant the player's head went under: no horizon, no light, no way to tell which way
	 * to swim. "Being submerged is unpleasant" is the intent; "the player cannot play" is not. The
	 * film is now something to see through — dark, streaked, with opaque beads scattered over it —
	 * and the {@code OilFogEnvironment} distances were opened up to match.
	 */
	private static final float OVERLAY_ALPHA = 0.85F;

	/** How far the texture is tiled across the screen; matches vanilla's underwater overlay. */
	private static final float UV_SIZE = 4.0F;

	private OilScreenEffects() {
	}

	/**
	 * Whether the entity's eyes are inside oil — see {@link OilPhysics#isEyeInOil}. Kept as a
	 * delegating alias because the fog environment and the overlay both read it, and because since
	 * MOD-250 the very same test also drives drowning, which is server-side and cannot live in a
	 * client class.
	 */
	public static boolean isEyeInOil(Entity entity) {
		return OilPhysics.isEyeInOil(entity);
	}

	/**
	 * Submits the overlay quad. The geometry is the same trivial full-screen quad vanilla uses for
	 * every screen effect — corners at ±1 with the near plane at −0.5 — with the UV origin driven by
	 * the look direction so the texture drifts as the player turns instead of sitting frozen on the
	 * screen.
	 */
	public static void submitOverlay(Entity camera, PoseStack poseStack, SubmitNodeCollector collector) {
		float u0 = -camera.getYRot() / 64.0F;
		float v0 = camera.getXRot() / 64.0F;
		int color = ARGB.colorFromFloat(OVERLAY_ALPHA, 1.0F, 1.0F, 1.0F);
		collector.submitCustomGeometry(poseStack, RenderTypes.blockScreenEffect(OVERLAY_TEXTURE),
				(pose, builder) -> {
					builder.addVertex(pose.pose(), -1.0F, -1.0F, -0.5F)
							.setUv(u0 + UV_SIZE, v0 + UV_SIZE).setColor(color);
					builder.addVertex(pose.pose(), 1.0F, -1.0F, -0.5F)
							.setUv(u0, v0 + UV_SIZE).setColor(color);
					builder.addVertex(pose.pose(), 1.0F, 1.0F, -0.5F)
							.setUv(u0, v0).setColor(color);
					builder.addVertex(pose.pose(), -1.0F, 1.0F, -0.5F)
							.setUv(u0 + UV_SIZE, v0).setColor(color);
				});
	}
}
