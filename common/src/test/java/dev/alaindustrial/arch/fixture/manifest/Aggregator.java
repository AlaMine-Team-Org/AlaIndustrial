package dev.alaindustrial.arch.fixture.manifest;

import java.util.List;

/**
 * Stand-in for {@code ContentManifest} in the negative control of {@code contentDomainsInitialiseOnTheirOwn}
 * (MOD-711): a static list the domains must not read back, and a nested record they may build.
 */
public final class Aggregator {
	private Aggregator() {
	}

	public static final List<String> LIST = List.of("a", "b");

	/** A nested record: building one does not initialise {@link Aggregator}. */
	public record Entry(String id) {
	}
}
