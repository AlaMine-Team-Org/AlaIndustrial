package dev.alaindustrial.client.compat.wthit;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.fluid.FluidPortHost;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;

/**
 * WTHIT client entry point (MOD-938), named in {@code waila_plugins.json}. Only WTHIT loads it; the API
 * is {@code compileOnly}.
 */
public final class AlaWthitClientPlugin implements IWailaClientPlugin {
	@Override
	public void register(IClientRegistrar registrar) {
		registrar.body(new TankLine(), FluidPortHost.class, TankLine.PRIORITY);
		Industrialization.LOGGER.info("[wthit] registered the tank line");
	}
}
