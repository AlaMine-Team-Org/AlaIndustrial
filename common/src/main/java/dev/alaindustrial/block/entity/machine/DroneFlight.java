package dev.alaindustrial.block.entity.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The flight of a drone that is a block entity's state, not an entity (MOD-277, extracted for MOD-858; ADR-042): the
 * cell it flies to, the ticks left of the leg, and whether it is on its way home. The garden drone and the town's
 * builder drone each hold one and decide themselves what happens on arrival; this component only counts the legs.
 *
 * <p><b>The counter is the authority.</b> A leg ends when {@link #remaining} runs out, never by the world clock: a
 * block entity timed off {@code getGameTime()} would stall when its tick is driven without the world advancing (every
 * gametest does that) and lurch under {@code /time set}. {@link #start} is kept only so a client can interpolate the
 * leg it got once, with no per-tick packet.
 *
 * <p><b>The pause on the target</b> is carried by the return leg itself: {@link #turnBack} starts the leg in the
 * future, so a renderer clamps the progress to 0 for those ticks and holds the drone where it landed, and a chunk that
 * unloads mid-pause resumes the pause on load instead of landing the action twice.
 *
 * <p>Saved under the keys the garden drone always wrote ({@code DroneTarget}, {@code FlightRemaining},
 * {@code FlightStart}, {@code FlightTotal}, {@code Returning}), so its old saves read unchanged; a leg with a route of
 * its own adds {@code FlightPath}, which a save without it reads as «no route».
 */
public final class DroneFlight {

	/** What one tick of the flight came to. */
	public enum Phase {
		/** Parked: no leg in the air. */
		PARKED,
		/** In the air, still on its way. */
		FLYING,
		/** The outbound leg has just ended: the drone stands on its target. */
		ARRIVED,
		/** The return leg has just ended: the drone is home. */
		HOME
	}

	@Nullable
	private BlockPos target;
	private int remaining;
	private long start;
	private int total;
	private boolean returning;
	/**
	 * The corners of the route the leg flies, x, y, z triplets of world cells from the dock to the goal; empty for a
	 * flight with no route of its own (the garden drone's arc, an older save). Saved and synced with the leg.
	 */
	private int[] path = new int[0];

	/** The cell the drone flies to (or back from), or {@code null} while it is parked. */
	@Nullable
	public BlockPos target() {
		return target;
	}

	/** Whether a leg is in the air. */
	public boolean flying() {
		return target != null;
	}

	/** Whether the drone is on its way home: the action has already landed. */
	public boolean returning() {
		return returning;
	}

	/** Ticks left of the current leg, the landing pause of a return leg included. */
	public int remaining() {
		return remaining;
	}

	/** Length of the current leg in ticks. */
	public int total() {
		return total;
	}

	/**
	 * The corners of the leg's route (MOD-858, owner's round 2), x, y, z triplets dock first; empty when the flight
	 * has no route of its own. A copy.
	 */
	public int[] path() {
		return path.clone();
	}

	/** Sends the drone out to {@code to}: a leg of {@code ticks} ticks starting at {@code gameTime}. */
	public void launch(BlockPos to, int ticks, long gameTime) {
		launch(to, new int[0], ticks, gameTime);
	}

	/**
	 * Sends the drone out to {@code to} along {@code route} (x, y, z triplets, dock first): a leg of {@code ticks}
	 * ticks starting at {@code gameTime}; it comes home along the same route.
	 */
	public void launch(BlockPos to, int[] route, int ticks, long gameTime) {
		path = route.length % 3 == 0 ? route.clone() : new int[0];
		target = to.immutable();
		returning = false;
		total = Math.max(1, ticks);
		remaining = total;
		start = gameTime;
	}

	/**
	 * One tick of the flight: a leg in the air counts down; the tick it runs out reports {@link Phase#ARRIVED} or
	 * {@link Phase#HOME} and leaves the state as it was, so the owner decides what to do next.
	 */
	public Phase tick() {
		if (target == null) {
			return Phase.PARKED;
		}
		if (remaining > 0) {
			remaining--;
			return Phase.FLYING;
		}
		return returning ? Phase.HOME : Phase.ARRIVED;
	}

	/**
	 * Heads home along the same leg after standing {@code pause} ticks on the target: the leg is started
	 * {@code pause} ticks in the future, so the client holds the drone where it landed meanwhile.
	 */
	public void turnBack(long gameTime, int pause) {
		returning = true;
		remaining = total + Math.max(0, pause);
		start = gameTime + Math.max(0, pause);
	}

	/** Parks the drone: no target, nothing to fly or to draw in the air. */
	public void park() {
		target = null;
		path = new int[0];
		returning = false;
		remaining = 0;
		total = 0;
		start = 0L;
	}

	/**
	 * How far along the current leg the drone is, 0 (just departed) to 1 (arrived), at a client's
	 * {@code gameTimeWithPartials}; clamped, so a leg starting in the future reads 0.
	 */
	public float progress(float gameTimeWithPartials) {
		if (total <= 0) {
			return 0.0F;
		}
		return Math.clamp((gameTimeWithPartials - start) / total, 0.0F, 1.0F);
	}

	/**
	 * How far along its route the drone is now, 0 (dock) to 1 (goal), by the counter rather than the clock (the
	 * counter is the authority): eased as a client draws it, run backwards on the way home, and held on the goal
	 * through the landing pause. 0 while parked.
	 */
	public float travel() {
		if (target == null || total <= 0) {
			return 0.0F;
		}
		float eased = accelerate((float) (total - remaining) / total);
		return returning ? 1.0F - eased : eased;
	}

	/**
	 * The speed curve: slow off the pad, fast across the middle, braking onto the target. Smootherstep — its
	 * derivative is zero at both ends, so a drone leaves and meets the ground with zero speed (MOD-317). Here, on the
	 * flight model, so a renderer and a flight-loop sound follow the same curve (MOD-329).
	 */
	public static float accelerate(float rawProgress) {
		float c = Math.clamp(rawProgress, 0.0F, 1.0F);
		return c * c * c * (c * (c * 6.0F - 15.0F) + 10.0F);
	}

	/**
	 * The box a renderer draws the flight in (MOD-633): the home block alone while parked; in the air, home and target
	 * together, widened by {@code margin} sideways and raised by {@code headroom}.
	 */
	public AABB renderBox(BlockPos home, double margin, double headroom) {
		AABB dock = new AABB(home);
		if (target == null) {
			return dock;
		}
		return dock.minmax(new AABB(target)).inflate(margin, 0.0, margin).expandTowards(0.0, headroom, 0.0);
	}

	/** Writes the flight under the garden drone's historical keys. */
	public void save(ValueOutput output) {
		output.storeNullable("DroneTarget", BlockPos.CODEC, target);
		output.putInt("FlightRemaining", remaining);
		output.putLong("FlightStart", start);
		output.putInt("FlightTotal", total);
		output.putBoolean("Returning", returning);
		if (path.length > 0) {
			output.putIntArray("FlightPath", path);
		}
	}

	/** Reads the flight back; a save without it reads as parked. */
	public void load(ValueInput input) {
		target = input.read("DroneTarget", BlockPos.CODEC).orElse(null);
		remaining = input.getIntOr("FlightRemaining", 0);
		start = input.getLongOr("FlightStart", 0L);
		total = input.getIntOr("FlightTotal", 0);
		returning = input.getBooleanOr("Returning", false);
		int[] read = input.getIntArray("FlightPath").orElse(new int[0]);
		path = target != null && read.length % 3 == 0 ? read : new int[0];
	}
}
