package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import dev.alaindustrial.client.ServerBalance;

/**
 * Clean counterpart of {@link TooltipKnobReader} (MOD-695): the tooltip reads {@code ServerBalance}, and
 * the server-side method of the same class keeps reading {@code Config} — which the tooltip rule must not
 * report, since it is scoped to the tooltip body.
 */
public final class TooltipBalanceReader {

	int appendHoverText() {
		return ServerBalance.euPerXp();
	}

	int serverSideLogic() {
		return Config.euPerXp;
	}
}
