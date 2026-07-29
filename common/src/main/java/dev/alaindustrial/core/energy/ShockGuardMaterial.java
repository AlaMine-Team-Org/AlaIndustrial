package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;
import java.util.function.DoubleSupplier;

/**
 * The material of an <b>insulating stand</b> slipped under a bare cable segment (MOD-279) — a cheap,
 * partial alternative to re-laying a line in rubber-insulated cable.
 *
 * <p><b>Not to be confused with cable insulation.</b> {@link CableType#isInsulated()} is a property of
 * the cable <em>block</em> (the rubber grades, MOD-259) and removes the shock hazard
 * <em>entirely</em>. A stand is a separate, weaker thing: it sits under an ordinary bare cable and
 * only lowers the <em>probability</em> of a shock. The two never combine — a stand cannot be placed on
 * an insulated grade, because there is no hazard left there to reduce and the stand would be a
 * decoration that silently does nothing.
 *
 * <p><b>A stand protects from below and beside, not from above.</b> The plate sits under the wire, so
 * a player walking past a stood segment — or standing under it — is not shocked at all, however close
 * they get. Stepping <em>onto</em> the cable puts the player above the plate, where it can only help
 * so much: that is the one case these chances govern.
 *
 * <p>Each material carries its own from-above hit chance, read <b>live</b> from {@link Config} (same
 * contract as {@link CableType}'s knobs), so a server operator can retune the ladder from
 * {@code config/alaindustrial.json} without a code change. Lower chance = better protection:
 *
 * <ul>
 *   <li>{@link #NONE} — no stand. Chance is a hard {@code 1.0}: the shipped MOD-260 behaviour, where
 *       an energized bare segment always shocks, from any direction. {@code CableBlock} short-circuits
 *       on this value without drawing from the world RNG at all, so the no-stand path stays
 *       bit-for-bit unchanged.</li>
 *   <li>{@link #WOOD} — any wood: planks, logs, stripped variants. The early-game default, available
 *       long before rubber.</li>
 *   <li>{@link #WOOL} — the softest option, and the weakest of the three from above.</li>
 *   <li>{@link #GLASS} — the best stand, and the one that costs a furnace step to make.</li>
 * </ul>
 *
 * <p><b>Deliberately free of Minecraft types</b>, exactly like {@link CableType}: the L1 unit suite
 * runs without the game on the classpath, and the pitest target glob {@code dev.alaindustrial.core.*}
 * covers this package. The item↔material matching and the stack a stand drops when removed both need
 * MC types, so they live next to their only consumer in {@code CableBlock}.
 */
public enum ShockGuardMaterial {
	/** No stand under this segment — the cable shocks on every eligible contact, as it always has. */
	NONE("none", () -> 1.0),
	/** Any wood — planks, logs, stripped. Available the moment a player has a tree, long before rubber. */
	WOOD("wood", () -> Config.shockGuardWoodHitChance),
	/** Wool. Soft, cheap, and the least effective of the three. */
	WOOL("wool", () -> Config.shockGuardWoolHitChance),
	/** Glass. The strongest stand, gated behind a furnace. */
	GLASS("glass", () -> Config.shockGuardGlassHitChance);

	private final String name;
	private final DoubleSupplier hitChance;

	ShockGuardMaterial(String name, DoubleSupplier hitChance) {
		this.name = name;
		this.hitChance = hitChance;
	}

	/**
	 * Probability (0..1) that a shock <b>from above</b> still lands through this stand. Contacts from
	 * the side or below never reach this method — the stand blocks those outright. {@code 1.0} means
	 * the stand offers no protection from above, {@code 0.0} means it blocks every such hit. Values
	 * outside the range are not an error and need no clamping: the caller compares against
	 * {@code RandomSource.nextDouble()}, whose range is {@code [0,1)}, so anything {@code >= 1.0}
	 * always hits and anything {@code <= 0.0} never does.
	 */
	public double hitChance() {
		return hitChance.getAsDouble();
	}

	/** Whether this is a real stand rather than the {@link #NONE} sentinel. */
	public boolean isPresent() {
		return this != NONE;
	}

	/** Stable id used for NBT persistence — never rename without a save migration. */
	public String serializedName() {
		return name;
	}

	/**
	 * Resolve a {@link #serializedName()} back to its constant, falling back to {@link #NONE} for an
	 * unknown or absent id. The lenient fallback is deliberate: it is what makes a world saved by a
	 * future version (or a hand-edited chunk) load as "no stand" instead of throwing during chunk load.
	 */
	public static ShockGuardMaterial byName(String name) {
		for (ShockGuardMaterial material : values()) {
			if (material.name.equals(name)) {
				return material;
			}
		}
		return NONE;
	}
}
