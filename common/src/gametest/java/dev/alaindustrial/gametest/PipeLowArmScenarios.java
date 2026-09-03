package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.core.item.PipeFaceRender;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * MOD-540: a pipe joining a half-block neighbour sideways drops its arm so the sleeve hugs the
 * neighbour's side instead of hanging in the air above it.
 *
 * <p>The rig uses the garden drone station — the 4px block from the bug report — and the mod's iron
 * chest as the full-height control. Both are containers, so the item pipe genuinely connects to
 * them; nothing here asserts on a face that a real pipe would refuse to draw.
 *
 * <p>Every check reads BOTH the blockstate and {@code getShape}. That pairing is the point: MOD-195
 * was a case where the two disagreed — geometry drawn where nothing could be clicked, and a hitbox
 * where nothing was drawn — and no gate in this repo compares a model against a shape.
 */
public final class PipeLowArmScenarios {
	private PipeLowArmScenarios() {
	}

	private static final BlockPos PIPE = new BlockPos(2, 2, 2);
	/**
	 * The bottom of the low arm's nozzle in block units. It sits level with the neighbour's own side
	 * face, so a dropped arm reaches the floor of its cell; a level arm never goes below core height.
	 */
	private static final double NOZZLE_BOTTOM = 0.0;
	private static final double EPSILON = 1.0E-6;

	private static PipeFaceRender itemRender(GameTestHelper helper, Direction face) {
		return ItemPipeBlock.renderAt(helper.getBlockState(PIPE), face);
	}

	private static double pipeShapeFloor(GameTestHelper helper) {
		BlockState state = helper.getBlockState(PIPE);
		return state.getShape(helper.getLevel(), helper.absolutePos(PIPE), CollisionContext.empty())
				.bounds().minY;
	}

	private static void refresh(GameTestHelper helper) {
		ItemPipeBlock.refreshConnections(helper.getLevel(), helper.absolutePos(PIPE));
	}

	/** Place the pipe with a low neighbour to the west and a full-height one to the east. */
	private static void buildItemRig(GameTestHelper helper) {
		helper.setBlock(PIPE.west(), ModContent.GARDEN_DRONE_STATION.get());
		helper.setBlock(PIPE.east(), ModContent.IRON_CHEST.get());
		helper.setBlock(PIPE, ModContent.ITEM_PIPE.get());
		refresh(helper);
	}

	/** The half-block face drops; the full-block face beside it does not. */
	public static void itemPipeDropsArmTowardHalfBlock(GameTestHelper helper) {
		buildItemRig(helper);
		if (itemRender(helper, Direction.WEST) != PipeFaceRender.NEUTRAL_LOW) {
			helper.fail("MOD-540: west face toward the 4px drone station should draw a low arm, drew "
					+ itemRender(helper, Direction.WEST));
		}
		if (itemRender(helper, Direction.EAST) != PipeFaceRender.NEUTRAL) {
			helper.fail("MOD-540: east face toward a full-height chest must keep the level arm, drew "
					+ itemRender(helper, Direction.EAST));
		}
		double floor = pipeShapeFloor(helper);
		if (Math.abs(floor - NOZZLE_BOTTOM) > EPSILON) {
			helper.fail("MOD-540: the outline must reach down to the nozzle bottom (" + NOZZLE_BOTTOM
					+ "), lowest point was " + floor);
		}
		helper.succeed();
	}

	/** A pipe with only full-height neighbours keeps its arms at core height — no regression. */
	public static void itemPipeKeepsArmLevelWithoutHalfBlocks(GameTestHelper helper) {
		helper.setBlock(PIPE.west(), ModContent.IRON_CHEST.get());
		helper.setBlock(PIPE.east(), ModContent.IRON_CHEST.get());
		helper.setBlock(PIPE, ModContent.ITEM_PIPE.get());
		refresh(helper);
		for (Direction face : new Direction[] { Direction.WEST, Direction.EAST }) {
			if (itemRender(helper, face) != PipeFaceRender.NEUTRAL) {
				helper.fail("MOD-540: " + face + " face has no half-block neighbour, drew "
						+ itemRender(helper, face));
			}
		}
		double floor = pipeShapeFloor(helper);
		if (floor < 6 / 16.0 - EPSILON) {
			helper.fail("MOD-540: nothing should drop below core height here, lowest point was " + floor);
		}
		helper.succeed();
	}

