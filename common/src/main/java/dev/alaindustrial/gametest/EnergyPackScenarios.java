package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Loader-neutral gametest bodies for the Energy Pack (MOD-065, suite TC-PACK-001). Same pattern as
 * {@link PouchScenarios}: plain {@code GameTestHelper} bodies wrapped by the Fabric
 * {@code EnergyPackGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests} — both loaders exercise the SAME transfer logic.
 *
 * <p>The transfer is driven through {@link EnergyPackItem#chargeStep} rather than by waiting for the
 * once-a-second equipment tick, so every case is deterministic. Numbers come from {@link Config}
 * (energyPackBuffer=20000, energyPackOutputRate=32) — the balance source of truth.
 */
public final class EnergyPackScenarios {

	private EnergyPackScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);

	/** EU the worn pack hands out in one step: a full second's worth of its output rate. */
	private static long step() {
		return (long) Config.energyPackOutputRate * 20L;
	}

	private static ItemStack pack(long eu) {
		ItemStack stack = new ItemStack(ModContent.ENERGY_PACK.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static ItemStack pouch(long eu) {
		ItemStack stack = new ItemStack(ModContent.BATTERY_POUCH.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static BatteryBoxBlockEntity placeBox(GameTestHelper helper) {
		helper.setBlock(BOX, ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity be = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (be == null) {
			helper.fail("battery_box block entity missing");
		}
		return be;
	}

	private static void tickBox(GameTestHelper helper, BatteryBoxBlockEntity be, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.serverTick(helper.getLevel(), be.getBlockPos(),
					helper.getLevel().getBlockState(be.getBlockPos()));
		}
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/** FUN01: a worn pack tops up a pouch carried in the inventory, one output batch per step. */
	public static void fun01WornPackChargesPouch(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(Config.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.getInventory().setItem(0, pouch);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (moved != step()) {
			helper.fail("one step must move a full batch (" + step() + " EU), got " + moved);
		}
		if (ItemEnergy.get(pouch) != step()) {
			helper.fail("the pouch must gain exactly what the pack sent, got " + ItemEnergy.get(pouch));
		}
		if (ItemEnergy.get(pack) != Config.energyPackBuffer - step()) {
			helper.fail("the pack must pay exactly what it sent, left " + ItemEnergy.get(pack));
		}
		helper.succeed();
	}

	/** FUN02: the offhand is not part of the main inventory list — it must still be served. */
	public static void fun02ChargesOffhand(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(Config.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.setItemSlot(EquipmentSlot.OFFHAND, pouch);

		EnergyPackItem.chargeStep(pack, player);
		if (ItemEnergy.get(player.getItemBySlot(EquipmentSlot.OFFHAND)) != step()) {
			helper.fail("a pouch in the offhand must be charged too");
		}
		helper.succeed();
	}

	/** FUN03: the batch is split across several consumers in slot order until it runs out. */
	public static void fun03BudgetSplitAcrossConsumers(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(Config.energyPackBuffer);
		// First pouch has room for a quarter of the batch; the rest of the budget must flow onwards.
		long firstRoom = step() / 4;
		ItemStack first = pouch(Config.lvPouchBuffer - firstRoom);
		ItemStack second = pouch(0);
		player.getInventory().setItem(0, first);
		player.getInventory().setItem(1, second);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (ItemEnergy.get(first) != Config.lvPouchBuffer) {
			helper.fail("the first pouch must be filled to capacity");
		}
		if (ItemEnergy.get(second) != step() - firstRoom) {
			helper.fail("the leftover budget must reach the second pouch, got " + ItemEnergy.get(second));
		}
		if (moved != step()) {
			helper.fail("the whole batch must be spent, moved " + moved);
		}
		helper.succeed();
	}

	/** FUN04: the pack is charged in the Battery Box slot, capped by its own intake rate. */
	public static void fun04ChargeInBatteryBox(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity();
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, pack(0));

		tickBox(helper, box, 1);
		long expected = Math.min(EnergyTier.LV.maxVoltage(), Config.energyPackInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, pack intake) = " + expected + " EU, got " + gained);
		}
		tickBox(helper, box, 9);
		if (ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT)) != expected * 10) {
			helper.fail("ten ticks must move ten times the per-tick rate");
		}
		helper.succeed();
	}

	/** FUN05: the pack never charges another pack — that loop would drain the wearer for nothing. */
	public static void fun05PackDoesNotChargePack(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack worn = pack(Config.energyPackBuffer);
		ItemStack carried = pack(0);
		player.getInventory().setItem(0, carried);

		long moved = EnergyPackItem.chargeStep(worn, player);
		if (moved != 0 || ItemEnergy.get(carried) != 0) {
			helper.fail("a pack must not charge another pack");
		}
		if (ItemEnergy.get(worn) != Config.energyPackBuffer) {
			helper.fail("a pack with nothing to charge must not lose EU");
		}
		helper.succeed();
	}

	// ── NEG — nothing happens when it should not ─────────────────────────────────────────────────

	/** NEG01: an empty pack, a full pouch or a plain item — no transfer, no component writes. */
	public static void neg01NoTransferWhenNothingToDo(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		// Empty pack: nothing to give.
		ItemStack emptyPack = pack(0);
		player.getInventory().setItem(0, pouch(0));
		if (EnergyPackItem.chargeStep(emptyPack, player) != 0) {
			helper.fail("an empty pack must move nothing");
		}

		// Full pouch: no room, so the pack must not spend anything.
		ItemStack pack = pack(Config.energyPackBuffer);
		ItemStack full = pouch(Config.lvPouchBuffer);
		player.getInventory().setItem(0, full);
		if (EnergyPackItem.chargeStep(pack, player) != 0 || ItemEnergy.get(pack) != Config.energyPackBuffer) {
			helper.fail("a full pouch must not cost the pack any EU");
		}

		// A non-powered item has no buffer at all.
		player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		if (EnergyPackItem.chargeStep(pack, player) != 0) {
			helper.fail("a plain item must not receive EU");
		}
		helper.succeed();
	}

	/** NEG02: the pack gives only what it has — the last step drains it to exactly 0. */
	public static void neg02PackFloorsAtZero(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long left = step() / 2;
		ItemStack pack = pack(left);
		ItemStack pouch = pouch(0);
		player.getInventory().setItem(0, pouch);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (moved != left || ItemEnergy.get(pack) != 0 || ItemEnergy.get(pouch) != left) {
			helper.fail("a nearly empty pack must hand over exactly its remaining EU and stop at 0");
		}
		if (EnergyPackItem.chargeStep(pack, player) != 0) {
			helper.fail("a drained pack must move nothing");
		}
		helper.succeed();
	}

	/**
	 * NEG03: the charge slot filter — on BOTH sides. {@code mayPlace} is the client's prediction and
	 * {@code canPlaceItem} is the server's word; they must agree, or the item flickers in and out of
	 * the slot. Hoppers stay locked out of the slot entirely (GUI-only), pack included.
	 */
	public static void neg03MenuSlotAcceptsPack(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(pack(0)) || !slot.mayPlace(pouch(0))) {
			helper.fail("the charge slot must accept every powered item (client prediction)");
		}
		if (slot.mayPlace(new ItemStack(Items.COBBLESTONE))) {
			helper.fail("the charge slot must refuse items without an EU buffer (client prediction)");
		}
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, pack(0))
				|| box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, new ItemStack(Items.COBBLESTONE))) {
			helper.fail("the server-side filter must match the menu's");
		}
		for (Direction side : Direction.values()) {
			if (box.canPlaceItemThroughFace(BatteryBoxBlockEntity.CHARGE_SLOT, pack(0), side)) {
				helper.fail("hoppers must not be able to push a pack into the charge slot from " + side);
			}
		}
		helper.succeed();
	}

	/** NEG04: a pack in the offhand is not a consumer either — the anti-loop filter covers both passes. */
	public static void neg04PackInOffhandNotCharged(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack worn = pack(Config.energyPackBuffer);
		ItemStack carried = pack(0);
		player.setItemSlot(EquipmentSlot.OFFHAND, carried);

		if (EnergyPackItem.chargeStep(worn, player) != 0 || ItemEnergy.get(carried) != 0) {
			helper.fail("a pack in the offhand must not be charged by the worn pack");
		}
		helper.succeed();
	}

	// ── PER — persistence ────────────────────────────────────────────────────────────────────────

	/**
	 * FUN06: the worn look follows the charge — a charged pack points at the lit asset, a drained one
	 * falls back to the default (dark) asset. This is what the player sees on their back, and it is
	 * driven purely by the EQUIPPABLE component, so it is worth pinning down.
	 */
	public static void fun06WornAssetFollowsCharge(GameTestHelper helper) {
		ItemStack pack = pack(0);
		if (assetOf(pack) != EnergyPackItem.ENERGY_PACK_OFF_ASSET) {
			helper.fail("an empty pack must wear the drained asset");
		}
		ItemEnergy.set(pack, 500);
		if (assetOf(pack) != EnergyPackItem.ENERGY_PACK_ASSET) {
			helper.fail("a charged pack must wear the lit asset");
		}
		ItemEnergy.set(pack, 0);
		if (assetOf(pack) != EnergyPackItem.ENERGY_PACK_OFF_ASSET) {
			helper.fail("a drained pack must go back to the drained asset");
		}
		// It must still be wearable: the equippable component is swapped, never dropped.
		if (!pack.has(DataComponents.EQUIPPABLE)) {
			helper.fail("a drained pack must stay equippable");
		}
		helper.succeed();
	}

	/** The worn asset the stack currently points at, or null if it somehow lost its equippable. */
	private static ResourceKey<EquipmentAsset> assetOf(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null ? null : equippable.assetId().orElse(null);
	}

	/**
	 * FUN07: the real tick path, end to end. Every other case drives {@link EnergyPackItem#chargeStep}
	 * directly, which would keep passing even if the equipment tick never reached the pack at all —
	 * i.e. if the feature were completely dead in game. So: put the pack on the player's chest, run
	 * the item's own {@code inventoryTick} across a full second, and check the pouch was fed exactly
	 * one batch — no more (the {@code % 20} gate holds) and no less (the CHEST gate lets it through).
	 */
	public static void fun07InventoryTickDrivesTransfer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(Config.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.setItemSlot(EquipmentSlot.CHEST, pack);
		player.getInventory().setItem(0, pouch);

		// A pack that is NOT worn must stay inert no matter how often it is ticked — the slot gate
		// returns before the clock is even consulted, so this needs no real ticks.
		ItemStack carried = pack(Config.energyPackBuffer);
		ItemStack idle = pouch(0);
		player.getInventory().setItem(1, idle);
		for (int i = 0; i < 40; i++) {
			carried.getItem().inventoryTick(carried, level, player, EquipmentSlot.MAINHAND);
		}
		if (ItemEnergy.get(idle) != 0 || ItemEnergy.get(carried) != Config.energyPackBuffer) {
			helper.fail("a pack that is not worn must transfer nothing");
		}

		// The worn one, driven across 20 REAL ticks of the level: the once-a-second gate reads the game
		// time, so the clock has to actually advance. Any 20 consecutive ticks contain exactly one time
		// divisible by 20 — so exactly one batch must land, no more and no less.
		helper.startSequence()
				.thenExecuteFor(20, () ->
						pack.getItem().inventoryTick(pack, level, player, EquipmentSlot.CHEST))
				.thenExecute(() -> {
					long moved = ItemEnergy.get(pouch);
					if (moved != step()) {
						helper.fail("a worn pack must feed the pouch exactly one batch (" + step()
								+ " EU) per second through inventoryTick, moved " + moved);
					}
					if (ItemEnergy.get(pack) != Config.energyPackBuffer - step()) {
						helper.fail("the pack must pay exactly what the pouch received");
					}
				})
				.thenSucceed();
	}

	/**
	 * FUN08: a pack that arrives already charged without ever passing through {@link ItemEnergy#set}
	 * — /give with a pouch_energy component, a loot table, a datapack recipe — still ends up wearing
	 * the charged look. Without the self-heal in {@code inventoryTick} it would be worn with the dead
	 * texture indefinitely: a charged pack with nothing to charge writes nothing, so nothing would
	 * ever correct it.
	 */
	public static void fun08ChargedByComponentFixesItsLook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// Bypass ItemEnergy.set entirely — exactly what /give ...[pouch_energy=20000] produces.
		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		pack.set(ModDataComponents.POUCH_ENERGY.get(), (long) Config.energyPackBuffer);
		if (assetOf(pack) != EnergyPackItem.ENERGY_PACK_OFF_ASSET) {
			helper.fail("precondition: a component-only charge must leave the default (drained) asset");
		}
		player.setItemSlot(EquipmentSlot.CHEST, pack);

		pack.getItem().inventoryTick(pack, level, player, EquipmentSlot.CHEST);
		if (assetOf(pack) != EnergyPackItem.ENERGY_PACK_ASSET) {
			helper.fail("a worn pack charged straight through the component must correct its worn look");
		}
		helper.succeed();
	}

	/** PER01: charge survives a copy of the stack (the component is what persists, not the instance). */
	public static void per01ChargeRoundTrip(GameTestHelper helper) {
		ItemStack pack = pack(1234);
		ItemStack copy = pack.copy();
		if (ItemEnergy.get(copy) != 1234) {
			helper.fail("a copied pack must keep its charge");
		}
		// 0 EU removes the component: a drained pack and a freshly crafted one are component-identical.
		ItemEnergy.set(pack, 0);
		if (!ItemStack.matches(pack, new ItemStack(ModContent.ENERGY_PACK.get()))) {
			helper.fail("a drained pack must be component-identical to a fresh one");
		}
		// The buffer clamps: a pack cannot be pushed past its capacity.
		ItemEnergy.set(pack, Config.energyPackBuffer + 5000);
		if (ItemEnergy.get(pack) != Config.energyPackBuffer) {
			helper.fail("the pack buffer must clamp at capacity");
		}
		helper.succeed();
	}
}
