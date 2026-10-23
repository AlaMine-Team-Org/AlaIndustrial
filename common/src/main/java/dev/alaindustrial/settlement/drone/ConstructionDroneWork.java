package dev.alaindustrial.settlement.drone;

import static dev.alaindustrial.settlement.drone.DroneCellWork.comesWithItsPair;
import static dev.alaindustrial.settlement.drone.DroneCellWork.creatureIn;
import static dev.alaindustrial.settlement.drone.DroneCellWork.cleared;
import static dev.alaindustrial.settlement.drone.DroneCellWork.foundationShortage;
import static dev.alaindustrial.settlement.drone.DroneCellWork.pay;
import static dev.alaindustrial.settlement.drone.DroneCellWork.roadShortage;
import static dev.alaindustrial.settlement.drone.DroneCellWork.takeAway;
import static dev.alaindustrial.settlement.drone.DroneCellWork.takeOtherHalf;
import static dev.alaindustrial.settlement.drone.DroneCellWork.unsupportedFalling;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.machine.DroneFlight;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.HousePlans;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHouses;
import dev.alaindustrial.settlement.works.TownHousing;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkOrders;
import dev.alaindustrial.settlement.works.WorkQueue;
import dev.alaindustrial.core.drone.DroneRoute;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

// size-justified: one drone's whole job loop — taking a job, walking a building's cells or a road's, the ring and a
// house's foundation, the flight, arriving and paying; the rules live in DroneCellRules, BuildStageRules and RoadWork.
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
 * — so the walls stand free. Under a house a player laid (MOD-877, R109) the clearing then fills the pits under the
 * floor from the buffer ({@link BuildingPlan#fillSize()}), numbered after the ring; a house's building job does the
 * same before its first block (owner's round 3 of 0.1.219, R3-5), so a restored ruin stands on earth too.
 *
 * <p><b>One flight, one block.</b> {@link #next} picks a cell and sends the drone; {@link #arrive} looks at the cell
 * again — the world may have changed in flight — and acts: takes the block away or puts the plan's block. The item
 * leaves the buffer at that moment ({@link BuildStageRules#consumeOnPlace}), and the core pays the block's price; the
 * battery pays what the core cannot (a dark core, R94).
 *
 * <p><b>Why a job stops.</b> A block the drone may not touch where the plan wants something else — «in the way: X at
 * x y z», told the restorer in chat once; a cell no route reaches — «the drone cannot reach x y z»;
 * a cell in a chunk nobody keeps loaded — «chunk not loaded» (never loaded for the drone's sake); no plan in the
 * dispatcher — «no plan»; a short buffer — «no materials: …»; no energy — «no energy». A stopped job is blocked in
 * the queue with its reason, and a drone with nothing queued to do tries it again every
 * {@link DroneNestRules#RETRY_TICKS} ticks — whichever drone stopped it. A cell with
 * a creature in it is passed and tried again later; it stops nothing. Two drones never take one cell
 * ({@link TownDroneRoster#reserve}).
 */
public final class ConstructionDroneWork {

	/** The job kinds a tier-1 drone takes (R87: the class «town hall»; the queue holds nothing else yet). */
	public static final Set<WorkJobType> TAKES = Set.of(WorkJobType.CLEAR, WorkJobType.BUILD, WorkJobType.ROAD);

	private static final int NONE = 0;
	private static final int REMOVE = 1;
	private static final int PLACE = 2;
	/** Changing a plate of a house's plaque (R4-12); the cell is {@code building * PLAQUE_SLOTS + slot}. */
	private static final int PLAQUE = 3;
	/** Room for a plaque's plates in the plaque cell's number. */
	private static final int PLAQUE_SLOTS = 16;
	/** Lifting a block out of the way to a walled-up cell, and putting it back ({@link DroneAccess}); cell = spot. */
	private static final int LIFT = 4;
	private static final int RESTORE = 5;
	/** The keys of the lifted spots in {@link #skipped}: below every other key. */
	private static final int SPOT_KEY = -1_000_000;
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
	/** The way in to a walled-up cell under way (owner's round 5 of 0.1.219). Saved. */
	private final DroneAccess access = new DroneAccess();
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
		TownWorks works = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town());
		WorkQueue queue = works.queue();
		Optional<WorkJob> job = takeJob(works, ctx.taker(), ctx.now());
		if (job.isEmpty()) {
			// Nothing queued: the drone looks after the houses' plaques (owner's round 4 of 0.1.219, R4-12).
			Optional<Step> plaque = walkPlaques(ctx, works, flight, battery, spendFlight);
			return plaque.orElse(new Step(DroneNestRules.Status.IDLE, DroneNestRules.RETRY_TICKS / 5));
		}
		WorkJob held = job.get();
		if (held.type() == WorkJobType.ROAD) {
			return walkRoad(ctx, queue, held, flight, battery, spendFlight);
		}
		// The plan first (owner's round 2 of 0.1.219, R2-6): a house without its plan in the dispatcher
		// raises no road.
		Optional<BuildingPlan> plan = TownWorksScanner.plan(ctx.level(), ctx.town(), held.building());
		if (plan.isEmpty() || !planInDispatcher(ctx, held.building())) {
			access.abandon(ctx.level(), ctx.dispatcher().buffer());
			block(queue, held, ctx.taker(), BlockReason.of(BlockReason.NO_PLAN), ctx.now());
			return new Step(DroneNestRules.Status.NO_PLAN, DroneNestRules.DOCK_TICKS);
		}
		if (access.active()) {
			Optional<Step> way = accessStep(ctx, queue, held, plan.get(), flight, battery, spendFlight);
			if (way.isPresent()) {
				return way.get();
			}
		}
		if (held.type() == WorkJobType.BUILD && RoadWork.needsRoad(ctx.level(), ctx.town(), held.building())) {
			// The road before the house (MOD-861, R108): the building job waits for the road job it raises.
			queue.raise(WorkJobType.ROAD, held.building());
			queue.giveBack(held.id(), ctx.taker(), held.progress());
			return new Step(DroneNestRules.Status.IDLE, 1);
		}
		if (held.type() == WorkJobType.BUILD) {
			// The foundation before the house (R3-5): a pit under the floor gets earth before a block goes up.
			Optional<Step> footing = walkRing(ctx, queue, held, plan.get(), false, true, flight, battery, spendFlight);
			if (footing.isPresent()) {
				return footing.get();
			}
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

	/**
	 * The cells — a building job bottom up, a clearing top down (owner's round 1 of 0.1.219: sand over rubble comes off
	 * before what holds it up): the first one with something to do gets the drone.
	 */
	private Step walk(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan,
			Optional<BuildMaterials.StagedPlan> staged, Optional<BuildStage> stage, DroneFlight flight, long battery,
			IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		Job job = held.type() == WorkJobType.CLEAR ? Job.CLEAR : Job.BUILD;
		boolean pending = false;
		int deferred = -1;
		for (int n = 0; n < plan.size(); n++) {
			int i = job == Job.CLEAR ? plan.size() - 1 - n : n;
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
				return block(queue, held, ctx, BlockReason.obstructed(world.getBlock().getDescriptionId(), pos.getX(),
						pos.getY(), pos.getZ()));
			}
			if (decided == Action.PLACE && comesWithItsPair(ctx.level(), plan, staged, i)) {
				continue; // the half its pair pays for goes up with that half (R2-4)
			}
			if (decided == Action.PLACE && seals(ctx, plan, staged, stage, i)) {
				deferred = deferred < 0 ? i : deferred; // it would wall in the cell next to it: that one first
				continue;
			}
			String holder = roster.holder(pos.asLong());
			if (holder != null && !holder.equals(ctx.taker()) || creatureIn(ctx.level(), pos)
					|| decided == Action.PLACE && unsupportedFalling(ctx.level(), pos, plan.expected(i))) {
				skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
				pending = true;
				continue;
			}
			return takeOff(ctx, queue, held, plan, i, pos, decided, flight, battery, spendFlight);
		}
		if (job == Job.CLEAR) {
			Optional<Step> ring = walkRing(ctx, queue, held, plan, true, plan.laid(), flight, battery, spendFlight);
			if (ring.isPresent()) {
				return ring.get();
			}
			pending |= ringPending;
		}
		if (pending) {
			return pendingStep(ctx, queue, held, k -> k < plan.size() + plan.ringSize() + plan.fillSize()
					? plan.target(k) : null);
		}
		// Only cells that wait for another are left: the first goes up all the same, never a silent wait.
		return deferred >= 0 ? takeOff(ctx, queue, held, plan, deferred, plan.pos(deferred), Action.PLACE, flight,
				battery, spendFlight) : finish(ctx, queue, held, plan, staged, stage);
	}

	/** Whether putting cell {@code i} of the stage now would wall in a cell next to it ({@link DroneAccess#seals}). */
	private boolean seals(Context ctx, BuildingPlan plan, Optional<BuildMaterials.StagedPlan> staged,
			Optional<BuildStage> stage, int i) {
		return staged.isPresent() && stage.isPresent()
				&& DroneAccess.seals(ctx.level(), plan, staged.get(), stage.get(), i, p -> loaded(ctx.level(), p));
	}

	/**
	 * The walk found nothing to do now but cells passed for a while. When one of them is a cell no route reaches, the
	 * job stops naming it (owner's round 5 of 0.1.219: no silent wait) and is tried again after
	 * {@link DroneNestRules#RETRY_TICKS}, so a way cleared meanwhile lets it go on by itself; a cell with a creature in
	 * it only makes the drone wait. {@code where} gives a passed cell's position by its key, or null for a key of
	 * another job.
	 */
	private Step pendingStep(Context ctx, WorkQueue queue, WorkJob held, IntFunction<@Nullable BlockPos> where) {
		for (int key : unreachable) {
			BlockPos pos = skipped.getOrDefault(key, Long.MIN_VALUE) > ctx.now() ? where.apply(key) : null;
			if (pos != null) {
				return block(queue, held, ctx, BlockReason.noRoute(pos.getX(), pos.getY(), pos.getZ()));
			}
		}
		return new Step(DroneNestRules.Status.WAITING, DroneNestRules.DOCK_TICKS);
	}

	/**
	 * The walk found nothing left to do: the job is done — unless it is a building job whose stage still has a cell
	 * missing. Then the stage rules and the drone disagree on that cell, and the job never ends with the building half
	 * built (round 5 of 0.1.219): it stops, naming the cell, so the player sees where and the job stays open.
	 */
	private Step finish(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan,
			Optional<BuildMaterials.StagedPlan> staged, Optional<BuildStage> stage) {
		if (stage.isPresent() && staged.isPresent()) {
			return stuck(ctx, queue, held, plan, staged.get(), stage.get());
		}
		queue.complete(held.id(), ctx.taker());
		SettlementRegistry.get(ctx.level().getServer()).drones().forgetStops(ctx.town(), held.building());
		if (held.type() == WorkJobType.CLEAR) {
			cleared(ctx, held);
		} else if (held.type() == WorkJobType.BUILD) {
			HallBuildNews.buildingJobDone(ctx.level(), ctx.town(), held);
		}
		TownWorksScanner.measureNow(ctx.level(), ctx.town(), held.building());
		return new Step(DroneNestRules.Status.IDLE, DroneNestRules.DOCK_TICKS);
	}

	/**
	 * Stops building job {@code held}, whose stage {@code stage} still has a cell missing that the drone found nothing
	 * to do on: «%s cannot be built at x y z», named after the stage's first missing cell. Logged: it means the stage
	 * rules and the drone's cell rules disagree, which they should not.
	 */
	private Step stuck(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan,
			BuildMaterials.StagedPlan staged, BuildStage stage) {
		int cellIndex = staged.firstMissing(ctx.level(), stage);
		BlockReason reason;
		if (cellIndex < 0) {
			reason = BlockReason.of(BlockReason.CHUNK_NOT_LOADED);
		} else {
			BlockPos pos = plan.pos(cellIndex);
			reason = BlockReason.cellStuck(plan.expected(cellIndex).getBlock().getDescriptionId(),
					pos.getX() + " " + pos.getY() + " " + pos.getZ());
			Industrialization.LOGGER.warn("[ala-towns] settlement {}: building {} job {} found nothing to do in"
					+ " stage {} though {} at {} is missing (the world holds {}); the job is stopped, not finished",
					ctx.town(), held.building(), held.id(), stage.key(), plan.expected(cellIndex), pos.toShortString(),
					ctx.level().getBlockState(pos));
		}
		return block(queue, held, ctx, reason);
	}

	/** Set by {@link #walkRing}: a ring cell was passed for now and will be tried again. */
	private boolean ringPending;

	/**
	 * The ring around the building, once its own cells are clear (owner's round 4): terrain and rubble that touch the
	 * walls go, so the building stands free; and the foundation under a house, filled from the buffer (MOD-877, R3-5).
	 * {@code ring} and {@code foundation} say which of the two to walk. The step that sends the drone or stops the job,
	 * or empty when they hold nothing to do now ({@link #ringPending} says whether a cell waits).
	 */
	private Optional<Step> walkRing(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan, boolean ring,
			boolean foundation, DroneFlight flight, long battery, IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		ringPending = false;
		for (int r = ring ? 0 : plan.ringSize(); r < plan.ringSize() + (foundation ? plan.fillSize() : 0); r++) {
			int i = plan.size() + r;
			BlockPos pos = plan.target(i);
			boolean fill = r >= plan.ringSize();
			if (skipped.getOrDefault(i, Long.MIN_VALUE) > ctx.now()) {
				ringPending = true;
				continue;
			}
			if (!loaded(ctx.level(), pos)) {
				return Optional.of(block(queue, held, ctx, BlockReason.of(BlockReason.CHUNK_NOT_LOADED)));
			}
			BlockState world = ctx.level().getBlockState(pos);
			if (fill ? !DroneCellSurvey.needsFoundation(ctx.level(), plan, r - plan.ringSize())
					: !DroneCellSurvey.ringClearable(world, plan) || RoadWork.carriesLine(ctx.level(), pos)) {
				continue;
			}
			if (fill && SiteWork.fillState(ctx.dispatcher().buffer()).isEmpty()) {
				return Optional.of(block(queue, held, ctx, foundationShortage(ctx, plan)));
			}
			String holder = roster.holder(pos.asLong());
			if (holder != null && !holder.equals(ctx.taker()) || creatureIn(ctx.level(), pos)) {
				skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
				ringPending = true;
				continue;
			}
			return Optional.of(takeOff(ctx, queue, held, plan, i, pos, fill ? Action.PLACE : Action.REMOVE, flight,
					battery, spendFlight));
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
		if (planned == PLAQUE && index >= 0) {
			arriveOnPlaque(ctx, battery, index);
			return;
		}
		if (planned == LIFT || planned == RESTORE) {
			arriveOnSpot(ctx, battery, planned, index);
			return;
		}
		WorkQueue queue = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town()).queue();
		Optional<WorkJob> held = queue.heldBy(ctx.taker());
		if (held.isPresent() && held.get().type() == WorkJobType.ROAD && index >= 0 && planned != NONE) {
			arriveOnRoad(ctx, battery, held.get(), index);
			return;
		}
		if (held.isEmpty() || index < 0 || planned == NONE || !planInDispatcher(ctx, held.get().building())) {
			// No plan in the dispatcher any more (owner's round 2): the drone does nothing here and flies home; the
			// next docked step blocks the job «no plan». Nothing has left the buffer — that happens only on placing.
			return;
		}
		Optional<BuildingPlan> plan = TownWorksScanner.plan(ctx.level(), ctx.town(), held.get().building());
		if (plan.isEmpty() || index >= plan.get().size() + plan.get().ringSize() + plan.get().fillSize()
				|| !loaded(ctx.level(), plan.get().target(index))) {
			return;
		}
		if (index >= plan.get().size() + plan.get().ringSize()) {
			arriveFill(ctx, battery, plan.get(), index);
			return;
		}
		if (index >= plan.get().size()) {
			arriveInRing(ctx, battery, plan.get().target(index), index, plan.get());
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
			if (plan.get().laid()) {
				SiteWork.keepSpoil(ctx.level(), pos, world, ctx.dispatcher().buffer());
			}
			takeAway(ctx.level(), pos);
			ctx.dispatcher().journal().cleared(ctx.now(), world.getBlock().getDescriptionId());
		} else if (now == Action.PLACE) {
			place(ctx, battery, held.get(), plan.get(), index, world, seen);
		}
		afterCell(ctx, held.get(), index, pos, world, now);
	}

	/**
	 * After the drone stood on cell {@code index} at {@code pos}, which held {@code world}: a stop naming the cell is
	 * gone once the cell changed, and the walled-up cell of a way in is done with ({@link DroneAccess#placed}).
	 */
	private void afterCell(Context ctx, WorkJob held, int index, BlockPos pos, BlockState world, Action now) {
		if (now != Action.NONE && !ctx.level().getBlockState(pos).equals(world)) {
			SettlementRegistry.get(ctx.level().getServer()).drones().stopResolved(ctx.town(), held.building(),
					BlockReason.at(pos.getX(), pos.getY(), pos.getZ()));
		}
		if (access.active() && access.job() == held.id() && index == access.target()) {
			access.placed();
		}
	}

	/**
	 * The drone stands on a ring cell: takes its terrain away if it is still there, the core paying. Around a laid
	 * house (MOD-877, R109) what it takes goes into the dispatcher's buffer.
	 */
	private void arriveInRing(Context ctx, BatteryPay battery, BlockPos pos, int index, BuildingPlan plan) {
		BlockState world = ctx.level().getBlockState(pos);
		if (creatureIn(ctx.level(), pos)) {
			skipped.put(index, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			return;
		}
		if (DroneCellSurvey.ringClearable(world, plan) && !RoadWork.carriesLine(ctx.level(), pos)) {
			pay(ctx, battery, 1);
			if (plan.laid()) {
				SiteWork.keepSpoil(ctx.level(), pos, world, ctx.dispatcher().buffer());
			}
			takeAway(ctx.level(), pos);
			ctx.dispatcher().journal().cleared(ctx.now(), world.getBlock().getDescriptionId());
		}
	}

	/**
	 * The drone stands in the pit under a house's floor (MOD-877, R109; R3-5): puts a foundation block from the
	 * buffer, the core paying.
	 */
	private void arriveFill(Context ctx, BatteryPay battery, BuildingPlan plan, int index) {
		BlockPos pos = plan.target(index);
		if (creatureIn(ctx.level(), pos)) {
			skipped.put(index, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			return;
		}
		Optional<BlockState> fill = SiteWork.fillState(ctx.dispatcher().buffer());
		if (DroneCellSurvey.needsFoundation(ctx.level(), plan, index - plan.size() - plan.ringSize())
				&& fill.isPresent() && SiteWork.takeFill(ctx.dispatcher().buffer(), fill.get())) {
			pay(ctx, battery, 1);
			ctx.level().setBlock(pos, fill.get(), PLACE_FLAGS);
			DroneWires.changed(ctx.level(), pos);
			ctx.dispatcher().journal().placed(ctx.now(), fill.get().getBlock().getDescriptionId());
		}
	}

	/** Puts cell {@code index}'s block, if the stage rules let it and the buffer still holds its item. */
	private void place(Context ctx, BatteryPay battery, WorkJob held, BuildingPlan plan, int index, BlockState world,
			DroneCellRules.Cell seen) {
		Optional<BuildMaterials.StagedPlan> staged = BuildMaterials.forBuilding(ctx.level(), ctx.town(),
				held.building());
		Optional<BuildStageRules.Cell> rules = staged.flatMap(s -> s.cell(index, world));
		Optional<BuildStageRules.Progress> progress = staged.flatMap(s -> s.progress(ctx.level(), access.lifted()));
		DispatcherBuffer buffer = ctx.dispatcher().buffer();
		if (unsupportedFalling(ctx.level(), plan.pos(index), plan.expected(index))) {
			return;
		}
		if (rules.isPresent() && (progress.isEmpty() || !BuildStageRules.mayPlace(progress.get(), buffer, rules.get())
				|| !BuildStageRules.consumeOnPlace(buffer, rules.get()))) {
			return;
		}
		BlockState want = plan.expected(index);
		OptionalInt pair = PairedCells.partnerIndex(plan, index);
		boolean withPair = pair.isPresent() && !plan.matches(pair.getAsInt(),
				ctx.level().getBlockState(plan.pos(pair.getAsInt())))
				&& PairedCells.free(ctx.level().getBlockState(plan.pos(pair.getAsInt())));
		pay(ctx, battery, (DroneCellRules.placementRemoves(seen) ? 2 : 1) + (withPair ? 1 : 0));
		takeOtherHalf(ctx.level(), plan.pos(index), world);
		ctx.level().setBlock(plan.pos(index), want, PLACE_FLAGS);
		if (withPair) {
			// A two-cell block goes up whole, as a player places it (owner's round 2 of 0.1.219, R2-4).
			ctx.level().setBlock(plan.pos(pair.getAsInt()), plan.expected(pair.getAsInt()), PLACE_FLAGS);
		}
		// A house inlet put beside the town road: the road draws its arm into the socket (R4-10).
		DroneWires.changed(ctx.level(), plan.pos(index));
		ctx.dispatcher().journal().placed(ctx.now(), want.getBlock().getDescriptionId());
		if (rules.isPresent() && !held.repair()) {
			// A repair's stages are no news (owner's round 4): the hall says «damaged» and «mended» instead.
			BuildStage stage = rules.get().stage();
			Optional<BuildStageRules.Progress> after = staged.flatMap(s -> s.progress(ctx.level(), access.lifted()));
			boolean closed = after.flatMap(p -> p.stages().stream().filter(s -> s.stage() == stage).findFirst())
					.filter(BuildStageRules.StageProgress::done).isPresent();
			if (closed) {
				ctx.dispatcher().journal().stageDone(ctx.now(), stage);
				HallBuildNews.stageDone(ctx.level(), ctx.town(), held.building(), stage, after.get(), buffer);
			}
		}
	}

	/**
	 * Sends the drone to cell {@code i} at {@code pos} along a route through the air — once its search is done (a few
	 * cells a tick), and only with the round trip in the battery. A cell no route reaches is passed and tried later.
	 */
	private Step takeOff(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan, int i, BlockPos pos,
			Action decided, DroneFlight flight, long battery, IntConsumer spendFlight) {
		Step step = takeOffIn(ctx, queue, held, plan.box().orElse(null), i, pos, decided, flight, battery, spendFlight);
		if (decided == Action.PLACE && held.type() == WorkJobType.BUILD && i < plan.size() && unreachable.contains(i)
				&& access.begin(ctx.level(), ctx.nest(), loadedProbe, held.id(), held.building(), plan, i, ctx.now())) {
			// Walled up: the drone lifts what is in its way for a while instead of stopping (round 5 of 0.1.219).
			unreachable.remove(i);
			skipped.remove(i);
		}
		return step;
	}

	/** {@code takeOff} with the route's search box given: the building's, or none for a road cell (MOD-861). */
	private Step takeOffIn(Context ctx, WorkQueue queue, WorkJob held, @Nullable BoundingBox box, int i, BlockPos pos,
			Action decided, DroneFlight flight, long battery, IntConsumer spendFlight) {
		Step step = launch(ctx, box, i, i, pos, decided == Action.REMOVE ? REMOVE : PLACE, flight, battery,
				spendFlight);
		if (step.status() == DroneNestRules.Status.WORKING) {
			queue.progress(held.id(), ctx.taker(), i);
		}
		return step;
	}

	/**
	 * Sends the drone to {@code pos} — cell {@code target} of what it does, {@code code} the doing — once the route is
	 * found and the round trip is in the battery; {@code key} is the cell's key in {@link #skipped}.
	 */
	private Step launch(Context ctx, @Nullable BoundingBox box, int key, int target, BlockPos pos, int code,
			DroneFlight flight, long battery, IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		Optional<DroneRoute> planned = route(ctx, pos, box);
		if (planned.isEmpty()) {
			// The route is still being searched (a few cells a tick), or the town's path budget is spent this tick.
			return new Step(null, 1);
		}
		DroneRoute route = planned.get();
		if (!route.found()) {
			skipped.put(key, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			unreachable.add(key);
			return new Step(null, 1);
		}
		unreachable.remove(key);
		int path = DroneNestRules.routeBlocks(route.length());
		if (!DroneNestRules.canTakeOff(battery, path)) {
			return new Step(DroneNestRules.Status.NO_ENERGY, DroneNestRules.DOCK_TICKS);
		}
		roster.reserve(pos.asLong(), ctx.taker());
		flight.launch(pos, route.points(), DroneNestRules.flightTicks(path), ctx.now());
		spendFlight.accept(path);
		cell = target;
		action = code;
		return new Step(DroneNestRules.Status.WORKING, 0);
	}

	/**
	 * A drone with no job looks after the houses' plaques (owner's round 4 of 0.1.219, R4-12): the first plate of a
	 * house that does not show its resident's number — or, the house empty, its last resident's with the letter
	 * broken — gets the drone. Quiet work: no chat, no job in the queue. Empty when every plaque reads right.
	 */
	private Optional<Step> walkPlaques(Context ctx, TownWorks works, DroneFlight flight, long battery,
			IntConsumer spendFlight) {
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		for (int building = 0; building < works.buildings().size(); building++) {
			for (TownHousing.PlaquePlate plate : TownHousing.plaqueToEngrave(ctx.level(), ctx.town(), building)) {
				int target = building * PLAQUE_SLOTS + plate.slot();
				int key = -2 - target;
				String holder = roster.holder(plate.pos().asLong());
				if (plate.slot() >= PLAQUE_SLOTS || skipped.getOrDefault(key, Long.MIN_VALUE) > ctx.now()
						|| holder != null && !holder.equals(ctx.taker())) {
					continue;
				}
				return Optional.of(launch(ctx, null, key, target, plate.pos(), PLAQUE, flight, battery,
						spendFlight));
			}
		}
		return Optional.empty();
	}

	/** The drone stands at a plaque's plate: changes it, if it still wants changing; the core pays a block's price. */
	private void arriveOnPlaque(Context ctx, BatteryPay battery, int target) {
		int building = target / PLAQUE_SLOTS;
		int slot = target % PLAQUE_SLOTS;
		for (TownHousing.PlaquePlate plate : TownHousing.plaqueToEngrave(ctx.level(), ctx.town(), building)) {
			if (plate.slot() == slot) {
				pay(ctx, battery, 1);
				ctx.level().setBlock(plate.pos(), plate.wanted(), Block.UPDATE_CLIENTS);
				ctx.dispatcher().journal().placed(ctx.now(), plate.wanted().getBlock().getDescriptionId());
				return;
			}
		}
	}

	/**
	 * The route to {@code pos} once its search is done, or empty while it goes on. A new search asks the town's path
	 * budget first (ADR-050 point 11) and then runs {@link DroneAirspace#NODES_PER_TICK} cells a tick; a search for
	 * another cell is dropped. Its box reaches over the whole building (owner's round 3): the way to the fittings
	 * inside is the door, wherever it is.
	 */
	private Optional<DroneRoute> route(Context ctx, BlockPos pos, @Nullable BoundingBox box) {
		if (planner == null || !pos.equals(planning)) {
			planner = null;
			if (!ShiftWorkers.tryPlanPath(ctx.level(), ctx.town())) {
				return Optional.empty();
			}
			planner = DroneAirspace.plan(ctx.level(), ctx.nest(), pos, loadedProbe, box);
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

	/**
	 * A road job's docked step (MOD-861, R114; owner's round 1 of 0.1.219, R1-4, R1-5): done when the house's inlet is
	 * on the town's line — the road laid, or a player wired it — or when no slot of the course wants anything; else the
	 * first slot that does gets the drone: a cut, a fill while the buffer holds earth or stone, a road block while it
	 * holds a road block. A short buffer stops the job naming what the whole road still takes.
	 */
	private Step walkRoad(Context ctx, WorkQueue queue, WorkJob held, DroneFlight flight, long battery,
			IntConsumer spendFlight) {
		if (RoadWork.wired(ctx.level(), ctx.town(), held.building())) {
			return finishRoad(ctx, queue, held);
		}
		RoadWork.Course course = RoadWork.course(ctx.level(), ctx.town(), held.building());
		if (course.problem().isPresent()) {
			return block(queue, held, ctx, BlockReason.of(course.problem().get()));
		}
		List<RoadWork.Slot> slots = RoadWork.slots(course.cells());
		TownDroneRoster roster = SettlementRegistry.get(ctx.level().getServer()).drones();
		boolean pending = false;
		for (int i = 0; i < slots.size(); i++) {
			RoadWork.Slot slot = slots.get(i);
			BlockPos pos = slot.pos();
			if (skipped.getOrDefault(i, Long.MIN_VALUE) > ctx.now()) {
				pending = true;
				continue;
			}
			if (!loaded(ctx.level(), pos)) {
				return block(queue, held, ctx, BlockReason.of(BlockReason.CHUNK_NOT_LOADED));
			}
			RoadWork.Want want = RoadWork.want(ctx.level(), slot);
			if (want == RoadWork.Want.NONE) {
				continue;
			}
			if (want == RoadWork.Want.OBSTRUCTED) {
				String what = ctx.level().getBlockState(pos).getBlock().getDescriptionId();
				return block(queue, held, ctx, BlockReason.obstructed(what, pos.getX(), pos.getY(), pos.getZ()));
			}
			if (slot.kind() == RoadWork.Kind.FILL && SiteWork.fillState(ctx.dispatcher().buffer()).isEmpty()
					|| slot.kind() == RoadWork.Kind.ROAD && ctx.dispatcher().buffer().count(RoadWork.ROAD_ITEM) <= 0) {
				return block(queue, held, ctx, roadShortage(ctx, course.cells(), slot.kind()));
			}
			String holder = roster.holder(pos.asLong());
			if (holder != null && !holder.equals(ctx.taker()) || creatureIn(ctx.level(), pos)) {
				skipped.put(i, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
				pending = true;
				continue;
			}
			// A joint lies under its column's road block, shut in by ground: the drone works it from the cell above.
			return takeOffIn(ctx, queue, held, null, i, slot.joint() ? pos.above() : pos,
					slot.kind() == RoadWork.Kind.CUT ? Action.REMOVE : Action.PLACE, flight, battery, spendFlight);
		}
		if (pending) {
			return pendingStep(ctx, queue, held, k -> k < slots.size() ? slots.get(k).pos() : null);
		}
		return finishRoad(ctx, queue, held);
	}

	/**
	 * The road job is done: every cell of its course that carries the line is marked laid in the town's network
	 * (R129), and the house is measured again — its inlet is on the line now.
	 */
	private static Step finishRoad(Context ctx, WorkQueue queue, WorkJob held) {
		queue.complete(held.id(), ctx.taker());
		SettlementRegistry.get(ctx.level().getServer()).drones().forgetStops(ctx.town(), held.building());
		for (BlockPos cell : RoadWork.course(ctx.level(), ctx.town(), held.building()).cells()) {
			if (ctx.level().isLoaded(cell) && RoadWork.done(ctx.level(), cell, ctx.level().getBlockState(cell))) {
				TownRoadNetwork.markLaid(ctx.level(), ctx.town(), cell);
			}
		}
		TownWorksScanner.measureNow(ctx.level(), ctx.town(), held.building());
		return new Step(DroneNestRules.Status.IDLE, DroneNestRules.DOCK_TICKS);
	}

	/**
	 * The drone stands on a slot of the road (owner's round 1 of 0.1.219, R1-4): if it still wants its work, does it —
	 * a cut takes the block away into the buffer, a fill puts earth or stone from the buffer, a road block leaves the
	 * buffer and joins the line; the core pays the block's price, as for a building's block.
	 */
	private void arriveOnRoad(Context ctx, BatteryPay battery, WorkJob held, int index) {
		RoadWork.Course course = RoadWork.course(ctx.level(), ctx.town(), held.building());
		List<RoadWork.Slot> slots = RoadWork.slots(course.cells());
		if (course.problem().isPresent() || index >= slots.size() || !loaded(ctx.level(), slots.get(index).pos())) {
			return;
		}
		RoadWork.Slot slot = slots.get(index);
		BlockPos pos = slot.pos();
		if (creatureIn(ctx.level(), pos)) {
			skipped.put(index, ctx.now() + DroneNestRules.SKIP_RETRY_TICKS);
			return;
		}
		if (RoadWork.want(ctx.level(), slot) != RoadWork.Want.WORK) {
			return;
		}
		BlockState world = ctx.level().getBlockState(pos);
		DispatcherBuffer buffer = ctx.dispatcher().buffer();
		switch (slot.kind()) {
			case CUT -> {
				pay(ctx, battery, 1);
				SiteWork.keepSpoil(ctx.level(), pos, world, buffer);
				ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				ctx.dispatcher().journal().cleared(ctx.now(), world.getBlock().getDescriptionId());
			}
			case FILL -> {
				Optional<BlockState> fill = SiteWork.fillState(buffer);
				if (fill.isPresent() && SiteWork.takeFill(buffer, fill.get())) {
					pay(ctx, battery, 1);
					ctx.level().setBlock(pos, fill.get(), Block.UPDATE_ALL);
					ctx.dispatcher().journal().placed(ctx.now(), fill.get().getBlock().getDescriptionId());
				}
			}
			case ROAD -> {
				if (!buffer.consume(RoadWork.ROAD_ITEM, 1)) {
					return;
				}
				pay(ctx, battery, 1);
				BlockState road = ModContent.TOWN_ROAD.get().defaultBlockState();
				// Every neighbour hears of it: the cables beside it draw their arms, the line sees its new segment.
				ctx.level().setBlock(pos, road, Block.UPDATE_ALL);
				if (!slot.joint()) {
					TownRoadNetwork.markLaid(ctx.level(), ctx.town(), pos);
				}
				BlockPos above = pos.above();
				BlockState over = ctx.level().getBlockState(above);
				if (!over.isAir() && (over.canBeReplaced() || DroneCellSurvey.litter(over))
						&& !(over.getBlock() instanceof LiquidBlock)) {
					ctx.level().setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				ctx.dispatcher().journal().placed(ctx.now(), road.getBlock().getDescriptionId());
			}
		}
	}

	/**
	 * A docked step of the way in to a walled-up cell ({@link DroneAccess}): lift the next block out of the way, put
	 * the cell's block, or put a lifted block back. Empty once the way in is over, or when it broke down and every
	 * lifted block was put back.
	 */
	private Optional<Step> accessStep(Context ctx, WorkQueue queue, WorkJob held, BuildingPlan plan, DroneFlight flight,
			long battery, IntConsumer spendFlight) {
		if (held.id() != access.job()) {
			access.abandon(ctx.level(), ctx.dispatcher().buffer());
			return Optional.empty();
		}
		DroneAccess.Order order = access.next(ctx.level(), plan);
		if (order.kind() == DroneAccess.Kind.NONE || order.pos() == null) {
			return Optional.empty();
		}
		int target = access.target();
		if (order.kind() == DroneAccess.Kind.PLACE) {
			if (DroneCellRules.decide(Job.BUILD, DroneCellSurvey.survey(plan, target,
					ctx.level().getBlockState(order.pos()))) != Action.PLACE) {
				access.placed();
				return Optional.of(new Step(null, 1));
			}
			Step step = takeOff(ctx, queue, held, plan, target, order.pos(), Action.PLACE, flight, battery,
					spendFlight);
			if (unreachable.contains(target)) {
				access.abandon(ctx.level(), ctx.dispatcher().buffer());
			}
			return Optional.of(step);
		}
		int key = SPOT_KEY - order.spot();
		Step step = launch(ctx, plan.box().orElse(null), key, order.spot(), order.pos(),
				order.kind() == DroneAccess.Kind.LIFT ? LIFT : RESTORE, flight, battery, spendFlight);
		if (unreachable.remove(key)) {
			skipped.remove(key);
			access.abandon(ctx.level(), ctx.dispatcher().buffer());
		}
		return Optional.of(step);
	}

	/** The drone stands at a spot of the way in: lifts the block there, or puts it back; the core pays. */
	private void arriveOnSpot(Context ctx, BatteryPay battery, int planned, int spot) {
		Optional<WorkJob> held = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town()).queue()
				.heldBy(ctx.taker());
		Optional<BuildingPlan> plan = TownWorksScanner.plan(ctx.level(), ctx.town(), access.building());
		if (held.isEmpty() || held.get().id() != access.job() || plan.isEmpty()) {
			return;
		}
		boolean acted = planned == LIFT ? access.arriveLift(ctx.level(), plan.get(), spot)
				: access.arriveRestore(ctx.level(), spot, ctx.dispatcher().buffer());
		if (acted) {
			pay(ctx, battery, 1);
		}
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
	private Optional<WorkJob> takeJob(TownWorks works, String taker, long now) {
		WorkQueue queue = works.queue();
		Optional<WorkJob> held = queue.heldBy(taker);
		if (held.isPresent()) {
			return held;
		}
		// The owner's order of the orders (round 2 of 0.1.219, Q3): the first order nobody works on yet.
		List<Integer> orders = works.orders();
		java.util.function.IntUnaryOperator rank = building -> WorkOrders.rank(orders, building);
		Optional<WorkJob> queued = queue.takeNext(taker, TAKES, rank);
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
		return queue.takeBlocked(taker, TAKES, rank);
	}

	/**
	 * Blocks {@code job} with {@code reason}; the drone tries it again after {@link DroneNestRules#RETRY_TICKS}. A
	 * stop of {@link BlockReason#TOLD} is told the town's restorer in chat once (owner's round 5 of 0.1.219): again
	 * only when the job or its reason changes.
	 */
	private Step block(WorkQueue queue, WorkJob job, Context ctx, BlockReason reason) {
		block(queue, job, ctx.taker(), reason, ctx.now());
		if (BlockReason.TOLD.contains(reason.key()) && SettlementRegistry.get(ctx.level().getServer()).drones()
				.firstStop(ctx.town(), job.building(), reason)) {
			HallBuildNews.jobStopped(ctx.level(), ctx.town(), job.building(), reason);
		}
		return new Step(reason.key().equals(BlockReason.NO_ROUTE) ? DroneNestRules.Status.NO_ROUTE
				: DroneNestRules.Status.WAITING, DroneNestRules.DOCK_TICKS);
	}

	private void block(WorkQueue queue, WorkJob job, String taker, BlockReason reason, long now) {
		queue.block(job.id(), taker, reason, job.progress());
		lastBlocked = job.id();
		retryAt = now + DroneNestRules.RETRY_TICKS;
	}

	/** Blocks the held job for want of energy — the core went dark, or the battery is empty and nothing charges it. */
	public void blockForEnergy(ServerLevel level, int town, String taker) {
		access.abandon(level, null);
		WorkQueue queue = SettlementRegistry.get(level.getServer()).works().town(town).queue();
		queue.heldBy(taker).ifPresent(job -> block(queue, job, taker, BlockReason.of(BlockReason.NO_ENERGY),
				level.getGameTime()));
	}

	/**
	 * Blocks the held job «no plan» — the town's dispatcher is gone, so nobody holds the plan (owner's round 2): the
	 * drones stand till a dispatcher with the plan is back.
	 */
	public void blockForNoPlan(ServerLevel level, int town, String taker) {
		access.abandon(level, null);
		WorkQueue queue = SettlementRegistry.get(level.getServer()).works().town(town).queue();
		queue.heldBy(taker).ifPresent(job -> block(queue, job, taker, BlockReason.of(BlockReason.NO_PLAN),
				level.getGameTime()));
	}

	/** The nest stopped serving: its job goes back to the queue, its cell to the roster. */
	public void giveBack(ServerLevel level, int town, String taker) {
		access.abandon(level, null);
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		registry.works().find(town).ifPresent(works -> works.queue().releaseTaker(taker));
		registry.drones().release(taker);
		cell = -1;
		action = NONE;
		planner = null;
		planning = null;
	}

	/**
	 * Whether the drones have the building's plan (MOD-859; round 2 of 0.1.219, Q4): the town hall's in the
	 * dispatcher's plan slot, a house's in the town's library of house plans. No plan, no building.
	 */
	private static boolean planInDispatcher(Context ctx, int building) {
		TownWorks works = SettlementRegistry.get(ctx.level().getServer()).works().town(ctx.town());
		return planKnown(works, building, ctx.dispatcher().plan());
	}

	/**
	 * Whether building {@code building} of {@code works} may be built with {@code slot} in the dispatcher's plan slot:
	 * a repair of a building restored once needs no plan (MOD-862); a house needs its type learned by the town — or,
	 * for a moment, lying in the slot before the dispatcher learns it; the hall its own plan in the slot.
	 */
	public static boolean planKnown(TownWorks works, int building, Optional<RestorationPlan> slot) {
		Optional<TownBuilding> found = works.building(building);
		if (found.isEmpty() || found.get().intact().isEmpty()) {
			return false;
		}
		boolean repair = works.queue().jobs().stream()
				.anyMatch(job -> job.building() == building && job.repair() && job.status().open());
		boolean inSlot = slot.isPresent() && slot.get().template().equals(found.get().intact().get());
		return repair && found.get().everRestored() || inSlot
				|| TownHouses.isHouse(found.get()) && HousePlans.knows(works, TownHall.index(works),
						found.get().intact().get());
	}

	private boolean loaded(ServerLevel level, BlockPos pos) {
		return loadedProbe != null ? loadedProbe.test(pos) : level.isLoaded(pos);
	}

	public void save(ValueOutput output) {
		output.putInt("DroneCell", cell);
		output.putInt("DroneAction", action);
		output.putInt("LastBlocked", lastBlocked);
		output.putLong("RetryAt", retryAt);
		access.save(output);
	}

	public void load(ValueInput input) {
		cell = input.getIntOr("DroneCell", -1);
		action = input.getIntOr("DroneAction", NONE);
		lastBlocked = input.getIntOr("LastBlocked", 0);
		retryAt = input.getLongOr("RetryAt", 0L);
		access.load(input);
	}
}
