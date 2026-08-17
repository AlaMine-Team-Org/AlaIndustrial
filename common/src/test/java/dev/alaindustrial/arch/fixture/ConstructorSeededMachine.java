package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Clean counterpart of {@link StaticRateShortcutViolator} (MOD-435): the static shortcut is called
 * ONLY from the constructor, which is the allowed seeding case. The negative control asserts this
 * class is absent from the report — the regression guard for the {@code <init>} exemption.
 */
public final class ConstructorSeededMachine {
	private final int maxProgress;

	ConstructorSeededMachine() {
		this.maxProgress = Config.scaledDuration(200);
	}

	int progressCeiling() {
		return maxProgress;
	}
}
