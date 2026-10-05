package dev.alaindustrial.arch.fixture;

/**
 * Deliberate violator for {@code ArchitectureRules.callForTestOutside} (MOD-710): holds a knob across
 * ticks, and its {@code hold} is not in the allow-list the negative control passes. One call, one
 * method reference (both are accesses the condition reads).
 */
public final class ForTestCallerOffList {

	void hold() {
		ForTestHolder.forTest(null);
	}

	java.util.function.Function<Object, ForTestHolder> reference() {
		return ForTestHolder::forTest;
	}
}
