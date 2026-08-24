package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.FluidImmersion;
import dev.alaindustrial.mixin.client.FogRendererAccessor;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

/**
 * Near-black, arm's-length fog while the camera is inside oil (MOD-248) — the other half of the
 * "you are submerged" read, next to {@link OilScreenEffects}.
 *
 * <p><b>Why it is installed into a vanilla list.</b> Fog is chosen by walking
 * {@code FogRenderer.FOG_ENVIRONMENTS} and taking the first entry whose {@code isApplicable} accepts
 * the current {@link FogType}. That enum cannot be extended and {@code Camera#getFluidInCamera}
 * classifies every custom fluid as {@code NONE} → {@code ATMOSPHERIC}, so no data-driven route
 * exists. What saves this is that {@code isApplicable} also receives the {@link Entity}: the fluid
 * test can be our own, and the {@code FogType} argument is simply ignored.
 *
 * <p>The entry is inserted immediately BEFORE {@link AtmosphericFogEnvironment}, which is the
 * documented last resort of the list. Lava, powder snow, blindness, darkness and water therefore
 * keep their priority — a player who is on fire inside oil still gets the lava treatment, and a
 * blindness effect is not overridden by a cosmetic tint.
 */
public final class OilFogEnvironment extends FogEnvironment {

	/**
	 * Fog colour, ARGB. Not pure black on purpose: an absolutely black fog reads as a broken render
	 * rather than as a substance, and it leaves nothing for a light source to pick out.
	 */
	/** Fallback tint if the profile lookup misses; the live values come from {@link FluidImmersion}. */
	private static final int COLOR = 0xFF0B0906;

	/**
	 * Where the fog begins and ends, in blocks.
	 *
	 * <p>These started at 0.05/0.5 — thicker than lava's 0.25/1.0, which was faithful to crude oil
	 * and unplayable: combined with the screen overlay it left the player blind, unable to tell
	 * where they were or which way to go. Nine blocks of murk still reads unmistakably as being
	 * inside a thick liquid while leaving enough of the world visible to swim out of it, or down a
	 * geyser shaft on purpose.
	 */
	private static final float START = 1.5F;
	private static final float END = 9.0F;

	/** Spectators pass through everything, including this — same courtesy vanilla extends in lava. */
	private static final float SPECTATOR_START = -8.0F;

	private OilFogEnvironment() {
	}

	/**
	 * Installs the environment ahead of the atmospheric fallback, once. Failure is swallowed and
	 * logged: this is a cosmetic effect riding an accessor mixin from the OPTIONAL mixin config, so a
	 * conflict with a rendering overhaul must cost the tint and nothing else.
	 */
	public static void install() {
		try {
			List<FogEnvironment> environments = FogRendererAccessor.alaindustrial$fogEnvironments();
			for (FogEnvironment existing : environments) {
				if (existing instanceof OilFogEnvironment) {
					return;
				}
			}
			int insertAt = environments.size();
			for (int i = 0; i < environments.size(); i++) {
				if (environments.get(i) instanceof AtmosphericFogEnvironment) {
					insertAt = i;
					break;
				}
			}
			environments.add(insertAt, new OilFogEnvironment());
		} catch (RuntimeException | LinkageError | AssertionError failure) {
			// AssertionError is the one that actually matters and it was missing: an unapplied
			// @Accessor leaves the interface's own stub body in place, and that stub throws exactly
			// that. Catching only RuntimeException/LinkageError covered the immutable-list case and
			// let the case this whole optional-mixin arrangement exists for — a rendering mod winning
			// the conflict — crash the client on startup instead of costing the tint.
			Industrialization.LOGGER.warn("Oil fog is unavailable — could not extend the vanilla fog"
					+ " environment list. Being submerged in oil will look like being in air.", failure);
		}
	}

	@Override
	public int getBaseColor(ClientLevel level, Camera camera, int renderDistance, float partialTicks) {
		FluidImmersion profile = FluidImmersion.atEyes(camera.entity());
		return profile != null ? profile.fogColor() : COLOR;
	}

	@Override
	public void setupFog(FogData fog, Camera camera, ClientLevel level, float renderDistance,
			DeltaTracker deltaTracker) {
		if (camera.entity().isSpectator()) {
			fog.environmentalStart = SPECTATOR_START;
			fog.environmentalEnd = renderDistance * 0.5F;
		} else {
			FluidImmersion profile = FluidImmersion.atEyes(camera.entity());
			fog.environmentalStart = profile != null ? profile.fogStart() : START;
			fog.environmentalEnd = profile != null ? profile.fogEnd() : END;
		}
		// Left at their defaults these stay 0 and the sky punches straight through the fog.
		fog.skyEnd = fog.environmentalEnd;
		fog.cloudEnd = fog.environmentalEnd;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return FluidImmersion.atEyes(entity) != null;
	}
}
