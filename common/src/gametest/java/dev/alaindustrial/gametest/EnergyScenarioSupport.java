package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared fixture for the loader-neutral world-based energy gametest bodies (MOD-022), which live in
 * the thematic scenario classes {@link CableEnergyScenarios}, {@link StorageEnergyScenarios},
 * {@link GeneratorEnergyScenarios}, {@link MachineEnergyScenarios} and {@link WorldContentScenarios}.
 * Each scenario is a plain {@code Consumer<GameTestHelper>} using only the vanilla
 * {@code GameTestHelper} + loader-neutral content ({@code ModContent}, {@code NetworkManager},
 * {@code Config}, the common {@code BlockEntity} classes) — no loader-specific gametest
 * infrastructure. Both the Fabric {@code @GameTest} suite (via its own copies) and the NeoForge
 * {@code gameTestServer} lane (via {@code dev.alaindustrial.gametest.neoforge.NeoForgeGameTests})
 * exercise the SAME energy core. These are the scenarios the JUnit
 * {@code EphemeralTestServerProvider} could not run — they need a live ticking {@code ServerLevel}.
 *
 * <p>The bodies mirror the Fabric {@code GeneratorGameTest}/{@code NetworkGameTest}/
 * {@code PersistenceGameTest} cases they are traced from (see the "Mirrors:" note on each method);
 * numbers come from {@code Config}.
 */
final class EnergyScenarioSupport {

	private EnergyScenarioSupport() {}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	/** Null-safe world BE lookup by RELATIVE pos (helper.getBlockEntity asserts presence and throws). */
	static BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/**
	 * Tick an energy BE (null-safe — a position with no BE is skipped so rigs may hold plain blocks),
	 * driving the shared {@code EnergyBlockEntity#serverTick} at its absolute pos. Every ticking BE of
	 * the energy core — machines, generators, storage, cables, item and fluid pipes — descends from
	 * {@link EnergyBlockEntity}, so there is nothing to dispatch on. Anything else is a rig mistake and
	 * fails LOUDLY: the previous instanceof chain silently returned for an unknown BE, which is a test
	 * that ticks nothing and stays green (MOD-438). A BE outside the hierarchy either has its own tick
	 * entry point (iron furnace, fluid tank, column segment) — call it explicitly — or no energy tick at
	 * all (a vanilla chest), in which case it does not belong in the tick list.
	 */
	static void tick(GameTestHelper helper, BlockEntity be) {
		if (be == null) {
			return;
		}
		BlockPos p = be.getBlockPos();
		if (be instanceof EnergyBlockEntity e) {
			e.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else {
			throw new IllegalArgumentException("tick(): " + be.getClass().getSimpleName() + " at " + p
					+ " is not an EnergyBlockEntity - drive it explicitly");
		}
	}

	/**
	 * Drive every block of a rig plus the network, {@code ticks} times: each position is ticked (see
	 * {@link #tick}) in the order given, then {@code NetworkManager.tickAll} runs once. Positions with
	 * no BE are skipped. Note the network runs AFTER all blocks — a rig whose consumer must tick after
	 * the network (source, wire, network, sink; see {@link #driveLine}) needs its own loop.
	 */
	static void driveWithNetwork(GameTestHelper helper, int ticks, BlockPos... positions) {
		for (int i = 0; i < ticks; i++) {
			for (BlockPos pos : positions) {
				tick(helper, be(helper, pos));
			}
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	static final BlockPos LINE_GEN = new BlockPos(1, 2, 1);
	static final BlockPos LINE_CABLE = new BlockPos(2, 2, 1);
	static final BlockPos LINE_MAC = new BlockPos(3, 2, 1);

	static void driveLine(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			tick(helper, be(helper, LINE_GEN));
			tick(helper, be(helper, LINE_CABLE));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, LINE_MAC));
		}
	}
}
