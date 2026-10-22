package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.block.entity.machine.TownBinding;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.misc.ConstructionDroneItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.ConstructionDroneWork;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.DroneNestRules.Status;
import dev.alaindustrial.settlement.drone.TownDroneRoster;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.SettlementGate;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A builder drone's nest on the town hall's roof (MOD-858; epic MOD-824, decisions R82, R87, R91, R94): a grate on
 * legs with the drone docked on it. The drone is not an entity — it is this block entity's state, drawn by its
 * renderer, as the garden drone is (MOD-277): it cannot be lost, killed, doubled or stranded in an unloaded chunk.
 *
 * <p><b>When it works.</b> In its town ({@link TownBinding}), standing in the town hall ({@link TownHall#contains}), as
 * one of the town's first nests up to the core's tier's limit ({@link DroneNestRules#nestLimit}, R91), with a working
 * dispatcher and a lit core. Anything short of that is its status, which a right-click shows over the hotbar.
 *
 * <p><b>Energy (R94).</b> The drone's battery is this block entity's buffer; no cable reaches it. While the drone is
 * docked the nest charges it from the core's spare EU ({@link SettlementCoreBlockEntity#giveToDrones}). The battery
 * pays the flight at take-off, out and back; the core pays every block placed or taken away, the battery while the
 * core cannot. A drone takes off only with the round trip and one block in its battery, so a core that goes dark
 * mid-flight lets it finish the block and come home; then its job waits with «no energy».
 *
 * <p><b>The work</b> — which job, which cell, what to do there — is the {@link ConstructionDroneWork} component; the
 * flight is the {@link DroneFlight} component the garden drone shares. A flight is synced once per leg; the client
 * works out where the drone is from its own clock.
 */
public final class DroneNestBlockEntity extends MachineBlockEntity {

	private final TownBinding binding = new TownBinding();
	private final DroneFlight flight = new DroneFlight();
	private final ConstructionDroneWork work = new ConstructionDroneWork();
	private Status status = Status.NO_DRONE;
	/** Whether a builder drone is docked in the nest (owner's round 1: the drone is its own item). Saved. */
	private boolean hasDrone;
	/** Whether the town's lit core charged the drone on the last look. Not saved: the next tick knows again. */
	private boolean coreCharging;
	/** Ticks the drone stays docked after it came home — the time the nest charges it in. Saved. */
	private int docked;

	public DroneNestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.DRONE_NEST_BE.get(), pos, state, EnergyTier.LV, 0, DroneNestRules.batteryCapacity(), 0L, 0L);
	}

	/** The id the nest takes jobs and cells under: {@code nest:x,y,z}. */
	public String taker() {
		return "nest:" + worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ();
	}

	public Status status() {
		return status;
	}

	/** EU in the drone's battery. */
	public long battery() {
		return energy.getAmount();
	}

	/** Sets the battery: a game test's charge or drain. */
	public void setBattery(long eu) {
		energy.setAmountUntracked(Math.max(0L, Math.min(energy.getCapacity(), eu)));
	}

	public DroneFlight flight() {
		return flight;
	}

	public ConstructionDroneWork work() {
		return work;
	}

	public int settlementId() {
		return binding.id();
	}

	/** A game test's answer to «is this cell's chunk loaded»; see {@link ConstructionDroneWork#useLoadedProbe}. */
	public void useLoadedProbe(@Nullable Predicate<BlockPos> probe) {
		work.useLoadedProbe(probe);
	}

	/**
	 * Server side, on a right-click (R98): whether {@code player} may use or read the nest at all. A stranger to its
	 * town hears why not in chat and gets nothing: no drone in or out, no status line.
	 */
	public boolean admitOrTell(Player player) {
		return SettlementGate.admitOrTell(binding.resolveNow(level, worldPosition, this::setChanged), player);
	}

	/** Whether {@code player} breaks this block: the town's owner, or anyone while the town has none. */
	public boolean mayBeBrokenBy(Player player) {
		return binding.mayControl(level, player);
	}

	/** Whether a builder drone is docked in the nest. */
	public boolean hasDrone() {
		return hasDrone;
	}

	/**
	 * Puts a builder drone with {@code charge} EU into the empty nest. False — nothing changes — when a drone is
	 * already docked.
	 */
	public boolean insertDrone(long charge) {
		if (hasDrone) {
			return false;
		}
		hasDrone = true;
		energy.setAmountUntracked(Math.max(0L, Math.min(energy.getCapacity(), charge)));
		status = Status.IDLE;
		docked = 0;
		wake();
		setChanged();
		syncBlockEntityToClient();
		return true;
	}

	/**
	 * Takes the docked drone out: returns its charge, or -1 when there is no drone or it is in the air. The nest gives
	 * its job back to the queue and leaves the roster.
	 */
	public long removeDrone() {
		if (!hasDrone || flight.flying()) {
			return -1L;
		}
		long charge = energy.getAmount();
		hasDrone = false;
		energy.setAmountUntracked(0L);
		leaveTown();
		status = Status.NO_DRONE;
		wake();
		setChanged();
		syncBlockEntityToClient();
		return charge;
	}

	/**
	 * The line a right-click shows over the hotbar: what the nest does, and for a docked drone its charge and where the
	 * charge comes from — the settlement core, and nothing else (R94).
	 */
	public Component statusLine() {
		if (!hasDrone) {
			return Component.translatable("settlement.alaindustrial.drone_nest.no_drone_hint");
		}
		Component charge = Component.translatable(coreCharging ? "settlement.alaindustrial.drone_nest.charge_core"
				: "settlement.alaindustrial.drone_nest.charge_none",
				Math.round(100.0 * energy.getAmount() / Math.max(1L, energy.getCapacity())));
		return Component.translatable("settlement.alaindustrial.drone_nest.line", Component.translatable(status.key()),
				charge);
	}

	/** Gives the job back and leaves the roster: the nest stopped being one of its town's working nests. */
	private void leaveTown() {
		if (level instanceof ServerLevel serverLevel && binding.id() > 0) {
			work.giveBack(serverLevel, binding.id(), taker());
			roster(serverLevel).forget(binding.id(), worldPosition.asLong());
			roster(serverLevel).release(taker());
		}
	}

	@Override
	public boolean tracksOwner() {
		return false;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.NONE;
	}

	@Override
	public long perTickDemand() {
		return 0L;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return IDLE_SLEEP_TICKS;
		}
		long now = serverLevel.getGameTime();
		Optional<Settlement> town = binding.resolve(serverLevel, pos, this::setChanged);
		if (binding.ownerChanged(town)) {
			syncBlockEntityToClient();
		}
		Optional<SettlementCoreBlockEntity> core = town.flatMap(t -> litCore(serverLevel, t));
		Optional<TownDispatcherBlockEntity> dispatcher = town.flatMap(t -> TownDispatcherBlockEntity.of(serverLevel,
				t.id()));
		Optional<ConstructionDroneWork.Context> ctx = town.flatMap(t -> dispatcher.map(d ->
				new ConstructionDroneWork.Context(serverLevel, t.id(), core, d, taker(), pos, now)));
		if (flight.flying()) {
			fly(serverLevel, town, ctx, now);
			return 0;
		}
		Status before = status;
		int sleep = docked(serverLevel, town, core, ctx, now);
		if (status != before) {
			setChanged();
			syncBlockEntityToClient();
		}
		return sleep;
	}

	/** One tick in the air: the leg counts down; on arrival the drone acts, at home it docks. */
	private void fly(ServerLevel level, Optional<Settlement> town, Optional<ConstructionDroneWork.Context> ctx,
			long now) {
		town.ifPresent(t -> roster(level).nestCheckIn(t.id(), worldPosition.asLong(), now, true));
		DroneFlight.Phase phase = flight.tick();
		if (phase == DroneFlight.Phase.FLYING) {
			return;
		}
		if (phase == DroneFlight.Phase.ARRIVED) {
			ctx.ifPresent(c -> work.arrive(c, energy::drainInternal));
			flight.turnBack(now, DroneNestRules.LANDING_PAUSE_TICKS);
		} else {
			flight.park();
			roster(level).release(taker());
			docked = DroneNestRules.DOCK_TICKS;
		}
		setChanged();
		syncBlockEntityToClient();
	}

	/** One tick docked: charge, check what the nest may do, and send the drone when it may. Returns the sleep. */
	private int docked(ServerLevel level, Optional<Settlement> town, Optional<SettlementCoreBlockEntity> core,
			Optional<ConstructionDroneWork.Context> ctx, long now) {
		coreCharging = hasDrone && core.isPresent();
		long charged = hasDrone ? charge(core) : 0L;
		if (town.isEmpty()) {
			status = hasDrone ? Status.NO_TOWN : Status.NO_DRONE;
			return IDLE_SLEEP_TICKS;
		}
		int id = town.get().id();
		Status before = placement(level, town.get());
		if (before != null) {
			leaveTown();
			status = before;
			return TownDispatcherBlockEntity.TICK_INTERVAL;
		}
		TownDroneRoster roster = roster(level);
		int rank = roster.nestCheckIn(id, worldPosition.asLong(), now,
				level.getServer() != null && holdsJob(level, id));
		Status refusal = refusal(town.get(), rank, core, ctx, TownHall.restored(level, id));
		if (refusal != null) {
			if (refusal == Status.CORE_DARK) {
				work.blockForEnergy(level, id, taker());
			} else if (refusal == Status.NO_DISPATCHER) {
				// No dispatcher, no plan (owner's round 2): the job stands «no plan» rather than going back unheld.
				work.blockForNoPlan(level, id, taker());
			} else {
				work.giveBack(level, id, taker());
			}
			status = refusal;
			return TownDispatcherBlockEntity.TICK_INTERVAL;
		}
		if (docked > 0) {
			docked--;
			return 0;
		}
		ConstructionDroneWork.Step step = work.next(ctx.get(), flight, energy.getAmount(),
				blocks -> energy.drainInternal(DroneNestRules.flightCost(blocks)));
		if (step.status() == Status.NO_ENERGY && charged == 0) {
			work.blockForEnergy(level, id, taker());
		}
		if (step.status() != null) {
			status = step.status();
		}
		if (flight.flying()) {
			setChanged();
			syncBlockEntityToClient();
		}
		return step.sleep();
	}

	/**
	 * Why the nest is not one of its town's nests at all, or null: it stands nowhere over the hall, or it holds no
	 * drone. Such a nest takes no rank — only a nest with a drone over the hall counts against the tier's limit.
	 */
	private @Nullable Status placement(ServerLevel level, Settlement town) {
		if (!TownHall.over(level, town.id(), worldPosition)) {
			return Status.OUTSIDE_HALL;
		}
		return hasDrone ? null : Status.NO_DRONE;
	}

	/** Why the counted nest may not work, or null when it may. */
	private @Nullable Status refusal(Settlement town, int rank, Optional<SettlementCoreBlockEntity> core,
			Optional<ConstructionDroneWork.Context> ctx, boolean hallRestored) {
		if (!DroneNestRules.works(rank, DroneNestRules.nestLimit(town.tier(), hallRestored))) {
			return Status.EXTRA;
		}
		if (ctx.isEmpty()) {
			return Status.NO_DISPATCHER;
		}
		if (core.isEmpty()) {
			return Status.CORE_DARK;
		}
		return null;
	}

	/** Charges the docked drone from the lit core's spare EU; returns what it took. */
	private long charge(Optional<SettlementCoreBlockEntity> core) {
		long wanted = DroneNestRules.chargeWanted(energy.getAmount());
		if (wanted <= 0 || core.isEmpty()) {
			return 0L;
		}
		long given = core.get().giveToDrones(wanted);
		if (given > 0) {
			energy.receiveInternal(given);
			setChanged();
		}
		return given;
	}

	/** The town's core, if it stands loaded and lit. */
	private static Optional<SettlementCoreBlockEntity> litCore(ServerLevel level, Settlement town) {
		BlockPos pos = town.corePos();
		if (!town.active() || !town.corePlaced() || !town.dimension().equals(level.dimension())
				|| !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SettlementCoreBlockEntity core)
				|| !core.isActive()) {
			return Optional.empty();
		}
		return Optional.of(core);
	}

	private boolean holdsJob(ServerLevel level, int town) {
		return SettlementRegistry.get(level.getServer()).works().find(town)
				.flatMap(works -> works.queue().heldBy(taker())).isPresent();
	}

	private static TownDroneRoster roster(ServerLevel level) {
		return SettlementRegistry.get(level.getServer()).drones();
	}

	/**
	 * A broken nest gives its job back to the queue and its cell to the roster, and drops its drone as an item with
	 * the drone's charge (every way of removal).
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		leaveTown();
		if (hasDrone && level != null && !level.isClientSide()) {
			Block.popResource(level, pos, ConstructionDroneItem.withCharge(energy.getAmount()));
			hasDrone = false;
		}
	}

	// --- client: what the renderer reads ---

	/** The cell the drone flies to, or {@code null} while docked. */
	public @Nullable BlockPos droneTarget() {
		return flight.target();
	}

	public float flightProgress(float gameTimeWithPartials) {
		return flight.progress(gameTimeWithPartials);
	}

	public boolean isReturning() {
		return flight.returning();
	}

	/**
	 * The corners of the leg in the air, world cells as x, y, z triplets from the nest to the goal: the route the
	 * server found (owner's round 2), or — for a leg saved before routes existed — the old way over: up to the cruise
	 * height, across, down onto the target. Empty while docked.
	 */
	public int[] routeCorners() {
		BlockPos target = flight.target();
		if (target == null) {
			return new int[0];
		}
		int[] route = flight.path();
		if (route.length >= 6) {
			return route;
		}
		BlockPos nest = worldPosition;
		int cruise = DroneNestRules.cruiseY(nest.getY(), target.getY());
		return new int[] {nest.getX(), nest.getY(), nest.getZ(), nest.getX(), cruise, nest.getZ(), target.getX(),
				cruise, target.getZ(), target.getX(), target.getY() + 1, target.getZ()};
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		binding.save(output);
		flight.save(output);
		work.save(output);
		output.putInt("Status", status.ordinal());
		output.putInt("Docked", docked);
		output.putBoolean("HasDrone", hasDrone);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		binding.load(input);
		flight.load(input);
		work.load(input);
		status = Status.byOrdinal(input.getIntOr("Status", 0));
		docked = Math.max(0, input.getIntOr("Docked", 0));
		hasDrone = input.getBooleanOr("HasDrone", false);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		binding.writeUpdate(tag, level);
		return tag;
	}
}
