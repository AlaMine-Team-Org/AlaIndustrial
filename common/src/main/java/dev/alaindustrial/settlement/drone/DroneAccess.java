package dev.alaindustrial.settlement.drone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.drone.DroneCellRules.Action;
import dev.alaindustrial.settlement.drone.DroneCellRules.Job;
import dev.alaindustrial.settlement.works.BuildMaterials;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildingPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * How a builder drone gets to a cell of its building no route reaches (epic MOD-824, owner's round 5 of 0.1.219: «let
 * the drone break the block next to it, put the one it could not reach, then put back what it broke»). One field of
 * the drone's work ({@link ConstructionDroneWork}); saved with the nest, so a lifted block is never forgotten.
 *
 * <p><b>The order first.</b> A building job does not wall itself in: a block whose cell is the only open side of
 * another cell of the same stage still to be put waits for that cell ({@link #seals}).
 *
 * <p><b>Then the way in.</b> When a cell is walled up all the same — a hole in the ruin's floor under a standing wall,
 * say — the drone looks for the fewest blocks, at most {@link DroneAirspace#MAX_LIFTS}, it may lift out of its way
 * ({@link DroneCellSurvey#liftable}): blocks of the building's own plan standing right, and ground, rubble and litter.
 * It lifts them one flight each, dock side first, puts the cell's block, and puts each lifted block back, last first,
 * from what it remembers of it — no item leaves the dispatcher's buffer for them and none is lost. While a block is
 * lifted, its cell counts as standing for the stages. If anything goes wrong on the way — the job changes, a lifted
 * block cannot be reached again, the nest stops working — every lifted block is put back at once where its cell is
 * still empty, and given to the buffer (or dropped) where it is not: the drone never leaves a hole behind.
 */
public final class DroneAccess {

	/** What the drone does next on the way in. */
	public enum Kind {
		/** Nothing: no way in under way. */
		NONE,
		/** Lift spot {@link Order#spot()}'s block. */
		LIFT,
		/** Put the walled-up cell's block. */
		PLACE,
		/** Put spot {@link Order#spot()}'s block back. */
		RESTORE
	}

	/** The next step: what, which lifted spot (-1 for the cell itself) and where. */
	public record Order(Kind kind, int spot, @Nullable BlockPos pos) {
	}

	private static final int PHASE_LIFT = 0;
	private static final int PHASE_PLACE = 1;
	private static final int PHASE_RESTORE = 2;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** One block lifted out of the way: where, what it was (empty: nothing to put back), and whether it is lifted. */
	private record Spot(BlockPos pos, Optional<BlockState> kept, boolean lifted) {
		static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
				BlockState.CODEC.optionalFieldOf("kept").forGetter(Spot::kept),
				Codec.BOOL.fieldOf("lifted").forGetter(Spot::lifted)).apply(i, Spot::new));
	}

	/** The way in under way, as saved. */
	private record Saved(int job, int building, int target, int phase, List<Spot> spots) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("job").forGetter(Saved::job),
				Codec.INT.fieldOf("building").forGetter(Saved::building),
				Codec.INT.fieldOf("target").forGetter(Saved::target),
				Codec.INT.fieldOf("phase").forGetter(Saved::phase),
				Spot.CODEC.listOf().fieldOf("spots").forGetter(Saved::spots)).apply(i, Saved::new));
	}

	private int job;
	private int building;
	private int target = -1;
	private int phase;
	private final List<Spot> spots = new ArrayList<>();
	/** Cells a way in was looked for and not found, by plan index, and when it may be looked for again. Not saved. */
	private final Map<Integer, Long> failed = new HashMap<>();

	/** Whether a way in is under way. */
	public boolean active() {
		return job != 0;
	}

	/** The job the way in serves. */
	public int job() {
		return job;
	}

	/** The building it serves. */
	public int building() {
		return building;
	}

	/** The walled-up cell, by plan index. */
	public int target() {
		return target;
	}

	/** The lifted blocks' cells, packed: they count as standing while they are out of the way. */
	public Set<Long> lifted() {
		Set<Long> out = new HashSet<>();
		for (Spot spot : spots) {
			if (spot.lifted() && spot.kept().isPresent()) {
				out.add(spot.pos().asLong());
			}
		}
		return out;
	}

	/**
	 * Looks for a way in to cell {@code target} of {@code plan}, which no route from {@code nest} reaches, for job
	 * {@code job} of building {@code building}; true when one was found and is now under way.
	 */
	public boolean begin(ServerLevel level, BlockPos nest, @Nullable Predicate<BlockPos> probe, int job, int building,
			BuildingPlan plan, int target, long now) {
		if (active() || failed.getOrDefault(target, Long.MIN_VALUE) > now) {
			return false;
		}
		List<BlockPos> lifts = DroneAirspace.lifts(level, nest, plan.pos(target), probe, plan.box().orElse(null),
				pos -> DroneCellSurvey.liftable(level, plan, pos));
		if (lifts == null || lifts.isEmpty()) {
			fail(target, now);
			return false;
		}
		this.job = job;
		this.building = building;
		this.target = target;
		this.phase = PHASE_LIFT;
		spots.clear();
		lifts.forEach(pos -> spots.add(new Spot(pos, Optional.empty(), false)));
		return true;
	}

	/** No way in to cell {@code target} was found, or it broke down: it is not looked for again for a while. */
	public void fail(int target, long now) {
		failed.put(target, now + DroneNestRules.RETRY_TICKS);
	}

	/** The next step of the way in to {@code plan}'s cell; {@link Kind#NONE} once it is over. */
	public Order next(ServerLevel level, BuildingPlan plan) {
		if (phase == PHASE_LIFT) {
			for (int k = 0; k < spots.size(); k++) {
				Spot spot = spots.get(k);
				if (spot.lifted()) {
					continue;
				}
				if (level.getBlockState(spot.pos()).isAir()) {
					spots.set(k, new Spot(spot.pos(), Optional.empty(), true));
					continue;
				}
				if (!DroneCellSurvey.liftable(level, plan, spot.pos())) {
					abandon(level, null);
					return new Order(Kind.NONE, -1, null);
				}
				return new Order(Kind.LIFT, k, spot.pos());
			}
			phase = PHASE_PLACE;
		}
		if (phase == PHASE_PLACE) {
			return new Order(Kind.PLACE, -1, plan.pos(target));
		}
		for (int k = spots.size() - 1; k >= 0; k--) {
			Spot spot = spots.get(k);
			if (spot.lifted() && spot.kept().isPresent()) {
				return new Order(Kind.RESTORE, k, spot.pos());
			}
		}
		clear();
		return new Order(Kind.NONE, -1, null);
	}

	/** The walled-up cell is done with — its block put, or nothing to put there any more: the lifted go back. */
	public void placed() {
		phase = PHASE_RESTORE;
	}

	/** The drone stands at spot {@code k}: lifts its block, if it may still. True when it did. */
	public boolean arriveLift(ServerLevel level, BuildingPlan plan, int k) {
		if (k < 0 || k >= spots.size() || phase != PHASE_LIFT) {
			return false;
		}
		Spot spot = spots.get(k);
		BlockState state = level.getBlockState(spot.pos());
		if (!DroneCellSurvey.liftable(level, plan, spot.pos())) {
			return false;
		}
		spots.set(k, new Spot(spot.pos(), Optional.of(state), true));
		level.setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
		return true;
	}

	/** The drone stands at spot {@code k}: puts its block back. True when it put it. */
	public boolean arriveRestore(ServerLevel level, int k, @Nullable DispatcherBuffer buffer) {
		if (k < 0 || k >= spots.size() || phase != PHASE_RESTORE) {
			return false;
		}
		return restore(level, k, buffer);
	}

	/**
	 * Puts every lifted block back at once and ends the way in: the job changed, the nest stopped, or a lifted block
	 * cannot be reached again. A cell no longer empty gets nothing; its block goes to {@code buffer}, or is dropped.
	 */
	public void abandon(ServerLevel level, @Nullable DispatcherBuffer buffer) {
		for (int k = spots.size() - 1; k >= 0; k--) {
			Spot spot = spots.get(k);
			if (!spot.lifted() || spot.kept().isEmpty()) {
				continue;
			}
			if (!level.isLoaded(spot.pos())) {
				Industrialization.LOGGER.warn("[ala-towns] a drone could not put {} back at {}: its chunk is not"
						+ " loaded", spot.kept().get(), spot.pos().toShortString());
				continue;
			}
			restore(level, k, buffer);
		}
		if (target >= 0) {
			fail(target, level.getGameTime());
		}
		clear();
	}

	private boolean restore(ServerLevel level, int k, @Nullable DispatcherBuffer buffer) {
		Spot spot = spots.get(k);
		spots.set(k, new Spot(spot.pos(), Optional.empty(), true));
		if (spot.kept().isEmpty()) {
			return false;
		}
		BlockState kept = spot.kept().get();
		if (level.getBlockState(spot.pos()).isAir()) {
			level.setBlock(spot.pos(), kept, FLAGS);
			return true;
		}
		// Someone filled the cell meanwhile: the lifted block is kept, never lost.
		for (ItemStack drop : Block.getDrops(kept, level, spot.pos(), null)) {
			ItemStack rest = buffer == null ? drop : buffer.insert(drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, spot.pos(), rest);
			}
		}
		return false;
	}

	private void clear() {
		job = 0;
		building = 0;
		target = -1;
		phase = PHASE_LIFT;
		spots.clear();
	}

	/**
	 * Whether putting cell {@code i} of {@code plan}, of stage {@code stage}, would wall in a cell next to it — one of
	 * the same stage still to be put, whose only open side is cell {@code i} — while cell {@code i} has another open
	 * side: then it waits for that cell (owner's round 5 of 0.1.219: inner cells before the ones that close them).
	 */
	public static boolean seals(ServerLevel level, BuildingPlan plan, BuildMaterials.StagedPlan staged,
			BuildStage stage, int i, Predicate<BlockPos> loaded) {
		BlockPos at = plan.pos(i);
		for (Direction side : Direction.values()) {
			BlockPos next = at.relative(side);
			int j = plan.indexOf(next);
			if (j < 0 || !loaded.test(next) || !staged.stage(j).equals(Optional.of(stage))
					|| DroneCellRules.decide(Job.BUILD, DroneCellSurvey.survey(plan, j, level.getBlockState(next)))
							!= Action.PLACE) {
				continue;
			}
			if (openSides(level, next, at, loaded) == 0 && openSides(level, at, next, loaded) > 0) {
				return true;
			}
		}
		return false;
	}

	/** How many face-neighbours of {@code pos} but {@code except} the drone may fly through. */
	private static int openSides(ServerLevel level, BlockPos pos, BlockPos except, Predicate<BlockPos> loaded) {
		int open = 0;
		for (Direction side : Direction.values()) {
			BlockPos next = pos.relative(side);
			if (!next.equals(except) && DroneAirspace.open(level, next, loaded)) {
				open++;
			}
		}
		return open;
	}

	public void save(ValueOutput output) {
		if (active()) {
			output.store("DroneAccess", Saved.CODEC, new Saved(job, building, target, phase, List.copyOf(spots)));
		}
	}

	public void load(ValueInput input) {
		clear();
		input.read("DroneAccess", Saved.CODEC).ifPresent(saved -> {
			job = saved.job();
			building = saved.building();
			target = saved.target();
			phase = saved.phase();
			spots.addAll(saved.spots());
		});
	}
}
