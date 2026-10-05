package dev.alaindustrial.config;

import java.lang.reflect.Field;

/**
 * One registered knob as code outside the mechanism may see it: its json key, its section, its lower
 * bound and the declared field behind it (MOD-710). The L1 suites read the registry through this
 * record instead of reaching into private fields by name, so moving the mechanism is a compile error
 * rather than a red run.
 */
public record KnobEntry(String key, Section section, double min, Field field) {
}
