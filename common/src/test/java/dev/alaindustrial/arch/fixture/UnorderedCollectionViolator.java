package dev.alaindustrial.arch.fixture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deliberate violator for {@code ArchitectureRules.useUnorderedCollections()} (MOD-435).
 *
 * <p>One method per shape the condition claims to see, so a report that misses one of them names
 * exactly which accessor went blind: a constructor CALL, a constructor REFERENCE (the shape MOD-313
 * found the rule blind to), a populated {@code Set.of}, and a {@code Collectors.toSet}. Never loaded,
 * never run — the negative control only imports its bytecode.
 */
public final class UnorderedCollectionViolator {
	private UnorderedCollectionViolator() {
	}

	static Set<Integer> constructorCall() {
		return new HashSet<>();
	}

	static Supplier<Map<Integer, Integer>> constructorReference() {
		return HashMap::new;
	}

	static Set<Integer> populatedFactory() {
		return Set.of(1);
	}

	static Set<Integer> collectorToSet() {
		return Stream.of(1, 2).collect(Collectors.toSet());
	}

	static List<Integer> unrelated() {
		return List.of(1);
	}
}
