package dev.alaindustrial.arch.fixture.manifest;

import java.util.List;

/**
 * Clean counterpart (MOD-711): builds the aggregator's nested record and reads only its own constants — the
 * shape of a real domain file. The negative control asserts it is NOT reported.
 */
public final class CleanContent {
	private CleanContent() {
	}

	static final Aggregator.Entry ENTRY = new Aggregator.Entry("clean");

	static final List<Aggregator.Entry> ALL = List.of(ENTRY);
}
