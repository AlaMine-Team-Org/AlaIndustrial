package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;
import java.lang.reflect.Field;

/**
 * Clean counterpart of {@link KnobWriter} (MOD-710): it READS a knob, writes a static field that is not a
 * knob, and sets a knob through a reflected {@link Field} — which is how {@code ConfigOverrides} itself
 * writes. The negative control asserts this class is absent from the report, so the rule is proven to
 * fail on the write and not on every class that mentions {@code Config}.
 */
public final class KnobReaderOnly {

	static int lastRead;

	int read() {
		lastRead = Config.euPerXp;
		return lastRead;
	}

	void writeThroughTheField(Field knob) throws IllegalAccessException {
		knob.setInt(null, 5);
	}
}
