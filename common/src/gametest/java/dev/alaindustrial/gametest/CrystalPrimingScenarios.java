package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.item.energy.CrystalBlankItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the EU crystals (MOD-504).
 *
 * <p>The mechanic is two items per tier: a <b>blank</b> with an EU buffer that gives nothing back, and
 * the <b>finished crystal</b> — a plain item with no energy at all — that the blank turns into the
 * moment its buffer fills. Three properties carry the whole design, and each is the kind that fails
 * silently:
 *
 * <ul>
 *   <li><b>A full blank becomes the crystal.</b> If the swap is missed at a charging site, the blank
 *       simply stays a blank forever and no exception is thrown anywhere.</li>
 *   <li><b>A blank never gives energy back.</b> The guard lives in {@code ItemEnergy}; without it a
 *       discharge slot drains a half-filled blank, which turns the ladder into a free EU tank.</li>
 *   <li><b>The finished crystal holds no energy.</b> It must have zero capacity, or it would show a
 *       charge bar and a discharge slot would happily pull energy out of a crafting material.</li>
 * </ul>
 */
public final class CrystalPrimingScenarios {

	private CrystalPrimingScenarios() {}

	private static final BlockPos STORE = new BlockPos(1, 2, 1);

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

	/**
	 * CRYSTAL-01 — a blank one tick short of full becomes the crystal on the tick that fills it.
	 *
	 * <p>Driven from just below the top rather than from empty so the test stays a few ticks long
	 * whatever the configured price is: the blank is pre-charged to {@code capacity - 1}, and a single
	 * server tick has to both finish the charge and perform the swap.
	 */
	public static void crystal01FullBlankBecomesCrystal(GameTestHelper helper) {
		BatteryBoxBlockEntity be = placeStore(helper);
		if (be == null) {
			return;
		}
		ItemStack blank = new ItemStack(ModContent.ENERGY_CRYSTAL_BLANK.get());
		ItemEnergy.set(blank, ItemEnergy.capacity(blank) - 1);
		be.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);
		be.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, blank);

		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());

		ItemStack after = be.getItem(BatteryBoxBlockEntity.CHARGE_SLOT);
		if (!after.is(ModContent.ENERGY_CRYSTAL.get())) {
			helper.fail("a full blank did not become the crystal: slot holds "
					+ after.getItem() + " with " + ItemEnergy.get(after) + " EU");
			return;
		}
		if (ItemEnergy.capacity(after) != 0) {
			helper.fail("the finished crystal still has an EU buffer of " + ItemEnergy.capacity(after)
					+ " — it must be a plain item");
			return;
		}
		helper.succeed();
	}

	/**
	 * CRYSTAL-02 — a part-filled blank is never drained.
	 *
	 * <p>Put a half-charged blank in the discharge slot and tick. The store must gain nothing and the
	 * blank must keep every EU. Remove the guard in {@code ItemEnergy.stackAdd} and the store banks
	 * energy the blank never gave up — EU created from nothing.
	 */
	public static void crystal02BlankRefusesToDischarge(GameTestHelper helper) {
		BatteryBoxBlockEntity be = placeStore(helper);
		if (be == null) {
			return;
		}
		ItemStack blank = new ItemStack(ModContent.ENERGY_CRYSTAL_BLANK.get());
		long half = ItemEnergy.capacity(blank) / 2;
		ItemEnergy.set(blank, half);
		be.getEnergyStorage().setAmountUntracked(0);
		be.setItem(BatteryBoxBlockEntity.DISCHARGE_SLOT, blank);

		be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());

		long banked = be.getEnergyStorage().getAmount();
		ItemStack after = be.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT);
		if (banked != 0) {
			helper.fail("the store drained an unfinished blank: banked " + banked + " EU");
			return;
		}
		if (ItemEnergy.get(after) != half) {
			helper.fail("the blank lost charge in a discharge slot: " + ItemEnergy.get(after)
					+ " instead of " + half);
			return;
		}
		helper.succeed();
	}

	/**
	 * CRYSTAL-03 — every tier's blank finishes into its own crystal, and no crystal carries a buffer.
	 *
	 * <p>Walks the whole ladder through {@link CrystalBlankItem#promote} rather than through a machine:
	 * the swap decision is one method, and this pins that each tier maps to the right item. A copy-paste
	 * slip in the tier switch — a lapotron blank finishing into an energy crystal — passes every
	 * single-tier test and is caught only here.
	 */
	public static void crystal03EveryTierFinishesIntoItsOwn(GameTestHelper helper) {
		record Pair(ItemStack blank, net.minecraft.world.item.Item expected) {}
		Pair[] ladder = {
			new Pair(new ItemStack(ModContent.ENERGY_CRYSTAL_BLANK.get()), ModContent.ENERGY_CRYSTAL.get()),
			new Pair(new ItemStack(ModContent.LAPOTRON_CRYSTAL_BLANK.get()), ModContent.LAPOTRON_CRYSTAL.get()),
			new Pair(new ItemStack(ModContent.RESONANT_CRYSTAL_BLANK.get()), ModContent.RESONANT_CRYSTAL.get()),
		};
		for (Pair pair : ladder) {
			ItemStack blank = pair.blank();
			long capacity = ItemEnergy.capacity(blank);
			if (capacity <= 0) {
				helper.fail(blank.getItem() + " has no EU buffer — every blank must be chargeable");
				return;
			}
			// One EU short: promote() must refuse, or "full" would mean nothing.
			ItemEnergy.set(blank, capacity - 1);
			if (!CrystalBlankItem.promote(blank).isEmpty()) {
				helper.fail(blank.getItem() + " finished one EU short of full");
				return;
			}
			ItemEnergy.set(blank, capacity);
			ItemStack finished = CrystalBlankItem.promote(blank);
			if (!finished.is(pair.expected())) {
				helper.fail(blank.getItem() + " finished into " + finished.getItem()
						+ " instead of " + pair.expected());
				return;
			}
			if (ItemEnergy.capacity(finished) != 0) {
				helper.fail(finished.getItem() + " is a finished crystal but still has an EU buffer");
				return;
			}
		}
		helper.succeed();
	}
}
