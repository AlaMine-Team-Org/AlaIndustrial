package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.item.PouchContents;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.menu.BatteryBoxMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Loader-neutral gametest bodies for the Battery Pouch (MOD-052, suite TC-POUCH-001). Same pattern as
 * {@link CoreEnergyScenarios}: plain {@code Consumer<GameTestHelper>} bodies using only vanilla
 * {@code GameTestHelper} + neutral content, wrapped by the Fabric {@code PouchGameTest} suite and
 * registered on the NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both
 * loaders exercise the SAME pouch logic (data components resolve per loader).
 *
 * <p>Numbers come from {@link Config} (lvPouchCapacity=128, lvPouchBuffer=2000,
 * lvPouchDrainPerSecond=1) — the balance source of truth.
 */
public final class PouchScenarios {

	private PouchScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	private static ItemStack pouch(long eu) {
		ItemStack stack = new ItemStack(ModContent.BATTERY_POUCH.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	private static PouchItem item(ItemStack pouch) {
		return (PouchItem) pouch.getItem();
	}

	/** A mutable "cursor" for driving overrideOtherStackedOnMe like the inventory screen does. */
	private static final class Cursor implements SlotAccess {
		ItemStack held;

		Cursor(ItemStack held) {
			this.held = held;
		}

		@Override
		public ItemStack get() {
			return held;
		}

		@Override
		public boolean set(ItemStack stack) {
			held = stack;
			return true;
		}
	}

	/** A plain unrestricted slot holding the pouch (stand-in for an inventory-screen slot). */
	private static Slot pouchSlot(ItemStack pouch) {
		SimpleContainer container = new SimpleContainer(1);
		container.setItem(0, pouch);
		return new Slot(container, 0, 0, 0);
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

	/** FUN01: at 0 EU the pouch refuses insertion through the click path; charged, it accepts. */
	public static void fun01InsertRequiresEnergy(GameTestHelper helper) {
		ItemStack pouch = pouch(0);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Cursor cursor = new Cursor(new ItemStack(Items.COBBLESTONE, 64));
		boolean handled = item(pouch).overrideOtherStackedOnMe(pouch, cursor.get(), pouchSlot(pouch),
				ClickAction.SECONDARY, player, cursor);
		if (!handled || !PouchItem.contentsOf(pouch).isEmpty() || cursor.get().getCount() != 64) {
			helper.fail("depleted pouch must refuse insertion (lock)");
		}
		ItemEnergy.set(pouch, Config.lvPouchBuffer);
		handled = item(pouch).overrideOtherStackedOnMe(pouch, cursor.get(), pouchSlot(pouch),
				ClickAction.SECONDARY, player, cursor);
		if (!handled || PouchItem.contentsOf(pouch).weight() != 64 || !cursor.get().isEmpty()) {
			helper.fail("charged pouch must accept the cursor stack");
		}
		helper.succeed();
	}

	/** FUN02: capacity is 128 weight — a third 64-stack does not fit and comes back as leftover. */
	public static void fun02CapacityLeftover(GameTestHelper helper) {
		PouchContents contents = PouchContents.EMPTY;
		PouchContents.InsertResult r1 = contents.insert(new ItemStack(Items.COBBLESTONE, 64));
		PouchContents.InsertResult r2 = r1.contents().insert(new ItemStack(Items.STONE, 64));
		PouchContents.InsertResult r3 = r2.contents().insert(new ItemStack(Items.DIRT, 64));
		if (r2.contents().weight() != Config.lvPouchCapacity || !r2.contents().isFull()) {
			helper.fail("two 64-stacks must fill the pouch exactly (weight " + r2.contents().weight() + ")");
		}
		if (r3.inserted() != 0 || r3.leftover().getCount() != 64) {
			helper.fail("insert into a full pouch must return the whole stack as leftover");
		}
		helper.succeed();
	}

	/** FUN03: weight classes — stack64 → 1/item, stack16 → 4/item, unstackable → 64/item. */
	public static void fun03WeightMath(GameTestHelper helper) {
		if (PouchContents.weightOf(new ItemStack(Items.COBBLESTONE)) != 1
				|| PouchContents.weightOf(new ItemStack(Items.ENDER_PEARL)) != 4
				|| PouchContents.weightOf(new ItemStack(Items.IRON_PICKAXE)) != 64) {
			helper.fail("weightOf must be 64/maxStackSize (1 / 4 / 64)");
		}
		PouchContents contents = PouchContents.EMPTY
				.insert(new ItemStack(Items.IRON_PICKAXE)).contents()
				.insert(new ItemStack(Items.ENDER_PEARL, 16)).contents();
		if (contents.weight() != 128 || !contents.isFull()) {
			helper.fail("pickaxe (64) + 16 pearls (64) must fill 128 exactly, got " + contents.weight());
		}
		if (contents.insert(new ItemStack(Items.COBBLESTONE)).inserted() != 0) {
			helper.fail("a full pouch must reject even a single item");
		}
		helper.succeed();
	}

	/** FUN04: LIFO — removeTop returns whole stacks in reverse insertion order. */
	public static void fun04RemoveLifo(GameTestHelper helper) {
		PouchContents contents = PouchContents.EMPTY
				.insert(new ItemStack(Items.STONE, 8)).contents()
				.insert(new ItemStack(Items.IRON_INGOT, 8)).contents()
				.insert(new ItemStack(Items.COAL, 8)).contents();
		PouchContents.RemoveResult r1 = contents.removeTop();
		PouchContents.RemoveResult r2 = r1.contents().removeTop();
		PouchContents.RemoveResult r3 = r2.contents().removeTop();
		if (!r1.removed().is(Items.COAL) || !r2.removed().is(Items.IRON_INGOT) || !r3.removed().is(Items.STONE)) {
			helper.fail("removeTop order must be LIFO: coal, iron, stone");
		}
		if (r1.removed().getCount() != 8 || !r3.contents().isEmpty()) {
			helper.fail("removeTop must return the WHOLE top stack and drain to empty");
		}
		helper.succeed();
	}

	/** FUN05: one drain step takes exactly lvPouchDrainPerSecond EU while items are inside. */
	public static void fun05PassiveDrain(GameTestHelper helper) {
		ItemStack pouch = pouch(Config.lvPouchBuffer);
		PouchItem.setContents(pouch, PouchContents.EMPTY.insert(new ItemStack(Items.COBBLESTONE, 8)).contents());
		if (!PouchItem.drainStep(pouch) || ItemEnergy.get(pouch) != Config.lvPouchBuffer - Config.lvPouchDrainPerSecond) {
			helper.fail("drain step must take exactly " + Config.lvPouchDrainPerSecond + " EU");
		}
		helper.succeed();
	}

	/** FUN06: an empty pouch never drains — and never rewrites its components. */
	public static void fun06NoDrainWhenEmpty(GameTestHelper helper) {
		ItemStack pouch = pouch(Config.lvPouchBuffer);
		for (int i = 0; i < 100; i++) {
			if (PouchItem.drainStep(pouch)) {
				helper.fail("empty pouch must not drain");
			}
		}
		if (ItemEnergy.get(pouch) != Config.lvPouchBuffer) {
			helper.fail("empty pouch charge must stay untouched");
		}
		helper.succeed();
	}

	/** FUN07: the Battery Box charge slot refills the pouch from the block buffer. */
	public static void fun07ChargeInBatteryBox(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		box.getEnergyStorage().amount = Config.lvPouchBuffer; // exactly one pouch worth
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, pouch(0));
		tickBox(helper, box, 64); // 2000 EU at 32 EU/t needs ⌈2000/32⌉ = 63 ticks
		ItemStack pouch = box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT);
		if (ItemEnergy.get(pouch) != Config.lvPouchBuffer) {
			helper.fail("pouch must be fully charged, got " + ItemEnergy.get(pouch));
		}
		if (box.getEnergyStorage().amount != 0) {
			helper.fail("box must have paid exactly the pouch's charge, left " + box.getEnergyStorage().amount);
		}
		helper.succeed();
	}

	/** FUN08: inserting merges into an existing partial stack before opening a new one. */
	public static void fun08MergeOnInsert(GameTestHelper helper) {
		PouchContents contents = PouchContents.EMPTY.insert(new ItemStack(Items.COBBLESTONE, 32)).contents();
		PouchContents merged = contents.insert(new ItemStack(Items.COBBLESTONE, 64)).contents();
		if (merged.items().size() != 2
				|| merged.items().get(0).getCount() != 64
				|| merged.items().get(1).getCount() != 32
				|| merged.weight() != 96) {
			helper.fail("merge must top up the existing stack to 64 and append the remaining 32");
		}
		helper.succeed();
	}

	/** FUN09: the bundle-style tooltip image is present exactly when the pouch holds items. */
	public static void fun09TooltipImage(GameTestHelper helper) {
		ItemStack pouch = pouch(0);
		if (item(pouch).getTooltipImage(pouch).isPresent()) {
			helper.fail("empty pouch must expose no tooltip image");
		}
		PouchItem.setContents(pouch, PouchContents.EMPTY.insert(new ItemStack(Items.COBBLESTONE, 8)).contents());
		if (!(item(pouch).getTooltipImage(pouch).orElse(null) instanceof dev.alaindustrial.item.PouchTooltip tooltip)
				|| tooltip.contents().weight() != 8) {
			helper.fail("filled pouch must expose a PouchTooltip carrying its contents");
		}
		helper.succeed();
	}

	// ── NEG — must-not behaviours ────────────────────────────────────────────────────────────────

	/** NEG01: no pouch-in-pouch. */
	public static void neg01NoPouchInPouch(GameTestHelper helper) {
		PouchContents.InsertResult r = PouchContents.EMPTY.insert(pouch(0));
		if (r.inserted() != 0 || r.leftover().isEmpty()) {
			helper.fail("a pouch must never fit into a pouch");
		}
		helper.succeed();
	}

	/** NEG02: the charge slot is GUI-only — hoppers can neither push into it nor pull from it. */
	public static void neg02HopperCutOff(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		ItemStack pouch = pouch(0);
		if (!box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, pouch)) {
			helper.fail("manual placement of a pouch must be allowed");
		}
		if (box.canPlaceItem(BatteryBoxBlockEntity.CHARGE_SLOT, new ItemStack(Items.COBBLESTONE))) {
			helper.fail("non-pouch items must be rejected");
		}
		for (Direction side : Direction.values()) {
			if (box.canPlaceItemThroughFace(BatteryBoxBlockEntity.CHARGE_SLOT, pouch, side)) {
				helper.fail("hopper insertion must be blocked on face " + side);
			}
			if (box.canTakeItemThroughFace(BatteryBoxBlockEntity.CHARGE_SLOT, pouch, side)) {
				helper.fail("hopper extraction must be blocked on face " + side);
			}
		}
		helper.succeed();
	}

