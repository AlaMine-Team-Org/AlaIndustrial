package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The inlet's tank (MOD-468, stage 3): one small buffer, published on every face.
 *
 * <p><b>A crossing, not a store.</b> Its whole capacity is {@code Config.reactorPortThroughput} —
 * one tick's worth — so it behaves as a hole in the wall rather than as a reservoir: the pipe outside
 * fills it, the pipe inside empties it, and nothing accumulates. Sizing it to exactly one tick is what
 * makes the throughput figure real, because a segment can never hand on more than it holds (the same
 * mechanism that makes a cable's buffer its throughput — the lesson of MOD-070).
 *
 * <p><b>Both directions, deliberately.</b> Water goes in and steam comes back out through inlets of
 * the same kind, so the player builds one component and decides what it carries by what they plumb
 * into it. A dedicated "steam outlet" block would be a second recipe, a second texture and a second
 * thing to be placed the wrong way round, for no decision the player actually gets to make.
 */
public class ReactorPortBlockEntity extends BlockEntity implements FluidPortHost {

	/**
	 * Accepts and gives up anything. The inlet does not police what crosses it — the column on the
	 * far side takes water and nothing else, which is the honest place for that rule: a pipe carrying
	 * the wrong fluid should stall against the machine that cannot use it, not against the wall.
	 */
	public final FluidTank tank = new FluidTank(Config.reactorPortThroughput,
			fluid -> true, fluid -> true, this::setChanged);

	public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.REACTOR_PORT_BE.get(), pos, state);
	}

	@Override
	public FluidPort fluidPort(Direction side) {
		return tank;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("PortMb", tank.amount);
		if (!tank.fluid.isEmpty()) {
			output.putString("PortFluid", BuiltInRegistries.FLUID.getKey(tank.fluid.fluid()).toString());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Fluid fluid = resolve(input.getStringOr("PortFluid", ""));
		long amount = Math.max(0, Math.min(tank.capacity, input.getLongOr("PortMb", 0)));
		if (fluid == Fluids.EMPTY || amount == 0) {
			tank.fluid = FluidHolder.EMPTY;
			tank.amount = 0;
			return;
		}
		tank.fluid = FluidHolder.of(fluid);
		tank.amount = amount;
	}

	/**
	 * A saved fluid id that no longer resolves empties the buffer instead of throwing. One tick of
	 * water is not worth a world that refuses to load after a mod is removed.
	 */
	private static Fluid resolve(String id) {
		if (id.isEmpty()) {
			return Fluids.EMPTY;
		}
		Identifier key = Identifier.tryParse(id);
		return key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(key);
	}
}
