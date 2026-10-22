package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.entity.ShiftWorkerHunters;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.SettlementTerritory;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Shift workers and hostile mobs (owner decision 2026-10-22, MOD-824), on both gametest lanes: a hostile mob hunts a
 * worker as it hunts a player and hurts it, and a worker runs from a hostile mob without ever leaving its town's
 * territory, then goes back to its shift.
 */
public final class ShiftWorkerThreatScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerThreatScenarios::aZombieHuntsAndHurtsAWorker,
						"shift_worker_hunted_by_a_zombie").ticks(200),
				RosterEntry.of(ShiftWorkerThreatScenarios::aWorkerRunsButKeepsToItsTerritory,
						"shift_worker_flees_within_its_territory").ticks(400)
						.structure(Industrialization.id("demo_stand_area")));

		private Roster() {}
	}

	private ShiftWorkerThreatScenarios() {}

	/**
	 * A zombie next to a worker gets the goal to hunt workers when it joins the level, picks the worker as its target
	 * and hurts it. The worker is held still (no goals) so its run does not decide the test — that is the next one.
	 *
	 * @implements MOD-824-HUNT — hostile mobs hunt shift workers as they hunt players
	 */
	public static void aZombieHuntsAndHurtsAWorker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL,
				"the test world is peaceful: no hostile mob targets anything there, a player included");
		ShiftWorker worker = ShiftWorkers.spawn(level, helper.absolutePos(new BlockPos(2, 1, 3)), 0)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker"));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
		helper.assertTrue(ShiftWorkerHunters.huntsWorkers(zombie),
				"the zombie joined the level without the goal to hunt shift workers");
		float full = worker.getMaxHealth();
		helper.startSequence()
				.thenExecuteAfter(3, () -> worker.setNoAi(true)) // after the first tick, whose bind sets it
				.thenWaitUntil(() -> helper.assertTrue(zombie.getTarget() == worker,
						"the zombie's target is " + zombie.getTarget() + ", not the worker"))
				.thenWaitUntil(() -> helper.assertTrue(worker.getHealth() < full,
						"the zombie has not hurt the worker: health " + worker.getHealth()))
				.thenExecute(() -> {
					zombie.discard();
					worker.discard();
				})
				.thenSucceed();
	}

	/** Where the worker stands: three columns inside the territory's east edge. */
	private static final BlockPos STAND = new BlockPos(46, 1, 26);
	/** The core: 32 columns (the LV radius) west of the edge the worker is pressed to. */
	private static final int CORE_TO_STAND = 29;
	/** The zombie, still: on the worker's west, between it and the core, so running away means running east. */
	private static final BlockPos ZOMBIE = STAND.west(4);
	/** Ticks the run is watched before the zombie is taken away. */
	private static final int RUN_TICKS = 240;
	/** Ticks after the zombie is gone by which the worker must have stopped running. */
	private static final int CALM_TICKS = 60;
	/** How far past the edge the body's middle may be: half its width, and a step the navigation overshoots. */
	private static final double EDGE_SLACK = 0.8;

	/**
	 * A worker pressed against the east edge of its town's territory by a zombie on its west runs — farther from the
	 * zombie than it started — and never leaves the territory: it runs along the edge, not over it. The floor
	 * reaches past the edge, so the territory, not the floor's end, is what holds it. Once the zombie is gone it
	 * stops running.
	 *
	 * @implements MOD-824-FLEE — a shift worker runs from hostile mobs and stays inside its town's territory
	 */
	public static void aWorkerRunsButKeepsToItsTerritory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 25; x <= 70; x++) {
			for (int z = 6; z <= 46; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		int town = registry.place(level.dimension(), helper.absolutePos(STAND.west(CORE_TO_STAND)), 0).settlementId();
		helper.assertTrue(town > 0, "no town was placed");
		registry.setActive(town, true);
		ShiftWorker worker = ShiftWorkers.spawn(level, helper.absolutePos(STAND), town)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker"));
		SettlementTerritory.Area territory = registry.find(town).orElseThrow().territory();
		helper.assertTrue(territory.reach(helper.absolutePos(STAND).getX(), helper.absolutePos(STAND).getZ()) <= 4,
				"the worker does not stand near the territory's edge: the test shows nothing");
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		double start = worker.distanceTo(zombie);
		boolean[] ran = {false};
		double[] farthest = {start};
		helper.startSequence().thenExecuteFor(RUN_TICKS, () -> {
			double outside = outside(territory, worker.getX(), worker.getZ());
			if (outside > EDGE_SLACK) {
				throw helper.assertionException("the worker ran " + String.format(Locale.ROOT, "%.2f", outside)
						+ " blocks out of its town's territory, at " + worker.blockPosition());
			}
			ran[0] |= worker.isFleeing();
			farthest[0] = Math.max(farthest[0], worker.distanceTo(zombie));
		}).thenExecute(() -> {
			helper.assertTrue(ran[0], "the worker never ran from the zombie " + String.format(Locale.ROOT, "%.1f",
					start) + " blocks away");
			helper.assertTrue(farthest[0] > start + 3.0, "the worker never got farther from the zombie: at most "
					+ String.format(Locale.ROOT, "%.1f", farthest[0]) + " blocks, it started "
					+ String.format(Locale.ROOT, "%.1f", start) + " away");
			zombie.discard();
		}).thenExecuteAfter(CALM_TICKS, () -> {
			helper.assertTrue(!worker.isFleeing(), "the zombie is gone, yet the worker is still running");
			worker.discard();
			registry.setActive(town, false); // its territory holds nothing for the scenarios that run after this one
		}).thenSucceed();
	}

	/** How far the point ({@code x}, {@code z}) lies outside {@code area}, in blocks; 0 inside. */
	private static double outside(SettlementTerritory.Area area, double x, double z) {
		double dx = Math.max(0.0, Math.max(area.minX() - x, x - (area.maxX() + 1)));
		double dz = Math.max(0.0, Math.max(area.minZ() - z, z - (area.maxZ() + 1)));
		return Math.max(dx, dz);
	}
}
