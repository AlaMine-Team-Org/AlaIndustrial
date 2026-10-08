package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * A grade enum whose number is a JDK supplier lambda over {@code Config} (MOD-761) — the shape of the cable,
 * tank, pipe and magnet grades. The read lives in the lambda, which ArchUnit attributes to the static
 * initializer: only the supplier bridge of {@code ClientBalanceReachRules} gets from {@link #amount()} to it.
 */
public enum SuppliedGrade {
	LOW(() -> Config.euPerXp);

	private final IntSupplier amount;

	SuppliedGrade(IntSupplier amount) {
		this.amount = amount;
	}

	public int amount() {
		return amount.getAsInt();
	}
}
