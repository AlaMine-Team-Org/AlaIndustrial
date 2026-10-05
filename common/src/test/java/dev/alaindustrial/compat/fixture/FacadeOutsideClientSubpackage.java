package dev.alaindustrial.compat.fixture;

import dev.alaindustrial.arch.fixture.clientstandin.ClientTypeStandIn;

/**
 * Deliberate violator for {@code ArchitectureRulesNegativeControl} (MOD-703): a class under
 * {@code dev.alaindustrial.compat} but OUTSIDE {@code compat.client} that uses a client type. The host
 * list of {@code clientTypesStayInsideClientPackages} must not admit it — only {@code compat.client} is a
 * client host, the rest of the facade package stays server-safe.
 */
public final class FacadeOutsideClientSubpackage {

	void use(ClientTypeStandIn clientType) {
		clientType.draw();
	}
}
