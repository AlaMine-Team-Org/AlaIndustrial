package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import dev.alaindustrial.Config;
import team.reborn.energy.api.EnergyStorage;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.fabric.FabricEnergyPort;

/**
 * Fabric lane for the processing machines (macerator, electric furnace, compressor, extractor,
 * sawmill). Migrated from legacy {@code IndustrializationSelfTest.PROCESSING_RECIPES}.
 *
 * <p>The loader-neutral bodies live in {@link MachineScenarios} and run on both lanes from its roster
 * (MOD-717, ADR-038; the Fabric ids of the former wrappers are kept there). Only the eight
 * cases that look at a machine THROUGH the Fabric Transfer/Energy API stay as real bodies in this
 * file — {@code *Prf02_*BufferCapsViaInsert} (transaction-committed {@code insert}) and
 * {@code *Con04_*AllFacesAcceptEnergy} ({@code EnergyStorage.SIDED}) — because they test this
 * loader's seam, not the machine.
 *
 * <p>Numbers/recipes come from datapack + {@link dev.alaindustrial.Config}; outputs from the recipe.
 */
public class MachineGameTest {

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static MachineBlockEntity place(GameTestHelper helper, Block block) {
		return AlaGameTestHelper.place(helper, POS, block);
	}

	// ── PRF: buffer cap 800 EU via the real Team Reborn insert() path (parametric) ─────────────────

	/**
	 * BVA: inserting far more than the buffer's capacity through the real TR Energy API {@code insert}
	 * (transaction-committed, not the direct {@code amount=} field write used elsewhere in this suite)
	 * must cap at the configured buffer (800 EU / {@code Config.machineBuffer}), never exceeding it.
	 */
	private void assertBufferCapsViaInsert(GameTestHelper helper, Block block, int expectedBuffer) {
		MachineBlockEntity be = place(helper, block);
		EnergyPort storage = be.getEnergyStorage();
		// A single insert() is rate-capped at maxInsert (32 EU/t LV), separate from capacity. Insert
		// repeatedly until the buffer saturates, then verify it caps at the configured buffer (never over).
		for (int i = 0; i < 100; i++) {
			long moved;
			try (Transaction tx = Transaction.openOuter()) {
				moved = storage.insert(8000, FabricEnergyPort.wrap(tx));
				tx.commit();
			}
			if (moved == 0) {
				break;
			}
		}
		if (be.getEnergyStorage().getAmount() != expectedBuffer) {
			helper.fail(block + ": buffer cap via TR insert() expected " + expectedBuffer
					+ " but got " + be.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MACH-001-PRF02 — macerator: insert(8000) via TR API caps at its own buffer
	 *     ({@code Config.maceratorBuffer}, distinct constant from the shared {@code machineBuffer} used
	 *     by the other three machines — both default to 800 EU per PERFORMANCE.md).
	 * @covers R-NRG-01
	 */
	@GameTest
	public void tcMach001Prf02_maceratorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.MACERATOR, Config.maceratorBuffer);
	}

	/** @implements TC-MACH-002-PRF02 — electric furnace: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach002Prf02_furnaceBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.ELECTRIC_FURNACE, Config.machineBuffer);
	}

	/** @implements TC-MACH-003-PRF02 — compressor: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach003Prf02_compressorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.COMPRESSOR, Config.machineBuffer);
	}

	/** @implements TC-MACH-004-PRF02 — extractor: insert(8000) via TR API caps at buffer=800 EU. @covers R-NRG-01 */
	@GameTest
	public void tcMach004Prf02_extractorBufferCapsViaInsert(GameTestHelper helper) {
		assertBufferCapsViaInsert(helper, ModBlocks.EXTRACTOR, Config.machineBuffer);
	}

	// ── CON: pairwise 5 non-FACING faces + FACING face (parametric) ────────────────────────────────

	/**
	 * @implements TC-MACH-001-CON04 — energy face roles across all 6 world faces, default placement
	 *     (FACING=NORTH): the 5 non-FACING faces are IN-only; FACING itself is energy-inert (no port),
	 *     per the human decision D-FACING (R-NRG-03). Matches
	 *     {@code EnergyFaceGameTest#rNrg03_maceratorEveryFaceInOnly}.
	 * @covers R-CON-01, R-NRG-03
	 */
	private void assertAllSixFacesAcceptEnergy(GameTestHelper helper, Block block) {
		helper.setBlock(POS, block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		for (Direction d : Direction.values()) {
			EnergyStorage port = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), d);
			if (d == Direction.NORTH) {
				if (port != null) {
					helper.fail(block + ": FACING face (north) must be inert (no energy port)");
				}
				continue;
			}
			if (port == null || !port.supportsInsertion() || port.supportsExtraction()) {
				helper.fail(block + ": face " + d + " must be IN-only");
			}
		}
		helper.succeed();
	}

	/** @implements TC-MACH-001-CON04 — macerator: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach001Con04_maceratorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.MACERATOR);
	}

	/** @implements TC-MACH-002-CON04 — electric furnace: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach002Con04_furnaceAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.ELECTRIC_FURNACE);
	}

	/** @implements TC-MACH-003-CON04 — compressor: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach003Con04_compressorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.COMPRESSOR);
	}

	/** @implements TC-MACH-004-CON04 — extractor: 5 non-FACING faces IN-only, FACING inert. @covers R-CON-01, R-NRG-03 */
	@GameTest
	public void tcMach004Con04_extractorAllFacesAcceptEnergy(GameTestHelper helper) {
		assertAllSixFacesAcceptEnergy(helper, ModBlocks.EXTRACTOR);
	}

}
