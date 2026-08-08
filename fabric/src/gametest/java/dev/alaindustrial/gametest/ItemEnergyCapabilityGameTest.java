package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import team.reborn.energy.api.EnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 functional suite for the Fabric half of the cross-mod item energy bridge (MOD-084, suite
 * TC-XMOD-001): the mod's powered items exposed through Team Reborn's {@code EnergyStorage.ITEM}.
 *
 * <p>Unlike the other item suites these bodies are NOT loader-neutral and do not live in
 * {@code common/.../gametest}: the whole point of the test is the loader's own capability API, which
 * common code must not import. The NeoForge lane runs the mirrored assertions against
 * {@code Capabilities.Energy.ITEM} in {@code NeoForgeGameTests}.
 *
 * <p>These stand in for a foreign charger: a third-party mod calling the capability is doing exactly what
 * these tests do.
 */
public class ItemEnergyCapabilityGameTest {

	/** The pouch's per-operation input ceiling (LV tier: 32 EU) — what a foreign charger may push per call. */
	private static final long POUCH_INPUT_RATE = 32L;

	/** Resolve the Team Reborn energy storage of the item the player holds in inventory slot 0. */
	private static EnergyStorage storageInSlotZero(Player player) {
		return ContainerItemContext.ofPlayerSlot(player, PlayerInventoryStorage.of(player).getSlot(0))
				.find(EnergyStorage.ITEM);
	}

	/**
	 * @implements TC-XMOD-001-FUN01 — a foreign charger fills a carried pouch through EnergyStorage.ITEM,
	 *             clamped to the item's own input rate (not the charger's appetite).
	 */
	@GameTest
	public void tcXmod001Fun01_foreignChargerFillsPouch(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(ModItems.BATTERY_POUCH));

