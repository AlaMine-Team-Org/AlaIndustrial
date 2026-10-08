package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/** The subclass behind {@link AbstractKnobBase} that reads {@code Config} (MOD-761). */
public final class KnobBaseImpl extends AbstractKnobBase {

	@Override
	public int amount() {
		return Config.euPerXp;
	}
}
