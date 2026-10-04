package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed tick trace of the distillation_column's cycle (MOD-712, BE-3) — the reference {@link
 * MachineCycleTraceScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_cycle_trace_distillation_column*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class MachineCycleTraceDistillationColumn {

	private MachineCycleTraceDistillationColumn() {}

	static final List<String> LINES = List.of(
			"funded t0: c2=0 c3=200 c4=100 c5=0 c6=0 c7=5 c8=-1 c9=-1 c10=3 c11=0 c12=0 c13=0 done=0",
			"funded t1: spent=2 c10-2 c11+5",
			"funded t2-200 (199): spent=2 c11+5",
			"funded t201: spent=2 c2+1 c10-1",
			"funded t202-399 (198): spent=2 c2+1",
			"funded t400: spent=2 c2-199 c4-100 c5+70 c6+20 c7-6 c8+8 c9+10 c12+2 done+1",
			"funded t401: spent=0 c10+3",
			"funded t402: spent=0 c11-5",
			"funded t403: spent=0",
			"cut t0: c2=0 c3=200 c4=100 c5=0 c6=0 c7=5 c8=-1 c9=-1 c10=3 c11=0 c12=0 c13=0 done=0",
			"cut t1: spent=2 c10-2 c11+5",
			"cut t2-200 (199): spent=2 c11+5",
			"cut t201: spent=2 c2+1 c10-1",
			"cut t202-300 (99): spent=2 c2+1",
			"cut t301: CUT to 1 EU, spent=0 c10+2",
			"cut t302-341 (20 x 2): spent=0 c11-5 / spent=0",
			"output-full t0: c2=0 c3=200 c4=100 c5=1000 c6=1000 c7=5 c8=2 c9=2 c10=3 c11=0 c12=0 c13=0"
					+ " done=0",
			"output-full t1: spent=0 c10+1",
			"output-full t2-500 (499): spent=0");
}
