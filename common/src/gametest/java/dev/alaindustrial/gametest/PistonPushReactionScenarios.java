package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * What a piston does to the mod's blocks whose push reaction is not the default (MOD-703, batch 0).
 *
 * <p>The push reaction is a version seam: 26.3 spells it {@code POPPED}/{@code IMMOVEABLE}, 26.2
 * {@code DESTROY}/{@code BLOCK}. This scenario names neither — it asks the piston. A block that pops is
 * gone after the piston fires and the piston stands extended; a block that is pinned is still in place
 * and the piston never extends. The registry snapshot ({@code RegistrySnapshotScenarios}) holds the enum
 * value of every block; this is the behaviour that value is supposed to produce, on both lines.
 *
 * <p>The control row pushes a plain machine casing one cell on: if the rig itself were broken (no power,
 * a piston facing the wrong way), the pinned rows would pass for the wrong reason, and the control reddens
 * first.
 */
public final class PistonPushReactionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PistonPushReactionScenarios::pistonPopsAndPinsTheModBlocks,
								"piston_pops_and_pins_the_mod_blocks")
						.fabricId("PistonPushReactionGameTest", "pistonPopsAndPinsTheModBlocks").ticks(100));

		private Roster() {}
	}

	/** Comfortably past the two ticks a piston needs to extend. */
	private static final int SETTLE_TICKS = 12;

	/** One rig: redstone block, piston facing east, the block under test in front of it. */
	private record Rig(String name, BlockPos origin) {
		BlockPos power() {
			return origin.offset(0, 2, 0);
		}

		BlockPos piston() {
			return origin.offset(1, 2, 0);
		}

		BlockPos target() {
			return origin.offset(2, 2, 0);
		}

		BlockPos pushedTo() {
			return origin.offset(3, 2, 0);
		}
	}

	private static final Rig CONTROL = new Rig("machine_casing", new BlockPos(0, 0, 0));
	private static final Rig BUTTON = new Rig("reactor_button", new BlockPos(0, 0, 2));
	private static final Rig TRELLIS = new Rig("trellis", new BlockPos(0, 0, 4));
	private static final Rig DOME = new Rig("incubator_dome", new BlockPos(0, 0, 6));
	private static final Rig FLOOR = new Rig("crystal_farm_floor", new BlockPos(4, 0, 2));

	private PistonPushReactionScenarios() {
	}

	/**
	 * A piston breaks the reactor button and the trellis (both halves), cannot move the incubator dome or
	 * the crystal-farm floor, and pushes a machine casing one cell on.
	 *
	 * @implements MOD-703 — the button and the trellis break, the dome and the farm floor stay, a casing moves.
	 */
	public static void pistonPopsAndPinsTheModBlocks(GameTestHelper helper) {
		List<Rig> rigs = List.of(CONTROL, BUTTON, TRELLIS, DOME, FLOOR);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}

		helper.setBlock(CONTROL.target(), ModContent.MACHINE_CASING.get());
		helper.setBlock(BUTTON.target(), ModContent.REACTOR_BUTTON.get().defaultBlockState()
				.setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
		Block trellis = ModContent.TRELLIS.get();
		helper.setBlock(TRELLIS.target().below(), Blocks.FARMLAND);
		helper.setBlock(TRELLIS.target(), trellis.defaultBlockState()
				.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(TRELLIS.target().above(), trellis.defaultBlockState()
				.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(DOME.target(), ModContent.INCUBATOR_DOME.get());
		helper.setBlock(FLOOR.target(), ModContent.CRYSTAL_FARM_FLOOR.get());

		for (Rig rig : rigs) {
			helper.setBlock(rig.piston(), Blocks.PISTON.defaultBlockState()
					.setValue(DirectionalBlock.FACING, Direction.EAST));
		}
		for (Rig rig : rigs) {
			if (helper.getBlockState(rig.target()).isAir()) {
				helper.fail("fixture error: the " + rig.name() + " did not stay where it was placed, "
						+ "so the piston would have nothing to act on");
				return;
			}
		}
		if (!helper.getBlockState(TRELLIS.target().above()).is(trellis)) {
			helper.fail("fixture error: the trellis lost its upper half before the piston fired");
			return;
		}

		for (Rig rig : rigs) {
			helper.setBlock(rig.power(), Blocks.REDSTONE_BLOCK);
		}

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			// The control first: the rig must be able to move a block at all.
			if (!extended(helper, CONTROL)
					|| !helper.getBlockState(CONTROL.pushedTo()).is(ModContent.MACHINE_CASING.get())) {
				helper.fail("control: the piston did not push the machine casing one cell on — the rig is broken");
				return;
			}
			for (Rig popped : List.of(BUTTON, TRELLIS)) {
				if (!extended(helper, popped)) {
					helper.fail("a piston must break the " + popped.name() + " and extend, it stayed retracted");
					return;
				}
			}
			if (helper.getBlockState(BUTTON.pushedTo()).is(ModContent.REACTOR_BUTTON.get())
					|| helper.getBlockState(BUTTON.target()).is(ModContent.REACTOR_BUTTON.get())) {
				helper.fail("the reactor button survived the piston");
				return;
			}
			for (BlockPos pos : List.of(TRELLIS.target(), TRELLIS.target().above(), TRELLIS.pushedTo(),
					TRELLIS.pushedTo().above())) {
				if (helper.getBlockState(pos).is(trellis)) {
					helper.fail("half of the trellis survived the piston at " + pos);
					return;
				}
			}
			for (Rig pinned : List.of(DOME, FLOOR)) {
				if (extended(helper, pinned)) {
					helper.fail("a piston must not move the " + pinned.name() + ", it extended");
					return;
				}
			}
			if (!helper.getBlockState(DOME.target()).is(ModContent.INCUBATOR_DOME.get())) {
				helper.fail("the incubator dome left its cell");
				return;
			}
			if (!helper.getBlockState(FLOOR.target()).is(ModContent.CRYSTAL_FARM_FLOOR.get())) {
				helper.fail("the crystal-farm floor left its cell");
				return;
			}
			helper.succeed();
		});
	}

	private static boolean extended(GameTestHelper helper, Rig rig) {
		return helper.getBlockState(rig.piston()).is(Blocks.PISTON)
				&& helper.getBlockState(rig.piston()).getValue(PistonBaseBlock.EXTENDED);
	}
}
