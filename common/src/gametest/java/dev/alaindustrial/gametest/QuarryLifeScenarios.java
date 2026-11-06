package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.QuarryDigScenarios.digUntilGone;
import static dev.alaindustrial.gametest.QuarryDigScenarios.pit;
import static dev.alaindustrial.gametest.QuarryDigScenarios.ready;
import static dev.alaindustrial.gametest.QuarryDigScenarios.withDigging;

import dev.alaindustrial.block.QuarryFoundationBlock;
import dev.alaindustrial.block.entity.BlockBreakerBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryFrameLayout;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidHostRedirect;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.entity.QuarryDrillBody;
import dev.alaindustrial.gametest.QuarryFrameScenarios.Rig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The quarry comes alive (MOD-952, stage C of MOD-637): the drill has a solid body that follows it, it heats up and
 * water cools it, the socket takes water for it, and a redstone mode can stand it. On the stage A rig with the
 * stage B digging, the controller ticked by hand ({@link QuarryDigScenarios}).
 */
public final class QuarryLifeScenarios {

	private QuarryLifeScenarios() {
	}

	/** Gametest roster entries for this scenario class (MOD-669). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(QuarryLifeScenarios::theDrillHasABodyWhileItHasABit, "quarry_life_drill_body")
						.ticks(40),
				RosterEntry.of(QuarryLifeScenarios::anOverheatedDrillStandsUntilWaterCoolsIt, "quarry_life_overheat")
						.ticks(40),
				RosterEntry.of(QuarryLifeScenarios::theSocketTakesWaterOnly, "quarry_life_socket_water").ticks(40),
				RosterEntry.of(QuarryLifeScenarios::theRedstoneModeStandsIt, "quarry_life_redstone").ticks(40),
				RosterEntry.of(QuarryLifeScenarios::theDrillTakesATwoByTwoTileAtOnce, "quarry_life_tile").ticks(40),
				RosterEntry.of(QuarryLifeScenarios::aStepSavedByCellsGoesBackToItsLayersStart,
						"quarry_life_old_step").ticks(40));

		private Roster() {
		}
	}

	/** The drill bodies inside the frame and down into its pit. */
	private static List<QuarryDrillBody> bodies(Rig rig) {
		QuarryFrameLayout frame = rig.controller().layout();
		AABB box = new AABB(frame.minX(), frame.baseY() - 8, frame.minZ(), frame.maxX() + 1, frame.headY() + 4,
				frame.maxZ() + 1);
		return rig.level().getEntitiesOfClass(QuarryDrillBody.class, box, body -> !body.isRemoved());
	}

	/**
	 * The drill goes down on the corner four cells share and takes all four in one operation: one drilling, four
	 * blocks in the buffer, four points off the bit (owner, 2026-11-06).
	 *
	 * @implements MOD-952 — the drill takes a 2 x 2 tile at once, standing over its middle
	 */
	public static void theDrillTakesATwoByTwoTileAtOnce(GameTestHelper helper) {
		withDigging(() -> {
			Rig rig = ready(helper);
			QuarryControllerBlockEntity controller = rig.controller();
			List<BlockPos> tile = List.of(pit(rig, 0), pit(rig, 1), pit(rig, 0).relative(towardsSecondRow(rig)),
					pit(rig, 1).relative(towardsSecondRow(rig)));
			for (BlockPos cell : tile) {
				rig.set(cell, Blocks.STONE.defaultBlockState());
			}
			int before = controller.getItem(QuarryControllerBlockEntity.BIT_SLOT).getDamageValue();
			rig.scanNow();
			if (!digUntilGone(rig, tile.get(0))) {
				helper.fail("the tile was not dug: " + controller.workStatus());
				return;
			}
			boolean allGone = tile.stream().allMatch(cell -> rig.state(cell).isAir());
			int worn = controller.getItem(QuarryControllerBlockEntity.BIT_SLOT).getDamageValue() - before;
			if (!allGone || worn != 4 || controller.digger().index() != 0) {
				helper.fail("one operation must take all four cells of the tile and wear the bit by four: all gone "
						+ allGone + ", worn " + worn + ", still on step " + controller.digger().index());
				return;
			}
			rig.hold().release();
			helper.succeed();
		});
	}

