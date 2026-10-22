package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkQueue;
import dev.alaindustrial.core.drone.DroneRoute;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The work of one tier-1 builder drone (MOD-858; epic MOD-824, decisions R85, R91, R93, R94): which job it holds, which
 * cell it flies to next and what it does there. One field of the drone nest's block entity (ADR-042); the nest owns
 * the flight and the battery, this component the job.
 *
 * <p><b>The job.</b> The drone takes one job of the town's work queue — a clearing or a building of one building
 * (R91: one building, one drone) — and keeps it till it is done. The cells come from the building's plan
 * ({@link BuildingPlan}), bottom layer first; what to do with a cell is {@link DroneCellRules}' answer on what
 * {@link DroneCellSurvey} sees. A building job builds stage by stage ({@link BuildStageRules}, MOD-860): it starts or
 * goes on with the current stage only while the dispatcher's buffer holds everything that stage still needs, and
 * waits naming what is missing otherwise.
 *
 * <p><b>The ring.</b> A clearing, once the building's own cells are clear, takes the terrain and rubble out of the
 * ring around its footprint ({@link BuildingPlan#ringSize()}, owner's round 4) — its cells numbered after the plan's
 * — so the walls stand free.
 *
 * <p><b>One flight, one block.</b> {@link #next} picks a cell and sends the drone; {@link #arrive} looks at the cell
 * again — the world may have changed in flight — and acts: takes the block away or puts the plan's block. The item
 * leaves the buffer at that moment ({@link BuildStageRules#consumeOnPlace}), and the core pays the block's price; the
 * battery pays what the core cannot (a dark core, R94).
 *
 * <p><b>Why a job stops.</b> A block the drone may not touch where the plan wants something else — «obstructed by X»;
 * a cell in a chunk nobody keeps loaded — «chunk not loaded» (never loaded for the drone's sake); no plan in the
 * dispatcher — «no plan»; a short buffer — «no materials: …»; no energy — «no energy». A stopped job is blocked in
 * the queue with its reason, and a drone with nothing queued to do tries it again every
 * {@link DroneNestRules#RETRY_TICKS} ticks — whichever drone stopped it. A cell with
 * a creature in it is passed and tried again later; it stops nothing. Two drones never take one cell
 * ({@link TownDroneRoster#reserve}).
 */
public final class ConstructionDroneWork {

	/** The job kinds a tier-1 drone takes (R87: the class «town hall»; the queue holds nothing else yet). */
	public static final Set<WorkJobType> TAKES = Set.of(WorkJobType.CLEAR, WorkJobType.BUILD);

	private static final int NONE = 0;
	private static final int REMOVE = 1;
	private static final int PLACE = 2;
	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** What the nest needs to know this tick. */
	public record Context(ServerLevel level, int town, Optional<SettlementCoreBlockEntity> core,
			TownDispatcherBlockEntity dispatcher, String taker, BlockPos nest, long now) {
	}

	/** What a docked step came to: the nest's status ({@code null}: as it was), and the ticks it may sleep. */
	public record Step(DroneNestRules.@Nullable Status status, int sleep) {
	}

	/** The plan cell the drone is flying to, or -1. Saved. */
	private int cell = -1;
	/** What it is going to do there ({@link #REMOVE}, {@link #PLACE}), decided at take-off and checked again. Saved. */
	private int action = NONE;
	/** The job it last had to stop, and the game time it tries it again. Saved. */
	private int lastBlocked;
	private long retryAt;
	/** Cells with a creature in them, by plan index, and the game time they may be tried again. Not saved. */
	private final Map<Integer, Long> skipped = new HashMap<>();
	/** Cells no route reaches, by plan index (within {@link #skipped}): the nest says «no way there». Not saved. */
	private final Set<Integer> unreachable = new HashSet<>();
	/** The route search in progress, spread over ticks, and the cell it is for. Not saved: a reload searches again. */
	private DroneRoute.@Nullable Planner planner;
	private @Nullable BlockPos planning;
	/** Whether a cell lies in a loaded chunk: the level's answer, or a game test's. Not saved. */
	private @Nullable Predicate<BlockPos> loadedProbe;

	/** A game test's own answer to «is this cell's chunk loaded» (an unloaded chunk cannot be made on demand). */
	public void useLoadedProbe(@Nullable Predicate<BlockPos> probe) {
		this.loadedProbe = probe;
	}

	/** The plan cell the drone is busy with, or -1. */
	public int cell() {
		return cell;
	}

	/**
	 * One docked step: holds or takes a job, picks its next cell and sends the drone — or says why it does not. The
	 * caller has checked the town, the hall, the nest's rank, the dispatcher and the core.
	 */
	public Step next(Context ctx, DroneFlight flight, long battery, IntConsumer spendFlight) {
		WorkQueue queue = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town()).queue();
		Optional<WorkJob> job = takeJob(queue, ctx.taker(), ctx.now());
		if (job.isEmpty()) {
			return new Step(DroneNestRules.Status.IDLE, DroneNestRules.RETRY_TICKS / 5);
		}
		WorkJob held = job.get();
		Optional<BuildingPlan> plan = TownWorksScanner.plan(ctx.level(), ctx.town(), held.building());
		if (plan.isEmpty() || !planInDispatcher(ctx, held.building())) {
			block(queue, held, ctx.taker(), BlockReason.of(BlockReason.NO_PLAN), ctx.now());
			return new Step(DroneNestRules.Status.NO_PLAN, DroneNestRules.DOCK_TICKS);
		}
		Optional<BuildStage> stage = Optional.empty();
		Optional<BuildMaterials.StagedPlan> staged = Optional.empty();
		if (held.type() == WorkJobType.BUILD) {
			staged = BuildMaterials.forBuilding(ctx.level(), ctx.town(), held.building());
			Optional<BuildStageRules.Progress> progress = staged.flatMap(s -> s.progress(ctx.level()));
			if (staged.isEmpty() || progress.isEmpty()) {
				return block(queue, held, ctx, BlockReason.of(BlockReason.CHUNK_NOT_LOADED));
			}
			BuildStageRules.Decision decision = BuildStageRules.decide(progress.get(), ctx.dispatcher().buffer());
			if (decision.verdict() == BuildStageRules.Verdict.WAIT) {
				decision.missing().forEach(need -> ctx.dispatcher().journal().missing(ctx.now(),
						BuildMaterials.describe(need.item()), need.count()));
				return block(queue, held, ctx, BuildMaterials.reason(decision.missing()));
			}
			stage = decision.stage();
		}
		return walk(ctx, queue, held, plan.get(), staged, stage, flight, battery, spendFlight);
	}

	/** The cells, bottom up: the first one with something to do gets the drone. */
	private Step walk(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan,
			Optional<BuildMaterials.StagedPlan> staged, Optional<BuildStage> stage, DroneFlight flight, long battery,
			IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		Job job = held.type() == WorkJobType.CLEAR ? Job.CLEAR : Job.BUILD;
		boolean pending = false;
		for (int i = 0; i < plan.size(); i++) {
			BlockPos pos = plan.pos(i);
			if (skipped.getOrDefault(i, Long.MIN_VALUE) > ctx.now()) {
				pending = true;
				continue;
			}
			if (!loaded(ctx.level(), pos)) {
				return block(queue, held, ctx, BlockReason.of(BlockReason.CHUNK_NOT_LOADED));
			}
			if (staged.isPresent() && staged.get().counted(i) && !staged.get().stage(i).equals(stage)) {
				continue;
			}
			BlockState world = ctx.level().getBlockState(pos);
			Action decided = DroneCellRules.decide(job, DroneCellSurvey.survey(plan, i, world));
			if (decided == Action.NONE) {
				continue;
			}
			if (decided == Action.OBSTRUCTED) {
				return block(queue, held, ctx, BlockReason.obstructed(world.getBlock().getDescriptionId()));
			}
			String holder = roster.holder(pos.asLong());
			if (holder != null && !holder.equals(ctx.taker()) || creatureIn(ctx.level(), pos)) {
				skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
				pending = true;
				continue;
			}
			return takeOff(ctx, queue, held, plan, i, pos, decided, flight, battery, spendFlight);
		}
		if (job == Job.CLEAR) {
			Optional<Step> ring = walkRing(ctx, queue, held, plan, flight, battery, spendFlight);
			if (ring.isPresent()) {
				return ring.get();
			}
			pending |= ringPending;
		}
		if (pending) {
			boolean noRoute = unreachable.stream().anyMatch(i -> skipped.getOrDefault(i, Long.MIN_VALUE) > ctx.now());
			return new Step(noRoute ? DroneNestRules.Status.NO_ROUTE : DroneNestRules.Status.WAITING,
					DroneNestRules.DOCK_TICKS);
		}
		queue.complete(held.id(), ctx.taker());
		TownWorksScanner.measureNow(ctx.level(), ctx.town(), held.building());
		return new Step(DroneNestRules.Status.IDLE, DroneNestRules.DOCK_TICKS);
	}

	/** Set by {@link #walkRing}: a ring cell was passed for now and will be tried again. */
	private boolean ringPending;

	/**
	 * The ring around the building, once its own cells are clear (owner's round 4): terrain and rubble that touch the
	 * walls go, so the building stands free. The step that sends the drone or stops the job, or empty when the ring
	 * holds nothing to do now ({@link #ringPending} says whether a cell waits).
	 */
	private Optional<Step> walkRing(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan, DroneFlight flight,
			long battery, IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		ringPending = false;
		for (int r = 0; r < plan.ringSize(); r++) {
			int i = plan.size() + r;
			BlockPos pos = plan.ringPos(r);
			if (skipped.getOrDefault(i, Long.MIN_VALUE) > ctx.now()) {
				ringPending = true;
				continue;
			}
			if (!loaded(ctx.level(), pos)) {
				return Optional.of(block(queue, held, ctx, BlockReason.of(BlockReason.CHUNK_NOT_LOADED)));
			}
			if (!DroneCellSurvey.ringClearable(ctx.level().getBlockState(pos))) {
				continue;
			}
			String holder = roster.holder(pos.asLong());
			if (holder != null && !holder.equals(ctx.taker()) || creatureIn(ctx.level(), pos)) {
				skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
				ringPending = true;
				continue;
			}
			return Optional.of(takeOff(ctx, queue, held, plan, i, pos, Action.REMOVE, flight, battery, spendFlight));
		}
		return Optional.empty();
	}

	/**
	 * The drone stands on its cell: looks again and acts — takes the block away, or puts the plan's block and takes its
	 * item out of the buffer in the same tick. Pays the block's price: the core, or the battery while the core cannot.
	 * A cell that turned out right, taken, or not the drone's any more is left as it is.
	 */
	public void arrive(Context ctx, BatteryPay battery) {
		int index = cell;
		int planned = action;
		cell = -1;
		action = NONE;
		WorkQueue queue = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town()).queue();
		Optional<WorkJob> held = queue.heldBy(ctx.taker());
		if (held.isEmpty() || index < 0 || planned == NONE || !planInDispatcher(ctx, held.get().building())) {
			// No plan in the dispatcher any more (owner's round 2): the drone does nothing here and flies home; the
			// next docked step blocks the job «no plan». Nothing has left the buffer — that happens only on placing.
			return;
		}
		Optional<BuildingPlan> plan = TownWorksScanner.plan(ctx.level(), ctx.town(), held.get().building());
		if (plan.isEmpty() || index >= plan.get().size() + plan.get().ringSize()
				|| !loaded(ctx.level(), plan.get().target(index))) {
			return;
		}
		if (index >= plan.get().size()) {
			arriveInRing(ctx, battery, plan.get().target(index), index);
			return;
		}
		BlockPos pos = plan.get().pos(index);
		BlockState world = ctx.level().getBlockState(pos);
		DroneCellRules.Cell seen = DroneCellSurvey.survey(plan.get(), index, world);
		Job job = held.get().type() == WorkJobType.CLEAR ? Job.CLEAR : Job.BUILD;
		Action now = DroneCellRules.decide(job, seen);
		if (creatureIn(ctx.level(), pos)) {
			skipped.put(index, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			return;
		}
		if (now == Action.REMOVE) {
			pay(ctx, battery, 1);
			ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
			ctx.dispatcher().journal().cleared(ctx.now(), world.getBlock().getDescriptionId());
		} else if (now == Action.PLACE) {
			place(ctx, battery, held.get(), plan.get(), index, world, seen);
		}
	}

	/** The drone stands on a ring cell: takes its terrain away if it is still there, the core paying. */
	private void arriveInRing(Context ctx, BatteryPay battery, BlockPos pos, int index) {
		BlockState world = ctx.level().getBlockState(pos);
		if (creatureIn(ctx.level(), pos)) {
			skipped.put(index, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			return;
		}
		if (DroneCellSurvey.ringClearable(world)) {
			pay(ctx, battery, 1);
			ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
			ctx.dispatcher().journal().cleared(ctx.now(), world.getBlock().getDescriptionId());
		}
	}

	/** Puts cell {@code index}'s block, if the stage rules let it and the buffer still holds its item. */
	private void place(Context ctx, BatteryPay battery, WorkJob held, BuildingPlan plan, int index, BlockState world,
			DroneCellRules.Cell seen) {
		Optional<BuildMaterials.StagedPlan> staged = BuildMaterials.forBuilding(ctx.level(), ctx.town(),
				held.building());
		Optional<BuildStageRules.Cell> rules = staged.flatMap(s -> s.cell(index, world));
		Optional<BuildStageRules.Progress> progress = staged.flatMap(s -> s.progress(ctx.level()));
		DispatcherBuffer buffer = ctx.dispatcher().buffer();
		if (rules.isPresent() && (progress.isEmpty() || !BuildStageRules.mayPlace(progress.get(), buffer, rules.get())
				|| !BuildStageRules.consumeOnPlace(buffer, rules.get()))) {
			return;
		}
		pay(ctx, battery, DroneCellRules.placementRemoves(seen) ? 2 : 1);
		BlockState want = plan.expected(index);
		ctx.level().setBlock(plan.pos(index), want, PLACE_FLAGS);
		ctx.dispatcher().journal().placed(ctx.now(), want.getBlock().getDescriptionId());
		if (rules.isPresent() && !held.repair()) {
			// A repair's stages are no news (owner's round 4): the hall says «damaged» and «mended» instead.
			BuildStage stage = rules.get().stage();
			Optional<BuildStageRules.Progress> after = staged.flatMap(s -> s.progress(ctx.level()));
			boolean closed = after.flatMap(p -> p.stages().stream().filter(s -> s.stage() == stage).findFirst())
					.filter(BuildStageRules.StageProgress::done).isPresent();
			if (closed) {
				ctx.dispatcher().journal().stageDone(ctx.now(), stage);
				HallBuildNews.stageDone(ctx.level(), ctx.town(), held.building(), stage, after.get(), buffer);
			}
		}
	}

	/** Pays {@code blocks} blocks' price: the core first, the battery for the rest (R94). */
	private static void pay(Context ctx, BatteryPay battery, int blocks) {
		long price = DroneNestRules.blockPrice() * blocks;
		long fromCore = ctx.core().map(core -> core.giveToDrones(price)).orElse(0L);
		battery.spend(price - fromCore);
	}

	/**
	 * Sends the drone to cell {@code i} at {@code pos} along a route through the air — once its search is done (a few
	 * cells a tick), and only with the round trip in the battery. A cell no route reaches is passed and tried later.
	 */
	private Step takeOff(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan, int i, BlockPos pos,
			Action decided, DroneFlight flight, long battery, IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		Optional<DroneRoute> planned = route(ctx, pos, plan);
		if (planned.isEmpty()) {
			// The route is still being searched (a few cells a tick), or the town's path budget is spent this tick.
			return new Step(null, 1);
		}
		DroneRoute route = planned.get();
		if (!route.found()) {
			skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			unreachable.add(i);
			return new Step(null, 1);
		}
		unreachable.remove(i);
		int path = DroneNestRules.routeBlocks(route.length());
		if (!DroneNestRules.canTakeOff(battery, path)) {
			return new Step(DroneNestRules.Status.NO_ENERGY, DroneNestRules.DOCK_TICKS);
		}
		roster.reserve(pos.asLong(), ctx.taker());
		flight.launch(pos, route.points(), DroneNestRules.flightTicks(path), ctx.now());
		spendFlight.accept(path);
		cell = i;
		action = decided == Action.REMOVE ? REMOVE : PLACE;
		queue.progress(held.id(), ctx.taker(), i);
		return new Step(DroneNestRules.Status.WORKING, 0);
	}

	/**
	 * The route to {@code pos} once its search is done, or empty while it goes on. A new search asks the town's path
	 * budget first (ADR-050 point 11) and then runs {@link DroneAirspace#NODES_PER_TICK} cells a tick; a search for
	 * another cell is dropped. Its box reaches over the whole building (owner's round 3): the way to the fittings
	 * inside is the door, wherever it is.
	 */
	private Optional<DroneRoute> route(Context ctx, BlockPos pos, BuildingPlan plan) {
		if (planner == null || !pos.equals(planning)) {
			planner = null;
			if (!ShiftWorkers.tryPlanPath(ctx.level(), ctx.town())) {
				return Optional.empty();
			}
			planner = DroneAirspace.plan(ctx.level(), ctx.nest(), pos, loadedProbe, plan.box().orElse(null));
			planning = pos.immutable();
		}
		if (!planner.advance(DroneAirspace.NODES_PER_TICK)) {
			return Optional.empty();
		}
		DroneRoute done = planner.result();
		planner = null;
		planning = null;
		return Optional.of(done);
	}

	/** The battery the drone pays from when the core cannot. */
	@FunctionalInterface
	public interface BatteryPay {
		void spend(long eu);
	}

	/**
	 * The job the drone holds; else the oldest queued one it may take; else — every {@link DroneNestRules#RETRY_TICKS}
	 * ticks — the one it last had to stop, or any stopped one it may take (owner's round 3: a job stopped «no
	 * materials» stayed stopped for good once a clearing of the same building went first, because only the job this
	 * drone remembered was ever tried again, and that memory was dropped when the clearing kept it busy).
	 */
	private Optional<WorkJob> takeJob(WorkQueue queue, String taker, long now) {
		Optional<WorkJob> held = queue.heldBy(taker);
		if (held.isPresent()) {
			return held;
		}
		Optional<WorkJob> queued = queue.takeNext(taker, TAKES);
		if (queued.isPresent() || now < retryAt) {
			return queued;
		}
		if (lastBlocked > 0) {
			int id = lastBlocked;
			lastBlocked = 0;
			if (queue.take(id, taker) == WorkQueue.Outcome.OK) {
				return queue.job(id);
			}
		}
		return queue.takeBlocked(taker, TAKES);
	}

	/** Blocks {@code job} with {@code reason}; the drone tries it again after {@link DroneNestRules#RETRY_TICKS}. */
	private Step block(WorkQueue queue, WorkJob job, Context ctx, BlockReason reason) {
		block(queue, job, ctx.taker(), reason, ctx.now());
		return new Step(DroneNestRules.Status.WAITING, DroneNestRules.DOCK_TICKS);
	}

	private void block(WorkQueue queue, WorkJob job, String taker, BlockReason reason, long now) {
		queue.block(job.id(), taker, reason, job.progress());
		lastBlocked = job.id();
		retryAt = now + DroneNestRules.RETRY_TICKS;
	}

	/** Blocks the held job for want of energy — the core went dark, or the battery is empty and nothing charges it. */
	public void blockForEnergy(ServerLevel level, int town, String taker) {
		WorkQueue queue = SettlementRegistry.get(level.getServer()).works().town(town).queue();
		queue.heldBy(taker).ifPresent(job -> block(queue, job, taker, BlockReason.of(BlockReason.NO_ENERGY),
				level.getGameTime()));
	}

	/**
	 * Blocks the held job «no plan» — the town's dispatcher is gone, so nobody holds the plan (owner's round 2): the
	 * drones stand till a dispatcher with the plan is back.
	 */
	public void blockForNoPlan(ServerLevel level, int town, String taker) {
		WorkQueue queue = SettlementRegistry.get(level.getServer()).works().town(town).queue();
		queue.heldBy(taker).ifPresent(job -> block(queue, job, taker, BlockReason.of(BlockReason.NO_PLAN),
				level.getGameTime()));
	}

	/** The nest stopped serving: its job goes back to the queue, its cell to the roster. */
	public void giveBack(ServerLevel level, int town, String taker) {
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		registry.works().find(town).ifPresent(works -> works.queue().releaseTaker(taker));
		registry.drones().release(taker);
		cell = -1;
		action = NONE;
		planner = null;
		planning = null;
	}

	/** Whether the building's restoration plan lies in the dispatcher (MOD-859): no plan, no building. */
	private static boolean planInDispatcher(Context ctx, int building) {
		TownWorks works = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town());
		Optional<TownBuilding> found = works.building(building);
		Optional<RestorationPlan> plan = ctx.dispatcher().plan();
		return found.isPresent() && found.get().intact().isPresent() && plan.isPresent()
				&& plan.get().template().equals(found.get().intact().get());
	}

	private boolean loaded(ServerLevel level, BlockPos pos) {
		return loadedProbe != null ? loadedProbe.test(pos) : level.isLoaded(pos);
	}

	private static boolean creatureIn(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
	}

	public void save(ValueOutput output) {
		output.putInt("DroneCell", cell);
		output.putInt("DroneAction", action);
		output.putInt("LastBlocked", lastBlocked);
		output.putLong("RetryAt", retryAt);
	}

	public void load(ValueInput input) {
		cell = input.getIntOr("DroneCell", -1);
		action = input.getIntOr("DroneAction", NONE);
		lastBlocked = input.getIntOr("LastBlocked", 0);
		retryAt = input.getLongOr("RetryAt", 0L);
	}
}
