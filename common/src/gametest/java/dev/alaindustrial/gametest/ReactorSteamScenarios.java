package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * World scenarios for what a working steam loop shows and sounds like (MOD-662).
 *
 * <p>Particles and sounds are made on the client, so nothing here watches them: each scenario asks the block
 * entity that sends them what it sent — a count of puffs, a count of hiss clips, the particle types of the last
 * burst. The bare-core half of the puff rule (a bare core never puffs) lives in
 * {@code ReactorScenarios.bareReactorProducesMeltsAndObeysTheSwitch}, the one scenario that already owns a bare rig.
 */
public final class ReactorSteamScenarios {

	private ReactorSteamScenarios() {
	}

	/** Bottom of a two-high stack in the room rig's interior. */
	private static final BlockPos STACK_BOTTOM = new BlockPos(2, 1, 2);

	/** A column standing on its own, with no water: a stack that never boils. */
	private static final BlockPos DRY_COLUMN = new BlockPos(1, 1, 1);

	/**
	 * A sealed room puffs once per boiling stack per pulse — and not at all while nothing boils.
	 *
	 * <p>The stack is two columns high on purpose: one puff per STACK, over its top, is the rule, so a two-high
	 * tower puffing twice a pulse would be the walk grouping it wrongly. The dry column beside it is the other half:
	 * a stack that is not boiling must stay quiet even while its neighbour works.
	 */
	public static void aBoilingRoomPuffsOverEachBoilingStack(GameTestHelper helper) {
		ReactorScenarios.buildRoom(helper);
		ReactorControllerBlockEntity brain = ReactorScenarios.controller(helper);
		FuelRodAssemblyBlockEntity bottom = ReactorScenarios.placeColumnAt(helper, STACK_BOTTOM);
		FuelRodAssemblyBlockEntity top = ReactorScenarios.placeColumnAt(helper, STACK_BOTTOM.above());
		ReactorScenarios.placeColumnAt(helper, DRY_COLUMN);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			bottom.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		helper.setBlock(ReactorScenarios.CONTROLLER.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());

		// Dry: the reaction runs, nothing boils, and there is nothing to puff about.
		ReactorScenarios.driveUnderLoad(helper, brain, 60);
		if (brain.getStatus() != ReactorRoomStatus.FORMED) {
			helper.fail("room did not seal: " + brain.getStatus());
		}
		if (brain.getSteamPuffsSent() != 0) {
			helper.fail("a dry room puffed steam " + brain.getSteamPuffsSent() + " time(s) with nothing boiling");
		}

		// Plumbed: water topped up and steam carried off every tick, the way a working loop keeps a stack.
		int interval = Math.max(1, Config.reactorSteamPlumeIntervalTicks);
		keepBoiling(helper, brain, bottom, top, interval * 2);
		if (!bottom.isBoiling() && !top.isBoiling()) {
			helper.fail("the watered stack is not boiling, so the puff count below would be measuring nothing");
		}
		int before = brain.getSteamPuffsSent();
		int pulses = 3;
		keepBoiling(helper, brain, bottom, top, interval * pulses);
		int puffs = brain.getSteamPuffsSent() - before;
		// Exactly one boiling stack, so exactly one puff each pulse — a window of whole intervals holds whole pulses.
		if (puffs != pulses) {
			helper.fail("expected " + pulses + " puffs over " + interval * pulses + " ticks for one boiling stack, got "
					+ puffs);
		}
		helper.succeed();
	}

	private static void keepBoiling(GameTestHelper helper, ReactorControllerBlockEntity brain,
			FuelRodAssemblyBlockEntity bottom, FuelRodAssemblyBlockEntity top, int ticks) {
		for (int i = 0; i < ticks; i++) {
			for (FuelRodAssemblyBlockEntity column : List.of(bottom, top)) {
				column.setTank(true, column.waterTank.capacity);
				column.setTank(false, 0);
			}
			ReactorScenarios.driveUnderLoad(helper, brain, 1);
		}
	}

