package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/** The implementation behind {@link KnobShape} that reads {@code Config} (MOD-761). */
public final class KnobShapeImpl implements KnobShape {

	@Override
	public int amount() {
		return Config.euPerXp;
	}
}
