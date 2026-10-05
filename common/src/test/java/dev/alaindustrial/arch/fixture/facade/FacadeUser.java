package dev.alaindustrial.arch.fixture.facade;

import dev.alaindustrial.arch.fixture.facade.compat.StandInFacade;

/**
 * Clean twin of {@link ApiCallViolator} (MOD-703, batch 13): the same work through the facade, plus the overload
 * both lines share called directly — none of it may be reported.
 */
public final class FacadeUser {

	void throughTheFacade(StandInApi api) {
		StandInFacade.turnBy(api, new StandInApi.Quaternion());
		StandInFacade.growOnce(api);
	}

	void sharedOverload(StandInApi api) {
		api.turn(new StandInApi.Matrix());
	}
}
