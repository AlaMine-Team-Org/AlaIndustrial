package dev.alaindustrial.block.entity.quarry;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.quarry.QuarryHeat;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The quarry's water and heat (MOD-952; spec {@code docs/blocks/mining/quarry.md}, "water and heat"): the water tank
 * the main tower's socket fills and the drill's heat gauge, held by the controller in one field. The rule itself —
 * what a block adds, what water takes, when the drill stands — is {@link QuarryHeat}; this is its world side.
 */
public final class QuarryCooling {

	/**
	 * Water only, filled by pipe or bucket through the socket, never drawn out again: it is spent on the heat.
	 */
	public final FluidTank waterTank;
	private final QuarryHeat.Gauge gauge = new QuarryHeat.Gauge();
	private final Runnable changed;
	/** The heat's share of the scale as the server last saved it: what a client draws, with no knob of its own. */
	private int savedPermille;

	/** {@code changed} is the controller's {@code setChanged}: the tank and the gauge are saved with it. */
	public QuarryCooling(Runnable changed) {
		this.changed = changed;
		this.waterTank = new FluidTank(Math.max(1, Config.quarryWaterTankMb), QuarryCooling::isWater, fluid -> false,
				changed);
	}

	private static boolean isWater(FluidHolder fluid) {
		return !fluid.isEmpty() && fluid.fluid() == Fluids.WATER;
	}

	/** The heat scale as the config sets it now. */
	private static QuarryHeat scale() {
		return new QuarryHeat(Math.max(0, Config.quarryHeatPerBlock), Math.max(1, Config.quarryHeatMax),
				Math.max(0, Config.quarryCoolPerSecond), Math.max(0, Config.quarryHeatPerWaterMb));
	}

	public int heat() {
		return gauge.heat();
	}

	/** Whether the drill stands to cool. */
	public boolean overheated() {
		return gauge.overheated();
	}

	/** The heat per mille of the scale, for the screen. */
	public int heatPermille() {
		return (int) Math.min(1000L, gauge.heat() * 1000L / Math.max(1, Config.quarryHeatMax));
	}

	/**
	 * The heat's share of the scale as the server worked it out, for the client's drawing: the scale's top is a
	 * server knob, and a client's own config may hold another number.
	 */
	public int syncedHeatPermille() {
		return savedPermille;
	}

	/** A block was drilled: the drill heats up. */
	public void drilled() {
		scale().drilled(gauge);
		changed.run();
	}

	/**
	 * One tick of cooling: the water the heat needs is spent from the tank, then the drill's own cooling. Whether
	 * water took heat this tick, for the steam.
	 */
	public boolean tick() {
		int before = gauge.heat();
		int water = (int) Math.min(Integer.MAX_VALUE, waterTank.amount);
		int used = scale().cool(gauge, water);
		if (used > 0) {
			waterTank.amount = Math.max(0L, waterTank.amount - used);
			if (waterTank.amount == 0) {
				waterTank.fluid = FluidHolder.EMPTY;
			}
		}
		if (used > 0 || gauge.heat() != before) {
			changed.run();
		}
		return used > 0;
	}

	public void save(ValueOutput output) {
		waterTank.save(output, "Water");
		output.putInt("Heat", gauge.heat());
		output.putBoolean("Overheated", gauge.overheated());
		output.putInt("HeatPermille", heatPermille());
	}

	public void load(ValueInput input) {
		waterTank.load(input, "Water");
		gauge.restore(input.getIntOr("Heat", 0), input.getBooleanOr("Overheated", false));
		savedPermille = input.getIntOr("HeatPermille", 0);
	}
}
