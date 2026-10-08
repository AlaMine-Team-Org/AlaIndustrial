package dev.alaindustrial.arch.fixture.clientstandin;

import dev.alaindustrial.arch.fixture.OwnSuppliedGrade;
import dev.alaindustrial.arch.fixture.SubsystemKnobHolder;
import dev.alaindustrial.client.ServerBalance;

/**
 * Clean twin of {@link IndirectKnobScreen} (MOD-761): the number comes from {@code ServerBalance}, which reads
 * {@code Config} itself — the walk must stop there — and is handed to a pure holder formula. The second method
 * goes through the mod's own functional interface, which the rule does not bridge.
 */
public final class BalanceScreen {

	int shown() {
		return SubsystemKnobHolder.doubled(ServerBalance.machineEuPerTick());
	}

	int ownSupplied() {
		return OwnSuppliedGrade.LOW.amount();
	}
}
