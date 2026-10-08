package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidNetwork;
import dev.alaindustrial.core.fluid.FluidNetworkManager;
import dev.alaindustrial.core.fluid.FluidPipeNode;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.fluid.PipeFamily;
import dev.alaindustrial.core.fluid.SteamLineMigration;
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
 *
 * <p><b>One class for both families (MOD-662).</b> The steam pipes share this block entity; what a
 * segment accepts is read live from the block it stands in ({@link #family()}), so a pipe that the world
 * migration turns from fluid to steam keeps this very object — buffer, face modes and all.
 *
 * <p><b>Transport, not a machine (MOD-400).</b> The segment holds a fluid buffer and nothing else: no
 * item inventory, no processing progress, no upgrade panel, no owner.
 */
public final class FluidPipeBlockEntity extends EnergyBlockEntity implements FluidPortHost, FluidPipeNode {

	/**
	 * The segment's live buffer. Public for the same reason the tank block's is: direct drain/fill.
	 * Its size is the grade's ({@link FluidPipeBlock#segmentCapacity}, MOD-675), fixed when the segment
	 * is created or loaded.
	 */
	public final FluidTank fluidBuffer;

	/** Save key marking a segment written after pipes split into families (MOD-662). */
	private static final String STEAM_SPLIT_KEY = "SteamSplit";

	private int packedFaceModes;
	private boolean registered;
	/** Whether the once-per-load face re-derive has run — see {@link #validateShapeOnce}. */
	private boolean shapeValidated;
	/**
	 * Loaded from a save written before the steam pipes existed (MOD-662): such a segment may be part of a
	 * steam line laid in fluid pipe, and is checked by {@link SteamLineMigration} before anything else.
	 * A segment placed in this session, or loaded from a newer save, is never legacy.
	 */
	private boolean legacy;
	/** Whether this session's migration check has settled — see {@link #onServerTick}. */
	private boolean migrationChecked;

	public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.FLUID_PIPE_BE.get(), pos, state, EnergyTier.LV, 0, 0, 0);
		this.fluidBuffer = new FluidTank(segmentCapacity(state), this::accepts, fluid -> true, this::bufferChanged);
	}

	/**
	 * Not an energy block (MOD-691): no face is a port. The zero-capacity buffer is scaffolding inherited
	 * with the tick and persistence of {@link EnergyBlockEntity}; left on the default {@code BOTH} role it
	 * made every face a live port, and NeoForge's lookup, which asks {@code energyPort()} directly, turned a
	 * pipe pressed against a cable into a producer and a consumer of that network — one that then never
	 * slept. {@code NONE} makes {@link #energyPort} return {@code null} on both loaders, as the sprinkler's does.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.NONE;
	}

	/** The grade's segment size; the basic knob for a state that is somehow not a pipe. */
	private static long segmentCapacity(BlockState state) {
		return state.getBlock() instanceof FluidPipeBlock pipe
				? pipe.segmentCapacity() : Math.max(1, Config.fluidPipeSegmentBuffer);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ensureRegistered();
		// MOD-662: the world migration runs from the segment's own tick — never from loadAdditional,
		// where there is no level, and never from setRemoved, where touching the world deadlocks a 26.x
		// server. An unanswered check (a neighbour not loaded yet) simply runs again next tick.
		if (legacy && !migrationChecked) {
			migrationChecked = SteamLineMigration.check(this, level, pos);
		}
		if (reloadedLive && level instanceof ServerLevel server) {
			// Saved data loaded over this live segment may carry other face modes (MOD-734 review): re-join it
			// exactly as a wrench does — re-partition, mark the network dirty, redraw the arms on both sides.
			reloadedLive = false;
			FluidNetworkManager.topologyChanged(server, pos);
			for (Direction dir : Direction.values()) {
				FluidPipeBlock.refreshConnections(server, pos.relative(dir));
			}
		}
		validateShapeOnce(level, pos);
		// A commit that no network tick settled (MOD-734): written from outside the network while one
		// was ticking. Settling here keeps the chunk's unsaved mark at most a tick late, and the wake lets
		// a sleeping network see the write (ADR-047).
		if (unsettled) {
			settle();
			wakeNetwork();
		}
		return 0;
	}

	/** The family of the block this segment stands in; a fluid pipe's if that block is somehow not a pipe. */
	public PipeFamily family() {
		return getBlockState().getBlock() instanceof FluidPipeBlock pipe ? pipe.family() : PipeFamily.FLUID;
	}

	private boolean accepts(FluidHolder fluid) {
		return family().accepts(fluid);
	}

	/** Whether this segment came from a save older than the pipe families and still awaits its check. */
	public boolean isLegacy() {
		return legacy;
	}

	/**
	 * Ask a legacy segment to check itself again on its next tick — the migration's wave (MOD-662): a
	 * neighbour that just became a steam pipe may be exactly the evidence this segment lacked.
	 */
	public void recheckMigration() {
		if (legacy) {
			migrationChecked = false;
		}
	}

	/**
	 * Re-derive the six drawn faces once per load (MOD-540, the pattern MOD-061 established for
	 * cables). The blockstate stores what each face draws, including whether its arm drops toward a
	 * half-block neighbour — but the paths that keep it current, {@code getStateForPlacement} and
	 * {@code updateShape}, run on placement and on a neighbour change, never on chunk load. A pipe
	 * laid before this feature existed, or one whose neighbour changed height while its chunk was
	 * unloaded, would therefore keep a stale sleeve for ever. One pass on the first server tick costs
	 * a handful of neighbour reads per segment per load and makes the saved geometry self-correcting.
	 */
	private void validateShapeOnce(Level level, BlockPos pos) {
		if (shapeValidated) {
			return;
		}
		shapeValidated = true;
		FluidPipeBlock.refreshConnections(level, pos);
		// The drawn core follows the buffer as loaded (MOD-734): the fill is settled once per network
		// tick, so a save is the one place the two could part, and this read brings them back together.
		FluidPipeBlock.refreshFilled(level, pos, fluidBuffer.amount > 0);
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

	@Override
	public PipeFaceMode faceMode(Direction direction) {
		return PipeFaceMode.values()[(packedFaceModes >>> (direction.ordinal() * 2)) & 3];
	}

	/** The segment's buffer, as the network core reads it ({@link FluidPipeNode}, MOD-715). */
	@Override
	public FluidTank lineBuffer() {
		return fluidBuffer;
	}

	/** {@link FluidPipeBlock#shouldConnectTo} for this segment's own position ({@link FluidPipeNode}). */
	@Override
	public boolean connects(Direction side) {
		return FluidPipeBlock.shouldConnectTo(level, worldPosition, side);
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
		setChangedQuietly();
		syncBlockEntityToClient();
		if (level instanceof ServerLevel server) {
			FluidNetworkManager.topologyChanged(server, worldPosition);
			FluidPipeBlock.refreshConnections(server, worldPosition);
			FluidPipeBlock.refreshConnections(server, worldPosition.relative(direction));
		}
	}

	/**
	 * The buffer committed (MOD-734). Inside a fluid-network tick the commit only marks the segment: the
	 * network {@link #settle() settles} it once the tick is over, against the state the tick left. A write
	 * from anywhere else — a pump, a capsule, another mod — settles at once, as every commit used to, and
	 * wakes the network, which may be asleep (ADR-047).
	 */
	private void bufferChanged() {
		unsettled = true;
		if (!FluidNetwork.isTicking()) {
			settle();
			wakeNetwork();
		}
	}

	/** A sleeping network does not watch its segments: a write from outside its tick has to wake it (ADR-047). */
	private void wakeNetwork() {
		if (level instanceof ServerLevel server) {
			FluidNetworkManager.segmentChanged(server, worldPosition);
		}
	}

	/**
	 * Mark the chunk unsaved and push the buffer to the client so the tint can follow it, but only when
	 * the visible state actually changed — the fluid TYPE or empty/non-empty, never the raw amount. A pipe
	 * run is hundreds of segments and this would otherwise be a full block-entity packet per segment per
	 * tick.
	 */
	@Override
	public void settle() {
		if (!unsettled) {
			return;
		}
		unsettled = false;
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

	/** A commit has not been settled yet — see {@link #bufferChanged}. */
	private boolean unsettled;
	/** Saved data was loaded over this segment while it stood in a server level — see {@link #loadMachineData}. */
	private boolean reloadedLive;
	private Fluid lastSyncedFluid = Fluids.EMPTY;
	private boolean lastSyncedFilled;

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("FaceModes", packedFaceModes);
		// MOD-662: the only key the steam split added. Old saves lack it and load unchanged; its absence is
		// what marks a segment for the one-off migration check.
		output.putBoolean(STEAM_SPLIT_KEY, true);
		output.putLong("FluidMb", fluidBuffer.amount);
		if (!fluidBuffer.fluid.isEmpty()) {
			output.putString("FluidId", BuiltInRegistries.FLUID.getKey(fluidBuffer.fluid.fluid()).toString());
		}
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		packedFaceModes = input.getIntOr("FaceModes", 0);
		legacy = !input.getBooleanOr(STEAM_SPLIT_KEY, false);
		migrationChecked = false;
		Fluid fluid = resolveFluid(input.getStringOr("FluidId", ""));
		long amount = Math.max(0L, Math.min(fluidBuffer.capacity, input.getLongOr("FluidMb", 0L)));
		if (fluid == Fluids.EMPTY || amount == 0) {
			fluidBuffer.fluid = FluidHolder.EMPTY;
			fluidBuffer.amount = 0;
		} else {
			fluidBuffer.fluid = FluidHolder.of(fluid);
			fluidBuffer.amount = amount;
		}
		lastSyncedFluid = fluidBuffer.fluid.fluid();
		lastSyncedFilled = fluidBuffer.amount > 0;
		if (level instanceof ServerLevel) {
			// Loaded over a LIVE segment (MOD-734 review): /data merge, a structure or a schematic tool wrote
			// the buffer and the face modes with no commit and no wrench. Its network may be asleep, its joins
			// and endpoints stale, the drawn core and arms out of date, so the next tick re-joins the segment
			// (dirty), redraws it and settles, which wakes the network (ADR-047). A chunk load has no level
			// yet and registers the segment anew, which marks the network dirty by itself.
			unsettled = true;
			shapeValidated = false;
			reloadedLive = true;
		}
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
