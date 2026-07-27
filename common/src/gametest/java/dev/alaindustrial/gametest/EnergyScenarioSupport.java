package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
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

	/** Tick any of the energy BEs (null-safe), driving its serverTick at its absolute pos. */
	static void tick(GameTestHelper helper, BlockEntity be) {
		if (be == null) {
			return;
		}
		BlockPos p = be.getBlockPos();
		BlockState st = helper.getLevel().getBlockState(p);
		if (be instanceof GeneratorBlockEntity gen) {
			gen.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof CableBlockEntity c) {
			c.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof dev.alaindustrial.block.entity.MachineBlockEntity mac) {
			// Covers macerator/electric_furnace/compressor/extractor — all extend MachineBlockEntity
			// and share its final serverTick (slot layout 0=input, 1=output).
			mac.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof BatteryBoxBlockEntity bb) {
			bb.serverTick(helper.getLevel(), p, st);
		} else if (be instanceof WindMillBlockEntity wd) {
			wd.serverTick(helper.getLevel(), p, st);
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
