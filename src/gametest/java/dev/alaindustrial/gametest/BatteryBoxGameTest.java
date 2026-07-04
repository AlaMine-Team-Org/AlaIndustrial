package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModDataComponents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the BatteryBox (LV energy storage). Unlike machines/generators it has no
 * inventory and no production — it accepts, holds, and emits EU. Migrated from legacy persistence
 * checks; the buffer node behaviour the network relies on.
 */
public class BatteryBoxGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static BatteryBoxBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX);
		BatteryBoxBlockEntity be = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		if (be == null) {
			helper.fail("battery_box block entity missing");
		}
		return be;
	}

	/**
	 * @implements TC-BATTERYBOX-001-FUN01 — storage both accepts (insert) and emits (extract) EU, and
	 *     stores up to its configured capacity.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Fun01_acceptsAndEmits(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		if (!bat.getEnergyStorage().supportsInsertion()) {
			helper.fail("battery_box must accept energy (maxInsert > 0)");
		}
		if (!bat.getEnergyStorage().supportsExtraction()) {
			helper.fail("battery_box must emit energy (maxExtract > 0)");
		}
		if (bat.getEnergyStorage().getCapacity() <= 0) {
			helper.fail("battery_box capacity must be positive");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-PER01 — stored EU survives an NBT save/load round-trip (the storage
	 *     drop carries its charge — R-BRK-07's prerequisite).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcBatteryBox001Per01_chargeSurvivesNbt(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		long charge = Math.min(12345L, bat.getEnergyStorage().getCapacity());
		bat.getEnergyStorage().amount = charge;

		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = bat.saveCustomOnly(registries);
		BatteryBoxBlockEntity restored = new BatteryBoxBlockEntity(bat.getBlockPos(),
				helper.getLevel().getBlockState(bat.getBlockPos()));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (restored.getEnergyStorage().getAmount() != charge) {
			helper.fail("battery_box charge lost on NBT round-trip: " + charge + " -> "
					+ restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-BRK07 — stored EU rides the dropped item via the STORED_ENERGY
	 *     component (what the loot table copies off the broken block entity) and is restored on place.
	 * @covers R-BRK-07
	 */
	@GameTest
	public void tcBatteryBox001Brk07_energyCarriedByComponent(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		long charge = Math.min(12345L, bat.getEnergyStorage().getCapacity());
		bat.getEnergyStorage().amount = charge;

		// collect = what loot `copy_components source=block_entity` reads off the broken block.
		DataComponentMap map = bat.collectComponents();
		Long carried = map.get(ModDataComponents.STORED_ENERGY);
		if (carried == null || carried != charge) {
			helper.fail("STORED_ENERGY not emitted on drop: " + carried);
		}

		// apply = what placement does to the freshly placed block entity.
		BatteryBoxBlockEntity restored = new BatteryBoxBlockEntity(bat.getBlockPos(),
				helper.getLevel().getBlockState(bat.getBlockPos()));
		restored.applyComponents(map, DataComponentPatch.EMPTY);
		if (restored.getEnergyStorage().getAmount() != charge) {
			helper.fail("charge not restored from component: " + restored.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-BRK07b — a machine (not storage) does NOT carry EU on its drop
	 *     (R-BRK-07 second half: machines lose their buffer on break).
	 */
	@GameTest
	public void tcBatteryBox001Brk07b_machineDropsNoEnergy(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.MACERATOR);
		MachineBlockEntity mac = helper.getBlockEntity(POS, MachineBlockEntity.class);
		mac.getEnergyStorage().amount = 5000;
		if (mac.collectComponents().get(ModDataComponents.STORED_ENERGY) != null) {
			helper.fail("a machine leaked the STORED_ENERGY component");
		}
		helper.succeed();
	}

	// ── PRF — buffer cap and per-tick rate (BVA), through the real Team Reborn EnergyStorage API ────

	/**
	 * @implements TC-BATTERYBOX-001-PRF01 — insert(100_000, EXECUTE) through the TR API caps at
	 *     getCapacity() (20 000 EU from Config), not at whatever amount= would have allowed.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Prf01_insertCapsAtCapacity(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		// A single insert() is rate-capped at maxInsert (32 EU/t LV), separate from capacity. Insert
		// repeatedly until the buffer saturates, then verify it caps at capacity (never over).
		for (int i = 0; i < 1000; i++) {
			long moved;
			try (Transaction tx = Transaction.openOuter()) {
				moved = bat.getEnergyStorage().insert(100_000L, tx);
				tx.commit();
			}
			if (moved == 0) {
				break;
			}
		}
		if (bat.getEnergyStorage().getAmount() != bat.getEnergyStorage().getCapacity()) {
			helper.fail("battery_box overshot/undershot capacity on insert: "
					+ bat.getEnergyStorage().getAmount() + "/" + bat.getEnergyStorage().getCapacity());
		}
		if (bat.getEnergyStorage().getCapacity() != Config.batteryBoxBuffer) {
			helper.fail("battery_box capacity does not match Config.batteryBoxBuffer: "
					+ bat.getEnergyStorage().getCapacity() + " != " + Config.batteryBoxBuffer);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF02 — extract(1_000, EXECUTE) from an empty buffer returns 0 and
	 *     getAmount() stays 0 (does not go negative).
	 * @covers R-NRG-02
	 */
	@GameTest
	public void tcBatteryBox001Prf02_extractFromEmptyReturnsZero(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = 0;
		long extracted;
		try (Transaction tx = Transaction.openOuter()) {
			extracted = bat.getEnergyStorage().extract(1_000L, tx);
			tx.commit();
		}
		if (extracted != 0) {
			helper.fail("empty battery_box extracted " + extracted + " EU, expected 0");
		}
		if (bat.getEnergyStorage().getAmount() != 0) {
			helper.fail("battery_box amount went below 0: " + bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF03 — the SIDED view on the input face (FACING) never offers more
	 *     than the LV rate (32 EU/t = EnergyTier.LV.maxVoltage()) per SIMULATE call, even on an empty buffer.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Prf03_inputRateCappedAtLv(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX); // default FACING = NORTH (input face)
		BatteryBoxBlockEntity bat = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		bat.getEnergyStorage().amount = 0;
		EnergyStorage in = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.NORTH);
		if (in == null) {
			helper.fail("no input-face EnergyStorage view on battery_box");
			return;
		}
		long offered;
		try (Transaction tx = Transaction.openOuter()) {
			offered = in.insert(1_000L, tx);
			// SIMULATE: do not commit.
		}
		if (offered > EnergyTier.LV.maxVoltage()) {
			helper.fail("battery_box input face offered " + offered + " EU, expected <= "
					+ EnergyTier.LV.maxVoltage());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF04 — the SIDED view on the output face (opposite FACING) never
	 *     offers more than the LV rate (32 EU/t) per SIMULATE call, even at a full buffer.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Prf04_outputRateCappedAtLv(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX); // default FACING = NORTH, output = SOUTH
		BatteryBoxBlockEntity bat = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		bat.getEnergyStorage().amount = bat.getEnergyStorage().getCapacity();
		EnergyStorage out = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.SOUTH);
		if (out == null) {
			helper.fail("no output-face EnergyStorage view on battery_box");
			return;
		}
		long offered;
		try (Transaction tx = Transaction.openOuter()) {
			offered = out.extract(1_000L, tx);
			// SIMULATE: do not commit.
		}
		if (offered > EnergyTier.LV.maxVoltage()) {
			helper.fail("battery_box output face offered " + offered + " EU, expected <= "
					+ EnergyTier.LV.maxVoltage());
		}
		helper.succeed();
	}

	// ── NEG — no passive drain, no passive charge, no leak to vanilla neighbours ────────────────────

	/**
	 * @implements TC-BATTERYBOX-001-NEG01 — a charged battery_box left alone (no neighbours, no load)
	 *     does not lose EU over 1000 server ticks (no passive self-drain in onServerTick).
	 * @covers R-NRG-13
	 */
	@GameTest
	public void tcBatteryBox001Neg01_noSelfDrainOver1000Ticks(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = 10_000L;
		for (int i = 0; i < 1000; i++) {
			bat.serverTick(helper.getLevel(), bat.getBlockPos(), helper.getLevel().getBlockState(bat.getBlockPos()));
		}
		if (bat.getEnergyStorage().getAmount() != 10_000L) {
			helper.fail("battery_box self-drained: 10000 -> " + bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-NEG02 — an empty battery_box left alone (no neighbours, no source)
	 *     does not gain EU out of nowhere over 1000 server ticks.
	 */
	@GameTest
	public void tcBatteryBox001Neg02_noSelfChargeOver1000Ticks(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = 0;
		for (int i = 0; i < 1000; i++) {
			bat.serverTick(helper.getLevel(), bat.getBlockPos(), helper.getLevel().getBlockState(bat.getBlockPos()));
		}
		if (bat.getEnergyStorage().getAmount() != 0L) {
			helper.fail("battery_box self-charged from nothing: 0 -> " + bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-NEG03 — a charged battery_box next to a vanilla block (furnace) does
	 *     not leak EU into it: EnergyStorage.SIDED.find() is null for vanilla blocks, so the direct-push
	 *     path in onServerTick has nothing to deliver to, and the buffer is unchanged after several ticks.
	 * @covers R-NRG-09
	 */
	@GameTest
	public void tcBatteryBox001Neg03_noLeakToVanillaNeighbor(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = 10_000L;
		// Output face is opposite FACING; default FACING = NORTH, so output = SOUTH.
		BlockPos vanillaPos = POS.relative(Direction.SOUTH);
		helper.setBlock(vanillaPos, Blocks.FURNACE);

		EnergyStorage vanillaView = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(vanillaPos),
				Direction.NORTH);
		if (vanillaView != null) {
			helper.fail("vanilla furnace unexpectedly exposed an EnergyStorage view");
		}

		for (int i = 0; i < 20; i++) {
			bat.serverTick(helper.getLevel(), bat.getBlockPos(), helper.getLevel().getBlockState(bat.getBlockPos()));
		}
		if (bat.getEnergyStorage().getAmount() != 10_000L) {
			helper.fail("battery_box leaked EU toward a vanilla neighbour: 10000 -> "
					+ bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	// ── CON/NET — network topology: ring, break/rejoin, per-face throughput cap, split, full/empty ───

	private BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private void tickAny(GameTestHelper helper, BlockEntity be) {
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

	// Ring rig: a closed loop of 3 cables (a literal cycle in the network graph) with the generator
	// touching two of them — the network must resolve the cycle instead of hanging or double-counting.
	// Box FACING=WEST so its input face meets RING_CABLE_A.
	private static final BlockPos RING_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos RING_CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos RING_CABLE_B = new BlockPos(2, 2, 2);
	private static final BlockPos RING_CABLE_C = new BlockPos(1, 2, 2);
	private static final BlockPos RING_BOX = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-BATTERYBOX-001-CON01 — a ring/cyclic cable topology (generator, three cables forming
	 *     a closed loop back to the generator, one of the loop cables touching the battery_box) charges
	 *     the battery_box, and driving it for a bounded number of ticks completes without hanging (no
	 *     infinite loop / stack overflow on cycle discovery).
	 * @covers R-CON-05
	 */
	@GameTest
	public void tcBatteryBox001Con01_ringTopologyNoHang(GameTestHelper helper) {
		helper.setBlock(RING_GEN, ModBlocks.GENERATOR);
		helper.setBlock(RING_CABLE_A, ModBlocks.COPPER_CABLE); // (2,2,1) — adjacent to gen (+x) and box (-x)
		helper.setBlock(RING_CABLE_B, ModBlocks.COPPER_CABLE); // (2,2,2) — adjacent to A (+z)
		helper.setBlock(RING_CABLE_C, ModBlocks.COPPER_CABLE); // (1,2,2) — adjacent to B (-x) and gen (+z): closes the loop
		helper.setBlock(RING_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, RING_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		for (int i = 0; i < 120; i++) {
			for (BlockPos p : new BlockPos[] { RING_GEN, RING_CABLE_A, RING_CABLE_B, RING_CABLE_C }) {
				var e = be(helper, p);
				if (e != null) {
					tickAny(helper, e);
				}
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, RING_BOX);
			if (box != null) {
				tickAny(helper, box);
			}
		}
		long charge = be(helper, RING_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		if (charge <= 0) {
			helper.fail("ring-fed battery_box received no EU: " + charge);
		}
		helper.succeed();
	}

	// Break/rejoin rig: generator -> cable -> battery_box.
	private static final BlockPos BRJ_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos BRJ_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos BRJ_BOX = new BlockPos(3, 2, 1);

	/**
	 * @implements TC-BATTERYBOX-001-CON02 — removing the only cable stops delivery into the
	 *     battery_box; replacing it resumes flow without player intervention beyond the block placement.
	 * @covers R-CON-04, R-CON-09
	 */
	@GameTest
	public void tcBatteryBox001Con02_breakRejoinCable(GameTestHelper helper) {
		helper.setBlock(BRJ_GEN, ModBlocks.GENERATOR);
		helper.setBlock(BRJ_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(BRJ_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, BRJ_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		java.util.function.BiConsumer<GameTestHelper, Integer> drive = (h, n) -> {
			for (int i = 0; i < n; i++) {
				var gen = be(h, BRJ_GEN);
				if (gen != null) {
					tickAny(h, gen);
				}
				var cable = be(h, BRJ_CABLE);
				if (cable != null) {
					tickAny(h, cable);
				}
				NetworkManager.tickAll(h.getLevel());
				var box = be(h, BRJ_BOX);
				if (box != null) {
					tickAny(h, box);
				}
			}
		};
		drive.accept(helper, 60);
		long chargeAfterFirstRun = be(helper, BRJ_BOX) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1;
		if (chargeAfterFirstRun <= 0) {
			helper.fail("battery_box did not charge before cable removal: " + chargeAfterFirstRun);
		}

		// Break the cable: charge must stop increasing.
		helper.setBlock(BRJ_CABLE, Blocks.AIR);
		drive.accept(helper, 40);
		long chargeAfterBreak = be(helper, BRJ_BOX) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1;
		if (chargeAfterBreak != chargeAfterFirstRun) {
			helper.fail("battery_box kept charging through an air gap: " + chargeAfterFirstRun + " -> "
					+ chargeAfterBreak);
		}

		// Rejoin the cable and drain the box back down so we can observe fresh delivery.
		if (be(helper, BRJ_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		helper.setBlock(BRJ_CABLE, ModBlocks.COPPER_CABLE);
		drive.accept(helper, 80);
		long chargeAfterRejoin = be(helper, BRJ_BOX) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1;
		if (chargeAfterRejoin <= 0) {
			helper.fail("flow did not resume after the cable was replaced: " + chargeAfterRejoin);
		}
		helper.succeed();
	}

	// Throughput-cap rig: 5 generators feeding one cable feeding the battery_box's single input face
	// (combined output >> 32 EU/t LV cap on that face). CAP_CABLE sits at the hub; generators are
	// orthogonally adjacent to it directly (west/north/up) plus two via a stub cable, so every contact
	// is orthogonal — no diagonal touches per R-CON-06.
	private static final BlockPos CAP_BOX = new BlockPos(4, 2, 3);
	private static final BlockPos CAP_CABLE = new BlockPos(3, 2, 3);
	private static final BlockPos CAP_STUB_CABLE = new BlockPos(3, 2, 4);
	private static final BlockPos[] CAP_GENS = {
		new BlockPos(2, 2, 3), // -x of hub
		new BlockPos(3, 2, 2), // -z of hub
		new BlockPos(3, 3, 3), // +y of hub
		new BlockPos(3, 2, 5), // -z of the stub cable
		new BlockPos(4, 2, 4), // +x of the stub cable
	};

	/**
	 * @implements TC-BATTERYBOX-001-CON04 — five generators (combined well above the 32 EU/t LV rate)
	 *     feed one cable into the battery_box's single input face; the face-level rate cap holds: charge
	 *     never grows by more than the LV rate in a single tick, even though supply exceeds it.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Con04_faceThroughputCappedUnderExcessSupply(GameTestHelper helper) {
		for (BlockPos g : CAP_GENS) {
			helper.setBlock(g, ModBlocks.GENERATOR);
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
				gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
			}
		}
		helper.setBlock(CAP_CABLE, ModBlocks.COPPER_CABLE);
		helper.setBlock(CAP_STUB_CABLE, ModBlocks.COPPER_CABLE); // orthogonal to CAP_CABLE (+z), links the stub gens in
		helper.setBlock(CAP_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));

		long maxSingleTickGain = 0;
		long prev = 0;
		for (int i = 0; i < 60; i++) {
			for (BlockPos g : CAP_GENS) {
				var e = be(helper, g);
				if (e != null) {
					tickAny(helper, e);
				}
			}
			var cable = be(helper, CAP_CABLE);
			if (cable != null) {
				tickAny(helper, cable);
			}
			var stub = be(helper, CAP_STUB_CABLE);
			if (stub != null) {
				tickAny(helper, stub);
			}
			NetworkManager.tickAll(helper.getLevel());
			var box = be(helper, CAP_BOX);
			if (box != null) {
				tickAny(helper, box);
			}
			long now = be(helper, CAP_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : prev;
			maxSingleTickGain = Math.max(maxSingleTickGain, now - prev);
			prev = now;
		}
		if (maxSingleTickGain > EnergyTier.LV.maxVoltage()) {
			helper.fail("battery_box input face exceeded the LV rate in a single tick: " + maxSingleTickGain
					+ " > " + EnergyTier.LV.maxVoltage());
		}
		if (prev <= 0) {
			helper.fail("battery_box received no EU despite ample oversupply: " + prev);
		}
		helper.succeed();
	}

	// Split rig: charged battery_box -> one cable -> two macerators (both empty input, so they only buffer).
	private static final BlockPos SPLIT_BOX = new BlockPos(1, 2, 1);
	private static final BlockPos SPLIT_CABLE = new BlockPos(2, 2, 1);
	private static final BlockPos SPLIT_MAC_A = new BlockPos(3, 2, 1);
	private static final BlockPos SPLIT_MAC_B = new BlockPos(2, 2, 2);

	/**
	 * @implements TC-BATTERYBOX-001-CON05 — a charged battery_box's output feeds one cable that branches
	 *     to two macerators; both receive a share of the flow (no "first consumer takes all").
	 * @covers R-NRG-08, R-CON-01
	 */
	@GameTest
	public void tcBatteryBox001Con05_splitsToTwoConsumers(GameTestHelper helper) {
		// SPLIT_CABLE(2,2,1) is +x (EAST) of SPLIT_BOX(1,2,1); the box's output is the face OPPOSITE
		// FACING, so FACING=WEST puts the output on EAST, toward the cable. (An earlier version of this
		// rig set FACING=NORTH — output=SOUTH — which pointed away from the cable entirely and never
		// connected, masking the real split behaviour under test.)
		helper.setBlock(SPLIT_BOX, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST)); // output = EAST, toward the cable
		helper.setBlock(SPLIT_CABLE, ModBlocks.COPPER_CABLE);
		// SPLIT_MAC_A(3,2,1) touches the cable on its own WEST face (a working face, default FACING=NORTH
		// is fine). SPLIT_MAC_B(2,2,2) touches the cable on its own NORTH face — which IS the default
		// FACING and therefore energy-inert (D-FACING) — so it needs a non-default FACING to actually
		// connect; SOUTH keeps NORTH (its cable-facing side) as a working face.
		helper.setBlock(SPLIT_MAC_A, ModBlocks.MACERATOR);
		helper.setBlock(SPLIT_MAC_B, ModBlocks.MACERATOR.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		if (be(helper, SPLIT_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = bb.getEnergyStorage().getCapacity();
		}
		// Both macerators' input slots stay empty: no recipe consumption masking the split.

		for (int i = 0; i < 120; i++) {
			var box = be(helper, SPLIT_BOX);
			if (box != null) {
				tickAny(helper, box);
			}
			var cable = be(helper, SPLIT_CABLE);
			if (cable != null) {
				tickAny(helper, cable);
			}
			NetworkManager.tickAll(helper.getLevel());
			var a = be(helper, SPLIT_MAC_A);
			if (a != null) {
				tickAny(helper, a);
			}
			var b = be(helper, SPLIT_MAC_B);
			if (b != null) {
				tickAny(helper, b);
			}
		}
		long macA = be(helper, SPLIT_MAC_A) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		long macB = be(helper, SPLIT_MAC_B) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (macA <= 0 || macB <= 0) {
			helper.fail("split did not reach both consumers: a=" + macA + " b=" + macB);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-NET02 — a full battery_box's insert(100, EXECUTE) through the TR API
	 *     returns 0; a full buffer accepts nothing more.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Net02_fullInsertReturnsZero(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = bat.getEnergyStorage().getCapacity();
		long inserted;
		try (Transaction tx = Transaction.openOuter()) {
			inserted = bat.getEnergyStorage().insert(100L, tx);
			tx.commit();
		}
		if (inserted != 0) {
			helper.fail("full battery_box accepted " + inserted + " EU, expected 0");
		}
		if (bat.getEnergyStorage().getAmount() != bat.getEnergyStorage().getCapacity()) {
			helper.fail("full battery_box amount changed: " + bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-NET03 — an empty battery_box's extract(100, EXECUTE) through the TR
	 *     API returns 0; an empty buffer emits nothing.
	 * @covers R-NRG-02
	 */
	@GameTest
	public void tcBatteryBox001Net03_emptyExtractReturnsZero(GameTestHelper helper) {
		BatteryBoxBlockEntity bat = place(helper);
		bat.getEnergyStorage().amount = 0;
		long extracted;
		try (Transaction tx = Transaction.openOuter()) {
			extracted = bat.getEnergyStorage().extract(100L, tx);
			tx.commit();
		}
		if (extracted != 0) {
			helper.fail("empty battery_box emitted " + extracted + " EU, expected 0");
		}
		if (bat.getEnergyStorage().getAmount() != 0) {
			helper.fail("empty battery_box amount changed: " + bat.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}
}
