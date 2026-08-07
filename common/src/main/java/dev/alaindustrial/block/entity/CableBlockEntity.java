package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.energy.ShockGuardMaterial;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * LV copper cable: a transport segment of a logical {@link dev.alaindustrial.core.energy.EnergyNetwork}.
 * The cable no longer pushes energy itself — the network owns transport and ticks once per server
 * tick via {@link NetworkManager}. The cable's job is lifecycle: it registers itself with the
 * {@link NetworkManager} on first server tick (after its level + neighbours are loaded) and
 * unregisters on {@link #setRemoved()}. Cable transport is a throughput limit owned by the network
 * (tier packetCap per consumer), not an EU-destroying toll — see MOD-009.
 */
public class CableBlockEntity extends MachineBlockEntity {
	/** Game tick of the most recent committed energy transfer; transient by design. */
	private long lastEnergyTransferTick = Long.MIN_VALUE;

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
	 * The exact block a player slipped under this segment as an insulating stand, or {@code null}
	 * (MOD-279). Unlike {@link #lastEnergyTransferTick} and the two flags above, this one
	 * <b>persists</b> — a stand a player paid an item for must survive a relog.
	 *
	 * <p>The concrete block is stored, not just its {@link ShockGuardMaterial} category, so the
	 * renderer can show birch as birch and blue stained glass as blue, and so removing the stand hands
	 * back the very item that was installed. The category — which is all the balance cares about — is
	 * derived on demand by {@link #shockGuard()}.
	 */
	@Nullable
	private Block shockGuard;

	/**
	 * Whether a breaker is clamped onto this segment, and whether it is currently closed (MOD-276).
	 *
	 * <p>Two flags rather than a tri-state enum because they answer two independent questions: an
	 * installed breaker is an item the player paid for (it must persist and drop back), while
	 * open/closed is the switch position. Both persist — a line left dead for maintenance must still
	 * be dead after a relog, otherwise a player logs back in to a live wire they believed they had
	 * isolated.
	 *
	 * <p>{@code breakerClosed} defaults to {@code true} so a freshly installed breaker leaves the line
	 * working: installing a switch must not black out a base by surprise.
	 */
	private boolean breakerInstalled;
	private boolean breakerClosed = true;

	/**
	 * Persist the cable-segment buffer and the insulating stand — NOT the full machine-path keys. The
	 * cable has no inventory (0 slots), no processing state (progress is always 0) and no owner
	 * (transport, not a working machine), so the base class's {@code Progress}/{@code MaxProgress}/
	 * {@code items} keys would all be empty/zero. Skipping them keeps per-cable NBT minimal —
	 * meaningful on a base with hundreds of cable segments — and also avoids a redundant duplicate
	 * {@code "Energy"} write that a {@code super} call would perform before {@link #saveEnergyOnly}
	 * wrote it again.
	 *
	 * <p>Backward-compat: existing player saves that DO carry the legacy {@code Progress}/
	 * {@code MaxProgress} keys (always 0 on a cable) round-trip cleanly — they are simply ignored on
	 * load (defaults remain 0, which is the correct value for a transport segment that never
	 * processes anything). A save from before MOD-279 has no {@code "ShockGuard"} key and reads back
	 * as {@link ShockGuardMaterial#NONE}, which is exactly right for a cable laid before stands existed.
	 *
	 * <p>This slim path is also what carries the stand to the <b>client</b>: the inherited
	 * {@code getUpdateTag} is {@code saveWithoutMetadata}, which routes through this very method, so
	 * writing the key here is all the renderer needs — no bespoke packet (see
	 * {@code CableAccessoryBlockEntityRenderer}).
	 */
	@Override
	protected void saveAdditional(ValueOutput output) {
		// Intentionally NOT calling super.saveAdditional — we want the slim path, not the machine path.
		saveEnergyOnly(output);
		if (shockGuard != null) {
			output.putString("ShockGuard", BuiltInRegistries.BLOCK.getKey(shockGuard).toString());
		}
		// Only written when a breaker is actually installed: the overwhelming majority of segments have
		// none, and this NBT is paid per cable on a base that may hold hundreds of them.
		if (breakerInstalled) {
			output.putBoolean("Breaker", true);
			output.putBoolean("BreakerClosed", breakerClosed);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		// Intentionally NOT calling super.loadAdditional — see saveAdditional above.
		loadEnergyOnly(input);
		shockGuard = readShockGuard(input.getStringOr("ShockGuard", ""));
		breakerInstalled = input.getBooleanOr("Breaker", false);
		// Default true, mirroring the field: a save from before MOD-276 has neither key and must read
		// back as "no breaker, line closed" — the state every existing world is in.
		breakerClosed = input.getBooleanOr("BreakerClosed", true);
	}

	/**
	 * Resolves a persisted block id back to its block, or {@code null} for absent, malformed or
	 * unknown ids. The lenient fallback is what makes a world saved with a mod that is no longer
	 * installed load as "no stand" instead of failing chunk load.
	 */
	@Nullable
	private static Block readShockGuard(String id) {
		if (id.isEmpty()) {
			return null;
		}
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return null;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(key);
		// The registry answers AIR for an unknown key rather than null; AIR is never a valid stand.
		return block == Blocks.AIR ? null : block;
	}

	/** The exact block installed as a stand under this segment (MOD-279), or {@code null} when bare. */
	@Nullable
	public Block shockGuardBlock() {
		return shockGuard;
	}

	/**
	 * The balance category of the installed stand — {@link ShockGuardMaterial#NONE} when bare. Derived
	 * from the stored block on each call rather than persisted alongside it, so the two can never
	 * disagree after a retag or a config change.
	 */
	public ShockGuardMaterial shockGuard() {
		return shockGuard == null ? ShockGuardMaterial.NONE : CableBlock.shockGuardMaterialOf(shockGuard);
	}

	/**
	 * Install ({@code block}) or clear ({@code null}) the insulating stand, persisting it and pushing
	 * it to watching clients so the renderer updates immediately. Both side effects live here rather
	 * than at the call sites because every caller needs both, and {@code syncBlockEntityToClient} is
	 * {@code protected} on the base class — {@link dev.alaindustrial.block.CableBlock} could not
	 * invoke it itself.
	 */
	public void setShockGuard(@Nullable Block block) {
		if (shockGuard == block) {
			return;
		}
		shockGuard = block;
		setChanged();
		syncBlockEntityToClient();
	}

	public CableBlockEntity(BlockPos pos, BlockState state) {
		// Every knob comes from the block's CableType (MOD-219) — before that, all four cable blocks shared
		// this one hardcoded LV/cableBuffer line and were therefore identical.
		//
		// capacity = the grade's segment buffer (copper 12, tin 8, gold 48 EU). This is the REAL throughput:
		// energy flows through the segment buffer (MOD-070), so a cable carries segmentBuffer EU/tick.
		// maxInsert/maxExtract = the grade's packetCap, set for symmetry but effectively no-ops on every
		// current grade: capacity < packetCap, so EnergyBuffer's min(maxInsert, capacity - amount) is always
		// dominated by the capacity term. The packet cap that actually bites is applied network-wide in
		// EnergyNetwork.tick(). A new grade must therefore raise its BUFFER to be felt as a wider pipe —
		// raising only its packet cap changes nothing.
		//
		// The state's block is read before the super() call (legal: it is derived from a constructor
		// parameter), which is what lets one BlockEntityType back three differently-tuned blocks.
		super(ModContent.COPPER_CABLE_BE.get(), pos, state, CableBlock.typeOf(state).tier(), 0,
				CableBlock.typeOf(state).segmentBuffer(), CableBlock.typeOf(state).packetCap(),
				CableBlock.typeOf(state).packetCap());
	}

	/**
	 * The tier this segment reports to the network — taken from the LIVE grade, not from the value frozen
	 * in the base class at construction, for the same in-place-swap reason as {@link #cableType()}.
	 */
	@Override
	public EnergyTier getTier() {
		return cableType().tier();
	}

	/**
	 * This segment's grade — the source of its packet cap, loss and tier. Resolved from the LIVE block
	 * state on every call rather than cached at construction.
	 *
	 * <p>All five cable blocks share ONE {@code BlockEntityType}, so an in-place grade swap
	 * ({@code /setblock} or {@code /fill} over an existing cable, a structure, a world edit) is a case
	 * worth being explicit about. Measured on 26.2: vanilla DOES rebuild the entity there — a cable
	 * swapped copper→gold comes back with gold's buffer immediately, and
	 * {@code tcCable003Phy01_inPlaceGradeSwapRebuildsSegment} guards that. Reading the grade off the live
	 * state rather than caching it costs nothing and keeps this method correct without depending on that
	 * vanilla detail staying the way it is.
	 */
	public CableType cableType() {
		return CableBlock.typeOf(getBlockState());
	}

	@Override
	protected void onEnergyTransactionCommitted() {
		super.onEnergyTransactionCommitted();
		if (level != null) {
			lastEnergyTransferTick = level.getGameTime();
		}
	}

	/**
	 * Whether energy actually moved through this exact segment on the current or immediately preceding
	 * server tick. The one-tick grace makes collision order independent while still becoming safe as
	 * soon as a disconnected line stops moving; retained buffer energy alone never energizes a cable.
	 */
	public boolean isEnergizedForShock() {
		if (level == null || lastEnergyTransferTick == Long.MIN_VALUE) {
			return false;
		}
		long age = level.getGameTime() - lastEnergyTransferTick;
		return age >= 0 && age <= 1;
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
		dropShockGuardIfObstructed(level, pos);
		shockNearbyPlayers(level, pos, state);
		// Transport is owned by the network tick, not the cable; the per-cable check above is trivial,
		// so cables stay awake (return 0) rather than manage a sleep timer (R-29).
		return 0;
	}

	/**
	 * Pops the insulating stand off this segment once a downward connection claims the space it
	 * occupies (MOD-279). A stand renders as a thin plate at the floor of the cell, which is the same
	 * volume {@code CableBlock}'s {@code DOWN} arm fills, so the two are mutually exclusive:
	 * {@code CableBlock.useItemOn} refuses to install a stand on an already-connected segment, and this
	 * handles the opposite order — a neighbour appearing <em>under</em> a segment that already has one.
	 * The stand is returned as an item rather than deleted, because the player paid for it.
	 *
	 * <p><b>Why here and not in {@code validateShape}.</b> That method is a one-shot migration guarded
	 * by {@link #shapeValidated}: it runs once per load and then early-returns forever, so a neighbour
	 * placed minutes later would never be seen. This check is unguarded and runs every tick, which is
	 * affordable for the same reason {@link #shockNearbyPlayers}'s guards are — the common case is a
	 * segment with no stand at all, which costs one enum comparison and returns.
	 * {@link dev.alaindustrial.block.CableBlock#neighborChanged} calls it too, so the reaction is
	 * immediate rather than up to a tick late.
	 */
	public void dropShockGuardIfObstructed(Level level, BlockPos pos) {
		if (shockGuard == null || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockState live = serverLevel.getBlockState(pos);
		if (!(live.getBlock() instanceof CableBlock)
				|| !live.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN))) {
			return;
		}
		ItemStack drop = new ItemStack(shockGuard);
		setShockGuard(null);
		Block.popResource(serverLevel, pos, drop);
	}

	/**
	 * Extends the MOD-260 shock hazard beyond direct contact: a survival player standing within
	 * {@link dev.alaindustrial.Config#bareCableShockProximityRadius} blocks of an energized bare
	 * segment is shocked even without touching the thin wire model. {@link CableBlock}'s
	 * {@code entityInside} shape cannot reach past this block's own cell (a vanilla constraint), so
	 * the extra radius is enforced here instead. Delegates the final eligibility decision to
	 * {@link CableBlock#tryShockPlayer} so the per-player rules (creative/spectator, invulnerability
	 * window) stay defined in exactly one place.
	 *
	 * <p><b>The cheap constant guards come first, and that ordering is load-bearing.</b> Cables never
	 * sleep (see {@link #onServerTick}), so this runs for every segment on every tick — a base can hold
	 * hundreds. Config-off, insulated and de-energized segments therefore return before touching the
	 * player list at all, which is what keeps MOD-260's shipped "no tick overhead when disabled"
	 * guarantee true. For the same reason this walks {@link ServerLevel#players()} (a handful of
	 * entries, no allocation) instead of a broadphase entity query.
	 *
	 * <p>A clear-line check keeps the radius honest: without it a cable buried in a wall or floor would
	 * shock a player on the far side of solid stone, which is neither intuitive nor what the radius is
	 * for. Occlusion is only tested for players already inside the zone, so the raycast is rare.
	 */
	private void shockNearbyPlayers(Level level, BlockPos pos, BlockState state) {
		double radius = Config.bareCableShockProximityRadius;
		if (!Config.bareCableShockEnabled || radius <= 0 || !(level instanceof ServerLevel serverLevel)
				|| !(state.getBlock() instanceof CableBlock cableBlock)
				|| cableBlock.type().isInsulated() || !isEnergizedForShock()) {
			return;
		}
		for (ServerPlayer player : serverLevel.players()) {
			if (isWithinShockReach(serverLevel, pos, player)) {
				cableBlock.tryShockPlayer(serverLevel, pos, player);
			}
		}
	}

	/**
	 * Whether {@code player} is close enough to the segment at {@code pos} for the proximity hazard, and
	 * not shielded from it. Side-effect-free and public so both loader GameTest lanes can assert the
	 * radius and the cover rule directly, the same way {@link CableBlock#shouldShockPlayer} exposes the
	 * per-player eligibility rules (MOD-269).
	 *
	 * <p>Reads {@link Config#bareCableShockProximityRadius} live, so a radius of {@code 0} answers
	 * {@code false} for everyone — the MOD-260 direct-contact-only behaviour.
	 */
	public static boolean isWithinShockReach(ServerLevel level, BlockPos pos, ServerPlayer player) {
		double radius = Config.bareCableShockProximityRadius;
		if (radius <= 0) {
			return false;
		}
		return player.getBoundingBox().intersects(new AABB(pos).inflate(radius))
				&& hasClearContact(level, pos, player);
	}

	/**
	 * Whether nothing solid stands between the cable at {@code pos} and {@code player}. A hit on the
	 * cable's own cell still counts as clear — that is the wire itself, not cover.
	 *
	 * <p>The ray is cast <b>from the player toward the cable</b>, not the other way round, and the
	 * direction is load-bearing. A cable's own core occupies the middle of its cell, so a ray starting
	 * at {@link Vec3#atCenterOf} begins <em>inside</em> that collider and {@code clip} returns the cable
	 * itself at zero distance — which this method reads as "clear", making the whole cover check inert
	 * no matter what stands further along. Starting at the player's centre (open air, since the player
	 * is standing there) means the first thing the ray meets is genuine cover if any exists, and the
	 * cable itself otherwise.
	 */
	private static boolean hasClearContact(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockHitResult hit = level.clip(new ClipContext(player.getBoundingBox().getCenter(), Vec3.atCenterOf(pos),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
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
		// An open breaker keeps this segment out of the graph (MOD-276). The gate belongs HERE, not only
		// in the toggle handler: onServerTick calls this every tick unconditionally, so an open segment
		// would re-register itself one tick after being cut out and the switch would do nothing at all.
		if (isBreakerOpen()) {
			return;
		}
		if (!registered && level instanceof ServerLevel) {
			NetworkManager.register(this);
			registered = true;
		}
	}

	/** True when a breaker is installed here and switched off — this segment is cut out of the grid. */
	public boolean isBreakerOpen() {
		return breakerInstalled && !breakerClosed;
	}

	/**
	 * Every face goes inert while the breaker is open (MOD-276).
	 *
	 * <p><b>Leaving the graph is not enough on its own.</b> A cable that is only unregistered is still a
	 * block entity exposing an energy port on all six faces, and the networks on either side then treat
	 * it as an ordinary neighbouring machine: the upstream grid charges its buffer as a consumer, the
	 * downstream grid drains it as a source, and energy keeps crossing the open switch one segment
	 * buffer at a time. Measured on the first run of this feature: the load past an "open" breaker
	 * still drew 180 EU. Reporting {@link EnergyRole#NONE} is what actually opens the circuit —
	 * {@code EnergyTopologyCache} skips a null port outright, so the segment joins neither
	 * {@code producers} nor {@code consumers} of either side.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return isBreakerOpen() ? EnergyRole.NONE : super.energyRoleForFace(worldFace);
	}

	/** Whether a breaker is clamped onto this segment at all (MOD-276). */
	public boolean hasBreaker() {
		return breakerInstalled;
	}

	/** The switch position of an installed breaker; meaningless (and always {@code true}) without one. */
	public boolean isBreakerClosed() {
		return breakerClosed;
	}

	/**
	 * Install or remove the breaker accessory. Removing one always restores the segment to the grid —
	 * a switch that is gone cannot keep a line dead, which is what makes "just break the thing off" a
	 * reliable escape hatch if a player forgets which of a dozen breakers is the open one.
	 */
	public void setBreakerInstalled(boolean installed) {
		if (breakerInstalled == installed) {
			return;
		}
		breakerInstalled = installed;
		if (!installed) {
			breakerClosed = true;
		}
		applyBreakerToNetwork();
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * Throw the switch. Closing re-joins this segment to its neighbours through the ordinary
	 * {@link NetworkManager#register} path — the very call a newly placed cable makes — and opening
	 * cuts it out through {@link NetworkManager#unregister}, exactly as breaking the cable would.
	 * Reusing those two entry points is what keeps this feature out of {@code core/energy}: splitting
	 * and merging components is code that has been carrying the grid since day one.
	 *
	 * <p>Early-returns when nothing changes, so a double click costs nothing.
	 */
	public void setBreakerClosed(boolean closed) {
		if (!breakerInstalled || breakerClosed == closed) {
			return;
		}
		breakerClosed = closed;
		applyBreakerToNetwork();
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * Reconcile graph membership with the switch position. Note the asymmetry with {@link #registered}:
	 * it tracks whether {@code NetworkManager} currently knows this cable, and both branches keep it
	 * truthful, so {@link #setRemoved()} never double-unregisters an already-cut segment.
	 */
	private void applyBreakerToNetwork() {
		if (!(level instanceof ServerLevel)) {
			return;
		}
		if (isBreakerOpen()) {
			if (registered) {
				NetworkManager.unregister(this);
				registered = false;
			}
		} else {
			ensureRegistered();
		}
		applyBreakerToShape();
	}

	/**
	 * Mirror the switch position into the blockstate and re-derive this segment's six connection flags
	 * (MOD-276), so an open breaker leaves a real gap in the run rather than a wire that merely looks
	 * whole while carrying nothing.
	 *
	 * <p>Both halves are needed. The {@code BREAKER_OPEN} flag is what the <em>neighbours</em> read
	 * through {@code isCableConnectable}, and writing it with {@code UPDATE_ALL} is what makes them
	 * re-run {@code updateShape} and retract their own arms. This segment's own flags are not covered
	 * by that — {@code updateShape} fires on the neighbours, not on the block that changed — so they
	 * are recomputed here: all-false while open, re-derived from the world when closed again.
	 */
	private void applyBreakerToShape() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockState current = serverLevel.getBlockState(worldPosition);
		if (!(current.getBlock() instanceof CableBlock)) {
			return;
		}
		boolean open = isBreakerOpen();
		BlockState updated = current.setValue(CableBlock.BREAKER_OPEN, open);
		for (Direction dir : Direction.values()) {
			BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			boolean connected = !open && CableBlock.shouldConnectTo(serverLevel, worldPosition, dir);
			updated = updated.setValue(prop, connected);
		}
		if (updated != current) {
			serverLevel.setBlock(worldPosition, updated, Block.UPDATE_ALL);
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
		// An open breaker keeps every arm retracted (MOD-276) — this migration re-derives connections
		// from the neighbours, which are perfectly connectable, so without the gate it would helpfully
		// reconnect the gap the switch is supposed to leave, on the first tick after every world load.
		boolean open = isBreakerOpen();
		if (corrected.getValue(CableBlock.BREAKER_OPEN) != open) {
			corrected = corrected.setValue(CableBlock.BREAKER_OPEN, open);
			changed = true;
		}
		// Six connection flags (one per face): re-derive from the live connectsTo contract.
		for (Direction dir : Direction.values()) {
			BooleanProperty prop = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			boolean expected = !open && CableBlock.shouldConnectTo(serverLevel, pos, dir);
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
