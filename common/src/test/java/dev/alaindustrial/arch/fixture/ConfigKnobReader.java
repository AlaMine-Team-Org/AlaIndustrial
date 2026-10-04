package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * Deliberate violator for {@code ArchitectureRules.readBalanceKnobsFromConfig()} (MOD-695): a
 * client-shaped class that reads the balance from {@code Config} instead of from
 * {@code ServerBalance} — the defect that made a dedicated server's players see their own file's
 * numbers. One method per shape the condition claims to see: a field read (of {@code Config} and of a
 * per-subsystem holder, MOD-710), a call to a holder shortcut that reads a knob itself, and a method
 * reference to one (on the fixture holder: the {@code Config} shortcuts are gone since MOD-710 batch 4b).
 */
public final class ConfigKnobReader {

	int fieldRead() {
		return Config.euPerXp;
	}

	int holderFieldRead() {
		return SubsystemKnobHolder.fixtureKnob;
	}

	int shortcutCall() {
		return SubsystemKnobHolder.doubledKnob();
	}

	IntSupplier shortcutReference() {
		return SubsystemKnobHolder::doubledKnob;
	}
}
