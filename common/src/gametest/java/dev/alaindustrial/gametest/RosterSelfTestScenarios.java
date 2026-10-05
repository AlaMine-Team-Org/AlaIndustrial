package dev.alaindustrial.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-tests of the scenario roster itself (MOD-717, ADR-038): that an entry of {@link ScenarioRoster}
 * reaches each lane as a running test, and that its non-default parameters reach the test instance.
 * They began as the D2 spike probes ({@code docs/tasks/MOD-717-gametest-scaffolding/research.md}).
 *
 * <ul>
 *   <li>{@link #trivialPass} — default parameters; proves the entry registers and its body runs. It logs
 *       the block above the structure as the control for the second test: with the default
 *       {@code skyAccess=false} the game puts a barrier ceiling there.</li>
 *   <li>{@link #skyAccessAndLongBudget} — sky access (fails on a barrier ceiling) and a tick budget above
 *       every lane default (succeeds only at tick {@value #LATE_TICK}, so a budget lost on the way times
 *       out and the failure names the budget the instance really had).</li>
 * </ul>
 *
 * <p>Both run under the same id on both lanes ({@code alaindustrial:roster_self_test_*}) in the lanes'
 * default environments, so they also cover the default-environment lookup of each replayer.
 */
public final class RosterSelfTestScenarios {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	/** Tick at which the long-budget test succeeds: above the Fabric default (20) and every NeoForge budget. */
	static final long LATE_TICK = 150;

	/** This class's roster entries (nested so reading them does not initialise the class). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RosterSelfTestScenarios::trivialPass, "roster_self_test_trivial_pass"),
				RosterEntry.of(RosterSelfTestScenarios::skyAccessAndLongBudget, "roster_self_test_sky_long_budget")
						.ticks(200)
						.sky());

		private Roster() {}
	}

	private RosterSelfTestScenarios() {}

	/** Default parameters; logs the ceiling cell as the control for {@link #skyAccessAndLongBudget}. */
	public static void trivialPass(GameTestHelper helper) {
		BlockPos ceiling = ceilingCell(helper);
		LOGGER.info("[scenario roster] roster_self_test_trivial_pass ran; ceiling {} = {} (control: expect barrier)",
				ceiling.toShortString(), helper.getLevel().getBlockState(ceiling).getBlock());
		helper.succeed();
	}

	/** Needs {@code skyAccess=true} and a budget above {@value #LATE_TICK} ticks, both from the roster. */
	public static void skyAccessAndLongBudget(GameTestHelper helper) {
		BlockPos ceiling = ceilingCell(helper);
		boolean barrier = helper.getLevel().getBlockState(ceiling).is(Blocks.BARRIER);
		helper.assertFalse(barrier, "[scenario roster] barrier ceiling at " + ceiling.toShortString()
				+ ": skyAccess=true from the roster did not reach the test instance");
		helper.runAfterDelay(LATE_TICK, () -> {
			LOGGER.info("[scenario roster] roster_self_test_sky_long_budget passed at tick {}; ceiling {} is open",
					helper.getTick(), ceiling.toShortString());
			helper.succeed();
		});
	}

	/**
	 * The cell the game turns into a barrier when the instance has no sky access:
	 * {@code TestInstanceBlockEntity.processStructureBoundary} encases the structure and adds the row
	 * {@code y = floor(bounds.maxY)} only when {@code skyAccess} is false.
	 */
	private static BlockPos ceilingCell(GameTestHelper helper) {
		AABB bounds = helper.getBounds();
		return BlockPos.containing((bounds.minX + bounds.maxX) / 2, bounds.maxY, (bounds.minZ + bounds.maxZ) / 2);
	}
}
