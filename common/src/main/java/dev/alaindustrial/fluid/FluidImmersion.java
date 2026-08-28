package dev.alaindustrial.fluid;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * What being inside each of the mod's fluids feels like (MOD-496) — one profile per fluid, and the
 * single implementation of the physics that reads it.
 *
 * <p><b>Why this exists.</b> Until MOD-496 the whole immersion mechanic lived inside
 * {@link OilFluid#entityInside}: drag, the held-jump ascent (MOD-250) and the fall-distance reset
 * were written for crude oil and reachable only from crude oil. Diesel and fuel oil, which share
 * {@link DistillateFluid}, therefore had no physics of any kind — and the result was not the "behaves
 * like water" the spec promised but a trap: an entity fell straight to the bottom of a pool and
 * could not get out, because a modded fluid is in neither vanilla tag and so inherits neither water's
 * swimming nor its buoyancy. A player reported exactly that. Keeping the mechanic keyed to one class
 * is what let a second and third fluid ship without it, so it now lives here, per fluid, and a new
 * fluid without an entry fails loudly in {@link #of} rather than silently behaving like a wall.
 *
 * <p><b>How the numbers relate.</b> Vertical drag is the whole model. An entity inside one of these
 * fluids stays on vanilla's AIR path (see {@code LivingEntityModFluidTravelMixin}, MOD-495), so per
 * tick its vertical speed is multiplied by air drag 0.98, then by {@link #verticalDrag}, with gravity
 * 0.08 subtracted. With {@code k = 0.98 × verticalDrag} the steady-state speeds are
 * {@code sink = 0.08 / (1 − k)} and {@code rise = (ascent − 0.08) / (1 − k)}. That is why one shared
 * {@link #ASCENT_IMPULSE} produces three different climb rates: the thinner the fluid, the less of
 * the impulse the drag eats. Ordering follows the viscosity already fixed in the fluid types —
 * diesel 1200, fuel oil 2400, oil 3000 — so the fluid that reads as thinner also moves the entity
 * more freely, and no number here is invented independently of that scale.
 *
 * <table>
 *   <caption>Resulting feel</caption>
 *   <tr><th>Fluid</th><th>Sink</th><th>Rise on held jump</th></tr>
 *   <tr><td>Diesel</td><td>≈ 9.6 blocks/s</td><td>≈ 2.4 blocks/s</td></tr>
 *   <tr><td>Fuel oil</td><td>≈ 6.8 blocks/s</td><td>≈ 1.7 blocks/s</td></tr>
 *   <tr><td>Crude oil</td><td>≈ 5.4 blocks/s</td><td>≈ 1.4 blocks/s</td></tr>
 * </table>
 *
 * <p><b>Drowning stays crude-only.</b> The fractions are machine products the player pours between
 * tanks; the spec calls them harmless on purpose, and nothing here changes that. What MOD-496 fixes
 * is that "harmless" must not mean "inescapable".
 */
public enum FluidImmersion {

	/**
	 * Crude oil — the thickest thing in the game. Values unchanged from MOD-248/MOD-250: wading not
	 * walking, a slow sink, a slow rise, and the near-black film that made a deposit read as a hazard.
	 */
	OIL(() -> ModContent.OIL.get(), () -> ModContent.FLOWING_OIL.get(),
			0.45D, 0.72D, true,
			0xFF0B0906, 1.5F, 9.0F,
			Industrialization.id("textures/misc/oil_overlay.png"), 0.85F),

	/**
	 * Fuel oil — the heavy residue (viscosity 2400). Between diesel and crude in every respect; the
	 * overlay is dark but browner than crude's, so the two are told apart while submerged.
	 */
	FUEL_OIL(() -> ModContent.FUEL_OIL.get(), () -> ModContent.FLOWING_FUEL_OIL.get(),
			0.62D, 0.78D, false,
			0xFF120C05, 2.5F, 14.0F,
			Industrialization.id("textures/misc/fuel_oil_overlay.png"), 0.70F),

	/**
	 * Diesel — the light fraction (viscosity 1200), barely thicker than water. The most forgiving of
	 * the three to fall into and the quickest to climb out of, with a thin amber film that says "you
	 * are submerged" without taking the view away.
	 */
	DIESEL(() -> ModContent.DIESEL.get(), () -> ModContent.FLOWING_DIESEL.get(),
			0.80D, 0.85D, false,
			0xFF3A2A0C, 4.0F, 22.0F,
			Industrialization.id("textures/misc/diesel_overlay.png"), 0.55F),

	/**
	 * Biofuel (MOD-146) — brewed rather than refined, and thinner than any oil fraction: the easiest
	 * of the mod's liquids to climb out of, with a murky olive film.
	 */
	BIOFUEL(() -> ModContent.BIOFUEL.get(), () -> ModContent.FLOWING_BIOFUEL.get(),
			0.85D, 0.88D, false,
			0xFF2A3310, 4.5F, 24.0F,
			Industrialization.id("textures/misc/biofuel_overlay.png"), 0.50F),

	/**
	 * Nutrient solution (MOD-525) — the thinnest thing the mod makes, near enough to water to swim
	 * in. Its film is the faintest of the five: this is the one liquid a player is meant to stand in
	 * while working a field.
	 */
	NUTRIENT_SOLUTION(() -> ModContent.NUTRIENT_SOLUTION.get(),
			() -> ModContent.FLOWING_NUTRIENT_SOLUTION.get(),
			0.90D, 0.90D, false,
			0xFF16301F, 6.0F, 28.0F,
			Industrialization.id("textures/misc/nutrient_solution_overlay.png"), 0.40F);

	/**
	 * Upward impulse added per tick while an entity holds jump inside any of these fluids (MOD-250,
	 * generalised in MOD-496).
	 *
	 * <p>Shared deliberately: the per-fluid feel comes from {@link #verticalDrag}, which is what the
	 * viscosity scale actually describes. Sized against crude, the thickest case — vanilla's water
	 * impulse of 0.04 does not even cancel gravity there, which was the original MOD-250 bug (an
	 * entity that reached the bottom of a pool could not get back up).
	 */
	public static final double ASCENT_IMPULSE = 0.10D;

	private final Supplier<Fluid> source;
	private final Supplier<Fluid> flowing;
	private final double horizontalDrag;
	private final double verticalDrag;
	private final boolean drowns;
	private final int fogColor;
	private final float fogStart;
	private final float fogEnd;
	private final Identifier overlayTexture;
	private final float overlayAlpha;

	FluidImmersion(Supplier<Fluid> source, Supplier<Fluid> flowing, double horizontalDrag,
			double verticalDrag, boolean drowns, int fogColor, float fogStart, float fogEnd,
			Identifier overlayTexture, float overlayAlpha) {
		this.source = source;
		this.flowing = flowing;
		this.horizontalDrag = horizontalDrag;
		this.verticalDrag = verticalDrag;
		this.drowns = drowns;
		this.fogColor = fogColor;
		this.fogStart = fogStart;
		this.fogEnd = fogEnd;
		this.overlayTexture = overlayTexture;
		this.overlayAlpha = overlayAlpha;
	}

	/** The profile for this fluid, or {@code null} when the fluid is not one of the mod's. */
	public static FluidImmersion of(Fluid fluid) {
		for (FluidImmersion profile : values()) {
			if (fluid == profile.source.get() || fluid == profile.flowing.get()) {
				return profile;
			}
		}
		return null;
	}

	/** Whether this fluid is one the mod gives immersion physics to. */
	public static boolean isModFluid(Fluid fluid) {
		return of(fluid) != null;
	}

	/**
	 * The profile whose fluid the entity's eyes are inside, or {@code null}.
	 *
	 * <p>Deliberately the same shape as {@code Camera#getFluidInCamera}: the fluid at the eye block,
	 * plus the eye actually being below that cell's fluid surface. Without the second half, standing
	 * in a shallow flow with the head well clear would tint the screen and start the drowning timer.
	 */
	public static FluidImmersion atEyes(Entity entity) {
		if (entity == null) {
			return null;
		}
		Vec3 eye = entity.getEyePosition();
		BlockPos pos = BlockPos.containing(eye);
		FluidState fluidState = entity.level().getFluidState(pos);
		FluidImmersion profile = of(fluidState.getType());
		if (profile == null) {
			return null;
		}
		return eye.y < pos.getY() + fluidState.getHeight(entity.level(), pos) ? profile : null;
	}

	/**
	 * The whole immersion effect for one entity in one cell — damping, the held-jump ascent and the
	 * fall-distance reset. Called from each fluid's {@code entityInside}, which vanilla fires from
	 * {@code Entity.checkInsideBlocks} on BOTH sides, so client and server agree and the local player
	 * never fights a server correction.
	 *
	 * <p>The guard on {@code blockPosition()} is what makes this idempotent: {@code checkInsideBlocks}
	 * fires once per intersected block, so an entity spanning three cells would otherwise be damped
	 * three times in one tick and the strength would depend on how deep it happens to be.
	 *
	 * <p>The impulse is added <em>after</em> the drag so a held jump is worth its full value on the
	 * tick it is made instead of being damped twice. {@code isAffectedByFluids} is vanilla's gate for
	 * "fluids move this entity at all" — false for a creative player in flight, who would otherwise be
	 * shoved upward while flying through a deposit.
	 */
	public static void applyEntityInside(Level level, BlockPos pos, Entity entity) {
		if (!pos.equals(entity.blockPosition())) {
			return;
		}
		FluidImmersion profile = of(level.getFluidState(pos).getType());
		if (profile == null) {
			return;
		}
		Vec3 motion = entity.getDeltaMovement();
		double ascent = 0.0D;
		if (entity instanceof LivingEntity living && living.isJumping() && living.isAffectedByFluids()) {
			ascent = ASCENT_IMPULSE;
		}
		entity.setDeltaMovement(motion.x * profile.horizontalDrag,
				motion.y * profile.verticalDrag + ascent,
				motion.z * profile.horizontalDrag);
		entity.resetFallDistance();
	}

	/** Whether an entity with its eyes in this fluid loses air (crude oil only — see the class doc). */
	public boolean drowns() {
		return drowns;
	}

	/** ARGB colour of the fog while the camera is inside this fluid. */
	public int fogColor() {
		return fogColor;
	}

	/** Near plane of the environmental fog inside this fluid, in blocks. */
	public float fogStart() {
		return fogStart;
	}

	/** Far plane of the environmental fog inside this fluid, in blocks. */
	public float fogEnd() {
		return fogEnd;
	}

	/** Full-screen film drawn while the camera is inside this fluid. */
	public Identifier overlayTexture() {
		return overlayTexture;
	}

	/** Master opacity of {@link #overlayTexture()}, on top of the per-pixel alpha it already carries. */
	public float overlayAlpha() {
		return overlayAlpha;
	}
}