		EnergyStorage storage = storageInSlotZero(player);
		if (storage == null) {
			helper.fail("the pouch must expose EnergyStorage.ITEM so other mods' chargers can fill it");
			return;
		}
		long inserted;
		try (Transaction transaction = Transaction.openOuter()) {
			inserted = storage.insert(1000L, transaction);
			transaction.commit();
		}
		if (inserted != POUCH_INPUT_RATE) {
			helper.fail("a charger offering 1000 EU must be clamped to the pouch's input rate ("
					+ POUCH_INPUT_RATE + "), inserted " + inserted);
		}
		// The exchange swaps the stack in the slot, so the charge must be read back from the slot.
		if (ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("the inserted EU must land in the pouch's own pouch_energy component");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-XMOD-001-FUN02 — a worn pack charges another mod's powered item, resolved through the
	 *             loader's item energy lookup ({@link ForeignEnergyItemMod} plays the foreign mod).
	 */
	@GameTest
	public void tcXmod001Fun02_packChargesForeignItem(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = new ItemStack(ModItems.ENERGY_PACK);
		ItemEnergy.set(pack, Config.energyPackBuffer);
		player.getInventory().setItem(0, new ItemStack(Items.APPLE));

		long moved = EnergyPackItem.chargeStep(pack, player);

		// The stand-in's own limits are far above this, so the pack's per-step budget is what caps it.
		long budget = Config.energyPackOutputRate * 20L;
		if (moved != budget) {
			helper.fail("the pack must hand its whole step budget (" + budget
					+ " EU) to a foreign energy item, moved " + moved);
		}
		if (ForeignEnergyItemMod.storedEnergy(player.getInventory().getItem(0)) != budget) {
			helper.fail("the EU must land in the foreign item's own energy storage");
		}
		if (ItemEnergy.get(pack) != Config.energyPackBuffer - budget) {
			helper.fail("the pack must be debited exactly what the foreign item took");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-XMOD-001-NEG02 — a foreign item listed in alaindustrial:no_auto_charge is skipped.
	 *             This is the anti-ping-pong denylist doing what `instanceof EnergyPackItem` never could:
	 *             excluding another mod's charger.
	 */
	@GameTest
	public void tcXmod001Neg02_taggedForeignItemIsSkipped(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = new ItemStack(ModItems.ENERGY_PACK);
		ItemEnergy.set(pack, Config.energyPackBuffer);
		// Golden apple: same foreign energy storage as the apple, but the test datapack lists it in
		// alaindustrial:no_auto_charge — standing in for a foreign charger a pack owner wants excluded.
		player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE));

		long moved = EnergyPackItem.chargeStep(pack, player);

		if (moved != 0L || ForeignEnergyItemMod.storedEnergy(player.getInventory().getItem(0)) != 0L) {
			helper.fail("a foreign item in no_auto_charge must not be charged, moved " + moved);
		}
		if (ItemEnergy.get(pack) != Config.energyPackBuffer) {
			helper.fail("a pack with nothing to charge must not lose EU");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-XMOD-001-REG01 — EVERY powered item of the mod is reachable through
	 *             {@code EnergyStorage.ITEM} and really takes a first insert into its own
	 *             {@code pouch_energy} (MOD-372). The per-operation ceiling itself is pinned by the
	 *             independent literal in FUN01; here the amount is derived, see
	 *             {@link PoweredItemCatalog#expectedFirstInsert}.
	 *
	 *             <p>The bridge is wired by naming items in a literal list inside
	 *             {@code StackAsEnergyStorage.register()}, and that list fell behind the item roster
	 *             twice — chainsaw, shovel, hoe and saber shipped with specs promising foreign charging
	 *             and nothing behind it. The expected set therefore comes from
	 *             {@link PoweredItemCatalog} (registry ∩ {@code ItemEnergy.capacity > 0}), never from the
	 *             list under test: a guard reading the same literal could only ever confirm itself.
	 */
	@GameTest
	public void tcXmod001Reg01_everyPoweredItemExposesCapability(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		List<Item> powered = PoweredItemCatalog.poweredItems();
		if (powered.size() < PoweredItemCatalog.MIN_POWERED_ITEMS) {
			helper.fail("the powered-item roster collapsed to " + powered.size() + " item(s) — expected at"
					+ " least " + PoweredItemCatalog.MIN_POWERED_ITEMS + "; a guard over a shrunken set"
					+ " proves nothing. Found: " + PoweredItemCatalog.idsOf(powered));
			return;
		}
		List<String> broken = new ArrayList<>();
		for (Item item : powered) {
			player.getInventory().setItem(0, new ItemStack(item));
			EnergyStorage storage = storageInSlotZero(player);
			if (storage == null) {
				broken.add(PoweredItemCatalog.idOf(item) + " (no EnergyStorage.ITEM)");
				continue;
			}
			long expected = PoweredItemCatalog.expectedFirstInsert(item);
			if (expected <= 0L) {
				// An item with a buffer but no input rate is chargeable by nothing at all. Asserting
				// "inserted 0 == expected 0" below would wave it through — see expectedFirstInsert.
				broken.add(PoweredItemCatalog.idOf(item) + " (input rate 0 — nothing can charge it)");
				continue;
			}
			long inserted;
			try (Transaction transaction = Transaction.openOuter()) {
				inserted = storage.insert(expected * 100L, transaction);
				transaction.commit();
			}
			long charge = ItemEnergy.get(player.getInventory().getItem(0));
			if (inserted != expected || charge != expected) {
				broken.add(PoweredItemCatalog.idOf(item) + " (inserted " + inserted + ", charge " + charge
						+ ", expected " + expected + ")");
			}
		}
		if (!broken.isEmpty()) {
			helper.fail("every powered item must be chargeable by a foreign mod (MOD-084) — broken: "
					+ String.join(", ", broken));
		}
		helper.succeed();
	}

	/**
	 * @implements TC-XMOD-001-NEG01 — the capability is insert-only: a foreign machine cannot drain the
	 *             mod's items (the anti-ping-pong rule of MOD-065 applied to foreign chargers).
	 */
	@GameTest
	public void tcXmod001Neg01_foreignMachineCannotDrain(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pouch = new ItemStack(ModItems.BATTERY_POUCH);
		ItemEnergy.set(pouch, POUCH_INPUT_RATE);
		player.getInventory().setItem(0, pouch);

		EnergyStorage storage = storageInSlotZero(player);
		if (storage == null) {
			helper.fail("the pouch must expose EnergyStorage.ITEM");
			return;
		}
		if (storage.supportsExtraction()) {
			helper.fail("the item energy capability must advertise itself as insert-only");
		}
		long extracted;
		try (Transaction transaction = Transaction.openOuter()) {
			extracted = storage.extract(POUCH_INPUT_RATE, transaction);
			transaction.commit();
		}
		if (extracted != 0L || ItemEnergy.get(player.getInventory().getItem(0)) != POUCH_INPUT_RATE) {
			helper.fail("a foreign machine must not be able to drain a pouch, extracted " + extracted);
		}
		helper.succeed();
	}
}
