package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.StorageFeedShare;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * MOD-731: the cascade's EU goes to the boxes it was released for. A box at 60 % levelling into an empty box
 * along four copper cables, with a sink that does not take the cascade — a Teleporter, an Energy Condenser —
 * touching the second cable. Before the fix the line served that sink with the cascade's EU (by room, 512 or
 * 128 against the box's 32) and seeded the sink field from it, so the far box sat behind a seam and the donor
 * drained to the far box's level: the golden {@code fund} ended at 498 / 480 / 11 022. With a generator on the
 * bus, such a sink still charges from the generators' surplus while the boxes level (owner, 2026-10-05) — the
 * last two scenarios.
 *
 * <p>Asserted as rules, not numbers — the numbers of the same circuit are the golden's
 * ({@code EnergyFlowFieldGoldenScenarios}). Every bound comes from the rig: the deadband is the copper
 * segment buffer, the tail — EU still in the cables when the cascade closes, which the in-flight correction
 * has already counted to the box and which may reach the fund once it is back in the pass (accepted by the
 * owner, 2026-10-05) — is that buffer times the cables. The rigs are translation-invariant: positions are
 * relative and nothing is ordered by absolute coordinates.
 */
public final class CascadeAddressScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CascadeAddressScenarios::cascadeBesideAFundLevelsTheBoxes,
						"mod731_cascade_beside_a_fund_levels_the_boxes"),
				RosterEntry.of(CascadeAddressScenarios::condenserTakesNothingFromACascade,
						"mod731_condenser_takes_nothing_from_a_cascade"),
				RosterEntry.of(CascadeAddressScenarios::fundKeepsGeneratorSurplusWhileBoxesLevel,
						"mod731_fund_keeps_generator_surplus_while_boxes_level"),
				RosterEntry.of(CascadeAddressScenarios::fundChargesFromGeneratorSurplusBesideAPassThroughBox,
						"mod731_fund_charges_from_generator_surplus_beside_a_pass_through_box"));

		private Roster() {}
	}

	private static final BlockPos DONOR = p(0, 1);
	private static final BlockPos LAST_CABLE = p(4, 1);
	private static final BlockPos FAR_BOX = p(5, 1);
	/** Touches the second cable from the south, as in the golden {@code fund}. */
	private static final BlockPos FUND = p(2, 2);
	/** Sits on the second cable: the condenser takes energy through its vertical faces only. */
	private static final BlockPos CONDENSER = new BlockPos(2, 3, 1);
	/** Touches the first cable from the north. */
	private static final BlockPos GENERATOR = p(1, 0);
	private static final int CABLES = 4;
	private static final int TICKS = 1200;
	/** A tick well inside the levelling: 6 000 EU move at 12 EU/t, so it runs to about tick 500. */
	private static final int MID_CASCADE = 200;
	/** How long a window of the generator scenario's bank check is. */
	private static final int WINDOW = 40;
	/**
	 * The generator of the third scenario. Below the copper segment buffer on purpose: a 20 EU/t source on
	 * the donor's cable refills it every tick before the stores charge the line, so the donor never moves and
	 * the far box fills from the generator alone — the boxes would level near the top, never both below the
	 * reserve, and the bank rule would check nothing. At 4 EU/t both the generator and the donor move EU.
	 * Measured 2026-10-05: before the owner's decision the boxes levelled near 7 200 EU each at tick 601 while
	 * the Teleporter waited (22 EU by then, 2 152 by tick 1 200); now the Teleporter takes the generator's
	 * 4 EU/t from tick 3, the boxes level near 6 000 each at tick 749 with the Teleporter at 2 988 EU, and it
	 * ends at 4 600.
	 */
	private static final int GENERATOR_OUTPUT = 4;

	/** The fourth scenario's generator: beside the second cable, with a cable of its own to the east. */
	private static final BlockPos STRONG_GENERATOR = p(2, 0);
	/** The generator's own cable: touches it from the east and the third bus cable from the north. */
	private static final BlockPos GENERATOR_CABLE = p(3, 0);
	/**
	 * Stronger than the two cables the generator touches can take in a tick (two copper segments), so the line,
	 * not the generator, sets what it puts in.
	 */
	private static final int STRONG_OUTPUT = 32;
	/**
	 * What the network behind the far box takes from it every tick — a machine network hungrier than the copper
	 * wire that feeds the box, as in the review's scheme. The box's own output: one LV packet.
	 */
	private static final long RELAY = 32;

	private CascadeAddressScenarios() {
	}

	private static BlockPos p(int x, int z) {
		return new BlockPos(x, 2, z);
	}

	/** The cascade's "too small to bother" threshold on a copper line — its segment buffer (MOD-219). */
	private static long deadband() {
		return CableType.COPPER.segmentBuffer();
	}

	/** EU the rig's cables can hold: what may still be in transit when the cascade closes. */
	private static long tail() {
		return CABLES * deadband();
	}

	/**
	 * The most a sink outside the cascade may end up with from storage over a levelling: the tail, twice.
	 * One closing is not one tail. Measured on the fund rig (2026-10-05): with 48 EU in the cables at tick
	 * 480, the donor gave 256 EU more by tick 520, the far box got 252 and the Teleporter 52 — the cascade
	 * closed, the tail partly landed in the fund, the box came out short of what the in-flight correction had
	 * counted, and the cascade re-opened for a smaller round before it closed for good. Twice the tail leaves
	 * room for those smaller rounds and still fails the defect by two orders of magnitude (11 022 EU).
	 */
	private static long closingLeak() {
		return 2 * tail();
	}

	private static long donorStart() {
		return Config.batteryBoxBuffer * 6L / 10L;
	}

	/** Donor at 60 % facing west (OUT into the cable), four copper cables, an empty box facing west (IN). */
	private static EnergyGoldenRig twoBoxBus(GameTestHelper helper, String name) {
		return twoBoxBus(helper, name, donorStart());
	}

	/** {@link #twoBoxBus(GameTestHelper, String)} with the donor holding {@code donor} EU. */
	private static EnergyGoldenRig twoBoxBus(GameTestHelper helper, String name, long donor) {
		EnergyGoldenRig.clear(helper);
		EnergyGoldenRig rig = new EnergyGoldenRig(helper, name);
		rig.store(DONOR, ModContent.BATTERY_BOX.get(), Direction.WEST, donor);
		rig.run(p(1, 1), LAST_CABLE);
		rig.store(FAR_BOX, ModContent.BATTERY_BOX.get(), Direction.WEST, 0L);
		return rig;
	}

	/** The most the energy block at {@code pos} takes in one tick, or 0 when there is none. */
	private static long intake(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof EnergyBlockEntity e ? e.getEnergyStorage().maxInsert : 0L;
	}

	/** Take up to {@code eu} out of the energy block at {@code pos}; return what was taken. */
	private static long drain(GameTestHelper helper, BlockPos pos, long eu) {
		return be(helper, pos) instanceof EnergyBlockEntity e ? e.getEnergyStorage().drainInternal(eu) : 0L;
	}

	/** The EU in the cable at {@code pos}, read from the cable's own buffer (ADR-001). */
	private static long cableCharge(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof CableBlockEntity cable ? cable.getEnergyStorage().getAmount() : -1L;
	}

	/**
	 * Drive {@code rig} for the whole run; return the charge of the cable next to the far box in the middle of
	 * the cascade — energy that travels along the line has to be in that cable on its way.
	 */
	private static long driveReadingTheLastCable(GameTestHelper helper, EnergyGoldenRig rig) {
		long midCable = -1L;
		for (int t = 1; t <= TICKS; t++) {
			rig.step();
			if (t == MID_CASCADE) {
				midCable = cableCharge(helper, LAST_CABLE);
			}
		}
		return midCable;
	}

	/**
	 * The two boxes ended level and the donor kept its half: {@code null} when they did, the failure otherwise.
	 */
	private static String levelled(EnergyGoldenRig rig) {
		long donor = rig.amountAt(DONOR);
		long far = rig.amountAt(FAR_BOX);
		long floor = (donorStart() - tail()) / 2 - deadband();
		if (Math.abs(donor - far) > deadband() + tail()) {
			return "the boxes did not level: donor " + donor + ", far box " + far + " (allowed gap "
					+ (deadband() + tail()) + ")";
		}
		if (donor < floor) {
			return "the donor drained past half its charge: " + donor + " EU, floor " + floor;
		}
		return null;
	}

	/**
	 * The golden {@code fund} rig as a rule: the far box gets half the donor's charge along the wire, and the
	 * Teleporter gets nothing but the tail — after levelling the donor is below the feed's reserve, so the feed
	 * stays closed.
	 *
	 * @implements MOD-731-CF01 — while the cascade runs, a fund beside the bus takes nothing from it
	 */
	public static void cascadeBesideAFundLevelsTheBoxes(GameTestHelper helper) {
		EnergyGoldenRig rig = twoBoxBus(helper, "mod731");
		rig.store(FUND, ModContent.TELEPORTER.get(), Direction.SOUTH, 0L);
		long midCable = driveReadingTheLastCable(helper, rig);
		long fund = rig.amountAt(FUND);
		if (fund > closingLeak()) {
			helper.fail("the Teleporter took " + fund + " EU from a box levelling into another box; the cascade's"
					+ " EU is for the boxes, at most " + closingLeak() + " EU of tail may reach it");
			return;
		}
		if (midCable <= 0) {
			helper.fail("tick " + MID_CASCADE + ": the cable next to the far box holds " + midCable
					+ " EU — the cascade's energy is not travelling along the wire (ADR-001)");
			return;
		}
		String level = levelled(rig);
		if (level != null) {
			helper.fail(level + "; the Teleporter holds " + fund);
			return;
		}
		long reserve = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		if (rig.amountAt(DONOR) >= reserve) {
			helper.fail("precondition: after levelling the donor must sit below the feed's reserve (" + reserve
					+ "), or the feed could have reached the fund legally; donor " + rig.amountAt(DONOR));
			return;
		}
		helper.succeed();
	}

	/**
	 * The same rig with an Energy Condenser on the line instead of a Teleporter: its spec says it cannot pull
	 * a store's charge at all, and a cascade beside it must not be the way around that.
	 *
	 * @implements MOD-731-CF02 — a condenser takes nothing from a cascade beyond the tail
	 */
	public static void condenserTakesNothingFromACascade(GameTestHelper helper) {
		EnergyGoldenRig rig = twoBoxBus(helper, "mod731");
		rig.block(CONDENSER, ModContent.ENERGY_CONDENSER.get());
		driveReadingTheLastCable(helper, rig);
		long condenser = rig.amountAt(CONDENSER);
		if (condenser > closingLeak()) {
			helper.fail("the condenser banked " + condenser + " EU out of a cascade between two boxes; it may take"
					+ " generation only, at most " + closingLeak() + " EU of tail");
			return;
		}
		String level = levelled(rig);
		if (level != null) {
			helper.fail(level + "; the condenser holds " + condenser);
			return;
		}
		helper.succeed();
	}

	/**
	 * The least a fund beside the bus gets in a tick once the line runs, from a generator of {@code output} EU/t:
	 * on a cascade tick it may take the generators' surplus — all of {@code output}, which is below the segment
	 * its cable carries (owner, 2026-10-05) — and on any other tick it shares the generator's EU with the far box
	 * by intake, rounded down ({@code EnergyShare.split}). The smaller of the two.
	 */
	private static long fundFloorPerTick(GameTestHelper helper, long output) {
		long fund = intake(helper, FUND);
		return Math.min(output, output * fund / (fund + intake(helper, FAR_BOX)));
	}

	/**
	 * The counter-test against over-correcting: a generator on the same bus. While the boxes level, the
	 * Teleporter keeps charging from the generator's surplus (owner, 2026-10-05), and does from the first ticks:
	 * when the boxes first come level, and again at the end, it holds at least the per-tick floor for every tick
	 * past the line's fill. The boxes level; and while both sit below the feed's reserve (the feed is closed by
	 * construction) the bank never loses more than the tail over a window — what a store releases goes to a
	 * store, and the Teleporter takes only what the generator put in.
	 *
	 * @implements MOD-731-CF03 — a fund keeps the generators' surplus while the boxes on its bus level
	 */
	public static void fundKeepsGeneratorSurplusWhileBoxesLevel(GameTestHelper helper) {
		EnergyGoldenRig rig = twoBoxBus(helper, "mod731");
		rig.source(GENERATOR, GENERATOR_OUTPUT);
		rig.store(FUND, ModContent.TELEPORTER.get(), Direction.SOUTH, 0L);
		long reserve = StorageFeedShare.reserveFloor(Config.batteryBoxBuffer, Config.storageFeedReserveFraction);
		long perTick = fundFloorPerTick(helper, GENERATOR_OUTPUT);
		long closestGap = Long.MAX_VALUE;
		int levelledAt = -1;
		long windowStartBank = -1L;
		boolean windowBelowReserve = true;
		for (int t = 1; t <= TICKS; t++) {
			rig.step();
			long donor = rig.amountAt(DONOR);
			long far = rig.amountAt(FAR_BOX);
			closestGap = Math.min(closestGap, Math.abs(donor - far));
			if (levelledAt < 0 && Math.abs(donor - far) <= deadband() + tail()) {
				levelledAt = t;
				long floor = (t - 2L * CABLES) * perTick;
				if (rig.amountAt(FUND) < floor) {
					helper.fail("tick " + t + ": the boxes have levelled, and the Teleporter holds "
							+ rig.amountAt(FUND) + " EU — under " + floor + " (" + perTick + " EU/t from tick "
							+ 2 * CABLES + "): it did not charge from the " + GENERATOR_OUTPUT
							+ " EU/t generator while they levelled");
					return;
				}
			}
			windowBelowReserve &= donor < reserve && far < reserve;
			if (t % WINDOW == 0) {
				long bank = donor + far;
				if (windowStartBank >= 0 && windowBelowReserve && bank < windowStartBank - tail()) {
					helper.fail("ticks " + (t - WINDOW) + "-" + t + ": both boxes below the feed's reserve, yet the"
							+ " bank fell from " + windowStartBank + " to " + bank + " EU — a store's discharge left"
							+ " the stores");
					return;
				}
				windowStartBank = bank;
				windowBelowReserve = donor < reserve && far < reserve;
			}
		}
		if (levelledAt < 0) {
			helper.fail("the boxes never levelled: closest gap " + closestGap + " EU (allowed "
					+ (deadband() + tail()) + "); donor " + rig.amountAt(DONOR) + ", far box " + rig.amountAt(FAR_BOX));
			return;
		}
		long fund = rig.amountAt(FUND);
		long floor = (TICKS - 2L * CABLES) * perTick;
		if (fund < floor) {
			helper.fail("the Teleporter got " + fund + " EU in " + TICKS + " ticks from a " + GENERATOR_OUTPUT
					+ " EU/t generator on its bus, under " + floor + " (" + perTick + " EU/t past the fill)");
			return;
		}
		helper.succeed();
	}

	/**
	 * The review's scheme (MOD-731, 2026-10-05): a full box, a box that passes everything it gets on to a hungrier
	 * network, a generator stronger than the wire, and a Teleporter on the bus. The far box never fills, so the
	 * cascade toward it never closes. Before the owner's decision the Teleporter sat every tick out and the
	 * generator's surplus was lost for good. Now it takes that surplus: it charges within the line's fill, and
	 * gets the one segment a tick the generator's second cable adds beyond what the far box drinks — while the
	 * far box keeps the wire's throughput and the bank never loses more than the tail and one tick of the
	 * generator (the surplus is last tick's).
	 *
	 * <p>The hungrier network is the far box's buffer emptied by {@link #RELAY} after every tick: the line only
	 * sees a box that does not grow. Measured 2026-10-05: before, the Teleporter got 0 EU in 1 200 ticks while
	 * the far box passed 14 364 EU on; now it charges from tick 2 and holds 14 388 EU, and the far box still
	 * passes 14 364 EU on — the generator now puts two segments a tick into the line instead of one.
	 *
	 * @implements MOD-731-CF04 — a fund charges from the generators' surplus beside a cascade that never closes
	 */
	public static void fundChargesFromGeneratorSurplusBesideAPassThroughBox(GameTestHelper helper) {
		EnergyGoldenRig rig = twoBoxBus(helper, "mod731", Config.batteryBoxBuffer);
		rig.cable(GENERATOR_CABLE);
		rig.source(STRONG_GENERATOR, STRONG_OUTPUT);
		rig.store(FUND, ModContent.TELEPORTER.get(), Direction.SOUTH, 0L);
		long bankStart = rig.amountAt(DONOR) + rig.amountAt(FAR_BOX);
		long bankFloor = bankStart - tail() - 2 * deadband();
		long relayed = 0L;
		int firstCharge = -1;
		for (int t = 1; t <= TICKS; t++) {
			rig.step();
			relayed += drain(helper, FAR_BOX, RELAY);
			if (firstCharge < 0 && rig.amountAt(FUND) > 0) {
				firstCharge = t;
			}
			long bank = rig.amountAt(DONOR) + rig.amountAt(FAR_BOX) + relayed;
			if (bank < bankFloor) {
				helper.fail("tick " + t + ": the bank (donor + far box + what it passed on) fell to " + bank
						+ " EU, under " + bankFloor + " — the Teleporter took a store's charge");
				return;
			}
		}
		long fund = rig.amountAt(FUND);
		long throughput = (TICKS - 2L * CABLES) * deadband();
		if (firstCharge < 0 || firstCharge > 2 * CABLES) {
			helper.fail("the Teleporter " + (firstCharge < 0 ? "never charged" : "first charged at tick " + firstCharge)
					+ " (holds " + fund + " EU), not within the line's fill (" + 2 * CABLES + " ticks): a "
					+ STRONG_OUTPUT + " EU/t generator's surplus did not reach it while the far box passed "
					+ relayed + " EU on");
			return;
		}
		if (fund < throughput) {
			helper.fail("the Teleporter got " + fund + " EU, under " + throughput + " — the segment a tick the"
					+ " generator's second cable adds beyond what the far box drinks was lost");
			return;
		}
		if (relayed < throughput) {
			helper.fail("the far box passed on " + relayed + " EU, under " + throughput + " — the Teleporter took the"
					+ " wire's throughput from it, not the generator's surplus (fund " + fund + ")");
			return;
		}
		helper.succeed();
	}
}
