package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.registry.ModBlocks;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 integration suite for the energy network: generator → copper cable → macerator. Covers
 * delivery, split on cable removal, and rejoin on replacement. Compact 3-wide layout to stay inside
 * the default test region. Migrated from legacy {@code NETWORK_DELIVERY/SPLIT/REJOIN}.
 *
 * <p>Drives the line block entities + the per-level {@link NetworkManager} directly (deterministic).
 */
public class NetworkGameTest {

	private static final BlockPos GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos MAC = new BlockPos(3, 2, 1);

	/** Null-safe world lookup (helper.getBlockEntity asserts presence and throws when a block is removed). */
	private net.minecraft.world.level.block.entity.BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private void tick(GameTestHelper helper, net.minecraft.world.level.block.entity.BlockEntity be) {
		BlockPos p = be.getBlockPos();
		if (be instanceof GeneratorBlockEntity gen) {
			gen.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else if (be instanceof CableBlockEntity c) {
			c.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else if (be instanceof MaceratorBlockEntity mac) {
			mac.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		} else if (be instanceof BatteryBoxBlockEntity bb) {
			bb.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		}
	}

	private void build(GameTestHelper helper) {
		helper.setBlock(GEN, ModBlocks.GENERATOR);
		helper.setBlock(CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(MAC, ModBlocks.MACERATOR);
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	/** Tick generator + cable + NetworkManager + macerator for {@code n} synthetic ticks (null-safe). */
	private void drive(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			var gen = be(helper, GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			var cable = be(helper, CABLE);
			if (cable != null) {
				tick(helper, cable);
			}
			NetworkManager.tickAll(helper.getLevel());
			var mac = be(helper, MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
	}

	private long macEnergy(GameTestHelper helper) {
		return be(helper, MAC) instanceof MaceratorBlockEntity mac ? mac.getEnergyStorage().getAmount() : -1;
	}

	/** @implements IT-001 — generator delivers EU down a cable to the macerator. @covers R-CON-01,R-CON-05 */
	@GameTest
	public void it001_delivery(GameTestHelper helper) {
		build(helper);
		drive(helper, 120);
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE));
		if (net == null) {
			helper.fail("no energy network formed on the cable");
		}
		if (macEnergy(helper) <= 0) {
			helper.fail("macerator received no EU over the cable");
		}
		helper.succeed();
	}

	/**
	 * @implements IT-001-NEG01 — removing the cable splits the net; downstream machine stops.
	 * @covers R-CON-04, R-CON-07
	 */
	@GameTest
	public void it001Neg01_breakStopsDelivery(GameTestHelper helper) {
		build(helper);
		drive(helper, 120);
		// Break the only cable: the network must vanish and the macerator must stop receiving.
		helper.setBlock(CABLE, Blocks.AIR);
		if (helper.getBlockEntity(MAC, MaceratorBlockEntity.class) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 0;
		}
		drive(helper, 60);
		if (NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE)) != null) {
			helper.fail("network still present after cable removed");
		}
		if (macEnergy(helper) != 0) {
			helper.fail("macerator kept receiving EU through an air gap: " + macEnergy(helper));
		}
		helper.succeed();
	}

	/** @implements IT-001-FUN02 — replacing the cable rejoins the net and flow resumes. @covers R-CON-09 */
	@GameTest
	public void it001Fun02_rejoinResumesFlow(GameTestHelper helper) {
		build(helper);
		drive(helper, 60);
		helper.setBlock(CABLE, Blocks.AIR);
		if (helper.getBlockEntity(MAC, MaceratorBlockEntity.class) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 0;
		}
		drive(helper, 40);
		helper.setBlock(CABLE, ModBlocks.COPPER_CABLE); // rejoin
		drive(helper, 80);
		if (macEnergy(helper) <= 0) {
			helper.fail("flow did not resume after the cable was replaced");
		}
		helper.succeed();
	}

	// --- R-NRG-08 split rig: one generator -> one cable -> TWO macerators on two cable faces ---
	private static final BlockPos SPLIT_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos SPLIT_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos SPLIT_MAC_A = new BlockPos(3, 2, 1); // +x face of the cable
	private static final BlockPos SPLIT_MAC_B = new BlockPos(2, 2, 2); // +z face of the cable

	/** Energy currently buffered in a macerator at {@code rel}, or -1 if it's not there (null-safe). */
	private long splitMacEnergy(GameTestHelper helper, BlockPos rel) {
		return be(helper, rel) instanceof MaceratorBlockEntity mac ? mac.getEnergyStorage().getAmount() : -1;
	}

	/** Tick generator + cable + NetworkManager + BOTH macerators for {@code n} ticks (null-safe). */
	private void driveSplit(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			var gen = be(helper, SPLIT_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			var cable = be(helper, SPLIT_CABLE);
			if (cable != null) {
				tick(helper, cable);
			}
			NetworkManager.tickAll(helper.getLevel());
			var a = be(helper, SPLIT_MAC_A);
			if (a != null) {
				tick(helper, a);
			}
			var b = be(helper, SPLIT_MAC_B);
			if (b != null) {
				tick(helper, b);
			}
		}
	}

	/**
	 * @implements R-NRG-08 — a single network splits its deliverable energy ~proportionally between
	 * two equal consumers. Two macerators with empty input slots (so they never drain their buffers)
	 * share one cable's worth of generator output; with equal capacity and equal room their buffers
	 * must fill to roughly equal amounts.
	 * @covers R-NRG-08
	 */
	@GameTest
	public void rNrg08_splitsBetweenEqualConsumers(GameTestHelper helper) {
		// Build: generator -> one cable -> two macerators on two different cable faces.
		helper.setBlock(SPLIT_GEN, ModBlocks.GENERATOR);
		helper.setBlock(SPLIT_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(SPLIT_MAC_A, ModBlocks.MACERATOR);
		// SPLIT_MAC_B touches the cable on its own NORTH face, which is the default FACING and therefore
		// energy-inert (D-FACING); give it a non-default FACING so that face stays a working IN face.
		helper.setBlock(SPLIT_MAC_B, ModBlocks.MACERATOR.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		if (be(helper, SPLIT_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		// Leave both macerators' input slots EMPTY: with no recipe they cannot work, so their energy
		// buffers only accumulate and the split is read cleanly (no consumption masking the share).

		// One network must own the cable + both consumer faces.
		driveSplit(helper, 120);
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(SPLIT_CABLE));
		if (net == null) {
			helper.fail("no energy network formed on the cable");
		}

		long a = splitMacEnergy(helper, SPLIT_MAC_A);
		long b = splitMacEnergy(helper, SPLIT_MAC_B);
		if (a <= 0 || b <= 0) {
			helper.fail("a consumer received no EU: a=" + a + " b=" + b);
		}
		// Roughly equal split: generous tolerance (the room-proportional split can hand the rounding
		// remainder to one side each tick, and packetCap clamps per consumer, so allow up to half).
		if (Math.abs(a - b) > Math.max(a, b) / 2) {
			helper.fail("split not roughly equal: a=" + a + " b=" + b);
		}
		helper.succeed();
	}


	// --- R-NRG-09 sleep/wake rig: own positions so the producer-only network has no consumer. ---
	private static final BlockPos SLEEP_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos SLEEP_CABLE = new BlockPos(2, 2, 1);
	/** Consumer placed ABOVE the cable on wake, so no cable block is touched/replaced. */
	private static final BlockPos WAKE_MAC = new BlockPos(2, 3, 1);

	/**
	 * Build a producer-only line (generator + one cable, fuelled, no consumer) and register + charge
	 * it by ticking the generator, the cable (lazy {@link NetworkManager} registration) and the
	 * manager directly. Mirrors the monolith {@code NETWORK_SLEEP/WAKE} setup.
	 */
	private void buildProducerOnly(GameTestHelper helper) {
		helper.setBlock(SLEEP_GEN, ModBlocks.GENERATOR);
		helper.setBlock(SLEEP_CABLE, ModBlocks.COPPER_CABLE);
		if (be(helper, SLEEP_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		for (int i = 0; i < 10; i++) {
			var g = be(helper, SLEEP_GEN);
			if (g != null) {
				tick(helper, g);
			}
			var c = be(helper, SLEEP_CABLE);
			if (c != null) {
				tick(helper, c);
			}
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	/**
	 * @implements R-NRG-09 — a producer-only network (generator + one cable, no consumer) sleeps:
	 *     it has nothing to move, so {@link EnergyNetwork#isAwake()} must be false. Migrated from the
	 *     monolith {@code NETWORK_SLEEP} check.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09_idleNetworkSleeps(GameTestHelper helper) {
		buildProducerOnly(helper);
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(SLEEP_CABLE));
		if (net == null) {
			helper.fail("no energy network formed on the producer-only cable");
		}
		if (net.isAwake()) {
			helper.fail("producer-only network is awake; it has no consumer so it must sleep");
		}
		helper.succeed();
	}

	/**
	 * @implements R-NRG-09 (MOD-070) — a source wired to cables with NO consumer still fills the line to
	 *     its buffer capacity: {@code buildProducerOnly} runs a fuelled generator into one cable, and the
	 *     cable must hold exactly {@code cableBuffer} EU afterwards (0 under the old "producer-only sleeps
	 *     immediately, never charges" behaviour). This is why the network then sleeps — the line is full.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09b_sourceFillsCableWithoutConsumer(GameTestHelper helper) {
		buildProducerOnly(helper);
		long cable = cableAmount(helper, SLEEP_CABLE);
		if (cable != Config.cableBuffer) {
			helper.fail("producer-only cable did not fill to its buffer: " + cable + "/" + Config.cableBuffer
					+ " — a source must charge the wires even with no consumer");
		}
		helper.succeed();
	}

	/**
	 * @implements R-NRG-09 — an asleep producer-only network wakes when a consumer is placed adjacent
	 *     to the cable WITHOUT replacing a cable: the cable's neighbourChanged hook (fired by
	 *     setBlockAndUpdate) dirties the network, which re-discovers the consumer, becomes awake, and
	 *     delivers EU. Migrated from the monolith {@code NETWORK_WAKE} check.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void rNrg09_networkWakesOnConsumer(GameTestHelper helper) {
		buildProducerOnly(helper);
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(SLEEP_CABLE));
		if (net == null || net.isAwake()) {
			helper.fail("network should exist and be asleep before the consumer is placed");
		}

		// Place the consumer above the cable via setBlockAndUpdate so the cable's neighbourChanged hook
		// fires and dirties the network (gameplay: a machine placed next to an existing cable).
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(WAKE_MAC),
				ModBlocks.MACERATOR.defaultBlockState());
		long macBefore = 0;
		if (be(helper, WAKE_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
			macBefore = mac.getEnergyStorage().getAmount();
		}

		// Drive the producer-only line plus the new consumer.
		for (int i = 0; i < 60; i++) {
			var g = be(helper, SLEEP_GEN);
			if (g != null) {
				tick(helper, g);
			}
			var c = be(helper, SLEEP_CABLE);
			if (c != null) {
				tick(helper, c);
			}
			NetworkManager.tickAll(helper.getLevel());
			var m = be(helper, WAKE_MAC);
			if (m != null) {
				tick(helper, m);
			}
		}

		EnergyNetwork woke = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(SLEEP_CABLE));
		if (woke == null || !woke.isAwake()) {
			helper.fail("network did not wake after a consumer was placed adjacent to the cable");
		}
		long macAfter = 0;
		ItemStack out = ItemStack.EMPTY;
		if (be(helper, WAKE_MAC) instanceof MaceratorBlockEntity mac) {
			macAfter = mac.getEnergyStorage().getAmount();
			out = mac.getItem(MaceratorBlockEntity.OUTPUT_SLOT);
		}
		if (macAfter <= macBefore && out.isEmpty()) {
			helper.fail("woken network delivered no EU to the consumer: macBefore=" + macBefore
					+ " macAfter=" + macAfter);
		}
		helper.succeed();
	}

	// ── MOD-009: BatteryBox charges to 100%; machines served before storage; no self-churn ──────────────

	// Charge rig: generator → 5 copper cables → BatteryBox, laid along +x inside the 8×8×8 region.
	private static final BlockPos BB_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] BB_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1),
	};
	private static final BlockPos BB_BOX = new BlockPos(7, 2, 1);

	/**
	 * @implements MOD-009 — a BatteryBox charges all the way to 100% over a multi-cable network. The old
	 *     flat cable-loss term ({@code floor(0.2 × cables)}) was subtracted from the deliverable total,
	 *     so once the BatteryBox's remaining room fell below that loss the last packet never moved and the
	 *     buffer stuck at {@code capacity − loss} (e.g. 19998/20000). With transport now a pure throughput
	 *     limit, the buffer tops off exactly. Pre-charged near full to assert the top-off directly.
	 * @covers R-NRG-01
	 */
	@GameTest(maxTicks = 60)
	public void mod009_batteryBoxChargesToFull(GameTestHelper helper) {
		helper.setBlock(BB_GEN, ModBlocks.GENERATOR);
		for (BlockPos c : BB_CABLES) {
			helper.setBlock(c, ModBlocks.COPPER_CABLE);
		}
		// FACING = WEST so the BatteryBox's input face (MOD-006: input on FACING) meets the cable on its −x side.
		helper.setBlock(BB_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, BB_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity(); // ample supply
		}
		if (be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer - 10L; // 10 EU short of full
		}
		// Drive gen + cables + network + battery_box until topped off (5 cables ⇒ old loss=1 ⇒ stuck at −1).
		for (int i = 0; i < 40; i++) {
			var g = be(helper, BB_GEN);
			if (g != null) {
				tick(helper, g);
			}
			for (BlockPos c : BB_CABLES) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, BB_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}
		long got = be(helper, BB_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (got != Config.batteryBoxBuffer) {
			helper.fail("BatteryBox did not reach 100%: " + got + "/" + Config.batteryBoxBuffer
					+ " (stuck short = MOD-009 cable-loss regression)");
		}
		helper.succeed();
	}

	// Priority rig: generator + cable + macerator (machine) + BatteryBox (sink), all on one cable.
	private static final BlockPos PRI_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos PRI_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos PRI_MAC = new BlockPos(2, 3, 1);   // above the cable
	private static final BlockPos PRI_BOX = new BlockPos(2, 2, 2);   // +z of the cable

	/**
	 * @implements MOD-009-PRI — with scarce supply, a working machine is served before a storage sink:
	 *     the macerator fills from the limited generator buffer while the BatteryBox gets only the remainder
	 *     (here 0). Guards the "machines before storage, no BatteryBox starvation" criterion.
	 * @covers R-NRG-08
	 */
	@GameTest(maxTicks = 60)
	public void mod009Pri_machineServedBeforeStorage(GameTestHelper helper) {
		helper.setBlock(PRI_GEN, ModBlocks.GENERATOR);
		helper.setBlock(PRI_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(PRI_MAC, ModBlocks.MACERATOR);
		helper.setBlock(PRI_BOX, ModBlocks.BATTERY_BOX);
		// Scarce, fixed supply: no fuel (no regeneration), generator buffer seeded with exactly 10 EU.
		if (be(helper, PRI_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = 10L;
		}
		// Macerator input empty → it only buffers EU (no consumption masking the share).
		// Register the network + run a single delivery pass; 10 EU < packetCap so it all fits one tick.
		for (int i = 0; i < 8; i++) {
			var g = be(helper, PRI_GEN);
			if (g != null) {
				tick(helper, g);
			}
			var c = be(helper, PRI_CABLE);
			if (c != null) {
				tick(helper, c);
			}
			NetworkManager.tickAll(helper.getLevel());
		}
		long mac = be(helper, PRI_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		long box = be(helper, PRI_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (mac != 10L) {
			helper.fail("machine should be served first: macerator=" + mac + " (expected 10), battery_box=" + box);
		}
		if (box != 0L) {
			helper.fail("storage sink took EU before the machine was satisfied: battery_box=" + box + " (expected 0)");
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-CON04: ring network (two parallel cable paths, same producer + consumer) ──────

	// Ring: GEN(1,2,2) touches two cable arms directly (north + south), each arm runs +x to RING_A3/
	// RING_B3 (x=4). MAC(4,2,2) sits between the two arms but — being a machine, not a cable — is NOT
	// itself part of the union-find graph (NetworkManager.register only unions on cable-to-cable
	// adjacency). The two arms are joined into a real ring by a 3-cable BYPASS column one block further
	// east (x=5), which touches RING_A3/RING_B3's east faces directly: RING_A3-BYPASS_A-BYPASS_MID-
	// BYPASS_B-RING_B3 is an unbroken cable chain, closing the cycle around GEN+MAC without any cable
	// occupying MAC's own cell. No diagonal contact anywhere (R-CON-06 safe).
	private static final BlockPos RING_GEN = new BlockPos(1, 2, 2);
	private static final BlockPos RING_A0 = new BlockPos(1, 2, 1); // GEN's north neighbour
	private static final BlockPos RING_A1 = new BlockPos(2, 2, 1);
	private static final BlockPos RING_A2 = new BlockPos(3, 2, 1);
	private static final BlockPos RING_A3 = new BlockPos(4, 2, 1); // MAC's north neighbour
	private static final BlockPos RING_B0 = new BlockPos(1, 2, 3); // GEN's south neighbour
	private static final BlockPos RING_B1 = new BlockPos(2, 2, 3);
	private static final BlockPos RING_B2 = new BlockPos(3, 2, 3);
	private static final BlockPos RING_B3 = new BlockPos(4, 2, 3); // MAC's south neighbour
	private static final BlockPos RING_MAC = new BlockPos(4, 2, 2);
	private static final BlockPos RING_BYPASS_A = new BlockPos(5, 2, 1); // east of RING_A3
	private static final BlockPos RING_BYPASS_MID = new BlockPos(5, 2, 2); // closes the cycle
	private static final BlockPos RING_BYPASS_B = new BlockPos(5, 2, 3); // east of RING_B3

	private void driveRing(GameTestHelper helper, int n) {
		BlockPos[] cables = {
			RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3,
			RING_BYPASS_A, RING_BYPASS_MID, RING_BYPASS_B,
		};
		for (int i = 0; i < n; i++) {
			var gen = be(helper, RING_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			for (BlockPos c : cables) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var mac = be(helper, RING_MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
	}

	/**
	 * @implements TC-CABLE-001-CON04 — a ring network (two independent cable arms connecting the same
	 *     generator to the same macerator, closed into a cycle by a bypass cable joining the two arms
	 *     directly) delivers EU with no deadlock and no infinite loop: union-find merges the two arms
	 *     into one network the instant the closing cable is placed (tick 0), and {@link
	 *     NetworkManager#tickAll} still completes in a bounded number of ticks.
	 * @covers R-CON-05
	 *
	 *     <p>The two arms are built first as two disjoint networks (verified via {@code networkAt(A0) !=
	 *     networkAt(B0)}); the closing bypass cable is placed last and is cable-to-cable adjacent to both
	 *     arms (RING_A3 and RING_B3), so the union happens through {@link NetworkManager#register}'s
	 *     normal adjacency scan — no product change needed, only a rig that actually closes a cable cycle
	 *     (the prior rig only shared the macerator as a non-cable endpoint, which the union-find graph
	 *     never sees). {@link NetworkManager#networkCount} is per-{@code ServerLevel}, not per-structure,
	 *     so a raw count is unusable here — other gametests run concurrently in the same shared level —
	 *     hence the identity comparison instead of an absolute total.
	 */
	@GameTest
	public void tcCable001Con04_ringNetworkNoDeadlock(GameTestHelper helper) {
		// FACING = EAST: the generator's inert FACING face (east) and its opposite (west) are unused by
		// the ring's cable arms (which meet it from north/RING_A0 and south/RING_B0), so both arms stay on
		// working (non-FACING) faces regardless of the FACING-inert rule (R-NRG-03/D4).
		helper.setBlock(RING_GEN, ModBlocks.GENERATOR.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(RING_A0, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_A1, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_A2, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_A3, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_B0, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_B1, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_B2, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_B3, ModBlocks.COPPER_CABLE);
		// FACING = EAST: the macerator's inert FACING face (east) and its opposite (west) are both
		// unused by the ring's cable arms (which meet it from north/RING_A3 and south/RING_B3), so
		// both arms stay on working (non-FACING) faces regardless of the FACING-inert rule (R-NRG-03/D4).
		helper.setBlock(RING_MAC, ModBlocks.MACERATOR.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		if (be(helper, RING_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, RING_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}

		// helper.setBlock bypasses CableBlock.setPlacedBy (no LivingEntity placer), so a cable only joins
		// NetworkManager on its own first serverTick (CableBlockEntity.ensureRegistered). Register both
		// arms with one tick each — no NetworkManager.tickAll, so this is still "tick 0" for the network
		// topology, before any EU has moved.
		BlockPos[] armCables = {RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3};
		for (BlockPos c : armCables) {
			tick(helper, be(helper, c));
		}

		// Both arms are up but not yet joined: two disjoint cable networks. (networkCount() is
		// per-ServerLevel, not per-structure — other gametests share this level concurrently — so this
		// asserts identity, not an absolute total.)
		EnergyNetwork armABefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork armBBefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (armABefore == null || armBBefore == null || armABefore == armBBefore) {
			helper.fail("test setup: the two arms must start as disjoint networks before closing the ring");
		}

		// Close the cycle: place + register the bypass cable(s), cable-to-cable adjacent to both RING_A3
		// and RING_B3, so NetworkManager.register's adjacency scan must union both arms into one network
		// immediately (still tick 0 for the topology: registration, not NetworkManager.tickAll).
		helper.setBlock(RING_BYPASS_A, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_BYPASS_MID, ModBlocks.COPPER_CABLE);
		helper.setBlock(RING_BYPASS_B, ModBlocks.COPPER_CABLE);
		tick(helper, be(helper, RING_BYPASS_A));
		tick(helper, be(helper, RING_BYPASS_MID));
		tick(helper, be(helper, RING_BYPASS_B));

		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (net == null || netB == null || net != netB) {
			helper.fail("both ring branches must resolve to the same network (union-find merge on cycle)");
		}

		driveRing(helper, 120);

		long mac = be(helper, RING_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			helper.fail("ring network delivered no EU to the macerator: " + mac);
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-CON05: two LV generators into one BatteryBox — supply sums, throughput not duplicated ──

	/**
	 * @implements TC-CABLE-001-CON05 — two LV generators (8 EU/t each, {@code Config.fuelEuPerTick})
	 *     feed one BatteryBox through separate cables joined into one network; the delivered EU sums
	 *     both sources (bounded by the LV packet cap per consumer, {@link EnergyTier#LV}), it is not
	 *     dropped or duplicated.
	 * @covers R-CON-01
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Con05_twoGeneratorsSumIntoOneConsumer(GameTestHelper helper) {
		// gen A -> cable A -> cable B (cable-to-cable link, one network) <- gen B
		//                        |
		//                     BatteryBox (only consumer, on cable B's remaining orthogonal face)
		BlockPos genA = new BlockPos(1, 2, 1);
		BlockPos cableA = new BlockPos(2, 2, 1);
		BlockPos cableB = new BlockPos(3, 2, 1);
		BlockPos genB = new BlockPos(4, 2, 1);
		BlockPos boxPos = new BlockPos(3, 2, 2); // +z of cable B

		helper.setBlock(genA, ModBlocks.GENERATOR);
		helper.setBlock(cableA, ModBlocks.COPPER_CABLE);
		helper.setBlock(cableB, ModBlocks.COPPER_CABLE);
		helper.setBlock(genB, ModBlocks.GENERATOR);
		// FACING = NORTH so the input face (MOD-006: input on FACING) meets cable B on the box's -z side.
		helper.setBlock(boxPos, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		if (be(helper, genA) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		if (be(helper, genB) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}

		int ticks = 20;
		BlockPos[] cables = {cableA, cableB};
		for (int i = 0; i < ticks; i++) {
			for (BlockPos g : new BlockPos[] {genA, genB}) {
				var genBe = be(helper, g);
				if (genBe != null) {
					tick(helper, genBe);
				}
			}
			for (BlockPos c : cables) {
				var cableBe = be(helper, c);
				if (cableBe != null) {
					tick(helper, cableBe);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, boxPos);
			if (box != null) {
				tick(helper, box);
			}
		}

		EnergyNetwork netA = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cableA));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cableB));
		if (netA == null || netA != netB) {
			helper.fail("cable A and cable B must be one network (they are directly adjacent)");
		}
		long got = be(helper, boxPos) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		// A single generator alone could deliver at most fuelEuPerTick × ticks; two summed sources must
		// exceed that single-source ceiling (bounded above by the LV packet cap × ticks either way).
		long singleSourceCeiling = Config.fuelEuPerTick * (long) ticks;
		if (got <= singleSourceCeiling) {
			helper.fail("battery_box should receive summed supply from two generators, got only " + got
					+ " (a single generator alone could not exceed " + singleSourceCeiling + " over " + ticks
					+ " ticks)");
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-PHY09: diagonal (edge/corner-only) contact does NOT connect cables ──────────────

	/**
	 * @implements TC-CABLE-001-PHY09 — two cables offset diagonally (shift on both X and Y, touching
	 *     only at a corner/edge, never a shared face) do NOT form one network: connections are strictly
	 *     the 6 orthogonal faces, never diagonal.
	 * @covers R-CON-06
	 */
	@GameTest
	public void tcCable001Phy09_diagonalCablesDoNotConnect(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 3, 1); // +x AND +y from a: corner/edge contact only, no shared face
		helper.setBlock(a, ModBlocks.COPPER_CABLE);
		helper.setBlock(b, ModBlocks.COPPER_CABLE);
		if (be(helper, a) instanceof CableBlockEntity ca) {
			ca.ensureRegistered();
		}
		if (be(helper, b) instanceof CableBlockEntity cb) {
			cb.ensureRegistered();
		}
		NetworkManager.tickAll(helper.getLevel());

		EnergyNetwork netA = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(a));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(b));
		if (netA == null || netB == null) {
			helper.fail("both diagonally-placed cables must still register their own (separate) networks");
		}
		if (netA == netB) {
			helper.fail("diagonal (corner/edge-only) cables must NOT merge into one network (R-CON-06)");
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-NRG01: throughput cap <=32 EU/t (LV) even when far more is on offer ────────────

	private static final BlockPos CAP_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CAP_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos CAP_BOX = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-CABLE-001-NRG01 — a generator with an ample pre-charged buffer delivers energy
	 *     through the cable at a bounded rate: at most the per-cable throughput ({@code cableBuffer}
	 *     EU/tick, MOD-070 — the segment carries its buffer size, not the tier voltage) reaches the
	 *     consumer; the surplus is simply not transferred (no overvoltage penalty, R-NRG-04), and the
	 *     cable is not destroyed.
	 * @covers R-NRG-04
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg01_throughputCappedAtLvVoltage(GameTestHelper helper) {
		helper.setBlock(CAP_GEN, ModBlocks.GENERATOR);
		helper.setBlock(CAP_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(CAP_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, CAP_GEN) instanceof GeneratorBlockEntity gen) {
			// No fuel: buffer is fixed but far above any per-tick throughput, so the segment throughput —
			// not supply — is what limits delivery.
			gen.getEnergyStorage().amount = EnergyTier.LV.maxVoltage() * 100;
		}
		if (be(helper, CAP_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0; // empty: room is never the limiting factor either
		}

		// Drive several network ticks: the segment throughput bounds cumulative delivery to cableBuffer
		// EU per tick (MOD-070), so N ticks deliver at most N × cableBuffer — never a 32 EU "packet".
		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			var gen = be(helper, CAP_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			var cable = be(helper, CAP_CABLE);
			if (cable != null) {
				tick(helper, cable);
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, CAP_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}

		long got = be(helper, CAP_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		long perTickCap = Config.cableBuffer;
		if (got > perTickCap * ticks) {
			helper.fail("cable exceeded its throughput cap: delivered=" + got + " > "
					+ (perTickCap * ticks) + " (" + perTickCap + " EU/tick × " + ticks + ")");
		}
		if (got <= 0) {
			helper.fail("cable delivered no EU even though supply and room were both ample: " + got);
		}
		if (helper.getLevel().getBlockState(helper.absolutePos(CAP_CABLE)).getBlock() != ModBlocks.COPPER_CABLE) {
			helper.fail("cable was destroyed/changed by the overvoltage attempt");
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-NRG02: proportional distance loss over a 10-cable line (MOD-021) ────────────────

	// Zig-zag (not a straight line) to fit 10 cables inside the default 8×8×8 gametest region.
	private static final BlockPos LOSS_GEN = new BlockPos(0, 2, 1);
	private static final BlockPos[] LOSS_CABLES = {
		new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1), new BlockPos(7, 2, 1),
		new BlockPos(7, 2, 2), new BlockPos(7, 2, 3), new BlockPos(7, 2, 4),
	};
	private static final BlockPos LOSS_BOX = new BlockPos(7, 2, 5);

	/**
	 * @implements TC-CABLE-001-NRG02 — a 10-cable line loses EU proportional to distance (MOD-021). The
	 *     generator buffer is pre-charged well above the LV packet cap and the BatteryBox is left empty
	 *     (unlimited room), so flow is pinned at 32 EU/tick; each tick the box gains
	 *     {@code 32 − floor(32 × copperCableLossPerBlock × 10)} EU — strictly less than the 32 a lossless
	 *     line would deliver, proving the toll is active and distance-scaled.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 80)
	public void tcCable001Nrg02_lossOverTenCables(GameTestHelper helper) {
		helper.setBlock(LOSS_GEN, ModBlocks.GENERATOR);
		for (BlockPos c : LOSS_CABLES) {
			helper.setBlock(c, ModBlocks.COPPER_CABLE);
		}
		// LOSS_BOX(7,2,5)'s only cable neighbour is LOSS_CABLES' last link at (7,2,4), its −z (north) side.
		helper.setBlock(LOSS_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		// Pre-charge the generator far above the packet cap and leave the box empty (huge room), so flow
		// is pinned at the LV packet cap every tick — deterministic, fuel-independent loss accounting.
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, LOSS_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 200;
		}
		if (be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}

		int ticks = 50;
		for (int i = 0; i < ticks; i++) {
			var gen = be(helper, LOSS_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			for (BlockPos c : LOSS_CABLES) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, LOSS_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}

		long got = be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		// MOD-070: per-cable throughput is the segment buffer (cableBuffer), not the tier voltage. The
		// MOD-021 loss FORMULA is unchanged — it just applies to the actual per-tick flow (cableBuffer).
		long flow = Config.cableBuffer;
		long lossPerTick = (long) Math.floor(flow * Config.copperCableLossPerBlock * 10);
		long deliveredPerTick = flow - lossPerTick;
		// The fill front advances ~1 cable/tick, so the box only starts charging after ~LOSS_CABLES ticks.
		long fillLatency = LOSS_CABLES.length;
		long expected = deliveredPerTick * (ticks - fillLatency);
		if (lossPerTick <= 0) {
			helper.fail("test misconfigured: copperCableLossPerBlock too low to lose EU over 10 cables at this flow");
		}
		if (got >= flow * ticks) {
			helper.fail("expected proportional cable loss over 10 cables but got full lossless delivery: " + got);
		}
		// Delivered total must track the loss model, allowing a couple of cables' worth of latency slack.
		if (got < expected - 2 * flow || got > deliveredPerTick * ticks) {
			helper.fail("delivered EU does not match the loss model: expected ~" + expected
					+ " (deliveredPerTick=" + deliveredPerTick + ", latency~" + fillLatency + "), got " + got);
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-NRG02b: a single-hop line is loss-free even at a full packet (MOD-021 / MOD-073) ─

	private static final BlockPos SHORT_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos SHORT_CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos SHORT_BOX = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-CABLE-001-NRG02 (short-line boundary) — at cable-distance 1 the proportional loss
	 *     floors to zero even at a full 32 EU packet ({@code floor(32 × 0.02 × 1) = 0}), so a consumer one
	 *     cable away receives the full throughput. Narrowed from distance 2 to 1 in MOD-073: at 0.02 a
	 *     2-cable hop already loses 1 EU, so only the single-hop case is loss-free.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg02b_noLossOnShortLine(GameTestHelper helper) {
		helper.setBlock(SHORT_GEN, ModBlocks.GENERATOR);
		helper.setBlock(SHORT_CABLE_A, ModBlocks.COPPER_CABLE);
		helper.setBlock(SHORT_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, SHORT_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 200;
		}
		if (be(helper, SHORT_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}

		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			var gen = be(helper, SHORT_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			var ca = be(helper, SHORT_CABLE_A);
			if (ca != null) {
				tick(helper, ca);
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, SHORT_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}

		long got = be(helper, SHORT_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		// MOD-070: per-cable throughput is now the segment buffer (cableBuffer), not the tier voltage —
		// energy flows THROUGH the wire, so the wire's carry rate is its buffer size.
		long flow = Config.cableBuffer;
		long expected = flow * ticks;
		// Full delivery minus a couple ticks of line fill-front latency; a single hop must not lose EU.
		if (got < expected - 3 * flow) {
			helper.fail("short line lost EU it should not: expected ~" + expected + " (full throughput), got " + got);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-CABLE-001-NRG02 (top-off guard, anti-MOD-009) — even on a lossy 10-cable line a
	 *     nearly-full BatteryBox reaches its <em>exact</em> capacity: the last 1-EU top-off packet floors
	 *     to zero loss ({@code floor(1 × 0.02 × 10) = 0}), so it is delivered, not stranded. A flat
	 *     per-tick toll (the removed MOD-009 formula) would leave it stuck at {@code capacity − loss}.
	 * @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 40)
	public void tcCable001Nrg02c_topsOffExactlyOverLossyLine(GameTestHelper helper) {
		helper.setBlock(LOSS_GEN, ModBlocks.GENERATOR);
		for (BlockPos c : LOSS_CABLES) {
			helper.setBlock(c, ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(LOSS_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		long cap = EnergyTier.LV.maxVoltage();
		long capacity = Config.batteryBoxBuffer;
		if (be(helper, LOSS_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 200;
		}
		if (be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = capacity - 1; // one EU short of full: room = 1
		}

		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			var gen = be(helper, LOSS_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			for (BlockPos c : LOSS_CABLES) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, LOSS_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}

		long got = be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (got != capacity) {
			helper.fail("BatteryBox did not top off to exact capacity over a lossy line (MOD-009 regression): "
					+ got + "/" + capacity);
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-NEG01: cable next to a vanilla furnace — no NPE, no EU leak into it ─────────────

	/**
	 * @implements TC-CABLE-001-NEG01 — a cable adjacent to a vanilla furnace does not leak EU into it:
	 *     {@code EnergyStorage.SIDED.find()} returns null for vanilla blocks (no Team Reborn Energy
	 *     interface exposed), so the network's endpoint discovery simply skips it; no NPE, no crash.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void tcCable001Neg01_vanillaNeighborNoNpe(GameTestHelper helper) {
		BlockPos gen = new BlockPos(1, 2, 1);
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos furnace = new BlockPos(3, 2, 1);
		helper.setBlock(gen, ModBlocks.GENERATOR);
		helper.setBlock(cable, ModBlocks.COPPER_CABLE);
		helper.setBlock(furnace, Blocks.FURNACE);
		if (be(helper, gen) instanceof GeneratorBlockEntity g) {
			g.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}

		EnergyStorage vanillaView = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(furnace),
				Direction.WEST);
		if (vanillaView != null) {
			helper.fail("vanilla furnace unexpectedly exposed an EnergyStorage view");
		}

		for (int i = 0; i < 60; i++) {
			var g = be(helper, gen);
			if (g != null) {
				tick(helper, g);
			}
			var c = be(helper, cable);
			if (c != null) {
				tick(helper, c);
			}
			NetworkManager.tickAll(helper.getLevel()); // must not NPE while probing the furnace neighbour
		}
		helper.succeed();
	}

	// ── TC-CABLE-001-NEG02: two cables with no producer/consumer — no phantom EU, no hang over 10k ticks ──

	/**
	 * @implements TC-CABLE-001-NEG02 — two connected cables with neither a producer nor a consumer:
	 *     over 10 000 ticks there is no NPE walking the network, no EU accumulates "in the air", and the
	 *     network stays correctly asleep the whole time (matches {@code rNrg09_idleNetworkSleeps}, but
	 *     stress-tested over a much longer horizon).
	 * @covers R-NRG-09
	 */
	@GameTest(maxTicks = 20)
	public void tcCable001Neg02_twoEmptyCablesTenThousandTicksNoPhantomEu(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 2, 1);
		helper.setBlock(a, ModBlocks.COPPER_CABLE);
		helper.setBlock(b, ModBlocks.COPPER_CABLE);

		for (int i = 0; i < 10_000; i++) {
			var ca = be(helper, a);
			if (ca != null) {
				tick(helper, ca);
			}
			var cb = be(helper, b);
			if (cb != null) {
				tick(helper, cb);
			}
			NetworkManager.tickAll(helper.getLevel()); // must never NPE / hang across 10k synthetic ticks
		}

		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(a));
		if (net == null) {
			helper.fail("no network formed for two connected cables");
		}
		if (net.isAwake()) {
			helper.fail("a producer-less, consumer-less network must stay asleep");
		}
		if (net.lastTickMoved() != 0L) {
			helper.fail("phantom EU moved on a network with no producer and no consumer: " + net.lastTickMoved());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-CABLE-001-NRG03 — a cabled generator is never drained into the void when its only
	 *     consumer accepts less than a full LV packet. {@code EnergyNetwork.tick} sizes each consumer's
	 *     demand from a simulated {@code insert} (so {@code room} is already capped by the consumer's
	 *     {@code maxInsert}); {@code serveClass} therefore never over-pulls, and {@code returnRoundRobin}
	 *     never receives a surplus to push back into the generator (which publishes {@code maxInsert == 0}
	 *     and would silently destroy it — the same EU-loss shape fixed in {@code EnergyMover}). This drives
	 *     a generator whose only consumer has just 5 EU of room (far below the 32 EU packet cap) and asserts
	 *     the generator loses EXACTLY what the consumer gains — conservation, no void loss. @covers R-NRG-15
	 */
	@GameTest
	public void tcCable001Nrg03_generatorNotDrainedByPartialConsumer(GameTestHelper helper) {
		helper.setBlock(GEN, ModBlocks.GENERATOR);
		helper.setBlock(CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(MAC, ModBlocks.MACERATOR);

		// Generator with a pre-charged buffer but NO fuel: produce() == 0, so its buffer only ever goes
		// DOWN and any drain is measurable without production masking it. It publishes maxInsert == 0, so
		// a surplus pushed back by returnRoundRobin would be destroyed, not restored.
		long genStart = Config.generatorBuffer;
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = genStart;
			gen.setChanged();
		}
		// Macerator with NO input never processes, so its room only shrinks as it fills. Pre-charge it to
		// leave exactly 5 EU of room — far below a full LV packet (32). If the pull were sized by packetCap
		// instead of the consumer's simulated insert, serveClass would pull ~32, insert only 5, and hand a
		// 27 EU surplus to returnRoundRobin -> destroyed against the maxInsert==0 generator.
		final long room = 5;
		long macStart = Config.maceratorBuffer - room;
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = macStart;
			mac.setChanged();
		}

		drive(helper, 10);

		long genEnd = be(helper, GEN) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long macEnd = be(helper, MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		long genDrain = genStart - genEnd;
		long macGain = macEnd - macStart;

		if (macEnd != Config.maceratorBuffer) {
			helper.fail("macerator did not top off its 5 EU of room: " + macEnd + "/" + Config.maceratorBuffer);
		}
		// MOD-070: this is now a SYSTEM-WIDE conservation check. The generator fills the cable line
		// buffer as well as the macerator, so gen_drain == mac_gain + cable_buffered + loss (loss floors
		// to 0 for a 5 EU flow over this short line). LHS > RHS would mean EU vanished into the void.
		// (The original narrow "surplus destroyed against a maxInsert==0 generator" path no longer applies
		// to a MACHINE consumer under MOD-070 — a machine's surplus returns to the insertable cable
		// buffers, not the generator — so this guards conservation across the whole line instead.)
		long cableBuffered = be(helper, CABLE) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : 0;
		if (genDrain != macGain + cableBuffered) {
			helper.fail("generator lost " + genDrain + " EU but macerator gained " + macGain
					+ " and cables buffered " + cableBuffered
					+ " — surplus destroyed in returnRoundRobin (EnergyMover-class leak on the cable path)");
		}
		helper.succeed();
	}

	// ── MOD-070: segment-to-segment flow — cables carry a live buffer, energy does not teleport ────

	private static final BlockPos FLOW_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] FLOW_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1),
	};
	private static final BlockPos FLOW_MAC = new BlockPos(7, 2, 1);

	private void buildFlowLine(GameTestHelper helper) {
		helper.setBlock(FLOW_GEN, ModBlocks.GENERATOR);
		for (BlockPos c : FLOW_CABLES) {
			helper.setBlock(c, ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(FLOW_MAC, ModBlocks.MACERATOR);
		if (be(helper, FLOW_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, FLOW_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	/** Tick generator + every flow cable + NetworkManager + macerator for {@code n} ticks (null-safe). */
	private void driveFlow(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			var gen = be(helper, FLOW_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			for (BlockPos c : FLOW_CABLES) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var mac = be(helper, FLOW_MAC);
			if (mac != null) {
				tick(helper, mac);
			}
		}
	}

	private long cableAmount(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
	}

	private long macFlow(GameTestHelper helper) {
		return be(helper, FLOW_MAC) instanceof MaceratorBlockEntity mac ? mac.getEnergyStorage().getAmount() : -1;
	}

	/**
	 * @implements TC-CABLE-001-NRG04 — MOD-070 accumulation: energy flows through the cable buffers
	 *     segment-to-segment, it does not teleport producer→consumer. After driving a fueled generator
	 *     down a 5-cable line to a working macerator, the intermediate cable holds real EU (asserts >0
	 *     mid-line — impossible under the old teleport model where the buffer was dead) while delivery
	 *     still works, and no cable ever exceeds its tiny {@code cableBuffer} cap (the "no battery from
	 *     wires" ceiling, enforced per segment). @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 80)
	public void tcCable001Nrg04_lineAccumulatesInSegments(GameTestHelper helper) {
		buildFlowLine(helper);
		driveFlow(helper, 40);

		if (macFlow(helper) <= 0) {
			helper.fail("macerator received no EU over the cable line");
		}
		long mid = cableAmount(helper, FLOW_CABLES[2]);
		if (mid <= 0) {
			helper.fail("intermediate cable holds no EU — energy teleported instead of flowing through the line");
		}
		for (BlockPos c : FLOW_CABLES) {
			long a = cableAmount(helper, c);
			if (a > Config.cableBuffer) {
				helper.fail("cable " + c + " holds " + a + " EU > cableBuffer=" + Config.cableBuffer
						+ " — per-segment ceiling breached (battery from wires)");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-CABLE-001-NRG05 — MOD-070 break retains at source: once the line has filled,
	 *     removing a middle cable splits the network; the source-side half loses its consumer and sleeps,
	 *     but the EU already buffered in its cables is retained — not voided, not teleported to the now
	 *     disconnected macerator. Asserts the source-side cables still hold >0 EU after the break (the
	 *     "the remainder briefly lingers in the wires" criterion; 0 under the old dead-buffer model).
	 *     @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 100)
	public void tcCable001Nrg05_breakRetainsAtSource(GameTestHelper helper) {
		buildFlowLine(helper);
		driveFlow(helper, 40); // fill the line

		// Break at the middle cable: source half = GEN + cables[0..1]; far half = cables[3..4] + MAC.
		helper.setBlock(FLOW_CABLES[2], Blocks.AIR);
		long macAtBreak = macFlow(helper);
		driveFlow(helper, 10);

		long sourceSide = Math.max(0, cableAmount(helper, FLOW_CABLES[0]))
				+ Math.max(0, cableAmount(helper, FLOW_CABLES[1]));
		if (sourceSide <= 0) {
			helper.fail("source-side cables retained no EU after the break — the line buffer is not real");
		}
		// Negative half: the disconnected far side (now producer-less) must NOT receive any more EU —
		// nothing teleports across the gap from the source-side generator/buffers.
		long macAfter = macFlow(helper);
		if (macAfter > macAtBreak) {
			helper.fail("macerator gained EU after the line was cut (" + macAtBreak + " -> " + macAfter
					+ ") — energy teleported across the break");
		}
		helper.succeed();
	}

	// ── MOD-070: a storage source never charges another storage sink (no battery↔battery wash) ─────

	// Both boxes FACING WEST: BB_SRC's OUT (east/back) feeds the cable; BB_DST's IN (west/front) draws it.
	private static final BlockPos WASH_SRC = new BlockPos(1, 2, 1);
	private static final BlockPos WASH_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos WASH_DST = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-CABLE-001-NRG06 — MOD-070 storage priority: a charged BatteryBox connected by cable
	 *     to an empty BatteryBox (no generator, no machine) must NOT charge it — storage never sources for
	 *     another storage sink. Under the old paired path the empty box would drain the charged one
	 *     (pointless wash, lossy over distance). Asserts the destination stays empty and the source keeps
	 *     its charge. @covers R-NRG-08
	 */
	@GameTest(maxTicks = 60)
	public void tcCable001Nrg06_storageDoesNotChargeStorage(GameTestHelper helper) {
		helper.setBlock(WASH_SRC, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(WASH_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(WASH_DST, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long srcStart = Config.batteryBoxBuffer;
		if (be(helper, WASH_SRC) instanceof BatteryBoxBlockEntity src) {
			src.getEnergyStorage().amount = srcStart; // full
		}
		if (be(helper, WASH_DST) instanceof BatteryBoxBlockEntity dst) {
			dst.getEnergyStorage().amount = 0L; // empty
		}
		for (int i = 0; i < 20; i++) {
			var s = be(helper, WASH_SRC);
			if (s != null) {
				tick(helper, s);
			}
			var c = be(helper, WASH_CABLE);
			if (c != null) {
				tick(helper, c);
			}
			NetworkManager.tickAll(helper.getLevel());
			var d = be(helper, WASH_DST);
			if (d != null) {
				tick(helper, d);
			}
		}
		// Guard against a false green: the test only means something if a network actually formed on the
		// cable (otherwise "nothing happened" would pass even if the anti-wash logic were broken).
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

	// ── MOD-070 audit follow-up: storage charges THROUGH the line; lone storage source sleeps ──────

	private static final BlockPos STO_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] STO_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
	};
	private static final BlockPos STO_BOX = new BlockPos(5, 2, 1);

	/**
	 * @implements TC-CABLE-001-NRG07 — MOD-070 storage-through-line: a BatteryBox charged over a
	 *     multi-cable line pulls its EU THROUGH the wires (not a bypass) — the intermediate cable holds
	 *     real EU while the box fills. This is the storage analogue of NRG04 and the direct regression for
	 *     the in-game bug where a source→cable→BatteryBox link left the cable empty. @covers PERFORMANCE.md
	 */
	@GameTest(maxTicks = 100)
	public void tcCable001Nrg07_storageChargesThroughLine(GameTestHelper helper) {
		helper.setBlock(STO_GEN, ModBlocks.GENERATOR);
		for (BlockPos c : STO_CABLES) {
			helper.setBlock(c, ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(STO_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST)); // IN faces the cable at (4,2,1)
		if (be(helper, STO_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, STO_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 40; i++) {
			var gen = be(helper, STO_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			for (BlockPos c : STO_CABLES) {
				var cable = be(helper, c);
				if (cable != null) {
					tick(helper, cable);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, STO_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}
		long boxEnergy = be(helper, STO_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long midCable = cableAmount(helper, STO_CABLES[1]);
		if (boxEnergy <= 0) {
			helper.fail("BatteryBox received no EU over the cable line: " + boxEnergy);
		}
		if (midCable <= 0) {
			helper.fail("intermediate cable held no EU while charging a BatteryBox — storage charge "
					+ "bypassed the wire instead of flowing through it (the in-game bug)");
		}
		helper.succeed();
	}

	private static final BlockPos LONE_BOX = new BlockPos(1, 2, 1);
	private static final BlockPos LONE_CABLE = new BlockPos(2, 2, 1);

	/**
	 * @implements R-NRG-09 (MOD-070 audit) — a lone storage source (a charged BatteryBox whose OUT face is
	 *     cabled) with NO consumer and NO generator must sleep, not spin forever. Storage discharges into
	 *     the line only for a machine deficit, so with no machine it charges nothing; keeping the network
	 *     awake would run a no-op tick (and an O(cables) scan) every tick indefinitely. Asserts the network
	 *     is asleep and the cable stays empty. @covers R-NRG-09
	 */
	@GameTest(maxTicks = 40)
	public void rNrg09c_loneStorageSourceSleeps(GameTestHelper helper) {
		// BatteryBox facing WEST → OUT (east/back) touches the cable at (2,2,1); IN (west) faces air, so it
		// is a pure producer (storage source) with no consumer anywhere in the network.
		helper.setBlock(LONE_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(LONE_CABLE, ModBlocks.COPPER_CABLE);
		if (be(helper, LONE_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = Config.batteryBoxBuffer; // full: plenty to (wrongly) push
		}
		for (int i = 0; i < 10; i++) {
			var box = be(helper, LONE_BOX);
			if (box != null) {
				tick(helper, box);
			}
			var cable = be(helper, LONE_CABLE);
			if (cable != null) {
				tick(helper, cable);
			}
			NetworkManager.tickAll(helper.getLevel());
		}
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(LONE_CABLE));
		if (net == null) {
			helper.fail("no network formed on the lone storage-source cable");
		}
		if (net.isAwake()) {
			helper.fail("lone storage-source network is awake — it has no consumer and no generator, so it "
					+ "must sleep, not spin a no-op tick forever");
		}
		if (cableAmount(helper, LONE_CABLE) != 0L) {
			helper.fail("a lone storage source charged the wire: " + cableAmount(helper, LONE_CABLE)
					+ " (storage fills the line only for a machine deficit, not on its own)");
		}
		helper.succeed();
	}

	// ── MOD-156: the LAZY registration path (CableBlockEntity.ensureRegistered, called from
	// onServerTick) must re-register a cable that survives a world/chunk reload while its
	// NetworkManager-side bookkeeping is gone. Every other rig in this file builds via helper.setBlock,
	// which — same as real chunk load — never calls CableBlock#setPlacedBy (no LivingEntity placer), so
	// they all already register lazily on FIRST tick. None of them, though, exercise RE-registration of
	// an already-loaded cable after its network entry disappears, which is exactly what a relog/chunk
	// reload does: the NetworkManager per-level registry (an in-memory IdentityHashMap, never persisted)
	// starts empty again, while a freshly deserialized CableBlockEntity's `registered` field (transient,
	// not saved) also starts false. This rig reproduces BOTH halves of that reset, not just one. ──────

	private static final BlockPos RELOG_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos RELOG_CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos RELOG_CABLE_B = new BlockPos(3, 2, 1);
	private static final BlockPos RELOG_BOX = new BlockPos(4, 2, 1);

	/**
	 * Forces a cable block entity back into the "never registered" state a freshly-deserialized
	 * instance would start in (the {@code registered} field is {@code transient} — never saved/loaded —
	 * so a real world reload always produces {@code registered == false} on the new object). Reflection
	 * is the only way to reach it: there is deliberately no public reset API on
	 * {@link CableBlockEntity}, since production code always starts at {@code false} for free.
	 */
	private static void forceUnregisteredState(CableBlockEntity cable) {
		try {
			Field registered = CableBlockEntity.class.getDeclaredField("registered");
			registered.setAccessible(true);
			registered.setBoolean(cable, false);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** Tick generator + both RELOG cables + NetworkManager + the box for {@code n} ticks (null-safe). */
	private void driveRelog(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			var gen = be(helper, RELOG_GEN);
			if (gen != null) {
				tick(helper, gen);
			}
			var a = be(helper, RELOG_CABLE_A);
			if (a != null) {
				tick(helper, a);
			}
			var b = be(helper, RELOG_CABLE_B);
			if (b != null) {
				tick(helper, b);
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, RELOG_BOX);
			if (box != null) {
				tick(helper, box);
			}
		}
	}

	/**
	 * @implements MOD-156 — a cable that is still block-loaded but whose network registration was lost
	 *     (the exact shape of a player relog / chunk reload: a fresh {@link NetworkManager} registry and
	 *     a fresh {@code registered == false} block entity) re-registers on its own next server tick via
	 *     {@link CableBlockEntity#ensureRegistered()} in {@link CableBlockEntity#onServerTick}, and energy
	 *     delivery resumes. Drives generator → cable A → cable B → BatteryBox to a working baseline, then
	 *     — per-cable, NOT via the level-wide {@link NetworkManager#clear(net.minecraft.server.level.ServerLevel)}
	 *     (this test region shares its {@code ServerLevel} with every other concurrently-running gametest
	 *     structure, so a level-wide clear would corrupt their networks too) — removes both cables from the
	 *     registry with the public {@link NetworkManager#unregister(CableBlockEntity)} and resets each
	 *     entity's {@code registered} flag, reproducing "loaded block, forgotten registration" without
	 *     touching any other structure's state. Asserts the network re-forms, the intermediate cable buffer
	 *     genuinely refills (MOD-070 gotcha: energy flows THROUGH the wire, not around it), and the
	 *     BatteryBox keeps gaining EU past its pre-"reload" baseline.
	 * @covers R-CON-05, R-NRG-09
	 */
	@GameTest(maxTicks = 100)
	public void mod156_lazyPathReregistersAfterReload(GameTestHelper helper) {
		helper.setBlock(RELOG_GEN, ModBlocks.GENERATOR);
		helper.setBlock(RELOG_CABLE_A, ModBlocks.COPPER_CABLE);
		helper.setBlock(RELOG_CABLE_B, ModBlocks.COPPER_CABLE);
		// FACING = WEST so the BatteryBox's input face (MOD-006: input on FACING) meets cable B's east side.
		helper.setBlock(RELOG_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, RELOG_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}

		// Phase 1 — establish a normal working baseline (this alone already exercises the lazy path once,
		// same as every other rig in this file: helper.setBlock never calls CableBlock#setPlacedBy).
		driveRelog(helper, 30);

		EnergyNetwork netBefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RELOG_CABLE_A));
		if (netBefore == null) {
			helper.fail("test setup: no energy network formed before the simulated reload");
		}
		long boxBeforeReload = be(helper, RELOG_BOX) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1;
		if (boxBeforeReload <= 0) {
			helper.fail("test setup: battery_box received no EU before the simulated reload — baseline invalid");
		}

		// Phase 2 — simulate a relog/chunk reload: drop THIS network's registration (per-cable, so
		// concurrently-running gametest structures sharing the same ServerLevel are unaffected) and reset
		// each cable's own bookkeeping flag, exactly what a fresh, freshly-deserialized block entity starts
		// with after a real reload.
		if (be(helper, RELOG_CABLE_A) instanceof CableBlockEntity cableA) {
			NetworkManager.unregister(cableA);
			forceUnregisteredState(cableA);
		}
		if (be(helper, RELOG_CABLE_B) instanceof CableBlockEntity cableB) {
			NetworkManager.unregister(cableB);
			forceUnregisteredState(cableB);
		}
		if (NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RELOG_CABLE_A)) != null
				|| NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RELOG_CABLE_B)) != null) {
			helper.fail("test setup: the simulated reload did not actually drop the network registration");
		}

		// Phase 3 — the cables are still block-loaded (never removed from the world), so only the lazy
		// onServerTick path — never CableBlock#setPlacedBy, nothing else calls this — can bring them back.
		driveRelog(helper, 40);

		EnergyNetwork netAfter = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RELOG_CABLE_A));
		EnergyNetwork netAfterB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RELOG_CABLE_B));
		if (netAfter == null || netAfter != netAfterB) {
			helper.fail("cable did not re-register with NetworkManager after the simulated reload "
					+ "(ensureRegistered's lazy onServerTick path did not run)");
		}

		// MOD-070 gotcha: check the INTERMEDIATE cable's own buffer, not just the final consumer — energy
		// must flow THROUGH the re-registered wire, not bypass it.
		long cableABuffer = cableAmount(helper, RELOG_CABLE_A);
		long cableBBuffer = cableAmount(helper, RELOG_CABLE_B);
		if (cableABuffer <= 0 && cableBBuffer <= 0) {
			helper.fail("neither cable holds any EU after re-registration — energy is not flowing through the "
					+ "wire post-reload (cableA=" + cableABuffer + ", cableB=" + cableBBuffer + ")");
		}

		long boxAfterReload = be(helper, RELOG_BOX) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1;
		if (boxAfterReload <= boxBeforeReload) {
			helper.fail("battery_box did not gain EU past its pre-reload baseline: before=" + boxBeforeReload
					+ " after=" + boxAfterReload + " — delivery did not resume after the simulated reload");
		}
		helper.succeed();
	}
}
