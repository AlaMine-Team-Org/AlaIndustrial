package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.command.demo.DemoStand;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * MOD-058 smoke test: builds the {@code /ala demo} stand with the very same
 * {@link DemoStand#buildAll} the command uses, then asserts the two guarantees that keep the
 * stand from rotting:
 * <ol>
 *   <li><b>Coverage</b> — every block registered in the {@code alaindustrial} namespace appears
 *       somewhere on the stand. A new block that is not added to a zone turns this red.</li>
 *   <li><b>Liveness</b> — after 100 world ticks the fuelled generators have delivered EU into
 *       their battery boxes and the pre-charged macerator is processing (progress or consumed
 *       energy). A stand of dead props would pass a pure block-scan; this catches it.</li>
 * </ol>
 *
 * <p>Runs in a custom 44×14×28 empty structure ({@code demo_stand_area.snbt}) because the stand
 * does not fit the default 8×8×8 envelope. Sky access keeps the solar panels honest, though their
 * output is deliberately not asserted (test-world time of day is not fixed here).
 */
public class DemoStandGameTest {

	/** Stand origin inside the structure: 1-block margin on every axis. */
	private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 300, skyAccess = true)
	public void demoStandBuildsCoversAndRuns(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		DemoStand.buildAll(helper.getLevel(), origin);

		// --- coverage: every registered mod block is somewhere in the stand envelope ---
		Set<Identifier> missing = new HashSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (Industrialization.MOD_ID.equals(id.getNamespace())) {
				missing.add(id);
			}
		}
		for (int x = 0; x < DemoStand.WIDTH; x++) {
			for (int z = 0; z < DemoStand.DEPTH; z++) {
				for (int y = -1; y <= DemoStand.HEIGHT; y++) {
					missing.remove(BuiltInRegistries.BLOCK.getKey(
							helper.getLevel().getBlockState(origin.offset(x, y, z)).getBlock()));
				}
			}
		}
		if (!missing.isEmpty()) {
			helper.fail("demo stand does not showcase every mod block; missing: " + missing
					+ " — add them to a DemoStand zone");
		}

		// --- liveness after 100 ticks of normal world ticking ---
		helper.runAfterDelay(100, () -> {
			BatteryBoxBlockEntity coalBattery = helper.getLevel()
					.getBlockEntity(origin.offset(2, 1, 5)) instanceof BatteryBoxBlockEntity b ? b : null;
			if (coalBattery == null || coalBattery.getEnergyStorage().getAmount() <= 0) {
				helper.fail("fuel generator delivered no EU to its battery box after 100 ticks");
			}
			BatteryBoxBlockEntity millBattery = helper.getLevel()
					.getBlockEntity(origin.offset(17, 0, 5)) instanceof BatteryBoxBlockEntity b ? b : null;
			if (millBattery == null || millBattery.getEnergyStorage().getAmount() <= 0) {
				helper.fail("water mill delivered no EU to its battery box after 100 ticks");
			}
			MaceratorBlockEntity macerator = helper.getLevel()
					.getBlockEntity(origin.offset(2, 1, 10)) instanceof MaceratorBlockEntity m ? m : null;
			if (macerator == null) {
				helper.fail("macerator block entity missing on the stand");
			} else {
				boolean working = macerator.getDataAccess().get(2) > 0 // progress
						|| macerator.getEnergyStorage().getAmount() < macerator.getEnergyStorage().getCapacity()
						|| !macerator.getItem(MaceratorBlockEntity.OUTPUT_SLOT).isEmpty();
				if (!working) {
					helper.fail("pre-charged macerator with input shows no processing after 100 ticks");
				}
			}
			helper.succeed();
		});
	}

	/** {@code clear} removes every stand block above the restored floor — build → clear → scan. */
	@GameTest(structure = "alaindustrial:demo_stand_area", maxTicks = 100, skyAccess = true)
	public void demoStandClearLeavesNoBlocks(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		DemoStand.buildAll(helper.getLevel(), origin);
		DemoStand.clear(helper.getLevel(), origin);
		for (int x = 0; x < DemoStand.WIDTH; x++) {
			for (int z = 0; z < DemoStand.DEPTH; z++) {
				for (int y = 1; y <= DemoStand.HEIGHT; y++) {
					if (!helper.getLevel().getBlockState(origin.offset(x, y, z)).isAir()) {
						helper.fail("clear left a block at local (" + x + ", " + y + ", " + z + ")");
					}
				}
			}
		}
		helper.succeed();
	}
}
