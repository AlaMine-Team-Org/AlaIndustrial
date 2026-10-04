package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;

/**
 * The processing-machine scenarios' rig (MOD-717, TST-2): the cell a machine is placed in, the EU and tick
 * budget that outlast any operation, and the machine blocks — one place for every class split out of
 * {@link MachineScenarios}.
 */
final class MachineRig {

	private MachineRig() {
	}

	static final BlockPos POS = new BlockPos(1, 2, 1);
	static final int AMPLE_EU = 8000;      // > any single op's E_op; set directly (bypasses cap)
	static final int DRIVE_TICKS = 400;    // > longest machine duration (150) + margin

	static MachineBlockEntity place(GameTestHelper helper, Block block) {
		return AlaGameTestHelper.place(helper, POS, block);
	}

	static Block macerator() {
		return ModContent.MACERATOR.get();
	}

	static Block furnace() {
		return ModContent.ELECTRIC_FURNACE.get();
	}

	static Block compressor() {
		return ModContent.COMPRESSOR.get();
	}

	static Block extractor() {
		return ModContent.EXTRACTOR.get();
	}

	static Block sawmill() {
		return ModContent.SAWMILL.get();
	}


	static AbstractProcessingMachineBlockEntity processing(GameTestHelper helper, Block block) {
		return (AbstractProcessingMachineBlockEntity) place(helper, block);
	}
}
