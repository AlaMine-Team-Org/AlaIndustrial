package dev.alaindustrial.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Whether a stack is fuel and how long it burns in one of the mod's fuel-fired machines (the fuel
 * generator and the iron furnace) — a version facade (MOD-703, ADR-036): every Minecraft line has a twin
 * of this class with the same signatures, and only the bodies differ, so the two machines' sources are
 * the same on every line.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where fuel is a per-level lookup table, {@code FuelValues}
 * ({@code level.fuelValues()}); the {@code machine} argument of {@link #burnDuration} is unused here (26.3
 * resolves burn times through a loot context and needs it).
 *
 * <p>MOD-498 — {@code FuelValues#burnDuration} is deprecated by NeoForge's patch only (in favour of
 * {@code ItemStack#getBurnTime}, a NeoForge addition vanilla does not declare). This class is compiled for
 * Fabric too, so the vanilla form is the only one both loaders have.
 */
public final class FurnaceFuel {

	private FurnaceFuel() {
	}

	/** Whether the stack is fuel at all, asked by a slot predicate that has a level. 26.2: the table. */
	public static boolean isFuel(Level level, ItemStack stack) {
		return level.fuelValues().isFuel(stack);
	}

	/**
	 * Whether a fuel slot should take the stack when the machine may have no level yet (client side, before
	 * placement). 26.2: burn values are per level, so without one stay permissive — the server re-validates.
	 */
	@SuppressWarnings("deprecation")
	public static boolean isFuelOrUnknown(@Nullable Level level, ItemStack stack) {
		return level == null || level.fuelValues().burnDuration(stack) > 0;
	}

	/** Burn time in ticks, or {@code 0} for anything that is not fuel. 26.2: the level's table. */
	@SuppressWarnings("deprecation")
	public static <T extends BlockEntity & Container> int burnDuration(ServerLevel level, T machine,
			ItemStack stack) {
		return level.fuelValues().burnDuration(stack);
	}
}
