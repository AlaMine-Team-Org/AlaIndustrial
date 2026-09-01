package dev.alaindustrial.gametest;

import dev.alaindustrial.core.energy.NetworkManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 performance smoke for the energy network (MOD-323: bodies now loader-neutral, see
 * {@link EnergyNetworkPerfScenarios#benchLargeNetworkSmoke}).
 *
 * <p><b>What these guard:</b> a large connected cable field unions into one delivering network,
 * and tearing it down (each cut re-runs the component BFS) stays within a generous budget — the
 * teardown churn is O(N²) in the worst case and this is the guard against it blowing up.
 *
 * <p>This class guards teardown churn ONLY. The per-tick transport cost is guarded by
 * {@code EnergyNetworkPerfScenarios.perf01FiftyCableLineTickCost} (MOD-404, common — runs on both
 * loaders). The wall-clock {@code benchTickTiming} that used to live here timed 2000
 * {@link NetworkManager#tickAll} passes against an absolute 1500 ms budget — i.e. every other
 * suite's rig in the shared level too — and went red on CI on docs-only commits three times
 * (MOD-431); the redundant, Fabric-only one was removed rather than re-tuned.
 */
public class NetworkBenchGameTest {

	/**
	 * Large-network smoke + teardown bench (covers R-NRG-08/R-NRG-09, but with no dedicated
	 * case ID — this is an infrastructure check). A 72-cable field unions into a single
	 * network that delivers EU from the generator to the macerator; tearing the whole field down
	 * (which re-runs the component BFS on each cut) completes well within a generous budget and
	 * leaves this field with no network.
	 */
	@GameTest
	public void benchLargeNetworkSmoke(GameTestHelper helper) {
		EnergyNetworkPerfScenarios.benchLargeNetworkSmoke(helper);
	}
}
