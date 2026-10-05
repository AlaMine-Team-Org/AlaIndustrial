package dev.alaindustrial.gametest;

import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral gametest bodies for the Workstation (MOD-483, suite TC-WKST-001). Wrapped by the
 * Fabric {@code WorkstationGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}, {@code workstation_*}), so both loaders run the SAME bodies.
 *
 * <p>The rig is a 1×1×2 machine at {@code (1, 2, 1)}, which sits well inside the default 8×8×8
 * structure both lanes use — no structure of its own is needed.
 */
public final class WorkstationScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WorkstationScenarios::frm01TwoCasingsAssemble, "mod483_two_casings_assemble")
						.fabricId("WorkstationGameTest", "mod483TwoCasingsAssemble").ticks(20, 40),
				RosterEntry.of(WorkstationScenarios::frm02ThreeCasingsPairTheBottomTwo,
								"mod483_three_casings_pair_the_bottom_two")
						.fabricId("WorkstationGameTest", "mod483ThreeCasingsPairTheBottomTwo").ticks(20, 40),
				RosterEntry.of(WorkstationScenarios::brk01BreakingUpperDegradesLower,
								"mod483_breaking_upper_degrades_lower")
						.fabricId("WorkstationGameTest", "mod483BreakingUpperDegradesLower").ticks(20, 40),
				RosterEntry.of(WorkstationScenarios::brk02BreakingLowerDegradesUpper,
								"mod483_breaking_lower_degrades_upper")
						.fabricId("WorkstationGameTest", "mod483BreakingLowerDegradesUpper").ticks(20, 40),
				RosterEntry.of(WorkstationScenarios::nrg01OnlyTheLowerHalfTakesEnergy,
								"mod483_only_the_lower_half_takes_energy")
						.fabricId("WorkstationGameTest", "mod483OnlyTheLowerHalfTakesEnergy").ticks(20, 40),
				RosterEntry.of(WorkstationScenarios::nrg02ServedAlongsideHungryMachine,
								"mod691_served_alongside_hungry_machine")
						.fabricId("WorkstationGameTest", "mod691ServedAlongsideHungryMachine").ticks(20, 40));

		private Roster() {}
	}

	private WorkstationScenarios() {
	}

	private static final BlockPos BASE = new BlockPos(1, 2, 1);

	private static BlockState casing() {
		return ModContent.WORKSTATION.get().defaultBlockState();
	}

	private static void placePair(ServerLevel level, BlockPos base) {
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		// A programmatic setBlock never calls setPlacedBy (MOD-015), which is exactly why the assembly
		// hook is public and static — a scenario that could not reach it would have to assert on a
		// machine it has no way to build.
		WorkstationBlock.tryAssemble(level, base.above());
	}

	private static WorkstationPart partAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.hasProperty(WorkstationBlock.PART) ? state.getValue(WorkstationBlock.PART) : null;
	}

	/**
	 * Two casings stacked become one machine.
	 *
	 * @implements TC-WKST-001-FRM01 — assembly from two loose casings
	 */
	public static void frm01TwoCasingsAssemble(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		if (partAt(level, base) != WorkstationPart.LOWER) {
			helper.fail("the bottom casing must become the lower half, got " + partAt(level, base));
			return;
		}
		if (partAt(level, base.above()) != WorkstationPart.UPPER) {
			helper.fail("the top casing must become the upper half, got " + partAt(level, base.above()));
			return;
		}
		if (!(level.getBlockEntity(base) instanceof WorkstationBlockEntity)) {
			helper.fail("the lower half must carry the machine's block entity");
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the upper half leaves the lower one standing as a casing.
	 *
	 * <p>The point is that no removal hook is involved: {@code updateShape} alone answers every way a
	 * half can vanish, so this covers the pickaxe, an explosion and {@code /setblock} at once.
	 *
	 * @implements TC-WKST-001-BRK01 — losing the upper half degrades the lower
	 */
	public static void brk01BreakingUpperDegradesLower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		level.destroyBlock(base.above(), false);
		if (partAt(level, base) != WorkstationPart.SINGLE) {
			helper.fail("the lower half must fall back to a casing, got " + partAt(level, base));
			return;
		}
		helper.succeed();
	}

	/**
	 * Breaking the lower half leaves the upper one standing as a casing.
	 *
	 * @implements TC-WKST-001-BRK02 — losing the lower half degrades the upper
	 */
	public static void brk02BreakingLowerDegradesUpper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		placePair(level, base);

		level.destroyBlock(base, false);
		if (partAt(level, base.above()) != WorkstationPart.SINGLE) {
			helper.fail("the orphaned upper half must fall back to a casing, got "
					+ partAt(level, base.above()));
			return;
		}
		helper.succeed();
	}

	/**
	 * Energy goes into the lower half and nowhere else.
	 *
	 * <p>A loose casing that accepted energy would let a cable charge inventory standing in the world,
	 * and an upper half that accepted it would give the machine two independent buffers.
	 *
	 * @implements TC-WKST-001-NRG01 — only the assembled lower half is an energy sink
	 */
	public static void nrg01OnlyTheLowerHalfTakesEnergy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);

		level.setBlockAndUpdate(base, casing());
		if (level.getBlockEntity(base) instanceof WorkstationBlockEntity loose
				&& loose.energyRoleForFace(Direction.EAST) != EnergyRole.NONE) {
			helper.fail("a loose casing must be energy-inert on every face");
			return;
		}

		placePair(level, base);
		if (!(level.getBlockEntity(base) instanceof WorkstationBlockEntity lower)
				|| lower.energyRoleForFace(Direction.EAST) != EnergyRole.IN) {
			helper.fail("the assembled lower half must accept energy on its side faces");
			return;
		}
		if (!(level.getBlockEntity(base.above()) instanceof WorkstationBlockEntity upper)
				|| upper.energyRoleForFace(Direction.EAST) != EnergyRole.NONE) {
			helper.fail("the upper half must stay energy-inert");
			return;
		}
		// R-NRG-03: the face the player looks at carries no cable arm.
		if (lower.energyRoleForFace(level.getBlockState(base).getValue(WorkstationBlock.FACING))
				!= EnergyRole.NONE) {
			helper.fail("the front face must be energy-inert");
			return;
		}
		helper.succeed();
	}

	/**
	 * A stack of three casings resolves the same way every time.
	 *
	 * <p>Three in a column is the first ambiguous case a player can build, and "whichever pair the
	 * game noticed first" would be a different machine depending on which block was touched last.
	 * The rule is that a casing prefers the partner below it, so the bottom two pair up and the third
	 * is left over.
	 *
	 * @implements TC-WKST-001-FRM02 — a three-high stack assembles deterministically
	 */
	public static void frm02ThreeCasingsPairTheBottomTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		level.setBlockAndUpdate(base, casing());
		level.setBlockAndUpdate(base.above(), casing());
		level.setBlockAndUpdate(base.above(2), casing());
		WorkstationBlock.tryAssemble(level, base.above());
		WorkstationBlock.tryAssemble(level, base.above(2));

		if (partAt(level, base) != WorkstationPart.LOWER
				|| partAt(level, base.above()) != WorkstationPart.UPPER) {
			helper.fail("the bottom two casings must be the ones that pair up");
			return;
		}
		if (partAt(level, base.above(2)) != WorkstationPart.SINGLE) {
			helper.fail("the third casing must be left alone, got " + partAt(level, base.above(2)));
			return;
		}
		helper.succeed();
	}

	// ── MOD-691: where the station stands in the serve order ─────────────────────────────────────────

	private static final BlockPos SERVE_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos SERVE_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos SERVE_MAC = new BlockPos(3, 2, 1);
	/** Lower half; the upper half stands on it. South of the cable, so its NORTH face takes the arm. */
	private static final BlockPos SERVE_STATION = new BlockPos(2, 2, 2);

	/**
	 * A generator short of the demand, one copper cable, an LV machine and the workstation on that same
	 * cable: the station is served ALONGSIDE the hungry machine, as a machine, not after it as a store.
	 *
	 * <p>A characterization of the serve order the network applies today (MOD-691, variant (b)).
	 * The deficit is structural: a copper segment holds 12 EU and the macerator alone takes up to 32 EU/t,
	 * while forty ticks of at most 12 EU cannot fill its 800 EU buffer ({@code maceratorBuffer}), so over
	 * this run the machine never stops asking. Were the station a store
	 * (variant (a), ADR-002), it would get nothing until the macerator were full — this expectation is the
	 * one to flip if the owner chooses that. Two readings, both from the network itself: the station's own
	 * buffer, and {@code lastTickToStorage}, the part of each delivery the network booked as "into a store".
	 *
	 * <p>The station is deliberately not ticked: its upkeep would drain what it received, and the question
	 * here is only whether anything arrived.
	 *
	 * @implements TC-WKST-001-NRG02 — served as a machine on a short line
	 */
	public static void nrg02ServedAlongsideHungryMachine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(SERVE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(SERVE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(SERVE_MAC, ModContent.MACERATOR.get());
		BlockPos station = helper.absolutePos(SERVE_STATION);
		level.setBlockAndUpdate(station, casing().setValue(WorkstationBlock.FACING, Direction.SOUTH));
		level.setBlockAndUpdate(station.above(), casing().setValue(WorkstationBlock.FACING, Direction.SOUTH));
		WorkstationBlock.tryAssemble(level, station.above());
		if (EnergyScenarioSupport.be(helper, SERVE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (EnergyScenarioSupport.be(helper, SERVE_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
		if (partAt(level, station) != WorkstationPart.LOWER
				|| !(level.getBlockEntity(station) instanceof WorkstationBlockEntity)) {
			helper.fail("precondition: the workstation did not assemble at " + station);
			return;
		}

		long intoStorage = 0L;
		for (int i = 0; i < 40; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SERVE_GEN));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SERVE_CABLE));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, SERVE_MAC));
			NetworkManager.tickAll(level);
			EnergyNetwork net = NetworkManager.networkAt(level, helper.absolutePos(SERVE_CABLE));
			if (net != null) {
				intoStorage += net.lastTickToStorage();
			}
		}

		if (!(EnergyScenarioSupport.be(helper, SERVE_MAC) instanceof MaceratorBlockEntity mac)) {
			helper.fail("the macerator is gone");
			return;
		}
		long macAmount = mac.getEnergyStorage().getAmount();
		long macCapacity = mac.getEnergyStorage().getCapacity();
		if (macAmount <= 0) {
			helper.fail("precondition: the line delivered nothing to the macerator — the rig is not powered");
			return;
		}
		if (macAmount >= macCapacity) {
			helper.fail("precondition: the macerator filled up (" + macAmount + "/" + macCapacity
					+ "), so the line was never short and the serve order was not exercised");
			return;
		}
		long stationAmount = ((WorkstationBlockEntity) level.getBlockEntity(station)).getEnergyStorage().getAmount();
		if (stationAmount <= 0) {
			helper.fail("MOD-691: the workstation received nothing while the macerator was still hungry ("
					+ macAmount + "/" + macCapacity + ") — the network is serving it after the machines, as a"
					+ " store; MOD-691 variant (b) serves it as a machine");
			return;
		}
		if (intoStorage != 0L) {
			helper.fail("MOD-691: the network booked " + intoStorage + " EU as delivered into storage on a line"
					+ " whose only consumers are a macerator and the workstation — the station is being"
					+ " classed as a store");
			return;
		}
		helper.succeed();
	}
}
