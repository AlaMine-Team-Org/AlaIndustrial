package dev.alaindustrial.command.demo.energy;

import static dev.alaindustrial.command.demo.energy.Expectation.allFed;
import static dev.alaindustrial.command.demo.energy.Expectation.charges;
import static dev.alaindustrial.command.demo.energy.Expectation.conserves;
import static dev.alaindustrial.command.demo.energy.Expectation.fed;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.WEST;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.level.block.Block;

/**
 * Bays E31–E34 of the energy stand (MOD-799): three forty-cable lines of different grades — what a long haul
 * loses — and the mega network, a 32×32 sheet of copper under 64 machines, with 8 sources set into it and 4
 * boxes on its edge, whose tick cost the report shows.
 *
 * <p>The long lines live in the strip south of the mega network ({@code x 56..87}); each is a snake of rows of
 * 28 with one free row between rows, so the forty cables are forty hops.
 */
final class LongLineBays {
	/** West edge of the strip the long lines and the mega network share. */
	static final int STRIP_X = 56;
	/** Side of the mega network's cable sheet. */
	static final int MEGA = 32;

	private LongLineBays() {
	}

	static List<Bay> all() {
		return List.of(longLine("E31", 33, "Copper line 40", ModContent.COPPER_CABLE.get(), 0.75),
				longLine("E32", 39, "Insulated cu line 40", ModContent.INSULATED_COPPER_CABLE.get(), 0.5),
				longLine("E33", 45, "Tin line 40", ModContent.TIN_CABLE.get(), 0.35), mega());
	}

	/** A 20 EU/t source, forty cables of {@code grade}, a machine after the last and a box beside the last. */
	private static Bay longLine(String id, int z0, String title, Block grade, double maxLoss) {
		return new Bay(id, title, STRIP_X, z0, MEGA, 5, w -> {
			w.source("S1", 0, 0, 20);
			int[] end = w.snake(1, 0, 40, 28, grade);
			w.machine("M1", end[0], end[1], WEST);
			w.store("B1", end[0] + 2, end[1] + 1, NORTH, 0L);
		}, List.of(fed("M1"), charges("B1", 0.5), conserves(maxLoss)));
	}

	/**
	 * The mega network: a full 32×32 sheet of copper, a Macerator on top of every fourth cell, eight 64 EU/t
	 * sources set into the sheet, four boxes on its west edge charging from it. A source sits IN the sheet so
	 * four cables carry its output: on top of it, one cable under it would cap it at 12 EU/t and the 64 machines
	 * would be short (first run). A box set into the sheet would charge from its IN face and give back from its
	 * OUT face on the same network (second run): on the edge its OUT face meets nothing.
	 */
	private static Bay mega() {
		return new Bay("E34", "Mega grid 32x32", STRIP_X - 1, 0, MEGA + 1, MEGA + 1, w -> {
			int source = 0;
			for (int x = 0; x < MEGA; x++) {
				for (int z = 0; z < MEGA; z++) {
					if (x % 8 == 4 && z % 16 == 4) {
						source++;
						w.sourceAt("S" + source, w.at(x + 1, 1, z), 64);
					} else {
						w.cable(x + 1, z);
					}
				}
			}
			int machine = 0;
			for (int x = 2; x < MEGA; x += 4) {
				for (int z = 2; z < MEGA; z += 4) {
					machine++;
					w.machineAt("M" + machine, w.at(x + 1, 2, z), NORTH, 0);
				}
			}
			for (int box = 1; box <= 4; box++) {
				w.store("B" + box, 0, 8 * box - 4, EAST, 0L);
			}
		}, List.of(allFed(), charges("B1", 4), charges("B4", 4), conserves(0.25)));
	}
}
