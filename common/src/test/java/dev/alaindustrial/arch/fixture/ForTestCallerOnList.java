package dev.alaindustrial.arch.fixture;

/**
 * Clean counterpart of {@link ForTestCallerOffList} (MOD-710): the same call, from a method the
 * allow-list the negative control passes names. The control asserts it is absent from the report.
 */
public final class ForTestCallerOnList {

	void hold() {
		ForTestHolder.forTest(null);
	}
}