	/**
	 * A world saved while the quarry dug a cell a step holds a step its layer no longer has (a layer has a quarter as
	 * many tiles): the quarry goes back to the layer's first tile instead of failing, and digs on.
	 *
	 * @implements MOD-952 — an old world's quarry progress loads without a crash (player world rule)
	 */
	public static void aStepSavedByCellsGoesBackToItsLayersStart(GameTestHelper helper) {
		withDigging(() -> {
			Rig rig = ready(helper);
			BlockPos first = pit(rig, 0);
			rig.set(first, Blocks.STONE.defaultBlockState());
			rig.scanNow();
			QuarryControllerBlockEntity controller = rig.controller();
			controller.digger().restoreStep(0, 24);
			if (!digUntilGone(rig, first)) {
				helper.fail("a step past the layer's tiles must go back to the layer's start and dig, it is "
						+ controller.workStatus() + " at step " + controller.digger().index());
				return;
			}
			rig.hold().release();
			helper.succeed();
		});
	}

	/**
	 * The way from the first row of the rig's pit to the second: along the paths, which run along X from the
	 * controller's corner at the rig's origin, so east.
	 */
	private static Direction towardsSecondRow(Rig rig) {
		return Direction.EAST;
	}

	/**
	 * A formed quarry with a bit keeps two solid bodies on its drill - the head and the pipe - and none without a bit,
	 * when the drill is not drawn either.
	 *
	 * @implements MOD-952 — the drill collides with a player, working or not (owner, 2026-11-06)
	 */
	public static void theDrillHasABodyWhileItHasABit(GameTestHelper helper) {
		Rig[] made = new Rig[1];
		withDigging(() -> {
			Rig rig = ready(helper);
			rig.set(pit(rig, 0), Blocks.STONE.defaultBlockState());
			rig.scanNow();
			for (int i = 0; i < 5; i++) {
				rig.tick(rig.controllerPos());
			}
			made[0] = rig;
		});
		// The rig's chunks were taken this very tick, and an entity added there is not seen by a search until the
		// loading tickets have been worked, a tick on: count the bodies a few ticks later.
		helper.runAfterDelay(3, () -> withDigging(() -> countTheBodies(helper, made[0])));
	}

