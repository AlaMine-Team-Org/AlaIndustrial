package dev.alaindustrial.command.demo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Removes the item drops that land on the demo stand AFTER it was built (MOD-674).
 *
 * <p>The stand is built into an ordinary world, and the sky over it is not empty: it clears forty
 * blocks of column, which takes the trunks of every tree that stands in it, and the leaves around
 * them start to decay a little later, on their own random ticks, and shower saplings, sticks and
 * apples onto the floor. {@code DemoStand.killLooseEntities} runs once inside the build, so it
 * cannot see anything that happens afterwards — the drops arrive seconds to minutes after it has
 * finished.
 *
 * <p>So a build arms a short series of sweeps instead: two seconds after it, then progressively
 * further apart, for two minutes. It is a window rather than a permanent watch on purpose — the
 * stand is a test rig that a person walks around in, and what they drop there themselves an hour
 * later is theirs.
 *
 * <p>Only {@link ItemEntity}: the frames and the machines are the stand's own and are handled by
 * the build. The sweep is a {@code discard()}, no drops of its own.
 */
public final class DemoStandDropSweeper {

	/** Ticks after the build at which a sweep runs: 2 s, 5 s, 10 s, 20 s, 40 s, 80 s, 120 s. */
	private static final int[] SWEEP_AT_TICKS = {40, 100, 200, 400, 800, 1600, 2400};

	/** One armed stand. Mutable on purpose: it only ever lives on the server thread. */
	private static final class Job {
		private final ServerLevel level;
		private final BlockPos origin;
		private final int armedAtTick;
		private int next;

		private Job(ServerLevel level, BlockPos origin, int armedAtTick) {
			this.level = level;
			this.origin = origin;
			this.armedAtTick = armedAtTick;
		}
	}

	private static final List<Job> JOBS = new ArrayList<>();

	private DemoStandDropSweeper() {
	}

	/**
	 * Start the sweep series for a stand that was just built or cleared. A stand armed again — a rebuild
	 * inside the window — restarts its series rather than stacking a second one.
	 */
	public static void arm(ServerLevel level, BlockPos origin) {
		JOBS.removeIf(job -> job.level == level && job.origin.equals(origin));
		JOBS.add(new Job(level, origin, level.getServer().getTickCount()));
	}

	/** How many stands have sweeps still to run — the gametest asks, so it can tell "armed" from "never armed". */
	public static int pending() {
		return JOBS.size();
	}

	/**
	 * Run whatever is due. Called once per server tick from both loaders; with nothing armed — which is
	 * every tick of a game that never built the stand — it is one empty-list check.
	 */
	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		JOBS.removeIf(job -> {
			if (job.level.getServer() != server) {
				// Armed under a previous server of this JVM (an integrated server restarted): its level is dead.
				return true;
			}
			int elapsed = now - job.armedAtTick;
			while (job.next < SWEEP_AT_TICKS.length && elapsed >= SWEEP_AT_TICKS[job.next]) {
				sweep(job.level, job.origin);
				job.next++;
			}
			return job.next >= SWEEP_AT_TICKS.length;
		});
	}

	/**
	 * Every item drop in the stand's column: its footprint, and the whole {@link DemoStand#CLEAR_HEIGHT}
	 * of sky the stand clears — a drop still falling from a tree crown is inside it too.
	 */
	private static void sweep(ServerLevel level, BlockPos origin) {
		AABB box = AABB.encapsulatingFullBlocks(origin.offset(-1, 0, -1),
				origin.offset(DemoStand.WIDTH + 1, DemoStand.CLEAR_HEIGHT + 1, DemoStand.DEPTH + 1));
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box)) {
			drop.discard();
		}
	}
}
