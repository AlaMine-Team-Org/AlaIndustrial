package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A smart wire segment (MOD-480). It holds no state of its own — the wire is a link, and everything
 * worth remembering lives in the monitor core at the end of the run.
 *
 * <p>It exists at all for one reason: something has to enrol the position in the network graph, and
 * enrolment has to survive a chunk load. The pipe does the same through {@code ensureRegistered}.
 */
public class SmartWireBlockEntity extends BlockEntity {

	private boolean registered;

	public SmartWireBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SMART_WIRE_BE.get(), pos, state);
	}

	/** Join the graph if this segment has not yet done so. Safe to call repeatedly. */
	public void ensureRegistered() {
		if (registered || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		registered = true;
		MonitorNetworkManager.register(serverLevel, worldPosition);
	}

	public void serverTick() {
		ensureRegistered();
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.unregister(serverLevel, worldPosition);
			registered = false;
		}
		super.setRemoved();
	}
}