	private static void countTheBodies(GameTestHelper helper, Rig rig) {
		rig.tick(rig.controllerPos());
		List<QuarryDrillBody> bodies = bodies(rig);
		if (bodies.size() != 2 || !bodies.stream().allMatch(body -> body.canBeCollidedWith(null))) {
			helper.fail("a formed quarry with a bit must keep two solid drill bodies, it has " + bodies.size());
			return;
		}
		rig.controller().setItem(QuarryControllerBlockEntity.BIT_SLOT, ItemStack.EMPTY);
		rig.tick(rig.controllerPos());
		if (!bodies(rig).isEmpty()) {
			helper.fail("without a bit the drill is not drawn and must have no body, it has " + bodies(rig).size());
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * A drill whose heat reaches the top of the scale stands - the next stone stays - and says so; water in the tank
	 * takes the heat away and it digs on.
	 *
	 * @implements MOD-952 — water and heat: an overheated drill stands until cooled
	 */
	public static void anOverheatedDrillStandsUntilWaterCoolsIt(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("quarryDiggingEnabled", true)
				.set("quarryHeatPerBlock", 1000)) {
			Rig rig = ready(helper);
			BlockPos first = pit(rig, 0);
			BlockPos second = pit(rig, 2); // the next 2 x 2 tile
			rig.set(first, Blocks.STONE.defaultBlockState());
			rig.set(second, Blocks.STONE.defaultBlockState());
			rig.scanNow();
			QuarryControllerBlockEntity controller = rig.controller();
			if (!digUntilGone(rig, first)) {
				helper.fail("the first stone was not dug: " + controller.workStatus());
				return;
			}
			if (digUntilGone(rig, second) || controller.workStatus() != QuarryWorkStatus.OVERHEATED) {
				helper.fail("a drill at the top of its heat must stand and read OVERHEATED, it is "
						+ controller.workStatus() + " with heat " + controller.heat());
				return;
			}
			EnergyTransactions.get().runCommitting(txn ->
					controller.cooling.waterTank.insert(FluidHolder.of(Fluids.WATER), 8000L, txn));
			if (!digUntilGone(rig, second)) {
				helper.fail("water must cool the drill and let it dig on, it is " + controller.workStatus()
						+ " with heat " + controller.heat());
				return;
			}
			rig.hold().release();
			helper.succeed();
		}
	}

	/**
	 * The socket foundation lends the controller's tank to automation: water goes in, nothing comes out, and no other
	 * fluid is taken.
	 *
	 * @implements MOD-952 — water by pipe into the main tower's socket
	 */
	public static void theSocketTakesWaterOnly(GameTestHelper helper) {
		Rig rig = ready(helper);
		rig.scanNow();
		BlockPos socket = QuarryFoundationBlock.foundationBelow(rig.controllerPos());
		FluidPort lent = FluidHostRedirect.lent(rig.level(), socket, rig.state(socket), Direction.NORTH);
		if (lent == null) {
			helper.fail("the socket of a formed frame must lend the controller's tank");
			return;
		}
		long[] moved = new long[3];
		EnergyTransactions.get().runCommitting(txn -> {
			moved[0] = lent.insert(FluidHolder.of(Fluids.WATER), 1000L, txn);
			moved[1] = lent.insert(FluidHolder.of(Fluids.LAVA), 1000L, txn);
			moved[2] = lent.extract(FluidHolder.of(Fluids.WATER), 1000L, txn);
		});
		if (moved[0] != 1000L || moved[1] != 0L || moved[2] != 0L
				|| rig.controller().cooling.waterTank.amount != 1000L) {
			helper.fail("the socket must take water only and give none back: water in " + moved[0] + ", lava in "
					+ moved[1] + ", water out " + moved[2] + ", tank " + rig.controller().cooling.waterTank.amount);
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * In the "only with a signal" mode a quarry with no signal stands and says so; a redstone block against its main
	 * tower lets it dig.
	 *
	 * @implements MOD-952 — redstone: three modes, the signal read on the main tower
	 */
	public static void theRedstoneModeStandsIt(GameTestHelper helper) {
		withDigging(() -> {
			Rig rig = ready(helper);
			BlockPos first = pit(rig, 0);
			rig.set(first, Blocks.STONE.defaultBlockState());
			QuarryControllerBlockEntity controller = rig.controller();
			controller.setRedstoneMode(BlockBreakerBlockEntity.RedstoneMode.WITH_SIGNAL);
			rig.scanNow();
			if (digUntilGone(rig, first) || controller.workStatus() != QuarryWorkStatus.REDSTONE_STOPPED) {
				helper.fail("with no signal the quarry must stand and read REDSTONE_STOPPED, it is "
						+ controller.workStatus());
				return;
			}
			BlockPos socket = QuarryFoundationBlock.foundationBelow(rig.controllerPos());
			QuarryFrameLayout frame = controller.layout();
			BlockPos outside = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = socket.relative(side);
				if (next.getX() < frame.minX() || next.getX() > frame.maxX() || next.getZ() < frame.minZ()
						|| next.getZ() > frame.maxZ()) {
					outside = next;
					break;
				}
			}
			if (outside == null) {
				helper.fail("the rig's socket has no face outside the frame for a redstone block");
				return;
			}
			rig.set(outside, Blocks.REDSTONE_BLOCK.defaultBlockState());
			rig.scanNow();
			if (!digUntilGone(rig, first)) {
				helper.fail("with a signal on the main tower the quarry must dig, it is " + controller.workStatus());
				return;
			}
			rig.hold().release();
			helper.succeed();
		});
	}
}
