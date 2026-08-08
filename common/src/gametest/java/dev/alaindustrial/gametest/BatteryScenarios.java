package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loader-neutral gametest bodies for the Battery (MOD-083) — the mod's only powered item that stacks.
 *
 * <p>Everything here exists to pin one of the two properties that make a stacking EU carrier tricky:
 *
 * <ul>
 *   <li><b>Energy is conserved across a stack.</b> Charge is stored per item, so every transfer has to
 *       be a whole multiple of the count. A test that only looked at "did the battery gain charge"
 *       would pass while a stack of sixteen was being filled for the price of one — which is a dupe.</li>
 *   <li><b>Stacking follows the charge.</b> Drained batteries stack with fresh ones, equal charges stack
 *       with each other, unequal ones do not. That behaviour comes from vanilla's component equality,
 *       which is exactly why it needs a test: nothing in this mod's own code would fail if it broke.</li>
 * </ul>
 */
public final class BatteryScenarios {

	private BatteryScenarios() {}

	private static final BlockPos STORE = new BlockPos(1, 2, 1);

	/** Place a Battery Box and return its block entity, or null (after failing) if it did not stick. */
	private static BatteryBoxBlockEntity placeStore(GameTestHelper helper) {
		BlockState state = ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH);
		helper.setBlock(STORE, state);
		if (helper.getLevel().getBlockEntity(helper.absolutePos(STORE)) == null) {
			helper.fail("battery box block entity missing at " + STORE);
			return null;
		}
		return helper.getBlockEntity(STORE, BatteryBoxBlockEntity.class);
	}

	private static ItemStack batteries(int count, long chargeEach) {
		ItemStack stack = new ItemStack(ModContent.BATTERY.get(), count);
		ItemEnergy.set(stack, chargeEach);
		return stack;
	}

	/**
	 * BATTERY-01 — charging a stack costs count × the per-item gain.
	 *
	 * <p>The dupe guard. A stack of eight is charged for one tick and the EU that left the store is
	 * compared against the EU that arrived in the stack. Make {@code chargeItem} pay per item instead of
	 * per stack and the buffer loses an eighth of what the batteries gain — this goes red.
	 */
	public static void battery01ChargingAStackCostsPerItem(GameTestHelper helper) {
		BatteryBoxBlockEntity be = placeStore(helper);
		if (be == null) {
			return;
		}
		int count = 8;
		be.getEnergyStorage().amount = Config.batteryBoxBuffer;
		be.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, batteries(count, 0));

		long bufferBefore = be.getEnergyStorage().amount;
		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());
		long spent = bufferBefore - be.getEnergyStorage().amount;

		ItemStack charged = be.getItem(BatteryBoxBlockEntity.CHARGE_SLOT);
		long perItem = ItemEnergy.get(charged);
		if (perItem <= 0) {
			helper.fail("stack of " + count + " gained no charge at all");
			return;
		}
		if (charged.getCount() != count) {
			helper.fail("charging changed the stack size: " + charged.getCount() + " instead of " + count);
			return;
		}
		if (spent != perItem * count) {
			helper.fail("EU not conserved: store paid " + spent + " but the stack gained "
					+ perItem + " x " + count + " = " + (perItem * count));
			return;
		}
		if (spent > EnergyTier.LV.maxVoltage()) {
			helper.fail("charging exceeded the LV ceiling in one tick: " + spent);
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-02 — a full stack still charges at all.
	 *
	 * <p>This is the test that decides the stack size. Energy per item is {@code budget / count}, so once
	 * the count passes the per-tick ceiling the share rounds to zero and the stack sits at 0 EU forever.
	 * At {@code stacksTo(16)} against the LV ceiling of 32 there are 2 EU per item to give.
	 */
	public static void battery02FullStackStillCharges(GameTestHelper helper) {
		BatteryBoxBlockEntity be = placeStore(helper);
		if (be == null) {
			return;
		}
		be.getEnergyStorage().amount = Config.batteryBoxBuffer;
		be.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, batteries(BatteryItem.MAX_STACK, 0));

		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());

		long perItem = ItemEnergy.get(be.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (perItem <= 0) {
			helper.fail("a full stack of " + BatteryItem.MAX_STACK + " never charges: the per-item share "
					+ "rounds to zero against the " + EnergyTier.LV.maxVoltage() + " EU/t ceiling");
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-03 — the discharge slot drains a stack into the buffer, conserving EU.
	 *
	 * <p>The mirror of BATTERY-01, and the reason the Battery Box grew a second slot at all: the battery
	 * is an LV item, and before this its charge could only be recovered a whole tier later.
	 */
	public static void battery03DischargeSlotDrainsStack(GameTestHelper helper) {
		BatteryBoxBlockEntity be = placeStore(helper);
		if (be == null) {
			return;
		}
		int count = 4;
		long each = Config.batteryBuffer;
		be.getEnergyStorage().amount = 0;
		be.setItem(BatteryBoxBlockEntity.DISCHARGE_SLOT, batteries(count, each));

		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());

		long gained = be.getEnergyStorage().amount;
		ItemStack left = be.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT);
		long lost = (each - ItemEnergy.get(left)) * count;
		if (gained <= 0) {
			helper.fail("discharge slot moved no EU out of a full stack");
			return;
		}
		if (gained != lost) {
			helper.fail("EU not conserved: stack lost " + lost + " but buffer gained " + gained);
			return;
		}
		if (gained > EnergyTier.LV.maxVoltage()) {
			helper.fail("discharge exceeded the LV ceiling in one tick: " + gained);
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-04 — stacking follows the charge, in all three directions.
	 *
	 * <p>Drained batteries have to be component-identical to freshly crafted ones (that is what makes
	 * "drain it to zero and it stacks again" true), equal charges have to stack, and unequal ones must
	 * not. Write 0 EU into the component instead of removing it and the first case goes red.
	 */
	public static void battery04StackingFollowsCharge(GameTestHelper helper) {
		ItemStack fresh = new ItemStack(ModContent.BATTERY.get());
		ItemStack drained = batteries(1, Config.batteryBuffer);
		ItemEnergy.set(drained, 0);
		if (!ItemStack.isSameItemSameComponents(fresh, drained)) {
			helper.fail("a battery drained to zero does not stack with a fresh one: "
					+ drained.getComponents() + " vs " + fresh.getComponents());
			return;
		}
		ItemStack fullA = batteries(1, Config.batteryBuffer);
		ItemStack fullB = batteries(1, Config.batteryBuffer);
		if (!ItemStack.isSameItemSameComponents(fullA, fullB)) {
			helper.fail("two equally charged batteries do not stack");
			return;
		}
		ItemStack half = batteries(1, Config.batteryBuffer / 2);
		if (ItemStack.isSameItemSameComponents(fullA, half)) {
			helper.fail("batteries at different charges stack — a stack cannot describe two charges");
			return;
		}
		if (fresh.getMaxStackSize() != BatteryItem.MAX_STACK) {
			helper.fail("battery stack size is " + fresh.getMaxStackSize() + ", expected "
					+ BatteryItem.MAX_STACK + " (the size the per-tick arithmetic is built on)");
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-05 — a charged battery crafts into a powered item and takes its EU along.
	 *
	 * <p>Runs the real recipe out of the recipe manager rather than a hand-built one, so a datapack that
	 * stopped using the charge-carrying type would fail here rather than silently drop the feature.
	 */
	public static void battery05CraftCarriesChargeIntoResult(GameTestHelper helper) {
		Optional<RecipeHolder<?>> found = helper.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, Industrialization.id("electric_drill")));
		if (found.isEmpty()) {
			helper.fail("electric_drill recipe not loaded — cannot test charge transfer");
			return;
		}
		if (!(found.get().value() instanceof CraftingRecipe recipe)) {
			helper.fail("electric_drill recipe is not a crafting recipe");
			return;
		}
		long each = Config.batteryBuffer;
		// The real 3×3 layout: III / BPB / CEC. Filling it completely means matches() is exercised too —
		// that is what proves a CHARGED battery still satisfies the recipe rather than only that the
		// arithmetic works once a match is assumed.
		List<ItemStack> grid = new ArrayList<>(List.of(
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
				batteries(1, each), new ItemStack(Items.DIAMOND_PICKAXE), batteries(1, each),
				new ItemStack(ModContent.COPPER_COIL.get()), new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()),
				new ItemStack(ModContent.COPPER_COIL.get())));
		CraftingInput input = CraftingInput.of(3, 3, grid);
		if (!recipe.matches(input, helper.getLevel())) {
			helper.fail("recipe refuses a grid whose batteries are charged — components must be ignored");
			return;
		}
		ItemStack result = recipe.assemble(input);
		if (result.isEmpty()) {
			helper.fail("recipe assembled nothing");
			return;
		}
		long carried = ItemEnergy.get(result);
		long expected = Math.min(each * 2, ItemEnergy.capacity(result));
		if (carried != expected) {
			helper.fail("crafted result carries " + carried + " EU, expected " + expected
					+ " (two full batteries, clamped to the result's " + ItemEnergy.capacity(result) + ")");
			return;
		}
		// And the same recipe with drained batteries must produce the plain, component-free item.
		grid.set(3, batteries(1, 0));
		grid.set(5, batteries(1, 0));
		ItemStack plain = recipe.assemble(CraftingInput.of(3, 3, new ArrayList<>(grid)));
		if (ItemEnergy.get(plain) != 0) {
			helper.fail("crafting with drained batteries produced a charged result: " + ItemEnergy.get(plain));
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-07 — assembling the same recipe twice yields the same result.
	 *
	 * <p>What a bulk craft (shift-clicking the result) does: vanilla re-runs the recipe once per output.
	 * A recipe that accumulated state between calls — summing the previous result's charge into the next
	 * one — would multiply energy with every click of the mouse. Two identical calls have to give two
	 * identical stacks.
	 */
	public static void battery07RepeatedAssemblyDoesNotAccumulate(GameTestHelper helper) {
		Optional<RecipeHolder<?>> found = helper.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, Industrialization.id("electric_drill")));
		if (found.isEmpty() || !(found.get().value() instanceof CraftingRecipe recipe)) {
			helper.fail("electric_drill recipe not loaded as a crafting recipe");
			return;
		}
		long each = Config.batteryBuffer;
		List<ItemStack> grid = List.of(
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
				batteries(1, each), new ItemStack(Items.DIAMOND_PICKAXE), batteries(1, each),
				new ItemStack(ModContent.COPPER_COIL.get()), new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()),
				new ItemStack(ModContent.COPPER_COIL.get()));
		ItemStack first = recipe.assemble(CraftingInput.of(3, 3, new ArrayList<>(grid)));
		ItemStack second = recipe.assemble(CraftingInput.of(3, 3, new ArrayList<>(grid)));
		if (ItemEnergy.get(first) != ItemEnergy.get(second)) {
			helper.fail("repeated crafts differ: first carried " + ItemEnergy.get(first)
					+ " EU, second " + ItemEnergy.get(second) + " — the recipe is accumulating state");
			return;
		}
		if (!ItemStack.matches(first, second)) {
			helper.fail("repeated crafts produced different stacks");
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-08 — the Assembler builds a powered item out of charged batteries, and the charge lands.
	 *
	 * <p>Automation is where a charge-carrying recipe is most likely to break: the machine feeds the
	 * recipe the real stacks out of its warehouse, so whatever charge those batteries hold ends up in the
	 * result. Worth pinning, because the assembler compares what it made against what the blueprint
	 * promised, and a comparison that counted charge would stall the machine on a full warehouse.
	 */
	public static void battery08AssemblerCarriesChargeThrough(GameTestHelper helper) {
		Optional<RecipeHolder<?>> found = helper.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, Industrialization.id("electric_drill")));
		if (found.isEmpty() || !(found.get().value() instanceof CraftingRecipe recipe)) {
			helper.fail("electric_drill recipe not loaded as a crafting recipe");
			return;
		}
		long each = Config.batteryBuffer;
		// The blueprint is recorded with DRAINED batteries — the state a player's blueprint is normally
		// written in — while the warehouse supplies CHARGED ones. That mismatch between the promised
		// result and the produced one is exactly what the assembler's gate has to tolerate.
		List<ItemStack> recorded = List.of(
				new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
				batteries(1, 0), new ItemStack(Items.DIAMOND_PICKAXE), batteries(1, 0),
				new ItemStack(ModContent.COPPER_COIL.get()), new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get()),
				new ItemStack(ModContent.COPPER_COIL.get()));
		List<ItemStack> stocked = new ArrayList<>(recorded);
		stocked.set(3, batteries(1, each));
		stocked.set(5, batteries(1, each));

		ItemStack promised = recipe.assemble(CraftingInput.of(3, 3, new ArrayList<>(recorded)));
		ItemStack produced = recipe.assemble(CraftingInput.of(3, 3, stocked));
		if (ItemEnergy.get(produced) != each * 2) {
			helper.fail("charged warehouse stock did not reach the result: " + ItemEnergy.get(produced)
					+ " EU instead of " + (each * 2));
			return;
		}
		// The two differ only by charge — which is the whole point of comparing them blind to it.
		ItemStack blind = produced.copy();
		ItemEnergy.set(blind, 0L);
		if (!ItemStack.matches(promised, blind)) {
			helper.fail("promised and produced differ by more than charge: " + promised.getComponents()
					+ " vs " + blind.getComponents());
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-09 — upgrading a charged drill carries its EU into the upgrade (MOD-373).
	 *
	 * <p>The other charge-transfer scenarios all feed the grid <em>batteries</em>. This one is the case
	 * that slipped through MOD-083 precisely because it has none: the energy carrier is the player's own
	 * tool. The recipe accepted a charged drill from day one — a vanilla {@code Ingredient} ignores
	 * components — and burned every EU in it, up to a full 10 000 buffer, silently.
	 *
	 * <p>The recipe is taken out of the {@code RecipeManager} rather than built here, so this goes red if
	 * the JSON ever reverts to {@code minecraft:crafting_shaped} — which is the whole point of the test.
	 */
	public static void battery09DrillUpgradeCarriesChargeIntoResult(GameTestHelper helper) {
		Optional<RecipeHolder<?>> found = helper.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, Industrialization.id("electric_drill_diamond_tip")));
		if (found.isEmpty()) {
			helper.fail("electric_drill_diamond_tip recipe not loaded — cannot test charge transfer");
			return;
		}
		if (!(found.get().value() instanceof CraftingRecipe recipe)) {
			helper.fail("electric_drill_diamond_tip recipe is not a crafting recipe");
			return;
		}
		long charge = Config.electricDrillBuffer;
		if (charge <= 0) {
			helper.fail("the drill's configured buffer is " + charge + " — this test would prove nothing");
			return;
		}
		ItemStack drill = new ItemStack(ModContent.ELECTRIC_DRILL.get());
		ItemEnergy.set(drill, charge);
		// The real 3×3 layout: DDD / IUI / _G_ — filled completely, so matches() is exercised too and the
		// test proves a CHARGED drill still satisfies the recipe, not just that the arithmetic works.
		List<ItemStack> grid = new ArrayList<>(List.of(
				new ItemStack(ModContent.DIAMOND_DUST.get()), new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.DIAMOND_DUST.get()),
				new ItemStack(ModContent.INVAR_INGOT.get()), drill, new ItemStack(ModContent.INVAR_INGOT.get()),
				ItemStack.EMPTY, new ItemStack(ModContent.SILVER_GEAR.get()), ItemStack.EMPTY));
		CraftingInput input = CraftingInput.of(3, 3, grid);
		if (!recipe.matches(input, helper.getLevel())) {
			helper.fail("recipe refuses a grid whose drill is charged — components must be ignored");
			return;
		}
		ItemStack result = recipe.assemble(input);
		if (result.isEmpty()) {
			helper.fail("recipe assembled nothing");
			return;
		}
		// The upgrade is a subclass of the base drill, so ItemEnergy gives it the same 10 000 buffer and
		// the clamp cannot bite. Asserted rather than assumed: a future buffer split would make the
		// expected value wrong, and a test that quietly re-derived it from the result would never notice.
		long capacity = ItemEnergy.capacity(result);
		if (capacity != charge) {
			helper.fail("the upgrade's buffer is " + capacity + " EU, expected the base drill's " + charge
					+ " — the expected transfer below is no longer a full one");
			return;
		}
		long carried = ItemEnergy.get(result);
		if (carried != charge) {
			helper.fail("upgrading a drill charged to " + charge + " EU produced " + carried
					+ " EU — the charge is being burned instead of carried over");
			return;
		}
		// And an empty drill has to give exactly the item it always did: no charge component at all,
		// otherwise fresh upgrades would stop being interchangeable with crafted-from-empty ones.
		grid.set(4, new ItemStack(ModContent.ELECTRIC_DRILL.get()));
		ItemStack plain = recipe.assemble(CraftingInput.of(3, 3, new ArrayList<>(grid)));
		if (ItemEnergy.get(plain) != 0) {
			helper.fail("upgrading a drained drill produced a charged result: " + ItemEnergy.get(plain));
			return;
		}
		if (!ItemStack.matches(plain, new ItemStack(ModContent.ELECTRIC_DRILL_DIAMOND_TIP.get()))) {
			helper.fail("upgrading a drained drill no longer gives the plain item: " + plain.getComponents());
			return;
		}
		helper.succeed();
	}

	/**
	 * BATTERY-06 — the battery is excluded from automatic charging.
	 *
	 * <p>A worn Energy Pack holds 20 000 EU and a full stack of batteries holds 32 000: without the tag
	 * the pack would empty itself into the spares in the player's backpack the moment they were picked
	 * up. The Charging Station ignores the tag by policy and still fills them.
	 */
	public static void battery06BatteryIsExcludedFromAutoCharge(GameTestHelper helper) {
		ItemStack battery = new ItemStack(ModContent.BATTERY.get());
		if (!battery.is(ModTags.Items.NO_AUTO_CHARGE)) {
			helper.fail("battery is not in no_auto_charge — a worn Energy Pack will drain into it");
			return;
		}
		helper.succeed();
	}
}
