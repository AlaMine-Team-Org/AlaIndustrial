package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import net.minecraft.world.item.Item;

/**
 * A capacity card for the monitor core (MOD-480).
 *
 * <p>It stores nothing. Fitting one raises how many DIFFERENT item types the wall may watch at once
 * — the right to track a type, never a place to keep one, which is the line this system does not
 * cross.
 */
public class CapacityCardItem extends Item {

	public CapacityCardItem(Properties properties) {
		super(properties);
	}

	/** How many watched types this card adds to a core's allowance. */
	public int trackedTypes() {
		return Math.max(0, Config.monitorCardTrackedTypes);
	}
}
