package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.energy.StorageFeedShare;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.driveWithNetwork;

/**
 * MOD-353, step 0 — the <b>discriminating experiment</b>, run as a test rather than by hand.
 *
 * <p>The task's code audit predicts a hard zero: a Battery Box cabled to a Teleporter never moves a
 * single EU, because the station is an {@code isEnergyStorageSink} and therefore contributes nothing
 * to {@code machineDemand}, which leaves {@code storageBudget == 0} and closes the backup-discharge
 * stage; the cascade is closed separately by {@code acceptsCascade() == false}.
 *
 * <p>The player observed something else — charging through one box, nothing through two. That cannot
 * be produced by the code as read, so before any fix is designed the prediction has to be confirmed
 * on an isolated stand. <b>If scene 1 moves even 1 EU there is an unaccounted path and the design of
 * the fix changes.</b>
 *
 * <p>These are written as assertions of the CURRENT behaviour, so they are honest about what the mod
 * does today. Scene 1 will have to be inverted by the fix — that inversion is the point, and it is
 * the same "the assertion flips and history shows why" pattern MOD-314 used when it replaced
 * {@code storageDoesNotChargeStorage}.
 */
public final class Mod353DiagnosticScenarios {

	private Mod353DiagnosticScenarios() {}

	// Scene 1 — box → 5 cables → teleporter, and nothing else on the segment.
	private static final BlockPos S1_BOX = new BlockPos(1, 2, 1);
	private static final int S1_CABLES = 5;
	private static final BlockPos S1_TELEPORTER = new BlockPos(1 + S1_CABLES + 1, 2, 1);

	// Scene 3 — box flush against the teleporter, no cable at all.
	private static final BlockPos S3_BOX = new BlockPos(1, 2, 4);
	private static final BlockPos S3_TELEPORTER = new BlockPos(2, 2, 4);

	private static long teleporterCharge(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof TeleporterBlockEntity t ? t.getEnergyStorage().getAmount() : -1L;
	}

