package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 suite for R-NRG-03 — per-face energy roles, observed through the real {@code EnergyStorage.SIDED}
 * lookup (the same view cables, adjacent blocks and other mods see). Producers emit on their 5 working
 * faces (all but FACING), consumers accept on their 5 working faces (all but FACING), cables do both on
 * all 6 faces, and the BatteryBox has a designated single input/output face pair.
 */
public class EnergyFaceGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static EnergyStorage port(GameTestHelper helper, Direction face) {
		return EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), face);
	}

	/**
	 * @implements R-NRG-03 — generator: EU is emitted on its 5 working faces (every face but FACING);
	 *     the FACING face is inert — no cable connects to it, no hopper works through it. Placed with the
	 *     default state (FACING = NORTH, {@link HorizontalMachineBlock#FACING}), decided 2026-07-01 (see
	 *     {@code docs/blocks/generators/generator.md}). D4 fix: the old assertion required all 6 faces
	 *     OUT-only, which is the discrepancy this test corrects.
	 */
	@GameTest
	public void rNrg03_generatorEveryFaceOutOnly(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.GENERATOR); // default FACING = NORTH
		for (Direction d : Direction.values()) {
			EnergyStorage p = port(helper, d);
			if (d == Direction.NORTH) {
				if (p != null) {
					helper.fail("generator FACING face (north) must be inert (no energy port)");
				}
				continue;
			}
			if (p == null || !p.supportsExtraction() || p.supportsInsertion()) {
				helper.fail("generator working face " + d + " must be OUT-only");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements R-NRG-03 — consumer machine: EU is accepted on its 5 working faces (every face but
	 *     FACING); the FACING face is inert — no cable connects to it, no hopper works through it. Placed
	 *     with the default state (FACING = NORTH), decided 2026-07-01 (see
	 *     {@code docs/blocks/machines/macerator.md}). D4 fix: the old assertion required all 6 faces
	 *     IN-only, which is the discrepancy this test corrects.
	 */
	@GameTest
	public void rNrg03_maceratorEveryFaceInOnly(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MACERATOR); // default FACING = NORTH
		for (Direction d : Direction.values()) {
			EnergyStorage p = port(helper, d);
			if (d == Direction.NORTH) {
				if (p != null) {
					helper.fail("macerator FACING face (north) must be inert (no energy port)");
				}
				continue;
			}
			if (p == null || !p.supportsInsertion() || p.supportsExtraction()) {
				helper.fail("macerator working face " + d + " must be IN-only");
			}
		}
		helper.succeed();
	}

	/** @implements R-NRG-03 — cable: every face participates (accepts into the network). */
	@GameTest
	public void rNrg03_cableEveryFaceConnects(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.COPPER_CABLE);
		for (Direction d : Direction.values()) {
			EnergyStorage p = port(helper, d);
			if (p == null || !p.supportsInsertion()) {
				helper.fail("cable face " + d + " must participate in transfer");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements R-NRG-03 — BatteryBox single-axis IO (MOD-006): accepts ONLY on its FACING face, emits ONLY
	 *     on the opposite face, the other four are inert. Placed with the default state (FACING = NORTH),
	 *     so input = NORTH, output = SOUTH.
	 */
	@GameTest
	public void rNrg03_batteryBoxInputOutputFaces(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX); // default FACING = NORTH
		EnergyStorage in = port(helper, Direction.NORTH);
		if (in == null || !in.supportsInsertion() || in.supportsExtraction()) {
			helper.fail("battery_box input face (FACING=north) must be IN-only");
		}
		EnergyStorage out = port(helper, Direction.SOUTH);
		if (out == null || !out.supportsExtraction() || out.supportsInsertion()) {
			helper.fail("battery_box output face (opposite FACING=south) must be OUT-only");
		}
		for (Direction d : new Direction[] { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN }) {
			if (port(helper, d) != null) {
				helper.fail("battery_box face " + d + " must be inert (no energy port)");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements R-NRG-03 — BatteryBox single-axis IO (MOD-006) holds for EVERY {@code FACING} rotation,
	 *     not just the default NORTH: placed at each of the 4 horizontal directions in turn, input =
	 *     FACING, output = opposite FACING, and the remaining 4 faces (the other horizontal axis + UP/DOWN)
	 *     stay inert. Previously only NORTH was exercised (see
	 *     {@code rNrg03_batteryBoxInputOutputFaces}); this closes the other 3 rotations.
	 */
	@GameTest
	public void rNrg03_batteryBoxAllFacingDirections(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			helper.setBlock(POS, ModBlocks.BATTERY_BOX.defaultBlockState()
					.setValue(HorizontalMachineBlock.FACING, facing));

			EnergyStorage in = port(helper, facing);
			if (in == null || !in.supportsInsertion() || in.supportsExtraction()) {
				helper.fail("battery_box input face (FACING=" + facing + ") must be IN-only");
			}
			Direction opposite = facing.getOpposite();
			EnergyStorage out = port(helper, opposite);
			if (out == null || !out.supportsExtraction() || out.supportsInsertion()) {
				helper.fail("battery_box output face (opposite FACING=" + facing + " -> " + opposite
						+ ") must be OUT-only");
			}
			for (Direction d : Direction.values()) {
				if (d == facing || d == opposite) {
					continue;
				}
				if (port(helper, d) != null) {
					helper.fail("battery_box face " + d + " must be inert for FACING=" + facing);
				}
			}
		}
		helper.succeed();
	}
}
