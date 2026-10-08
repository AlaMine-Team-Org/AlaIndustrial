package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.client.ServerBalance;

/**
 * An item-shaped class outside the client packages whose bar and tooltip image are drawn on the client
 * (MOD-761). The stand-in has no Minecraft parameters because this lane has no Minecraft jar; the rule
 * matches the hooks by name.
 */
public final class DisplayHookItem {

	/** Public so a client stand-in can call it: the walk must stop here, at another root. */
	public int getBarWidth() {
		return KnobHelper.twice();
	}

	Object getTooltipImage() {
		return SuppliedGrade.LOW.amount();
	}

	/** The fix: the number comes from ServerBalance. */
	int getBarColor() {
		return ServerBalance.machineEuPerTick();
	}
}
