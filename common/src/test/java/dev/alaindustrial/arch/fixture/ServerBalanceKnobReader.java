package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.client.ServerBalance;

/**
 * Clean counterpart of {@link ConfigKnobReader} (MOD-695): reads the knob through
 * {@code ServerBalance} and applies a PURE holder formula to it. The negative control asserts this class
 * is absent from the report — the guard for the "a holder method that reads no knob stays allowed" half
 * of the rule.
 */
public final class ServerBalanceKnobReader {

	int drainShown() {
		return SubsystemKnobHolder.doubled(ServerBalance.machineEuPerTick());
	}
}
