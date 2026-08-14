package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 gametest for the Network Analyzer's Traverse mode (MOD-047).
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link NetworkAnalyzerScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation, so
 * the SAME traversal scenarios run on BOTH loaders — NeoForge registers them in
 * {@code NeoForgeGameTests}.
 */
public class NetworkAnalyzerGameTest {

	@GameTest
	public void mod047_traverseBridgesBatteryBox(GameTestHelper helper) {
		NetworkAnalyzerScenarios.mod047_traverseBridgesBatteryBox(helper);
	}

	@GameTest
	public void mod047_stopAtStorageStaysInSegment(GameTestHelper helper) {
		NetworkAnalyzerScenarios.mod047_stopAtStorageStaysInSegment(helper);
	}

	@GameTest
	public void mod047_traverseCapFlagsLimit(GameTestHelper helper) {
		NetworkAnalyzerScenarios.mod047_traverseCapFlagsLimit(helper);
	}

	@GameTest
	public void mod313_traverseCrossesSinksInGeometricOrder(GameTestHelper helper) {
		NetworkAnalyzerScenarios.mod313_traverseCrossesSinksInGeometricOrder(helper);
	}
}
