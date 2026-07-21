package dev.alaindustrial.core.fluid;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Platform-neutral fluid identity (MOD-028): a thin wrapper over the vanilla {@link Fluid} type (already
 * loader-neutral — {@code net.minecraft.world.level.material.Fluid} — so this holds no loader-specific
 * variant/component state, unlike Fabric's {@code FluidVariant} or NeoForge's {@code FluidResource}). The
 * mod's fluid machines (pump, geothermal generator) only ever move pure lava, so identity alone (no NBT
 * components) is sufficient — mirrors the scope note on {@link FluidPort}.
 */
public final class FluidHolder {

	/** The absence of a fluid — an empty tank/port reports this. */
	public static final FluidHolder EMPTY = new FluidHolder(Fluids.EMPTY);

	private final Fluid fluid;

	private FluidHolder(Fluid fluid) {
		this.fluid = fluid;
	}

	/** Wrap {@code fluid} as a holder, or {@link #EMPTY} if {@code fluid} is {@link Fluids#EMPTY}. */
	public static FluidHolder of(Fluid fluid) {
		return (fluid == null || fluid == Fluids.EMPTY) ? EMPTY : new FluidHolder(fluid);
	}

	public Fluid fluid() {
		return fluid;
	}

	public boolean isEmpty() {
		return fluid == Fluids.EMPTY;
	}

	/** Whether this holder identifies the same fluid as {@code other}. */
	public boolean is(Fluid other) {
		return fluid == other;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FluidHolder other && fluid == other.fluid;
	}

	@Override
	public int hashCode() {
		return fluid.hashCode();
	}

	@Override
	public String toString() {
		return "FluidHolder[" + fluid + "]";
	}
}
