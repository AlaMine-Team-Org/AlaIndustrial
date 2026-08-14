package dev.alaindustrial.block;

import net.minecraft.util.StringRepresentable;

/**
 * How hot the Electric Heater looks from the outside (MOD-418) — its coils read as a thermometer.
 *
 * <p>Deliberately four states and not the boolean {@code lit} it used to carry. {@code lit} could only
 * say "paying for a tick right now", which was true in the same instant whether the block was ice cold
 * or about to hand the machine above its x3. Temperature is the thing worth knowing, and it is worth
 * knowing <em>without opening the screen</em> — so it goes on the block, and
 * {@link dev.alaindustrial.block.entity.EnergyBlockEntity#updateLit} (typed against
 * {@code BlockStateProperties.LIT}) cannot drive it. Same reasoning, same shape as
 * {@link ChargePadState}.
 *
 * <p>The ladder is not evenly spaced on purpose: {@link #GLOWING} means "full temperature, x3 output
 * right now", so it gets its own rung at exactly 100 % rather than sharing the top third with
 * temperatures that still only pay x2.
 */
public enum HeaterGlow implements StringRepresentable {
	/** Stone cold and costing nothing. A heater at rest is not a light source. */
	COLD("cold"),
	/** Warming, first half. The coils have colour but the room does not notice. */
	WARM("warm"),
	/** Warming, second half — visibly working, still short of the x3. */
	HOT("hot"),
	/** Full temperature: the machine above is getting x3. The unmistakable "it is ready" look. */
	GLOWING("glowing");

	private final String serializedName;

	HeaterGlow(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/**
	 * Emitted light for this rung.
	 *
	 * <p>Tops out at the 13 the mod's working machines use, so a ready heater sits level with a running
	 * furnace rather than out-glowing it, and steps down to nothing when cold — a bank of idle heaters
	 * never lights a base. The two middle rungs are dim enough that "bright" still reads as "ready".
	 */
	public int lightLevel() {
		return switch (this) {
			case COLD -> 0;
			case WARM -> 4;
			case HOT -> 8;
			case GLOWING -> 13;
		};
	}

	/**
	 * The rung a warm-up fraction lands on, in permille so the caller needs no floating point.
	 *
	 * <p>Lives here rather than in the block entity so the thresholds sit next to the light levels they
	 * explain, and so L1 can pin the ladder without touching Minecraft.
	 */
	public static HeaterGlow forPermille(int permille) {
		if (permille <= 0) {
			return COLD;
		}
		if (permille >= 1000) {
			return GLOWING;
		}
		return permille < 500 ? WARM : HOT;
	}
}
