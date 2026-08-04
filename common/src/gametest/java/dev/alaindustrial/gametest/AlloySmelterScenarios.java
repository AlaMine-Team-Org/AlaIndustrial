package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loader-neutral gametest bodies for the Alloy Smelter (MOD-064, suite TC-ALLOY-001). Wrapped by the
 * Fabric {@code AlloySmelterGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}, {@code alloy_smelter_*}), so both loaders run the SAME bodies.
 *
 * <p>What this machine does that no other machine in the mod does — and therefore what these bodies
 * exist to pin down:
 *
 * <ul>
 * <li><b>Slot order is meaningless.</b> Every other multi-input machine matches positionally.
 * {@link #fun02OrderDoesNotMatter} runs the identical mix loaded back-to-front; if the matcher ever
 * regressed to a positional compare, the forward case would still pass and only this one would go
 * red.</li>
 * <li><b>Per-component counts.</b> Bronze eats three copper and one tin. A machine that consumed one
 * of each would still produce bronze and look correct — {@link #fun03ConsumesPerComponentCounts} is
 * what separates the two.</li>
 * <li><b>The strictness rule.</b> A two-component recipe must NOT run with junk in the third slot
 * ({@link #con01SpareSlotBlocksShorterRecipe}).</li>
 * <li><b>The anti-deadlock filter.</b> A hopper full of one metal must not be able to occupy all
 * three inputs ({@link #reg01DuplicateInputRefused}).</li>
 * </ul>
 */
public final class AlloySmelterScenarios {

	private AlloySmelterScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Comfortably above one operation's cost (1200 EU), set directly so the tier packet cap is bypassed. */
	private static final long AMPLE_EU = 20_000L;

	/** Ticks to drive: one full operation plus slack for the scaled-duration knob. */
	private static int driveTicks() {
		return Config.scaledDuration(Config.alloySmelterDuration) + 20;
	}

	private static AlloySmelterBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModContent.ALLOY_SMELTER.get());
		AlloySmelterBlockEntity be = helper.getBlockEntity(POS, AlloySmelterBlockEntity.class);
		if (be == null) {
			helper.fail("alloy smelter block entity missing after placement");
		}
		return be;
	}

	/**
	 * Drive the machine with its buffer topped up every tick, the way a connected cable keeps it fed.
	 *
	 * <p>Load-bearing: one operation costs 1200 EU while {@code machineBuffer} holds 800, so the
	 * smelter relies on the network refilling it as it works. A buffer set once at the start would run
	 * dry two thirds of the way through and report a working machine as broken.
	 */
	private static void drivePowered(AlloySmelterBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			be.getEnergyStorage().amount = AMPLE_EU;
			AlaGameTestHelper.drive(be, helper, 1);
		}
	}

	/** Load the three input slots directly, bypassing the slot filter (that is tested separately). */
	private static void load(AlloySmelterBlockEntity be, ItemStack a, ItemStack b, ItemStack c) {
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_0, a);
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_1, b);
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_2, c);
	}

	private static ItemStack copper(int count) {
		return new ItemStack(Items.COPPER_INGOT, count);
	}

	private static ItemStack tin(int count) {
		return new ItemStack(ModContent.TIN_INGOT.get(), count);
	}

	/** Assert the output slot holds exactly {@code count} of {@code expected}. */
	private static void assertOutput(AlloySmelterBlockEntity be, GameTestHelper helper,
			net.minecraft.world.item.Item expected, int count, String what) {
		ItemStack out = be.getItem(AlloySmelterBlockEntity.OUTPUT_SLOT);
		if (!out.is(expected) || out.getCount() != count) {
			helper.fail("expected " + count + "x " + expected + " from " + what + ", got "
					+ (out.isEmpty() ? "empty" : out.getCount() + "x " + out.getItem()));
		}
	}

	private static void assertOutputEmpty(AlloySmelterBlockEntity be, GameTestHelper helper, String why) {
		ItemStack out = be.getItem(AlloySmelterBlockEntity.OUTPUT_SLOT);
		if (!out.isEmpty()) {
			helper.fail(why + " — but the smelter produced " + out.getCount() + "x " + out.getItem());
		}
	}

	// ── FUN01/FUN02: the alloy is produced, and the loading order does not matter ───────────────────

	/** Three copper and one tin, loaded in declaration order, become two bronze. */
	public static void fun01BronzeIsSmelted(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, copper(3), tin(1), ItemStack.EMPTY);
		drivePowered(be, helper, driveTicks());
		assertOutput(be, helper, ModContent.BRONZE_INGOT.get(), 2, "3 copper + 1 tin");
		helper.succeed();
	}

	/**
	 * The same mix loaded back-to-front produces the same bronze.
	 *
	 * <p>The negative control for the whole design: with a positional matcher this is the only case
	 * that fails, because {@link #fun01BronzeIsSmelted} loads the components in the order the recipe
	 * declares them and would keep passing.
	 */
	public static void fun02OrderDoesNotMatter(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		// Tin first, and in the LAST slot rather than the first — neither the order nor the gap matches
		// the recipe's declaration.
		load(be, ItemStack.EMPTY, tin(1), copper(3));
		drivePowered(be, helper, driveTicks());
		assertOutput(be, helper, ModContent.BRONZE_INGOT.get(), 2, "tin and copper in reversed slots");
		helper.succeed();
	}

	/** Each input shrinks by its own count — three copper and one tin, not one of each. */
	public static void fun03ConsumesPerComponentCounts(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		// One spare of each on top of the recipe's price, so the remainder is unambiguous.
		load(be, copper(4), tin(2), ItemStack.EMPTY);
		drivePowered(be, helper, driveTicks());
		int copperLeft = be.getItem(AlloySmelterBlockEntity.INPUT_SLOT_0).getCount();
		int tinLeft = be.getItem(AlloySmelterBlockEntity.INPUT_SLOT_1).getCount();
		if (copperLeft != 1 || tinLeft != 1) {
			helper.fail("expected 3 copper and 1 tin consumed (leaving 1 and 1), left "
					+ copperLeft + " copper and " + tinLeft + " tin");
		}
		helper.succeed();
	}

	/** A three-slot recipe is not required: two components with one slot empty is the normal case. */
	public static void fun04ElectrumFromGoldAndSilver(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY,
				new ItemStack(ModContent.SILVER_INGOT.get()));
		drivePowered(be, helper, driveTicks());
		assertOutput(be, helper, ModContent.ELECTRUM_INGOT.get(), 1, "1 gold + 1 silver");
		helper.succeed();
	}

	// ── CON01/CON02: the machine refuses to run where it must ──────────────────────────────────────

	/**
	 * A two-component recipe must not run while a third slot holds something else.
	 *
	 * <p>Without the strictness rule the smelter would eat the copper and tin and leave the player's
	 * third stack sitting there forever with no indication it was never part of the recipe.
	 */
	public static void con01SpareSlotBlocksShorterRecipe(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, copper(3), tin(1), new ItemStack(Items.IRON_INGOT));
		drivePowered(be, helper, driveTicks());
		assertOutputEmpty(be, helper, "a spare occupied slot must block a two-component recipe");
		helper.succeed();
	}

	/** Too few of one component: nothing runs and nothing is consumed. */
	public static void con02ShortComponentBlocksWork(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, copper(2), tin(1), ItemStack.EMPTY); // bronze needs three copper
		drivePowered(be, helper, driveTicks());
		assertOutputEmpty(be, helper, "two copper is one short of the bronze recipe");
		if (be.getItem(AlloySmelterBlockEntity.INPUT_SLOT_0).getCount() != 2) {
			helper.fail("an unaffordable recipe must not consume anything");
		}
		helper.succeed();
	}

	/** With no energy the machine holds the recipe but produces nothing. */
	public static void con03NoEnergyBlocksWork(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, copper(3), tin(1), ItemStack.EMPTY);
		be.getEnergyStorage().amount = 0L;
		AlaGameTestHelper.drive(be, helper, driveTicks());
		assertOutputEmpty(be, helper, "an unpowered smelter must not produce");
		helper.succeed();
	}

	// ── REG01: the anti-deadlock input filter ──────────────────────────────────────────────────────

	/**
	 * An input slot refuses an item that already sits in another input slot.
	 *
	 * <p>This is what stops a hopper full of copper from filling all three inputs with copper — a set
	 * no recipe matches and which a hopper cannot undo, leaving the machine dead until a player
	 * intervenes.
	 */
	public static void reg01DuplicateInputRefused(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_0, copper(1));
		if (be.canPlaceItem(AlloySmelterBlockEntity.INPUT_SLOT_1, copper(1))) {
			helper.fail("a second input slot accepted copper while copper already sat in slot 0 — "
					+ "a hopper could fill every input with one metal and deadlock the machine");
		}
		// …while a metal the recipe still needs is welcome in the very same slot.
		if (!be.canPlaceItem(AlloySmelterBlockEntity.INPUT_SLOT_1, tin(1))) {
			helper.fail("the duplicate filter also rejected tin, which the bronze recipe needs");
		}
		helper.succeed();
	}

	/**
	 * Three DIFFERENT valid components must not be able to occupy all three inputs.
	 *
	 * <p>The duplicate rule alone does not cover this: copper, tin and nickel are each a component of
	 * some alloy, so three hoppers would fill every slot and leave a state no recipe matches — permanent,
	 * because a hopper cannot pull its mistake back out. The cap is read from the loaded recipes, so this
	 * stays correct if a three-component alloy is ever added.
	 */
	public static void reg05DistinctMetalsCannotFillEverySlot(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_0, copper(1));
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_1, tin(1));
		if (be.canPlaceItem(AlloySmelterBlockEntity.INPUT_SLOT_2,
				new ItemStack(ModContent.NICKEL_INGOT.get()))) {
			helper.fail("a third distinct metal was accepted while no shipped alloy takes three "
					+ "components — hoppers could strand the machine in a state nothing matches");
		}
		helper.succeed();
	}

	/**
	 * The global speed multiplier is documented as energy-neutral: one operation must cost the recipe's
	 * EU whatever the multiplier is.
	 */
	public static void reg06SpeedMultiplierKeepsOperationCost(GameTestHelper helper) {
		float original = Config.globalMachineSpeedMultiplier;
		try {
			Config.globalMachineSpeedMultiplier = 2.0f;
			AlloySmelterBlockEntity be = place(helper);
			load(be, copper(3), tin(1), ItemStack.EMPTY);
			long before = 20_000L;
			be.getEnergyStorage().amount = before;
			// Drive without topping up, so the drop measures the true cost of the operation.
			for (int i = 0; i < Config.scaledDuration(Config.alloySmelterDuration) + 5; i++) {
				AlaGameTestHelper.drive(be, helper, 1);
			}
			assertOutput(be, helper, ModContent.BRONZE_INGOT.get(), 2, "3 copper + 1 tin at 2x speed");
			long spent = before - be.getEnergyStorage().amount;
			// The recipe costs 1200 EU; allow one tick of slack for integer rounding of the rate.
			long slack = 2L * euPerTickForTest();
            if (Math.abs(spent - 1200L) > slack) {
				helper.fail("a 2x-speed operation spent " + spent + " EU, expected the recipe's 1200 EU "
						+ "(the speed knob is documented energy-neutral)");
			}
			helper.succeed();
		} finally {
			Config.globalMachineSpeedMultiplier = original;
		}
	}

	private static int euPerTickForTest() {
		return Math.max(1, Math.round(Config.alloySmelterEuPerTick * Config.globalMachineSpeedMultiplier));
	}

	/** An item no alloy recipe uses is refused outright. */
	public static void reg02NonComponentRefused(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		if (be.canPlaceItem(AlloySmelterBlockEntity.INPUT_SLOT_0, new ItemStack(Items.DIRT))) {
			helper.fail("the smelter accepted dirt as an alloy component");
		}
		helper.succeed();
	}

	/** The output slot never accepts a manual insert. */
	public static void reg03OutputSlotRefusesInsert(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		if (be.canPlaceItem(AlloySmelterBlockEntity.OUTPUT_SLOT, copper(1))) {
			helper.fail("the output slot accepted an insert");
		}
		helper.succeed();
	}

	/** Swapping a component mid-melt restarts the operation instead of crediting the old mix. */
	public static void reg04InputSwapResetsProgress(GameTestHelper helper) {
		AlloySmelterBlockEntity be = place(helper);
		load(be, copper(3), tin(1), ItemStack.EMPTY);
		drivePowered(be, helper, driveTicks() / 2);
		// Progress rides the shared ContainerData channel at index 2 (see MachineBlockEntity).
		if (be.getDataAccess().get(2) <= 0) {
			helper.fail("sanity: the smelter should have accumulated progress before the swap");
		}
		// Swap the metal in a slot the shared base class does not watch (it only guards slot 0).
		be.setItem(AlloySmelterBlockEntity.INPUT_SLOT_1, new ItemStack(ModContent.NICKEL_INGOT.get()));
		if (be.getDataAccess().get(2) != 0) {
			helper.fail("changing an input mid-operation must restart it, progress was "
					+ be.getDataAccess().get(2));
		}
		helper.succeed();
	}
}
