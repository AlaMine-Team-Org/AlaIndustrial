package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Upgrade Table's two-block assembly (MOD-483), pinned before its frame is shared with the Workstation's
 * (MOD-715, batch 0): until now no world scenario built or broke a table at all. The mirror of
 * {@code WorkstationScenarios} — two casings assemble, a stack of three pairs the bottom two, losing either
 * half degrades the other, only the assembled lower half takes energy — plus the piston: every state of
 * the table carries a block entity, so a piston cannot shove either half.
 *
 * <p>Like the Workstation's scenarios these call {@link UpgradeTableBlock#tryAssemble} directly: a
 * programmatic placement never reaches {@code setPlacedBy} (MOD-015).
 */
public final class UpgradeTableAssemblyScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(UpgradeTableAssemblyScenarios::twoCasingsAssemble,
						"mod715_upgrade_table_two_casings_assemble"),
				RosterEntry.of(UpgradeTableAssemblyScenarios::threeCasingsPairTheBottomTwo,
						"mod715_upgrade_table_three_casings_pair_the_bottom_two"),
				RosterEntry.of(UpgradeTableAssemblyScenarios::breakingUpperDegradesLower,
						"mod715_upgrade_table_breaking_upper_degrades_lower"),
				RosterEntry.of(UpgradeTableAssemblyScenarios::breakingLowerDegradesUpper,
						"mod715_upgrade_table_breaking_lower_degrades_upper"),
				RosterEntry.of(UpgradeTableAssemblyScenarios::onlyTheLowerHalfTakesEnergy,
						"mod715_upgrade_table_only_the_lower_half_takes_energy"),
				RosterEntry.of(UpgradeTableAssemblyScenarios::aPistonMovesNeitherHalf,
						"mod715_upgrade_table_a_piston_moves_neither_half").ticks(60));

		private Roster() {}
	}

	private static final BlockPos BASE = new BlockPos(3, 2, 3);
	/** Comfortably past the two ticks a piston needs to extend. */
	private static final int SETTLE_TICKS = 12;

	private UpgradeTableAssemblyScenarios() {
	}

	private static BlockState casing() {
		return ModContent.UPGRADE_TABLE.get().defaultBlockState();
	}

	private static void placePair(ServerLevel level, BlockPos base) {
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		UpgradeTableBlock.tryAssemble(level, base.above());
	}

	private static WorkstationPart partAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.hasProperty(UpgradeTableBlock.PART) ? state.getValue(UpgradeTableBlock.PART) : null;
	}

	private static boolean expectParts(GameTestHelper helper, BlockPos base, WorkstationPart lower,
			WorkstationPart upper, String when) {
		ServerLevel level = helper.getLevel();
		if (partAt(level, base) != lower || partAt(level, base.above()) != upper) {
			helper.fail(when + ": expected " + lower + "/" + upper + ", got " + partAt(level, base) + "/"
					+ partAt(level, base.above()));
			return false;
		}
		return true;
	}

	/**
	 * Two casings stacked become one table, the machine living in the lower half.
	 *
	 * @implements MOD-715-UT01 — two casings assemble into a table
	 */
	public static void twoCasingsAssemble(GameTestHelper helper) {
		BlockPos base = helper.absolutePos(BASE);
		placePair(helper.getLevel(), base);
		if (!expectParts(helper, base, WorkstationPart.LOWER, WorkstationPart.UPPER, "after assembly")) {
			return;
		}
		Direction side = helper.getLevel().getBlockState(base).getValue(HorizontalMachineBlock.FACING).getClockWise();
		if (!(helper.getLevel().getBlockEntity(base) instanceof UpgradeTableBlockEntity lower)
				|| lower.energyRoleForFace(side) != EnergyRole.IN) {
			helper.fail("the lower half must carry the machine's block entity, taking energy on its side");
			return;
		}
		helper.succeed();
	}

	/**
	 * Three casings in a column pair the bottom two; the third is left a casing.
	 *
	 * @implements MOD-715-UT02 — a three-high stack assembles deterministically
	 */
	public static void threeCasingsPairTheBottomTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		level.setBlockAndUpdate(base.above(2), casing());
		UpgradeTableBlock.tryAssemble(level, base.above());
		UpgradeTableBlock.tryAssemble(level, base.above(2));
		if (!expectParts(helper, base, WorkstationPart.LOWER, WorkstationPart.UPPER, "three high")) {
			return;
		}
		if (partAt(level, base.above(2)) != WorkstationPart.SINGLE) {
			helper.fail("the third casing must be left alone, got " + partAt(level, base.above(2)));
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the upper half leaves the lower one a casing again.
	 *
	 * @implements MOD-715-UT03 — losing the upper half degrades the lower
	 */
	public static void breakingUpperDegradesLower(GameTestHelper helper) {
		BlockPos base = helper.absolutePos(BASE);
		placePair(helper.getLevel(), base);
		helper.getLevel().destroyBlock(base.above(), false);
		if (partAt(helper.getLevel(), base) != WorkstationPart.SINGLE) {
			helper.fail("the lower half must fall back to a casing, got " + partAt(helper.getLevel(), base));
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the lower half leaves the upper one a casing again.
	 *
	 * @implements MOD-715-UT04 — losing the lower half degrades the upper
	 */
	public static void breakingLowerDegradesUpper(GameTestHelper helper) {
		BlockPos base = helper.absolutePos(BASE);
		placePair(helper.getLevel(), base);
		helper.getLevel().destroyBlock(base, false);
		if (partAt(helper.getLevel(), base.above()) != WorkstationPart.SINGLE) {
			helper.fail("the orphaned upper half must fall back to a casing, got "
					+ partAt(helper.getLevel(), base.above()));
			return;
		}
		helper.succeed();
	}

	/**
	 * A loose casing and the upper half are energy-inert; the assembled lower half takes energy on its side
	 * faces and not on its front, and the cable arm agrees with the role.
	 *
	 * @implements MOD-715-UT05 — only the assembled lower half is an energy sink
	 */
	public static void onlyTheLowerHalfTakesEnergy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		level.setBlockAndUpdate(base, casing());
		if (!(level.getBlockEntity(base) instanceof UpgradeTableBlockEntity loose)
				|| loose.energyRoleForFace(Direction.EAST) != EnergyRole.NONE
				|| ModContent.UPGRADE_TABLE.get() instanceof UpgradeTableBlock block
						&& block.isCableConnectable(level.getBlockState(base), Direction.EAST)) {
			helper.fail("a loose casing must be energy-inert and draw no cable arm");
			return;
		}
		placePair(level, base);
		UpgradeTableBlock block = (UpgradeTableBlock) ModContent.UPGRADE_TABLE.get();
		Direction front = level.getBlockState(base).getValue(HorizontalMachineBlock.FACING);
		Direction side = front.getClockWise();
		if (!(level.getBlockEntity(base) instanceof UpgradeTableBlockEntity lower)
				|| lower.energyRoleForFace(side) != EnergyRole.IN
				|| !block.isCableConnectable(level.getBlockState(base), side)) {
			helper.fail("the assembled lower half must accept energy, and draw an arm, on its side faces");
			return;
		}
		if (lower.energyRoleForFace(front) != EnergyRole.NONE
				|| block.isCableConnectable(level.getBlockState(base), front)) {
			helper.fail("the front face of the lower half must be energy-inert");
			return;
		}
		if (!(level.getBlockEntity(base.above()) instanceof UpgradeTableBlockEntity upper)
				|| upper.energyRoleForFace(side) != EnergyRole.NONE
				|| block.isCableConnectable(level.getBlockState(base.above()), side)) {
			helper.fail("the upper half must stay energy-inert and draw no arm");
			return;
		}
		helper.succeed();
	}

	/**
	 * A piston firing at either half moves nothing: the table stays assembled and the piston stays retracted.
	 *
	 * @implements MOD-715-UT06 — a piston moves neither half of the table
	 */
	public static void aPistonMovesNeitherHalf(GameTestHelper helper) {
		BlockPos base = helper.absolutePos(BASE);
		placePair(helper.getLevel(), base);
		BlockPos lowerPiston = BASE.west();
		BlockPos upperPiston = BASE.above().west();
		helper.setBlock(lowerPiston, Blocks.PISTON.defaultBlockState()
				.setValue(DirectionalBlock.FACING, Direction.EAST));
		helper.setBlock(upperPiston, Blocks.PISTON.defaultBlockState()
				.setValue(DirectionalBlock.FACING, Direction.EAST));
		helper.setBlock(lowerPiston.west(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(upperPiston.west(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			for (BlockPos piston : List.of(lowerPiston, upperPiston)) {
				BlockState state = helper.getBlockState(piston);
				if (!state.is(Blocks.PISTON) || state.getValue(PistonBaseBlock.EXTENDED)) {
					helper.fail("the piston at " + piston + " extended into the table: " + state);
					return;
				}
			}
			if (expectParts(helper, base, WorkstationPart.LOWER, WorkstationPart.UPPER, "after the pistons fired")) {
				helper.succeed();
			}
		});
	}
}