	/** Venting nozzle facing up, one facing east, one buried — placed apart so each vents into its own air. */
	private static final BlockPos NOZZLE_UP = new BlockPos(1, 1, 1);
	private static final BlockPos NOZZLE_EAST = new BlockPos(3, 1, 3);
	private static final BlockPos NOZZLE_BURIED = new BlockPos(5, 1, 1);

	/** Ticks the scenario lets the nozzles run: one edge start and one periodic restart of the hiss. */
	private static final int NOZZLE_RUN_TICKS = 110;

	/**
	 * A venting nozzle throws geyser particles — never the old cloud puff — and hisses on our own event: once when
	 * venting starts and again on its period while it goes on. A buried nozzle vents nothing and stays silent.
	 *
	 * <p>Run on real ticks, not by calling {@code tick} in a loop: the hiss is scheduled by game time, which stands
	 * still inside a loop.
	 */
	public static void aVentingNozzleThrowsAGeyserAndHisses(GameTestHelper helper) {
		SteamNozzleBlockEntity up = placeNozzle(helper, NOZZLE_UP, Direction.UP);
		SteamNozzleBlockEntity east = placeNozzle(helper, NOZZLE_EAST, Direction.EAST);
		SteamNozzleBlockEntity buried = placeNozzle(helper, NOZZLE_BURIED, Direction.UP);
		helper.setBlock(NOZZLE_BURIED.above(), Blocks.STONE.defaultBlockState());
		helper.onEachTick(() -> {
			for (SteamNozzleBlockEntity nozzle : List.of(up, east, buried)) {
				nozzle.tank.fluid = FluidHolder.of(ModContent.STEAM.get());
				nozzle.tank.amount = nozzle.tank.capacity;
			}
		});
		helper.runAfterDelay(NOZZLE_RUN_TICKS, () -> {
			// The edge and one period: a third clip needs two full periods, far past this run.
			if (up.getVentSounds() != 2) {
				helper.fail("a nozzle venting for " + NOZZLE_RUN_TICKS + " ticks hissed " + up.getVentSounds()
						+ " time(s); expected the start and one periodic restart");
			}
			int interval = Math.max(1, Config.reactorNozzlePlumeIntervalTicks);
			int minBursts = NOZZLE_RUN_TICKS / interval - 3;
			if (up.getPlumeBursts() < minBursts || east.getPlumeBursts() < minBursts) {
				helper.fail("too few geyser bursts: up " + up.getPlumeBursts() + ", east " + east.getPlumeBursts()
						+ ", expected at least " + minBursts);
			}
			if (!up.getLastBurstTypes().equals(List.of(ParticleTypes.GEYSER_PLUME, ParticleTypes.GEYSER_BASE))) {
				helper.fail("an upward nozzle drew " + up.getLastBurstTypes() + " instead of a geyser plume");
			}
			if (!east.getLastBurstTypes().equals(List.of(ParticleTypes.GEYSER_POOF))) {
				helper.fail("a sideways nozzle drew " + east.getLastBurstTypes() + " instead of geyser puffs");
			}
			for (SteamNozzleBlockEntity nozzle : List.of(up, east)) {
				if (nozzle.getLastBurstTypes().contains(ParticleTypes.CLOUD)) {
					helper.fail("a nozzle still draws the old cloud puff");
				}
			}
			if (buried.getVentSounds() != 0 || buried.getPlumeBursts() != 0) {
				helper.fail("a buried nozzle vents nothing, yet hissed " + buried.getVentSounds() + " time(s) and drew "
						+ buried.getPlumeBursts() + " burst(s)");
			}
			helper.succeed();
		});
	}

	private static SteamNozzleBlockEntity placeNozzle(GameTestHelper helper, BlockPos at, Direction facing) {
		helper.setBlock(at, ModContent.STEAM_NOZZLE.get().defaultBlockState().setValue(SteamNozzleBlock.FACING, facing));
		SteamNozzleBlockEntity nozzle = helper.getBlockEntity(at, SteamNozzleBlockEntity.class);
		if (nozzle == null) {
			helper.fail("steam nozzle has no block entity at " + at);
			throw new IllegalStateException("unreachable");
		}
		return nozzle;
	}
}
