package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed fields of the shift workers' saved form (MOD-832) — the reference
 * {@link ShiftWorkerSaveFormatScenarios} compares against: every key with its tag type,
 * and the version. Written by that scenario, and only on the explicit command below.
 *
 * <p>Update command (absolute path; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:shift_worker_save_format*
 *   -Dalaindustrial.shiftWorkerSnapshot.writeTo=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/ShiftWorkerRecordSnapshot.java"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that altered the form.
 */
final class ShiftWorkerRecordSnapshot {

	private ShiftWorkerRecordSnapshot() {}

	static final List<String> LINES = List.of(
			"data_version=1",
			"hall_log[].building.game_time: long",
			"hall_log[].building.name: string",
			"hall_log[].building.template: string",
			"hall_log[].death.cause: string",
			"hall_log[].death.game_time: long",
			"hall_log[].death.killer: string",
			"hall_log[].name: string",
			"hall_log[].note.game_time: long",
			"hall_log[].note.key: string",
			"hall_log[].number: string",
			"hall_log[].renumbered.game_time: long",
			"hall_log[].renumbered.to: string",
			"hall_log[].settlement: int",
			"workers[].death.cause: string",
			"workers[].death.game_time: long",
			"workers[].death.killer: string",
			"workers[].entity: int[]",
			"workers[].former_numbers: list",
			"workers[].look: string",
			"workers[].name: string",
			"workers[].number: string",
			"workers[].profile.biography: list",
			"workers[].profile.charge: long",
			"workers[].profile.eu_drained: long",
			"workers[].profile.highest_level: int",
			"workers[].profile.investments: list",
			"workers[].profile.issued_game_time: long",
			"workers[].profile.marks[].args: list",
			"workers[].profile.marks[].game_time: long",
			"workers[].profile.marks[].kind: string",
			"workers[].settlement: int",
			"workers[].slim: byte",
			"workers[].status: string");
}
