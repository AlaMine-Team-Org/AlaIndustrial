package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.environment.GeneratorConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;

/**
 * Self-tests of {@link ConfigOverrides} (MOD-710, batch 3a), declared in {@link Roster} and run by both
 * lanes from the scenario roster (MOD-717); the Fabric ids are those of the former
 * {@code ConfigOverridesGameTest} wrappers.
 *
 * <p>Each scenario owns a knob no other gametest reads or writes, so even the one that holds its
 * override across ticks cannot disturb a neighbour.
 *
 * <p><b>Why the end of a test is delivered by hand.</b> A scenario cannot observe anything after its own
 * end, and a scenario that really timed out would make the lane red by itself. So each end-of-test case
 * proves the two halves separately: (1) the listener {@code forTest} created is attached to THIS test's
 * {@code GameTestInfo} — the object whose {@code tick} calls {@code testPassed}/{@code testFailed} on
 * every attached listener once the test is done (a timeout is {@code testFailed}, one tick after the
 * {@code GameTestTimeoutException}; vanilla 26.3 behaviour, read with {@code javap -c}); (2) that very
 * listener, called the way the framework calls it, puts the knob back to its first-capture value and
 * releases the key. When the test later really ends, the framework's own call finds nothing left to do.
 */
public final class ConfigOverridesScenarios {

	/** Held across ticks by {@link #restoredWhenTheOwningTestEnds}: read by no gametest. */
	private static final String HELD_KNOB = "mobRepellerEvolveKillsHv";
	/** Captured twice and released by a simulated timeout: read by no gametest. */
	private static final String TIMEOUT_KNOB = "windGaugePeakKmh";
	/** Fought over by two owners: read by no gametest. */
	private static final String CONTESTED_KNOB = "skillWideWateringCost";
	/** How long {@link #restoredWhenTheOwningTestEnds} keeps its override before the test ends. */
	private static final int HOLD_TICKS = 5;