	/** The face keeps its routing mode when it drops — a lowered extract face still reads as extract. */
	public static void itemPipeLowArmKeepsFaceMode(GameTestHelper helper) {
		buildItemRig(helper);
		// getBlockEntity(pos, type) fails the test itself when the entity is missing or of another
		// class, so there is nothing to assert here beyond asking for it.
		ItemPipeBlockEntity pipe = helper.getBlockEntity(PIPE, ItemPipeBlockEntity.class);
		pipe.setFaceMode(Direction.WEST, PipeFaceMode.EXTRACT);
		refresh(helper);
		if (itemRender(helper, Direction.WEST) != PipeFaceRender.EXTRACT_LOW) {
			helper.fail("MOD-540: a dropped extract face must stay extract, drew "
					+ itemRender(helper, Direction.WEST));
		}
		if (ItemPipeBlock.renderAt(helper.getBlockState(PIPE), Direction.WEST).mode()
				!= PipeFaceMode.EXTRACT) {
			helper.fail("MOD-540: the rendered value must report the mode it was built from");
		}
		helper.succeed();
	}

	/** Vertical faces have no low variant: a pipe sitting on a half-block connects as it always did. */
	public static void itemPipeVerticalFaceNeverDrops(GameTestHelper helper) {
		helper.setBlock(PIPE.below(), ModContent.GARDEN_DRONE_STATION.get());
		helper.setBlock(PIPE, ModContent.ITEM_PIPE.get());
		refresh(helper);
		if (itemRender(helper, Direction.DOWN) != PipeFaceRender.NEUTRAL) {
			helper.fail("MOD-540: the down face must never take a low variant, drew "
					+ itemRender(helper, Direction.DOWN));
		}
		helper.succeed();
	}

	/**
	 * A pipe whose saved state predates the low arm (or whose neighbour changed height while the chunk
	 * slept) corrects itself. This is the work the block entity does once on its first server tick;
	 * driving it through the same entry point proves the re-derive, not the tick scheduling.
	 */
	public static void itemPipeRederivesStaleLowArm(GameTestHelper helper) {
		buildItemRig(helper);
		BlockState stale = helper.getBlockState(PIPE);
		helper.setBlock(PIPE, stale);
		ItemPipeBlock.refreshConnections(helper.getLevel(), helper.absolutePos(PIPE));
		if (itemRender(helper, Direction.WEST) != PipeFaceRender.NEUTRAL_LOW) {
			helper.fail("MOD-540: a stale face must be re-derived to the low arm, got "
					+ itemRender(helper, Direction.WEST));
		}
		helper.succeed();
	}

	/**
	 * The fluid pipe shares the geometry, and its shape must drop the same way. No block in the mod is
	 * both a fluid port and a half-block today, so the state is set directly: this covers the branch a
	 * future low tank would take, and fails if the two pipes' shapes ever diverge.
	 */
	public static void fluidPipeLowArmDropsShape(GameTestHelper helper) {
		helper.setBlock(PIPE, ModContent.FLUID_PIPE.get());
		BlockState low = helper.getBlockState(PIPE);
		for (Direction face : Direction.values()) {
			if (FluidPipeBlock.renderAt(low, face) != PipeFaceRender.DISABLED) {
				helper.fail("MOD-540: a lone fluid pipe should draw no arms, " + face + " drew "
						+ FluidPipeBlock.renderAt(low, face));
			}
		}
		low = FluidPipeBlock.withRender(low, Direction.WEST, PipeFaceRender.NEUTRAL_LOW);
		helper.setBlock(PIPE, low);
		double floor = helper.getBlockState(PIPE)
				.getShape(helper.getLevel(), helper.absolutePos(PIPE), CollisionContext.empty())
				.bounds().minY;
		if (Math.abs(floor - NOZZLE_BOTTOM) > EPSILON) {
			helper.fail("MOD-540: the fluid pipe's low arm must reach the same nozzle bottom as the item"
					+ " pipe's (" + NOZZLE_BOTTOM + "), lowest point was " + floor);
		}
		helper.succeed();
	}
}
