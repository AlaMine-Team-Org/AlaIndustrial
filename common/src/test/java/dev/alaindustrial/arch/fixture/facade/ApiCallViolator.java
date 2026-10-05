package dev.alaindustrial.arch.fixture.facade;

/**
 * Deliberate violator for {@code ArchitectureRules.callAFacadeOnlyMember} (MOD-703, batch 13): outside the facade
 * package it calls the overload listed by its first parameter, calls the member listed by name, and takes a
 * method reference to it — the three accesses the condition reads.
 */
public final class ApiCallViolator {

	void listedOverload(StandInApi api) {
		api.turn(new StandInApi.Quaternion());
	}

	void listedByName(StandInApi api) {
		api.grow();
	}

	Runnable reference(StandInApi api) {
		return api::grow;
	}
}
