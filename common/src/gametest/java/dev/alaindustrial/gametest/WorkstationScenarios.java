package dev.alaindustrial.gametest;

import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the Workstation (MOD-483, suite TC-WKST-001). Wrapped by the
 * Fabric {@code WorkstationGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}, {@code workstation_*}), so both loaders run the SAME bodies.
 *
 * <p>The rig is a 1×1×2 machine at {@code (1, 2, 1)}, which sits well inside the default 8×8×8
 * structure both lanes use — no structure of its own is needed.
 */
public final class WorkstationScenarios {

	private WorkstationScenarios() {
	}

	private static final BlockPos BASE = new BlockPos(1, 2, 1);

	private static BlockState casing() {
		return ModContent.WORKSTATION.get().defaultBlockState();
	}

	private static void placePair(ServerLevel level, BlockPos base) {
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		// A programmatic setBlock never calls setPlacedBy (MOD-015), which is exactly why the assembly
		// hook is public and static — a scenario that could not reach it would have to assert on a
		// machine it has no way to build.
		WorkstationBlock.tryAssemble(level, base.above());
	}

	private static WorkstationPart partAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.hasProperty(WorkstationBlock.PART) ? state.getValue(WorkstationBlock.PART) : null;
	}

	/**
	 * Two casings stacked become one machine.
	 *
	 * @implements TC-WKST-001-FRM01 — assembly from two loose casings
	 */
	public static void frm01TwoCasingsAssemble(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		if (partAt(level, base) != WorkstationPart.LOWER) {
			helper.fail("the bottom casing must become the lower half, got " + partAt(level, base));
			return;
		}
		if (partAt(level, base.above()) != WorkstationPart.UPPER) {
			helper.fail("the top casing must become the upper half, got " + partAt(level, base.above()));
			return;
		}
		if (!(level.getBlockEntity(base) instanceof WorkstationBlockEntity)) {
			helper.fail("the lower half must carry the machine's block entity");
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the upper half leaves the lower one standing as a casing.
	 *
	 * <p>The point is that no removal hook is involved: {@code updateShape} alone answers every way a
	 * half can vanish, so this covers the pickaxe, an explosion and {@code /setblock} at once.
	 *
	 * @implements TC-WKST-001-BRK01 — losing the upper half degrades the lower
	 */
	public static void brk01BreakingUpperDegradesLower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		level.destroyBlock(base.above(), false);
		if (partAt(level, base) != WorkstationPart.SINGLE) {
			helper.fail("the lower half must fall back to a casing, got " + partAt(level, base));
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the lower half leaves the upper one standing as a casing.
	 *
	 * @implements TC-WKST-001-BRK02 — losing the lower half degrades the upper
	 */
	public static void brk02BreakingLowerDegradesUpper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		level.destroyBlock(base, false);
		if (partAt(level, base.above()) != WorkstationPart.SINGLE) {
			helper.fail("the orphaned upper half must fall back to a casing, got "
					+ partAt(level, base.above()));
			return;
		}
		helper.succeed();
	}

	/**
	 * Energy goes into the lower half and nowhere else.
	 *
	 * <p>A loose casing that accepted energy would let a cable charge inventory standing in the world,
	 * and an upper half that accepted it would give the machine two independent buffers.
	 *
	 * @implements TC-WKST-001-NRG01 — only the assembled lower half is an energy sink
	 */
	public static void nrg01OnlyTheLowerHalfTakesEnergy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);

		level.setBlockAndUpdate(base, casing());
		if (level.getBlockEntity(base) instanceof WorkstationBlockEntity loose
				&& loose.energyRoleForFace(Direction.EAST) != EnergyRole.NONE) {
			helper.fail("a loose casing must be energy-inert on every face");
			return;
		}

		placePair(level, base);
		if (!(level.getBlockEntity(base) instanceof WorkstationBlockEntity lower)
				|| lower.energyRoleForFace(Direction.EAST) != EnergyRole.IN) {
			helper.fail("the assembled lower half must accept energy on its side faces");
			return;
		}
		if (!(level.getBlockEntity(base.above()) instanceof WorkstationBlockEntity upper)
				|| upper.energyRoleForFace(Direction.EAST) != EnergyRole.NONE) {
			helper.fail("the upper half must stay energy-inert");
			return;
		}
		// R-NRG-03: the face the player looks at carries no cable arm.
		if (lower.energyRoleForFace(level.getBlockState(base).getValue(WorkstationBlock.FACING))
				!= EnergyRole.NONE) {
			helper.fail("the front face must be energy-inert");
			return;
		}
		helper.succeed();
	}

	/**
	 * A stack of three casings resolves the same way every time.
	 *
	 * <p>Three in a column is the first ambiguous case a player can build, and "whichever pair the
	 * game noticed first" would be a different machine depending on which block was touched last.
	 * The rule is that a casing prefers the partner below it, so the bottom two pair up and the third
	 * is left over.
	 *
	 * @implements TC-WKST-001-FRM02 — a three-high stack assembles deterministically
	 */
	public static void frm02ThreeCasingsPairTheBottomTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		level.setBlockAndUpdate(base.above(2), casing());
		WorkstationBlock.tryAssemble(level, base.above());
		WorkstationBlock.tryAssemble(level, base.above(2));

		if (partAt(level, base) != WorkstationPart.LOWER
				|| partAt(level, base.above()) != WorkstationPart.UPPER) {
			helper.fail("the bottom two casings must be the ones that pair up");
			return;
		}
		if (partAt(level, base.above(2)) != WorkstationPart.SINGLE) {
			helper.fail("the third casing must be left alone, got " + partAt(level, base.above(2)));
			return;
		}
		helper.succeed();
	}
}
