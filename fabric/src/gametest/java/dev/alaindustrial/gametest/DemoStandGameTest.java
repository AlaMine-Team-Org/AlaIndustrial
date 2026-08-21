package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.command.demo.DemoStand;
import dev.alaindustrial.storage.StorageCluster;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.SignBlockEntity;

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
 *   <li><b>Polygon (MOD-294)</b> — the label signs carry readable text, the 36-cable loss lane
 *       actually delivers EU to its far furnace, and the LV-cycle farm's macerator works. Scenery
 *       that quietly died is the failure mode these three catch.</li>
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

		// --- the storage-module pair really is a pair (MOD-275 stage A) ---
		// Two `set` calls landed on the same cell, so the stand showed ONE module where the comment
		// promised two merged into one warehouse. The block-coverage scan below cannot see that — the
		// surviving module still ticks the "storage_module appears somewhere" box. Walking the cluster
		// does: a single module walks to moduleCount() == 1.
		if (StorageCluster.of(helper.getLevel(), origin.offset(32, 3, 10)).moduleCount() != 2) {
			helper.fail("the demo stand's two storage modules do not form one 2-module warehouse "
					+ "— a second `set` on the same cell overwrote one of them");
		}

		// --- MOD-470: the reactor room the stand builds actually FORMS ---
		// The block-coverage scan below is blind to this: every block of the room is also in the loose
		// sample row, so the room could stop sealing — a wall cell overwritten, the controller facing the
		// wrong way, the doorway lost — and the stand would still tick every box while showing a reactor
		// that does nothing.
		//
		// Asked of the geometry directly rather than of the controller's own status: the controller scans
		// on ITS schedule, so on the tick the stand finishes building, its status is still the "never
		// scanned" default — a first draft of this check read that default and reported a perfectly good
		// room as broken.
		BlockPos controllerPos = origin.offset(4, 2, 15);
		BlockState controllerState = helper.getLevel().getBlockState(controllerPos);
		if (!controllerState.is(ModContent.REACTOR_CONTROLLER.get())) {
			helper.fail("the demo stand's reactor controller is missing from the room's north wall");
		} else {
			RoomScan.Result room = RoomValidator.scan(helper.getLevel(), controllerPos,
					controllerState.getValue(HorizontalDirectionalBlock.FACING),
					Config.reactorRoomMinInner, Config.reactorRoomMaxInner,
					Config.reactorRoomMaxGlassPercent);
			if (room.status() != RoomScan.Status.FORMED) {
				helper.fail("the demo stand's reactor room does not form: " + room.status()
						+ " at " + room.x() + "," + room.y() + "," + room.z());
			}
		}

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

		// --- MOD-294: the polygon's labels are real (waxed signs carrying text, not blank props) ---
		// A label zone that silently stopped placing signs — or placed them unwritable-empty — would
		// pass every block-coverage scan above, because oak signs are vanilla. Read the text back.
		if (!(helper.getLevel().getBlockEntity(origin.offset(2, 1, 8)) instanceof SignBlockEntity lossSign)
				|| lossSign.getFrontText().getMessage(0, false).getString().isEmpty()) {
			helper.fail("loss-lane label sign missing or empty at (2, 1, 8)");
		}
		if (!(helper.getLevel().getBlockEntity(origin.offset(12, 1, 0)) instanceof SignBlockEntity tierSign)
				|| tierSign.getFrontText().getMessage(0, false).getString().isEmpty()) {
			helper.fail("tier-zone label sign missing or empty at (12, 1, 0)");
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
			// Cable zone (MOD-103): the first cable run's end furnace is NOT pre-charged, so any EU in
			// its buffer — or any smelting progress/output — proves the charged battery box fed it
			// through the 6-cable network. With the box mis-oriented (its output face away from the
			// cables) the run is dead and this stays at zero, so the check fails on the pre-fix code.
			ElectricFurnaceBlockEntity cableFurnace = helper.getLevel()
					.getBlockEntity(origin.offset(23, 1, 14)) instanceof ElectricFurnaceBlockEntity f ? f : null;
			if (cableFurnace == null) {
				helper.fail("cable-zone end furnace missing on the stand");
			} else {
				boolean fed = cableFurnace.getEnergyStorage().getAmount() > 0
						|| cableFurnace.getDataAccess().get(2) > 0 // progress
						|| !cableFurnace.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT).isEmpty();
				if (!fed) {
					helper.fail("cable-zone battery box delivered no EU down its cable run after 100 ticks");
				}
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

			// MOD-294 loss lane: the 36-cable copper run is the demo's whole point — if EU never
			// arrives at the far furnace, the lane is a dead prop, not a loss exhibit.
			ElectricFurnaceBlockEntity laneFurnace = helper.getLevel()
					.getBlockEntity(origin.offset(39, 1, 7)) instanceof ElectricFurnaceBlockEntity lf ? lf : null;
			if (laneFurnace == null) {
				helper.fail("loss-lane end furnace missing on the stand");
			} else {
				boolean fed = laneFurnace.getEnergyStorage().getAmount() > 0
						|| laneFurnace.getDataAccess().get(2) > 0 // progress
						|| !laneFurnace.getItem(ElectricFurnaceBlockEntity.OUTPUT_SLOT).isEmpty();
				if (!fed) {
					helper.fail("loss-lane battery box delivered no EU across 36 copper cables after 100 ticks");
				}
			}

			// MOD-294 farm A: the LV-cycle chain's macerator works like its showcase sibling — the
			// farm is "ready to test", not scenery.
			MaceratorBlockEntity farmMacerator = helper.getLevel()
					.getBlockEntity(origin.offset(6, 1, 23)) instanceof MaceratorBlockEntity fm ? fm : null;
			if (farmMacerator == null) {
				helper.fail("LV-cycle farm macerator missing on the stand");
			} else {
				boolean working = farmMacerator.getDataAccess().get(2) > 0
						|| farmMacerator.getEnergyStorage().getAmount() < farmMacerator.getEnergyStorage().getCapacity()
						|| !farmMacerator.getItem(MaceratorBlockEntity.OUTPUT_SLOT).isEmpty();
				if (!working) {
					helper.fail("LV-cycle farm macerator shows no processing after 100 ticks");
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
