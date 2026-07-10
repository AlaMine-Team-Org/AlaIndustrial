package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.item.AnalyzerMode;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import dev.alaindustrial.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 gametest for the Network Analyzer's Traverse mode (MOD-047). Exercises {@link NetworkTraverser}
 * directly against a real in-world layout, so the bridging logic is covered without needing a player
 * or the S2C payload path.
 *
 * <p>Layout: {@code [Generator]─cable─[BatteryBox]─cable─[Macerator]} along +X. The BatteryBox is a
 * storage sink (endpoint, not a cable), so the two cable segments form two distinct
 * {@link EnergyNetwork} instances. Traverse mode must stitch them into one picture; STOP_AT_STORAGE
 * must return only the clicked side.
 */
public class NetworkAnalyzerGameTest {

	/** Relative positions inside the test batch (helper.absolutePos is applied at lookup time). */
	private static final BlockPos GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos BAT = new BlockPos(3, 2, 1);
	private static final BlockPos CABLE_B = new BlockPos(4, 2, 1);
	private static final BlockPos MAC = new BlockPos(5, 2, 1);

	private BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private void tick(GameTestHelper helper, BlockEntity be) {
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

	/**
	 * Place the bridge layout. The BatteryBox faces EAST so its IN side (charge) sees the generator's
	 * cable and its OUT side sees the machine's cable — matching how a player would orient it. Both
	 * cable segments register into {@link NetworkManager} via their {@code serverTick}.
	 */
	private void build(GameTestHelper helper) {
		helper.setBlock(GEN, ModBlocks.GENERATOR);
		helper.setBlock(CABLE_A, ModBlocks.COPPER_CABLE);
		helper.setBlock(BAT, ModBlocks.BATTERY_BOX.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(CABLE_B, ModBlocks.COPPER_CABLE);
		helper.setBlock(MAC, ModBlocks.MACERATOR);
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	/** Tick every block + the per-level NetworkManager for {@code n} synthetic ticks. */
	private void drive(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			tickEntity(helper, GEN);
			tickEntity(helper, CABLE_A);
			tickEntity(helper, BAT);
			tickEntity(helper, CABLE_B);
			tickEntity(helper, MAC);
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	private void tickEntity(GameTestHelper helper, BlockPos rel) {
		BlockEntity entity = be(helper, rel);
		if (entity != null) {
			tick(helper, entity);
		}
	}

	/**
	 * @implements MOD-047-TRAVERSE — Traverse mode stitches two networks split by a BatteryBox into one
	 * picture, including the BatteryBox as a storage sink. @covers MOD-047
	 */
	@GameTest
	public void mod047_traverseBridgesBatteryBox(GameTestHelper helper) {
		build(helper);
		drive(helper, 40);
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_A));
		EnergyNetwork right = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_B));
		if (left == null || right == null) {
			helper.fail("cable segments did not form networks (left=" + left + ", right=" + right + ")");
		}
		if (left == right) {
			helper.fail("precondition failed: both cables are in the same network — BatteryBox must split them");
		}
		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.TRAVERSE, 32);
		// Both cable segments must appear in the union.
		if (!result.cables().contains(helper.absolutePos(CABLE_A))
				|| !result.cables().contains(helper.absolutePos(CABLE_B))) {
			helper.fail("traverse did not bridge through the BatteryBox: cables=" + result.cables());
		}
		// The BatteryBox must be classified as a storage sink, not a plain producer/consumer.
		if (!result.storageSinks().contains(helper.absolutePos(BAT))) {
			helper.fail("BatteryBox not reported as a storage sink: storage=" + result.storageSinks());
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-047-STOP — STOP_AT_STORAGE returns only the clicked cable's own network and never
	 * bridges. @covers MOD-047
	 */
	@GameTest
	public void mod047_stopAtStorageStaysInSegment(GameTestHelper helper) {
		build(helper);
		drive(helper, 40);
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_A));
		if (left == null) {
			helper.fail("left cable did not form a network");
		}
		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.STOP_AT_STORAGE, 32);
		if (!result.cables().contains(helper.absolutePos(CABLE_A))) {
			helper.fail("stop-at-storage dropped the clicked cable");
		}
		if (result.cables().contains(helper.absolutePos(CABLE_B))) {
			helper.fail("stop-at-storage leaked the far cable through the BatteryBox: " + result.cables());
		}
		if (!result.storageSinks().isEmpty()) {
			helper.fail("stop-at-storage must not populate the storage list: " + result.storageSinks());
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-047-LIMIT — a cap of 1 network means traversal can't bridge, and the result flags
	 * {@code hitLimit} when it would have crossed into a second network. @covers MOD-047
	 */
	@GameTest
	public void mod047_traverseCapFlagsLimit(GameTestHelper helper) {
		build(helper);
		drive(helper, 40);
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_A));
		if (left == null) {
			helper.fail("left cable did not form a network");
		}
		// Cap = 1: only the start network fits. A second adjacent network exists beyond the BatteryBox,
		// so the traversal must hit the limit and report it.
		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.TRAVERSE, 1);
		if (!result.hitLimit()) {
			helper.fail("expected hitLimit=true with cap=1 and a bridgeable neighbour, got hitLimit=false");
		}
		helper.succeed();
	}
}
