package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Deliberate violator for {@code ArchitectureRules.callStaticRateShortcutOutsideConstructor()}
 * (MOD-435): a machine-shaped class that calls the static {@code Config} rate shortcut from an
 * ordinary method — the exact defect of an upgrade panel silently ignored (MOD-392).
 *
 * <p>The constructor ALSO calls a shortcut, on purpose: seeding from {@code <init>} is the one
 * place the rule allows, and the negative control asserts that only the method call is reported.
 * A rule that flagged the constructor too would be red on every real machine.
 */
public final class StaticRateShortcutViolator {
	private final int maxProgress;

	StaticRateShortcutViolator() {
		this.maxProgress = Config.scaledDuration(200);
	}

	int drainPerTick() {
		return Config.machineEuPerTickEffective() + maxProgress;
	}
}
