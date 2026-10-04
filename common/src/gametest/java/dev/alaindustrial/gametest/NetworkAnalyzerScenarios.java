package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.network.AnalyzerTotals;
import dev.alaindustrial.network.NetworkTopology;
import dev.alaindustrial.network.NetworkTraverser;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(NetworkAnalyzerScenarios::mod047_traverseBridgesBatteryBox,
								"mod047_traverse_bridges_battery_box")
						.fabricId("NetworkAnalyzerGameTest", "mod047_traverseBridgesBatteryBox").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod047_stopAtStorageStaysInSegment,
								"mod047_stop_at_storage_stays_in_segment")
						.fabricId("NetworkAnalyzerGameTest", "mod047_stopAtStorageStaysInSegment").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod047_traverseCapFlagsLimit,
								"mod047_traverse_cap_flags_limit")
						.fabricId("NetworkAnalyzerGameTest", "mod047_traverseCapFlagsLimit").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod313_traverseCrossesSinksInGeometricOrder,
								"mod313_traverse_crosses_sinks_in_geometric_order")
						.fabricId("NetworkAnalyzerGameTest", "mod313_traverseCrossesSinksInGeometricOrder")
						.ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_traverseCapKeepsAcceptedNetworks,
								"mod665_traverse_cap_keeps_accepted_networks")
						.fabricId("NetworkAnalyzerGameTest", "mod665_traverseCapKeepsAcceptedNetworks").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_traverseIgnoresInertStorageFace,
								"mod665_traverse_ignores_inert_storage_face")
						.fabricId("NetworkAnalyzerGameTest", "mod665_traverseIgnoresInertStorageFace").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_dualRoleStoreListedOnce,
								"mod665_dual_role_store_listed_once")
						.fabricId("NetworkAnalyzerGameTest", "mod665_dualRoleStoreListedOnce").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_endpointFacesAreRealPorts,
								"mod665_endpoint_faces_are_real_ports")
						.fabricId("NetworkAnalyzerGameTest", "mod665_endpointFacesAreRealPorts").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_traverseCountsPassThroughOnce,
								"mod665_traverse_counts_pass_through_once")
						.fabricId("NetworkAnalyzerGameTest", "mod665_traverseCountsPassThroughOnce").ticks(20, 40),
				RosterEntry.of(NetworkAnalyzerScenarios::mod665_movedGoesStaleWhenTheNetworkSleeps,
								"mod665_moved_goes_stale_when_the_network_sleeps")
						.fabricId("NetworkAnalyzerGameTest", "mod665_movedGoesStaleWhenTheNetworkSleeps").ticks(100),
				RosterEntry.of(NetworkAnalyzerScenarios::mod691_stationClassMatchesNetwork,
								"mod691_station_class_matches_network")
						.fabricId("NetworkAnalyzerGameTest", "mod691_stationClassMatchesNetwork").ticks(20, 40));

		private Roster() {}
	}

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

	// ── MOD-665: the bug-fix pass ─────────────────────────────────────────────────────────────────────

	/**
	 * @implements MOD-665-D2 — at the network cap the networks already accepted are still collected:
	 *     with a cap of 3 the start network and the two neighbours that fit (far-B, far-A) are shown, and
	 *     only the third neighbour (far-C) is dropped
	 * @covers MOD-665
	 */
	public static void mod665_traverseCapKeepsAcceptedNetworks(GameTestHelper helper) {
		buildOrderRig(helper);
		driveOrderRig(helper, 40);
		EnergyNetwork start = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(ORDER_STACK_BOTTOM));
		if (start == null) {
			helper.fail("the cable stack did not form a network");
			return;
		}
		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), start, AnalyzerMode.TRAVERSE, 3);
		if (!result.hitLimit()) {
			helper.fail("three neighbours behind a cap of 3 must hit the limit");
			return;
		}
		boolean farB = result.cables().contains(helper.absolutePos(ORDER_FAR_B));
		boolean farA = result.cables().contains(helper.absolutePos(ORDER_FAR_A));
		boolean farC = result.cables().contains(helper.absolutePos(ORDER_FAR_C));
		if (!farB || !farA) {
			helper.fail("networks accepted before the cap were dropped (D2): far-B=" + farB + " far-A=" + farA
					+ " — the cap allows three networks, the picture must show three");
			return;
		}
		if (farC) {
			helper.fail("far-C is the fourth network and must be cut by the cap of 3");
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-665-D3 — Traverse crosses a store only through a face that is an energy port: a
	 *     cable of an unrelated network touching a Battery Box's inert top is not bridged into
	 * @covers MOD-665
	 */
	public static void mod665_traverseIgnoresInertStorageFace(GameTestHelper helper) {
		build(helper);
		BlockPos top = BAT.above();
		helper.setBlock(top, ModContent.COPPER_CABLE.get());
		for (int i = 0; i < 40; i++) {
			drive(helper, 1);
			tickEntity(helper, top);
		}
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_A));
		EnergyNetwork right = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE_B));
		EnergyNetwork above = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(top));
		if (left == null || right == null || above == null || above == left || above == right) {
			helper.fail("precondition: the cable on the box top must be a third, separate network (left=" + left
					+ ", right=" + right + ", above=" + above + ")");
			return;
		}
		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.TRAVERSE, 32);
		if (!result.cables().contains(helper.absolutePos(CABLE_B))) {
			helper.fail("the real bridge through the box energy faces must still be crossed");
			return;
		}
		if (result.cables().contains(helper.absolutePos(top))) {
			helper.fail("Traverse crossed into a network through the Battery Box inert top face (D3)");
			return;
		}
		helper.succeed();
	}

	/** A Battery Box in the middle of one bus: IN and OUT faces on the same network, a side touching it too. */
	private static final BlockPos BUS_BAT = new BlockPos(3, 2, 1);
	private static final BlockPos[] BUS_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2)};

	private static void buildBusRig(GameTestHelper helper) {
		helper.setBlock(BUS_BAT, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		for (BlockPos cable : BUS_CABLES) {
			helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		}
		for (int i = 0; i < 20; i++) {
			for (BlockPos cable : BUS_CABLES) {
				tickEntity(helper, cable);
			}
			tickEntity(helper, BUS_BAT);
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	/**
	 * @implements MOD-665-D6 — a store that both feeds and draws from one network is listed once, as
	 *     storage, in both modes — listed twice it was drawn as two cubes in one cell
	 * @covers MOD-665
	 */
	public static void mod665_dualRoleStoreListedOnce(GameTestHelper helper) {
		buildBusRig(helper);
		BlockPos bat = helper.absolutePos(BUS_BAT);
		EnergyNetwork bus = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(BUS_CABLES[0]));
		if (bus == null || !bus.diagnostics().producerPositions().contains(bat)
				|| !bus.diagnostics().consumerPositions().contains(bat)) {
			helper.fail("precondition: the box must be both a producer and a consumer of the bus, got "
					+ (bus == null ? "no network" : bus.diagnostics().producerPositions() + " / "
							+ bus.diagnostics().consumerPositions()));
			return;
		}
		for (AnalyzerMode mode : AnalyzerMode.values()) {
			TraversalResult r = NetworkTraverser.traverse(helper.getLevel(), bus, mode, 32);
			int roles = (r.producers().contains(bat) ? 1 : 0) + (r.consumers().contains(bat) ? 1 : 0)
					+ (r.storageSinks().contains(bat) ? 1 : 0);
			if (roles != 1 || !r.storageSinks().contains(bat)) {
				helper.fail(mode + ": the dual-role box must be exactly one node, storage — producers="
						+ r.producers() + " consumers=" + r.consumers() + " storage=" + r.storageSinks());
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-665-D5 — the faces the payload reports for an endpoint are the energy ports really
	 *     wired to the network: the bus box IN and OUT faces, not the inert side a cable merely touches
	 * @covers MOD-665
	 */
	public static void mod665_endpointFacesAreRealPorts(GameTestHelper helper) {
		buildBusRig(helper);
		BlockPos bat = helper.absolutePos(BUS_BAT);
		EnergyNetwork bus = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(BUS_CABLES[0]));
		if (bus == null) {
			helper.fail("the bus did not form a network");
			return;
		}
		TraversalResult r = NetworkTraverser.traverse(helper.getLevel(), bus, AnalyzerMode.STOP_AT_STORAGE, 32);
		int expected = (1 << Direction.WEST.ordinal()) | (1 << Direction.EAST.ordinal());
		Integer packed = r.endpointFaces().get(bat);
		Integer faces = packed == null ? null : NetworkTopology.wiredFaces(packed);
		if (faces == null || faces != expected) {
			helper.fail("box faces " + (faces == null ? "missing" : Integer.toBinaryString(faces)) + ", expected "
					+ Integer.toBinaryString(expected) + " (west + east) — the south side touches a cable but is "
					+ "no port, and a leg drawn there is a wire the network does not have");
			return;
		}
		helper.succeed();
	}

	/**
	 * The pass-through rig: generator, cable, Battery Box, cable, macerator — the box turned so its
	 * charging face ({@code FACING}, IN) meets the generator cable and its output face the machine cable.
	 */
	private static final BlockPos FLOW_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos FLOW_CABLE_A = new BlockPos(2, 2, 1);
	private static final BlockPos FLOW_BAT = new BlockPos(3, 2, 1);
	private static final BlockPos FLOW_CABLE_B = new BlockPos(4, 2, 1);
	private static final BlockPos FLOW_MAC = new BlockPos(5, 2, 1);

	private static void buildFlowRig(GameTestHelper helper) {
		helper.setBlock(FLOW_GEN, ModContent.GENERATOR.get());
		helper.setBlock(FLOW_CABLE_A, ModContent.COPPER_CABLE.get());
		helper.setBlock(FLOW_BAT, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(FLOW_CABLE_B, ModContent.COPPER_CABLE.get());
		helper.setBlock(FLOW_MAC, ModContent.MACERATOR.get());
		if (be(helper, FLOW_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, FLOW_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 64));
		}
		for (int i = 0; i < 80; i++) {
			for (BlockPos rel : new BlockPos[] {FLOW_GEN, FLOW_CABLE_A, FLOW_BAT, FLOW_CABLE_B, FLOW_MAC}) {
				tickEntity(helper, rel);
			}
			NetworkManager.tickAll(helper.getLevel());
		}
	}

	/**
	 * @implements MOD-665-D11 — Traverse does not count the EU that passes through a store twice (once
	 *     into the store on one side, again out of it into the machine on the other)
	 * @covers MOD-665
	 */
	public static void mod665_traverseCountsPassThroughOnce(GameTestHelper helper) {
		buildFlowRig(helper);
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(FLOW_CABLE_A));
		EnergyNetwork right = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(FLOW_CABLE_B));
		if (left == null || right == null || left == right) {
			helper.fail("precondition: the box must split two networks (left=" + left + ", right=" + right + ")");
			return;
		}
		long charged = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.STOP_AT_STORAGE, 32).moved();
		long fed = NetworkTraverser.traverse(helper.getLevel(), right, AnalyzerMode.STOP_AT_STORAGE, 32).moved();
		if (charged <= 0 || fed <= 0) {
			helper.fail("precondition: energy must flow on both sides of the box on the last tick — charged="
					+ charged + " fed=" + fed);
			return;
		}
		long total = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.TRAVERSE, 32).moved();
		if (total >= charged + fed) {
			helper.fail("Traverse summed the box pass-through twice (D11): total " + total + " = charged "
					+ charged + " + fed " + fed);
			return;
		}
		if (total < fed) {
			helper.fail("Traverse lost the machine share: total " + total + " < fed " + fed);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-665-D10 — once a network stops ticking (its generator removed) its last delivery is
	 *     not reported as current
	 * @covers MOD-665
	 */
	public static void mod665_movedGoesStaleWhenTheNetworkSleeps(GameTestHelper helper) {
		buildFlowRig(helper);
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(FLOW_CABLE_A));
		if (left == null) {
			helper.fail("the generator side did not form a network");
			return;
		}
		long before = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.STOP_AT_STORAGE, 32).moved();
		if (before <= 0) {
			helper.fail("precondition: the generator must be charging the box, moved=" + before);
			return;
		}
		helper.setBlock(FLOW_GEN, Blocks.AIR);
		helper.runAfterDelay(AnalyzerTotals.FRESH_WINDOW_TICKS + 10, () -> {
			EnergyNetwork now = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(FLOW_CABLE_A));
			if (now == null) {
				helper.fail("the cable lost its network");
				return;
			}
			long after = NetworkTraverser.traverse(helper.getLevel(), now, AnalyzerMode.STOP_AT_STORAGE, 32).moved();
			if (after != 0) {
				helper.fail("the network has no source and has not ticked for a second, yet the analyzer still "
						+ "reports " + after + " EU moved (D10) — the last delivery before the generator went");
				return;
			}
			helper.succeed();
		});
	}

	// ── MOD-691: the analyzer and the network agree on what a store is ───────────────────────────────

	private static final BlockPos CLASS_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CLASS_CABLE_A = new BlockPos(2, 2, 1);
	/** Workstation lower half, facing SOUTH, so both its WEST and EAST faces take a cable. */
	private static final BlockPos CLASS_STATION = new BlockPos(3, 2, 1);
	private static final BlockPos CLASS_CABLE_B = new BlockPos(4, 2, 1);
	private static final BlockPos CLASS_MAC = new BlockPos(5, 2, 1);

	/**
	 * The analyzer puts the workstation in the class the network serves it in, and walks through it as a
	 * bridge only if that class is "store".
	 *
	 * <p>Layout: {@code [Generator]─cable A─[Workstation]─cable B─[Macerator]}. The station splits the two
	 * cables into two networks, exactly like the Battery Box of {@link #mod047_traverseBridgesBatteryBox};
	 * the difference is that energy does not pass through a station, so the second network is no part of
	 * the first unless the station is a store. The network's own verdict is read from what it did, not from
	 * a private predicate: over the run it books every delivery into a store as {@code lastTickToStorage}.
	 * Before MOD-691 the two read the same flag two different ways ({@code instanceof MachineBlockEntity}
	 * against {@code instanceof EnergyBlockEntity}), and the analyzer drew the station as a store and
	 * stitched cable B into the picture of a network that served it as a machine.
	 *
	 * @implements MOD-691-ANALYZER — the analyzer's storage class matches the network's
	 * @covers MOD-691
	 */
	public static void mod691_stationClassMatchesNetwork(GameTestHelper helper) {
		helper.setBlock(CLASS_GEN, ModContent.GENERATOR.get());
		helper.setBlock(CLASS_CABLE_A, ModContent.COPPER_CABLE.get());
		BlockPos station = helper.absolutePos(CLASS_STATION);
		helper.getLevel().setBlockAndUpdate(station,
				ModContent.WORKSTATION.get().defaultBlockState().setValue(WorkstationBlock.FACING, Direction.SOUTH));
		helper.getLevel().setBlockAndUpdate(station.above(),
				ModContent.WORKSTATION.get().defaultBlockState().setValue(WorkstationBlock.FACING, Direction.SOUTH));
		WorkstationBlock.tryAssemble(helper.getLevel(), station.above());
		helper.setBlock(CLASS_CABLE_B, ModContent.COPPER_CABLE.get());
		helper.setBlock(CLASS_MAC, ModContent.MACERATOR.get());
		if (be(helper, CLASS_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, CLASS_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
		if (!(helper.getLevel().getBlockEntity(station) instanceof WorkstationBlockEntity)) {
			helper.fail("precondition: the workstation did not assemble at " + station);
			return;
		}

		long moved = 0L;
		long intoStorage = 0L;
		for (int i = 0; i < 40; i++) {
			tickEntity(helper, CLASS_GEN);
			tickEntity(helper, CLASS_CABLE_A);
			tickEntity(helper, CLASS_CABLE_B);
			tickEntity(helper, CLASS_MAC);
			NetworkManager.tickAll(helper.getLevel());
			EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CLASS_CABLE_A));
			if (net != null) {
				moved += net.lastTickMoved();
				intoStorage += net.lastTickToStorage();
			}
		}
		EnergyNetwork left = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CLASS_CABLE_A));
		EnergyNetwork right = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CLASS_CABLE_B));
		if (left == null || right == null || left == right) {
			helper.fail("precondition: the station must split the cables into two networks (left=" + left
					+ ", right=" + right + ")");
			return;
		}
		if (moved <= 0) {
			helper.fail("precondition: the generator's network delivered nothing, so the network never said how"
					+ " it classes the station");
			return;
		}
		boolean networkSaysStore = intoStorage > 0;

		TraversalResult result = NetworkTraverser.traverse(helper.getLevel(), left, AnalyzerMode.TRAVERSE, 32);
		boolean analyzerSaysStore = result.storageSinks().contains(station);
		if (analyzerSaysStore != networkSaysStore) {
			helper.fail("MOD-691: the analyzer draws the workstation as " + (analyzerSaysStore ? "a store" : "a machine")
					+ " but the network serves it as " + (networkSaysStore ? "a store" : "a machine")
					+ " (moved=" + moved + ", into storage=" + intoStorage + ")");
			return;
		}
		if (!networkSaysStore && result.cables().contains(helper.absolutePos(CLASS_CABLE_B))) {
			helper.fail("MOD-691: the analyzer bridged through the workstation into the macerator's network,"
					+ " although the network does not treat the station as a store: cables=" + result.cables());
			return;
		}
		if (!networkSaysStore && !result.consumers().contains(station)) {
			helper.fail("MOD-691: a workstation served as a machine must be listed as a consumer: consumers="
					+ result.consumers() + ", storage=" + result.storageSinks());
			return;
		}
		helper.succeed();
	}
}
