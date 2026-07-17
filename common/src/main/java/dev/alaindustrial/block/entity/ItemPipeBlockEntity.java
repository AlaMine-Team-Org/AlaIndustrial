package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.ItemNetworkManager;
import dev.alaindustrial.core.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Persistent per-face configuration and lifecycle hook for a passive item-pipe segment. */
public final class ItemPipeBlockEntity extends MachineBlockEntity {
	private int packedFaceModes;
	private boolean registered;

	public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ITEM_PIPE_BE.get(), pos, state, EnergyTier.LV, 0, 0, 0, 0);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		return 0;
	}

	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			ItemNetworkManager.register(this);
			registered = true;
		}
	}

	public PipeFaceMode faceMode(Direction direction) {
		return PipeFaceMode.values()[(packedFaceModes >>> (direction.ordinal() * 2)) & 3];
	}

	/** Advance this face one step along the wrench ladder (MOD-108: neutral → extract → insert → disabled). */
	public void cycleFaceMode(Direction direction) {
		setFaceMode(direction, faceMode(direction).nextInCycle());
	}

	public void setFaceMode(Direction direction, PipeFaceMode mode) {
		int shift = direction.ordinal() * 2;
		int next = (packedFaceModes & ~(3 << shift)) | (mode.ordinal() << shift);
		if (next == packedFaceModes) return;
		packedFaceModes = next;
		markDirtyAndSync();
		// Face modes drive a client-only terminal renderer. Persisting alone does not send a BE data
		// packet, so push the update before the client recomputes the static connection model.
		syncBlockEntityToClient();
		if (level instanceof ServerLevel server) {
			ItemNetworkManager.topologyChanged(server, worldPosition);
			ItemPipeBlock.refreshConnections(server, worldPosition);
			ItemPipeBlock.refreshConnections(server, worldPosition.relative(direction));
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("FaceModes", packedFaceModes);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		packedFaceModes = input.getIntOr("FaceModes", 0);
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			ItemNetworkManager.unregister(this);
			registered = false;
		}
		super.setRemoved();
	}
}
