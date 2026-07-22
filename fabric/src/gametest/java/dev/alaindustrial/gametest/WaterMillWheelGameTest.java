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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/** L2 coverage for the MOD-024 installable water-wheel component gate. */
public class WaterMillWheelGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * A FLOWING water block (LEVEL 1, {@code isSource() == false}) — the water mill counts only flowing
	 * water since MOD-188, and {@code Blocks.WATER.defaultBlockState()} is a still SOURCE (LEVEL 0)
	 * that no longer powers the mill. Placing this static flowing state is stable in these tests
	 * because {@link AlaGameTestHelper#drive} ticks only the mill's {@code serverTick}, never the
	 * world's fluid ticks, so the flowing block neither spreads nor dissipates.
	 */
	private static BlockState flowingWater() {
		return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
	}

	private static WaterMillBlockEntity placeWithWater(GameTestHelper helper) {
		WaterMillBlockEntity mill =
				AlaGameTestHelper.place(helper, POS, ModBlocks.WATER_MILL, WaterMillBlockEntity.class);
		helper.setBlock(POS.relative(Direction.EAST), flowingWater());
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
		helper.setBlock(POS.relative(Direction.NORTH), flowingWater()); // add a second flowing face
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
		// Dry mills report the "no water" hint (MOD-179) — the point here is that neither is stalled
		// by interference or obstruction.
		assertMode(helper, a, "spaced mill A", WaterMillBlockEntity.MODE_NO_WATER);
		assertMode(helper, b, "spaced mill B", WaterMillBlockEntity.MODE_NO_WATER);
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
		// Dry mills report the "no water" hint (MOD-179) — the point here is that neither is stalled.
		assertMode(helper, a, "back-to-back mill A", WaterMillBlockEntity.MODE_NO_WATER);
		assertMode(helper, b, "back-to-back mill B", WaterMillBlockEntity.MODE_NO_WATER);
		helper.succeed();
	}

	// ── MOD-179: wheel clearance — a solid block in the swept area hides the wheel and stalls ────────

	/**
	 * Two mills face to face with NO gap: each wheel box sits entirely inside the other mill's solid
	 * casing, where the AABB interference test is geometrically blind (overlap needs centre distance
	 * 1.165–2.915; here it is 1). The clearance check must catch it: both mills report
	 * MODE_OBSTRUCTED and produce nothing even with water present. Regression test for the MOD-179
	 * audit finding — before the fix both mills kept generating with wheels clipping through casings.
	 */
	@GameTest
	public void waterMill_faceToFaceAdjacentObstructed(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.EAST);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.WEST);
		helper.setBlock(POS.north(), Blocks.WATER); // water face for A: obstruction must still win
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "adjacent face-to-face mill A", WaterMillBlockEntity.MODE_OBSTRUCTED);
		assertMode(helper, b, "adjacent face-to-face mill B", WaterMillBlockEntity.MODE_OBSTRUCTED);
		if (a.getEnergyStorage().getAmount() != 0) {
			helper.fail("obstructed mill A generated EU");
		}
		helper.succeed();
	}

	/**
	 * A solid block directly in front of the wheel stalls the mill (the wheel would clip through it),
	 * and removing the block recovers within a tick or two — clearance is sampled every tick, unlike
	 * the 20-tick interference cadence.
	 */
	@GameTest
	public void waterMill_wallInFrontStallsAndRecovers(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.relative(Direction.EAST), flowingWater());
		helper.setBlock(POS.north(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "walled mill", WaterMillBlockEntity.MODE_OBSTRUCTED);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("walled mill generated EU");
		}
		helper.setBlock(POS.north(), Blocks.AIR);
		AlaGameTestHelper.drive(mill, helper, 2);
		assertMode(helper, mill, "un-walled mill", WaterMillBlockEntity.MODE_OK);
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("mill did not resume generation after the wall was removed");
		}
		helper.succeed();
	}

	/**
	 * The swept area includes the TOP row of the rotation plane (the rim reaches ~0.82 block above the
	 * front cell): a solid block there also stalls the mill. Locks the 6-cell sweep against shrinking
	 * to the front cell only.
	 */
	@GameTest
	public void waterMill_blockAboveFrontAlsoObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.north().above(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "roofed-wheel mill", WaterMillBlockEntity.MODE_OBSTRUCTED);
		helper.succeed();
	}

	/**
	 * The BOTTOM row of the rotation plane is deliberately NOT swept: the lower wheel arc dips into
	 * the river (water or a shallow river bed) by design, and shipped shallow-water placements rely on
	 * it. A solid block under the front cell must not stall the mill. Guards the documented asymmetry
	 * of {@code WaterMillClearance} against an over-eager future "fix".
	 */
	@GameTest
	public void waterMill_solidRiverBedBelowFrontIsAllowed(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.relative(Direction.EAST), flowingWater());
		helper.setBlock(POS.north().below(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "river-bed mill", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}

	/**
	 * A solid block BESIDE the front cell (at hub height) stalls the mill: the horizontal rim arc
	 * would clip through it. The wheel needs a clear slot on both sides, not just in front and above
	 * (MOD-179 feedback — the caged-side screenshot). Each side is checked independently, and removing
	 * the block recovers within a tick.
	 */
	@GameTest
	public void waterMill_sideBlockObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.relative(Direction.EAST), flowingWater()); // a flowing face — obstruction must still win
		// Right side of the wheel.
		helper.setBlock(POS.north().east(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 2);
		assertMode(helper, mill, "mill with right-side block", WaterMillBlockEntity.MODE_OBSTRUCTED);
		helper.setBlock(POS.north().east(), Blocks.AIR);
		// Left side of the wheel.
		helper.setBlock(POS.north().west(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 2);
		assertMode(helper, mill, "mill with left-side block", WaterMillBlockEntity.MODE_OBSTRUCTED);
		helper.setBlock(POS.north().west(), Blocks.AIR);
		AlaGameTestHelper.drive(mill, helper, 2);
		assertMode(helper, mill, "mill with sides cleared", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}

	/**
	 * Water beside the wheel is fine (it is replaceable): a wheel dipping into a water channel on its
	 * sides keeps working. Guards against the clearance check rejecting the canonical water-channel
	 * placement.
	 */
	@GameTest
	public void waterMill_waterBesideWheelIsAllowed(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.relative(Direction.EAST), flowingWater());
		// Beside-wheel cells are clearance-only (not generation faces); still SOURCE water there is
		// replaceable and must pass clearance just the same.
		helper.setBlock(POS.north().east(), Blocks.WATER);
		helper.setBlock(POS.north().west(), Blocks.WATER);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "water-channel mill", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}

	// ── MOD-179: interference cache — the 20-tick scan cadence must converge after world changes ────

	/**
	 * Breaking an interfering neighbour clears our stall within one scan interval: the cached
	 * interference flag may serve stale {@code true} for up to {@code SCAN_INTERVAL − 1} ticks, but the
	 * next scheduled scan (≤ 20 ticks) must see the neighbour gone and return to MODE_OK. Regression
	 * test for the cache cadence — a cache that never rescans would stay interfered forever.
	 */
	@GameTest
	public void waterMill_interferenceClearsWithinScanInterval(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		placeMillWithWheel(helper, POS.east(), Direction.NORTH);
		AlaGameTestHelper.drive(a, helper, 3);
		assertMode(helper, a, "mill A next to B", WaterMillBlockEntity.MODE_INTERFERENCE);
		helper.setBlock(POS.east(), Blocks.AIR);
		AlaGameTestHelper.drive(a, helper, 21); // ≥ one full SCAN_INTERVAL after the neighbour vanished
		// Dry mill → the "no water" hint; the point is that the stale interference flag cleared.
		assertMode(helper, a, "mill A after B was broken", WaterMillBlockEntity.MODE_NO_WATER);
		helper.succeed();
	}

	/**
	 * Pulling the WHEEL out of an interfering neighbour (mill block stays!) also clears our stall
	 * within one scan interval — a bare mill renders no wheel, so there is nothing to clash with.
	 * Mirrors the OKF rule "a neighbour without a wheel causes no interference".
	 */
	@GameTest
	public void waterMill_neighbourWheelRemovalClearsInterference(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.NORTH);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "mill A next to wheeled B", WaterMillBlockEntity.MODE_INTERFERENCE);
		b.setItem(WaterMillBlockEntity.WHEEL_SLOT, ItemStack.EMPTY);
		AlaGameTestHelper.drive(b, helper, 1); // B resets its own state immediately (no wheel)
		assertMode(helper, b, "bare mill B", WaterMillBlockEntity.MODE_OK);
		AlaGameTestHelper.drive(a, helper, 21);
		// Dry mill → the "no water" hint; the point is that A is no longer interfered.
		assertMode(helper, a, "mill A after B's wheel was removed", WaterMillBlockEntity.MODE_NO_WATER);
		helper.succeed();
	}

	// ── MOD-179 feedback: the "no water" GUI hint and the back-only energy output ────────────────────

	/**
	 * A wheeled, unobstructed mill with NO adjacent water reports MODE_NO_WATER — the GUI turns that
	 * into the localized "No water" hint so the player learns what is missing. Adding water flips the
	 * mode back to MODE_OK and production resumes.
	 */
	@GameTest
	public void waterMill_dryMillShowsNoWaterHint(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "dry mill", WaterMillBlockEntity.MODE_NO_WATER);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("dry mill generated EU");
		}
		helper.setBlock(POS.relative(Direction.EAST), flowingWater());
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "watered mill", WaterMillBlockEntity.MODE_OK);
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("watered mill generated no EU");
		}
		helper.succeed();
	}

	/**
	 * MOD-188: a still SOURCE block on every face powers NOTHING — only a current turns the wheel. The
	 * mill reports MODE_NO_WATER and generates 0 EU even fully surrounded by source water. Replacing one
	 * source face with FLOWING water flips it to MODE_OK and production starts. Regression test for the
	 * "drop it in a still pool and it self-powers" exploit; remove the {@code !isSource()} guard and the
	 * first assertion (still source → MODE_OK) fails.
	 */
	@GameTest
	public void waterMill_stillSourceDoesNotGenerate(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			helper.setBlock(POS.relative(dir), Blocks.WATER); // still SOURCE on all four faces
		}
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "mill in still source pool", WaterMillBlockEntity.MODE_NO_WATER);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("mill in a still source pool generated EU (should need a current)");
		}
		helper.setBlock(POS.relative(Direction.EAST), flowingWater()); // one flowing face
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "mill with one flowing face", WaterMillBlockEntity.MODE_OK);
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("mill with a flowing face did not generate");
		}
		helper.succeed();
	}

	/**
	 * The mill emits EU only from its BACK face (opposite of FACING) — the wind-mill single-output
	 * contract (R-NRG-03, MOD-179): a battery box behind the mill charges via the direct cable-less
	 * push, a box on a side face receives nothing. FACING = NORTH, so SOUTH is the sole OUT face.
	 */
	@GameTest
	public void waterMill_energyOnlyFromBackFace(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		mill.getEnergyStorage().amount = mill.getEnergyStorage().getCapacity(); // ample supply to push
		BlockPos back = POS.relative(Direction.SOUTH);
		helper.setBlock(back, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		BlockPos side = POS.relative(Direction.EAST);
		helper.setBlock(side, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		var backBox = helper.getBlockEntity(back, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		var sideBox = helper.getBlockEntity(side, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		backBox.getEnergyStorage().amount = 0;
		sideBox.getEnergyStorage().amount = 0;
		AlaGameTestHelper.drive(mill, helper, 20);
		if (backBox.getEnergyStorage().getAmount() <= 0) {
			helper.fail("battery box on the back face received no EU from the water mill");
		}
		if (sideBox.getEnergyStorage().getAmount() != 0) {
			helper.fail("battery box on a side face received EU (" + sideBox.getEnergyStorage().getAmount()
					+ ") — the water mill must emit only from its back face");
		}
		helper.succeed();
	}

	// ── MOD-179: sided automation — the FACING face is inert for hoppers/pipes ───────────────────────

	/**
	 * The front (FACING) face exposes no slots to automation, matching the energy side and the OKF
	 * promise "hoppers do not work through it"; every other face still exposes the wheel slot.
	 * Regression test for the MOD-179 audit finding — before the fix {@code getSlotsForFace} ignored
	 * the side and a hopper aimed at the front face could insert a wheel.
	 */
	@GameTest
	public void waterMill_frontFaceInertForAutomation(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		if (mill.getSlotsForFace(Direction.NORTH).length != 0) {
			helper.fail("FACING face exposed slots to automation");
		}
		for (Direction side : new Direction[] {Direction.SOUTH, Direction.EAST, Direction.WEST,
				Direction.UP, Direction.DOWN}) {
			if (mill.getSlotsForFace(side).length == 0) {
				helper.fail("non-FACING face " + side + " exposed no slots to automation");
			}
		}
		helper.succeed();
	}

	/**
	 * Automation can insert a wheel only while the slot is empty: with a wheel installed,
	 * {@code canPlaceItemThroughFace} refuses more wheels on every face, so a hopper cannot top the
	 * slot up to a stack (the "one wheel" limit otherwise lives only in the GUI slot).
	 */
	@GameTest
	public void waterMill_automationCannotStackSecondWheel(GameTestHelper helper) {
		WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, POS, ModBlocks.WATER_MILL,
				WaterMillBlockEntity.class);
		ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
		if (!mill.canPlaceItemThroughFace(WaterMillBlockEntity.WHEEL_SLOT, wheel, Direction.UP)) {
			helper.fail("automation could not insert a wheel into an empty slot");
		}
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		if (mill.canPlaceItemThroughFace(WaterMillBlockEntity.WHEEL_SLOT, wheel, Direction.UP)) {
			helper.fail("automation could insert a second wheel into an occupied slot");
		}
		helper.succeed();
	}
}
