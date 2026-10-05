package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.StorageFeedShare;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The boundaries between the three storage discharge channels of ADR-004 (MOD-715, batch 0), asserted as
 * rules rather than numbers — the numbers of the same circuits are pinned by
 * {@code EnergyFlowFieldGoldenScenarios}.
 *
 * <ol>
 *   <li><b>backup</b> opens on a generator deficit and closes the moment the generators cover the
 *       machines;</li>
 *   <li><b>cascade</b> opens with no machine demand, and outranks the feed: a donor the cascade levels
 *       ends below the reserve the feed would have stopped it at, and a fund on the bus gets none of it
 *       (MOD-731);</li>
 *   <li><b>feed</b> opens only when both are closed, and never takes a donor below its reserve.</li>
 * </ol>
 *
 * <p>Every rule is read off the network's own {@link EnergyNetwork#lastTickFromStorage()} as well as off
 * the buffers: what storage discharged into the line must be exactly what the donor lost.
 */
public final class DischargeChannelBoundaryScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(DischargeChannelBoundaryScenarios::backupOpensOnAGeneratorDeficit,
						"mod715_backup_opens_on_a_generator_deficit"),
				RosterEntry.of(DischargeChannelBoundaryScenarios::backupClosesWhenGeneratorsCover,
						"mod715_backup_closes_when_generators_cover"),
				RosterEntry.of(DischargeChannelBoundaryScenarios::cascadeOpensWithoutMachineDemand,
						"mod715_cascade_opens_without_machine_demand"),
				RosterEntry.of(DischargeChannelBoundaryScenarios::cascadeOutranksTheFeed,
						"mod715_cascade_outranks_the_feed"),
				RosterEntry.of(DischargeChannelBoundaryScenarios::feedKeepsTheDonorReserve,
						"mod715_feed_keeps_the_donor_reserve"));

		private Roster() {}
	}

	private static final BlockPos DONOR = new BlockPos(0, 2, 1);
	private static final BlockPos SIDE_BOX = new BlockPos(2, 2, 0);
	private static final BlockPos FAR_END = new BlockPos(5, 2, 1);
	private static final BlockPos FUND = new BlockPos(2, 2, 2);

	private DischargeChannelBoundaryScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	/** Drive {@code ticks} ticks and return the EU the network reports as drawn out of storage in total. */
	private static long drawnFromStorage(EnergyGoldenRig rig, int ticks) {
		long total = 0;
		for (int t = 0; t < ticks; t++) {
			rig.step();
			EnergyNetwork net = rig.network();
			total += net == null ? 0 : net.lastTickFromStorage();
		}
		return total;
	}

	/** Source of {@code output} EU/t, four cables, a working macerator, a full box on the bus's side. */
	private static EnergyGoldenRig machineLine(GameTestHelper helper, int output) {
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "boundary");
		rig.source(p(0, 1), output);
		rig.run(p(1, 1), p(4, 1));
		rig.machine(FAR_END, ModContent.MACERATOR.get(), Direction.EAST, new ItemStack(Items.RAW_IRON, 16));
		rig.store(SIDE_BOX, ModContent.BATTERY_BOX.get(), Direction.NORTH, Config.batteryBoxBuffer);
		return rig;
	}

	/**
	 * Channel 1 opens: a 4 EU/t source cannot cover a macerator, so the box beside the bus discharges, and
	 * what the network says it drew is exactly what the box lost.
	 *
	 * @implements MOD-715-DC01 — backup power opens on a generator deficit
	 */
	public static void backupOpensOnAGeneratorDeficit(GameTestHelper helper) {
		EnergyGoldenRig rig = machineLine(helper, 4);
		long drawn = drawnFromStorage(rig, 30);
		long lost = Config.batteryBoxBuffer - rig.amountAt(SIDE_BOX);
		if (drawn <= 0) {
			helper.fail("channel 1: a 4 EU/t source short of a working macerator drew nothing from the box");
			return;
		}
		if (drawn != lost) {
			helper.fail("channel 1: the network reports " + drawn + " EU drawn from storage, the box lost " + lost);
			return;
		}
		helper.succeed();
	}

	/**
	 * Channel 1 closes when the generators cover the machines, and nothing else opens: the box's input face
	 * looks at air, so it is no sink, and there is no other store or fund on the bus.
	 *
	 * @implements MOD-715-DC02 — backup closes when the generators cover the demand
	 */
	public static void backupClosesWhenGeneratorsCover(GameTestHelper helper) {
		EnergyGoldenRig rig = machineLine(helper, 64);
		long drawn = drawnFromStorage(rig, 30);
		if (drawn != 0 || rig.amountAt(SIDE_BOX) != Config.batteryBoxBuffer) {
			helper.fail("channel 1 must stay closed while a 64 EU/t source covers the macerator: drawn " + drawn
					+ ", box holds " + rig.amountAt(SIDE_BOX));
			return;
		}
		helper.succeed();
	}

	/** Copper cables between the two boxes of {@link #boxToBox}. */
	private static final int BOX_TO_BOX_CABLES = 4;

	/** A box of {@code donor} EU discharging along four cables into an empty box. */
	private static EnergyGoldenRig boxToBox(GameTestHelper helper, long donor) {
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "boundary");
		rig.store(DONOR, ModContent.BATTERY_BOX.get(), Direction.WEST, donor);
		rig.run(p(1, 1), p(BOX_TO_BOX_CABLES, 1));
		rig.store(FAR_END, ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		return rig;
	}

	/**
	 * Channel 2 opens with no machine anywhere: a full box levels into an empty one, and the network's
	 * "drawn from storage" is the donor's loss to the EU.
	 *
	 * @implements MOD-715-DC03 — the cascade opens without machine demand
	 */
	public static void cascadeOpensWithoutMachineDemand(GameTestHelper helper) {
		EnergyGoldenRig rig = boxToBox(helper, Config.batteryBoxBuffer);
		long drawn = drawnFromStorage(rig, 60);
		long lost = Config.batteryBoxBuffer - rig.amountAt(DONOR);
		if (drawn <= 0 || rig.amountAt(FAR_END) <= 0) {
			helper.fail("channel 2: a full box beside an empty one moved nothing (drawn " + drawn + ", receiver "
					+ rig.amountAt(FAR_END) + ")");
			return;
		}
		if (drawn != lost) {
			helper.fail("channel 2: the network reports " + drawn + " EU drawn from storage, the donor lost " + lost);
			return;
		}
		helper.succeed();
	}

	/**
	 * Channel 2 outranks channel 3: with a fund on the same bus, a box at 60 % still levels into the empty
	 * box — down below the reserve floor that the feed would have stopped it at — and the fund gets none of
	 * it (MOD-731). Before MOD-731 the end state "donor below the floor, empty box above zero" held under the
	 * defect too (donor 498, box 480, fund 11 022), so the rule is the three checks together: below the
	 * floor, levelled, and the fund holding no more than twice the tail the cables carry — the closing
	 * cascade re-opens for a smaller round as the tail lands (measured: 52 EU against a 48 EU tail; see
	 * {@code CascadeAddressScenarios.closingLeak}).
	 *
	 * @implements MOD-715-DC04 — the cascade outranks the feed
	 */
	public static void cascadeOutranksTheFeed(GameTestHelper helper) {
		EnergyGoldenRig rig = boxToBox(helper, Config.batteryBoxBuffer * 6L / 10L);
		rig.store(FUND, ModContent.TELEPORTER.get(), Direction.SOUTH, 0L);
		drawnFromStorage(rig, 1200);
		long floor = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		long deadband = CableType.COPPER.segmentBuffer();
		long tail = BOX_TO_BOX_CABLES * deadband;
		long donor = rig.amountAt(DONOR);
		long far = rig.amountAt(FAR_END);
		long fund = rig.amountAt(FUND);
		if (donor >= floor) {
			helper.fail("channel 2 must run before the feed: the donor ended at " + donor + " (reserve floor " + floor
					+ "), the empty box at " + far);
			return;
		}
		if (fund > 2 * tail) {
			helper.fail("channel 2 is for the boxes: the fund took " + fund + " EU of it, more than twice the tail"
					+ " of " + tail + " EU the cables can hold; donor " + donor + ", box " + far);
			return;
		}
		if (Math.abs(donor - far) > deadband + tail) {
			helper.fail("channel 2 did not level the boxes: donor " + donor + ", box " + far + " (allowed gap "
					+ (deadband + tail) + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * Channel 3 opens when both earlier channels are closed — no machine, no cascade partner — and it stops
	 * at the donor's reserve.
	 *
	 * @implements MOD-715-DC05 — the feed opens last and keeps the donor's reserve
	 */
	public static void feedKeepsTheDonorReserve(GameTestHelper helper) {
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, "boundary");
		rig.store(DONOR, ModContent.BATTERY_BOX.get(), Direction.WEST, Config.batteryBoxBuffer);
		rig.run(p(1, 1), p(3, 1));
		rig.store(p(4, 1), ModContent.TELEPORTER.get(), Direction.EAST, 0L);
		long drawn = drawnFromStorage(rig, 1200);
		long floor = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		long lost = Config.batteryBoxBuffer - rig.amountAt(DONOR);
		if (rig.amountAt(p(4, 1)) <= 0 || rig.amountAt(DONOR) < floor) {
			helper.fail("channel 3: the fund holds " + rig.amountAt(p(4, 1)) + ", the donor " + rig.amountAt(DONOR)
					+ " (reserve floor " + floor + ")");
			return;
		}
		if (drawn != lost) {
			helper.fail("channel 3: the network reports " + drawn + " EU drawn from storage, the donor lost " + lost);
			return;
		}
		helper.succeed();
	}
}
