package dev.alaindustrial.compat.wthit;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.fluid.FluidPortHost;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;

/**
 * WTHIT common entry point (MOD-938), named in {@code waila_plugins.json}. Only WTHIT loads it, so the
 * mod never touches a WTHIT class when WTHIT is absent; the API is {@code compileOnly}.
 */
public final class AlaWthitCommonPlugin implements IWailaCommonPlugin {
	@Override
	public void register(ICommonRegistrar registrar) {
		registrar.blockData(new TankData(), FluidPortHost.class, TankData.PRIORITY);
		Industrialization.LOGGER.info("[wthit] registered the tank data provider");
	}
}
