package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** L2 coverage for the MOD-024 installable water-wheel component gate. */
public class WaterMillWheelGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static WaterMillBlockEntity placeWithWater(GameTestHelper helper) {
		WaterMillBlockEntity mill =
				AlaGameTestHelper.place(helper, POS, ModBlocks.WATER_MILL, WaterMillBlockEntity.class);
		helper.setBlock(POS.relative(Direction.EAST), Blocks.WATER);
		return mill;
	}

	/** Water alone is insufficient: an empty component slot must keep the generator at 0 EU. */
	@GameTest
	public void waterMillWheel_missingWheelStopsGeneration(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper);
		AlaGameTestHelper.drive(mill, helper, 5);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("water mill generated EU without an installed water wheel");
		}
		helper.succeed();
	}

	/** Installing the dedicated wheel unlocks production without consuming the component. */
	@GameTest
	public void waterMillWheel_installedWheelEnablesGeneration(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper);
		ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, wheel);
		AlaGameTestHelper.drive(mill, helper, 5);
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("water mill with water and an installed wheel generated no EU");
		}
		if (!mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).is(ModContent.WATER_MILL_WHEEL.get())
				|| mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).getCount() != 1) {
			helper.fail("installed water wheel was consumed or replaced");
		}
		helper.succeed();
	}

	/** The component slot rejects unrelated items and accepts only water_mill_wheel. */
	@GameTest
	public void waterMillWheel_slotFilterIsDedicated(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper);
		if (mill.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(Items.STICK))) {
			helper.fail("water-wheel slot accepted an unrelated item");
		}
		if (!mill.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT,
				new ItemStack(ModContent.WATER_MILL_WHEEL.get()))) {
			helper.fail("water-wheel slot rejected water_mill_wheel");
		}
		helper.succeed();
	}
}
