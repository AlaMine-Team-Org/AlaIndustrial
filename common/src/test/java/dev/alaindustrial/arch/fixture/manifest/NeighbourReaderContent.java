package dev.alaindustrial.arch.fixture.manifest;

/** Violator (MOD-711): a domain that names another domain's entry, starting its initialiser inside its own. */
public final class NeighbourReaderContent {
	private NeighbourReaderContent() {
	}

	static final int BORROWED = OtherContent.IDS.size();
}
