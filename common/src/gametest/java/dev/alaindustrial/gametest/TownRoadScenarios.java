package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HouseInletBlock;
import dev.alaindustrial.block.entity.HouseInletBlockEntity;
import dev.alaindustrial.block.entity.TownRoadBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.TownPowerLinks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The town road (MOD-861) is a segment of the energy line: a house inlet with no road in front of its socket gets
 * nothing and is cut from the town's side; laid, the road carries the energy — its middle segment holds charge in the
 * middle of the run (ADR-001) — the inlet fills, and the link reads «connected».
 */
public final class TownRoadScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownRoadScenarios::tcTownRoad001Fun01RoadWiresTheInletToTheTown,
						"town_road_wires_the_inlet_to_the_town").ticks(40));

		private Roster() {}
	}

	/**
	 * The line along +X: the source stands for the town's side (the core's neighbour), the roads run to the inlet's
	 * socket, which faces west toward them.
	 */
	private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
	private static final BlockPos ROAD_A = new BlockPos(2, 2, 1);
	private static final BlockPos ROAD_B = new BlockPos(3, 2, 1);
	private static final BlockPos INLET = new BlockPos(4, 2, 1);

	private TownRoadScenarios() {}

	/**
	 * @implements TC-TOWN-ROAD-001-FUN01 — with air where the road goes the inlet takes nothing in forty ticks and is
	 *     cut from the source's line; with two road blocks laid it fills, the road segment next to it held charge in
	 *     the middle of the run, and {@link TownPowerLinks#link} finds the inlet on the town's line.
	 */
	public static void tcTownRoad001Fun01RoadWiresTheInletToTheTown(GameTestHelper helper) {
		helper.setBlock(SOURCE, ModContent.CREATIVE_ENERGY_SOURCE.get());
		helper.setBlock(INLET, ModContent.HOUSE_INLET.get().defaultBlockState()
				.setValue(HouseInletBlock.FACING, Direction.WEST));
		HouseInletBlockEntity inlet = helper.getBlockEntity(INLET, HouseInletBlockEntity.class);
		TownPowerLinks.Socket socket = new TownPowerLinks.Socket(helper.absolutePos(INLET), Direction.WEST);
		BlockPos town = helper.absolutePos(SOURCE);

		drive(helper, 40, false);
		if (inlet.storedEnergy() != 0L) {
			helper.fail("with no road the inlet took " + inlet.storedEnergy() + " EU");
			return;
		}
		if (TownPowerLinks.link(helper.getLevel(), socket, town, Optional.empty()) != TownPowerLinks.Link.CUT) {
			helper.fail("with no road the inlet must be cut from the town's line");
			return;
		}

		helper.setBlock(ROAD_A, ModContent.TOWN_ROAD.get());
		helper.setBlock(ROAD_B, ModContent.TOWN_ROAD.get());
		long roadPeak = drive(helper, 40, true);
		if (roadPeak <= 0L) {
			helper.fail("the road next to the inlet never held charge mid-run: it is no segment of the line");
			return;
		}
		if (inlet.storedEnergy() <= 0L) {
			helper.fail("the road carried nothing to the inlet in forty ticks");
			return;
		}
		if (TownPowerLinks.link(helper.getLevel(), socket, town, Optional.empty())
				!= TownPowerLinks.Link.CONNECTED) {
			helper.fail("with the road laid the inlet must share the town's line");
			return;
		}

		helper.setBlock(ROAD_B, Blocks.AIR);
		if (TownPowerLinks.link(helper.getLevel(), socket, town, Optional.empty()) != TownPowerLinks.Link.CUT) {
			helper.fail("a road with a gap must leave the inlet cut");
			return;
		}
		helper.succeed();
	}

	/**
	 * Ticks the line in the game's order (source, roads, the networks, the inlet) and returns the highest charge the
	 * road next to the inlet held at the end of a tick.
	 */
	private static long drive(GameTestHelper helper, int ticks, boolean roads) {
		long peak = 0L;
		for (int i = 0; i < ticks; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SOURCE));
			if (roads) {
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, ROAD_A));
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, ROAD_B));
			}
			NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, INLET));
			if (roads && EnergyScenarioSupport.be(helper, ROAD_B) instanceof TownRoadBlockEntity road) {
				peak = Math.max(peak, road.getEnergyStorage().getAmount());
			}
		}
		return peak;
	}
}
