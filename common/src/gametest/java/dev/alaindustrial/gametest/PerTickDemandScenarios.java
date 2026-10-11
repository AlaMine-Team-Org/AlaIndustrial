package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.MonitorCoreBlockEntity;
import dev.alaindustrial.core.energy.PerTickDemand;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Our consumers tell the network their per-tick need (MOD-789, review round 3, findings 1 and 5). A consumer with no
 * {@link PerTickDemand} has an unknown need; a machine that reports the shared machine rate while it draws its own
 * tariff is ranked wrong when the supply is short. Each block's entity is made for its default state and asked.
 */
public final class PerTickDemandScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PerTickDemandScenarios::consumersDeclareTheirDraw,
						"mod789_consumers_declare_their_draw"));

		private Roster() {}
	}

	private PerTickDemandScenarios() {}

	/** What a block's entity must report: a value read from it (its own tariff), or a fixed one. */
	private static Map<Supplier<Block>, ToLongFunction<BlockEntity>> expected() {
		Map<Supplier<Block>, ToLongFunction<BlockEntity>> out = new LinkedHashMap<>();
		out.put(ModContent.BLOCK_BREAKER, be -> Config.blockBreakerEuPerTick);
		out.put(ModContent.ELECTRIC_HEATER, be -> Config.electricHeaterEuPerTick);
		out.put(ModContent.MOB_REPELLER, be -> Config.mobRepellerEuPerTick);
		out.put(ModContent.THERMAL_CENTRIFUGE,
				be -> ((MachineBlockEntity) be).effectiveEuPerTick(Config.thermalCentrifugeEuPerTick));
		out.put(ModContent.RECYCLER, be -> ((MachineBlockEntity) be).effectiveEuPerTick(Config.recyclerEuPerTick));
		out.put(ModContent.WORKSTATION, be -> Config.workstationEuPerTick);
		out.put(ModContent.MONITOR_CORE, be -> 2L * Config.monitorCoreIdleEuPerTick); // empty: tops up a reserve
		out.put(ModContent.CRYSTAL_FARM_CONTROLLER, be -> 1L); // no seedbed yet: the floor
		out.put(ModContent.PUMP, be -> 0L); // pays per bucket: unknown on purpose
		out.put(ModContent.GARDEN_DRONE_STATION, be -> 0L); // pays per action: unknown on purpose
		return out;
	}

	/**
	 * @implements MOD-789-FW08 — the workstation, the monitor core and the crystal farm declare a per-tick need; the
	 *     machines with their own tariff report it, not the shared machine rate; bursty ones report unknown (0)
	 */
	public static void consumersDeclareTheirDraw(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		List<String> problems = new ArrayList<>();
		expected().forEach((block, want) -> {
			Block b = block.get();
			if (!(b instanceof EntityBlock entityBlock)) {
				problems.add(b + ": no block entity");
				return;
			}
			BlockEntity be = entityBlock.newBlockEntity(pos, b.defaultBlockState());
			if (!(be instanceof PerTickDemand demand)) {
				problems.add(b + ": does not tell its per-tick need (no PerTickDemand)");
				return;
			}
			long expect = want.applyAsLong(be);
			if (demand.perTickDemand() != expect) {
				problems.add(b + ": per-tick need " + demand.perTickDemand() + ", expected " + expect);
			}
		});
		// A core holding one debit interval's upkeep asks only for its upkeep: the reserve is kept, not grown.
		if (ModContent.MONITOR_CORE.get() instanceof EntityBlock core
				&& core.newBlockEntity(pos, ModContent.MONITOR_CORE.get().defaultBlockState())
						instanceof MonitorCoreBlockEntity full) {
			full.getEnergyStorage().setAmountUntracked(full.getEnergyStorage().getCapacity());
			if (full.perTickDemand() != Config.monitorCoreIdleEuPerTick) {
				problems.add("monitor core with its reserve: per-tick need " + full.perTickDemand() + ", expected "
						+ Config.monitorCoreIdleEuPerTick);
			}
		}
		if (!problems.isEmpty()) {
			helper.fail("MOD-789: " + problems.size() + " consumer(s) misreport their need: " + problems);
			return;
		}
		helper.succeed();
	}
}
