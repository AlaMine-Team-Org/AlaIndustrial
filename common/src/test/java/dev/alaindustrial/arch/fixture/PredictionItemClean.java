package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.client.ServerBalance;

/** The clean twin of {@link PredictionItem} (MOD-798): the same hooks, the price from the server's numbers. */
public final class PredictionItemClean {

	float getDestroySpeed(Object stack) {
		return ServerBalance.machineEuPerTick();
	}

	Object useOn(Object context) {
		return ServerBalance.machineEuPerTick();
	}
}
