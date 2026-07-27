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

	// ── MOD-070: a storage source never charges another storage sink (no battery↔battery wash) ─────

	private static final BlockPos WASH_SRC = new BlockPos(1, 2, 1);
	private static final BlockPos WASH_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos WASH_DST = new BlockPos(3, 2, 1);

	/**
	 * MOD-070 storage priority: a charged BatteryBox cabled to an empty one (no generator, no machine)
	 * must NOT charge it — storage never sources for another storage sink (battery↔battery wash).
	 * Mirrors: NetworkGameTest.tcCable001Nrg06_storageDoesNotChargeStorage
	 */
	public static void storageDoesNotChargeStorage(GameTestHelper helper) {
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
			helper.fail("no energy network formed between the two BatteryBoxes — test cannot verify anti-wash");
		}
		long dstEnd = be(helper, WASH_DST) instanceof BatteryBoxBlockEntity d ? d.getEnergyStorage().getAmount() : -1;
		if (dstEnd != 0L) {
			helper.fail("empty BatteryBox was charged from another BatteryBox: " + dstEnd
					+ " (storage must never source for another storage sink — battery↔battery wash)");
		}
		helper.succeed();
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
