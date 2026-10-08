package dev.alaindustrial.arch.fixture;

/** A functional interface of the mod's own (MOD-761): the supplier bridge deliberately does not cross it. */
@FunctionalInterface
public interface OwnSupplier {

	int get();
}
