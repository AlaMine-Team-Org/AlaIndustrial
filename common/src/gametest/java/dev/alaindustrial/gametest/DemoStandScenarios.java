package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.ElectricFurnaceBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.Config;
import dev.alaindustrial.core.structure.CrystalFarmRoom;
import dev.alaindustrial.core.structure.RoomFill;
import dev.alaindustrial.core.structure.RoomScan;
import dev.alaindustrial.core.structure.RoomValidator;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.command.demo.DemoStand;
import dev.alaindustrial.storage.StorageCluster;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * MOD-058/MOD-294 stand scenarios: build the {@code /ala demo} stand with the very same
 * {@link DemoStand#buildAll} the command uses, then assert the guarantees that keep the stand
 * from rotting. Loader-neutral — the fabric wrapper and the NeoForge {@code registerTest} line
 * both delegate here, so both lanes run the identical checks.
 *
 * <ol>
 *   <li><b>Block coverage</b> — every block registered in the {@code alaindustrial} namespace
 *       appears somewhere on the stand. A new block that is not added to a zone turns this red.</li>
 *   <li><b>Liveness</b> — after 100 world ticks the fuelled generators have delivered EU into
 *       their battery boxes and the pre-charged macerator is processing (progress or consumed
 *       energy). A stand of dead props would pass a pure block-scan; this catches it.</li>
 *   <li><b>Polygon (MOD-294)</b> — the 36-cable loss lane actually delivers EU to its far
 *       furnace and the LV-cycle farm's macerator works. Scenery that quietly died is the
 *       failure mode these two catch.</li>
 *   <li><b>Item showcase (MOD-294)</b> — every item of the registry hangs in a glow
 *       frame on the showcase wall. The wall refills from the live registry, so this reddens the
 *       day the registry outgrows the wall.</li>
 *   <li><b>Idempotency (MOD-294)</b> — build→build leaves the block multiset unchanged, no item
 *       drops and no duplicate frames: the entity sweep of {@code killLooseEntities} is what the
 *       rebuild×2 criterion hangs on.</li>
 * </ol>
 *
 * <p>Each scenario anchors the stand inside its own structure envelope with a 1-block margin
 * ({@link #ORIGIN}); the envelope is 44×14×28 ({@code demo_stand_area}), sky access keeps the
 * solar panels honest, though their output is deliberately not asserted (test-world time of day
 * is not fixed here).
 */
public final class DemoStandScenarios {
	private DemoStandScenarios() {
	}

	/**
	 * Stand origin inside the structure envelope: a 1-block margin horizontally, and
	 * {@code DemoStand.DEPTH_BELOW + 1} vertically.
	 *
	 * <p>The vertical margin used to be 1 as well, and that was wrong in a way no test could see: the
	 * stand digs two levels below its floor (the water-mill channel, the basin pans), so with the
	 * origin at y=1 those writes landed at structure y=-1 — OUTSIDE the rig, where nothing scans and
	 * nothing is reset between tests (MOD-597).
	 */
	private static final BlockPos ORIGIN = new BlockPos(1, DemoStand.DEPTH_BELOW + 1, 1);

	public static void demoStandBuildsCoversAndRuns(GameTestHelper helper) {
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

		// --- MOD-597: the crystal greenhouse the stand builds actually SEALS ---
		// Blind in exactly the way the reactor room was until MOD-470, and it cost a real defect: the
		// shielding chest and the irradiated-soil strip were placed at x=8 back when x=8 was empty, and
		// kept being placed there after the greenhouse claimed x 8..12 — two holes punched straight
		// through a shell whose entire point is that it closes. Every block of it is also somewhere else
		// on the stand, so the coverage scan below never noticed.
		BlockPos farmController = origin.offset(9, 2, 15);
		BlockState farmState = helper.getLevel().getBlockState(farmController);
		if (!farmState.is(ModContent.CRYSTAL_FARM_CONTROLLER.get())) {
			helper.fail("the demo stand's greenhouse controller is missing from its north wall");
		} else {
			RoomFill.Result greenhouse = CrystalFarmRoom.scan(helper.getLevel(), farmController,
					farmState.getValue(HorizontalDirectionalBlock.FACING),
					Math.max(1, Config.crystalFarmRoomMinCells),
					Math.max(1, Config.crystalFarmRoomMaxCells),
					Math.max(1, Config.crystalFarmRoomMaxSpan));
			if (!greenhouse.sealed()) {
				helper.fail("the demo stand's crystal greenhouse does not seal: " + greenhouse.status()
						+ " at " + greenhouse.x() + "," + greenhouse.y() + "," + greenhouse.z());
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
				for (int y = -DemoStand.DEPTH_BELOW; y <= DemoStand.HEIGHT; y++) {
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

	/** {@code clear} removes every stand block and entity above the restored floor — build → clear → scan. */
	public static void demoStandClearLeavesNoBlocks(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		Map<BlockPos, Block> ringBefore = ringSnapshot(helper, origin);
		DemoStand.buildAll(helper.getLevel(), origin);
		DemoStand.clear(helper.getLevel(), origin);
		for (int x = 0; x < DemoStand.WIDTH; x++) {
			for (int z = 0; z < DemoStand.DEPTH; z++) {
				for (int y = 1; y <= DemoStand.HEIGHT; y++) {
					if (!helper.getLevel().getBlockState(origin.offset(x, y, z)).isAir()) {
						helper.fail("clear left a block at local (" + x + ", " + y + ", " + z + ")");
					}
				}
				// MOD-597: the stand digs below its floor, so "cleared" has to mean filled back in.
				// A clear that only lays grass over the water-mill channel leaves a two-deep trap
				// under the lawn — and the scan above, which starts at y=1, could never see it.
				for (int y = -DemoStand.DEPTH_BELOW; y < 0; y++) {
					if (!helper.getLevel().getBlockState(origin.offset(x, y, z)).is(DemoStand.SUBSOIL)) {
						helper.fail("clear left the stand's own excavation at local (" + x + ", " + y
								+ ", " + z + "): expected " + BuiltInRegistries.BLOCK.getKey(DemoStand.SUBSOIL)
								+ ", found " + BuiltInRegistries.BLOCK.getKey(
										helper.getLevel().getBlockState(origin.offset(x, y, z)).getBlock()));
					}
				}
			}
		}
		// The showcase frames are entities — a clear that leaves them hanging in the air over the
		// restored grass would pop a moment later and shower the floor with frames and items.
		if (!helper.getLevel().getEntitiesOfClass(GlowItemFrame.class, envelope(origin)).isEmpty()) {
			helper.fail("clear left showcase item frames alive above the cleared stand");
		}
		if (!helper.getLevel().getEntitiesOfClass(ItemEntity.class, envelope(origin)).isEmpty()) {
			helper.fail("clear left item drops inside the stand envelope");
		}
		assertRingUntouched(helper, origin, ringBefore);
		helper.succeed();
	}

	/**
	 * Every cell of the one-block ring around the stand, as it stood before the build (MOD-586).
	 *
	 * <p>The scan above walks {@code x < WIDTH, z < DEPTH} because that is the volume the stand
	 * declares — and so does {@code clearAbove}, which is precisely the hole: a {@code set} one column
	 * past {@code WIDTH} is written by {@code buildAll}, is never cleared, and is invisible to BOTH
	 * oracles at once, because the test and the code agreed on the same wrong bound. It happened —
	 * MOD-584 put a root column and a smooth-stone pad at x=42 — and it shipped with every gate green.
	 *
	 * <p>Compared as a before/after snapshot rather than by namespace: the pad the incident left behind
	 * was VANILLA smooth stone, so "did the stand leave one of OUR blocks here" would have missed half
	 * of the very defect this exists for.
	 */
	private static Map<BlockPos, Block> ringSnapshot(GameTestHelper helper, BlockPos origin) {
		Map<BlockPos, Block> snapshot = new HashMap<>();
		for (BlockPos local : ringCells()) {
			snapshot.put(local, helper.getLevel().getBlockState(origin.offset(
					local.getX(), local.getY(), local.getZ())).getBlock());
		}
		return snapshot;
	}

	private static void assertRingUntouched(GameTestHelper helper, BlockPos origin,
			Map<BlockPos, Block> before) {
		for (Map.Entry<BlockPos, Block> cell : before.entrySet()) {
			BlockPos local = cell.getKey();
			Block now = helper.getLevel().getBlockState(origin.offset(
					local.getX(), local.getY(), local.getZ())).getBlock();
			if (now != cell.getValue()) {
				helper.fail("build+clear changed local (" + local.getX() + ", " + local.getY() + ", "
						+ local.getZ() + "), which is OUTSIDE the stand's own "
						+ DemoStand.WIDTH + "x" + DemoStand.HEIGHT + "x" + DemoStand.DEPTH
						+ " volume: " + BuiltInRegistries.BLOCK.getKey(cell.getValue()) + " -> "
						+ BuiltInRegistries.BLOCK.getKey(now) + ". Nothing clears that cell, so it "
						+ "outlives `demo clear`");
			}
		}
	}

	/**
	 * The ring's local coordinates. The rig is 44x14x28 with {@link #ORIGIN} at (1,1,1), so local
	 * x=-1..42 and z=-1..26 exist while the stand occupies x=0..41 and z=0..26 — a testable margin on
	 * three sides. The floor row (y=0) is included: {@code clear} restores grass only for x &lt; WIDTH,
	 * so a floor block set one column out survives exactly like the column above it — and since
	 * MOD-597 so are the {@code DEPTH_BELOW} dug levels, for exactly the same reason one level down.
	 */
	private static List<BlockPos> ringCells() {
		List<BlockPos> cells = new ArrayList<>();
		for (int y = -DemoStand.DEPTH_BELOW; y <= DemoStand.HEIGHT; y++) {
			for (int z = -1; z < DemoStand.DEPTH; z++) {
				cells.add(new BlockPos(-1, y, z));
				cells.add(new BlockPos(DemoStand.WIDTH, y, z));
			}
			for (int x = -1; x <= DemoStand.WIDTH; x++) {
				cells.add(new BlockPos(x, y, -1));
			}
		}
		return cells;
	}

	/**
	 * MOD-294 rebuild×2: the second {@code buildAll} must land on the identical block state — no
	 * duplicates, no leftovers, no item drops, and the showcase frames exactly once per item.
	 */
	public static void demoStandRebuildIsIdempotent(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		DemoStand.buildAll(helper.getLevel(), origin);
		Map<Integer, Integer> firstPass = blockMultiset(helper, origin);
		DemoStand.buildAll(helper.getLevel(), origin);

		Map<Integer, Integer> secondPass = blockMultiset(helper, origin);
		if (!firstPass.equals(secondPass)) {
			helper.fail("rebuild×2 changed the stand's block multiset — rebuild is not idempotent");
		}
		// Drops are the quiet half of the criterion: machine inventories spill when their blocks are
		// cleared, and popped frames drop themselves plus their item — all of it must be swept.
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, envelope(origin));
		if (!drops.isEmpty()) {
			String found = drops.stream()
					.map(d -> BuiltInRegistries.ITEM.getKey(d.getItem().getItem()).toString())
					.sorted()
					.toList()
					.toString();
			helper.fail("rebuild×2 left item drops inside the stand envelope: " + found);
		}
		int expected = DemoStand.showcaseItems().size();
		int frames = helper.getLevel().getEntitiesOfClass(GlowItemFrame.class, envelope(origin)).size();
		if (frames != expected) {
			helper.fail("rebuild×2 left " + frames + " showcase frames for " + expected
					+ " items — frames must be discarded and placed exactly once per rebuild");
		}
		helper.succeed();
	}

	/**
	 * MOD-294 item showcase: every item of the registry hangs in a glow frame on the wall — the
	 * item-side twin of the block coverage scan above. Since MOD-586 that includes block items, so
	 * this is also what keeps the wall's capacity honest: it is the check that reddens when the
	 * registry outgrows {@code SHOWCASE_COLUMNS × SHOWCASE_ROWS}.
	 */
	public static void demoStandShowcaseCoversItems(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		DemoStand.buildAll(helper.getLevel(), origin);

		Set<Identifier> shown = new HashSet<>();
		for (GlowItemFrame frame : helper.getLevel().getEntitiesOfClass(GlowItemFrame.class, envelope(origin))) {
			ItemStack stack = frame.getItem();
			if (!stack.isEmpty()) {
				shown.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			}
		}
		Set<Identifier> missing = new HashSet<>();
		for (Item item : DemoStand.showcaseItems()) {
			missing.add(BuiltInRegistries.ITEM.getKey(item));
		}
		missing.removeAll(shown);
		if (!missing.isEmpty()) {
			helper.fail("showcase wall does not display every mod item; missing: " + missing
					+ " — the wall is full or the frame placement broke");
		}
		helper.succeed();
	}

	/**
	 * MOD-597: the zone pass writes every cell it writes exactly once, and every stocking call finds
	 * something to stock.
	 *
	 * <p>The bug this exists for is invisible to every other check on this page. Two zones write one
	 * cell, the second wins, and the loser is almost always registered somewhere else on the stand —
	 * so the coverage scan stays green, the stocking call for the lost block falls through its
	 * {@code instanceof} without a word, and the stand quietly shows a machine that is not there. It
	 * happened in MOD-292, MOD-275, MOD-145, MOD-599 and MOD-597, and each time the fix was another
	 * comment in {@code DemoStand} asking the next author to check the neighbours by hand.
	 *
	 * <p>Deliberate layering is not reported: the floor and subsoil slabs are laid before the ledger
	 * opens, precisely so that carving a basin out of them is not a collision.
	 */
	public static void demoStandWritesEachCellOnce(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(ORIGIN);
		DemoStand.buildAll(helper.getLevel(), origin);
		List<String> problems = DemoStand.lastBuildProblems();
		if (!problems.isEmpty()) {
			helper.fail("the demo stand's zones stepped on each other " + problems.size()
					+ " time(s): " + String.join("; ", problems)
					+ " — give each block its own cell in DemoStand");
		}
		helper.succeed();
	}

	/** The stand envelope as an entity query box — 1 block of slack on every horizontal side. */
	private static AABB envelope(BlockPos origin) {
		return AABB.encapsulatingFullBlocks(origin.offset(-1, 0, -1),
				origin.offset(DemoStand.WIDTH + 1, DemoStand.HEIGHT + 2, DemoStand.DEPTH + 1));
	}

	private static Map<Integer, Integer> blockMultiset(GameTestHelper helper, BlockPos origin) {
		Map<Integer, Integer> counts = new HashMap<>();
		for (int x = 0; x < DemoStand.WIDTH; x++) {
			for (int z = 0; z < DemoStand.DEPTH; z++) {
				for (int y = -DemoStand.DEPTH_BELOW; y <= DemoStand.HEIGHT; y++) {
					BlockState state = helper.getLevel().getBlockState(origin.offset(x, y, z));
					counts.merge(Block.getId(state), 1, Integer::sum);
				}
			}
		}
		return counts;
	}
}
