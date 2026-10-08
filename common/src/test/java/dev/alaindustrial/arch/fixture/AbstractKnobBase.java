package dev.alaindustrial.arch.fixture;

/**
 * An abstract class whose subclass reads a knob (MOD-761): reached only through the overrides in subclasses,
 * like {@link KnobShape} but through a class rather than an interface.
 */
public abstract class AbstractKnobBase {

	public abstract int amount();
}
