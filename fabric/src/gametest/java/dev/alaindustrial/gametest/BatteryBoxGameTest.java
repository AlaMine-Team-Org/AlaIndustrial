package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import team.reborn.energy.api.EnergyStorage;

/**
 * L2 functional suite for the BatteryBox (LV energy storage). Unlike machines/generators it has no
 * inventory and no production — it accepts, holds, and emits EU. Migrated from legacy persistence
 * checks; the buffer node behaviour the network relies on.
 *
 * <p>MOD-323 batch D: the loader-neutral bodies live in {@link StorageEnergyScenarios}; the wrappers
 * below keep the traceability tags and delegate. What stays HERE is exactly the Fabric capability
 * seam: the {@code EnergyStorage.SIDED} view checks (PRF03/PRF04/NEG03) — the sided lookup itself is
 * the loader-specific machinery under test, so it has no loader-neutral twin.
 */
public class BatteryBoxGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static BatteryBoxBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, POS, ModBlocks.BATTERY_BOX, BatteryBoxBlockEntity.class);
	}

	/**
	 * @implements TC-BATTERYBOX-001-FUN01 — storage both accepts (insert) and emits (extract) EU, and
	 *     stores up to its configured capacity.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Fun01_acceptsAndEmits(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxAcceptsAndEmits(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-PER01 — stored EU survives an NBT save/load round-trip (the storage
	 *     drop carries its charge — R-BRK-07's prerequisite).
	 * @covers R-PER-01
	 */
	@GameTest
	public void tcBatteryBox001Per01_chargeSurvivesNbt(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxChargeSurvivesNbt(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-BRK07 — stored EU rides the dropped item via the STORED_ENERGY
	 *     component (what the loot table copies off the broken block entity) and is restored on place.
	 * @covers R-BRK-07
	 */
	@GameTest
	public void tcBatteryBox001Brk07_energyCarriedByComponent(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxDropCarriesEnergyHalfCharge(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-BRK07b — a machine (not storage) does NOT carry EU on its drop
	 *     (R-BRK-07 second half: machines lose their buffer on break).
	 */
	@GameTest
	public void tcBatteryBox001Brk07b_machineDropsNoEnergy(GameTestHelper helper) {
		StorageEnergyScenarios.machineDropsNoEnergy(helper);
	}

	// ── PRF — buffer cap and per-tick rate (BVA), through the real Team Reborn EnergyStorage API ────

	/**
	 * @implements TC-BATTERYBOX-001-PRF01 — insert(100_000, EXECUTE) through the TR API caps at
	 *     getCapacity() (20 000 EU from Config), not at whatever amount= would have allowed.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Prf01_insertCapsAtCapacity(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxInsertCapsAtCapacity(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF02 — extract(1_000, EXECUTE) from an empty buffer returns 0 and
	 *     getAmount() stays 0 (does not go negative).
	 * @covers R-NRG-02
	 */
	@GameTest
	public void tcBatteryBox001Prf02_extractFromEmptyReturnsZero(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxExtractFromEmptyReturnsZero(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF03 — the SIDED view on the input face (FACING) offers EXACTLY the
	 *     LV rate (32 EU/t = EnergyTier.LV.maxVoltage()) per SIMULATE call on an empty buffer: the port
	 *     publishes {@code maxInsert = LV.maxVoltage()} and there is ample room, so a healthy port must
	 *     move {@code min(maxInsert, room) = 32} EU. An upper bound alone ({@code offered <= 32}) would
	 *     silently pass a broken {@code maxInsert == 0} port that moves 0; the exact equality catches
	 *     both a missing cap (regression to unlimited insert) and a dead port.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Prf03_inputRateCappedAtLv(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX); // default FACING = NORTH (input face)
		BatteryBoxBlockEntity bat = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		bat.getEnergyStorage().setAmountUntracked(0);
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
		long lvCap = EnergyTier.LV.maxVoltage();
		if (offered != lvCap) {
			helper.fail("battery_box input face offered " + offered + " EU, expected exactly " + lvCap
					+ " (maxInsert == 0 would offer 0; unlimited insert would offer 1000/room — both are bugs)");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF04 — the SIDED view on the output face (opposite FACING) offers
	 *     EXACTLY the LV rate (32 EU/t) per SIMULATE call on a full buffer. See PRF03 for why an upper
	 *     bound alone is insufficient: a broken {@code maxExtract == 0} port would offer 0 and pass.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Prf04_outputRateCappedAtLv(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.BATTERY_BOX); // default FACING = NORTH, output = SOUTH
		BatteryBoxBlockEntity bat = helper.getBlockEntity(POS, BatteryBoxBlockEntity.class);
		bat.getEnergyStorage().setAmountUntracked(bat.getEnergyStorage().getCapacity());
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
		long lvCap = EnergyTier.LV.maxVoltage();
		if (offered != lvCap) {
			helper.fail("battery_box output face offered " + offered + " EU, expected exactly " + lvCap
					+ " (maxExtract == 0 would offer 0; unlimited extract would offer 1000/stored — both are bugs)");
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
		StorageEnergyScenarios.batteryBoxNoSelfDrainOver1000Ticks(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-NEG02 — an empty battery_box left alone (no neighbours, no source)
	 *     does not gain EU out of nowhere over 1000 server ticks.
	 */
	@GameTest
	public void tcBatteryBox001Neg02_noSelfChargeOver1000Ticks(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxNoSelfChargeOver1000Ticks(helper);
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
		bat.getEnergyStorage().setAmountUntracked(10_000L);
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

	/**
	 * @implements TC-BATTERYBOX-001-CON01 — a ring/cyclic cable topology (generator, three cables forming
	 *     a closed loop back to the generator, one of the loop cables touching the battery_box) charges
	 *     the battery_box, and driving it for a bounded number of ticks completes without hanging (no
	 *     infinite loop / stack overflow on cycle discovery).
	 * @covers R-CON-05
	 */
	@GameTest
	public void tcBatteryBox001Con01_ringTopologyNoHang(GameTestHelper helper) {
		StorageEnergyScenarios.ringTopologyNoHang(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-CON02 — removing the only cable stops delivery into the
	 *     battery_box; replacing it resumes flow without player intervention beyond the block placement.
	 * @covers R-CON-04, R-CON-09
	 */
	@GameTest
	public void tcBatteryBox001Con02_breakRejoinCable(GameTestHelper helper) {
		StorageEnergyScenarios.breakRejoinCable(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-CON04 — five generators (combined well above the 32 EU/t LV rate)
	 *     feed one cable into the battery_box's single input face; the face-level rate cap holds: charge
	 *     never grows by more than the LV rate in a single tick, even though supply exceeds it.
	 * @covers R-NRG-04
	 */
	@GameTest
	public void tcBatteryBox001Con04_faceThroughputCappedUnderExcessSupply(GameTestHelper helper) {
		StorageEnergyScenarios.faceThroughputCappedUnderExcessSupply(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-CON05 — a charged battery_box's output feeds one cable that branches
	 *     to two macerators; both receive a share of the flow (no "first consumer takes all").
	 * @covers R-NRG-08, R-CON-01
	 */
	@GameTest
	public void tcBatteryBox001Con05_splitsToTwoConsumers(GameTestHelper helper) {
		StorageEnergyScenarios.splitsToTwoConsumers(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-NET02 — a full battery_box's insert(100, EXECUTE) through the TR API
	 *     returns 0; a full buffer accepts nothing more.
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcBatteryBox001Net02_fullInsertReturnsZero(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxFullInsertReturnsZero(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-NET03 — an empty battery_box's extract(100, EXECUTE) through the TR
	 *     API returns 0; an empty buffer emits nothing.
	 * @covers R-NRG-02
	 */
	@GameTest
	public void tcBatteryBox001Net03_emptyExtractReturnsZero(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxEmptyExtractReturnsZero(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-RECIPE01 — the shaped crafting recipe resolves and yields a
	 *     battery_box (MOD-152: pattern is {@code PBP/CRC/PBP}, with two {@code alaindustrial:battery}
	 *     items in the middle of the top and bottom rows). Guards against a silently skipped recipe.
	 */
	@GameTest
	public void tcBatteryBox001Recipe01_craftingRecipeResolves(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxCraftingRecipeResolves(helper);
	}

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

	/**
	 * @implements TC-BATTERYBOX-001-BRK07 — the STORED_ENERGY component carries a 12345 EU charge on
	 * collectComponents() (the loader's data-component registration seam; the half-charge leg is
	 * {@link #tcBatteryBox001Brk07_energyCarriedByComponent}). Body: {@link StorageEnergyScenarios#batteryBoxDropCarriesEnergy}.
	 */
	@GameTest
	public void tcBatteryBox001Brk07b_energyCarriedByComponentAt12345(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxDropCarriesEnergy(helper);
	}

	/**
	 * @implements TC-BATTERYBOX-001-PRF03 — loader-neutral twin of {@link #tcBatteryBox001Prf03_inputRateCappedAtLv}:
	 * the shared {@code EnergyBuffer} publishes maxInsert == maxExtract == LV.maxVoltage() exactly (the SIDED-view
	 * check above exercises the same invariant through the Fabric capability). Body: {@link StorageEnergyScenarios#batteryBoxRateExactLv}.
	 */
	@GameTest
	public void tcBatteryBox001Prf03b_bufferCapsExactLv(GameTestHelper helper) {
		StorageEnergyScenarios.batteryBoxRateExactLv(helper);
	}
}
