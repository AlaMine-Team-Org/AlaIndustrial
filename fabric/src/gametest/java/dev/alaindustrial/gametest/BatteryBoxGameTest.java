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

	// ── PRF — buffer cap and per-tick rate (BVA), through the real Team Reborn EnergyStorage API ────

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

	// ── MOD-445: loader-neutral bodies the NeoForge lane already ran; wired here so both lanes run the same set ──

}
