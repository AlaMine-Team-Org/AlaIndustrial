package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ConcreteRailBeamBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.QuarryControllerBlock;
import dev.alaindustrial.block.QuarryFoundationBlock;
import dev.alaindustrial.block.QuarryFramePart;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.quarry.QuarryFrameShapes;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The quarry's portal frame on the designer's 3 × 3 grid, in the world (MOD-950, stage A of MOD-637): the controller
 * reads the frame, lights up, and opens the foundation under it as the power socket; taking the frame apart closes it
 * again.
 *
 * <p><b>The rig stands outside the test's own box.</b> The smallest legal frame is 11 × 11 (two 3 × 3 towers and a
 * span of 5 between them on each axis), more than the 8 × 8 box both lanes force-load. So each case stands its frame
 * on the next free cell of the town-drone rig grid ({@link TownDroneScenarios#nextOrigin}), keeps the chunks loaded
 * but not simulated for the length of the test ({@link TownDroneScenarios.ChunkHold}), and drives the controller, the
 * cables and the network by hand, one tick each — the pattern the town halls use, for the same reason.
 *
 * <p>The paths run along X; the near one is concrete rail beams and the far one plate rail beams, because the frame
 * takes either and a mix. The geometry rules themselves are L1 ({@code QuarryFrameScanTest}); what is checked here is
 * the world binding: the block-to-part mapping, the painting, the periodic sweep and the energy socket.
 */
public final class QuarryFrameScenarios {

	private QuarryFrameScenarios() {
	}

	/** Gametest roster entries for this scenario class (MOD-669). */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(QuarryFrameScenarios::aCompleteFrameForms, "quarry_frame_forms").ticks(40),
				RosterEntry.of(QuarryFrameScenarios::aBrokenFarTowerUnformsOnTheSweep,
						"quarry_frame_far_break_unforms_on_sweep").ticks(40),
				RosterEntry.of(QuarryFrameScenarios::removingTheControllerClosesThePort,
						"quarry_frame_controller_removed_closes_port").ticks(40),
				RosterEntry.of(QuarryFrameScenarios::onlyTheSocketOfAFormedFrameTakesPower,
						"quarry_frame_main_foundation_takes_power").ticks(40),
				RosterEntry.of(QuarryFrameScenarios::twoCablesOnTheSocketCreateNoEnergy,
						"quarry_frame_two_cables_create_no_energy").ticks(40),
				RosterEntry.of(QuarryFrameScenarios::aFormedFrameHidesItsCellsAndShellsTheModel,
						"quarry_frame_hides_cells_and_shells_model").ticks(40));

		private Roster() {
		}
	}

	static final int T = QuarryFrameScan.TOWER;
	static final int SPAN = 5;
	/** The far towers' north-west corner, from the controller tower's. */
	static final int FAR = T + SPAN;
	/** The bottom foundation layer, from the rig origin; the bottom head layer is {@code HEAD_HEIGHT} above it. */
	static final int BASE = 1;
	static final int HEAD = BASE + QuarryFrameScan.HEAD_HEIGHT;
	/** The upper path layer, where the bridge lies; the cart stands one above. */
	static final int TOP = HEAD + QuarryFrameScan.PATH_LAYERS - 1;
	private static final int BRIDGE_X = T + SPAN / 2;
	/** The rig's cleared box: the frame and the power line west and north of it. */
	private static final int CLEAR_LOW = -3;
	private static final int CLEAR_HIGH = FAR + T + 1;

	/** One stood frame: where it is, and the tickets that keep it loaded. */
	record Rig(ServerLevel level, BlockPos origin, TownDroneScenarios.ChunkHold hold) {
		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		BlockPos controllerPos() {
			return at(0, HEAD, 0);
		}

		BlockPos socket() {
			return at(0, BASE, 0);
		}

		void set(BlockPos pos, BlockState state) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}

		BlockState state(BlockPos pos) {
			return level.getBlockState(pos);
		}

		QuarryControllerBlockEntity controller() {
			if (level.getBlockEntity(controllerPos()) instanceof QuarryControllerBlockEntity controller) {
				return controller;
			}
			throw new IllegalStateException("no quarry controller block entity at " + controllerPos());
		}

		void tick(BlockPos pos) {
			if (level.getBlockEntity(pos) instanceof EnergyBlockEntity be) {
				be.serverTick(level, pos, level.getBlockState(pos));
			}
		}

		/** One controller tick with the scan due now, the way placement and a neighbour change arm it. */
		void scanNow() {
			controller().requestScan();
			tick(controllerPos());
		}

		boolean formed() {
			return state(controllerPos()).getValue(QuarryControllerBlock.FORMED);
		}

		boolean port(BlockPos foundation) {
			BlockState state = state(foundation);
			return state.hasProperty(QuarryFoundationBlock.PORT) && state.getValue(QuarryFoundationBlock.PORT);
		}
	}

	// --- the rig -------------------------------------------------------------------------------------------------

	static Rig rig(GameTestHelper helper, boolean withCart) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = TownDroneScenarios.nextOrigin();
		Rig rig = new Rig(level, origin, TownDroneScenarios.ChunkHold.forTest(helper, level, origin));
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = CLEAR_LOW; x <= CLEAR_HIGH; x++) {
			for (int z = CLEAR_LOW; z <= CLEAR_HIGH; z++) {
				rig.set(rig.at(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
				for (int y = 1; y <= HEAD + QuarryFrameScan.HEAD_LAYERS + 1; y++) {
					rig.set(rig.at(x, y, z), air);
				}
			}
		}
		tower(rig, 0, 0);
		tower(rig, FAR, 0);
		tower(rig, 0, FAR);
		tower(rig, FAR, FAR);
		rig.set(rig.controllerPos(), ModContent.QUARRY_CONTROLLER.get().defaultBlockState());
		for (int y = HEAD; y <= TOP; y++) {
			for (int x = T; x < FAR; x++) {
				for (int row = 0; row < T; row++) {
					rig.set(rig.at(x, y, row), beam(ModContent.CONCRETE_RAIL_BEAM.get(), Direction.Axis.X));
					// The far path mixes the two beams: either one is a rail.
					Block far = x == T ? ModContent.CONCRETE_RAIL_BEAM.get() : ModContent.PLATE_RAIL_BEAM.get();
					rig.set(rig.at(x, y, FAR + row), beam(far, Direction.Axis.X));
				}
			}
		}
		for (int z = T; z < FAR; z++) {
			rig.set(rig.at(BRIDGE_X, TOP, z), beam(ModContent.QUARRY_BRIDGE_BEAM.get(), Direction.Axis.Z));
		}
		for (int z = 0; z < FAR + T; z++) {
			if (z > T && z < FAR - 1) {
				continue; // the saddles: over each path and one cell into the pit
			}
			rig.set(rig.at(BRIDGE_X, TOP + 1, z), beam(ModContent.QUARRY_BRIDGE_BEAM.get(), Direction.Axis.Z));
		}
		if (withCart) {
			rig.set(cartPos(rig), ModContent.QUARRY_CART.get().defaultBlockState());
		}
		return rig;
	}

	private static BlockPos cartPos(Rig rig) {
		return rig.at(BRIDGE_X, TOP + 1, (FAR + T - 1) / 2);
	}

	private static void tower(Rig rig, int x0, int z0) {
		for (int dx = 0; dx < T; dx++) {
			for (int dz = 0; dz < T; dz++) {
				for (int layer = 0; layer < QuarryFrameScan.FOUNDATION_LAYERS; layer++) {
					rig.set(rig.at(x0 + dx, BASE + layer, z0 + dz),
							ModContent.QUARRY_FOUNDATION.get().defaultBlockState());
				}
				for (int layer = 0; layer < QuarryFrameScan.HEAD_LAYERS; layer++) {
					rig.set(rig.at(x0 + dx, HEAD + layer, z0 + dz), ModContent.QUARRY_HEAD.get().defaultBlockState());
				}
			}
		}
		for (int y = BASE + QuarryFrameScan.FOUNDATION_LAYERS; y < HEAD; y++) {
			for (int dx = 0; dx < T; dx++) {
				for (int dz = 0; dz < T; dz++) {
					rig.set(rig.at(x0 + dx, y, z0 + dz), ModContent.TEMPERED_IRON_SCAFFOLD.get().defaultBlockState());
				}
			}
		}
	}

	private static BlockState beam(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(ConcreteRailBeamBlock.AXIS, axis);
	}

	/** A charged Energy Storage whose OUT face (opposite its facing) points at {@code +X}. */
	private static void chargedBox(Rig rig, BlockPos box, long charge) {
		rig.set(box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (rig.level().getBlockEntity(box) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().setAmountUntracked(charge);
		}
	}

	private static long boxEnergy(Rig rig, BlockPos box) {
		return rig.level().getBlockEntity(box) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount()
				: -1L;
	}

	private static long cableAmount(Rig rig, BlockPos cable) {
		return rig.level().getBlockEntity(cable) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : 0L;
	}

	private static void driveLine(Rig rig, int ticks, BlockPos box, BlockPos... cables) {
		for (int i = 0; i < ticks; i++) {
			for (BlockPos cable : cables) {
				rig.tick(cable);
			}
			rig.tick(box);
			NetworkManager.tickAll(rig.level());
			rig.tick(rig.controllerPos());
		}
	}

	private static boolean armToward(Rig rig, BlockPos cable, Direction toward) {
		return rig.state(cable).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(toward));
	}

	// --- scenarios -----------------------------------------------------------------------------------------------

	/**
	 * A complete massive frame — either rail beam in its paths — lights the controller and opens the one foundation
	 * under it, and only that one: every other foundation of the 72 stays a plain block.
	 *
	 * @implements MOD-950 — the frame is read from its blocks, the controller lights, the cell under it is the socket
	 */
	public static void aCompleteFrameForms(GameTestHelper helper) {
		Rig rig = rig(helper, true);
		rig.scanNow();
		QuarryFrameScan.Status status = rig.controller().status();
		if (status != QuarryFrameScan.Status.FORMED || !rig.formed()) {
			helper.fail("a complete 5 x 5 frame did not form: " + status + ", panel lit " + rig.formed());
			return;
		}
		if (!rig.port(rig.socket())) {
			helper.fail("the foundation under the controller did not open its port");
			return;
		}
		for (BlockPos other : List.of(rig.at(1, BASE, 0), rig.at(0, BASE + 1, 0), rig.at(FAR, BASE, 0),
				rig.at(FAR + T - 1, BASE, FAR + T - 1))) {
			if (rig.port(other)) {
				helper.fail("the foundation at " + other + " opened a port, but only the cell under the controller"
						+ " is the socket");
				return;
			}
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * A block broken at the far tower is nobody's neighbour of the controller, so only the periodic sweep can see
	 * it: within one scan interval the panel goes dark, the socket closes and the report names the missing scaffold.
	 *
	 * @implements MOD-950 — the frame comes apart on the next sweep, with the broken block named
	 */
	public static void aBrokenFarTowerUnformsOnTheSweep(GameTestHelper helper) {
		Rig rig = rig(helper, true);
		rig.scanNow();
		if (!rig.formed()) {
			helper.fail("the frame did not form to begin with: " + rig.controller().status());
			return;
		}
		BlockPos broken = rig.at(FAR + 2, BASE + 3, FAR + 1);
		rig.set(broken, Blocks.AIR.defaultBlockState());
		for (int i = 0; i <= Math.max(1, Config.quarryScanIntervalTicks); i++) {
			rig.tick(rig.controllerPos());
		}
		if (rig.formed() || rig.port(rig.socket())) {
			helper.fail("a scan interval after the far scaffold went, the frame still reads formed " + rig.formed()
					+ " / socket open " + rig.port(rig.socket()));
			return;
		}
		QuarryFrameScan.Status status = rig.controller().status();
		if (status != QuarryFrameScan.Status.TOWER_INCOMPLETE) {
			helper.fail("the report says " + status + ", not the incomplete far tower");
			return;
		}
		String report = rig.controller().describeStatus().getString();
		String expected = broken.getX() + ", " + broken.getY() + ", " + broken.getZ();
		if (!report.contains(expected)) {
			helper.fail("the report should name the missing scaffold at " + expected + ", but said: " + report);
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * The frame cannot outlive its brain: removing the controller closes the socket at once, without waiting for a
	 * scan that would never come.
	 *
	 * @implements MOD-950 — the socket closes the moment the controller goes
	 */
	public static void removingTheControllerClosesThePort(GameTestHelper helper) {
		Rig rig = rig(helper, true);
		rig.scanNow();
		if (!rig.port(rig.socket())) {
			helper.fail("the socket did not open to begin with: " + rig.controller().status());
			return;
		}
		rig.set(rig.controllerPos(), Blocks.AIR.defaultBlockState());
		if (rig.port(rig.socket())) {
			helper.fail("the controller is gone but the foundation under it still lends a port");
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * The cable goes to the ground, to the one cell under the controller. Before the frame forms (no cart yet) a
	 * cable there draws no arm and moves nothing; once the cart completes the frame the arm appears and energy reaches
	 * the controller. A cable at another tower's foundation stays unconnected throughout.
	 *
	 * @implements MOD-950 — the socket foundation lends the controller's energy port, only while formed
	 */
	public static void onlyTheSocketOfAFormedFrameTakesPower(GameTestHelper helper) {
		Rig rig = rig(helper, false);
		BlockPos cable = rig.at(-1, BASE, 0);
		BlockPos box = rig.at(-2, BASE, 0);
		rig.set(cable, ModContent.COPPER_CABLE.get().defaultBlockState());
		chargedBox(rig, box, 10_000L);
		BlockPos otherCable = rig.at(FAR + T, BASE, FAR);
		rig.set(otherCable, ModContent.COPPER_CABLE.get().defaultBlockState());
		rig.scanNow();
		driveLine(rig, 10, box, cable, otherCable);
		long early = rig.controller().getEnergyStorage().getAmount();
		if (rig.formed() || early != 0L || armToward(rig, cable, Direction.EAST)) {
			helper.fail("without its cart the frame must not take power: formed " + rig.formed() + ", " + early
					+ " EU in, arm " + armToward(rig, cable, Direction.EAST));
			return;
		}
		rig.set(cartPos(rig), ModContent.QUARRY_CART.get().defaultBlockState());
		rig.scanNow();
		driveLine(rig, 20, box, cable, otherCable);
		if (!armToward(rig, cable, Direction.EAST)) {
			helper.fail("the cable at the formed frame's socket draws no arm toward it");
			return;
		}
		long got = rig.controller().getEnergyStorage().getAmount();
		if (got <= 0L) {
			helper.fail("the formed frame's controller got no energy through its socket (" + got + " EU)");
			return;
		}
		if (armToward(rig, otherCable, Direction.WEST)) {
			helper.fail("a cable at another tower's foundation joined the frame; only the socket takes power");
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * Two cables on two faces of the socket are one machine with two wires, not two machines: every EU the controller
	 * holds left the box. Counted twice, the controller would take one packet per wire while the box paid one.
	 *
	 * @implements MOD-950 — the frame is one consumer however many cables touch its socket
	 */
	public static void twoCablesOnTheSocketCreateNoEnergy(GameTestHelper helper) {
		Rig rig = rig(helper, true);
		BlockPos cableWest = rig.at(-1, BASE, 0);
		BlockPos cableNorth = rig.at(0, BASE, -1);
		BlockPos joint = rig.at(-1, BASE, -1);
		BlockPos box = rig.at(-2, BASE, -1);
		rig.set(cableWest, ModContent.COPPER_CABLE.get().defaultBlockState());
		rig.set(cableNorth, ModContent.COPPER_CABLE.get().defaultBlockState());
		rig.set(joint, ModContent.COPPER_CABLE.get().defaultBlockState());
		long charge = 10_000L;
		chargedBox(rig, box, charge);
		rig.scanNow();
		driveLine(rig, 30, box, cableWest, cableNorth, joint);
		long inController = rig.controller().getEnergyStorage().getAmount();
		long inCables = cableAmount(rig, cableWest) + cableAmount(rig, cableNorth) + cableAmount(rig, joint);
		long left = charge - boxEnergy(rig, box);
		if (inController <= 0L) {
			helper.fail("the controller got nothing through two cables on its socket");
			return;
		}
		if (inController + inCables != left) {
			helper.fail("energy is not conserved: the box gave " + left + " EU, the controller holds " + inController
					+ " and the cables " + inCables);
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/**
	 * Formed, the frame hides under the designer's model: its cells are flagged formed and take the model's shape, and
	 * where a sliver of the model rises past the blocks the air holds a solid quarry shell. A player breaking a shell
	 * breaks the frame block under it, so the frame comes apart; and with the frame apart the shells are gone.
	 *
	 * @implements MOD-950 — the formed frame is the designer's model, solid where it is drawn, and what the player sees
	 *             is what breaks it
	 */
	public static void aFormedFrameHidesItsCellsAndShellsTheModel(GameTestHelper helper) {
		Rig rig = rig(helper, true);
		rig.scanNow();
		BlockPos head = rig.at(1, HEAD + QuarryFrameScan.HEAD_LAYERS - 1, 1);
		if (!QuarryFramePart.formed(rig.state(head)) || QuarryFrameShapes.shapeAt(rig.level(), head) == null) {
			helper.fail("a formed tower's top head is not hidden under the model, or has no shape of its own");
			return;
		}
		BlockPos farScaffold = rig.at(FAR + 1, BASE + 3, FAR + 1);
		if (!rig.controllerPos().equals(QuarryFrameShapes.controllerOf(rig.level(), farScaffold))) {
			helper.fail("a far tower's scaffold must know its controller, so a click on it opens the screen");
			return;
		}
		BlockPos shellPos = firstShell(rig);
		if (shellPos == null) {
			helper.fail("no shell anywhere above the frame, though the bridge and the saddles rise past its blocks");
			return;
		}
		BlockState shell = rig.state(shellPos);
		if (shell.getCollisionShape(rig.level(), shellPos).isEmpty()) {
			helper.fail("the shell at " + shellPos + " does not collide, so the player walks into the drawn model");
			return;
		}
		BlockPos under = shellPos.below();
		shell.getBlock().playerWillDestroy(rig.level(), shellPos, shell, helper.makeMockPlayer(GameType.SURVIVAL));
		if (!rig.state(under).isAir()) {
			helper.fail("breaking the shell at " + shellPos + " left the frame block under it: " + rig.state(under));
			return;
		}
		rig.scanNow();
		if (rig.formed() || QuarryFramePart.formed(rig.state(head))) {
			helper.fail("with a block under a broken shell gone the frame stayed formed: " + rig.controller().status());
			return;
		}
		if (firstShell(rig) != null) {
			helper.fail("the frame came apart but a shell stayed at " + firstShell(rig));
			return;
		}
		rig.hold().release();
		helper.succeed();
	}

	/** The first quarry shell in the rig's box, or {@code null}. */
	private static BlockPos firstShell(Rig rig) {
		for (int y = HEAD; y <= HEAD + QuarryFrameScan.HEAD_LAYERS; y++) {
			for (int x = 0; x < FAR + T; x++) {
				for (int z = 0; z < FAR + T; z++) {
					if (rig.state(rig.at(x, y, z)).is(ModContent.QUARRY_SHELL.get())) {
						return rig.at(x, y, z);
					}
				}
			}
		}
		return null;
	}
}
