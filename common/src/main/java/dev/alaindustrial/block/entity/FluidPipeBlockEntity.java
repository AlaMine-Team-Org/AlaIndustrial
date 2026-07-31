package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.item.PipeFaceMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One segment of a fluid pipe (MOD-151): a small live {@link FluidTank} plus per-face configuration.
 *
 * <p><b>Buffered, like a cable — not like an item pipe.</b> The item pipe is bufferless: its transfer
 * is atomic and instantaneous, so nothing is ever "in" a pipe. Fluid instead physically occupies the
 * segment, one hop per tick, exactly as EU occupies a cable's {@code EnergyBuffer} (MOD-070). That is
 * what lets a player see what a line is carrying, and it is why breaking a segment simply loses its
 * contents: the buffer is deliberately tiny ({@link Config#fluidPipeSegmentBuffer}), so a wall of
 * pipes can never serve as bulk storage.
 *
 * <p><b>Single variant per segment.</b> The buffer is a plain {@link FluidTank}, which already refuses
 * a second fluid while it holds one — so "you cannot mix water and lava in the same line" needs no
 * code of its own.
 */
public final class FluidPipeBlockEntity extends MachineBlockEntity implements FluidPortHost {

	/** The segment's live buffer. Public for the same reason the tank block's is: direct drain/fill. */
	public final FluidTank fluidBuffer = new FluidTank(Config.fluidPipeSegmentBuffer,
			fluid -> !fluid.isEmpty(), fluid -> true, this::bufferChanged);

	private int packedFaceModes;
	private boolean registered;

	public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.FLUID_PIPE_BE.get(), pos, state, EnergyTier.LV, 0, 0, 0, 0);
	}

	/** Transport, not a working machine (MOD-133): no owner, no player stats, no per-segment UUID. */
	@Override
	public boolean tracksOwner() {
		return false;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		return 0;
	}

	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			FluidNetworkManager.register(this);
			registered = true;
		}
	}

	/**
	 * The buffer, unless this face is switched off. A {@code DISABLED} face publishes nothing, so a
	 * foreign mod reading our capability sees the same wall the player set up.
	 */
	@Override
	public FluidPort fluidPort(Direction side) {
		return faceMode(side) == PipeFaceMode.DISABLED ? null : fluidBuffer;
	}

	public PipeFaceMode faceMode(Direction direction) {
		return PipeFaceMode.values()[(packedFaceModes >>> (direction.ordinal() * 2)) & 3];
	}

	/** Advance this face one step along the wrench ladder (neutral → extract → insert → disabled). */
	public void cycleFaceMode(Direction direction) {
		setFaceMode(direction, faceMode(direction).nextInCycle());
	}

	public void setFaceMode(Direction direction, PipeFaceMode mode) {
		int shift = direction.ordinal() * 2;
		int next = (packedFaceModes & ~(3 << shift)) | (mode.ordinal() << shift);
		if (next == packedFaceModes) {
			return;
		}
		packedFaceModes = next;
		markDirtyAndSync();
		syncBlockEntityToClient();
		if (level instanceof ServerLevel server) {
			FluidNetworkManager.topologyChanged(server, worldPosition);
			FluidPipeBlock.refreshConnections(server, worldPosition);
			FluidPipeBlock.refreshConnections(server, worldPosition.relative(direction));
		}
	}

	/**
	 * Push the buffer to the client so the tint can follow it, but only when the visible state actually
	 * changed — the fluid TYPE or empty/non-empty, never the raw amount. A pipe run is hundreds of
	 * segments and this would otherwise be a full block-entity packet per segment per tick.
	 */
	private void bufferChanged() {
		setChanged();
		if (!(level instanceof ServerLevel)) {
			return;
		}
		Fluid current = fluidBuffer.fluid.fluid();
		boolean nowFilled = fluidBuffer.amount > 0;
		if (current != lastSyncedFluid || nowFilled != lastSyncedFilled) {
			lastSyncedFluid = current;
			lastSyncedFilled = nowFilled;
			syncBlockEntityToClient();
			FluidPipeBlock.refreshFilled(level, worldPosition, nowFilled);
		}
	}

	private Fluid lastSyncedFluid = Fluids.EMPTY;
	private boolean lastSyncedFilled;

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("FaceModes", packedFaceModes);
		output.putLong("FluidMb", fluidBuffer.amount);
		if (!fluidBuffer.fluid.isEmpty()) {
			output.putString("FluidId", BuiltInRegistries.FLUID.getKey(fluidBuffer.fluid.fluid()).toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		packedFaceModes = input.getIntOr("FaceModes", 0);
		Fluid fluid = resolveFluid(input.getStringOr("FluidId", ""));
		long amount = Math.max(0L, Math.min(Config.fluidPipeSegmentBuffer, input.getLongOr("FluidMb", 0L)));
		if (fluid == Fluids.EMPTY || amount == 0) {
			fluidBuffer.fluid = FluidHolder.EMPTY;
			fluidBuffer.amount = 0;
		} else {
			fluidBuffer.fluid = FluidHolder.of(fluid);
			fluidBuffer.amount = amount;
		}
		lastSyncedFluid = fluidBuffer.fluid.fluid();
		lastSyncedFilled = fluidBuffer.amount > 0;
	}

	private static Fluid resolveFluid(String key) {
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return Fluids.EMPTY;
		}
		Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
		return fluid == null ? Fluids.EMPTY : fluid;
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			FluidNetworkManager.unregister(this);
			registered = false;
		}
		super.setRemoved();
	}
}
