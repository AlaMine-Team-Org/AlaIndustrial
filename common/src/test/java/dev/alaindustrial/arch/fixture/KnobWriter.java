package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * Deliberate violator for {@code ArchitectureRules.writeAKnobField()} (MOD-710): a gametest-shaped class
 * that changes a balance knob by hand instead of through {@code ConfigOverrides}. One method per shape the
 * condition claims to see: a plain assignment (to {@code Config} and to a per-subsystem holder), a compound
 * assignment (a GET and a SET) and an assignment
 * inside a lambda, which javac lifts into a synthetic method of this class.
 */
public final class KnobWriter {

	void assign() {
		Config.euPerXp = 3;
	}

	void assignInHolder() {
		SubsystemKnobHolder.fixtureKnob = 4;
	}

	void compound() {
		Config.euPerXp += 1;
	}

	Runnable inLambda() {
		return () -> Config.teleporterMaxPoints = 2;
	}
}
