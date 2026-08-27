package dev.alaindustrial.gametest;

import dev.alaindustrial.block.CrystalFarmControllerBlock;
import dev.alaindustrial.block.CrystalFarmShellBlock;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Loader-neutral gametest bodies for the crystal greenhouse (MOD-505).
 *
 * <p>Every case here guards something a playtest actually reported, because each was invisible from
 * inside the game until somebody built the thing and looked:
 *
 * <ul>
 *   <li>the room sealing at all, and <em>showing</em> that it sealed — playtest one called a working
 *       greenhouse "not a multiblock" purely because nothing on screen changed;</li>
 *   <li>the shell going back to its loose look when a block is pulled out — the half that building
 *       alone cannot check;</li>
 *   <li>the fault report naming the block that went missing, after playtest five found the particles
 *       hanging over open water some thirty blocks away;</li>
 *   <li>a seedbed knowing whether anything is looking after it, so it can say so.</li>
 * </ul>
 *
 * <p>The rig is the reactor's, deliberately: a 5×5×5 shell with the controller in the middle of the
 * west wall. Its 3×3×3 interior is exactly {@code crystalFarmRoomMinCells}, so the room is the
 * smallest one the scan accepts and fits a default test structure without a bespoke template. The
 * shapes that make this feature interesting — domes, stepped pyramids — are covered at L1 by
 * {@code RoomFillTest}, where they cost nothing to build.
 */
public final class CrystalFarmScenarios {

	private CrystalFarmScenarios() {}

	/** Outer bound of the shell; the interior is 1..3 on every axis. */
	private static final int SHELL_MAX = 4;

	/**
	 * The controller sits in the middle of the WEST wall, and its {@code FACING} names the way its
	 * panel looks OUT of the room — the scan walks inward along the opposite. Getting this backwards
	 * is what made the reactor's first scenario report an unbounded room.
	 */
	private static final BlockPos CONTROLLER = new BlockPos(0, 2, 2);

	/** A wall cell that faces the interior and is neither the controller nor an edge. */
	private static final BlockPos WALL = new BlockPos(SHELL_MAX, 2, 2);

	/** Somewhere inside the room to stand a seedbed. */
	private static final BlockPos BED = new BlockPos(2, 1, 2);

