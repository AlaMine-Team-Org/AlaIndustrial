package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.registry.neoforge.ModItemsNeoForge;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * L2 functional bodies for the NeoForge half of the cross-mod item energy bridge (MOD-084, suite
 * TC-XMOD-001): the mod's powered items exposed through {@code Capabilities.Energy.ITEM}.
 *
 * <p>These bodies are deliberately NOT in {@code common/.../gametest} like the other item suites: they
 * exercise the loader's own capability API, which common code must not import. The assertions mirror the
 * Fabric lane's {@code ItemEnergyCapabilityGameTest} one-for-one, so both loaders are held to the same
 * contract — a foreign charger fills our items at their own input rate, and cannot drain them.
 */
public final class ItemEnergyCapabilityScenarios {
	private ItemEnergyCapabilityScenarios() {
	}

	/** The pouch's per-operation input ceiling (LV tier: 32 EU) — what a foreign charger may push per call. */
	private static final int POUCH_INPUT_RATE = 32;

	/** Resolve the energy handler of the item the player holds in inventory slot 0. */
	private static EnergyHandler handlerInSlotZero(Player player) {
		return ItemAccess.forPlayerSlot(player, 0).getCapability(Capabilities.Energy.ITEM);
	}

	/**
	 * Mirrors TC-XMOD-001-FUN01 on the NeoForge lane — a foreign charger fills a carried pouch through
	 * the item energy capability, clamped to the item's own input rate (not the charger's appetite).
	 *
	 * <p>The {@code @implements} tag that claims the case id lives on the Fabric twin: one tag per case
	 * keeps {@code docs/testing/TRACEABILITY.md} free of duplicate rows, the same way every other
	 * NeoForge-lane body inherits its id from the shared scenario it delegates to.
	 */
	public static void fun01ForeignChargerFillsPouch(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(ModItemsNeoForge.BATTERY_POUCH.get()));

		EnergyHandler handler = handlerInSlotZero(player);
		if (handler == null) {
			helper.fail("the pouch must expose Capabilities.Energy.ITEM so other mods' chargers can fill it");
			return;
		}
		int inserted;
		try (Transaction transaction = Transaction.openRoot()) {
			inserted = handler.insert(1000, transaction);
			transaction.commit();
		}
		if (inserted != POUCH_INPUT_RATE) {
			helper.fail("a charger offering 1000 FE must be clamped to the pouch's input rate ("
					+ POUCH_INPUT_RATE + "), inserted " + inserted);
		}
		// The exchange swaps the stack in the slot, so the charge must be read back from the slot.
		if (ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("the inserted FE must land in the pouch's own pouch_energy component as EU (1:1)");
		}
		helper.succeed();
	}

	/**
	 * Mirrors TC-XMOD-001-FUN02 on the NeoForge lane — a worn pack charges another mod's powered item,
	 * resolved through {@code Capabilities.Energy.ITEM} ({@link ForeignEnergyItemStandIn} plays the
	 * foreign mod). The case id is claimed by the Fabric twin; see {@link #fun01ForeignChargerFillsPouch}.
	 */
	public static void fun02PackChargesForeignItem(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = new ItemStack(ModItemsNeoForge.ENERGY_PACK.get());
		ItemEnergy.set(pack, Config.energyPackBuffer);
		player.getInventory().setItem(0, new ItemStack(Items.APPLE));

		long moved = EnergyPackItem.chargeStep(pack, player);

		// The stand-in's own limits are far above this, so the pack's per-step budget is what caps it.
		long budget = Config.energyPackOutputRate * 20L;
		if (moved != budget) {
			helper.fail("the pack must hand its whole step budget (" + budget
					+ " EU) to a foreign energy item, moved " + moved);
		}
		if (ForeignEnergyItemStandIn.storedEnergy(player.getInventory().getItem(0)) != budget) {
			helper.fail("the EU must land in the foreign item's own energy storage");
		}
		if (ItemEnergy.get(pack) != Config.energyPackBuffer - budget) {
			helper.fail("the pack must be debited exactly what the foreign item took");
		}
		helper.succeed();
	}

	/**
	 * Mirrors TC-XMOD-001-NEG01 on the NeoForge lane — the capability is insert-only: a foreign machine
	 * cannot drain the mod's items (the anti-ping-pong rule of MOD-065 applied to foreign chargers). The
	 * case id is claimed by the Fabric twin; see {@link #fun01ForeignChargerFillsPouch}.
	 */
	public static void neg01ForeignMachineCannotDrain(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pouch = new ItemStack(ModItemsNeoForge.BATTERY_POUCH.get());
		ItemEnergy.set(pouch, POUCH_INPUT_RATE);
		player.getInventory().setItem(0, pouch);

		EnergyHandler handler = handlerInSlotZero(player);
		if (handler == null) {
			helper.fail("the pouch must expose Capabilities.Energy.ITEM");
			return;
		}
		int extracted;
		try (Transaction transaction = Transaction.openRoot()) {
			extracted = handler.extract(POUCH_INPUT_RATE, transaction);
			transaction.commit();
		}
		if (extracted != 0 || ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("a foreign machine must not be able to drain a pouch, extracted " + extracted);
		}
		helper.succeed();
	}
}
