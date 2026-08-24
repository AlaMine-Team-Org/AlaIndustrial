package dev.alaindustrial.fluid;

import net.minecraft.world.level.material.Fluid;

/**
 * Which of the mod's fluids must keep an entity on vanilla's AIR movement path (MOD-495).
 *
 * <p><b>The bug this exists to prevent.</b> {@code LivingEntity#travel} picks one of two paths:
 * {@code travelInFluid} when {@code shouldTravelInFluid} says the entity is in a fluid, otherwise
 * {@code travelInAir}. On NeoForge, {@code shouldTravelInFluid} also asks
 * {@code isInFluidType(fluidState)} — true for ANY registered {@code FluidType}, ours included.
 * {@code travelInFluid} then runs {@code moveInFluid}, whose contract is spelled out in NeoForge's
 * own javadoc: <i>"the movement logic will default to water if {@code getIsWaterLike()} returns
 * true, or NO MOVEMENT if it returns false"</i>. Our fluids are deliberately not water-like, and we
 * implement no custom {@code FluidType#move}, so the entity lands in a branch where neither input
 * nor gravity is applied: it hangs in place and the fluid behaves like a solid block — cannot be
 * entered, cannot be fallen into, cannot be sunk in.
 *
 * <p>This was invisible on NeoForge 26.2.0.8-beta because the whole {@code IEntityExtension} fluid
 * integration was commented out upstream, so {@code isInFluidType} always answered false and every
 * entity stayed on the air path by accident. NeoForge re-implemented the patches in 26.2.0.49-beta,
 * and the accident ended the moment the platform was raised to 26.2.0.67.
 *
 * <p><b>Why the air path is the correct answer and not merely the old one.</b> The mod's fluid
 * behaviour is built on top of air physics on purpose, on both loaders: the drag and ascent numbers
 * in {@link OilPhysics} are derived from air drag 0.98 combined with {@link OilFluid}'s own 0.72,
 * and {@link OilFluid#entityInside} — a vanilla hook that fires on both sides and on both loaders —
 * is where sinking, damping and the held-jump ascent live. Letting NeoForge route these fluids
 * through its own movement path would not just break them, it would break them differently from
 * Fabric, where no such path exists at all.
 *
 * <p><b>Scope.</b> Exactly the fluids that carry an immersion profile ({@link FluidImmersion}) —
 * oil, diesel and fuel oil, each in both its source and flowing form. Since MOD-496 that roster is
 * the single list, so a new fluid cannot be given physics while being forgotten here (or the other
 * way round). Steam is deliberately absent — it is a plain {@code Fluid} with no liquid block, so no
 * entity is ever inside it.
 */
public final class ModFluidPhysics {

	private ModFluidPhysics() {
	}

	/**
	 * Whether an entity standing in this fluid must keep vanilla's air movement, rather than being
	 * routed through the loader's fluid-movement path.
	 *
	 * @param fluid the fluid at the entity's block position
	 * @return {@code true} for the mod's world-placeable fluids, {@code false} for anything else
	 *         (including vanilla water and lava, which must keep their own physics)
	 */
	public static boolean staysOnAirPath(Fluid fluid) {
		return FluidImmersion.isModFluid(fluid);
	}
}
