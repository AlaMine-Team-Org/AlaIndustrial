package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
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

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EnergyPackScenarios::fun01WornPackChargesPouch, "pack_worn_charges_pouch")
						.fabricId("EnergyPackGameTest", "tcPack001Fun01_wornPackChargesPouch").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun02ChargesOffhand, "pack_charges_offhand")
						.fabricId("EnergyPackGameTest", "tcPack001Fun02_chargesOffhand").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun03BudgetSplitAcrossConsumers, "pack_budget_split")
						.fabricId("EnergyPackGameTest", "tcPack001Fun03_budgetSplitAcrossConsumers").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun04ChargeInBatteryBox, "pack_charge_in_battery_box")
						.fabricId("EnergyPackGameTest", "tcPack001Fun04_chargeInBatteryBox").ticks(20, 80),
				RosterEntry.of(EnergyPackScenarios::fun05PackDoesNotChargePack, "pack_does_not_charge_pack")
						.fabricId("EnergyPackGameTest", "tcPack001Fun05_packDoesNotChargePack").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun06WornAssetFollowsCharge, "pack_worn_asset_follows_charge")
						.fabricId("EnergyPackGameTest", "tcPack001Fun06_wornAssetFollowsCharge").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun07InventoryTickDrivesTransfer, "pack_inventory_tick_transfers")
						.fabricId("EnergyPackGameTest", "tcPack001Fun07_inventoryTickDrivesTransfer").ticks(20, 60),
				RosterEntry.of(EnergyPackScenarios::fun08ChargedByComponentFixesItsLook,
								"pack_component_charge_fixes_look")
						.fabricId("EnergyPackGameTest", "tcPack001Fun08_chargedByComponentFixesItsLook").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::neg04PackInOffhandNotCharged, "pack_offhand_pack_not_charged")
						.fabricId("EnergyPackGameTest", "tcPack001Neg04_packInOffhandNotCharged").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::neg01NoTransferWhenNothingToDo, "pack_no_transfer_when_idle")
						.fabricId("EnergyPackGameTest", "tcPack001Neg01_noTransferWhenNothingToDo").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::neg02PackFloorsAtZero, "pack_floors_at_zero")
						.fabricId("EnergyPackGameTest", "tcPack001Neg02_packFloorsAtZero").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::neg03MenuSlotAcceptsPack, "pack_menu_slot_accepts_pack")
						.fabricId("EnergyPackGameTest", "tcPack001Neg03_menuSlotAcceptsPack").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun09CreativeKeepsCharge, "pack_creative_keeps_charge")
						.fabricId("EnergyPackGameTest", "tcPack001Fun09_creativeKeepsCharge").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::fun10ChargesCursorAndCraftGrid, "pack_charges_cursor_and_grid")
						.fabricId("EnergyPackGameTest", "tcPack001Fun10_chargesCursorAndCraftGrid").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::neg05DoesNotChargeOpenContainer, "pack_skips_open_container")
						.fabricId("EnergyPackGameTest", "tcPack001Neg05_doesNotChargeOpenContainer").ticks(20, 40),
				RosterEntry.of(EnergyPackScenarios::per01ChargeRoundTrip, "pack_charge_round_trip")
						.fabricId("EnergyPackGameTest", "tcPack001Per01_chargeRoundTrip").ticks(20, 40));

		private Roster() {}
	}

	private EnergyPackScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);

	/** EU the worn pack hands out in one step: a full second's worth of its output rate. */
	private static long step() {
		return (long) ToolConfig.energyPackOutputRate * 20L;
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

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/**
	 * FUN01: a worn pack tops up a pouch carried in the inventory, one output batch per step.
	 *
	 * @implements TC-PACK-001-FUN01 — a worn pack tops up a carried pouch by one output batch per
	 *     step (energyPackOutputRate × 20 EU), paying exactly what it sends.
	 */
	public static void fun01WornPackChargesPouch(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.getInventory().setItem(0, pouch);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (moved != step()) {
			helper.fail("one step must move a full batch (" + step() + " EU), got " + moved);
		}
		if (ItemEnergy.get(pouch) != step()) {
			helper.fail("the pouch must gain exactly what the pack sent, got " + ItemEnergy.get(pouch));
		}
		if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer - step()) {
			helper.fail("the pack must pay exactly what it sent, left " + ItemEnergy.get(pack));
		}
		helper.succeed();
	}

	/**
	 * FUN02: the offhand is not part of the main inventory list — it must still be served.
	 *
	 * @implements TC-PACK-001-FUN02 — the offhand is not part of the main inventory list in 26.2 and
	 *     is served by its own pass.
	 */
	public static void fun02ChargesOffhand(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.setItemSlot(EquipmentSlot.OFFHAND, pouch);

		EnergyPackItem.chargeStep(pack, player);
		if (ItemEnergy.get(player.getItemBySlot(EquipmentSlot.OFFHAND)) != step()) {
			helper.fail("a pouch in the offhand must be charged too");
		}
		helper.succeed();
	}

	/**
	 * FUN03: the batch is split across several consumers in slot order until it runs out.
	 *
	 * @implements TC-PACK-001-FUN03 — the batch is split across consumers in slot order: the first
	 *     pouch fills, the leftover budget flows on to the next.
	 */
	public static void fun03BudgetSplitAcrossConsumers(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		// First pouch has room for a quarter of the batch; the rest of the budget must flow onwards.
		long firstRoom = step() / 4;
		ItemStack first = pouch(ToolConfig.lvPouchBuffer - firstRoom);
		ItemStack second = pouch(0);
		player.getInventory().setItem(0, first);
		player.getInventory().setItem(1, second);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (ItemEnergy.get(first) != ToolConfig.lvPouchBuffer) {
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

	/**
	 * FUN04: the pack is charged in the Battery Box slot, capped by its own intake rate.
	 *
	 * @implements TC-PACK-001-FUN04 — the pack charges in the Battery Box slot at
	 *     min(LV ceiling, its own intake rate).
	 */
	public static void fun04ChargeInBatteryBox(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		box.getEnergyStorage().setAmountUntracked(box.getEnergyStorage().getCapacity());
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, pack(0));

		drive(box, helper, 1);
		long expected = Math.min(EnergyTier.LV.maxVoltage(), ToolConfig.energyPackInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, pack intake) = " + expected + " EU, got " + gained);
		}
		drive(box, helper, 9);
		if (ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT)) != expected * 10) {
			helper.fail("ten ticks must move ten times the per-tick rate");
		}
		helper.succeed();
	}

	/**
	 * FUN05: the pack never charges another pack — that loop would drain the wearer for nothing.
	 *
	 * @implements TC-PACK-001-FUN05 — a pack never charges another pack (anti-loop filter).
	 */
	public static void fun05PackDoesNotChargePack(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack worn = pack(ToolConfig.energyPackBuffer);
		ItemStack carried = pack(0);
		player.getInventory().setItem(0, carried);

		long moved = EnergyPackItem.chargeStep(worn, player);
		if (moved != 0 || ItemEnergy.get(carried) != 0) {
			helper.fail("a pack must not charge another pack");
		}
		if (ItemEnergy.get(worn) != ToolConfig.energyPackBuffer) {
			helper.fail("a pack with nothing to charge must not lose EU");
		}
		helper.succeed();
	}

	// ── NEG — nothing happens when it should not ─────────────────────────────────────────────────

	/**
	 * NEG01: an empty pack, a full pouch or a plain item — no transfer, no component writes.
	 *
	 * @implements TC-PACK-001-NEG01 — an empty pack, a full pouch or a plain item all result in no
	 *     transfer at all.
	 */
	public static void neg01NoTransferWhenNothingToDo(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);

		// Empty pack: nothing to give.
		ItemStack emptyPack = pack(0);
		player.getInventory().setItem(0, pouch(0));
		if (EnergyPackItem.chargeStep(emptyPack, player) != 0) {
			helper.fail("an empty pack must move nothing");
		}

		// Full pouch: no room, so the pack must not spend anything.
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		ItemStack full = pouch(ToolConfig.lvPouchBuffer);
		player.getInventory().setItem(0, full);
		if (EnergyPackItem.chargeStep(pack, player) != 0 || ItemEnergy.get(pack) != ToolConfig.energyPackBuffer) {
			helper.fail("a full pouch must not cost the pack any EU");
		}

		// A non-powered item has no buffer at all.
		player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		if (EnergyPackItem.chargeStep(pack, player) != 0) {
			helper.fail("a plain item must not receive EU");
		}
		helper.succeed();
	}

	/**
	 * NEG02: the pack gives only what it has — the last step drains it to exactly 0.
	 *
	 * @implements TC-PACK-001-NEG02 — the pack hands over exactly its remaining EU and floors at 0.
	 */
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
	 *
	 * @implements TC-PACK-001-NEG03 — the charge slot filter accepts every powered item and refuses
	 *     items without a buffer.
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

	/**
	 * NEG04: a pack in the offhand is not a consumer either — the anti-loop filter covers both passes.
	 *
	 * @implements TC-PACK-001-NEG04 — a pack in the offhand is not charged either (the anti-loop
	 *     filter covers the offhand pass too).
	 */
	public static void neg04PackInOffhandNotCharged(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack worn = pack(ToolConfig.energyPackBuffer);
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
	 *
	 * @implements TC-PACK-001-FUN06 — the worn asset follows the charge: lit while charged, drained
	 *     (default) at 0 EU.
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
	 *
	 * @implements TC-PACK-001-FUN07 — the real tick path: a worn pack feeds the pouch exactly one
	 *     batch per second through inventoryTick, and a pack that is not worn transfers nothing.
	 */
	public static void fun07InventoryTickDrivesTransfer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		ItemStack pouch = pouch(0);
		player.setItemSlot(EquipmentSlot.CHEST, pack);
		player.getInventory().setItem(0, pouch);

		// A pack that is NOT worn must stay inert no matter how often it is ticked — the slot gate
		// returns before the clock is even consulted, so this needs no real ticks.
		ItemStack carried = pack(ToolConfig.energyPackBuffer);
		ItemStack idle = pouch(0);
		player.getInventory().setItem(1, idle);
		for (int i = 0; i < 40; i++) {
			carried.getItem().inventoryTick(carried, level, player, EquipmentSlot.MAINHAND);
		}
		if (ItemEnergy.get(idle) != 0 || ItemEnergy.get(carried) != ToolConfig.energyPackBuffer) {
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
					if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer - step()) {
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
	 *
	 * @implements TC-PACK-001-FUN08 — a pack charged straight through the component (/give, loot)
	 *     corrects its worn look on the next tick.
	 */
	public static void fun08ChargedByComponentFixesItsLook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// Bypass ItemEnergy.set entirely — exactly what /give ...[pouch_energy=20000] produces.
		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		pack.set(ModDataComponents.POUCH_ENERGY.get(), (long) ToolConfig.energyPackBuffer);
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

	/**
	 * A mock player in a mode that gets its EU for free. {@code makeMockPlayer} only overrides
	 * {@code gameMode()} and leaves the abilities at their survival defaults, so the abilities are
	 * updated here exactly the way vanilla does it on a real game-mode change — which is what makes
	 * {@code instabuild} true for CREATIVE and (deliberately) false for SPECTATOR.
	 */
	private static Player freePlayer(GameTestHelper helper, GameType mode) {
		Player player = helper.makeMockPlayer(mode);
		mode.updatePlayerAbilities(player.getAbilities());
		return player;
	}

	/**
	 * FUN09: creative keeps the charge (MOD-081). The pack still does its job — the pouch is fed a full
	 * batch, exactly as in survival — but the pack itself pays nothing, the way creative does not wear a
	 * vanilla tool down. Both halves matter: a guard that also stopped the transfer would leave creative
	 * players with pouches that never fill.
	 *
	 * @implements TC-PACK-001-FUN09 — creative and spectator keep the pack's charge (MOD-081): the
	 *     consumers are still fed, the pack pays nothing, survival still pays.
	 */
	public static void fun09CreativeKeepsCharge(GameTestHelper helper) {
		for (GameType mode : new GameType[] {GameType.CREATIVE, GameType.SPECTATOR}) {
			Player player = freePlayer(helper, mode);
			ItemStack pack = pack(ToolConfig.energyPackBuffer);
			ItemStack pouch = pouch(0);
			player.getInventory().setItem(0, pouch);

			long moved = EnergyPackItem.chargeStep(pack, player);
			if (moved != step() || ItemEnergy.get(pouch) != step()) {
				helper.fail("in " + mode + " the pack must still charge the pouch, moved " + moved);
			}
			if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer) {
				helper.fail("in " + mode + " the pack must not pay for what it sent, left "
						+ ItemEnergy.get(pack));
			}
		}
		// Survival is the control: the very same step must be paid for, or this case would pass
		// against a build where the debit is simply gone.
		Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		survival.getInventory().setItem(0, pouch(0));
		EnergyPackItem.chargeStep(pack, survival);
		if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer - step()) {
			helper.fail("survival must still pay for the batch");
		}
		helper.succeed();
	}

	/**
	 * FUN10: the pouch on the cursor and the one in the inventory's 2×2 crafting grid are charged too
	 * (MOD-082) — both sit on the player while the inventory screen is open, and before this they were
	 * the one place where charging visibly stalled.
	 *
	 * @implements TC-PACK-001-FUN10 — the stack on the cursor and the inventory's 2×2 crafting grid
	 *     are charged as well (MOD-082).
	 */
	public static void fun10ChargesCursorAndCraftGrid(GameTestHelper helper) {
		// A real ServerPlayer, not the plain mock: writing to the crafting grid runs
		// InventoryMenu.slotsChanged → CraftingMenu.slotChangedCraftingGrid, which casts the owner to
		// ServerPlayer to send it the recipe result. Its gameMode() is hardcoded CREATIVE, so instabuild
		// is forced off to keep this a survival case — the same trick ElectricDrillScenarios uses.
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		player.getAbilities().instabuild = false;
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		// The cursor is served first, so it is given room for only a quarter of the batch — otherwise it
		// would swallow the whole thing and the grid would never be reached, passing this case for the
		// wrong reason.
		long cursorRoom = step() / 4;
		ItemStack onCursor = pouch(ToolConfig.lvPouchBuffer - cursorRoom);
		ItemStack inGrid = pouch(0);
		player.containerMenu.setCarried(onCursor);
		player.inventoryMenu.getCraftSlots().setItem(0, inGrid);

		long moved = EnergyPackItem.chargeStep(pack, player);
		if (ItemEnergy.get(onCursor) != ToolConfig.lvPouchBuffer) {
			helper.fail("a pouch held on the cursor must be charged, has " + ItemEnergy.get(onCursor));
		}
		if (ItemEnergy.get(inGrid) != step() - cursorRoom) {
			helper.fail("the leftover budget must flow on to the 2×2 crafting grid, grid has "
					+ ItemEnergy.get(inGrid));
		}
		if (moved != step() || ItemEnergy.get(pack) != ToolConfig.energyPackBuffer - step()) {
			helper.fail("the batch must be split across cursor and grid and paid for once, moved " + moved);
		}
		helper.succeed();
	}

	/**
	 * NEG05: the pack reaches the cursor and its own crafting grid — and nothing else. The slots of
	 * whatever container the player has open belong to a chest or a machine in the world, and a pack
	 * must not charge through them. The regression guarded here is a lazy "walk containerMenu.slots"
	 * implementation, which would drain the pack into any chest full of pouches.
	 *
	 * @implements TC-PACK-001-NEG05 — the slots of an open container (a chest) are NOT charged: the
	 *     pack reaches the cursor and its own crafting grid, nothing else (MOD-082).
	 */
	public static void neg05DoesNotChargeOpenContainer(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = pack(ToolConfig.energyPackBuffer);
		ItemStack inChest = pouch(0);
		SimpleContainer chest = new SimpleContainer(9);
		chest.setItem(0, inChest);
		player.containerMenu = new ChestMenu(MenuType.GENERIC_9x1, 1, player.getInventory(), chest, 1);

		if (EnergyPackItem.chargeStep(pack, player) != 0 || ItemEnergy.get(inChest) != 0) {
			helper.fail("a pouch inside an open chest must not be charged by the worn pack");
		}
		if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer) {
			helper.fail("the pack must not pay for a transfer it never made");
		}
		helper.succeed();
	}

	/**
	 * PER01: charge survives a copy of the stack (the component is what persists, not the instance).
	 *
	 * @implements TC-PACK-001-PER01 — charge survives a stack copy, 0 EU removes the component, and
	 *     writes clamp at capacity.
	 */
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
		ItemEnergy.set(pack, ToolConfig.energyPackBuffer + 5000);
		if (ItemEnergy.get(pack) != ToolConfig.energyPackBuffer) {
			helper.fail("the pack buffer must clamp at capacity");
		}
		helper.succeed();
	}
}
