package dev.alaindustrial.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.resources.Identifier;

/**
 * The floor under the common roster (MOD-717, ADR-038): this lane really replays {@link ScenarioRoster#ALL}.
 *
 * <p><b>The defect class.</b> Most world gametests reach a lane only through ONE line per loader: the
 * {@code ScenarioRosterFabric} {@code main} entrypoint in the Fabric gametest {@code fabric.mod.json}, and
 * the {@code ScenarioRosterNeoForge.init(modBus)} call in {@code NeoForgeGameTestBootstrap}. Drop either
 * and the lane quietly runs only its hand-wired tests: every roster test vanishes from the run instead of
 * failing, and with them the runtime oracles that replaced Python literals ({@code RegistrySnapshotScenarios},
 * {@code CreativeTabCoverageScenarios}). The text gate {@code gametest_lane_parity_check.py} reads those two
 * lines; this scenario asks the game itself.
 *
 * <p><b>Why it is wired by hand on both lanes.</b> A roster entry would vanish together with the roster it
 * is supposed to watch. The two lane entry points ({@code RosterReplayGameTest} on Fabric, a
 * {@code registerTest} line in {@code NeoForgeGameTests}) only name their lane.
 *
 * <p>For every entry, the id the lane gives it ({@link RosterEntry#testId(RosterEntry.Lane)}) must be bound
 * in both registries a vanilla {@code FunctionGameTestInstance} needs: the body in
 * {@link BuiltInRegistries#TEST_FUNCTION} and the instance in the server's {@link Registries#TEST_INSTANCE}
 * (the registry {@code GameTestServer} runs the lane from).
 */
public final class RosterReplayScenarios {

	/**
	 * Floor under the roster size (1244 entries on both game lines on 2026-10-03). An emptied
	 * {@code ScenarioRoster.PARTS} would make the presence check below vacuously green.
	 */
	static final int MIN_ROSTER_ENTRIES = 1000;

	/** How many missing ids the failure message names before it only counts. */
	private static final int NAMED_IN_MESSAGE = 10;

	private RosterReplayScenarios() {
	}

	/** Every roster entry is registered on {@code lane} — its body and its test instance. */
	public static void rosterIsReplayedOnThisLane(GameTestHelper helper, RosterEntry.Lane lane) {
		List<RosterEntry> roster = ScenarioRoster.ALL;
		if (roster.size() < MIN_ROSTER_ENTRIES) {
			helper.fail("MOD-717: ScenarioRoster.ALL holds " + roster.size() + " entries, expected at least "
					+ MIN_ROSTER_ENTRIES + " — ScenarioRoster.PARTS was emptied or truncated, so this floor"
					+ " would watch nothing");
			return;
		}
		Registry<Consumer<GameTestHelper>> functions = BuiltInRegistries.TEST_FUNCTION;
		Registry<GameTestInstance> instances =
				helper.getLevel().registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
		List<String> missing = new ArrayList<>();
		for (RosterEntry entry : roster) {
			Identifier id = entry.testId(lane);
			if (!functions.containsKey(id)) {
				missing.add("test_function " + id);
			}
			if (!instances.containsKey(id)) {
				missing.add("test_instance " + id);
			}
		}
		if (!missing.isEmpty()) {
			helper.fail("MOD-717: " + missing.size() + " roster registrations missing on the " + lane
					+ " lane (of " + roster.size() + " entries x 2 registries) — the lane does not replay"
					+ " ScenarioRoster.ALL, so its roster tests silently do not run. First: "
					+ missing.subList(0, Math.min(NAMED_IN_MESSAGE, missing.size())));
			return;
		}
		helper.succeed();
	}
}
