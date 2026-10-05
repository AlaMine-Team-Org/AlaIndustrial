package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.IronFurnaceBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How the iron furnace reads fuel (MOD-703, batch 0): the burn time it lights from coal and from a lava
 * bucket, and which stacks its fuel slot takes.
 *
 * <p>The fuel lookup is a version seam — 26.3 reads the stack's {@code minecraft:cooking_fuel}
 * component, 26.2 asks the level's {@code FuelValues} table — and before this suite the iron furnace's
 * fuel path was observed only in passing by an item-pipe delivery test. The expected numbers are
 * vanilla's own (coal 1600 ticks, a lava bucket 20000), scaled by the furnace's speed ratio exactly as
 * {@code IronFurnaceBlockEntity.serverTick} scales them, so smelts per fuel stay vanilla's.
 */
public final class IronFurnaceFuelScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(IronFurnaceFuelScenarios::coalBurnsForItsVanillaTime,
								"iron_furnace_coal_burns_for_its_vanilla_time")
						.fabricId("IronFurnaceFuelGameTest", "coalBurnsForItsVanillaTime").ticks(100),
				RosterEntry.of(IronFurnaceFuelScenarios::lavaBucketBurnsAndLeavesItsBucket,
								"iron_furnace_lava_bucket_burns_and_leaves_its_bucket")
						.fabricId("IronFurnaceFuelGameTest", "lavaBucketBurnsAndLeavesItsBucket").ticks(100),
				RosterEntry.of(IronFurnaceFuelScenarios::fuelSlotTakesFuelAndOneEmptyBucket,
								"iron_furnace_fuel_slot_takes_fuel_and_one_empty_bucket")
						.fabricId("IronFurnaceFuelGameTest", "fuelSlotTakesFuelAndOneEmptyBucket").ticks(100));

		private Roster() {}
	}

	private static final BlockPos FURNACE = new BlockPos(1, 2, 1);
	/** Ticks enough for the furnace to light: it lights on its first tick with something to cook. */
	private static final int LIGHT_TICKS = 5;
	private static final int COAL_BURN = 1600;
	private static final int LAVA_BUCKET_BURN = 20000;
	/** The reference smelt time the burn is scaled against (IronFurnaceBlockEntity.VANILLA_SMELT_TIME). */
	private static final int VANILLA_SMELT_TIME = 200;

	private IronFurnaceFuelScenarios() {
	}

	/**
	 * Coal lights the furnace for vanilla's 1600 ticks, scaled to the furnace's speed.
	 *
	 * @implements MOD-703 — coal lights the iron furnace for vanilla's burn time, scaled to its speed.
	 */
	public static void coalBurnsForItsVanillaTime(GameTestHelper helper) {
		assertLightsFor(helper, Items.COAL, COAL_BURN, false);
	}

	/**
	 * A lava bucket lights it for 20000 ticks, scaled, and leaves the empty bucket in the fuel slot.
	 *
	 * @implements MOD-703 — a lava bucket burns for vanilla's time and leaves its empty bucket.
	 */
	public static void lavaBucketBurnsAndLeavesItsBucket(GameTestHelper helper) {
		assertLightsFor(helper, Items.LAVA_BUCKET, LAVA_BUCKET_BURN, true);
	}

	/**
	 * The fuel slot takes fuel, refuses what does not burn, and takes an empty bucket only while the slot
	 * holds no bucket already — the vanilla furnace's rule.
	 *
	 * @implements MOD-703 — the fuel slot takes fuel and one empty bucket, and refuses what does not burn.
	 */
	public static void fuelSlotTakesFuelAndOneEmptyBucket(GameTestHelper helper) {
		helper.setBlock(FURNACE, ModContent.IRON_FURNACE.get());
		IronFurnaceBlockEntity furnace = furnace(helper);
		if (furnace == null) {
			return;
		}
		int slot = IronFurnaceBlockEntity.FUEL_SLOT;
		if (!furnace.canPlaceItem(slot, new ItemStack(Items.COAL))) {
			helper.fail("the fuel slot must take coal");
			return;
		}
		if (!furnace.canPlaceItem(slot, new ItemStack(Items.LAVA_BUCKET))) {
			helper.fail("the fuel slot must take a lava bucket");
			return;
		}
		if (furnace.canPlaceItem(slot, new ItemStack(Items.DIRT))) {
			helper.fail("the fuel slot must refuse dirt, which does not burn");
			return;
		}
		if (!furnace.canPlaceItem(slot, new ItemStack(Items.BUCKET))) {
			helper.fail("the fuel slot must take an empty bucket while it holds none");
			return;
		}
		furnace.setItem(slot, new ItemStack(Items.BUCKET));
		if (furnace.canPlaceItem(slot, new ItemStack(Items.BUCKET))) {
			helper.fail("the fuel slot must refuse a second empty bucket on top of the first");
			return;
		}
		helper.succeed();
	}

	private static void assertLightsFor(GameTestHelper helper, Item fuel, int vanillaBurn, boolean leavesBucket) {
		helper.setBlock(FURNACE, ModContent.IRON_FURNACE.get());
		IronFurnaceBlockEntity furnace = furnace(helper);
		if (furnace == null) {
			return;
		}
		furnace.setItem(IronFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 1));
		furnace.setItem(IronFurnaceBlockEntity.FUEL_SLOT, new ItemStack(fuel, 1));
		int cookTotal = Math.max(1, Config.ironFurnaceCookTime);
		int expected = Math.max(1, (int) ((long) vanillaBurn * cookTotal / VANILLA_SMELT_TIME));

		helper.runAfterDelay(LIGHT_TICKS, () -> {
			int litDuration = furnace.saveCustomOnly(helper.getLevel().registryAccess()).getIntOr("lit_duration", -1);
			if (litDuration != expected) {
				helper.fail(fuel + " lit the iron furnace for " + litDuration + " ticks, expected " + expected
						+ " (vanilla " + vanillaBurn + " scaled by " + cookTotal + "/" + VANILLA_SMELT_TIME + ")");
				return;
			}
			ItemStack left = furnace.getItem(IronFurnaceBlockEntity.FUEL_SLOT);
			if (leavesBucket ? !left.is(Items.BUCKET) : !left.isEmpty()) {
				helper.fail("after burning " + fuel + " the fuel slot holds " + left + ", expected "
						+ (leavesBucket ? "the empty bucket" : "nothing"));
				return;
			}
			helper.succeed();
		});
	}

	private static IronFurnaceBlockEntity furnace(GameTestHelper helper) {
		IronFurnaceBlockEntity be = helper.getBlockEntity(FURNACE, IronFurnaceBlockEntity.class);
		if (be == null) {
			helper.fail("fixture error: no iron furnace block entity at " + FURNACE);
		}
		return be;
	}
}
