package dev.alaindustrial.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * What being inside oil does to an entity (MOD-250) — the loader-neutral, side-neutral core shared by
 * {@link OilFluid#entityInside}, the drowning mixin and the client's screen effects.
 *
 * <p><b>Why the mod has to write this at all.</b> Every fluid behaviour vanilla ships is keyed on
 * {@code FluidTags.WATER} or {@code FluidTags.LAVA}: {@code isInWater()}, {@code isInLava()},
 * {@code shouldTravelInFluid}, the jump branch of {@code LivingEntity#aiStep}, the breathing block of
 * {@code LivingEntity#baseTick}. A modded fluid is none of those tags, so it inherits nothing —
 * an entity in oil falls with air physics and breathes indefinitely.
 *
 * <p><b>Why oil is not simply put in {@code #minecraft:water}.</b> That shortcut would hand us
 * swimming and drowning for free and was checked against the 26.2 sources before being rejected: 114
 * places read that tag, and two of them are fatal here — {@code LavaFluid} turns anything tagged as
 * water into stone or obsidian on contact, which would delete the entire burning mechanic wherever a
 * deposit meets lava, and {@code FarmlandBlock} would treat a pool of crude as irrigation. The tag
 * also grants water's swim speed, which is the opposite of what a viscous fluid should feel like.
 */
public final class OilPhysics {

	private OilPhysics() {
	}

	/**
	 * Upward impulse added per tick while an entity holds jump inside oil.
	 *
	 * <p>Sized from the actual physics rather than copied from water. Inside oil the entity is still on
	 * the air path ({@code shouldTravelInFluid} is false for a fluid in neither vanilla tag), so per
	 * tick the vertical speed is multiplied by air drag 0.98 and then by {@link OilFluid}'s own 0.72,
	 * i.e. 0.706, with gravity 0.08 subtracted. The steady-state climb is therefore
	 * {@code v ≈ 2.4 × (impulse − 0.08)}: vanilla's water impulse of 0.04 does not even cancel gravity
	 * here, which is exactly the reported bug — an entity that reached the bottom of a pool could not
	 * get back up. {@value} yields roughly one block per second up, deliberately slower than both water
	 * and lava, since crude is the thickest thing in the game.
	 */
	public static final double ASCENT_IMPULSE = 0.10D;

	/** Whether this fluid is oil, in either its source or its flowing form. */
	public static boolean isOil(Fluid fluid) {
		return fluid == ModContent.OIL.get() || fluid == ModContent.FLOWING_OIL.get();
	}

	/**
	 * Whether the entity's eyes are inside oil — the test behind both the screen overlay and drowning.
	 *
	 * <p>Deliberately the same shape as {@code Camera#getFluidInCamera}: the fluid at the eye block,
	 * plus the eye actually being below that cell's fluid surface. Without the second half, standing in
	 * a shallow flow with the head well clear would black the screen out and start the drowning timer.
	 */
	public static boolean isEyeInOil(Entity entity) {
		if (entity == null) {
			return false;
		}
		Vec3 eye = entity.getEyePosition();
		BlockPos pos = BlockPos.containing(eye);
		FluidState fluidState = entity.level().getFluidState(pos);
		if (!isOil(fluidState.getType())) {
			return false;
		}
		return eye.y < pos.getY() + fluidState.getHeight(entity.level(), pos);
	}
}
