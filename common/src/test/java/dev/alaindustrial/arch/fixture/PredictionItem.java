package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Deliberate violator for {@code ClientBalanceReachRules} (MOD-798): an electric-tool-shaped class whose
 * prediction hooks read a balance knob, one two calls away and one directly. No Minecraft parameters because this
 * lane has no Minecraft jar; the rule matches the hooks by name and the class by the predicate the control hands in.
 */
public final class PredictionItem {

	float getDestroySpeed(Object stack) {
		return KnobHelper.twice();
	}

	Object useOn(Object context) {
		return Config.euPerXp;
	}
}
