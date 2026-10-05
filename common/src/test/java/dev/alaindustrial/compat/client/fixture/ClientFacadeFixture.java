package dev.alaindustrial.compat.client.fixture;

import dev.alaindustrial.arch.fixture.clientstandin.ClientTypeStandIn;

/**
 * Clean counterpart of {@code FacadeOutsideClientSubpackage} (MOD-703): the same use of a client type,
 * from inside {@code dev.alaindustrial.compat.client}, where the version facades with client types live.
 * The negative control requires it to be absent from the report.
 */
public final class ClientFacadeFixture {

	void use(ClientTypeStandIn clientType) {
		clientType.draw();
	}
}
