package dev.alaindustrial.compat.wthit;

import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.data.FluidData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Server half of the WTHIT tank line (MOD-938): sends one of our tanks to the client in our own data
 * instead of WTHIT's {@link FluidData}, whose bar crashes on a tank 1–3 % full ({@link TankBarRatio}).
 *
 * <p><b>Why the order works.</b> WTHIT asks the {@link FluidData} providers in ascending priority and
 * stops at the first that adds or blocks. Its own fluid switch runs at 0 and blocks when the player
 * turned fluids off or blacklisted the block; the loader readers (Fabric Transfer API, NeoForge
 * capability) run at 2000. This provider runs in between and adds an EMPTY {@link FluidData}: the loader
 * reader is never asked and WTHIT draws no line of its own, while the data's presence on the client
 * tells {@code TankLine} that fluids are on for this block. Switched off, WTHIT blocks first, nothing
 * arrives, and {@code TankLine} draws nothing either — WTHIT's setting and blacklist hold with no key
 * of ours.
 *
 * <p>The tank is the one every loader lookup sees, {@link FluidPortHost#fluidPortForLookup} with no side
 * (MOD-126), so the line shows exactly what WTHIT showed before.
 */
public final class TankData implements IDataProvider<BlockEntity> {
	/** After WTHIT's fluid switch (0), before the loader fluid readers (2000). */
	static final int PRIORITY = 1000;
	/** The raw-data key: {@code fluid} (registry id), {@code amount} and {@code capacity} in mB. */
	public static final String KEY = "alaindustrial:tank";

	@Override
	public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
		if (!(accessor.getTarget() instanceof FluidPortHost host)) {
			return;
		}
		data.add(FluidData.TYPE, result -> result.add(FluidData.of(FluidData.Unit.MILLIBUCKETS)));
		FluidPort port = host.fluidPortForLookup(null);
		if (port == null || port.fluid().isEmpty() || port.getAmount() <= 0) {
			return;
		}
		CompoundTag tank = new CompoundTag();
		tank.putString("fluid", BuiltInRegistries.FLUID.getKey(port.fluid().fluid()).toString());
		tank.putLong("amount", port.getAmount());
		tank.putLong("capacity", port.getCapacity());
		data.raw().put(KEY, tank);
	}
}
