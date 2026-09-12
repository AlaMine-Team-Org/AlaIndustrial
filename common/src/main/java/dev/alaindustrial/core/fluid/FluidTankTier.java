package dev.alaindustrial.core.fluid;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * The two grades of portable fluid tank (MOD-612) — everything that differs between them, in one
 * place.
 *
 * <p>Before this the capacity was read straight from {@code Config.fluidTankCapacity} in three
 * places: the block entity that holds the fluid, the item tooltip that reports it and the guide. That
 * is exactly right while there is one tank and exactly wrong the moment there are two — the second
 * grade would either share the first one's number or grow an {@code if} at every read. The same shape
 * as {@link dev.alaindustrial.item.tool.MagnetTier} (MOD-580) and {@code BladeTier} (MOD-145), for
 * the same reason.
 *
 * <p><b>Why the tooltip matters here specifically.</b> This mod has shipped a tier-2 tooltip
 * describing tier 1 twice already — the advanced item pipe claimed the basic pipe's throughput
 * (0.1.149) and the advanced electromagnet claimed the basic magnet's range (0.1.148). Both times the
 * cause was the same: a number written out at the call site instead of asked of the grade. So the
 * capacity has exactly one reader-facing source, and it is this enum.
 *
 * <p>The value is an {@link IntSupplier} rather than an int because {@code Config} is mutable at
 * runtime: a reloaded config must reach a grade that was resolved at class-init, or the knob would
 * silently stop working for tanks.
 *
 * <p><b>Deliberately Minecraft-free</b>, exactly like {@code MagnetTier}: no {@code net.minecraft}
 * type appears here, so the L1 lane can unit-test the relation between the grades (advanced holds
 * more than basic) — the thing a careless config edit breaks in silence. Resolving a BLOCK to its
 * grade needs Minecraft, so that lives in {@code FluidTankBlock.tierOf}.
 */
public enum FluidTankTier {

	/** The original (MOD-111): eight buckets, deliberately below a machine tank's ten. */
	BASIC(() -> Config.fluidTankCapacity),

	/**
	 * The second grade (MOD-612): sixteen buckets.
	 *
	 * <p>Twice the first grade, not four times — the ratio this mod already uses for a logistics tier
	 * (the advanced item pipe moves 4 items where the basic moves 2). A bigger multiplier obsoletes
	 * the grade below it on the day the new one becomes craftable, and a tier the player skips is a
	 * tier that did not need to exist.
	 */
	ADVANCED(() -> Config.fluidTankAdvancedCapacity);

	private final IntSupplier capacity;

	FluidTankTier(IntSupplier capacity) {
		this.capacity = capacity;
	}

	/** How much this grade holds, in mB. */
	public int capacity() {
		return capacity.getAsInt();
	}

}
