package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV copper cable: a transport segment of a logical {@link dev.alaindustrial.core.energy.EnergyNetwork}.
 * The cable no longer pushes energy itself — the network owns transport and ticks once per server
 * tick via {@link NetworkManager}. The cable's job is lifecycle: it registers itself with the
 * {@link NetworkManager} on first server tick (after its level + neighbours are loaded) and
 * unregisters on {@link #setRemoved()}. Cable transport is a throughput limit owned by the network
 * (tier packetCap per consumer), not an EU-destroying toll — see MOD-009.
 */
public class CableBlockEntity extends MachineBlockEntity {
	/** Whether this cable has been registered with its level's {@link NetworkManager}. */
	private boolean registered;

	/**
	 * Whether this cable's connection + low-arm flags have been reconciled with current
	 * {@code isCableConnectable} semantics since it was loaded. {@code transient} on purpose — it
	 * must NOT persist: a freshly loaded cable starts {@code false}, re-derives its flags once on the
	 * first server tick, then stays {@code true} only in memory. Mirrors the {@link #registered}
	 * flag's lifecycle (also transient — see the persistence overrides below, which save energy only).
	 * See {@link #validateShape(Level, BlockPos, BlockState)}.
	 */
	private boolean shapeValidated;

	/**
	 * Persist only the cable-segment buffer — NOT the full machine-path keys. The cable has no
	 * inventory (0 slots), no processing state (progress is always 0) and no owner (transport, not
	 * a working machine), so the base class's {@code Progress}/{@code MaxProgress}/{@code items} keys
	 * would all be empty/zero. Skipping them keeps per-cable NBT minimal — meaningful on a base with
	 * hundreds of cable segments — and also avoids a redundant duplicate {@code "Energy"} write that
	 * a {@code super} call would perform before {@link #saveEnergyOnly} wrote it again.
	 *
	 * <p>Backward-compat: existing player saves that DO carry the legacy {@code Progress}/
	 * {@code MaxProgress} keys (always 0 on a cable) round-trip cleanly — they are simply ignored on
	 * load (defaults remain 0, which is the correct value for a transport segment that never
	 * processes anything).
	 */
	@Override
	protected void saveAdditional(ValueOutput output) {
		// Intentionally NOT calling super.saveAdditional — we want the slim path, not the machine path.
		saveEnergyOnly(output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		// Intentionally NOT calling super.loadAdditional — see saveAdditional above.
		loadEnergyOnly(input);
	}

	public CableBlockEntity(BlockPos pos, BlockState state) {
		// capacity = Config.cableBuffer (the live per-segment buffer, e.g. 12 EU). maxInsert/maxExtract =
		// LV.maxVoltage() (32) are set for symmetry, but on a cable they are effectively no-ops: since
		// capacity (12) < 32, EnergyBuffer's min(maxInsert, capacity - amount) is always dominated by the
		// capacity term. The REAL per-cable throughput is the buffer size (MOD-070: energy flows through
		// the segment buffer, so a cable carries cableBuffer EU/tick), and the tier packet cap is applied
		// separately in EnergyNetwork.tick(). A future MV/HV cable should raise Config's buffer, not assume
		// these rate fields gate throughput.
		super(ModContent.COPPER_CABLE_BE.get(), pos, state, EnergyTier.LV, 0,
				Config.cableBuffer, EnergyTier.LV.maxVoltage(), EnergyTier.LV.maxVoltage());
	}

	/** Transport, not a working machine (MOD-133): no owner, no player stats, no per-segment UUID ballast. */
	@Override
	public boolean tracksOwner() {
		return false;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// Register lazily on the first server tick: by now the level is set and the chunk (with its
		// neighbours) is loaded, so endpoint discovery and cross-chunk unions are correct. This is the
		// robust "on load" path that also uniformly covers world/chunk load (not just block placement).
		ensureRegistered();
		// Reconcile stale connection flags saved in chunk palette NBT against current
		// isCableConnectable semantics (e.g. the MOD-061 FACING-inert default). updateShape only fires
		// on neighbourChanged, not on chunk load, so without this a cable that was laid against a
		// machine's front face before MOD-061 would keep drawing the misleading arm forever.
		validateShape(level, pos, state);
		// Transport is owned by the network tick, not the cable; the per-cable check above is trivial,
		// so cables stay awake (return 0) rather than manage a sleep timer (R-29).
		return 0;
	}

	/**
	 * Registers this cable with its level's {@link NetworkManager} if not already done, keeping
	 * {@link #registered} in lockstep with the actual registration so {@link #setRemoved()} can
	 * reliably unregister it later. {@link NetworkManager#register} is itself idempotent, but that
	 * alone isn't enough — the flag must be set here (the same call site) or a caller that registers
	 * without setting it (e.g. wanting the network's awake state synchronously) would leave the
	 * network permanently unaware that this cable was ever removed if it's broken before its first
	 * {@link #onServerTick}. Called both from the tick path above and eagerly from
	 * {@link dev.alaindustrial.block.CableBlock#setPlacedBy}.
	 */
	public void ensureRegistered() {
		if (!registered && level instanceof ServerLevel) {
			NetworkManager.register(this);
			registered = true;
		}
	}

	/**
	 * Once-per-load reconciliation of the six {@link PipeBlock#PROPERTY_BY_DIRECTION} connection flags
	 * and the four horizontal {@code *_low} flags against the live {@code isCableConnectable} /
	 * collision-shape semantics. The chunk palette stores these flags in NBT, and
	 * {@link dev.alaindustrial.block.CableBlock#updateShape} only runs on {@code neighbourChanged}
	 * — so a cable loaded from an older world (where a face was connectable and no longer is, e.g.
	 * the MOD-061 FACING-inert default) keeps its stale, now-misleading arm until a neighbour
	 * changes. This re-derives all ten flags once on the first server tick and, if any diverged,
	 * writes the corrected state with {@link Block#UPDATE_CLIENTS} (NOT {@code setBlockAndUpdate} /
	 * {@code UPDATE_ALL}): {@code UPDATE_CLIENTS} omits the {@code UPDATE_NEIGHBORS} bit, so it does
	 * NOT re-enter through {@link dev.alaindustrial.block.CableBlock#neighborChanged} →
	 * {@link NetworkManager#onNeighbourChanged} (precedent: {@code MachineBlockEntity.updateLit}).
	 * Vanilla still runs a bounded {@code updateShape} cascade on the six neighbours (gate
	 * {@code (flags & 16) == 0 && limit > 0}, {@code limit = 512}) — that is desired and safe: it
	 * lets the neighbours re-derive their own flags from this cable's unchanged presence.
	 *
	 * <p>Energy-routing is unaffected: endpoint discovery (in the topology cache behind
	 * {@link dev.alaindustrial.core.energy.EnergyNetwork})
	 * queries energy capabilities through the loader adapters (Fabric {@code EnergyStorage.SIDED.find}
	 * / NeoForge {@code Capabilities.Energy.BLOCK}), never reads {@code PROPERTY_BY_DIRECTION} — so
	 * the flags are write-only relative to routing and this re-shape is energy-safe on both loaders.
	 * The re-derivation is also self-consistent with {@code getStateForPlacement} and
	 * {@code updateShape} (both flag sets), so a half-block neighbour (MOD-042 sibling) is handled
	 * correctly too.
	 *
	 * <p>Gated on {@code level instanceof ServerLevel} so a client-side cable (e.g. in a particle
	 * preview) never calls {@code setBlock}; same gate as {@link #ensureRegistered()}.
	 *
	 * <p>The {@code state} parameter is intentionally ignored: the live state is re-read from the
	 * level inside, because a caller driving {@code serverTick} directly (e.g. a GameTest) can pass
	 * a stale or mismatched state, and the migration must operate on what's actually in the world.
	 * If the block at {@code pos} is no longer a cable, there is nothing to migrate.
	 */
	private void validateShape(Level level, BlockPos pos, BlockState state) {
		if (shapeValidated || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		// Re-read the live state and bail out if `pos` is no longer a cable (broken mid-tick, stale
		// caller-supplied state, etc.) — see method javadoc for why `state` is not trusted.
		BlockState live = serverLevel.getBlockState(pos);
		if (!(live.getBlock() instanceof CableBlock)) {
			shapeValidated = true;
			return;
		}
		BlockState corrected = live;
		boolean changed = false;
		// Six connection flags (one per face): re-derive from the live connectsTo contract.
		for (Direction dir : Direction.values()) {
			BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			boolean expected = CableBlock.shouldConnectTo(serverLevel, pos, dir);
			if (corrected.getValue(prop) != expected) {
				corrected = corrected.setValue(prop, expected);
				changed = true;
			}
		}
		// Four horizontal low-arm flags: re-derive from the neighbour's collision shape, same as
		// updateShape / getStateForPlacement.
		for (Direction dir : Direction.values()) {
			BooleanProperty lowProp = CableBlock.lowFlagFor(dir);
			if (lowProp != null) {
				boolean expected = CableBlock.isLowNeighbourAt(serverLevel, pos.relative(dir));
				if (corrected.getValue(lowProp) != expected) {
					corrected = corrected.setValue(lowProp, expected);
					changed = true;
				}
			}
		}
		if (changed) {
			serverLevel.setBlock(pos, corrected, Block.UPDATE_CLIENTS);
		}
		shapeValidated = true;
	}

	@Override
	public void setRemoved() {
		if (registered && level instanceof ServerLevel) {
			NetworkManager.unregister(this);
			registered = false;
		}
		// Reset alongside `registered` for lifecycle symmetry: if a cable is removed and its entity
		// somehow reused (or the flag is read after removal), it must not claim a validated shape.
		shapeValidated = false;
		super.setRemoved();
	}
}
