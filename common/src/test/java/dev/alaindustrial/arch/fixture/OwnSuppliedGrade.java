package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Twin of {@link SuppliedGrade} through the mod's own functional interface (MOD-761). Not followed on purpose:
 * bridging the mod's interfaces reached the whole server tick from a pipe's tint. The negative control pins
 * that this stays unreported, so widening the bridge is a decision, not a drift.
 */
public enum OwnSuppliedGrade {
	LOW(() -> Config.euPerXp);

	private final OwnSupplier amount;

	OwnSuppliedGrade(OwnSupplier amount) {
		this.amount = amount;
	}

	public int amount() {
		return amount.get();
	}
}