	/**
	 * This class's roster entries (nested so reading them does not initialise the class). The test that
	 * holds an override across ticks runs in its own environment, {@code alaindustrial:config_overrides}:
	 * the game batches tests by environment, so it never shares ticks with an ordinary scenario. Fabric
	 * reads that environment from {@code data/alaindustrial/test_environment/config_overrides.json},
	 * NeoForge registers it empty.
	 */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ConfigOverridesScenarios::secondOwnerIsRefusedNamingTheFirst,
								"config_overrides_second_owner_refused")
						.fabricId("ConfigOverridesGameTest", "mod710_secondOwnerIsRefusedNamingTheFirst")
						.ticks(40),
				RosterEntry.of(ConfigOverridesScenarios::restoredWhenTheOwningTestEnds,
								"config_overrides_restored_when_test_ends")
						.fabricId("ConfigOverridesGameTest", "mod710_restoredWhenTheOwningTestEnds")
						.ticks(40)
						.environment(Industrialization.id("config_overrides")),
				RosterEntry.of(ConfigOverridesScenarios::restoredAfterATimeout,
								"config_overrides_restored_after_timeout")
						.fabricId("ConfigOverridesGameTest", "mod710_restoredAfterATimeout")
						.ticks(40));

		private Roster() {}
	}

	private ConfigOverridesScenarios() {
	}

	/**
	 * A key held by one owner is refused to a second one, and the refusal names the first owner — in
	 * both directions (test handle first, then a {@code sync()} handle first); the refused write changes
	 * nothing, and closing the owner restores the value. Unknown keys and values of the wrong type are
	 * refused too.
	 */
	public static void secondOwnerIsRefusedNamingTheFirst(GameTestHelper helper) {
		double before = Config.skillWideWateringCost;
		ConfigOverrides test = ConfigOverrides.forTest(helper).set(CONTESTED_KNOB, before + 1.0);
		if (ConfigOverrides.forTest(helper) != test) {
			helper.fail("forTest called twice in one test must hand out the same handle");
		}
		try (ConfigOverrides rival = ConfigOverrides.sync()) {
			String refusal = refusal(() -> rival.set(CONTESTED_KNOB, before + 2.0));
			if (refusal == null || !refusal.contains(test.owner())) {
				helper.fail("a second owner of " + CONTESTED_KNOB + " must be refused naming '" + test.owner()
						+ "'; got: " + refusal);
			}
			if (Config.skillWideWateringCost != before + 1.0) {
				helper.fail("the refused capture still wrote " + CONTESTED_KNOB + " = " + Config.skillWideWateringCost);
			}
		}
		test.close();
		if (Config.skillWideWateringCost != before || ConfigOverrides.ownerOf(CONTESTED_KNOB).isPresent()) {
			helper.fail("closing the owner must restore " + before + " and release the key; value "
					+ Config.skillWideWateringCost + ", owner " + ConfigOverrides.ownerOf(CONTESTED_KNOB));
		}

		try (ConfigOverrides first = ConfigOverrides.sync().set(CONTESTED_KNOB, before + 3.0)) {
			ConfigOverrides late = ConfigOverrides.forTest(helper);
			String refusal = refusal(() -> late.set(CONTESTED_KNOB, before + 4.0));
			if (refusal == null || !refusal.contains(first.owner())) {
				helper.fail("the test handle must be refused naming '" + first.owner() + "'; got: " + refusal);
			}
			if (refusal(() -> late.set("noSuchKnob", 1)) == null) {
				helper.fail("an unknown knob name must be refused");
			}
			if (refusal(() -> late.set(TIMEOUT_KNOB, 1.0)) == null) {
				helper.fail("a double for the float knob " + TIMEOUT_KNOB + " must be refused");
			}
		}
		if (Config.skillWideWateringCost != before) {
			helper.fail("the sync owner must restore " + before + "; found " + Config.skillWideWateringCost);
		}
		helper.succeed();
	}

	/**
	 * A {@code forTest} override lasts across ticks, still owned by the test, and the listener attached
	 * to the test's {@code GameTestInfo} restores it when the test passes. Runs in the
	 * {@code alaindustrial:config_overrides} environment: it is the separate-batch spike's probe (MOD-710
	 * research note).
	 */
	public static void restoredWhenTheOwningTestEnds(GameTestHelper helper) {
		int before = Config.mobRepellerEvolveKillsHv;
		int held = before + 11;
		ConfigOverrides test = ConfigOverrides.forTest(helper).set(HELD_KNOB, held);
		GameTestInfo info = attachedTo(helper, test);
		helper.runAfterDelay(HOLD_TICKS, () -> {
			if (Config.mobRepellerEvolveKillsHv != held) {
				helper.fail(HELD_KNOB + " did not keep its override for " + HOLD_TICKS + " ticks: "
						+ Config.mobRepellerEvolveKillsHv);
			}
			if (!ConfigOverrides.ownerOf(HELD_KNOB).equals(Optional.of(test.owner()))) {
				helper.fail(HELD_KNOB + " must still belong to " + test.owner() + "; owner "
						+ ConfigOverrides.ownerOf(HELD_KNOB));
			}
			test.endListener().testPassed(info, null);
			if (Config.mobRepellerEvolveKillsHv != before || ConfigOverrides.ownerOf(HELD_KNOB).isPresent()) {
				helper.fail("after the test passed " + HELD_KNOB + " must be back at " + before + " and free; value "
						+ Config.mobRepellerEvolveKillsHv + ", owner " + ConfigOverrides.ownerOf(HELD_KNOB));
			}
			if (refusal(() -> test.set(HELD_KNOB, held)) == null) {
				helper.fail("the handle of an ended test must refuse further writes");
			}
			helper.succeed();
		});
	}

	/**
	 * A test that times out: its override — set twice by the same owner — goes back to the value of the
	 * key's FIRST capture when the timeout reaches the listener as {@code testFailed}, and the key is
	 * free for the next owner.
	 */
	public static void restoredAfterATimeout(GameTestHelper helper) {
		float before = GeneratorConfig.windGaugePeakKmh;
		ConfigOverrides test = ConfigOverrides.forTest(helper).set(TIMEOUT_KNOB, before + 10.0f);
		test.set(TIMEOUT_KNOB, before + 20.0f);
		GameTestInfo info = attachedTo(helper, test);

		test.endListener().testFailed(info, null);
		if (Float.compare(GeneratorConfig.windGaugePeakKmh, before) != 0) {
			helper.fail("after a timeout " + TIMEOUT_KNOB + " must be back at its first-capture value " + before
					+ ", not " + GeneratorConfig.windGaugePeakKmh);
		}
		if (ConfigOverrides.ownerOf(TIMEOUT_KNOB).isPresent()) {
			helper.fail("a timed-out test must release " + TIMEOUT_KNOB + "; still held by "
					+ ConfigOverrides.ownerOf(TIMEOUT_KNOB));
		}
		try (ConfigOverrides next = ConfigOverrides.sync()) {
			String refusal = refusal(() -> next.set(TIMEOUT_KNOB, before + 1.0f));
			if (refusal != null) {
				helper.fail("the next owner must get " + TIMEOUT_KNOB + " after the timeout; refused: " + refusal);
			}
		}
		if (Float.compare(GeneratorConfig.windGaugePeakKmh, before) != 0) {
			helper.fail(TIMEOUT_KNOB + " must be " + before + " after the next owner closed; found "
					+ GeneratorConfig.windGaugePeakKmh);
		}
		helper.succeed();
	}

	/** The test's {@code GameTestInfo}, after checking {@code handle}'s end listener is attached to it. */
	private static GameTestInfo attachedTo(GameTestHelper helper, ConfigOverrides handle) {
		GameTestInfo info = ConfigOverrides.testInfo(helper);
		if (info.getListeners().noneMatch(listener -> listener == handle.endListener())) {
			helper.fail("forTest did not attach its end listener to the running test " + info.id()
					+ ", so nothing would restore the knob when the test ends");
		}
		return info;
	}

	/** The message {@code action} was refused with, or {@code null} when it went through. */
	private static String refusal(Runnable action) {
		try {
			action.run();
			return null;
		} catch (IllegalStateException | IllegalArgumentException refused) {
			return refused.getMessage();
		}
	}
}
