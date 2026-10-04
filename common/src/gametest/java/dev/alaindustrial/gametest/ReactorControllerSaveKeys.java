package dev.alaindustrial.gametest;

import java.util.List;
import java.util.Map;

/**
 * Reviewed top-level keys of the reactor controller's save, with their tag types, per rig
 * (MOD-713) — the reference {@link ReactorControllerPersistenceScenarios} compares against.
 * Written by that scenario, and only on the explicit command below; never by
 * {@code regen.py}, a hook or a merge driver (ADR-032). The same file on both Minecraft
 * lines: a difference between them is a finding.
 *
 * <p>Update command (absolute path; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:reactor_persistence_*
 *   -Dalaindustrial.reactorSaveKeys.writeTo=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/ReactorControllerSaveKeys.java"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together with
 * the change that altered the save.
 */
final class ReactorControllerSaveKeys {

	private ReactorControllerSaveKeys() {}

	static final Map<String, List<String>> BY_RIG = Map.of(
			"bare_pile", List.of(
					"AlaDataVersion:INT",
					"BlastBelowTicks:INT",
					"BlastCountdown:INT",
					"BlastCountdownTotal:INT",
					"BoxMaxX:INT",
					"BoxMaxY:INT",
					"BoxMaxZ:INT",
					"BoxMinX:INT",
					"BoxMinY:INT",
					"BoxMinZ:INT",
					"Depth:INT",
					"Energy:LONG",
					"Heat:LONG",
					"Instability:LONG",
					"Items:LIST",
					"Log:LIST",
					"LogBare:BYTE",
					"LogMeltEpisode:INT",
					"LogMelting:BYTE",
					"LogNextSeq:INT",
					"LogReaders:LIST",
					"LogRunning:BYTE",
					"MaxProgress:INT",
					"OverheatWarned:BYTE",
					"OwnerName:STRING",
					"Progress:INT",
					"StatsActiveTicks:LONG",
					"StatsEnergyConsumed:LONG",
					"StatsEnergyGenerated:LONG",
					"StatsEnergyIn:LONG",
					"StatsEnergyOut:LONG",
					"StatsItemsProcessed:LONG"),
			"countdown", List.of(
					"AlaDataVersion:INT",
					"BlastBelowTicks:INT",
					"BlastCountdown:INT",
					"BlastCountdownTotal:INT",
					"BoxMaxX:INT",
					"BoxMaxY:INT",
					"BoxMaxZ:INT",
					"BoxMinX:INT",
					"BoxMinY:INT",
					"BoxMinZ:INT",
					"Depth:INT",
					"Energy:LONG",
					"Heat:LONG",
					"Instability:LONG",
					"Items:LIST",
					"Log:LIST",
					"LogBare:BYTE",
					"LogMeltEpisode:INT",
					"LogMelting:BYTE",
					"LogNextSeq:INT",
					"LogReaders:LIST",
					"LogRunning:BYTE",
					"MaxProgress:INT",
					"OverheatWarned:BYTE",
					"OwnerName:STRING",
					"Progress:INT",
					"StatsActiveTicks:LONG",
					"StatsEnergyConsumed:LONG",
					"StatsEnergyGenerated:LONG",
					"StatsEnergyIn:LONG",
					"StatsEnergyOut:LONG",
					"StatsItemsProcessed:LONG"),
			"running_room", List.of(
					"AlaDataVersion:INT",
					"BlastBelowTicks:INT",
					"BlastCountdown:INT",
					"BlastCountdownTotal:INT",
					"BoxMaxX:INT",
					"BoxMaxY:INT",
					"BoxMaxZ:INT",
					"BoxMinX:INT",
					"BoxMinY:INT",
					"BoxMinZ:INT",
					"Depth:INT",
					"Energy:LONG",
					"Heat:LONG",
					"Instability:LONG",
					"Items:LIST",
					"Log:LIST",
					"LogBare:BYTE",
					"LogMeltEpisode:INT",
					"LogMelting:BYTE",
					"LogNextSeq:INT",
					"LogReaders:LIST",
					"LogRunning:BYTE",
					"MaxProgress:INT",
					"OverheatWarned:BYTE",
					"OwnerName:STRING",
					"Progress:INT",
					"StatsActiveTicks:LONG",
					"StatsEnergyConsumed:LONG",
					"StatsEnergyGenerated:LONG",
					"StatsEnergyIn:LONG",
					"StatsEnergyOut:LONG",
					"StatsItemsProcessed:LONG"));
}
