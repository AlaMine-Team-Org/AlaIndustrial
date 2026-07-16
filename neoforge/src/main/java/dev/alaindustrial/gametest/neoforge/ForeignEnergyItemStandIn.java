package dev.alaindustrial.gametest.neoforge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/**
 * Stands in for a third-party energy mod on the NeoForge lane (MOD-084), so the "the pack charges other
 * mods' items" half of the bridge is actually exercised. At the time of writing no energy mod exists for
 * MC 26.2 at all, so there is nothing to test against by hand.
 *
 * <p>Unlike the Fabric twin ({@code ForeignEnergyItemMod}), this cannot live in a separate test mod: on
 * NeoForge the gametests run inside the main mod, and an item capability can only be registered from
 * {@link RegisterCapabilitiesEvent} at startup. So the registration sits in the production entrypoint
 * behind a {@link FMLEnvironment#isProduction()} gate — the same dev-only pattern the {@code /ala demo}
 * stand uses. Nothing here is reachable in a shipped jar.
 *
 * <p>The charge is kept in the vanilla {@link DataComponents#DAMAGE} component through NeoForge's own
 * {@link ItemAccessEnergyHandler}. That handler demands a {@code DataComponentType<Integer>} (the very
 * mismatch that stopped us using it for our own {@code Long}-backed items), and {@code DAMAGE} is a
 * ready-made Integer component — using it avoids registering a throw-away component into the real
 * registry just to run a test. What matters for the test is only that this is a component the mod under
 * test does not know about, exactly like a real foreign mod's.
 */
public final class ForeignEnergyItemStandIn {
	private ForeignEnergyItemStandIn() {
	}

	/** Buffer of the stand-in — far above the pack's per-step budget, so the pack is what limits transfer. */
	public static final int CAPACITY = 100_000;

	/** Per-operation limit of the stand-in — likewise above the pack's budget. */
	public static final int MAX_TRANSFER = 100_000;

	/** Read the stand-in's charge the way this fake foreign mod stores it. */
	public static long storedEnergy(ItemStack stack) {
		return stack.getOrDefault(DataComponents.DAMAGE, 0);
	}

	/** Register the stand-in capability — dev/gametest environments only. */
	public static void register(RegisterCapabilitiesEvent event) {
		if (FMLEnvironment.isProduction()) {
			return;
		}
		event.registerItem(Capabilities.Energy.ITEM,
				(stack, access) -> new ItemAccessEnergyHandler(access, DataComponents.DAMAGE, CAPACITY,
						MAX_TRANSFER),
				Items.APPLE);
	}
}
