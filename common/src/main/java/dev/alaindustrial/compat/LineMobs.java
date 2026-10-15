package dev.alaindustrial.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * Vanilla mob classes whose name changed between the Minecraft lines (MOD-818, ADR-036). Every line has a twin of
 * this class with the same signatures; only the bodies differ.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where the enderman is {@code EnderMan}.
 */
public final class LineMobs {

	private LineMobs() {
	}

	/** Whether {@code entity} is an enderman. */
	public static boolean isEnderman(Entity entity) {
		return entity instanceof EnderMan;
	}
}
