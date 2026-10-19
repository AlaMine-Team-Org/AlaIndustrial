package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Golden master of a world characterization (MOD-715, ADR-032), written only by the
 * explicit command below; never by {@code regen.py}, a hook or a merge driver. A
 * behaviour-preserving change leaves every line as it is.
 *
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:ala_town_layout_snapshot_matches
 *   -Dalaindustrial.alaTownLayoutSnapshot.writeTo=&lt;this file&gt;"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing. Captured on one lane, it must pass on both.
 */
final class AlaTownLayoutSnapshot {

	private AlaTownLayoutSnapshot() {
	}

	static final List<String> LINES = List.of(
			"plains town_hall 0,0,0 COUNTERCLOCKWISE_90",
			"plains ground_16 11,0,0 COUNTERCLOCKWISE_90",
			"plains ground_08 -7,0,16 COUNTERCLOCKWISE_90",
			"plains ground_10 0,0,7 COUNTERCLOCKWISE_90",
			"plains ground_11 0,0,-13 COUNTERCLOCKWISE_90",
			"plains ground_14 9,0,19 COUNTERCLOCKWISE_90",
			"plains ground_18 19,0,-4 COUNTERCLOCKWISE_90",
			"plains ground_19 19,0,-25 COUNTERCLOCKWISE_90",
			"plains pump_station 19,0,-15 COUNTERCLOCKWISE_90",
			"plains ground_05 -13,0,-7 COUNTERCLOCKWISE_90",
			"plains ground_09 0,0,16 COUNTERCLOCKWISE_90",
			"plains capsule_house_1 -13,0,7 COUNTERCLOCKWISE_90",
			"plains ground_01 -19,0,19 COUNTERCLOCKWISE_90",
			"plains ground_12 0,0,-18 COUNTERCLOCKWISE_90",
			"plains ground_07 -13,0,-23 COUNTERCLOCKWISE_90",
			"plains capsule_house_2 -13,0,-1 COUNTERCLOCKWISE_90",
			"plains capsule_house_3 -13,0,-9 COUNTERCLOCKWISE_90",
			"plains ground_04 -13,0,1 COUNTERCLOCKWISE_90",
			"plains capsule_house_4 -13,0,-17 COUNTERCLOCKWISE_90",
			"plains ground_03 -13,0,16 COUNTERCLOCKWISE_90",
			"plains ground_06 -13,0,-15 COUNTERCLOCKWISE_90",
			"plains workshop 2,0,16 COUNTERCLOCKWISE_90",
			"plains ground_15 10,0,-18 COUNTERCLOCKWISE_90",
			"plains warehouse 2,0,-18 COUNTERCLOCKWISE_90",
			"plains ground_17 19,0,0 COUNTERCLOCKWISE_90",
			"plains ground_22 25,0,0 COUNTERCLOCKWISE_90",
			"plains ground_20 22,0,0 COUNTERCLOCKWISE_90",
			"plains ground_02 -19,0,16 COUNTERCLOCKWISE_90",
			"plains ground_13 2,0,-30 COUNTERCLOCKWISE_90",
			"plains guard_post 22,0,-1 COUNTERCLOCKWISE_90",
			"plains ground_21 24,0,-4 COUNTERCLOCKWISE_90",
			"plains lab=cave rot=COUNTERCLOCKWISE_90 depth=36 letter=4 hatch=8,0,-2",
			"desert town_hall 0,0,0 NONE",
			"desert ground_15 -3,0,11 NONE",
			"desert ground_04 -3,0,-6 NONE",
			"desert ground_10 -3,0,0 NONE",
			"desert ground_11 13,0,0 NONE",
			"desert ground_23 -3,0,20 NONE",
			"desert ground_01 -3,0,-14 NONE",
			"desert capsule_house_1 16,0,-6 NONE",
			"desert ground_19 40,0,12 NONE",
			"desert ground_06 30,0,-6 NONE",
			"desert workshop 15,0,12 NONE",
			"desert ground_17 24,0,12 NONE",
			"desert ground_18 38,0,12 NONE",
			"desert ground_07 38,0,-6 NONE",
			"desert ground_13 43,0,6 NONE",
			"desert ground_05 22,0,-6 NONE",
			"desert pump_station 43,0,1 NONE",
			"desert capsule_house_2 24,0,-6 NONE",
			"desert ground_02 43,0,-14 NONE",
			"desert warehouse 26,0,12 NONE",
			"desert ground_16 13,0,12 NONE",
			"desert capsule_house_3 32,0,-6 NONE",
			"desert ground_22 43,0,19 NONE",
			"desert ground_21 40,0,19 NONE",
			"desert ground_14 46,0,6 NONE",
			"desert capsule_house_4 40,0,13 NONE",
			"desert ground_09 54,0,-3 NONE",
			"desert ground_03 54,0,-14 NONE",
			"desert ground_12 53,0,1 NONE",
			"desert guard_post 54,0,-6 NONE",
			"desert ground_20 15,0,19 NONE",
			"desert ground_08 57,0,-6 NONE",
			"desert lab=archive rot=NONE depth=44 letter=2 hatch=2,0,8");
}
