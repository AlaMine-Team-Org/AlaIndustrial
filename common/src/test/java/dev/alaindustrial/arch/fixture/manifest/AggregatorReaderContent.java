package dev.alaindustrial.arch.fixture.manifest;

/** Violator (MOD-711): a domain that reads the aggregator's list back from its static initialiser. */
public final class AggregatorReaderContent {
	private AggregatorReaderContent() {
	}

	static final int SIZE = Aggregator.LIST.size();
}
