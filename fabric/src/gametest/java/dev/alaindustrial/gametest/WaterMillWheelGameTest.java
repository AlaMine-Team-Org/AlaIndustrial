package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
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

	/**
	 * The wheel renderer drives blade rotation from {@code dataAccess} slot 2 (progress == water-face
	 * count). This locks the server-side value the renderer consumes: it must track the live number of
	 * adjacent water faces, not stay at the placement default. (The client SYNC of this value — the actual
	 * MOD-173 fix that keeps the blades turning — is a client-render effect verified in the dev client;
	 * a server-only gametest cannot observe whether a block-entity update packet reached a client.)
	 */
	@GameTest
	public void waterMillWheel_progressTracksWaterFaces(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper); // water on EAST -> 1 face
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		AlaGameTestHelper.drive(mill, helper, 3);
		if (mill.getDataAccess().get(2) != 1) {
			helper.fail("water mill progress (wheel-renderer input) should be 1 with one water face, was "
					+ mill.getDataAccess().get(2));
		}
		helper.setBlock(POS.relative(Direction.NORTH), Blocks.WATER); // add a second water face
		AlaGameTestHelper.drive(mill, helper, 3);
		if (mill.getDataAccess().get(2) != 2) {
			helper.fail("water mill progress should track the added water face (expected 2), was "
					+ mill.getDataAccess().get(2));
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

	/**
	 * The water mill's shaped recipe resolves and yields a water mill (MOD-174: pattern
	 * {@code PUP/CGC/PUP} — planks in the corners, copper ingots top/bottom-center, copper cable on
	 * the sides, a wooden gear in the center). Guards the recipe against a silent break or ingredient
	 * drift when the crafting cost is retuned.
	 */
	@GameTest
	public void waterMill_craftingRecipeResolves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();
		ItemStack planks = new ItemStack(Items.OAK_PLANKS);
		ItemStack copper = new ItemStack(Items.COPPER_INGOT);
		ItemStack cable = new ItemStack(ModContent.COPPER_CABLE_ITEM.get());
		ItemStack gear = new ItemStack(ModContent.WOODEN_GEAR.get());
		CraftingInput input = CraftingInput.of(3, 3, List.of(
				planks, copper, planks,
				cable, gear, cable,
				planks, copper, planks));
		RecipeHolder<CraftingRecipe> recipe =
				recipes.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail("water_mill crafting recipe did not resolve (expected PUP/CGC/PUP)");
			return;
		}
		ItemStack out = recipe.value().assemble(input);
		if (!out.is(ModContent.WATER_MILL_ITEM.get())) {
			helper.fail("water_mill recipe produced " + out + " (expected water_mill)");
		}
		helper.succeed();
	}

	/**
	 * The water mill wheel's shaped recipe resolves and yields a wheel (MOD-174: pattern
	 * {@code PUP/SGS/PUP} — planks in the corners, copper ingots top/bottom-center, sticks on the
	 * sides, a wooden gear in the center).
	 */
	@GameTest
	public void waterMillWheel_craftingRecipeResolves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();
		ItemStack planks = new ItemStack(Items.OAK_PLANKS);
		ItemStack copper = new ItemStack(Items.COPPER_INGOT);
		ItemStack stick = new ItemStack(Items.STICK);
		ItemStack gear = new ItemStack(ModContent.WOODEN_GEAR.get());
		CraftingInput input = CraftingInput.of(3, 3, List.of(
				planks, copper, planks,
				stick, gear, stick,
				planks, copper, planks));
		RecipeHolder<CraftingRecipe> recipe =
				recipes.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (recipe == null) {
			helper.fail("water_mill_wheel crafting recipe did not resolve (expected PUP/SGS/PUP)");
			return;
		}
		ItemStack out = recipe.value().assemble(input);
		if (!out.is(ModContent.WATER_MILL_WHEEL.get())) {
			helper.fail("water_mill_wheel recipe produced " + out + " (expected water_mill_wheel)");
		}
		helper.succeed();
	}

	// ── MOD-175: wheel interference — two mills whose wheels overlap hide the wheel and stall ──────

	/** Place a water mill with the given FACING and install a wheel (the disc that can interfere). */
	private static WaterMillBlockEntity placeMillWithWheel(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, ModBlocks.WATER_MILL.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, facing));
		WaterMillBlockEntity be = helper.getBlockEntity(pos, WaterMillBlockEntity.class);
		if (be == null) {
			helper.fail("water mill block entity missing after placement at " + pos);
		}
		be.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		return be;
	}

	private static void assertMode(GameTestHelper helper, WaterMillBlockEntity mill, String label, int expected) {
		int mode = mill.getDataAccess().get(3);
		if (mode != expected) {
			helper.fail(label + " mode = " + mode + "; expected " + expected);
		}
	}

	/**
	 * Two mills directly adjacent with the same FACING: the wheels are coplanar and overlap heavily, so
	 * BOTH report MODE_INTERFERENCE and produce nothing (symmetric — no tie-break). MOD-175 analogue of
	 * the wind-mill side-by-side interference.
	 */
	@GameTest
	public void waterMill_sideBySideInterference(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.NORTH);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "side-by-side mill A", WaterMillBlockEntity.MODE_INTERFERENCE);
		assertMode(helper, b, "side-by-side mill B", WaterMillBlockEntity.MODE_INTERFERENCE);
		helper.succeed();
	}

	/**
	 * Two mills facing each other across a single block: the wheels push in front of each mill and
	 * overlap in the gap, so both report MODE_INTERFERENCE.
	 */
	@GameTest
	public void waterMill_faceToFaceInterference(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.EAST);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(2), Direction.WEST);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "face-to-face mill A", WaterMillBlockEntity.MODE_INTERFERENCE);
		assertMode(helper, b, "face-to-face mill B", WaterMillBlockEntity.MODE_INTERFERENCE);
		helper.succeed();
	}

	/**
	 * Two mills spaced two empty blocks apart (same FACING): the larger wheels still clear each other,
	 * so neither interferes. The wheel is bigger than the wind-mill rotor, hence a two-block gap (vs one).
	 */
	@GameTest
	public void waterMill_spacedMillsDoNotInterfere(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(3), Direction.NORTH);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "spaced mill A", WaterMillBlockEntity.MODE_OK);
		assertMode(helper, b, "spaced mill B", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}

	/**
	 * Two mills back to back (adjacent, opposite FACING): each wheel sits on the far side of its own
	 * mill, the boxes never overlap, so neither interferes.
	 */
	@GameTest
	public void waterMill_backToBackDoNotInterfere(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.SOUTH);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "back-to-back mill A", WaterMillBlockEntity.MODE_OK);
		assertMode(helper, b, "back-to-back mill B", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}
}
