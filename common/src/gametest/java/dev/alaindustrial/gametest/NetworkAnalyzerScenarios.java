package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import dev.alaindustrial.registry.ModContent;
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
public final class NetworkAnalyzerScenarios {

	private NetworkAnalyzerScenarios() {}

	/** Relative positions inside the test batch (helper.absolutePos is applied at lookup time). */
	private static final BlockPos GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos BAT = new BlockPos(3, 2, 1);
	private static final BlockPos CABLE_B = new BlockPos(4, 2, 1);
	private static final BlockPos MAC = new BlockPos(5, 2, 1);

	private static BlockEntity be(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private static void tick(GameTestHelper helper, BlockEntity be) {
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
	private static void build(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		helper.setBlock(CABLE_A, ModContent.COPPER_CABLE.get());
		helper.setBlock(BAT, ModContent.BATTERY_BOX.get().defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(CABLE_B, ModContent.COPPER_CABLE.get());
		helper.setBlock(MAC, ModContent.MACERATOR.get());
		if (be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	/** Tick every block + the per-level NetworkManager for {@code n} synthetic ticks. */
	private static void drive(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			tickEntity(helper, GEN);
			tickEntity(helper, CABLE_A);
			tickEntity(helper, BAT);
			tickEntity(helper, CABLE_B);
			tickEntity(helper, MAC);
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	private static void tickEntity(GameTestHelper helper, BlockPos rel) {
		BlockEntity entity = be(helper, rel);
		if (entity != null) {
			tick(helper, entity);
		}
	}

	/**
	 * @implements MOD-047-TRAVERSE — Traverse mode stitches two networks split by a BatteryBox into one
	 *     picture, including the BatteryBox as a storage sink.
	 * @implements R-DIAG-02 — the analyzer is read-only: traversing the network must not mutate its
	 *     topology (cable set) or its telemetry (last-tick moved EU). Snapshots both before and after
	 *     {@link NetworkTraverser#traverse} and asserts byte-for-byte equality — without this a regression
	 *     that adds a side effect to traversal (e.g. waking the network, marking dirty) would go undetected.
	 * @covers MOD-047, R-DIAG-02
	 */
	public static void mod047_traverseBridgesBatteryBox(GameTestHelper helper) {
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
		// R-DIAG-02: snapshot the network state BEFORE the read-only traversal.
		var cablesBefore = new java.util.HashSet<>(left.cables());
		long movedBefore = left.lastTickMoved();

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
		// R-DIAG-02: the network must be byte-for-byte unchanged after the traversal (read-only contract).
		var cablesAfter = left.cables();
		long movedAfter = left.lastTickMoved();
		if (!cablesAfter.equals(cablesBefore)) {
			helper.fail("R-DIAG-02 violation: traverse mutated the cable set — before=" + cablesBefore
					+ " after=" + cablesAfter);
		}
		if (movedAfter != movedBefore) {
			helper.fail("R-DIAG-02 violation: traverse changed lastTickMoved — before=" + movedBefore
					+ " after=" + movedAfter);
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-047-STOP — STOP_AT_STORAGE returns only the clicked cable's own network and never
	 * bridges. @covers MOD-047
	 */
	public static void mod047_stopAtStorageStaysInSegment(GameTestHelper helper) {
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
	public static void mod047_traverseCapFlagsLimit(GameTestHelper helper) {
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

	// ── MOD-313: the order neighbour networks are crossed into ───────────────────────────────────────

	/**
	 * A second rig, built only for the ordering question. One vertical cable segment with THREE
	 * BatteryBoxes hanging off it, each bridging into its own single-cable network.
	 *
	 * <p>The placement is the whole point: the boxes alternate sides, so the order the CABLES discover them
	 * in (bottom to top) is deliberately NOT the geometric order of the BOXES themselves (west before
	 * east). Sorted by {@code POSITION_ORDER} (x, then y, then z) the sinks come out {@code B, A, C} —
	 * that is what the scenario asserts.
	 *
	 * <p>Inherited from the endpoint lists they come out {@code A, C, B}, which is neither the cable order
	 * nor the geometric one. All three boxes face EAST, and a Battery Box takes charge on its {@code FACING}
	 * face and emits on the opposite one, so the face each turns to the stack decides its role: A and C sit
	 * east of the stack and touch it with their WEST (= OUT) face, landing in {@code producers}; B sits west
	 * of it and touches it with its EAST (= {@code FACING}, IN) face, landing in {@code consumers}. And
	 * {@code NetworkTraverser} fills {@code netSinks} from the producers first and the consumers after, so
	 * B goes last. Either way the unsorted answer differs from {@code B, A, C}, which is what keeps the
	 * assertion below from being satisfied by both readings at once.
	 */
	private static final BlockPos ORDER_STACK_BOTTOM = new BlockPos(4, 1, 1);
	private static final int ORDER_STACK_HEIGHT = 5;
	/** East of the bottom cable — first found by the cable walk, second in geometric order. */
	private static final BlockPos ORDER_BAT_A = new BlockPos(5, 1, 1);
	private static final BlockPos ORDER_FAR_A = new BlockPos(6, 1, 1);
	/** West of the middle cable — second found by the cable walk, FIRST in geometric order (x = 3). */
	private static final BlockPos ORDER_BAT_B = new BlockPos(3, 3, 1);
	private static final BlockPos ORDER_FAR_B = new BlockPos(2, 3, 1);
	/** East of the top cable — last in the cable walk and last in geometric order alike. */
	private static final BlockPos ORDER_BAT_C = new BlockPos(5, 5, 1);
	private static final BlockPos ORDER_FAR_C = new BlockPos(6, 5, 1);

	private static void buildOrderRig(GameTestHelper helper) {
		for (int i = 0; i < ORDER_STACK_HEIGHT; i++) {
			helper.setBlock(ORDER_STACK_BOTTOM.above(i), ModContent.COPPER_CABLE.get());
		}
		for (BlockPos bat : new BlockPos[] {ORDER_BAT_A, ORDER_BAT_B, ORDER_BAT_C}) {
			helper.setBlock(bat, ModContent.BATTERY_BOX.get().defaultBlockState()
					.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		}
		for (BlockPos far : new BlockPos[] {ORDER_FAR_A, ORDER_FAR_B, ORDER_FAR_C}) {
			helper.setBlock(far, ModContent.COPPER_CABLE.get());
		}
	}

	private static void driveOrderRig(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			for (int y = 0; y < ORDER_STACK_HEIGHT; y++) {
				tickEntity(helper, ORDER_STACK_BOTTOM.above(y));
			}
			for (BlockPos rel : new BlockPos[] {ORDER_BAT_A, ORDER_BAT_B, ORDER_BAT_C,
					ORDER_FAR_A, ORDER_FAR_B, ORDER_FAR_C}) {
				tickEntity(helper, rel);
			}
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	/**
	 * @implements MOD-313-TRAVERSE-ORDER — the traversal crosses storage sinks in the sinks' own
	 *     geometric order, so the same factory produces the same picture wherever it is built and
	 *     whichever neighbour the cap cuts off. @covers MOD-047
	 */
	public static void mod313_traverseCrossesSinksInGeometricOrder(GameTestHelper helper) {
		buildOrderRig(helper);
		driveOrderRig(helper, 40);

		EnergyNetwork start = NetworkManager.networkAt(helper.getLevel(),
				helper.absolutePos(ORDER_STACK_BOTTOM));
		if (start == null) {
			helper.fail("the cable stack did not form a network");
			return;
		}
		// Preconditions. Without these the scenario can go green on a rig that never had three separate
		// neighbours to order in the first place — a block silently placed outside the structure, or two
		// far cables that merged into one network, would both look like success.
		BlockPos[] fars = {ORDER_FAR_A, ORDER_FAR_B, ORDER_FAR_C};
		EnergyNetwork[] farNets = new EnergyNetwork[fars.length];
		for (int i = 0; i < fars.length; i++) {
			farNets[i] = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(fars[i]));
			if (farNets[i] == null) {
				helper.fail("far cable " + fars[i] + " did not form a network");
				return;
			}
			if (farNets[i] == start) {
				helper.fail("far cable " + fars[i] + " merged into the start network — the BatteryBox "
						+ "must split them or there is nothing to bridge");
				return;
			}
			for (int j = 0; j < i; j++) {
				if (farNets[i] == farNets[j]) {
					helper.fail("far cables " + fars[i] + " and " + fars[j] + " share one network — they "
							+ "must be three separate neighbours for the order to mean anything");
					return;
				}
			}
		}

		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), start,
				AnalyzerMode.TRAVERSE, 32);

		for (BlockPos bat : new BlockPos[] {ORDER_BAT_A, ORDER_BAT_B, ORDER_BAT_C}) {
			if (!result.storageSinks().contains(helper.absolutePos(bat))) {
				helper.fail("BatteryBox " + bat + " was not reported as a storage sink: "
						+ result.storageSinks());
				return;
			}
		}

		// B before A before C: the boxes' own geometric order (x=3 first, then the two at x=5 bottom to
		// top). NOT the order the endpoint lists hold them in, which is A, C, B — producers (A, C) first,
		// then the lone consumer B; see the rig javadoc for why the roles fall out that way.
		int indexA = result.cableList().indexOf(helper.absolutePos(ORDER_FAR_A));
		int indexB = result.cableList().indexOf(helper.absolutePos(ORDER_FAR_B));
		int indexC = result.cableList().indexOf(helper.absolutePos(ORDER_FAR_C));
		if (indexA < 0 || indexB < 0 || indexC < 0) {
			helper.fail("traverse did not reach all three neighbour networks: A=" + indexA + " B=" + indexB
					+ " C=" + indexC + " cables=" + result.cableList());
			return;
		}
		if (!(indexB < indexA && indexA < indexC)) {
			helper.fail("neighbour networks were collected in the order the endpoint lists happened to "
					+ "hold their storage sinks, not in the sinks' geometric order — expected far-B "
					+ "before far-A before far-C, got B=" + indexB + " A=" + indexA + " C=" + indexC);
			return;
		}
		helper.succeed();
	}
}
