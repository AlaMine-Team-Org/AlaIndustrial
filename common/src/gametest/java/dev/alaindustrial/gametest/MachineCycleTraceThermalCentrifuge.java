package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed tick trace of the thermal_centrifuge's cycle (MOD-712, BE-3) — the reference {@link
 * MachineCycleTraceScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_cycle_trace_thermal_centrifuge*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class MachineCycleTraceThermalCentrifuge {

	private MachineCycleTraceThermalCentrifuge() {}

	static final List<String> LINES = List.of(
			"funded t0: c2=0 c3=200 c4=0 c5=5 done=0",
			"funded t1-400 (200 x 2): spent=4 c4+2 / spent=4 c4+3",
			"funded t401: spent=4 c2+1 c5-5",
			"funded t402-599 (198): spent=4 c2+1",
			"funded t600: spent=4 c2-199 done+1",
			"funded t601-603 (3): spent=4 c2+1",
			"cut t0: c2=0 c3=200 c4=0 c5=5 done=0",
			"cut t1-400 (200 x 2): spent=4 c4+2 / spent=4 c4+3",
			"cut t401: spent=4 c2+1 c5-5",
			"cut t402-500 (99): spent=4 c2+1",
			"cut t501: CUT to 1 EU, spent=1",
			"cut t502: spent=0",
			"cut t503: spent=0 c4-3",
			"cut t504: spent=0 c5+5",
			"cut t505-541 (37): spent=0",
			"output-full t0: c2=0 c3=200 c4=0 c5=5 done=0",
			"output-full t1: spent=4 c4+2 c5-1",
			"output-full t2-399 (199 x 2): spent=4 c4+3 / spent=4 c4+2",
			"output-full t400: spent=4 c4+3",
			"output-full t401-700 (300): spent=1");
}