	private static void buildRoom(GameTestHelper helper) {
		for (int x = 0; x <= SHELL_MAX; x++) {
			for (int y = 0; y <= SHELL_MAX; y++) {
				for (int z = 0; z <= SHELL_MAX; z++) {
					boolean shell = x == 0 || y == 0 || z == 0
							|| x == SHELL_MAX || y == SHELL_MAX || z == SHELL_MAX;
					BlockPos at = new BlockPos(x, y, z);
					if (!shell) {
						helper.setBlock(at, Blocks.AIR.defaultBlockState());
					} else if (!at.equals(CONTROLLER)) {
						helper.setBlock(at, ModContent.CRYSTAL_FARM_FLOOR.get().defaultBlockState());
					}
				}
			}
		}
		helper.setBlock(CONTROLLER, ModContent.CRYSTAL_FARM_CONTROLLER.get().defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
	}

	private static CrystalFarmControllerBlockEntity brain(GameTestHelper helper) {
		CrystalFarmControllerBlockEntity controller =
				helper.getBlockEntity(CONTROLLER, CrystalFarmControllerBlockEntity.class);
		if (controller == null) {
			helper.fail("the greenhouse controller has no block entity");
			throw new IllegalStateException("unreachable");
		}
		return controller;
	}

	/** Forces a scan now instead of waiting out the periodic one. */
	private static void scan(GameTestHelper helper) {
		CrystalFarmControllerBlockEntity controller = brain(helper);
		controller.requestScan();
		controller.serverTick(helper.getLevel(), helper.absolutePos(CONTROLLER),
				helper.getBlockState(CONTROLLER));
	}

	private static boolean formed(GameTestHelper helper) {
		return helper.getBlockState(CONTROLLER).getValue(CrystalFarmControllerBlock.FORMED);
	}

	/**
	 * FUN01 — a sealed room forms, and the shell visibly says so.
	 *
	 * @implements TC-FARM-001-FUN01 — a closed greenhouse seals and paints its shell
	 */
	public static void fun01SealedRoomForms(GameTestHelper helper) {
		buildRoom(helper);
		scan(helper);
		if (!formed(helper)) {
			helper.fail("a sealed room did not form");
		}
		// The paint IS the mechanic, not decoration: playtest one had the logic working and this
		// missing, and read the whole feature as broken.
		if (!helper.getBlockState(WALL).getValue(CrystalFarmShellBlock.FORMED)) {
			helper.fail("the shell did not take on its sealed look");
		}
		helper.succeed();
	}

	/**
	 * FUN02 — pulling one block out un-forms the room AND strips the sealed look off the rest.
	 *
	 * <p>The second half is the one worth having: a failed scan measures no room, so a sweep driven
	 * by the scan result could only ever turn the flag ON, and a breached greenhouse would go on
	 * looking finished forever.
	 *
	 * @implements TC-FARM-001-FUN02 — a breached greenhouse un-forms and its shell goes loose again
	 */
	public static void fun02BreachUnformsAndRepaints(GameTestHelper helper) {
		buildRoom(helper);
		scan(helper);
		helper.setBlock(WALL, Blocks.AIR.defaultBlockState());
		scan(helper);
		if (formed(helper)) {
			helper.fail("a room with a hole in it still reports as formed");
		}
		BlockPos intact = new BlockPos(SHELL_MAX, 2, 1);
		if (helper.getBlockState(intact).getValue(CrystalFarmShellBlock.FORMED)) {
			helper.fail("the shell kept its sealed look after the room was breached");
		}
		helper.succeed();
	}

	/**
	 * FUN03 — the fault report names the block that went missing.
	 *
	 * <p>Playtest five found the fault particles roughly thirty blocks from the greenhouse, out over
	 * open water: a flood fill reports wherever it ran out of leash, which is nowhere near the hole.
	 * The controller hunts its remembered shell instead. Point it back at the fill's own position and
	 * this goes red, because those coordinates land outside the structure entirely.
	 *
	 * @implements TC-FARM-001-FUN03 — a breach is reported at the missing block, not at the fill's leash
	 */
	public static void fun03BreachIsReportedAtTheHole(GameTestHelper helper) {
		buildRoom(helper);
		scan(helper);
		helper.setBlock(WALL, Blocks.AIR.defaultBlockState());
		scan(helper);

		BlockPos hole = helper.absolutePos(WALL);
		String expected = hole.getX() + ", " + hole.getY() + ", " + hole.getZ();
		String report = brain(helper).describeStatus(helper.absolutePos(CONTROLLER)).getString();
		if (!report.contains(expected)) {
			helper.fail("the fault report should name the missing block at " + expected
					+ ", but said: " + report);
		}
		helper.succeed();
	}

	/**
	 * FUN05 — a controller taken by anything at all takes the sealed look with it.
	 *
	 * <p>The bug this guards was found by audit, not by play, and it is the nastiest of the set: the
	 * clean-up used to hang off a player-only hook, so a greenhouse whose brain was blown up by a
	 * creeper kept its seamless shell and its tended seedbeds <em>forever</em>, with nothing left in
	 * the world that owned either — and the player went on feeding shards into beds that could never
	 * bud and were never going to say so.
	 *
	 * <p>{@code setBlock} here stands in for the creeper: it is the same path as an explosion or a
	 * command, and the same one a player never takes.
	 *
	 * @implements TC-FARM-001-FUN05 — removing the controller by any means clears the shell and the beds
	 */
	public static void fun05ControllerRemovedAnyWayClearsUp(GameTestHelper helper) {
		buildRoom(helper);
		helper.setBlock(BED, ModContent.CRYSTAL_SEEDBED.get().defaultBlockState());
		scan(helper);
		if (!helper.getBlockState(WALL).getValue(CrystalFarmShellBlock.FORMED)
				|| !helper.getBlockState(BED).getValue(CrystalSeedbedBlock.TENDED)) {
			helper.fail("the room did not seal, so this test would prove nothing");
		}

		helper.setBlock(CONTROLLER, Blocks.AIR.defaultBlockState());
		if (helper.getBlockState(WALL).getValue(CrystalFarmShellBlock.FORMED)) {
			helper.fail("the shell kept its sealed look after the controller was destroyed");
		}
		if (helper.getBlockState(BED).getValue(CrystalSeedbedBlock.TENDED)) {
			helper.fail("a seedbed stayed marked as tended after the controller was destroyed");
		}
		helper.succeed();
	}

	/**
	 * FUN04 — a seedbed learns whether a greenhouse is looking after it, and forgets when it opens.
	 *
	 * <p>Playtest five fed a bed standing in the open, watched its charge counter climb exactly as it
	 * would indoors, and had no way to find out that nothing would ever come of it. The flag is what
	 * lets the bed say so; the clearing half matters just as much, or a bed would keep claiming to be
	 * tended long after its greenhouse came down.
	 *
	 * @implements TC-FARM-001-FUN04 — a seedbed is marked tended inside a sealed room, and only there
	 */
	public static void fun04SeedbedKnowsItIsTended(GameTestHelper helper) {
		buildRoom(helper);
		helper.setBlock(BED, ModContent.CRYSTAL_SEEDBED.get().defaultBlockState());
		scan(helper);
		if (!helper.getBlockState(BED).getValue(CrystalSeedbedBlock.TENDED)) {
			helper.fail("a seedbed inside a sealed greenhouse was not marked as tended");
		}

		helper.setBlock(WALL, Blocks.AIR.defaultBlockState());
		scan(helper);
		if (helper.getBlockState(BED).getValue(CrystalSeedbedBlock.TENDED)) {
			helper.fail("a seedbed stayed marked as tended after its greenhouse was opened");
		}
		helper.succeed();
	}
}