	/**
	 * MOD-353 scene 1 — the critical negative control: a full Battery Box, five copper cables, a
	 * Teleporter at the far end, and <b>no generator, panel or machine anywhere on the segment</b>.
	 *
	 * <p>Asserted a hard zero before the fix; now asserts the opposite plus the donor's reserve.
	 */
	public static void scene1BoxOverCableToTeleporter(GameTestHelper helper) {
		// OUT face must look at the cable: FACING is the INPUT face, so point it away (west) and the
		// output lands on the east side where the cable run starts.
		helper.setBlock(S1_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		BlockPos[] chain = new BlockPos[S1_CABLES + 2];
		chain[0] = S1_BOX;
		for (int i = 0; i < S1_CABLES; i++) {
			BlockPos cable = S1_BOX.offset(1 + i, 0, 0);
			helper.setBlock(cable, ModContent.COPPER_CABLE.get());
			chain[1 + i] = cable;
		}
		// CRITICAL: facingAwareRole makes the FACING face INERT and every other face an input. The cable
		// arrives from the west, so the station must NOT face west — otherwise energy is offered to a dead
		// face and the test reads "no transfer" for the wrong reason (a green that proves nothing).
		helper.setBlock(S1_TELEPORTER, ModContent.TELEPORTER.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		chain[chain.length - 1] = S1_TELEPORTER;

		if (be(helper, S1_BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);
		} else {
			helper.fail("scene 1: battery box did not place");
			return;
		}
		if (be(helper, S1_TELEPORTER) instanceof TeleporterBlockEntity t) {
			t.getEnergyStorage().setAmountUntracked(0L);
		} else {
			helper.fail("scene 1: teleporter did not place");
			return;
		}

		driveWithNetwork(helper, 200, chain);

		long moved = teleporterCharge(helper, S1_TELEPORTER);
		long cableHeld = 0;
		for (int i = 0; i < S1_CABLES; i++) {
			if (be(helper, S1_BOX.offset(1 + i, 0, 0)) instanceof CableBlockEntity c) {
				cableHeld += c.getEnergyStorage().getAmount();
			}
		}
		long boxLeft = be(helper, S1_BOX) instanceof BatteryBoxBlockEntity b
				? b.getEnergyStorage().getAmount() : -1L;

		// THE ASSERTION IS INVERTED ON PURPOSE. Before the MOD-353 fix this read `moved != 0 -> fail`,
		// because a storage-only segment delivered a hard zero and the experiment existed to prove it.
		// The fix opened the feed stage, so the same stand must now charge. Keeping the test at the same
		// name and coordinates makes the flip visible in history, exactly as MOD-314 did when it replaced
		// storageDoesNotChargeStorage with its opposite.
		if (moved <= 0L) {
			helper.fail("MOD-353: teleporter still received nothing over cable from a full battery box —"
					+ " the storage feed stage did not run. box=" + boxLeft + " cables=" + cableHeld);
			return;
		}
		// The donor must stop at its reserve, never below: that promise is the whole reason this channel
		// is safe to enable by default (MOD-314 R3 must not come back).
		long floor = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		if (boxLeft < floor) {
			helper.fail("MOD-353: donor fell below its reserve — " + boxLeft + " < " + floor
					+ ". The feed channel must never drain a store past storageFeedReserveFraction.");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-353 scene 3 — the same pair with no cable at all: the box sits flush against the station.
	 *
	 * <p>{@code DirectAdjacencyDistributor} applies the cascade gate only to cascade stores, so the
	 * station is fed unconditionally here. Asserts that this path really is open today — it is the
	 * mod's existing, undocumented answer to "I want it charged now", and the fix must not close it by
	 * accident.
	 */
	public static void scene3BoxFlushAgainstTeleporter(GameTestHelper helper) {
		helper.setBlock(S3_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		// Same trap as scene 1: the box is west of the station, so the station faces east and keeps its
		// west face live as an input.
		helper.setBlock(S3_TELEPORTER, ModContent.TELEPORTER.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));

		if (be(helper, S3_BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);
		} else {
			helper.fail("scene 3: battery box did not place");
			return;
		}
		if (be(helper, S3_TELEPORTER) instanceof TeleporterBlockEntity t) {
			t.getEnergyStorage().setAmountUntracked(0L);
		} else {
			helper.fail("scene 3: teleporter did not place");
			return;
		}

		driveWithNetwork(helper, 40, S3_BOX, S3_TELEPORTER);

		long moved = teleporterCharge(helper, S3_TELEPORTER);
		if (moved <= 0L) {
			helper.fail("MOD-353 scene 3: a box placed flush against the teleporter moved " + moved
					+ " EU — the direct, cable-less path was expected to feed it unconditionally.");
			return;
		}
		helper.succeed();
	}

	// ── MOD-353 coverage beyond the experiment ────────────────────────────────────────────────────
	// Each scene below is placed by exactly one scenario, so scenes may share coordinates with the
	// experiment scenes above. Every scene stays inside the 8x8x8 gametest structure both lanes
	// register on (MOD-445): the P and C scenes used to sit at z = 10 / 13, outside the box the
	// engine force-loads and clears (see NeoForgeGameTests#RIG_STRUCTURE).

	private static final BlockPos M_BOX = new BlockPos(1, 2, 7);
	private static final BlockPos M_CABLE = new BlockPos(2, 2, 7);
	private static final BlockPos M_MACHINE = new BlockPos(3, 2, 7);
	private static final BlockPos M_TELEPORTER = new BlockPos(4, 2, 7);

	private static final BlockPos P_BOX = new BlockPos(1, 2, 4);
	private static final BlockPos P_CABLE = new BlockPos(2, 2, 4);
	private static final BlockPos P_PAD = new BlockPos(3, 2, 4);

	private static final BlockPos C_BOX = new BlockPos(1, 2, 6);
	private static final BlockPos C_CABLE = new BlockPos(2, 2, 6);
	private static final BlockPos C_TELEPORTER = new BlockPos(3, 2, 6);

	/**
	 * MOD-353 — machines keep their priority: the feed stage runs only when nothing else wants power.
	 *
	 * <p>Without this the fix would be a balance change, not a bug fix: a Teleporter on a shared bus would
	 * start competing with the base's machines, which is precisely what {@code isEnergyStorageSink} was
	 * introduced to prevent (MOD-009).
	 */
	public static void mod353StorageFeedYieldsToMachines(GameTestHelper helper) {
		helper.setBlock(M_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(M_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(M_MACHINE, ModContent.ELECTRIC_FURNACE.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(M_TELEPORTER, ModContent.TELEPORTER.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		if (be(helper, M_BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);
		}
		if (be(helper, M_TELEPORTER) instanceof TeleporterBlockEntity t) {
			t.getEnergyStorage().setAmountUntracked(0L);
		}
		if (be(helper, M_MACHINE) instanceof dev.alaindustrial.block.entity.MachineBlockEntity m) {
			m.getEnergyStorage().setAmountUntracked(0L);
		}

		driveWithNetwork(helper, 60, M_BOX, M_CABLE, M_MACHINE, M_TELEPORTER);

		long machine = be(helper, M_MACHINE) instanceof dev.alaindustrial.block.entity.MachineBlockEntity m
				? m.getEnergyStorage().getAmount() : -1L;
		if (machine <= 0L) {
			helper.fail("machine on the same bus got nothing (" + machine + ") — backup power must still"
					+ " run and must outrank the storage feed");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-353 — the Charging Station is fixed by the same mechanism, not by a teleporter-shaped patch.
	 *
	 * <p>It had the identical defect: a storage sink outside the cascade, so a cabled Battery Box fed it
	 * nothing. If this ever goes red while the teleporter test stays green, the fix has drifted back into
	 * being about one block.
	 */
	public static void mod353ChargePadChargesFromBatteryBoxOverCable(GameTestHelper helper) {
		helper.setBlock(P_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(P_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(P_PAD, ModContent.CHARGE_PAD.get());
		if (be(helper, P_BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(Config.batteryBoxBuffer);
		}
		if (be(helper, P_PAD) instanceof dev.alaindustrial.block.entity.MachineBlockEntity pad) {
			pad.getEnergyStorage().setAmountUntracked(0L);
		} else {
			helper.fail("charge pad did not place");
			return;
		}

		driveWithNetwork(helper, 120, P_BOX, P_CABLE, P_PAD);

		long got = be(helper, P_PAD) instanceof dev.alaindustrial.block.entity.MachineBlockEntity padEnd
				? padEnd.getEnergyStorage().getAmount() : -1L;
		if (got <= 0L) {
			helper.fail("charging station received " + got + " EU from a cabled battery box — the MOD-353"
					+ " feed stage must cover every non-cascade sink, not just the teleporter");
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-314 R3 — the cascade must NOT pour a Battery Box into the Teleporter's fund.
	 *
	 * <p><b>Written here for the first time.</b> MOD-314 recorded this decision and claimed a test for it,
	 * but the audit found none in either loader's registry: the rule lived only in the default of
	 * {@code acceptsCascade()}. MOD-353 opens a second, deliberately different route from a store to the
	 * fund, so the distinction now has to be pinned rather than assumed.
	 *
	 * <p>The stand is the one from the original argument: a box at 50 % against an almost-empty fund. By
	 * <em>fill fraction</em> the cascade would happily empty the box into it. The feed channel is allowed
	 * to move energy here — that is the MOD-353 fix — but it must stop at the donor's reserve, which the
	 * cascade never would.
	 */
	public static void mod314CascadeIgnoresTeleporterFund(GameTestHelper helper) {
		helper.setBlock(C_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(C_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(C_TELEPORTER, ModContent.TELEPORTER.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));

		long start = Config.batteryBoxBuffer / 2;
		if (be(helper, C_BOX) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().setAmountUntracked(start);
		}
		if (be(helper, C_TELEPORTER) instanceof TeleporterBlockEntity t) {
			t.getEnergyStorage().setAmountUntracked(Config.teleporterBuffer / 50);  // ~2 %, as in the MOD-314 argument
		}

		driveWithNetwork(helper, 200, C_BOX, C_CABLE, C_TELEPORTER);

		long boxLeft = be(helper, C_BOX) instanceof BatteryBoxBlockEntity b
				? b.getEnergyStorage().getAmount() : -1L;
		long floor = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		// A box that starts AT its reserve must not be drained at all — by the cascade or by anything else.
		if (boxLeft < floor) {
			helper.fail("MOD-314 R3 violated: the box fell from " + start + " to " + boxLeft
					+ ", below the reserve floor " + floor + ". A proportional cascade into the fund would"
					+ " look exactly like this — the teleporter must stay outside it.");
			return;
		}
		helper.succeed();
	}
}
