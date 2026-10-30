package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.AlaGameTestHelper.drive;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.HoneyCentrifugeBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.menu.HoneyCentrifugeMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loader-neutral gametest bodies for the Honey Centrifuge (MOD-924, suite TC-HONEY-CF). Both loaders replay
 * {@link Roster#ENTRIES} (ADR-038).
 *
 * <p>The recipe family is exercised end to end by every production scenario: an item → fluid family that
 * failed to bind on a loader would leave the machine idle there, and every yield check below would fail.
 */
public final class HoneyCentrifugeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(HoneyCentrifugeScenarios::fun01EachInputGivesItsHoney,
						"honey_centrifuge_each_input_gives_its_honey").ticks(400),
				RosterEntry.of(HoneyCentrifugeScenarios::con01FullTankStopsBeforeEatingInput,
						"honey_centrifuge_full_tank_stops_before_eating_input").ticks(400),
				RosterEntry.of(HoneyCentrifugeScenarios::con02BlockedBottleSlotKeepsTheBottle,
						"honey_centrifuge_blocked_bottle_slot_keeps_the_bottle").ticks(400),
				RosterEntry.of(HoneyCentrifugeScenarios::fun02EmptyBucketIsFilled,
						"honey_centrifuge_empty_bucket_is_filled").ticks(20, 60),
				RosterEntry.of(HoneyCentrifugeScenarios::con03NeighbourDrainsButCannotFill,
						"honey_centrifuge_neighbour_drains_but_cannot_fill").ticks(20, 60),
				RosterEntry.of(HoneyCentrifugeScenarios::reg01ShiftClickRoutesByWhatTheSlotTakes,
						"honey_centrifuge_shift_click_routes_by_what_the_slot_takes").ticks(20, 60));

		private Roster() {}
	}

	private HoneyCentrifugeScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Far above one operation's cost (200 EU), set directly so the tier packet cap is bypassed. */
	private static final long AMPLE_EU = 8000L;

	/** Ticks to drive: one full operation plus slack for the scaled-duration knob. */
	private static int oneOperation() {
		return MachineRates.duration(Config.honeyCentrifugeDuration, Config.globalMachineSpeedMultiplier) + 20;
	}

	private static HoneyCentrifugeBlockEntity place(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.HONEY_CENTRIFUGE.get());
		HoneyCentrifugeBlockEntity be = helper.getBlockEntity(pos, HoneyCentrifugeBlockEntity.class);
		if (be == null) {
			helper.fail("honey centrifuge block entity missing after placement");
		}
		return be;
	}

	private static void stock(HoneyCentrifugeBlockEntity be, long mb) {
		be.honeyTank.fluid = FluidHolder.of(ModContent.HONEY.get());
		be.honeyTank.amount = mb;
	}

	/** One input → the yield the owner decided, no more, no less; only the bottle gives a container back. */
	private record Yield(Item input, long honeyMb, Item remainder) {
	}

	private static final List<Yield> YIELDS = List.of(
			new Yield(Items.HONEYCOMB, 100, null),
			new Yield(Items.HONEYCOMB_BLOCK, 400, null),
			new Yield(Items.HONEY_BOTTLE, 250, Items.GLASS_BOTTLE),
			new Yield(Items.HONEY_BLOCK, 1000, null));

	// ── FUN01: each of the four inputs gives exactly its own volume ─────────────────────────────────

	/**
	 * Two of each input in four machines side by side; after one operation each machine has eaten ONE item
	 * and holds exactly that item's volume, and only the bottle handed back an empty bottle. Two in the slot,
	 * not one, so an operation that ate the whole stack — or none of it — shows.
	 *
	 * @implements TC-HONEY-CF-FUN01 — comb 100 mB, comb block 400, bottle 250 + glass bottle, honey block
	 * 1000; one item per operation.
	 */
	public static void fun01EachInputGivesItsHoney(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity[] machines = new HoneyCentrifugeBlockEntity[YIELDS.size()];
		for (int i = 0; i < YIELDS.size(); i++) {
			machines[i] = place(helper, new BlockPos(1 + i, 2, 1));
			machines[i].getEnergyStorage().setAmountUntracked(AMPLE_EU);
			machines[i].setItem(HoneyCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(YIELDS.get(i).input(), 2));
		}
		for (HoneyCentrifugeBlockEntity be : machines) {
			drive(be, helper, oneOperation());
		}
		for (int i = 0; i < YIELDS.size(); i++) {
			Yield yield = YIELDS.get(i);
			HoneyCentrifugeBlockEntity be = machines[i];
			String what = yield.input().toString();
			if (be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).getCount() != 1) {
				helper.fail(what + ": one operation must eat exactly one item, left "
						+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).getCount());
				return;
			}
			if (be.honeyTank.amount != yield.honeyMb() || !be.honeyTank.fluid.is(ModContent.HONEY.get())) {
				helper.fail(what + ": expected " + yield.honeyMb() + " mB of honey, tank holds "
						+ be.honeyTank.amount + " mB of " + be.honeyTank.fluid);
				return;
			}
			ItemStack back = be.getItem(HoneyCentrifugeBlockEntity.REMAINDER_SLOT);
			boolean expected = yield.remainder() == null ? back.isEmpty()
					: back.is(yield.remainder()) && back.getCount() == 1;
			if (!expected) {
				helper.fail(what + ": container slot holds " + back + ", expected "
						+ (yield.remainder() == null ? "nothing" : "1 " + yield.remainder()));
				return;
			}
		}
		helper.succeed();
	}

	// ── CON01: a tank without room for the next portion stops the machine before it eats the input ───

	/**
	 * 9950 mB in the tank leaves no room for a comb's 100 mB: the comb stays, the tank stays, nothing is
	 * voided. The same machine with 9900 mB fills to exactly 10 000 — the guard is "room", not "below full".
	 *
	 * @implements TC-HONEY-CF-CON01 — a tank without room for the yield stops the machine with the input
	 * intact; a yield that fits exactly is still made.
	 */
	public static void con01FullTankStopsBeforeEatingInput(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity be = place(helper, POS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		long capacity = HoneyCentrifugeBlockEntity.TANK_CAPACITY;
		stock(be, capacity - 50);
		be.setItem(HoneyCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(Items.HONEYCOMB));

		drive(be, helper, oneOperation());

		if (be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).getCount() != 1 || be.honeyTank.amount != capacity - 50) {
			helper.fail("a tank without room must keep the comb and its honey: input "
					+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT) + ", tank " + be.honeyTank.amount + " mB");
			return;
		}
		stock(be, capacity - 100);
		drive(be, helper, oneOperation());
		if (!be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).isEmpty() || be.honeyTank.amount != capacity) {
			helper.fail("a yield that fits exactly must be made: input "
					+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT) + ", tank " + be.honeyTank.amount + " mB");
			return;
		}
		helper.succeed();
	}

	// ── CON02: a full container slot freezes a bottle's operation ──────────────────────────────────

	/**
	 * With 64 glass bottles already in the container slot, a honey bottle may not be spun: the bottle and
	 * the tank stay as they were.
	 *
	 * @implements TC-HONEY-CF-CON02 — a full container slot stops a honey bottle's operation: no honey, no
	 * lost bottle.
	 */
	public static void con02BlockedBottleSlotKeepsTheBottle(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity be = place(helper, POS);
		be.getEnergyStorage().setAmountUntracked(AMPLE_EU);
		be.setItem(HoneyCentrifugeBlockEntity.INPUT_SLOT, new ItemStack(Items.HONEY_BOTTLE));
		be.setItem(HoneyCentrifugeBlockEntity.REMAINDER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 64));

		drive(be, helper, oneOperation());

		if (be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).getCount() != 1 || be.honeyTank.amount != 0
				|| be.getItem(HoneyCentrifugeBlockEntity.REMAINDER_SLOT).getCount() != 64) {
			helper.fail("a blocked container slot must stop the bottle: input "
					+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT) + ", tank " + be.honeyTank.amount
					+ " mB, bottles " + be.getItem(HoneyCentrifugeBlockEntity.REMAINDER_SLOT).getCount());
			return;
		}
		helper.succeed();
	}

	// ── FUN02: an empty bucket in the container pair is filled from the tank ─────────────────────────

	/**
	 * 1000 mB of honey and an empty bucket: the bucket comes out filled with honey and the tank is empty.
	 *
	 * @implements TC-HONEY-CF-FUN02 — an empty bucket in the container slot is filled with 1000 mB of honey.
	 */
	public static void fun02EmptyBucketIsFilled(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity be = place(helper, POS);
		stock(be, FluidAmounts.BUCKET);
		be.setItem(HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT, new ItemStack(Items.BUCKET));

		drive(be, helper, 2);

		ItemStack out = be.getItem(HoneyCentrifugeBlockEntity.BUCKET_OUTPUT_SLOT);
		if (!out.is(ModContent.HONEY_BUCKET.get())) {
			helper.fail("expected a honey bucket in the container output, got " + out);
			return;
		}
		if (be.honeyTank.amount != 0 || !be.getItem(HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT).isEmpty()) {
			helper.fail("the bucket must take the whole 1000 mB: tank " + be.honeyTank.amount + " mB, input "
					+ be.getItem(HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT));
			return;
		}
		helper.succeed();
	}

	// ── CON03: the published port gives honey out and takes nothing in ──────────────────────────────

	/**
	 * Through {@code FluidLookup} — the path a pipe or a pump takes — a neighbour drains the tank and cannot
	 * pour anything into it, honey included.
	 *
	 * @implements TC-HONEY-CF-CON03 — the fluid port drains honey and refuses every insert.
	 */
	public static void con03NeighbourDrainsButCannotFill(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity be = place(helper, POS);
		stock(be, FluidAmounts.BUCKET);
		FluidPort port = FluidLookup.get().find(helper.getLevel(), helper.absolutePos(POS), Direction.UP);
		if (port == null) {
			helper.fail("no fluid port published for the honey centrifuge");
			return;
		}
		long[] in = {0};
		EnergyTransactions.get().runCommitting(txn ->
				in[0] = port.insert(FluidHolder.of(ModContent.HONEY.get()), FluidAmounts.BUCKET, txn));
		if (in[0] != 0 || be.honeyTank.amount != FluidAmounts.BUCKET) {
			helper.fail("the tank must refuse inserts, took " + in[0] + " mB");
			return;
		}
		long[] out = {0};
		EnergyTransactions.get().runCommitting(txn ->
				out[0] = port.extract(FluidHolder.of(ModContent.HONEY.get()), 400, txn));
		if (out[0] != 400 || be.honeyTank.amount != FluidAmounts.BUCKET - 400) {
			helper.fail("a neighbour must drain honey, moved " + out[0] + " mB, tank " + be.honeyTank.amount);
			return;
		}
		helper.succeed();
	}

	// ── REG01: shift-click sends an item where it belongs ──────────────────────────────────────────

	/**
	 * Shift-clicking an empty bucket from the inventory puts it in the empty-container slot, not in the
	 * input; an item no recipe takes stays in the inventory; a honeycomb goes to the input. The first case was
	 * the review finding: the input slot took anything, and it comes first in the menu.
	 *
	 * @implements TC-HONEY-CF-REG01 — shift-click routes a bucket to the container slot, a comb to the input,
	 * and a foreign item to neither.
	 */
	public static void reg01ShiftClickRoutesByWhatTheSlotTakes(GameTestHelper helper) {
		HoneyCentrifugeBlockEntity be = place(helper, POS);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		HoneyCentrifugeMenu menu = new HoneyCentrifugeMenu(1, player.getInventory(), be,
				ContainerLevelAccess.create(helper.getLevel(), be.getBlockPos()));
		int inv = be.upgradeSlotStart() + HoneyCentrifugeBlockEntity.UPGRADE_SLOT_COUNT;

		menu.getSlot(inv).set(new ItemStack(Items.BUCKET));
		menu.quickMoveStack(player, inv);
		if (!be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).isEmpty()
				|| !be.getItem(HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT).is(Items.BUCKET)) {
			helper.fail("an empty bucket must go to the container slot: input "
					+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT) + ", container "
					+ be.getItem(HoneyCentrifugeBlockEntity.BUCKET_INPUT_SLOT));
			return;
		}
		menu.getSlot(inv + 1).set(new ItemStack(Items.COBBLESTONE));
		menu.quickMoveStack(player, inv + 1);
		if (!be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).isEmpty()) {
			helper.fail("an item no recipe takes must not enter the input: "
					+ be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT));
			return;
		}
		menu.getSlot(inv + 2).set(new ItemStack(Items.HONEYCOMB, 3));
		menu.quickMoveStack(player, inv + 2);
		if (!be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT).is(Items.HONEYCOMB)) {
			helper.fail("a honeycomb must go to the input, got " + be.getItem(HoneyCentrifugeBlockEntity.INPUT_SLOT));
			return;
		}
		helper.succeed();
	}
}
