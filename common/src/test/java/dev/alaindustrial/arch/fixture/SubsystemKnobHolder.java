package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * A knob holder that is not {@code Config} (MOD-710, ADR-034): the shape every per-subsystem holder has.
 * The knob conditions of {@code ArchitectureRules} decide by the {@code @Knob} annotation, not by the
 * owner, and the negative control proves it on a read and a write of this field, and on a call of the
 * shortcut below (MOD-710 batch 4b: no real holder declares one any more). Never registered: the
 * real registry scans only the holders listed in {@code Config.REGISTRY}.
 */
public final class SubsystemKnobHolder {

	private SubsystemKnobHolder() {
	}

	@Knob(section = Section.TOOLS, doc = "Fixture knob of a holder other than Config.")
	public static int fixtureKnob = 1;

	/** A holder shortcut that reads a knob itself — the shape the client rule reports when called. */
	public static int doubledKnob() {
		return fixtureKnob * 2;
	}

	/** A pure holder formula: reads no knob, so a client may feed it the server's numbers. */
	public static int doubled(int value) {
		return value * 2;
	}
}
