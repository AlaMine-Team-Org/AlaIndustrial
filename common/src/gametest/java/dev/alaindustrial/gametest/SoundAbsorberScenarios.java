package dev.alaindustrial.gametest;

import dev.alaindustrial.block.SoundAbsorberBlock;
import dev.alaindustrial.core.acoustic.AbsorberTier;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MOD-804 — the sound absorber's group state on the server: the tier every block of a group carries (the
 * lamps, and what the client reads to build the zones), the seams, and how both follow a placement and a
 * break. Zone geometry and the "strongest zone wins" rule are Minecraft-free and pinned on L1
 * ({@code AcousticZonesTest}).
 */
public final class SoundAbsorberScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SoundAbsorberScenarios::tiersFollowTheGroupAsItGrows,
						"sound_absorber_tiers_follow_the_group_as_it_grows").ticks(40),
				RosterEntry.of(SoundAbsorberScenarios::breakingSplitsAndLowersTheGroup,
						"sound_absorber_breaking_splits_and_lowers_the_group").ticks(40),
				RosterEntry.of(SoundAbsorberScenarios::farEndOfALongGroupFollowsTheChange,
						"sound_absorber_far_end_of_a_long_group_follows_the_change").ticks(40),
				RosterEntry.of(SoundAbsorberScenarios::groupPastTheCapStaysTierFive,
						"sound_absorber_group_past_the_cap_stays_tier_five").ticks(60));

		private Roster() {}
	}

	private SoundAbsorberScenarios() {
	}

	/** 1, 2, 4 (2x2), 8 (2x2x2) and 16 (4x2x2) blocks: tiers I..V on EVERY block of the group, seams joined. */
	public static void tiersFollowTheGroupAsItGrows(GameTestHelper helper) {
		List<BlockPos> group = new ArrayList<>();
		int[][] order = {
				{0, 1, 0},
				{1, 1, 0},
				{0, 2, 0}, {1, 2, 0},
				{0, 1, 1}, {1, 1, 1}, {0, 2, 1}, {1, 2, 1},
				{2, 1, 0}, {3, 1, 0}, {2, 2, 0}, {3, 2, 0}, {2, 1, 1}, {3, 1, 1}, {2, 2, 1}, {3, 2, 1}};
		for (int[] at : order) {
			BlockPos pos = new BlockPos(at[0], at[1], at[2]);
			place(helper, pos);
			group.add(pos);
			int size = group.size();
			if (size == 1 || size == 2 || size == 4 || size == 8 || size == 16) {
				expectTier(helper, group, AbsorberTier.forGroupSize(size).level(), size + " blocks");
			}
		}
		BlockState first = helper.getBlockState(new BlockPos(0, 1, 0));
		boolean joinedAsExpected = first.getValue(PipeBlock.EAST) && first.getValue(PipeBlock.UP)
				&& first.getValue(PipeBlock.SOUTH);
		boolean openAsExpected = !first.getValue(PipeBlock.WEST) && !first.getValue(PipeBlock.DOWN)
				&& !first.getValue(PipeBlock.NORTH);
		if (!joinedAsExpected || !openAsExpected) {
			helper.fail("the corner absorber's seams must be joined east, up and south only, got " + first);
		}
		helper.succeed();
	}

	/** A row of five (tier III); breaking the middle leaves two pairs (II), breaking an end of one a single (I). */
	public static void breakingSplitsAndLowersTheGroup(GameTestHelper helper) {
		List<BlockPos> row = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			BlockPos pos = new BlockPos(x, 1, 2);
			place(helper, pos);
			row.add(pos);
		}
		expectTier(helper, row, 3, "a row of five");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		expectTier(helper, List.of(new BlockPos(0, 1, 2), new BlockPos(1, 1, 2)), 2,
				"the west pair after the split");
		expectTier(helper, List.of(new BlockPos(3, 1, 2), new BlockPos(4, 1, 2)), 2,
				"the east pair after the split");
		if (helper.getBlockState(new BlockPos(1, 1, 2)).getValue(PipeBlock.EAST)) {
			helper.fail("the seam toward the broken block must open");
		}
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);
		expectTier(helper, List.of(new BlockPos(3, 1, 2)), 1, "the lone block left of the east pair");
		helper.succeed();
	}

	/**
	 * A snake of fifteen (tier IV), then a sixteenth at its head: the block fifteen steps away along the group
	 * must turn V as well — the refresh walk must reach the whole group a tier change can touch.
	 */
	public static void farEndOfALongGroupFollowsTheChange(GameTestHelper helper) {
		List<BlockPos> snake = new ArrayList<>();
		for (int x = 0; x <= 7; x++) {
			snake.add(new BlockPos(x, 1, 0));
		}
		snake.add(new BlockPos(7, 1, 1));
		for (int x = 7; x >= 2; x--) {
			snake.add(new BlockPos(x, 1, 2));
		}
		for (BlockPos pos : snake) {
			place(helper, pos);
		}
		expectTier(helper, snake, 4, "a snake of fifteen");
		BlockPos head = new BlockPos(1, 1, 2);
		place(helper, head);
		snake.add(head);
		expectTier(helper, snake, 5, "the snake grown to sixteen (the tail is fifteen steps from the new head)");
		helper.succeed();
	}

	/** Forty-eight blocks (4x4x3): tier V everywhere, counting stops at the cap, breaking one changes nothing. */
	public static void groupPastTheCapStaysTierFive(GameTestHelper helper) {
		List<BlockPos> block = new ArrayList<>();
		for (int x = 0; x < 4; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 0; z < 4; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					place(helper, pos);
					block.add(pos);
				}
			}
		}
		expectTier(helper, block, 5, "a 4x4x3 group of 48");
		int counted = SoundAbsorberBlock.groupSize(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)),
				AbsorberTier.GROUP_CAP + 1);
		if (counted != AbsorberTier.GROUP_CAP + 1) {
			helper.fail("counting a 48-block group must stop at the limit it was given, counted " + counted);
		}
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
		block.remove(new BlockPos(1, 2, 1));
		expectTier(helper, block, 5, "the group after one block is broken out of its middle");
		helper.succeed();
	}

	private static void place(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModContent.SOUND_ABSORBER.get());
	}

	private static void expectTier(GameTestHelper helper, List<BlockPos> blocks, int tier, String what) {
		for (BlockPos pos : blocks) {
			BlockState state = helper.getBlockState(pos);
			if (!(state.getBlock() instanceof SoundAbsorberBlock)) {
				helper.fail(what + ": no absorber at " + pos);
				return;
			}
			if (state.getValue(SoundAbsorberBlock.TIER) != tier) {
				helper.fail(what + ": the absorber at " + pos + " shows tier "
						+ state.getValue(SoundAbsorberBlock.TIER) + ", want " + tier);
				return;
			}
		}
	}
}
