package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
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
public final class WaterMillWheelScenarios {

	private WaterMillWheelScenarios() {}

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

	/**
	 * The wheel's undershot cell for a mill at {@code pos} facing {@code facing} — one below the front
	 * cell (MOD-352). Since MOD-352 the mill is driven by the four cells the WHEEL sweeps, not by the
	 * mill block's own neighbours. Wetting a drive cell rather than filling it never collides with the
	 * clearance check, because water is {@code canBeReplaced()} and so counts as clear (MOD-355).
	 */
	private static BlockPos undershotCell(BlockPos pos, Direction facing) {
		return pos.relative(facing).below();
	}

	/** Put a current in the undershot cell of a NORTH-facing mill at {@link #POS} — one driven side. */
	private static void driveOneSide(GameTestHelper helper) {
		helper.setBlock(undershotCell(POS, Direction.NORTH), flowingWater());
	}

	private static WaterMillBlockEntity placeWithWater(GameTestHelper helper) {
		WaterMillBlockEntity mill =
				AlaGameTestHelper.place(helper, POS, ModContent.WATER_MILL.get(), WaterMillBlockEntity.class);
		driveOneSide(helper); // default FACING is NORTH
		return mill;
	}

	/** Water alone is insufficient: an empty component slot must keep the generator at 0 EU. */
	public static void waterMillWheel_missingWheelStopsGeneration(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper);
		AlaGameTestHelper.drive(mill, helper, 5);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("water mill generated EU without an installed water wheel");
		}
		helper.succeed();
	}

	/** Installing the dedicated wheel unlocks production without consuming the component. */
	public static void waterMillWheel_installedWheelEnablesGeneration(GameTestHelper helper) {
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
	public static void waterMillWheel_progressTracksWaterFaces(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWithWater(helper); // undershot cell wet -> 1 driven side
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
		AlaGameTestHelper.drive(mill, helper, 3);
		if (mill.getDataAccess().get(2) != 1) {
			helper.fail("water mill progress (wheel-renderer input) should be 1 with one driven side, was "
					+ mill.getDataAccess().get(2));
		}
		// Wet a second cell the wheel sweeps — beside the rim, not beside the mill block (MOD-352).
		helper.setBlock(POS.north().east(), flowingWater());
		AlaGameTestHelper.drive(mill, helper, 3);
		if (mill.getDataAccess().get(2) != 2) {
			helper.fail("water mill progress should track the added water face (expected 2), was "
					+ mill.getDataAccess().get(2));
		}
		helper.succeed();
	}

	/** The component slot rejects unrelated items and accepts only water_mill_wheel. */
	public static void waterMillWheel_slotFilterIsDedicated(GameTestHelper helper) {
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
	public static void waterMill_craftingRecipeResolves(GameTestHelper helper) {
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
	public static void waterMillWheel_craftingRecipeResolves(GameTestHelper helper) {
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
		helper.setBlock(pos, ModContent.WATER_MILL.get().defaultBlockState()
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
	public static void waterMill_sideBySideInterference(GameTestHelper helper) {
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
	public static void waterMill_faceToFaceInterference(GameTestHelper helper) {
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
	public static void waterMill_spacedMillsDoNotInterfere(GameTestHelper helper) {
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
	public static void waterMill_backToBackDoNotInterfere(GameTestHelper helper) {
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
	public static void waterMill_faceToFaceAdjacentObstructed(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.EAST);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.WEST);
		helper.setBlock(POS.east().below(), flowingWater()); // a driven side for A: obstruction must still win
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
	public static void waterMill_wallInFrontStallsAndRecovers(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		driveOneSide(helper);
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
	public static void waterMill_blockAboveFrontAlsoObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		helper.setBlock(POS.north().above(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "roofed-wheel mill", WaterMillBlockEntity.MODE_OBSTRUCTED);
		helper.succeed();
	}

	/**
	 * A solid river bed directly under the wheel now stalls the mill (MOD-355), and water in the same
	 * cell does not. Before MOD-355 the bottom row of the rotation plane was exempt so a mill could sit
	 * on a river bed — but that let a player sink the lower third of the wheel into stone and keep
	 * generating, which is the "broken wheel" look the clearance check exists to prevent. The exemption
	 * was never needed: clearance treats any {@code canBeReplaced()} cell as free, and water is
	 * replaceable, so a wheel hanging over a channel passes exactly as before.
	 *
	 * <p>Both halves are load-bearing. Without the first, sinking the wheel into rock stays legal;
	 * without the second, the fix would forbid every real water channel.
	 */
	public static void waterMill_solidRiverBedBelowFrontObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		// A driven side, so the only thing that can stall the mill below is the clearance check.
		helper.setBlock(POS.north().east(), flowingWater());
		helper.setBlock(POS.north().below(), Blocks.STONE);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "wheel sunk into a solid river bed", WaterMillBlockEntity.MODE_OBSTRUCTED);
		helper.setBlock(POS.north().below(), flowingWater());
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "wheel hanging over a water channel", WaterMillBlockEntity.MODE_OK);
		helper.succeed();
	}

	/**
	 * Every one of the nine cells of the wheel's plane stalls the mill when it holds a solid block, and
	 * clearing that cell lifts the stall (MOD-355).
	 *
	 * <p>The owner reported three builds that all kept running while the wheel was visibly cut through:
	 * stone under the wheel, blocks on the lower diagonals, and dirt on all four diagonals. The old
	 * check looked at four cells out of nine — it never looked at a diagonal at all, and the bottom row
	 * was deliberately exempt. Walking the full plane locks the set: drop any cell from the check and
	 * the sweep fails on it by name.
	 *
	 * <p>The clear-and-recheck half is the negative control: a test that only ever placed blocks would
	 * still pass if the mill were stalled by something else entirely.
	 */
	public static void waterMill_everyWheelPlaneCellObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		BlockPos front = POS.north();
		BlockPos[] plane = {
			front, front.above(), front.below(),
			front.east(), front.east().above(), front.east().below(),
			front.west(), front.west().above(), front.west().below(),
		};
		for (BlockPos cell : plane) {
			helper.setBlock(cell, Blocks.STONE);
			AlaGameTestHelper.drive(mill, helper, 2);
			assertMode(helper, mill, "solid block at wheel-plane cell " + cell,
					WaterMillBlockEntity.MODE_OBSTRUCTED);
			helper.setBlock(cell, Blocks.AIR);
			AlaGameTestHelper.drive(mill, helper, 2);
			if (mill.getDataAccess().get(3) == WaterMillBlockEntity.MODE_OBSTRUCTED) {
				helper.fail("clearing wheel-plane cell " + cell + " did not lift the obstruction");
			}
		}
		helper.succeed();
	}

	/**
	 * A solid block BESIDE the front cell (at hub height) stalls the mill: the horizontal rim arc
	 * would clip through it. The wheel needs a clear slot on both sides, not just in front and above
	 * (MOD-179 feedback — the caged-side screenshot). Each side is checked independently, and removing
	 * the block recovers within a tick.
	 */
	public static void waterMill_sideBlockObstructs(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		driveOneSide(helper); // a driven side — obstruction must still win
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
	public static void waterMill_waterBesideWheelIsAllowed(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		driveOneSide(helper);
		// Still SOURCE water beside the wheel drives nothing (MOD-188) but is
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
	public static void waterMill_interferenceClearsWithinScanInterval(GameTestHelper helper) {
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
	public static void waterMill_neighbourWheelRemovalClearsInterference(GameTestHelper helper) {
		WaterMillBlockEntity a = placeMillWithWheel(helper, POS, Direction.NORTH);
		WaterMillBlockEntity b = placeMillWithWheel(helper, POS.east(), Direction.NORTH);
		AlaGameTestHelper.drive(a, helper, 3);
		AlaGameTestHelper.drive(b, helper, 3);
		assertMode(helper, a, "mill A next to wheeled B", WaterMillBlockEntity.MODE_INTERFERENCE);
		b.setItem(WaterMillBlockEntity.WHEEL_SLOT, ItemStack.EMPTY);
		AlaGameTestHelper.drive(b, helper, 1); // B resets its own state immediately (no wheel)
		// MOD-430: a bare mill reports MODE_NO_WHEEL, not MODE_OK. It used to claim OK on the reasoning
		// that an empty slot needs no explanation, which left the GUI's status row blank and the player
		// with a working-looking screen making nothing.
		assertMode(helper, b, "bare mill B", WaterMillBlockEntity.MODE_NO_WHEEL);
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
	public static void waterMill_dryMillShowsNoWaterHint(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "dry mill", WaterMillBlockEntity.MODE_NO_WATER);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("dry mill generated EU");
		}
		driveOneSide(helper);
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
	public static void waterMill_stillSourceDoesNotGenerate(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		// Flood the four cells the WHEEL sweeps with still sources (MOD-352: those are the cells that
		// drive it). Flooding the mill block's own neighbours instead would make this test vacuous —
		// since MOD-352 they drive nothing whether they are source or flowing, so it would stay green
		// even with the isSource() guard deleted.
		BlockPos front = POS.north();
		for (BlockPos cell : new BlockPos[] {front.above(), front.below(), front.east(), front.west()}) {
			helper.setBlock(cell, Blocks.WATER);
		}
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "mill in still source pool", WaterMillBlockEntity.MODE_NO_WATER);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("mill in a still source pool generated EU (should need a current)");
		}
		driveOneSide(helper); // one driven side, now with a current
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
	public static void waterMill_energyOnlyFromBackFace(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		mill.getEnergyStorage().setAmountUntracked(mill.getEnergyStorage().getCapacity()); // ample supply to push
		BlockPos back = POS.relative(Direction.SOUTH);
		helper.setBlock(back, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		BlockPos side = POS.relative(Direction.EAST);
		helper.setBlock(side, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		var backBox = helper.getBlockEntity(back, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		var sideBox = helper.getBlockEntity(side, dev.alaindustrial.block.entity.BatteryBoxBlockEntity.class);
		backBox.getEnergyStorage().setAmountUntracked(0);
		sideBox.getEnergyStorage().setAmountUntracked(0);
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
	public static void waterMill_frontFaceInertForAutomation(GameTestHelper helper) {
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
	public static void waterMill_automationCannotStackSecondWheel(GameTestHelper helper) {
		WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, POS, ModContent.WATER_MILL.get(),
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

	// ── MOD-352: the wheel is driven by the cells the WHEEL touches, not the mill block's ────────────

	/**
	 * The mill block's own horizontal neighbours drive nothing; the cells the wheel sweeps do.
	 *
	 * <p>This is the owner's in-game report reproduced exactly. Before MOD-352 the mill counted a
	 * current on its own four horizontal faces, three of which the wheel never touches — the wheel is
	 * rendered a full block in front of the mill. So pouring water around the visible wheel produced
	 * nothing, while pouring it around the buried mill block spun a wheel the water never reached. The
	 * model was unreadable from the picture, and the picture is all a player has.
	 *
	 * <p>Both halves matter. The first asserts that all four block-neighbour cells (including the front
	 * cell, where the hub sits) yield zero — delete the fix and this fails immediately. The second
	 * asserts the wheel's own cell does drive it, so the test cannot pass by the mill simply never
	 * generating.
	 */
	public static void waterMill_blockNeighboursDoNotDriveWheel(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			helper.setBlock(POS.relative(dir), flowingWater());
		}
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "mill with a current on all four BLOCK faces",
				WaterMillBlockEntity.MODE_NO_WATER);
		if (mill.getEnergyStorage().getAmount() != 0) {
			helper.fail("a current touching only the mill block generated EU ("
					+ mill.getEnergyStorage().getAmount() + ") — the wheel is a block in front and never "
					+ "touches those cells");
		}
		driveOneSide(helper); // now wet a cell the rim actually passes through
		AlaGameTestHelper.drive(mill, helper, 3);
		assertMode(helper, mill, "mill with a current under the wheel", WaterMillBlockEntity.MODE_OK);
		if (mill.getEnergyStorage().getAmount() <= 0) {
			helper.fail("a current in the wheel's undershot cell generated no EU");
		}
		helper.succeed();
	}

	/**
	 * All four of the wheel's cells count, and they cap at the documented 4 EU/t. Locks the drive set
	 * itself: drop any one of the four (above / below / left / right of the front cell) and the total
	 * comes up short.
	 */
	public static void waterMill_allFourWheelCellsDrive(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
		BlockPos front = POS.north();
		BlockPos[] driveCells = {front.above(), front.below(), front.east(), front.west()};
		for (int i = 0; i < driveCells.length; i++) {
			helper.setBlock(driveCells[i], flowingWater());
			AlaGameTestHelper.drive(mill, helper, 3);
			int expected = i + 1;
			int actual = mill.getDataAccess().get(2);
			if (actual != expected) {
				helper.fail("after wetting " + expected + " wheel cell(s) the driven-side count was "
						+ actual + "; cell " + driveCells[i] + " did not register");
			}
		}
		int rate = mill.getDataAccess().get(4);
		int expectedRate = Config.waterMillEuPerTick * 4;
		if (rate != expectedRate) {
			helper.fail("fully driven wheel reported " + rate + " EU/t, expected " + expectedRate);
		}
		helper.succeed();
	}

	// ── MOD-348: the GUI production readout rides its own sync channel ───────────────────────────────

	/**
	 * Sync channel 4 carries the mill's live output in EU/t (what the GUI status row prints), and it is a
	 * channel of its own — channel 2 keeps carrying the water-face count that drives the wheel renderer.
	 *
	 * <p><b>Why the Config override is load-bearing.</b> At the shipped default of 1 EU per face the two
	 * quantities are numerically identical (1 face = 1 EU/t), so an implementation that simply returned the
	 * face count as the rate — the obvious way to copy the wind mill, whose rate genuinely does ride
	 * channel 2 — would pass a test written against defaults. Three EU per face separates them: the rate
	 * channel must read 3 and 6 where the face channel reads 1 and 2. Remove the dedicated channel and
	 * this test fails on the very first assertion.
	 *
	 * <p>The last leg covers the stale-readout case: a mill that stalls must publish 0, not keep serving
	 * the last figure it produced.
	 */
	public static void waterMill_productionRateChannelTracksOutput(GameTestHelper helper) {
		int savedPerSide = Config.waterMillEuPerTick;
		try {
			Config.waterMillEuPerTick = 3;
			WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
			driveOneSide(helper);
			AlaGameTestHelper.drive(mill, helper, 3);
			assertRate(helper, mill, "one driven side", 3, 1);

			helper.setBlock(POS.north().east(), flowingWater());
			AlaGameTestHelper.drive(mill, helper, 3);
			assertRate(helper, mill, "two driven sides", 6, 2);

			helper.setBlock(POS.north(), Blocks.STONE); // wheel blocked → generation halts
			AlaGameTestHelper.drive(mill, helper, 3);
			assertMode(helper, mill, "obstructed mill", WaterMillBlockEntity.MODE_OBSTRUCTED);
			assertRate(helper, mill, "obstructed mill", 0, 0);
			helper.succeed();
		} finally {
			Config.waterMillEuPerTick = savedPerSide;
		}
	}

	/**
	 * MOD-356: channel 4 must carry the <b>effective</b> rate — what the buffer really gains — not
	 * {@code produce()}'s mechanical figure from before {@link Config#globalEuRateMultiplier}.
	 *
	 * <p>Both overrides are load-bearing. The multiplier at 2.0 separates the mechanical rate from the
	 * credited one (at the shipped 1.0 they are equal, which is why the bug went unnoticed); 3 EU per face
	 * keeps the rate distinct from the face count on channel 2, so a regression that collapses the two
	 * channels back together cannot pass. On the pre-fix code the mill banks 6 EU in the measured tick
	 * while channel 4 still reads 3.
	 */
	public static void waterMill_productionRateChannelFollowsGlobalMultiplier(GameTestHelper helper) {
		int savedPerSide = Config.waterMillEuPerTick;
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			Config.waterMillEuPerTick = 3;
			Config.globalEuRateMultiplier = 2.0f;
			WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
			driveOneSide(helper);
			AlaGameTestHelper.drive(mill, helper, 3);

			mill.getEnergyStorage().setAmountUntracked(0);
			AlaGameTestHelper.drive(mill, helper, 1);
			long gained = mill.getEnergyStorage().getAmount();
			int shown = mill.getDataAccess().get(4);
			if (gained != 6) {
				helper.fail("one driven side at 3 EU/face and multiplier 2.0 banked " + gained
						+ " EU in a tick, expected 6 — the readout comparison below would be vacuous");
				return;
			}
			if (shown != gained) {
				helper.fail("water mill readout says " + shown + " EU/t but the buffer gained " + gained
						+ " EU in the same tick (globalEuRateMultiplier=2.0)");
				return;
			}
			int faces = mill.getDataAccess().get(2);
			if (faces != 1) {
				helper.fail("water-face channel = " + faces + ", expected 1 — channel 2 must stay the wheel"
						+ " renderer's face count and must not follow the EU multiplier");
				return;
			}
			helper.succeed();
		} finally {
			Config.waterMillEuPerTick = savedPerSide;
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/**
	 * The {@code max(1, ...)} floor reaches the READOUT, not just the buffer: at a multiplier small enough
	 * to round the rate to zero, a mill that is genuinely turning reports 1 EU/t — never 0, and never the
	 * un-multiplied mechanical figure.
	 *
	 * <p>8 EU per face against a 0.05 multiplier is what gives this test teeth: {@code round(8 × 0.05) = 0},
	 * so the floor is the only thing keeping the number off zero, and the three candidate answers are all
	 * distinct — 1 (correct), 8 (the MOD-356 bug: mechanical rate published) and 0 (floor dropped from the
	 * readout). A gentler multiplier collapses them together and proves nothing.
	 */
	public static void waterMill_readoutKeepsFloorUnderTinyMultiplier(GameTestHelper helper) {
		int savedPerSide = Config.waterMillEuPerTick;
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			Config.waterMillEuPerTick = 8;
			Config.globalEuRateMultiplier = 0.05f;
			WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
			driveOneSide(helper);
			AlaGameTestHelper.drive(mill, helper, 3);

			mill.getEnergyStorage().setAmountUntracked(0);
			AlaGameTestHelper.drive(mill, helper, 1);
			long gained = mill.getEnergyStorage().getAmount();
			int shown = mill.getDataAccess().get(4);
			if (gained != 1) {
				helper.fail("a turning mill banked " + gained + " EU at 8 EU/face × 0.05, expected the"
						+ " max(1, ...) floor to give exactly 1");
				return;
			}
			if (shown != 1) {
				helper.fail("readout says " + shown + " EU/t while the mill banks 1 EU/t at a 0.05 multiplier"
						+ " — expected 1 (8 would mean the un-multiplied mechanical rate, 0 a lost floor)");
				return;
			}
			helper.succeed();
		} finally {
			Config.waterMillEuPerTick = savedPerSide;
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/**
	 * Wheel wear stays charged on the <b>mechanical</b> rate even when the EU economy is scaled up
	 * (MOD-189, re-guarded for MOD-356): the multiplier is an economy knob, so doubling EU output must not
	 * double how fast the wheel physically wears out.
	 *
	 * <p>Built as a one-bit discriminator. At 1 EU per face, 1 EU per durability point and a wheel two
	 * points from death, a single active tick spends exactly one point and the wheel SURVIVES. If wear were
	 * charged on the effective rate instead, the 2.0 multiplier would spend two points in that same tick and
	 * the wheel would break and vanish from the slot — the assertion below then fails loudly. Nothing else
	 * in the suite covers this: every other wear test runs at the default multiplier, where the mechanical
	 * and effective rates are identical and the regression is invisible.
	 */
	public static void waterMillWheel_wearFollowsMechanicalRateNotMultiplier(GameTestHelper helper) {
		int savedPerDamage = Config.waterMillWheelEuPerDamage;
		int savedPerSide = Config.waterMillEuPerTick;
		float savedMultiplier = Config.globalEuRateMultiplier;
		try {
			Config.waterMillWheelEuPerDamage = 1; // 1 EU of production spends 1 durability point
			Config.waterMillEuPerTick = 1;        // one driven side → mechanical 1 EU/t, effective 2 EU/t
			Config.globalEuRateMultiplier = 2.0f;
			WaterMillBlockEntity mill = placeMillWithWheel(helper, POS, Direction.NORTH);
			driveOneSide(helper);

			ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
			int seeded = wheel.getMaxDamage() - 2; // two active ticks from death at the mechanical rate
			wheel.setDamageValue(seeded);
			mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, wheel);

			AlaGameTestHelper.drive(mill, helper, 1);
			ItemStack after = mill.getItem(WaterMillBlockEntity.WHEEL_SLOT);
			if (after.isEmpty()) {
				helper.fail("the wheel broke in a single tick at a 2.0 multiplier — wear is being charged on"
						+ " the multiplied rate; it must follow the mechanical one (MOD-189)");
				return;
			}
			int spent = after.getDamageValue() - seeded;
			if (spent != 1) {
				helper.fail("one active tick spent " + spent + " durability points at a 2.0 multiplier,"
						+ " expected 1 — wear must not scale with the EU economy knob");
				return;
			}
			// Sanity: the mill really was producing at the doubled rate this tick, so the check above was
			// exercised rather than passing on an idle wheel.
			if (mill.getDataAccess().get(4) != 2) {
				helper.fail("expected the doubled readout of 2 EU/t while wearing, got "
						+ mill.getDataAccess().get(4) + " — the multiplier never took effect,"
						+ " so the wear assertion proved nothing");
				return;
			}
			helper.succeed();
		} finally {
			Config.waterMillWheelEuPerDamage = savedPerDamage;
			Config.waterMillEuPerTick = savedPerSide;
			Config.globalEuRateMultiplier = savedMultiplier;
		}
	}

	/** Assert both readout channels at once: 4 = EU/t for the GUI, 2 = water faces for the renderer. */
	private static void assertRate(GameTestHelper helper, WaterMillBlockEntity mill, String label,
			int expectedEu, int expectedFaces) {
		int rate = mill.getDataAccess().get(4);
		if (rate != expectedEu) {
			helper.fail(label + ": production channel = " + rate + " EU/t, expected " + expectedEu);
		}
		int faces = mill.getDataAccess().get(2);
		if (faces != expectedFaces) {
			helper.fail(label + ": water-face channel = " + faces + ", expected " + expectedFaces
					+ " — channel 2 must stay the wheel renderer's face count, never the EU/t rate");
		}
	}

	// ── MOD-189: wheel wear — the wheel is a durability component that wears out and breaks ───────────

	/**
	 * A producing water mill wears its wheel down and, once its durability is spent, breaks it: the slot
	 * empties and generation halts. Uses a Config override (1 EU per durability point) so wear is fast and
	 * deterministic — the wear RATE is read live every tick — plus a wheel pre-damaged to one point from
	 * death, so a single active tick finishes it off. Regression guard: without the wear code the wheel
	 * never breaks and the first assertion fails.
	 */
	public static void waterMillWheel_wearsOutAndBreaks(GameTestHelper helper) {
		int savedRate = Config.waterMillWheelEuPerDamage;
		try {
			Config.waterMillWheelEuPerDamage = 1; // 1 EU of production spends 1 durability point
			WaterMillBlockEntity mill = placeWithWater(helper); // one driven side → 1 EU/t
			ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
			wheel.setDamageValue(wheel.getMaxDamage() - 1); // one active tick from breaking
			mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, wheel);
			AlaGameTestHelper.drive(mill, helper, 3);
			if (!mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).isEmpty()) {
				helper.fail("worn-out water wheel was not removed from the slot; damage="
						+ mill.getItem(WaterMillBlockEntity.WHEEL_SLOT).getDamageValue());
			}
			mill.getEnergyStorage().setAmountUntracked(0);
			AlaGameTestHelper.drive(mill, helper, 3);
			if (mill.getEnergyStorage().getAmount() != 0) {
				helper.fail("water mill kept generating after its wheel broke");
			}
			helper.succeed();
		} finally {
			Config.waterMillWheelEuPerDamage = savedRate;
		}
	}

	/**
	 * A wheel in an idle mill (no water → produces 0 EU) does NOT wear, even at the aggressive 1-EU-per-point
	 * rate: wear accrues only while the mill actually produces EU. The wheel is pre-damaged to one point from
	 * death, so any spurious wear would break it — it must survive untouched.
	 */
	public static void waterMillWheel_noWearWhileDry(GameTestHelper helper) {
		int savedRate = Config.waterMillWheelEuPerDamage;
		try {
			Config.waterMillWheelEuPerDamage = 1;
			WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, POS, ModContent.WATER_MILL.get(),
					WaterMillBlockEntity.class); // no water → MODE_NO_WATER, 0 EU
			ItemStack wheel = new ItemStack(ModContent.WATER_MILL_WHEEL.get());
			int seeded = wheel.getMaxDamage() - 1;
			wheel.setDamageValue(seeded);
			mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, wheel);
			AlaGameTestHelper.drive(mill, helper, 10);
			ItemStack after = mill.getItem(WaterMillBlockEntity.WHEEL_SLOT);
			if (after.isEmpty()) {
				helper.fail("idle (dry) water mill wore out its wheel — wear must only accrue while producing EU");
			}
			if (after.getDamageValue() != seeded) {
				helper.fail("idle water mill changed wheel damage from " + seeded + " to " + after.getDamageValue()
						+ "; expected no wear while dry");
			}
			helper.succeed();
		} finally {
			Config.waterMillWheelEuPerDamage = savedRate;
		}
	}
}
