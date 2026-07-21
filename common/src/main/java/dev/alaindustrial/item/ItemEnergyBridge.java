package dev.alaindustrial.item;

import net.minecraft.world.entity.player.Player;

/**
 * Platform-neutral bridge to <i>other mods'</i> item energy (MOD-084): pushes EU into the item held in
 * one of a player's inventory slots through whatever energy API the running loader speaks — Fabric
 * {@code EnergyStorage.ITEM} (Team Reborn Energy) versus NeoForge {@code Capabilities.Energy.ITEM}. This
 * is the item-side counterpart of {@link dev.alaindustrial.core.energy.EnergyLookup}, and it lets the Energy
 * Pack charge foreign tools without common code importing either loader's capability types.
 *
 * <p>Only insertion is bridged. The mod's own powered items are handled directly through
 * {@link ItemEnergy} — cheaper, and it keeps the pack's behaviour toward mod items identical to before
 * MOD-084. EU is passed at the block-side rate of 1 EU = 1 foreign energy unit
 * ({@link dev.alaindustrial.core.energy.EnergyUnits#UNITS_PER_EU}), so the two energy worlds agree.
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(ItemEnergyBridge)}; common code reaches it through {@link #get()}.
 */
public interface ItemEnergyBridge {

	/**
	 * Insert up to {@code maxEu} EU into the foreign energy item in {@code slot} of the player's
	 * inventory (slot indices are vanilla {@link net.minecraft.world.entity.player.Inventory} indices),
	 * and return how much it accepted.
	 *
	 * <p>Returns 0 when the slot holds nothing, holds an item with no energy capability, or the item is
	 * full. Implementations write the charge themselves — the caller only debits the source for the
	 * returned amount.
	 */
	long chargeSlot(Player player, int slot, long maxEu);

	/** Bridge that moves nothing — used before a loader installs its own, and in loader-free unit tests. */
	ItemEnergyBridge NONE = (player, slot, maxEu) -> 0L;

	// --- service locator (installed by the loader entrypoint) ---

	ItemEnergyBridge[] INSTANCE = new ItemEnergyBridge[1];

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(ItemEnergyBridge impl) {
		INSTANCE[0] = impl;
	}

	/**
	 * The installed loader implementation, or {@link #NONE} if none is installed yet. Unlike
	 * {@link dev.alaindustrial.core.energy.EnergyLookup#get()} this does not throw: a missing bridge only means
	 * "no cross-mod charging", which is exactly what {@link #NONE} does — a common-side unit test must not
	 * have to stand up a loader to tick a pack.
	 */
	static ItemEnergyBridge get() {
		ItemEnergyBridge impl = INSTANCE[0];
		return impl == null ? NONE : impl;
	}
}
