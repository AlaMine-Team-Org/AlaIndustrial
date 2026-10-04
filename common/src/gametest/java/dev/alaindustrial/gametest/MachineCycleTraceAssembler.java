package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed tick trace of the assembler's cycle (MOD-712, BE-3) — the reference {@link
 * MachineCycleTraceScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_cycle_trace_assembler*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class MachineCycleTraceAssembler {

	private MachineCycleTraceAssembler() {}

	static final List<String> LINES = List.of(
			"funded t0: c2=0 c3=40 c4=-1 c5=1 done=0",
			"funded t1: spent=0 c4+1 c5-1",
			"funded t2-41 (40): spent=12 c2+1",
			"funded t42: spent=0 c2-40 c4-1 done+1",
			"funded t43: spent=0 c5+2",
			"funded t44-45 (2): spent=0",
			"cut t0: c2=0 c3=40 c4=-1 c5=1 done=0",
			"cut t1: spent=0 c4+1 c5-1",
			"cut t2-21 (20): spent=12 c2+1",
			"cut t22: CUT to 1 EU, spent=0 c5+4",
			"cut t23-62 (40): spent=0",
			"output-full t0: c2=0 c3=40 c4=-1 c5=1 done=0",
			"output-full t1: spent=0 c5+2",
			"output-full t2-120 (119): spent=0");
}
