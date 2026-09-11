package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableArmReach;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorSectionBlock;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.menu.RadiantSolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Mirror Concentrator's two-by-two-by-two assembly (MOD-603), and the assembled machine answering
 * as one: its screen from any cell, its energy port along the whole bottom tier (MOD-608).
 *
 * <p><b>Why these scenarios call the assembler directly.</b> Programmatic placement reaches neither
 * {@code setPlacedBy} nor the neighbour update a real placement causes (MOD-015), so a test that
 * merely wrote the sections into the world would assert on a machine the harness had no way to
 * build. The Workstation's scenarios call {@code tryAssemble} for exactly this reason and this is
 * the same trade: what is covered here is the ASSEMBLY RULE — which boxes count, which do not, and
 * what happens when one is broken — while the two hooks that reach it are covered by playing.
 *
 * <p>Disassembly is different and is NOT helped along: breaking a cell here goes through the same
 * {@code updateShape} cascade a pickaxe causes, so those assertions do test the wiring.
 *
 * <p>The core sits at {@link #CORE} so that all four boxes it could grow into, and the neighbours
 * they touch, stay inside the force-loaded rig.
 */
public final class ConcentratorStructureScenarios {

	private ConcentratorStructureScenarios() {
	}

	/** Bottom corner the panel stands in. Chosen so every rotation of the box fits the rig. */
	private static final BlockPos CORE = new BlockPos(3, 2, 3);

	/** Put a grown, un-assembled concentrator at {@link #CORE}. */
	private static void placeCore(GameTestHelper helper) {
		helper.setBlock(CORE, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
	}

	/** Drop a loose section into every cell of the box but the core's. */
	private static void placeSections(GameTestHelper helper, Direction facing, ConcentratorPart skip) {
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE || part == skip) {
				continue;
			}
			helper.setBlock(CORE.offset(part.worldOffset(facing)),
					ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
		}
	}

	/** Run the assembler on the core, the way a placement hook would. */
	private static void assemble(GameTestHelper helper) {
		ConcentratorStructure.tryAssemble(helper.getLevel(), helper.absolutePos(CORE));
	}

	private static BlockState stateAt(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockState(helper.absolutePos(relative));
	}

	/** Assert that every cell of the box carries the part and facing it should. */
	private static void assertAssembled(GameTestHelper helper, Direction facing) {
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			BlockPos cell = CORE.offset(part.worldOffset(facing));
			if (!ConcentratorStructure.isMember(stateAt(helper, cell), part, facing)) {
				helper.fail("cell " + part + " of the " + facing + " box did not assemble: "
						+ stateAt(helper, cell));
			}
		}
	}

	/**
	 * Seven sections around the grown panel make the machine — and they make it in the box the player
	 * actually filled, not in a fixed one.
	 *
	 * @implements MOD-603 — assembly, and the direction coming from where the sections went
	 */
	public static void assemblesInTheBoxThatWasFilled(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			// Clear the whole neighbourhood so the previous rotation cannot be mistaken for this one.
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, facing, null);
			assemble(helper);
			assertAssembled(helper, facing);
		}
		helper.succeed();
	}

	/**
	 * Six sections are not seven: the machine must refuse to be most of the way built.
	 *
	 * @implements MOD-603 — an incomplete box does not assemble
	 */
	public static void refusesToAssembleWithACellMissing(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, ConcentratorPart.TOP_BACK_RIGHT);
		assemble(helper);
		if (stateAt(helper, CORE).getValue(RadiantSolarPanelBlock.ASSEMBLED)) {
			helper.fail("the panel assembled with one cell still empty");
		}
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE || part == ConcentratorPart.TOP_BACK_RIGHT) {
				continue;
			}
			BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
			if (stateAt(helper, cell).getValue(ConcentratorSectionBlock.PART)
					!= ConcentratorPart.LOOSE) {
				helper.fail("section " + part + " claimed a structure that was never completed");
			}
		}
		helper.succeed();
	}

	/**
	 * Breaking ANY one of the eight cells takes the whole machine apart, and leaves every survivor in
	 * the form the player originally put down.
	 *
	 * @implements MOD-603 — disassembly by degradation, from each cell in turn
	 */
	public static void breakingAnyCellDisassemblesAll(GameTestHelper helper) {
		for (ConcentratorPart broken : ConcentratorPart.CELLS) {
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, Direction.NORTH, null);
			assemble(helper);
			assertAssembled(helper, Direction.NORTH);

			helper.setBlock(CORE.offset(broken.worldOffset(Direction.NORTH)), Blocks.AIR);
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				if (part == broken) {
					continue;
				}
				BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
				BlockState state = stateAt(helper, cell);
				if (ConcentratorStructure.partOf(state) != null) {
					helper.fail("breaking " + broken + " left " + part + " still assembled: " + state);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * The buffer stays on the core, and the whole bottom tier lends it. No section grows a block entity,
	 * yet every outward face of a bottom cell exposes the core's own port, and the cable arm is drawn on
	 * exactly those faces — the arm and the port read one rule, so they are asserted together. The top
	 * tier and every inner face lend nothing, and neither does a section that is not part of a machine.
	 *
	 * <p>Replaces MOD-603's "only the core carries energy": a cable run around the whole base connected
	 * at one cell out of four, and the owner ruled the bottom tier the machine's socket.
	 *
	 * @implements MOD-608 — the bottom tier lends the core's port and draws the cable arm; the top tier does not
	 */
	public static void bottomTierLendsTheCorePort(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);

		// Before assembly the sections are casings: no port, no arm, on any face.
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			BlockPos cell = helper.absolutePos(CORE.offset(part.worldOffset(Direction.NORTH)));
			for (Direction face : Direction.values()) {
				if (EnergyLookup.get().find(helper.getLevel(), cell, face) != null
						|| CableBlock.shouldConnectTo(helper.getLevel(), cell.relative(face), face.getOpposite())) {
					helper.fail("loose section " + part + " offers energy or an arm on its " + face + " face");
					return;
				}
			}
		}

		assemble(helper);
		assertAssembled(helper, Direction.NORTH);
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
				instanceof RadiantSolarPanelBlockEntity core)) {
			helper.fail("the assembled core lost its block entity");
			return;
		}
		// A marker charge: a lent port must read the core's own buffer, not a copy or an empty stand-in.
		long marker = 1234L;
		core.getEnergyStorage().setAmountUntracked(marker);

		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue; // the core answers with its own one-block rules, asserted by the solar scenarios
			}
			BlockPos cell = helper.absolutePos(CORE.offset(part.worldOffset(Direction.NORTH)));
			if (helper.getLevel().getBlockEntity(cell) != null) {
				helper.fail("cell " + part + " has a block entity — the structure's buffer lives on the core");
				return;
			}
			Vec3i offset = part.canonicalOffset();
			boolean bottom = offset != null && offset.getY() == 0;
			for (Direction face : Direction.values()) {
				boolean outward = ConcentratorStructure.neighbourPart(part, Direction.NORTH, face) == null;
				boolean expected = bottom && outward;
				EnergyPort port = EnergyLookup.get().find(helper.getLevel(), cell, face);
				boolean arm = CableBlock.shouldConnectTo(helper.getLevel(), cell.relative(face), face.getOpposite());
				if ((port != null) != expected) {
					helper.fail(part + " " + face + ": expected " + (expected ? "the core's port" : "no port")
							+ " but the lookup found " + port);
					return;
				}
				if (arm != expected) {
					helper.fail(part + " " + face + ": the cable arm (" + arm + ") disagrees with the port ("
							+ expected + ") — the two must read one rule");
					return;
				}
				if (port != null && (!port.supportsExtraction() || port.getAmount() != marker)) {
					helper.fail(part + " " + face + ": the lent port is not the core's buffer (holds "
							+ port.getAmount() + ", extracts " + port.supportsExtraction() + ")");
					return;
				}
			}
		}

		// Taking the machine apart takes the lending with it: a former bottom cell is a casing again.
		helper.setBlock(CORE.offset(ConcentratorPart.TOP_BACK_RIGHT.worldOffset(Direction.NORTH)), Blocks.AIR);
		for (ConcentratorPart part : new ConcentratorPart[] {
				ConcentratorPart.RIGHT, ConcentratorPart.BACK, ConcentratorPart.BACK_RIGHT}) {
			BlockPos cell = helper.absolutePos(CORE.offset(part.worldOffset(Direction.NORTH)));
			for (Direction face : Direction.values()) {
				if (EnergyLookup.get().find(helper.getLevel(), cell, face) != null
						|| CableBlock.shouldConnectTo(helper.getLevel(), cell.relative(face), face.getOpposite())) {
					helper.fail("former " + part + " still offers the core's energy on its " + face
							+ " face after the machine was taken apart");
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * A right-click on any of the eight cells opens the core's screen, and a loose section lets the
	 * click through so the next section can be placed against it (MOD-039).
	 *
	 * @implements MOD-608 — the core's screen opens from any cell of the assembled machine
	 */
	public static void anyCellOpensTheCoreScreen(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);

		BlockPos looseCell = CORE.offset(ConcentratorPart.TOP_BACK_RIGHT.worldOffset(Direction.NORTH));
		if (click(helper, player, looseCell) != InteractionResult.PASS) {
			helper.fail("a loose section consumed the click — sections could not be placed against it");
			return;
		}

		assemble(helper);
		assertAssembled(helper, Direction.NORTH);
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			player.closeContainer();
			BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
			InteractionResult result = click(helper, player, cell);
			if (result != InteractionResult.SUCCESS) {
				helper.fail("a click on " + part + " returned " + result + " instead of opening the screen");
				return;
			}
			if (!(player.containerMenu instanceof RadiantSolarPanelMenu)) {
				helper.fail("a click on " + part + " opened " + player.containerMenu
						+ " instead of the concentrator's screen");
				return;
			}
		}
		player.closeContainer();
		helper.succeed();
	}

	/** An empty-hand right-click on the top face of {@code cell}, the way the server delivers it. */
	private static InteractionResult click(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
		BlockPos absolute = helper.absolutePos(cell);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		return stateAt(helper, cell).useWithoutItem(helper.getLevel(), player, hit);
	}

	// ── MOD-608: energy through the cells ───────────────────────────────────────────────────────────
	//
	// The rig below is laid out for the NORTH box (cells at x 3..4, z 3..4): every cable touches a
	// SECTION of the bottom tier and none touches the core, so the only way in is the lent port.

	/** Cables along the east and south sides of the bottom tier, touching RIGHT, BACK_RIGHT and BACK. */
	private static final BlockPos[] SIDE_LINE = {
		new BlockPos(5, 2, 3), new BlockPos(5, 2, 4), new BlockPos(5, 2, 5),
		new BlockPos(4, 2, 5), new BlockPos(3, 2, 5),
	};

	/** Clear the rig's cells outside the structure's own box. */
	private static void clearRig(GameTestHelper helper) {
		for (int x = 2; x <= 7; x++) {
			for (int z = 2; z <= 7; z++) {
				if (x <= 4 && z <= 4) {
					continue; // the structure's neighbourhood, cleared by clearArea
				}
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
	}

	/** Build the assembled NORTH machine with its core charged and no cable anywhere near the core. */
	private static RadiantSolarPanelBlockEntity buildChargedMachine(GameTestHelper helper, long charge) {
		clearArea(helper);
		clearRig(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);
		assemble(helper);
		assertAssembled(helper, Direction.NORTH);
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
				instanceof RadiantSolarPanelBlockEntity core)) {
			throw new IllegalStateException("the assembled core lost its block entity");
		}
		core.getEnergyStorage().setAmountUntracked(charge);
		return core;
	}

	private static long cableAmount(GameTestHelper helper, BlockPos cable) {
		return EnergyScenarioSupport.be(helper, cable) instanceof CableBlockEntity c
				? c.getEnergyStorage().getAmount() : -1L;
	}

	/**
	 * One machine pushes one packet a tick, however many of its cells a line touches. Three bottom
	 * sections touch the same copper line, and their four neighbouring cables have more room between
	 * them than one packet: counted per cell, the machine would fill them all in a single tick.
	 *
	 * <p>The line has no consumer, so everything the machine gives in the first tick is still in the
	 * cables afterwards; measuring the cables rather than the core keeps the machine's own production
	 * out of the number.
	 *
	 * @implements MOD-608 — the per-source packet cap holds per machine, not per touching cell
	 */
	public static void oneMachinePushesOnePacket(GameTestHelper helper) {
		buildChargedMachine(helper, 10_000L);
		for (BlockPos c : SIDE_LINE) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		for (BlockPos c : SIDE_LINE) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, c)); // lazy registration
		}
		NetworkManager.tickAll(helper.getLevel());

		long packetCap = CableType.COPPER.packetCap();
		long touchingRoom = 4L * CableType.COPPER.segmentBuffer(); // four cables touch a section
		if (touchingRoom <= packetCap) {
			helper.fail("rig misconfigured: the touching cables (" + touchingRoom + " EU of room) must"
					+ " exceed one packet (" + packetCap + ") or a per-cell cap cannot show");
			return;
		}
		long inLine = 0;
		for (BlockPos c : SIDE_LINE) {
			inLine += cableAmount(helper, c);
		}
		if (inLine != packetCap) {
			helper.fail("the machine put " + inLine + " EU into the line in one tick; one packet is "
					+ packetCap + " — " + (inLine > packetCap ? "each touching cell pushed its own packet"
					: "the lent port did not feed the line"));
			return;
		}
		helper.succeed();
	}

	/**
	 * A machine touched through three cells is counted once when the network decides whether batteries
	 * must cover the machines. Counted per cell, the concentrator would promise three times the energy
	 * it can give, the backup stage would see no shortfall, and a charged Energy Storage on the same
	 * line would sit idle while a hungry macerator stayed short.
	 *
	 * <p>The macerator takes a full LV packet a tick and the concentrator offers one extract's worth, so
	 * a correct count leaves a shortfall the box must cover; any EU leaving the box proves it did.
	 *
	 * @implements MOD-608 — a multiblock's supply is counted once, so storage still backs up the machines
	 */
	public static void oneMachineCountsOnceForBackupPower(GameTestHelper helper) {
		buildChargedMachine(helper, 10_000L);
		for (BlockPos c : SIDE_LINE) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// Front (inert) pointing away from the line, so the west face is a real input.
		BlockPos macerator = new BlockPos(6, 2, 4);
		helper.setBlock(macerator, ModContent.MACERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		// FACING = EAST puts the box's OUT face on its west side, against the cable at (5, 2, 5).
		BlockPos box = new BlockPos(6, 2, 5);
		helper.setBlock(box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		long boxStart = Config.batteryBoxBuffer / 2L;
		if (EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().setAmountUntracked(boxStart);
		}
		if (EnergyScenarioSupport.be(helper, macerator) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().setAmountUntracked(0L); // no input item: its buffer only fills
		}
		for (int i = 0; i < 20; i++) {
			for (BlockPos c : SIDE_LINE) {
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, macerator));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, box));
		}
		long boxEnd = EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1L;
		if (boxEnd >= boxStart) {
			helper.fail("the Energy Storage never backed the macerator up (" + boxEnd + " of " + boxStart
					+ " EU left) — the concentrator's supply was counted once per touching cell");
			return;
		}
		helper.succeed();
	}

	/**
	 * A cable meets the bottom tier low, the way it meets a solar panel, and the cell tells it how far
	 * to carry the dropped arm on into the housing. The top tier asks for nothing, and neither does a
	 * section once the machine is taken apart — the cable then stops dropping toward it.
	 *
	 * <p>The continuation itself is drawn by the cable's client renderer and cannot be seen from a
	 * server test; what is asserted here is everything that decides whether it is drawn and how long.
	 *
	 * @implements MOD-609 — a cable drops to the bottom tier and reaches into its housing
	 */
	public static void cableDropsToTheBottomTier(GameTestHelper helper) {
		buildChargedMachine(helper, 0L);
		BlockPos cell = CORE.offset(ConcentratorPart.BACK_RIGHT.worldOffset(Direction.NORTH));
		BlockPos cable = cell.east();
		helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		// Programmatic placement skips getStateForPlacement; the first tick re-derives the arms, exactly
		// as it does for a cable loaded from disk.
		EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));

		BlockState cableState = stateAt(helper, cable);
		if (!cableState.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST))) {
			helper.fail("the cable drew no arm toward the bottom tier");
			return;
		}
		if (!cableState.getValue(CableBlock.lowFlagFor(Direction.WEST))) {
			helper.fail("the arm toward the bottom tier did not drop — it would meet the housing at mid-height");
			return;
		}
		BlockState cellState = stateAt(helper, cell);
		List<CableArmReach.Band> bands = cellState.getBlock() instanceof CableArmReach arm
				? arm.cableArmReach(cellState) : List.of();
		if (bands.isEmpty()) {
			helper.fail("the bottom tier asks for no continuation — the dropped arm would stop short of the housing");
			return;
		}
		// The renderer draws the bands as one stepped sleeve: stacked without a gap over the dropped
		// sleeve's full height, each reaching in, none past half the cell.
		float height = (float) CableBlock.LOW_SLEEVE_BOTTOM;
		for (CableArmReach.Band band : bands) {
			if (band.bottom() != height || band.top() <= band.bottom()
					|| band.depth() <= 0.0F || band.depth() >= 8.0F) {
				helper.fail("the bottom tier's continuation " + bands + " is not one stepped sleeve from "
						+ CableBlock.LOW_SLEEVE_BOTTOM + " to " + CableBlock.LOW_SLEEVE_TOP
						+ " px high, reaching in less than half the cell");
				return;
			}
			height = band.top();
		}
		if (height != (float) CableBlock.LOW_SLEEVE_TOP) {
			helper.fail("the bottom tier's continuation " + bands + " stops below the top of the dropped sleeve");
			return;
		}
		BlockState coreState = stateAt(helper, CORE);
		if (!ConcentratorStructure.cableArmReach(coreState).equals(bands)) {
			helper.fail("the core asks for " + ConcentratorStructure.cableArmReach(coreState)
					+ ", the sections for " + bands + " — the bottom tier is one housing");
			return;
		}
		BlockPos topCell = CORE.offset(ConcentratorPart.TOP_BACK_RIGHT.worldOffset(Direction.NORTH));
		if (!ConcentratorStructure.cableArmReach(stateAt(helper, topCell)).isEmpty()) {
			helper.fail("a top cell asks for a cable continuation — the top tier takes no cable");
			return;
		}

		// Take the machine apart: the section is a full casing again, met at the edge like any block.
		helper.setBlock(topCell, Blocks.AIR);
		BlockState loose = stateAt(helper, cell);
		if (!ConcentratorStructure.cableArmReach(loose).isEmpty()) {
			helper.fail("a taken-apart section still asks for a cable continuation");
			return;
		}
		if (stateAt(helper, cable).getValue(CableBlock.lowFlagFor(Direction.WEST))) {
			helper.fail("the cable still drops toward a section that is no longer part of a machine");
			return;
		}
		helper.succeed();
	}

	/**
	 * A consumer wired only to a section — no cable anywhere near the core — is fed from the core's
	 * buffer. This is the defect the owner found: a cable run around the whole base was connected at
	 * the one cell the panel used to stand in.
	 *
	 * @implements MOD-608 — energy leaves the machine through a cable that touches only a section
	 */
	public static void sectionOnlyCableCarriesTheCoreEnergy(GameTestHelper helper) {
		buildChargedMachine(helper, 10_000L);
		BlockPos cable = new BlockPos(5, 2, 4); // touches BACK_RIGHT's east face only
		helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		// FACING = WEST puts the box's IN face on its west side, against the cable.
		BlockPos box = new BlockPos(6, 2, 4);
		helper.setBlock(box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().setAmountUntracked(0L);
		}
		for (int i = 0; i < 20; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));
			NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, box));
		}
		long got = EnergyScenarioSupport.be(helper, box) instanceof BatteryBoxBlockEntity bb
				? bb.getEnergyStorage().getAmount() : -1L;
		if (got <= 0L) {
			helper.fail("a box wired only to a section got " + got + " EU — the section does not lend the"
					+ " core's port");
			return;
		}
		helper.succeed();
	}

	/**
	 * Assembling must never make the machine WORSE.
	 *
	 * <p>The two knobs are equal by design — the structure is the concentrator at its proper size,
	 * not a higher tier — so this cannot demand that one exceed the other. What it can demand is the
	 * floor: whatever a future balance pass does to either number, a player who spent seven sections
	 * must not end up generating less than before they built. That is the invariant a careless edit
	 * would break, and the only one the pair genuinely has.
	 *
	 * <p>Asserted on the CONFIGURED values, not on literals: hard-coding today's 8 would turn every
	 * balance change into a red test instead of the balance change it is.
	 *
	 * @implements MOD-603 — building the structure is never a downgrade
	 */
	public static void assembledOutputIsNeverADowngrade(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);
		assemble(helper);
		assertAssembled(helper, Direction.NORTH);
		if (Config.radiantAssembledEuPerTick < Config.radiantEuPerTick) {
			helper.fail("the assembled knob (" + Config.radiantAssembledEuPerTick
					+ ") is below the one-block knob (" + Config.radiantEuPerTick
					+ ") — seven sections would buy the player a weaker machine");
		}
		helper.succeed();
	}

	/**
	 * A roof over ANY of the four columns stops the machine, and the machine does not shade itself.
	 *
	 * <p>Both halves matter and both were wrong at first. Covering three corners out of four did
	 * nothing, because only the core's own column was ever read; and once every column was read, the
	 * core found its own top cell overhead and would have run at half power under an open sky.
	 *
	 * @implements MOD-603 — sky is judged over the whole footprint, and above the structure
	 */
	public static void anyCoveredColumnStopsTheMachine(GameTestHelper helper) {
		for (ConcentratorPart covered : ConcentratorPart.CELLS) {
			Vec3i cell = covered.canonicalOffset();
			if (cell == null || cell.getY() != 1) {
				continue; // only the four top cells carry a column of their own
			}
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, Direction.NORTH, null);
			assemble(helper);
			assertAssembled(helper, Direction.NORTH);

			BlockState core = stateAt(helper, CORE);
			BlockPos absoluteCore = helper.absolutePos(CORE);
			if (!(core.getBlock() instanceof RadiantSolarPanelBlock block)) {
				helper.fail("the core stopped being a concentrator");
				return;
			}
			if (!block.isWorking(helper.getLevel(), absoluteCore, core)) {
				helper.fail("the assembled machine is idle under an open sky — it is shading itself");
				return;
			}

			BlockPos roof = CORE.offset(covered.worldOffset(Direction.NORTH)).above();
			helper.setBlock(roof, Blocks.STONE);
			if (block.isWorking(helper.getLevel(), absoluteCore, stateAt(helper, CORE))) {
				helper.fail("a roof over " + covered + " did not stop the machine");
			}
			helper.setBlock(roof, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * The middle the wings turn about is the middle of the cells the structure actually occupies.
	 *
	 * <p>This is the assertion that was missing when the mirrors flew off the machine. The structure
	 * grows to ONE side of the core, and which side depends on the facing, so its middle sits at a
	 * different place relative to the core on each of the four — {@code (1, 1)} north, {@code (0, 1)}
	 * east, {@code (0, 0)} south, {@code (1, 0)} west. A hard-coded {@code (1, 1)} looked right on
	 * north, which is the one facing every render and every earlier scenario happened to use.
	 *
	 * <p>Asserted against the CELLS, not against a second copy of the formula: the cells are where the
	 * blocks really are, so a wrong formula cannot agree with them by construction.
	 *
	 * @implements MOD-603 — the wings turn about the structure's real middle on every facing
	 */
	public static void structureCentreMatchesItsCells(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				Vec3i offset = part.worldOffset(facing);
				minX = Math.min(minX, offset.getX());
				maxX = Math.max(maxX, offset.getX());
				minZ = Math.min(minZ, offset.getZ());
				maxZ = Math.max(maxZ, offset.getZ());
			}
			// Cells are unit cubes, so the box runs from min to max + 1 and its middle is the average.
			float expectedX = (minX + maxX + 1) / 2.0f;
			float expectedZ = (minZ + maxZ + 1) / 2.0f;
			float[] centre = ConcentratorPart.structureCentre(facing);
			if (Math.abs(centre[0] - expectedX) > 1.0e-4f
					|| Math.abs(centre[1] - expectedZ) > 1.0e-4f) {
				helper.fail("facing " + facing + ": structureCentre says ("
						+ centre[0] + ", " + centre[1] + ") but the cells sit around ("
						+ expectedX + ", " + expectedZ + ")");
			}
		}
		helper.succeed();
	}

	/**
	 * The transform the wings are drawn with lands every cell exactly where its blocks stand.
	 *
	 * <p>This is the assertion that was missing while the mirrors kept flying off, and it is stated
	 * against the CELLS — where the blocks really are — rather than against a second copy of the
	 * formula, so a wrong transform cannot agree with it. It would have caught both mistakes: the
	 * hard-coded middle, and using the world middle where the canonical one belonged.
	 *
	 * @implements MOD-603 — canonical-to-world mapping of the structure, on every facing
	 */
	public static void canonicalMappingLandsOnTheCells(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				Vec3i canonical = part.canonicalOffset();
				Vec3i world = part.worldOffset(facing);
				if (canonical == null) {
					continue;
				}
				// Middle of this cell, canonical and actual: cells are unit cubes, so it is +0.5.
				float[] mapped = ConcentratorPart.canonicalToWorld(facing,
						canonical.getX() + 0.5f, canonical.getZ() + 0.5f);
				float expectedX = world.getX() + 0.5f;
				float expectedZ = world.getZ() + 0.5f;
				if (Math.abs(mapped[0] - expectedX) > 1.0e-4f
						|| Math.abs(mapped[1] - expectedZ) > 1.0e-4f) {
					helper.fail("facing " + facing + ", cell " + part + ": the drawing transform puts it"
							+ " at (" + mapped[0] + ", " + mapped[1] + ") but its block stands at ("
							+ expectedX + ", " + expectedZ + ")");
				}
			}
		}
		helper.succeed();
	}

	/** Air out every cell any rotation could touch, plus the core. */
	private static void clearArea(GameTestHelper helper) {
		for (int dx = -1; dx <= 1; dx++) {
			// Up to dy = 2: the roofs this suite lays down sit one block above the top cells, and a
			// leftover roof would make the next rotation look like a machine that never starts.
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					helper.setBlock(CORE.offset(dx, dy, dz), Blocks.AIR);
				}
			}
		}
	}
}
