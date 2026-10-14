package dev.alaindustrial.arch.fixture;

import dev.alaindustrial.Config;

/**
 * A {@code useOn} that reads a knob, outside the predicting items (MOD-798): the prediction hooks are roots for the
 * electric mining tools only, so this one is not reported, and the control says so.
 */
public final class UnscopedUseOnItem {

	Object useOn(Object context) {
		return Config.euPerXp;
	}
}
