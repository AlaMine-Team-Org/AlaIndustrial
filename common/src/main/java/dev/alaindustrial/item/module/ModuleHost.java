package dev.alaindustrial.item.module;

import net.minecraft.world.item.ItemStack;

/**
 * An item that has module slots of its own (MOD-592) — the electromagnet today, drills, the jetpack and
 * armour later. The modules live on the host's stack in {@link ItemModules}; a host only answers two
 * questions, and every slot rule on every screen goes through these two answers.
 */
public interface ModuleHost {

	/** How many module slots this host stack has. */
	int moduleSlots(ItemStack host);

	/**
	 * Whether {@code module} may go into slot {@code slot} of {@code host}, given what is fitted now.
	 * Called with the slot's current occupant still in place, so a host that allows one module of a
	 * kind must not count the slot being replaced.
	 */
	boolean acceptsModule(ItemStack host, ItemModules fitted, int slot, ItemStack module);
}
