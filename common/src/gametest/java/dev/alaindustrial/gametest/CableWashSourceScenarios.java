package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.CableDyeScenarios.cable;
import static dev.alaindustrial.gametest.CableDyeScenarios.click;
import static dev.alaindustrial.gametest.CableDyeScenarios.dyedRow;
import static dev.alaindustrial.gametest.CableDyeScenarios.hand;
import static dev.alaindustrial.gametest.CableDyeScenarios.holding;

import dev.alaindustrial.block.CableDyeing;
import dev.alaindustrial.item.fluid.FluidTankContents;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 scenarios for washing a dyed cable with water that is not a bucket (MOD-912): the water bottle, the
 * mod's portable tank and another mod's canister. A plain click costs 250 mB for one segment, Shift 1000 mB
 * for the run; a container short of the run's bucket washes the clicked segment only.
 */
public final class CableWashSourceScenarios {

	/**
	 * The "other mod's" water canister: a vanilla item the mod has no business with, given a fluid
	 * capability only by the gametest stand-ins of both loaders ({@code ForeignWaterItemMod},
	 * {@code ForeignWaterItemStandIn}). It holds water in millibuckets in {@code repair_cost}, up to a bucket.
	 */
	public static final Item FOREIGN_CANISTER = Items.NAUTILUS_SHELL;

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh08_waterBottleWashesOneSegment,
						"tc_dye001_wsh08_water_bottle_washes_one_segment").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh09_shiftWithABottleWashesOneSegment,
						"tc_dye001_wsh09_shift_with_a_bottle_washes_one_segment").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh10_bottleOnACleanCableIsAbsorbed,
						"tc_dye001_wsh10_bottle_on_a_clean_cable_is_absorbed").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh11_tankPaysByTheAmount,
						"tc_dye001_wsh11_tank_pays_by_the_amount").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh12_tankWithNothingToWashLeavesTheClick,
						"tc_dye001_wsh12_tank_with_nothing_to_wash_leaves_the_click").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh13_foreignCanisterPaysByTheAmount,
						"tc_dye001_wsh13_foreign_canister_pays_by_the_amount").ticks(80),
				RosterEntry.of(CableWashSourceScenarios::tcDye001Wsh14_creativeKeepsTheBottle,
						"tc_dye001_wsh14_creative_keeps_the_bottle").ticks(80));

		private Roster() {}
	}

	private static final BlockPos FIRST = new BlockPos(1, 2, 1);
	private static final BlockPos SECOND = new BlockPos(2, 2, 1);
	private static final BlockPos THIRD = new BlockPos(3, 2, 1);

	private CableWashSourceScenarios() {
	}

	private static ItemStack waterBottle() {
		return PotionContents.createItemStack(Items.POTION, Potions.WATER);
	}

	private static boolean isWaterBottle(ItemStack stack) {
		return stack.is(Items.POTION)
				&& stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
	}

	private static ItemStack tankOfWater(long mb) {
		ItemStack tank = new ItemStack(ModContent.FLUID_TANK_ITEM.get());
		tank.set(ModDataComponents.FLUID_TANK_CONTENTS.get(),
				new FluidTankContents(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER), mb));
		tank.set(DataComponents.MAX_STACK_SIZE, 1);
		return tank;
	}

	/** Water in the tank in hand; -1 when the hand holds no tank. */
	private static long tankWater(ServerPlayer player) {
		ItemStack held = hand(player);
		if (!held.is(ModContent.FLUID_TANK_ITEM.get())) {
			return -1L;
		}
		FluidTankContents contents = held.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		return contents == null ? 0L : contents.amount();
	}

	private static ItemStack canisterOfWater(int mb) {
		ItemStack canister = new ItemStack(FOREIGN_CANISTER);
		canister.set(DataComponents.REPAIR_COST, mb);
		return canister;
	}

	/** Water in the canister in hand; -1 when the hand holds no canister. */
	private static int canisterWater(ServerPlayer player) {
		ItemStack held = hand(player);
		return held.is(FOREIGN_CANISTER) ? held.getOrDefault(DataComponents.REPAIR_COST, 0) : -1;
	}

	private static boolean failUnlessColours(GameTestHelper helper, String why, DyeColor... expected) {
		for (int i = 0; i < expected.length; i++) {
			DyeColor actual = cable(helper, new BlockPos(1 + i, 2, 1)).color();
			if (actual != expected[i]) {
				helper.fail(why + ": the segment at x=" + (1 + i) + " is " + actual + ", expected " + expected[i]);
				return true;
			}
		}
		return false;
	}

	/**
	 * A water bottle washes the clicked segment only and comes back as an empty glass bottle.
	 *
	 * @implements TC-DYE-001-WSH08 — a water bottle washes one segment and turns into a glass bottle.
	 */
	public static void tcDye001Wsh08_waterBottleWashesOneSegment(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_COPPER_CABLE.get(), DyeColor.RED, DyeColor.RED);
		ServerPlayer player = holding(helper, waterBottle());
		click(helper, player, FIRST);
		if (failUnlessColours(helper, "a water bottle washes the clicked segment", null, DyeColor.RED)) {
			return;
		}
		if (!hand(player).is(Items.GLASS_BOTTLE) || hand(player).getCount() != 1) {
			helper.fail("the bottle must come back empty: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * Shift with a water bottle still washes the clicked segment only: a bottle is not a run's bucket.
	 *
	 * @implements TC-DYE-001-WSH09 — Shift with a water bottle washes one segment, not the run.
	 */
	public static void tcDye001Wsh09_shiftWithABottleWashesOneSegment(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_GOLD_CABLE.get(), DyeColor.BLUE, DyeColor.BLUE, DyeColor.BLUE);
		ServerPlayer player = holding(helper, waterBottle());
		player.setShiftKeyDown(true);
		click(helper, player, SECOND);
		if (failUnlessColours(helper, "Shift with a bottle washes the clicked segment only",
				DyeColor.BLUE, null, DyeColor.BLUE)) {
			return;
		}
		if (!hand(player).is(Items.GLASS_BOTTLE)) {
			helper.fail("the bottle must come back empty: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * A water bottle on a clean insulated cable, on a clean run with Shift and on a bare cable takes the
	 * click — so it is neither drunk nor poured — and spends nothing.
	 *
	 * <p>A potion is drunk from {@code use()}, which the client sends only after a click on the block that
	 * was not consumed, so the consumed result is what keeps it in the bottle.
	 *
	 * @implements TC-DYE-001-WSH10 — a water bottle with nothing to wash is not spent, drunk or poured.
	 */
	public static void tcDye001Wsh10_bottleOnACleanCableIsAbsorbed(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_TIN_CABLE.get(), null, null);
		BlockPos bare = new BlockPos(1, 2, 3);
		helper.setBlock(bare, ModContent.COPPER_CABLE.get());
		BlockPos[] targets = {FIRST, SECOND, bare};
		boolean[] shift = {false, true, false};
		for (int i = 0; i < targets.length; i++) {
			ServerPlayer player = holding(helper, waterBottle());
			player.setShiftKeyDown(shift[i]);
			InteractionResult result = click(helper, player, targets[i]);
			String what = targets[i] + (shift[i] ? " with Shift" : "");
			if (!result.consumesAction()) {
				helper.fail("a water bottle on the cable at " + what + " was not taken (result " + result
						+ "), so it would be drunk");
				return;
			}
			if (!isWaterBottle(hand(player)) || player.isUsingItem()) {
				helper.fail("nothing to wash at " + what + ", yet the bottle was spent or drunk: " + hand(player));
				return;
			}
		}
		if (failUnlessColours(helper, "a wash with nothing to wash changes nothing", null, null)
				|| cable(helper, bare).color() != null) {
			return;
		}
		helper.succeed();
	}

	/**
	 * The portable tank pays by the amount: 250 mB for a plain click, 1000 mB for a Shift run; a tank short
	 * of a bucket washes only the clicked segment with Shift, and the last 250 mB leave an empty tank.
	 *
	 * @implements TC-DYE-001-WSH11 — the portable tank of water washes for 250 mB a segment and 1000 mB a run.
	 */
	public static void tcDye001Wsh11_tankPaysByTheAmount(GameTestHelper helper) {
		Block copper = ModContent.INSULATED_COPPER_CABLE.get();
		dyedRow(helper, copper, DyeColor.LIME, DyeColor.LIME, DyeColor.LIME);
		ServerPlayer player = holding(helper, tankOfWater(1500));
		click(helper, player, FIRST);
		if (failUnlessColours(helper, "a plain click with the tank", null, DyeColor.LIME, DyeColor.LIME)) {
			return;
		}
		if (tankWater(player) != 1250) {
			helper.fail("a plain click costs 250 mB: the tank holds " + tankWater(player));
			return;
		}
		player.setShiftKeyDown(true);
		click(helper, player, SECOND);
		if (failUnlessColours(helper, "Shift with the tank washes the run", null, null, null)) {
			return;
		}
		if (tankWater(player) != 250) {
			helper.fail("a Shift run costs 1000 mB: the tank holds " + tankWater(player));
			return;
		}
		dyedRow(helper, copper, DyeColor.CYAN, DyeColor.CYAN, DyeColor.CYAN);
		click(helper, player, SECOND);
		if (failUnlessColours(helper, "Shift with 250 mB left washes the clicked segment only",
				DyeColor.CYAN, null, DyeColor.CYAN)) {
			return;
		}
		if (tankWater(player) != 0 || hand(player).has(DataComponents.MAX_STACK_SIZE)) {
			helper.fail("the last 250 mB must leave a plain empty tank, hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}

	/**
	 * A tank of water with nothing to wash, or too little water, does not take the click: the tank is a
	 * block and must still be placeable against a cable. Asked of the hook itself — a click that passes
	 * goes on to place the tank.
	 *
	 * @implements TC-DYE-001-WSH12 — the tank leaves the click alone when it washes nothing.
	 */
	public static void tcDye001Wsh12_tankWithNothingToWashLeavesTheClick(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_ELECTRUM_CABLE.get(), null, DyeColor.PINK);
		long[] water = {1000, 100};
		BlockPos[] targets = {FIRST, SECOND};
		for (int i = 0; i < targets.length; i++) {
			ServerPlayer player = holding(helper, tankOfWater(water[i]));
			BlockPos abs = helper.absolutePos(targets[i]);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
			InteractionResult result = CableDyeing.tryWash(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
			if (result != InteractionResult.PASS) {
				helper.fail("a tank of " + water[i] + " mB on the cable at " + targets[i]
						+ " must leave the click (got " + result + ")");
				return;
			}
			if (tankWater(player) != water[i]) {
				helper.fail("a tank that washed nothing must keep its water, holds " + tankWater(player));
				return;
			}
		}
		if (failUnlessColours(helper, "the tank washed nothing", null, DyeColor.PINK)) {
			return;
		}
		helper.succeed();
	}

	/**
	 * Another mod's water canister washes through the loader's item fluid API and pays by the amount, the
	 * same way as the portable tank.
	 *
	 * @implements TC-DYE-001-WSH13 — another mod's water container washes through the fluid API by the amount.
	 */
	public static void tcDye001Wsh13_foreignCanisterPaysByTheAmount(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_TIN_CABLE.get(), DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.YELLOW);
		ServerPlayer player = holding(helper, canisterOfWater(1000));
		click(helper, player, FIRST);
		if (failUnlessColours(helper, "a plain click with the canister", null, DyeColor.YELLOW, DyeColor.YELLOW)) {
			return;
		}
		if (canisterWater(player) != 750) {
			helper.fail("a plain click costs 250 mB of the canister, it holds " + canisterWater(player));
			return;
		}
		player.setShiftKeyDown(true);
		click(helper, player, SECOND);
		if (failUnlessColours(helper, "Shift with 750 mB in the canister washes the clicked segment only",
				null, null, DyeColor.YELLOW)) {
			return;
		}
		if (canisterWater(player) != 500) {
			helper.fail("a canister short of a bucket pays 250 mB on Shift, it holds " + canisterWater(player));
			return;
		}
		ServerPlayer full = holding(helper, canisterOfWater(1000));
		full.setShiftKeyDown(true);
		dyedRow(helper, ModContent.INSULATED_TIN_CABLE.get(), DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.YELLOW);
		click(helper, full, THIRD);
		if (failUnlessColours(helper, "Shift with a full canister washes the run", null, null, null)) {
			return;
		}
		if (canisterWater(full) != 0) {
			helper.fail("a Shift run costs the canister's 1000 mB, it holds " + canisterWater(full));
			return;
		}
		helper.succeed();
	}

	/**
	 * In creative a water bottle still washes and stays full.
	 *
	 * @implements TC-DYE-001-WSH14 — a creative wash keeps the water bottle full.
	 */
	public static void tcDye001Wsh14_creativeKeepsTheBottle(GameTestHelper helper) {
		dyedRow(helper, ModContent.INSULATED_GOLD_CABLE.get(), DyeColor.BLACK);
		ServerPlayer player = holding(helper, waterBottle());
		player.getAbilities().instabuild = true;
		click(helper, player, FIRST);
		if (failUnlessColours(helper, "a creative wash still washes", (DyeColor) null)) {
			return;
		}
		if (!isWaterBottle(hand(player))) {
			helper.fail("creative must keep the bottle full: hand holds " + hand(player));
			return;
		}
		helper.succeed();
	}
}
