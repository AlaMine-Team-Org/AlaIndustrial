package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed tick trace of the incubator's cycle (MOD-712, BE-3) — the reference {@link
 * MachineCycleTraceScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_cycle_trace_incubator*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class MachineCycleTraceIncubator {

	private MachineCycleTraceIncubator() {}

	static final List<String> LINES = List.of(
			"funded t0: c2=0 c3=0 c4=0 c5=0 c6=1 c7=0 done=0",
			"funded t1: spent=8 c2+1 c3+300 c5+3",
			"funded t2-299 (298): spent=8 c2+1",
			"funded t300: spent=8 c2-299 c5-1 done+1",
			"cut t0: c2=0 c3=0 c4=0 c5=0 c6=1 c7=0 done=0",
			"cut t1: spent=8 c2+1 c3+300 c5+3",
			"cut t2-150 (149): spent=8 c2+1",
			"cut t151: CUT to 1 EU, spent=0",
			"cut t152-191 (40): spent=0",
			"output-full t0: c2=0 c3=0 c4=0 c5=0 c6=1 c7=0 done=0",
			"output-full t1: spent=0 c3+300 c5+3 c7+6",
			"output-full t2-400 (399): spent=0");
}
