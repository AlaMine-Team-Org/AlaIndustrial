package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

/**
 * Shared loader-neutral gametest helpers, extracted to kill the {@code place}/{@code drive} boilerplate
 * that was copy-pasted across the Fabric {@code @GameTest} classes ({@code MachineGameTest},
 * {@code GeneratorGameTest}, {@code BatteryBoxGameTest}, {@code WaterMillWheelGameTest},
 * {@code WindMillGameTest}, {@code FluidGameTest}, {@code SolarPanelGameTest}, …).
 *
 * <p>Every ticking block entity of the energy core is an {@link EnergyBlockEntity} descendant — the
 * machines, generators, solar panels, wind/water mills, battery box, pump and geothermal through
 * {@link MachineBlockEntity}, and the cable, item pipe and fluid pipe directly — and therefore shares
 * the {@code final serverTick(Level, BlockPos, BlockState)} defined there, so a single generic
 * {@link #drive} covers them all. Ticking a resolved BE once (null-safe, loud on a foreign BE) is
 * {@code EnergyScenarioSupport#tick(helper, be)} — pair it with {@code EnergyScenarioSupport#be(helper, rel)}
 * for a RELATIVE-position lookup; a whole rig plus the network is
 * {@code EnergyScenarioSupport#driveWithNetwork}.
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
	 * Drive an energy BE (machine, generator, storage, cable, pipe) for {@code ticks} ticks by invoking
	 * its {@code serverTick} directly — deterministic and fast, with no {@code Thread.sleep} or
	 * real-time waiting. The level/pos/state passed are the BE's own, read fresh each tick (so
	 * placement-driven state cascades apply).
	 */
	public static void drive(EnergyBlockEntity be, GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			BlockPos p = be.getBlockPos();
			be.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		}
	}

	/**
	 * A survival mock player that is a real member of the level: added to the player list with a
	 * live (embedded-channel) connection, so it can open menus, be sent packets, be found by entity
	 * queries and go through {@code setGameMode}. Wraps the vanilla
	 * {@code GameTestHelper#makeMockServerPlayerInLevel()}.
	 *
	 * <p>Why the two extra lines are load-bearing (MOD-081, verified against the 26.2 sources): the
	 * vanilla mock overrides {@code gameMode()} to return {@code CREATIVE} unconditionally, and
	 * {@code setGameMode} cannot undo that override — {@code Player#isCreative()} is therefore always
	 * true on this mock. The half we DO control is {@code Abilities}: {@code hasInfiniteMaterials()} and
	 * {@code preventsBlockDrops()} read {@code abilities.instabuild}, which is what {@code ItemEnergy.free},
	 * durability, block drops and inventory decrements key off. Forcing {@code instabuild} off makes the
	 * mock spend EU, wear tools and drop loot like a real survival player; leaving it on turns every EU /
	 * durability / drop assertion in a suite vacuous while staying green.
	 *
	 * <p>Per-file post-steps stay with the caller — e.g. the scythe pins {@code setYRot(0)} so its AOE
	 * box direction is deterministic. Only what every survival mock needs lives here.
	 *
	 * <p>Not this helper: the detached variant {@link #detachedSurvivalPlayer} for tests that never
	 * touch the connection (packets, {@code setGameMode}, {@code snapTo}) — those would NPE on it.
	 */
	public static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	/**
	 * A survival mock player that is NOT in the level: no connection, not in the player list, created
	 * at the world origin. Wraps the vanilla {@code GameTestHelper#makeMockServerPlayer(GameType)},
	 * which hardcodes {@code gameMode()} to the given type and applies that type's abilities. Anything
	 * that reaches for {@code connection} — {@code setGameMode}, {@code snapTo} (dies in
	 * {@code resetPosition()}), packet sends — throws on this mock; use {@link #survivalPlayer} there.
	 *
	 * <p>Every invulnerability path is cleared explicitly, because the tests that need a detached
	 * player (cable shock, breaker, charge pad) exist to prove the player CAN be hurt or debited:
	 * {@code abilities.instabuild}/{@code invulnerable} for the {@link #survivalPlayer} reasons,
	 * {@code Entity#setInvulnerable(false)} for the entity-level flag, and {@code invulnerableTime = 0}
	 * so the post-hit grace window never swallows the first hit. The position is the caller's job:
	 * the mock spawns at the world origin, and a test that reads "who is standing in this cell" must
	 * {@code setPos} the player into it or it passes/fails for the wrong reason.
	 */
	public static ServerPlayer detachedSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		player.setInvulnerable(false);
		player.invulnerableTime = 0;
		return player;
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
