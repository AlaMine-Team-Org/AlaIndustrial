package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.fluid.FluidImmersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;

/**
 * What the player sees while submerged in oil (MOD-248): the screen overlay and the shared
 * "are my eyes in oil" test the fog environment uses too.
 *
 * <p><b>Why any of this is hand-rolled.</b> Vanilla's own overlay is hard-coded to
 * {@code isEyeInFluid(FluidTags.WATER)} inside {@code ScreenEffectRenderer#submit}, and the camera's
 * fluid classification ({@code Camera#getFluidInCamera} → {@link net.minecraft.world.level.material.FogType})
 * is a five-constant enum that only knows water, lava and powder snow. Fabric ships no fog or
 * screen-effect API at all, so a client mixin in {@code common/} is the only route that covers both
 * loaders with one implementation.
 *
 * <p><b>NeoForge's own extension point is live since 26.2.0.67 — and deliberately left unused
 * (MOD-495).</b> {@code ScreenEffectRenderer#submit} now calls
 * {@code IClientFluidTypeExtensions.of(fluidType).renderOverlay(...)} for any custom fluid the eyes
 * are in (on 26.2.0.8-beta nothing called it, which is why this mixin was written). It stays unused
 * because it is NeoForge-only and this overlay must also exist on Fabric. The default
 * {@code getRenderOverlayTexture} returns {@code null}, so an unregistered extension draws nothing
 * and the mixin remains the single source of the effect. <b>Registering the extension WITHOUT
 * removing this mixin would draw the overlay twice.</b>
 *
 * <p>The mixin lives in {@code alaindustrial.compat-optional.mixins.json}, not the required config:
 * a screen tint is cosmetic, and a conflict with a rendering overhaul must degrade the effect rather
 * than refuse to launch the game.
 */
public final class OilScreenEffects {

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
		return FluidImmersion.atEyes(entity) != null;
	}

	/**
	 * Submits the overlay quad. The geometry is the same trivial full-screen quad vanilla uses for
	 * every screen effect — corners at ±1 with the near plane at −0.5 — with the UV origin driven by
	 * the look direction so the texture drifts as the player turns instead of sitting frozen on the
	 * screen.
	 */
	public static void submitOverlay(Entity camera, PoseStack poseStack, SubmitNodeCollector collector) {
		FluidImmersion profile = FluidImmersion.atEyes(camera);
		if (profile == null) {
			return;
		}
		float u0 = -camera.getYRot() / 64.0F;
		float v0 = camera.getXRot() / 64.0F;
		int color = ARGB.colorFromFloat(profile.overlayAlpha(), 1.0F, 1.0F, 1.0F);
		collector.submitCustomGeometry(poseStack, RenderTypes.blockScreenEffect(profile.overlayTexture()),
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
