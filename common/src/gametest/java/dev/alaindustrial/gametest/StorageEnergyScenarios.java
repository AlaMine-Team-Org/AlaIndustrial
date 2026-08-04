package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_CABLE;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_GEN;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_MAC;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.driveLine;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

/**
 * Loader-neutral world-based energy gametest bodies (MOD-022) — battery-box storage: charge
 * through the line, storage priorities and anti-wash (MOD-070), the idle-producer seam
 * (MOD-214), drop data components and system-wide conservation. Suite contract and shared
 * helpers: {@link EnergyScenarioSupport}.
 */
public final class StorageEnergyScenarios {

	private StorageEnergyScenarios() {}

	// ── scenario 0b: battery box carries its EU on drop (data-component seam, MOD-022) ────────────

	private static final BlockPos DROP = new BlockPos(1, 2, 1);

	/**
	 * A charged battery box emits its buffered EU as the {@code STORED_ENERGY} data component on
	 * {@code collectComponents()} — what the loot table's {@code copy_components} reads onto the drop. This
	 * proves the custom data component registers + carries on THIS loader — the NeoForge frozen-registry
	 * seam (MOD-022): NeoForge registers it via a {@code DeferredRegister} ({@code ModDataComponentsNeoForge}),
	 * and {@code ModDataComponents.STORED_ENERGY.get()} must resolve. Before the fix the component was
	 * unregistered on NeoForge, the loot table failed to parse, and a charged box dropped empty.
	 * Mirrors the Fabric-side {@code BatteryBoxGameTest.tcBatteryBox001Brk07}.
	 */
	public static void batteryBoxDropCarriesEnergy(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long charge = Math.min(12345L, bb.getEnergyStorage().getCapacity());
			bb.getEnergyStorage().amount = charge;
			DataComponentMap map = bb.collectComponents();
			Long carried = map.get(ModDataComponents.STORED_ENERGY.get());
			if (carried == null || carried.longValue() != charge) {
				helper.fail("battery box did not carry STORED_ENERGY on drop: " + carried + "/" + charge
						+ " (data component unregistered on this loader?)");
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}

	// ── MOD-314: a fuller store cascades into an emptier one, and stops when they level out ─────────

	private static final BlockPos WASH_SRC = new BlockPos(1, 2, 1);
	private static final BlockPos WASH_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos WASH_DST = new BlockPos(3, 2, 1);

	/**
	 * MOD-314 cascade: a charged BatteryBox cabled to an empty one (no generator, no machine) DOES charge
	 * it — that is the whole point of placing a second box to extend the bank.
	 *
	 * <p>This test replaces MOD-070's {@code storageDoesNotChargeStorage}, which asserted the exact
	 * opposite. That rule was written to stop two batteries washing energy back and forth, and it did —
	 * but it also blocked the legitimate "full box tops up empty box" case, which is the bug MOD-314 was
	 * filed for. Washing is now excluded by CascadeShare's construction (gradient + half step + deadband,
	 * and a donor never feeds another donor) rather than by banning the transfer outright, so the
	 * assertion inverts. Kept at the same coordinates and the same traceability id so the rewrite is
	 * visible in history instead of appearing as one test deleted and an unrelated one added.
	 *
	 * <p>Mirrors: NetworkGameTest.tcCable001Nrg06_cascadeChargesEmptyBatteryBoxOverCable
	 */
	public static void cascadeChargesEmptyBatteryBoxOverCable(GameTestHelper helper) {
		helper.setBlock(WASH_SRC, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(WASH_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(WASH_DST, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, WASH_SRC) instanceof BatteryBoxBlockEntity src) {
			src.getEnergyStorage().amount = Config.batteryBoxBuffer;
		}
		if (be(helper, WASH_DST) instanceof BatteryBoxBlockEntity dst) {
			dst.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 20; i++) {
			tick(helper, be(helper, WASH_SRC));
			tick(helper, be(helper, WASH_CABLE));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, WASH_DST));
		}
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(WASH_CABLE));
		if (net == null) {
			helper.fail("no energy network formed between the two BatteryBoxes — test cannot verify the cascade");
		}
		long dstEnd = be(helper, WASH_DST) instanceof BatteryBoxBlockEntity d ? d.getEnergyStorage().getAmount() : -1;
		if (dstEnd <= 0L) {
			helper.fail("empty BatteryBox was not charged from the full one: " + dstEnd
					+ " (MOD-314: a fuller store must cascade into an emptier one once machines are covered)");
		}
		long srcEnd = be(helper, WASH_SRC) instanceof BatteryBoxBlockEntity s ? s.getEnergyStorage().getAmount() : -1;
		if (srcEnd >= Config.batteryBoxBuffer) {
			helper.fail("donor BatteryBox did not give anything away: " + srcEnd);
		}
		// The donor must never end up emptier than the box it fed: that would mean the half step
		// overshot, which is the first half of an oscillation.
		if (srcEnd < dstEnd) {
			helper.fail("cascade overshot — donor " + srcEnd + " ended below receiver " + dstEnd);
		}
		helper.succeed();
	}

	// ── MOD-314 anti-wash: a levelled pair must stop, and stop COSTING ─────────────────────────────

	private static final BlockPos EQ_DONOR = new BlockPos(1, 2, 1);
	private static final BlockPos EQ_RECEIVER = new BlockPos(12, 2, 1);
	private static final BlockPos[] EQ_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1), new BlockPos(5, 2, 1),
		new BlockPos(6, 2, 1), new BlockPos(7, 2, 1), new BlockPos(8, 2, 1), new BlockPos(9, 2, 1),
		new BlockPos(10, 2, 1), new BlockPos(11, 2, 1),
	};

	/**
	 * MOD-314 anti-wash: once two stores have levelled out, the cascade stops — and stops COSTING.
	 *
	 * <p>The first version of this test could not fail, and it is worth saying why so it is not written
	 * that way again. It put the two boxes one cable apart and started them at exactly equal charge.
	 * Both choices disarmed it: {@code cableLoss} is {@code floor(gross × (1 − (1 − rate)^distance))},
	 * which is identically 0 for a 12 EU packet over one copper block, so the conservation assertion was
	 * arithmetically incapable of detecting a ping-pong; and at exactly equal fill the cascade never opens
	 * in the first place, so nothing was exercised at all.
	 *
	 * <p>This version fixes both. Ten cables make the per-delivery loss non-zero (≈2 EU per 12 EU packet
	 * on copper), so any energy going round in circles is permanently destroyed and visible as a shrinking
	 * total. And the pair starts with a real gap, so the cascade genuinely runs, converges, and only then
	 * is asked to be quiet: the total is sampled after settling and again much later, and the two must be
	 * identical. A limit cycle would show up as a total that keeps sliding.
	 */
	public static void cascadeStopsAtEquilibrium(GameTestHelper helper) {
		for (BlockPos c : EQ_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(EQ_DONOR, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(EQ_RECEIVER, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (!(be(helper, EQ_DONOR) instanceof BatteryBoxBlockEntity donor)
				|| !(be(helper, EQ_RECEIVER) instanceof BatteryBoxBlockEntity receiver)) {
			helper.fail("cascade pair was not placed — outside the structure?");
			return;
		}
		long half = Config.batteryBoxBuffer / 2;
		donor.getEnergyStorage().amount = half + 3_000L;
		receiver.getEnergyStorage().amount = half;
		long receiverStart = receiver.getEnergyStorage().getAmount();

		// Phase 1 — let it converge.
		driveEqualisingLine(helper, 400);
		long settledTotal = equalisingTotal(helper);
		long donorMid = be(helper, EQ_DONOR) instanceof BatteryBoxBlockEntity d ? d.getEnergyStorage().getAmount() : -1;
		long receiverMid = be(helper, EQ_RECEIVER) instanceof BatteryBoxBlockEntity r
				? r.getEnergyStorage().getAmount() : -1;

		// Guard against a vacuous pass: if nothing ever moved, the quiet phase below proves nothing.
		if (receiverMid <= receiverStart) {
			helper.fail("the cascade never ran — receiver still at " + receiverMid
					+ " EU, so the settling assertions below would be vacuous");
			return;
		}
		if (donorMid < receiverMid) {
			helper.fail("cascade overshot — donor " + donorMid + " ended below receiver " + receiverMid);
			return;
		}

		// Phase 2 — it must now be quiet. Not "almost level": costing nothing.
		driveEqualisingLine(helper, 200);
		long laterTotal = equalisingTotal(helper);
		if (laterTotal != settledTotal) {
			helper.fail("a settled cascade pair kept moving energy: total " + settledTotal + " -> " + laterTotal
					+ " EU over 200 idle ticks. Every lap pays MOD-021 cable loss, so a drifting total means the"
					+ " pair is circulating and will bleed the bank dry.");
		}
		helper.succeed();
	}

	private static void driveEqualisingLine(GameTestHelper helper, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, EQ_DONOR));
			for (BlockPos c : EQ_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, EQ_RECEIVER));
		}
	}

	/** Every EU the equalising layout holds: both boxes plus every cable segment between them. */
	private static long equalisingTotal(GameTestHelper helper) {
		long total = 0;
		if (be(helper, EQ_DONOR) instanceof BatteryBoxBlockEntity d) {
			total += d.getEnergyStorage().getAmount();
		}
		if (be(helper, EQ_RECEIVER) instanceof BatteryBoxBlockEntity r) {
			total += r.getEnergyStorage().getAmount();
		}
		for (BlockPos c : EQ_CABLES) {
			if (be(helper, c) instanceof CableBlockEntity cb) {
				total += cb.getEnergyStorage().getAmount();
			}
		}
		return total;
	}

	// ── MOD-314 B1: a box wired on BOTH faces (bus running through it) must still receive ───────────

	private static final BlockPos MIDBUS_RING_BOX = new BlockPos(2, 56, 2);
	private static final BlockPos MIDBUS_DONOR = new BlockPos(2, 56, 4);
	private static final BlockPos[] MIDBUS_CABLES = {
		new BlockPos(1, 56, 2), new BlockPos(1, 56, 3), new BlockPos(2, 56, 3),
		new BlockPos(3, 56, 3), new BlockPos(3, 56, 2),
	};

	/**
	 * MOD-314 regression: an EMPTY Battery Box whose IN and OUT faces both touch the same network — the
	 * ordinary "run the bus through the box" wiring — must still be charged by the cascade.
	 *
	 * <p>This is the case the first cut of the fix silently missed, and it is worth stating why, because
	 * the mistake is easy to repeat. A node lands in the network's storage-source list purely by face
	 * role: {@code supportsExtraction()} asks the FACE whether it may emit and never looks at the buffer.
	 * So a box with a cable on its OUT face is a "source" even at 0 EU. Keying the self-serve guard on
	 * that list therefore dropped every mid-bus box out of the consumer pass, and it could never fill —
	 * exactly the bug this task was filed for, preserved for the more common wiring while the end-of-line
	 * case looked fixed. The guard now excludes only nodes that actually received a cascade allowance.
	 */
	public static void cascadeChargesMidBusBatteryBox(GameTestHelper helper) {
		for (BlockPos c : MIDBUS_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// Receiver: FACING=WEST puts IN on (1,56,2) and OUT on (3,56,2) — both ring cables, so the box is
		// dual-role on ONE network, which is what makes it a "source" despite being empty.
		helper.setBlock(MIDBUS_RING_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		// Donor: FACING=SOUTH puts its OUT face on the north side, touching ring cable (2,56,3); its IN
		// face points at air, so it is a pure source and cannot itself receive.
		helper.setBlock(MIDBUS_DONOR, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));

		// A block placed outside the structure bounds is dropped SILENTLY, which would leave this test
		// green and meaningless. Prove both boxes exist before drawing any conclusion from the result.
		if (!(be(helper, MIDBUS_DONOR) instanceof BatteryBoxBlockEntity donor)) {
			helper.fail("donor BatteryBox was not placed at " + MIDBUS_DONOR + " — outside the structure?");
			return;
		}
		if (!(be(helper, MIDBUS_RING_BOX) instanceof BatteryBoxBlockEntity receiver)) {
			helper.fail("mid-bus BatteryBox was not placed at " + MIDBUS_RING_BOX);
			return;
		}
		donor.getEnergyStorage().amount = Config.batteryBoxBuffer;
		receiver.getEnergyStorage().amount = 0L;

		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, MIDBUS_DONOR));
			tick(helper, be(helper, MIDBUS_RING_BOX));
			for (BlockPos c : MIDBUS_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
		}

		long received = be(helper, MIDBUS_RING_BOX) instanceof BatteryBoxBlockEntity r
				? r.getEnergyStorage().getAmount() : -1;
		if (received <= 0L) {
			helper.fail("a Battery Box with the bus running through it was never charged: " + received
					+ " EU (MOD-314 B1: face role, not stored charge, decides membership of the storage-source"
					+ " list — an empty mid-bus box must not be treated as a donor and dropped from the sinks)");
			return;
		}
		helper.succeed();
	}

	/** Total EU held by the cascade pair and the cable between them. */
	private static long storedTotal(GameTestHelper helper) {
		long total = 0;
		if (be(helper, WASH_SRC) instanceof BatteryBoxBlockEntity s) {
			total += s.getEnergyStorage().getAmount();
		}
		if (be(helper, WASH_DST) instanceof BatteryBoxBlockEntity d) {
			total += d.getEnergyStorage().getAmount();
		}
		if (be(helper, WASH_CABLE) instanceof dev.alaindustrial.block.entity.CableBlockEntity c) {
			total += c.getEnergyStorage().getAmount();
		}
		return total;
	}

	private static final BlockPos STO_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] STO_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
	};
	private static final BlockPos STO_BOX = new BlockPos(5, 2, 1);

	// MOD-214: same line as above, plus ONE cable past the box — on its OUT face. That is what a player
	// builds when the bus continues past the battery box, or when two boxes sit in a row.
	private static final BlockPos BOTH_GEN = new BlockPos(1, 2, 3);
	private static final BlockPos[] BOTH_CABLES = {
		new BlockPos(2, 2, 3), new BlockPos(3, 2, 3), new BlockPos(4, 2, 3),
	};
	private static final BlockPos BOTH_BOX = new BlockPos(5, 2, 3);
	private static final BlockPos BOTH_TAIL_CABLE = new BlockPos(6, 2, 3);

	/**
	 * Layout cover — a BatteryBox whose OUT face also touches a cable still charges from its IN face
	 * (a bus that runs past the box, or two boxes in a row). Not the MOD-214 guard: this layout was
	 * already correct before that fix — see {@link #storageChargesPastIdleProducer}.
	 *
	 */
	public static void storageChargesWithCabledOutputFace(GameTestHelper helper) {
		helper.setBlock(BOTH_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : BOTH_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = WEST → IN faces the cable line at x=4, OUT faces the tail cable at x=6.
		helper.setBlock(BOTH_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(BOTH_TAIL_CABLE, ModContent.COPPER_CABLE.get());
		if (be(helper, BOTH_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, BOTH_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, BOTH_GEN));
			for (BlockPos c : BOTH_CABLES) {
				tick(helper, be(helper, c));
			}
			tick(helper, be(helper, BOTH_TAIL_CABLE));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, BOTH_BOX));
		}
		long boxEnergy = be(helper, BOTH_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long inputCable = be(helper, BOTH_CABLES[2]) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		if (boxEnergy <= 0) {
			helper.fail("BatteryBox with a cabled OUT face never charged: " + boxEnergy
					+ " EU — its own producer role poisoned the distance of the cable on its INPUT face,"
					+ " so the fill front could never reach it (input cable held " + inputCable + " EU)");
		}
		helper.succeed();
	}

	/**
	 * MOD-070 storage-through-line: a BatteryBox charged over a multi-cable line pulls its EU THROUGH the
	 * wires — the intermediate cable holds real EU while the box fills (direct regression for the in-game
	 * bug where source→cable→BatteryBox left the cable empty).
	 * Mirrors: NetworkGameTest.tcCable001Nrg07_storageChargesThroughLine
	 */
	public static void storageChargesThroughLine(GameTestHelper helper) {
		helper.setBlock(STO_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : STO_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(STO_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, STO_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, STO_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, STO_GEN));
			for (BlockPos c : STO_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, STO_BOX));
		}
		long boxEnergy = be(helper, STO_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long midCable = be(helper, STO_CABLES[1]) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		if (boxEnergy <= 0) {
			helper.fail("BatteryBox received no EU over the cable line: " + boxEnergy);
		}
		if (midCable <= 0) {
			helper.fail("intermediate cable held no EU while charging a BatteryBox — storage charge bypassed "
					+ "the wire instead of flowing through it (the in-game bug)");
		}
		helper.succeed();
	}

	private static final BlockPos IDLE_LIVE_GEN = new BlockPos(1, 2, 5);
	private static final BlockPos[] IDLE_CABLES = {
		new BlockPos(2, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5),
		new BlockPos(5, 2, 5), new BlockPos(6, 2, 5),
	};
	private static final BlockPos IDLE_DEAD_GEN = new BlockPos(5, 2, 4);
	private static final BlockPos IDLE_BOX = new BlockPos(7, 2, 5);

	/**
	 * MOD-214 — an idle producer standing along the bus must not stop energy reaching what is past it.
	 *
	 * <p>Reported in game on 0.1.46: a row of solar panels feeding a Battery Box charged nothing, the
	 * cables by the working panels read full and the ones by the box read 0. The line held moonlit panels,
	 * which produce nothing in daylight. A producer endpoint is collected by face-role capability, not by
	 * output, so those panels still seeded the distance field at 1 all along their stretch; two adjacent
	 * cables at equal distance cannot exchange under the strictly-upstream rule, so the fill front died at
	 * that seam and everything past it starved forever. The player confirmed it from the other side:
	 * removing the moonlit panel made the same build charge immediately.
	 *
	 * <p>Modelled here with an unfuelled generator — same shape, no dependence on world time or sky.
	 * Regression guard: before the fix the box ends at 0 EU and every cable past the idle generator is
	 * empty while the ones next to the live generator sit at their full 12.
	 */
	public static void storageChargesPastIdleProducer(GameTestHelper helper) {
		helper.setBlock(IDLE_LIVE_GEN, ModContent.GENERATOR.get());
		if (be(helper, IDLE_LIVE_GEN) instanceof GeneratorBlockEntity g) {
			g.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		for (BlockPos c : IDLE_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(IDLE_DEAD_GEN, ModContent.GENERATOR.get()); // no fuel: a producer that never supplies
		helper.setBlock(IDLE_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, IDLE_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 150; i++) {
			tick(helper, be(helper, IDLE_LIVE_GEN));
			tick(helper, be(helper, IDLE_DEAD_GEN));
			for (BlockPos c : IDLE_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, IDLE_BOX));
		}
		long box = be(helper, IDLE_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (box <= 0) {
			helper.fail("BatteryBox past an idle producer never charged: " + box
					+ " EU — the idle producer seeded the distance field and froze the fill front");
		}
		long lastCable = be(helper, IDLE_CABLES[4]) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		if (lastCable <= 0) {
			helper.fail("the cable past the idle producer held no EU (" + lastCable
					+ ") — energy reached the box without flowing through the line");
		}
		helper.succeed();
	}

	// MOD-252 (D2): a base running off stored energy — charged box, a generator with no fuel standing on
	// the bus, a machine at the far end. Own cell block (y=48) because the NeoForge lane shares one
	// ServerLevel across scenarios.
	private static final BlockPos BASE_BOX = new BlockPos(1, 48, 1);
	private static final BlockPos[] BASE_CABLES = {
		new BlockPos(2, 48, 1), new BlockPos(3, 48, 1), new BlockPos(4, 48, 1),
		new BlockPos(5, 48, 1), new BlockPos(6, 48, 1),
	};
	private static final BlockPos BASE_DEAD_GEN = new BlockPos(4, 48, 2);
	private static final BlockPos BASE_MAC = new BlockPos(7, 48, 1);

	/**
	 * MOD-252 (D2) — the hole MOD-214 left open: a network with NO live generator.
	 *
	 * <p>MOD-214 stopped idle sources from seeding the flow field, but its live-supply set was only ever
	 * filled from the generator branch — a storage source never entered it. So on a base running off a
	 * charged Battery Box the set was permanently empty, the fallback fired every tick, and an unfuelled
	 * generator standing on the bus went straight back to seeding distance 1 and cutting the line in two.
	 * Reported in game as: box at 19968/20000, macerator at 0/800, and nothing moving.
	 *
	 * <p>The fix removes the whole question — the direction is seeded from what WANTS energy, so a source
	 * holding nothing simply has no say in it. Regression guard: before it, the macerator ends at 0 EU and
	 * every cable past the dead generator is empty.
	 */
	public static void mod252BaseWithoutLiveGeneratorFeedsMachine(GameTestHelper helper) {
		// FACING = WEST → IN face at (0,48,1) (air), OUT face on the bus at (2,48,1): a discharging base.
		helper.setBlock(BASE_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		for (BlockPos c : BASE_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// No fuel, no EU: a source that never supplies. FACING = SOUTH so its front (energy-inert,
		// R-NRG-03) points away from the bus and it really IS a producer endpoint on the cable it touches
		// — otherwise this scenario would not reproduce the seam it exists to guard against.
		helper.setBlock(BASE_DEAD_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		helper.setBlock(BASE_MAC, ModContent.MACERATOR.get());
		if (be(helper, BASE_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer;
		}
		if (be(helper, BASE_DEAD_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = 0L;
		}
		if (be(helper, BASE_MAC) instanceof dev.alaindustrial.block.entity.MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 0L;
			mac.setItem(dev.alaindustrial.block.entity.MaceratorBlockEntity.INPUT_SLOT,
					new ItemStack(Items.RAW_IRON, 8));
		}
		for (int i = 0; i < 150; i++) {
			tick(helper, be(helper, BASE_BOX));
			tick(helper, be(helper, BASE_DEAD_GEN));
			for (BlockPos c : BASE_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, BASE_MAC));
		}
		long lastCable = be(helper, BASE_CABLES[4]) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		long macEnergy = be(helper, BASE_MAC) instanceof dev.alaindustrial.block.entity.MaceratorBlockEntity m
				? m.getEnergyStorage().getAmount() : -1;
		if (lastCable <= 0) {
			helper.fail("no EU reached the cable past the unfuelled generator (" + lastCable
					+ ") — with no live generator anywhere, the idle one is seeding the flow field again"
					+ " (MOD-214 regression through the storage-only hole)");
			return;
		}
		if (macEnergy <= 0) {
			helper.fail("the macerator got no EU (" + macEnergy + ") from a full Battery Box on the same bus");
		}
		helper.succeed();
	}

	// MOD-255: the layout the player actually built — a Battery Box wired on BOTH its faces, with the two
	// sides joined into ONE network by a loop of cable around the box. That is what makes the box a source
	// and a sink of the same line; a straight bus past the box leaves the two faces in separate networks and
	// cannot reproduce the churn. Own cell block (y=52) because the NeoForge lane shares one ServerLevel.
	private static final BlockPos CHURN_BOX = new BlockPos(2, 52, 2);
	private static final BlockPos[] CHURN_CABLES = {
		new BlockPos(1, 52, 2), // the box's IN face
		new BlockPos(1, 52, 3), new BlockPos(2, 52, 3), new BlockPos(3, 52, 3), // the loop around it
		new BlockPos(3, 52, 2), // the box's OUT face
		new BlockPos(4, 52, 2), new BlockPos(5, 52, 2), // the bus onward
	};
	private static final BlockPos CHURN_MAC = new BlockPos(6, 52, 2);

	/**
	 * MOD-255 — a Battery Box must not drink back the EU it just pushed into the wire.
	 *
	 * <p>Reported in game on a base with no working generator: box at 19968/20000, macerator and electric
	 * furnace both at 0 EU, nothing moving. The box is a dual-role endpoint — a source through its OUT face
	 * and a sink through its IN face — and the line kernel's no-self-churn rule compared POSITIONS. On the
	 * line path the supply pool is the touched cable buffers, and a cable is never at the box's position, so
	 * that rule never fired once. The tick order closed the loop: sinks are served from the line before the
	 * line is recharged, so every tick the box took back exactly what it had discharged a tick earlier and
	 * the charge oscillated between the box and its own neighbouring cables forever.
	 *
	 * <p>Regression guard: before the fix the macerator ends at 0 EU and the far end of the bus is empty
	 * while the box sits nearly full.
	 */
	public static void mod255DualRoleBatteryFeedsMachineThroughLine(GameTestHelper helper) {
		// FACING = WEST → IN face on the cable at (1,52,2), OUT face on the cable at (3,52,2). The cable at
		// (2,52,3) touches the box's south face, which is role NONE — it must never carry EU into or out of
		// the box, only around it.
		helper.setBlock(CHURN_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		for (BlockPos c : CHURN_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = EAST so the macerator's inert front points away from the bus and its west face is a real
		// input (R-NRG-03) — otherwise it would not be a consumer endpoint at all and the test would pass
		// vacuously.
		helper.setBlock(CHURN_MAC, ModContent.MACERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		long boxStart = Config.batteryBoxBuffer / 100L * 99L; // the reported 99 %
		if (be(helper, CHURN_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = boxStart;
		}
		if (be(helper, CHURN_MAC) instanceof dev.alaindustrial.block.entity.MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 0L; // no input item: the buffer only fills, never burns down
		}
		for (int i = 0; i < 150; i++) {
			tick(helper, be(helper, CHURN_BOX));
			for (BlockPos c : CHURN_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, CHURN_MAC));
		}
		long macEnergy = be(helper, CHURN_MAC) instanceof dev.alaindustrial.block.entity.MaceratorBlockEntity m
				? m.getEnergyStorage().getAmount() : -1;
		long farCable = be(helper, CHURN_CABLES[6]) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		long boxEnd = be(helper, CHURN_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (macEnergy < Config.maceratorBuffer / 2L) {
			helper.fail("the macerator got " + macEnergy + "/" + Config.maceratorBuffer
					+ " EU over 150 ticks from a Battery Box at " + boxEnd + "/" + Config.batteryBoxBuffer
					+ " on the same line (far cable held " + farCable + ") — the box is drinking back its own"
					+ " discharge instead of letting it travel down the wire");
			return;
		}
		if (boxEnd >= boxStart) {
			helper.fail("the macerator charged but the Battery Box did not pay for it: " + boxEnd + " vs "
					+ boxStart + " EU — the EU came from somewhere other than the box");
			return;
		}
		helper.succeed();
	}

	// MOD-255 acceptance criterion 2: the same both-faces-wired box, but with nothing to feed. Same shape,
	// own cell block (y=56).
	private static final BlockPos IDLE_CHURN_BOX = new BlockPos(2, 56, 2);
	private static final BlockPos[] IDLE_CHURN_CABLES = {
		new BlockPos(1, 56, 2), new BlockPos(1, 56, 3), new BlockPos(2, 56, 3),
		new BlockPos(3, 56, 3), new BlockPos(3, 56, 2),
	};

	/**
	 * MOD-255 — a Battery Box wired on both faces with nothing to power must not circulate its own charge.
	 * The player's words: it must not chase its EU around the loop and burn it on cable loss. With no
	 * machine there is no deficit to back up, so the storage budget is zero and the box discharges nothing;
	 * the ring must stay empty and the charge must be exactly what it started at.
	 */
	public static void mod255DualRoleBatteryHoldsChargeWithoutConsumers(GameTestHelper helper) {
		helper.setBlock(IDLE_CHURN_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		for (BlockPos c : IDLE_CHURN_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		long boxStart = Config.batteryBoxBuffer / 100L * 99L;
		if (be(helper, IDLE_CHURN_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = boxStart;
		}
		for (int i = 0; i < 60; i++) {
			tick(helper, be(helper, IDLE_CHURN_BOX));
			for (BlockPos c : IDLE_CHURN_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
		}
		long boxEnd = be(helper, IDLE_CHURN_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (boxEnd != boxStart) {
			helper.fail("a Battery Box wired on both faces with no consumer changed charge: " + boxEnd
					+ " vs " + boxStart + " EU — it is circulating its own energy through the ring");
			return;
		}
		for (BlockPos c : IDLE_CHURN_CABLES) {
			long amount = be(helper, c) instanceof CableBlockEntity cb ? cb.getEnergyStorage().getAmount() : -1;
			if (amount != 0L) {
				helper.fail("cable " + c + " holds " + amount + " EU — a storage source with no machine to back"
						+ " up must not charge the wire (MOD-070)");
				return;
			}
		}
		helper.succeed();
	}

	private static final BlockPos LONE_BOX = new BlockPos(1, 2, 1);
	private static final BlockPos LONE_CABLE = new BlockPos(2, 2, 1);

	/**
	 * MOD-070 audit: a lone storage source (charged BatteryBox with a cabled OUT face) with no consumer and
	 * no generator must SLEEP, not spin a no-op tick forever, and must not charge the wire.
	 * Mirrors: NetworkGameTest.rNrg09c_loneStorageSourceSleeps
	 */
	public static void loneStorageSourceSleeps(GameTestHelper helper) {
		helper.setBlock(LONE_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(LONE_CABLE, ModContent.COPPER_CABLE.get());
		if (be(helper, LONE_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer;
		}
		for (int i = 0; i < 10; i++) {
			tick(helper, be(helper, LONE_BOX));
			tick(helper, be(helper, LONE_CABLE));
			NetworkManager.tickAll(helper.getLevel());
		}
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(LONE_CABLE));
		if (net == null) {
			helper.fail("no network formed on the lone storage-source cable");
		}
		if (net.isAwake()) {
			helper.fail("lone storage-source network is awake — no consumer and no generator, must sleep");
		}
		long cable = be(helper, LONE_CABLE) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		if (cable != 0L) {
			helper.fail("a lone storage source charged the wire: " + cable + " (must fill only for a machine deficit)");
		}
		helper.succeed();
	}

	// ── scenario 3: MOD-009 battery box charges to 100% over a multi-cable network ────────────────

	private static final BlockPos BB_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] BB_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1),
	};
	private static final BlockPos BB_BOX = new BlockPos(7, 2, 1);

	/**
	 * MOD-009: a BatteryBox pre-charged 10 EU short of full charges all the way to exact capacity over a
	 * 5-cable network (no residual cable-loss term stranding the last packet).
	 * Mirrors: NetworkGameTest.mod009_batteryBoxChargesToFull
	 */
	public static void mod009BatteryBoxChargesToFull(GameTestHelper helper) {
		helper.setBlock(BB_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : BB_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(BB_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, BB_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		if (be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer - 10L;
		}
		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, BB_GEN));
			for (BlockPos c : BB_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, BB_BOX));
		}
		long got = be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (got != Config.batteryBoxBuffer) {
			helper.fail("BatteryBox did not reach 100%: " + got + "/" + Config.batteryBoxBuffer
					+ " (stuck short = MOD-009 cable-loss regression)");
		}
		helper.succeed();
	}

	// ── scenario 8: battery box rate BVA — buffer publishes EXACTLY LV.maxVoltage() ───────────────

	/**
	 * The battery box's energy buffer publishes per-tick rate caps of EXACTLY
	 * {@code EnergyTier.LV.maxVoltage()} for both insert and extract. Asserting the canonical
	 * {@link dev.alaindustrial.core.energy.EnergyBuffer#maxInsert}/{@code maxExtract} fields directly (loader-neutral —
	 * the buffer is shared common code; each loader only wraps it) catches both a dead port
	 * ({@code maxInsert == 0}) and a missing cap (unlimited insert) — either regression would pass a
	 * weaker "{@code <= lvCap}" upper-bound check. Complements the Fabric lane's
	 * {@code BatteryBoxGameTest.tcBatteryBox001Prf03/Prf04} which exercise the same invariant via the
	 * loader-specific capability view.
	 * Mirrors: BatteryBoxGameTest.tcBatteryBox001Prf03_inputRateCappedAtLv
	 */
	public static void batteryBoxRateExactLv(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long lvCap = EnergyTier.LV.maxVoltage();
			dev.alaindustrial.core.energy.EnergyBuffer buf = bb.getEnergyStorage();
			if (buf.maxInsert != lvCap) {
				helper.fail("battery box maxInsert=" + buf.maxInsert + " EU, expected exactly LV.maxVoltage()="
						+ lvCap + " (maxInsert==0 = dead port; > lvCap = uncapped — both are bugs)");
				return;
			}
			if (buf.maxExtract != lvCap) {
				helper.fail("battery box maxExtract=" + buf.maxExtract + " EU, expected exactly LV.maxVoltage()="
						+ lvCap + " (maxExtract==0 = dead port; > lvCap = uncapped — both are bugs)");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}

	// ── scenario 16: battery box conservation — genDrain == boxGain (no leak) ──────────────────────

	/**
	 * A cabled generator whose only consumer is a battery box with partial room destroys no EU into the
	 * void. With the MOD-070 line buffers the box charges THROUGH the wire, so the generator also fills
	 * the cable buffer: conservation is system-wide, genDrain == boxGain + cableBuffered + loss (loss
	 * floors to 0 on a 1-cable line). Pre-charge the generator (no fuel: buffer only goes down), leave the
	 * box 5 EU of room, drive, assert conservation.
	 * Mirrors: NetworkGameTest.tcCable001Nrg03_generatorNotDrainedByPartialConsumer
	 */
	public static void batteryBoxConservationPartialConsumer(GameTestHelper helper) {
		helper.setBlock(LINE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(LINE_MAC, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long genStart = Config.generatorBuffer;
		if (be(helper, LINE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = genStart; // no fuel: buffer only goes down
			gen.setChanged();
		}
		final long room = 5;
		long boxStart = Config.batteryBoxBuffer - room;
		if (be(helper, LINE_MAC) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = boxStart; // leave exactly `room` EU
			bb.setChanged();
		}
		driveLine(helper, 10);
		long genEnd = be(helper, LINE_GEN) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long boxEnd = be(helper, LINE_MAC) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long genDrain = genStart - genEnd;
		long boxGain = boxEnd - boxStart;
		if (boxEnd != Config.batteryBoxBuffer) {
			helper.fail("battery box did not top off its 5 EU of room: " + boxEnd + "/" + Config.batteryBoxBuffer);
			return;
		}
		// MOD-070: the box charges through the line, so the generator also fills the cable buffer.
		long cableBuffered = be(helper, LINE_CABLE) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : 0;
		if (genDrain != boxGain + cableBuffered) {
			helper.fail("generator lost " + genDrain + " EU but box gained " + boxGain
					+ " and cables buffered " + cableBuffered + " — surplus destroyed (cable-path leak)");
			return;
		}
		helper.succeed();
	}

	// ── scenario 30: battery box drop carries EU (R-BRK-07) ────────────────────────────────────────

	/**
	 * A charged battery box emits its buffered EU as the {@code STORED_ENERGY} data component on
	 * {@code collectComponents()} — what the loot table's {@code copy_components} reads onto the drop.
	 * This is the NeoForge lane's second check of the data-component seam (the first is
	 * {@link #batteryBoxDropCarriesEnergy}); here a different charge value exercises the same path.
	 * Mirrors: BatteryBoxGameTest.tcBatteryBox001Brk07_energyCarriedByComponent
	 */
	public static void batteryBoxDropCarriesEnergyHalfCharge(GameTestHelper helper) {
		helper.setBlock(DROP, ModContent.BATTERY_BOX.get());
		if (be(helper, DROP) instanceof BatteryBoxBlockEntity bb) {
			long charge = bb.getEnergyStorage().getCapacity() / 2; // half charge (≠ the 12345 in the other case)
			bb.getEnergyStorage().amount = charge;
			DataComponentMap map = bb.collectComponents();
			Long carried = map.get(ModDataComponents.STORED_ENERGY.get());
			if (carried == null || carried.longValue() != charge) {
				helper.fail("battery box did not carry half-charge STORED_ENERGY: " + carried + "/" + charge);
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("battery box block entity missing");
	}
}
