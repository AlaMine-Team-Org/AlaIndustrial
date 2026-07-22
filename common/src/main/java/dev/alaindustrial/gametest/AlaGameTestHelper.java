package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;

/**
 * Shared loader-neutral gametest helpers, extracted to kill the {@code place}/{@code drive} boilerplate
 * that was copy-pasted across the Fabric {@code @GameTest} classes ({@code MachineGameTest},
 * {@code GeneratorGameTest}, {@code BatteryBoxGameTest}, {@code WaterMillWheelGameTest},
 * {@code WindMillGameTest}, {@code FluidGameTest}, {@code SolarPanelGameTest}, …).
 *
 * <p>Every ticking block entity in the mod is a {@link MachineBlockEntity} descendant (machines,
 * generators, solar panels, wind/water mills, battery box, pump, geothermal) and therefore shares the
 * {@code final serverTick(Level, BlockPos, BlockState)} defined there — so a single generic
 * {@link #drive} covers them all. {@code CableBlockEntity} is the one ticking BE outside that hierarchy;
 * it is not driven through here (see {@code CoreEnergyScenarios#tick} for its instanceof dispatch).
 *
 * <p>These helpers use only {@link GameTestHelper} + loader-neutral content, so both loader gametest
 * lanes (Fabric {@code @GameTest}, NeoForge {@code gameTestServer}) can call them.
 */
public final class AlaGameTestHelper {

	private AlaGameTestHelper() {}

	/**
	 * Place {@code block} at {@code pos} (relative to the test rig origin) and return its
	 * {@link MachineBlockEntity}, failing the test with a clear message if the BE is missing.
	 * The returned BE is the canonical typed handle (energy storage, inventory, slots) the test
	 * drives directly — no real-time tick waiting, see {@link #drive}.
	 *
	 * @param helper the gametest harness
	 * @param pos    relative position inside the rig
	 * @param block  the machine/generator block to place
	 * @return the freshly placed block entity
	 */
	public static MachineBlockEntity place(GameTestHelper helper, BlockPos pos, Block block) {
		helper.setBlock(pos, block);
		return requireMachine(helper, pos, block);
	}

	/**
	 * Place {@code block} at {@code pos} (relative) and return its BE typed as {@code type}, failing
	 * the test if the BE is missing or the wrong subclass. Use the typed variant when the test calls
	 * subclass-specific accessors (e.g. {@code SolarPanelBlockEntity#evolve}, {@code PumpBlockEntity#getTank}).
	 */
	public static <T extends MachineBlockEntity> T place(GameTestHelper helper, BlockPos pos, Block block, Class<T> type) {
		helper.setBlock(pos, block);
		if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null) {
			helper.fail("block entity missing for " + block + " at " + pos);
		}
		T be = helper.getBlockEntity(pos, type);
		if (be == null) {
			helper.fail(block + " at " + pos + " is not a " + type.getSimpleName());
		}
		return be;
	}

	/**
	 * Drive a machine BE for {@code ticks} ticks by invoking its {@code serverTick} directly —
	 * deterministic and fast, with no {@code Thread.sleep} or real-time waiting. The level/pos/state
	 * passed are the BE's own, read fresh each tick (so placement-driven state cascades apply).
	 */
	public static void drive(MachineBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			BlockPos p = be.getBlockPos();
			be.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		}
	}

	private static MachineBlockEntity requireMachine(GameTestHelper helper, BlockPos pos, Block block) {
		// 26.2 GameTestHelper#getBlockEntity is typed (requires a Class) and asserts presence itself,
		// but a missing BE would throw rather than report a clear test failure — so we probe the raw
		// world first and fail the test with a useful message.
		if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null) {
			helper.fail("block entity missing for " + block + " at " + pos);
		}
		return helper.getBlockEntity(pos, MachineBlockEntity.class);
	}
}
