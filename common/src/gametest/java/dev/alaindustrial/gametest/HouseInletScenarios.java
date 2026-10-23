package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HouseInletBlock;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.HouseInletBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The house inlet (MOD-876) takes EU only through its outer face: over a real line (creative source, copper cables,
 * inlet) it stays empty while the cable meets a wall face, and fills once the same inlet is turned to face the cable.
 * What it holds the beds draw through {@link dev.alaindustrial.settlement.HousePowerSource}.
 */
public final class HouseInletScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(HouseInletScenarios::tcHouseInlet001Fun01TakesEnergyOnlyThroughItsOuterFace,
						"house_inlet_takes_energy_only_through_outer_face").ticks(20),
				RosterEntry.of(HouseInletScenarios::tcHouseInlet001Fun02BedsDrawWhatTheInletHolds,
						"house_inlet_beds_draw_what_it_holds").ticks(20));

		private Roster() {}
	}

	/** The line along +X: the cables meet the inlet's west face. */
	private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos CABLE_B = new BlockPos(3, 2, 1);
	private static final BlockPos INLET = new BlockPos(4, 2, 1);

	private HouseInletScenarios() {}

	/**
	 * @implements TC-HOUSE-INLET-001-FUN01 — the line runs along +X, so the cable meets the inlet's west face. Facing
	 *     north (the default), the inlet takes nothing in forty ticks and exposes no port on the west; turned to face
	 *     west, the same line fills it. The cables held charge all along (the control), so the wall face refused it.
	 */
	public static void tcHouseInlet001Fun01TakesEnergyOnlyThroughItsOuterFace(GameTestHelper helper) {
		helper.setBlock(SOURCE, ModContent.CREATIVE_ENERGY_SOURCE.get());
		helper.setBlock(CABLE_A, ModContent.COPPER_CABLE.get());
		helper.setBlock(CABLE_B, ModContent.COPPER_CABLE.get());
		helper.setBlock(INLET, ModContent.HOUSE_INLET.get());
		HouseInletBlockEntity inlet = helper.getBlockEntity(INLET, HouseInletBlockEntity.class);
		if (inlet.energyRoleForFace(Direction.WEST) != EnergyRole.NONE || inlet.energyPort(Direction.WEST) != null
				|| inlet.energyRoleForFace(Direction.NORTH) != EnergyRole.IN) {
			helper.fail("facing north the inlet must take EU on the north face only: west "
					+ inlet.energyRoleForFace(Direction.WEST) + ", north " + inlet.energyRoleForFace(Direction.NORTH));
			return;
		}
		long cablePeak = drive(helper, 40);
		if (cablePeak <= 0L) {
			helper.fail("control: the source never charged the cables, so the wall face proved nothing");
			return;
		}
		if (inlet.storedEnergy() != 0L) {
			helper.fail("a cable on a wall face fed the inlet " + inlet.storedEnergy() + " EU");
			return;
		}
		helper.setBlock(INLET, ModContent.HOUSE_INLET.get().defaultBlockState()
				.setValue(HouseInletBlock.FACING, Direction.WEST));
		HouseInletBlockEntity turned = helper.getBlockEntity(INLET, HouseInletBlockEntity.class);
		drive(helper, 40);
		if (turned.storedEnergy() <= 0L) {
			helper.fail("the inlet facing the cable took nothing in forty ticks");
			return;
		}
		for (Direction face : Direction.values()) {
			EnergyRole want = face == Direction.WEST ? EnergyRole.IN : EnergyRole.NONE;
			if (turned.energyRoleForFace(face) != want) {
				helper.fail("facing west, the " + face + " face must be " + want + ", is "
						+ turned.energyRoleForFace(face));
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Ticks the line in the game's order (source, cables, the networks, the inlet) and returns the highest buffer
	 * either cable held at the end of a tick.
	 */
	private static long drive(GameTestHelper helper, int ticks) {
		long peak = 0L;
		for (int i = 0; i < ticks; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SOURCE));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, CABLE_A));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, CABLE_B));
			NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, INLET));
			for (BlockPos cable : List.of(CABLE_A, CABLE_B)) {
				if (EnergyScenarioSupport.be(helper, cable) instanceof CableBlockEntity be) {
					peak = Math.max(peak, be.getEnergyStorage().getAmount());
				}
			}
		}
		return peak;
	}

	/**
	 * @implements TC-HOUSE-INLET-001-FUN02 — a bed draws through {@code drawEnergy}: never more than the inlet holds,
	 *     nothing for a request of zero or less, and what it took leaves the buffer.
	 */
	public static void tcHouseInlet001Fun02BedsDrawWhatTheInletHolds(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 2, 1);
		helper.setBlock(at, ModContent.HOUSE_INLET.get());
		HouseInletBlockEntity inlet = helper.getBlockEntity(at, HouseInletBlockEntity.class);
		inlet.getEnergyStorage().setAmountUntracked(300L);
		if (inlet.drawEnergy(0L) != 0L || inlet.drawEnergy(-5L) != 0L) {
			helper.fail("a request of zero or less must take nothing");
			return;
		}
		long first = inlet.drawEnergy(200L);
		long second = inlet.drawEnergy(200L);
		if (first != 200L || second != 100L || inlet.storedEnergy() != 0L) {
			helper.fail("from 300 EU two requests of 200 must take 200 then 100 and leave 0: took " + first + ", "
					+ second + ", left " + inlet.storedEnergy());
			return;
		}
		helper.succeed();
	}
}
