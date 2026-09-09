package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.monitor.MonitorNetworkManager;
import dev.alaindustrial.core.monitor.MonitorReadout;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One panel of a monitoring wall (MOD-480).
 *
 * <p><b>Deliberately not a {@code MachineBlockEntity}.</b> A panel owns two values — the item it
 * watches and the number it shows — and nothing else: no buffer, no progress, no upgrade slots.
 * The reason is the wire, not tidiness: {@code syncBlockEntityToClient} ships a block entity's whole
 * NBT to every player watching the chunk, so a hundred-panel wall of machines would be a hundred
 * full machine packets every time a single number moved. Two fields make that packet trivial, and it
 * is only sent when the number actually changes.
 *
 * <p>The count is NOT persisted. After a load it is unknown until the core's next scan, and showing
 * a remembered number would be showing a number that was true before the world was closed.
 */
public class MonitorPanelBlockEntity extends BlockEntity {

	private ItemStack filter = ItemStack.EMPTY;
	private MonitorReadout readout = MonitorReadout.IDLE;
	private long count;
	private boolean registered;

	public MonitorPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MONITOR_PANEL_BE.get(), pos, state);
	}

	public ItemStack getFilter() {
		return filter;
	}

	public MonitorReadout getReadout() {
		return readout;
	}

	public long getCount() {
		return count;
	}

	/** Set (or clear) the watched item and tell the network its demand has changed. */
	public void setFilter(ItemStack stack) {
		this.filter = stack;
		if (stack.isEmpty()) {
			this.readout = MonitorReadout.IDLE;
			this.count = 0L;
		}
		sync();
		if (level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.demandChanged(serverLevel, worldPosition);
		}
	}

	/**
	 * The core's verdict for this panel. Called once per scan; the packet only goes out when
	 * something a player can see has actually moved.
	 */
	public void publish(MonitorReadout newReadout, long newCount) {
		if (this.readout == newReadout && this.count == newCount) {
			return;
		}
		this.readout = newReadout;
		this.count = newCount;
		sync();
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	public void ensureRegistered() {
		if (registered || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		registered = true;
		MonitorNetworkManager.register(serverLevel, worldPosition);
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel serverLevel) {
			MonitorNetworkManager.unregister(serverLevel, worldPosition);
			registered = false;
		}
		super.setRemoved();
	}

	/**
	 * Hand the watched item back when the panel is broken.
	 *
	 * <p>In {@code preRemoveSideEffects} rather than the block's {@code affectNeighborsAfterRemoval}:
	 * by the time the latter runs, 26.2 has already detached the block entity and the filter would be
	 * gone with it.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel && !filter.isEmpty()) {
			Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					filter.copy());
			filter = ItemStack.EMPTY;
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("Filter", ItemStack.OPTIONAL_CODEC, filter);
		output.putInt("Readout", readout.ordinal());
		// Written because this same call builds the client's update tag; a stale number after a world
		// load survives at most one scan interval, and the alternative is a second sync path.
		output.putLong("Count", count);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		filter = input.read("Filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		readout = MonitorReadout.byOrdinal(input.getIntOr("Readout", MonitorReadout.IDLE.ordinal()));
		count = input.getLongOr("Count", 0L);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return saveWithoutMetadata(provider);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
