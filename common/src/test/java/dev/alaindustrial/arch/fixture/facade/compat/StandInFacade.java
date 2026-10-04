package dev.alaindustrial.arch.fixture.facade.compat;

import dev.alaindustrial.arch.fixture.facade.StandInApi;

/**
 * Plays a version facade (MOD-703, batch 13): it lives in the package the negative control treats as the facade
 * package, so its direct calls of the stand-in members are the allowed ones. Its own methods are named apart
 * from the members they wrap, as the real facades' are ({@code Bonemeal.perform} wraps {@code performBonemeal}):
 * a member listed by name alone matches any owner.
 */
public final class StandInFacade {

	private StandInFacade() {
	}

	public static void turnBy(StandInApi api, StandInApi.Quaternion rotation) {
		api.turn(rotation);
	}

	public static void growOnce(StandInApi api) {
		api.grow();
	}
}
