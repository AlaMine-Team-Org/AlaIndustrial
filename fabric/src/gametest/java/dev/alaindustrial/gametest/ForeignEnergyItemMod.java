package dev.alaindustrial.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Items;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyItem;

/**
 * Stands in for a third-party energy mod (MOD-084), so the "the pack charges other mods' items" half of
 * the bridge is actually exercised. At the time of writing no energy mod exists for MC 26.2 at all — no
 * Tech Reborn, no Powah, no Modern Industrialization — so there is nothing to test against by hand; this
 * is the closest honest substitute.
 *
 * <p>And it is a close one: this lives in the gametest source set, which is its own mod
 * ({@code alaindustrial-gametest}, see its {@code fabric.mod.json}) and never ships in the release jar.
 * So a genuinely foreign mod registers a genuinely foreign energy item, and our bridge resolves it
 * through the same {@code EnergyStorage.ITEM} lookup a real mod would use — the mod code under test
 * cannot tell the difference, because the lookup does not record who registered a provider.
 *
 * <p>The storage is Team Reborn's own {@link SimpleEnergyItem#createStorage}, which keeps the charge in
 * Team Reborn's {@code ENERGY_COMPONENT} — exactly what a real Fabric energy mod does, and deliberately
 * NOT the mod's own {@code pouch_energy} component. The capacity and per-operation limits are set far
 * above the pack's step budget so a test asserting "the whole budget moved" is not silently measuring
 * this stand-in's limits instead of the pack's.
 *
 * <ul>
 * <li>{@link Items#APPLE} — a plain foreign energy item; the pack must charge it.</li>
 * <li>{@link Items#GOLDEN_APPLE} — same, but listed in {@code alaindustrial:no_auto_charge} by this
 *     mod's test datapack; the pack must skip it. This is what proves the denylist works on a
 *     <i>foreign</i> item, which is the whole reason the rule moved from {@code instanceof} to a tag.</li>
 * </ul>
 */
public class ForeignEnergyItemMod implements ModInitializer {

	/** Buffer of the stand-in item — far above the pack's per-step budget, so the pack is what limits transfer. */
	public static final long CAPACITY = 100_000L;

	/** Per-operation limit of the stand-in — likewise above the pack's budget. */
	public static final long MAX_TRANSFER = 100_000L;

	/** Read the stand-in's charge the way a foreign mod stores it (Team Reborn's own component). */
	public static long storedEnergy(net.minecraft.world.item.ItemStack stack) {
		return SimpleEnergyItem.getStoredEnergyUnchecked(stack);
	}

	@Override
	public void onInitialize() {
		EnergyStorage.ITEM.registerForItems(
				(stack, context) -> SimpleEnergyItem.createStorage(context, CAPACITY, MAX_TRANSFER, MAX_TRANSFER),
				Items.APPLE, Items.GOLDEN_APPLE);
	}
}