	/** NEG03: drain floors at 0 (never negative) and the lock then refuses extraction. */
	public static void neg03DrainFloorsAndLocks(GameTestHelper helper) {
		ItemStack pouch = pouch(1);
		PouchItem.setContents(pouch, PouchContents.EMPTY.insert(new ItemStack(Items.COBBLESTONE, 8)).contents());
		PouchItem.drainStep(pouch);
		if (ItemEnergy.get(pouch) != 0 || PouchItem.drainStep(pouch)) {
			helper.fail("drain must stop exactly at 0 EU");
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Cursor cursor = new Cursor(ItemStack.EMPTY);
		boolean handled = item(pouch).overrideOtherStackedOnMe(pouch, cursor.get(), pouchSlot(pouch),
				ClickAction.SECONDARY, player, cursor);
		if (!handled || !cursor.get().isEmpty() || PouchItem.contentsOf(pouch).isEmpty()) {
			helper.fail("locked pouch must refuse extraction and keep its contents");
		}
		helper.succeed();
	}

	/** NEG04: the menu slot's client-side filter (mayPlace) accepts only pouches. */
	public static void neg04MenuSlotFilter(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BatteryBoxMenu menu = new BatteryBoxMenu(0, player.getInventory(), box, ContainerLevelAccess.NULL);
		Slot slot = menu.slots.get(0);
		if (!slot.mayPlace(pouch(0)) || slot.mayPlace(new ItemStack(Items.COBBLESTONE))) {
			helper.fail("charge slot mayPlace must accept only PouchItem");
		}
		helper.succeed();
	}

	// ── PRF — limits and rates ───────────────────────────────────────────────────────────────────

	/** PRF01: charging moves at most the LV ceiling (32 EU) per tick. */
	public static void prf01ChargeRateCap(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity();
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, pouch(0));
		tickBox(helper, box, 1);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != EnergyTier.LV.maxVoltage()) {
			helper.fail("one tick must move exactly the LV ceiling (32 EU), got " + gained);
		}
		helper.succeed();
	}

	/** PRF02: the pouch buffer caps at lvPouchBuffer — no overcharge, exact payment. */
	public static void prf02NoOvercharge(GameTestHelper helper) {
		BatteryBoxBlockEntity box = placeBox(helper);
		box.getEnergyStorage().amount = Config.lvPouchBuffer;
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, pouch(Config.lvPouchBuffer - 10));
		tickBox(helper, box, 10);
		ItemStack pouch = box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT);
		if (ItemEnergy.get(pouch) != Config.lvPouchBuffer) {
			helper.fail("pouch must stop exactly at capacity, got " + ItemEnergy.get(pouch));
		}
		if (box.getEnergyStorage().amount != Config.lvPouchBuffer - 10) {
			helper.fail("box must pay exactly the missing 10 EU, left " + box.getEnergyStorage().amount);
		}
		helper.succeed();
	}

	// ── PER — persistence ────────────────────────────────────────────────────────────────────────

	/** PER01: contents survive a full ItemStack codec round-trip (types, counts, order). */
	public static void per01ContentsRoundTrip(GameTestHelper helper) {
		ItemStack pouch = pouch(777);
		PouchContents contents = PouchContents.EMPTY
				.insert(new ItemStack(Items.COBBLESTONE, 64)).contents()
				.insert(new ItemStack(Items.ENDER_PEARL, 5)).contents()
				.insert(new ItemStack(Items.IRON_PICKAXE)).contents();
		PouchItem.setContents(pouch, contents);
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag tag = ItemStack.CODEC.encodeStart(ops, pouch).getOrThrow();
		ItemStack back = ItemStack.CODEC.parse(ops, tag).getOrThrow();
		if (!PouchItem.contentsOf(back).equals(contents)) {
			helper.fail("pouch contents must survive the codec round-trip intact");
		}
		if (ItemEnergy.get(back) != 777) {
			helper.fail("pouch energy must survive the codec round-trip, got " + ItemEnergy.get(back));
		}
		helper.succeed();
	}

	/** PER02: energy write semantics — clamping to capacity and absent-at-zero normalisation. */
	public static void per02EnergySemantics(GameTestHelper helper) {
		ItemStack pouch = pouch(0);
		if (pouch.has(ModDataComponents.POUCH_ENERGY.get())) {
			helper.fail("0 EU must be stored as an absent component");
		}
		ItemEnergy.set(pouch, 777);
		if (ItemEnergy.get(pouch) != 777) {
			helper.fail("set/get must round-trip 777");
		}
		ItemEnergy.add(pouch, Config.lvPouchBuffer); // way past capacity
		if (ItemEnergy.get(pouch) != Config.lvPouchBuffer) {
			helper.fail("writes must clamp to capacity");
		}
		ItemEnergy.add(pouch, -2L * Config.lvPouchBuffer); // way below zero
		if (ItemEnergy.get(pouch) != 0 || pouch.has(ModDataComponents.POUCH_ENERGY.get())) {
			helper.fail("writes must floor at 0 and drop the component");
		}
		helper.succeed();
	}
}
