package dev.alaindustrial.gametest;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite for MOD-039 "cable cannot be placed flush against another cable". Before the fix,
 * {@code AbstractMachineBlock.useWithoutItem} returned {@link InteractionResult#SUCCESS} for every
 * right-click, so vanilla's {@code ServerPlayerGameMode} treated the click as consumed and never
 * reached {@code BlockItem.place} — making it impossible to extend a cable run by aiming at an
 * existing cable (player had to hold Shift or aim at a different block).
 *
 * <p>This is the first test in the repo that simulates "right-click with item in hand on a block":
 * {@code GameTestHelper.setBlock} bypasses {@code BlockItem} entirely (it goes straight to the
 * chunk), so it cannot catch this bug. Instead we replay what {@code ServerPlayerGameMode.useItemOn}
 * does at the placement point: build a {@link UseOnContext} from a player + a {@link BlockHitResult}
 * aimed at the existing cable, then invoke {@code stack.useOn(ctx)} — which is exactly the call
 * vanilla makes once the block returns PASS.
 *
 * <p>Verified against MC 26.2 (mojmap) via the bytecode of {@code ServerPlayerGameMode.useItemOn}:
 * when {@code useWithoutItem} returns PASS (consumesAction()==false) the chain falls through to
 * {@code ItemStack.useOn(ctx)} → {@code BlockItem.place} (see {@code task.md}, MOD-039, step 0).
 * All players are created with {@link GameTestHelper#makeMockServerPlayerInLevel()} because the
 * machine test triggers {@code player.openMenu(...)}, which needs a real network handler.
 */
public class CablePlacementGameTest {

	/** Existing cable the player aims at. */
	private static final BlockPos CABLE = new BlockPos(1, 2, 1);
	/** Adjacent cell (+X) where the new cable should land when aiming at CABLE's EAST face. */
	private static final BlockPos ADJACENT = new BlockPos(2, 2, 1);

	/**
	 * @implements MOD-039-CONTRACT-CABLE — a cable is a non-interactive block: right-clicking it
	 *     with an empty hand must return PASS (not SUCCESS), so the click is not "consumed" and
	 *     placement can proceed. Regression guard for the useWithoutItem fix.
	 * @covers R-PLACE-01
	 */
	@GameTest
	public void mod039_cableUseReturnsPass(GameTestHelper helper) {
		helper.setBlock(CABLE, ModBlocks.COPPER_CABLE);
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CABLE));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockHitResult hit = hitOnCable(helper);
		InteractionResult result = state.useWithoutItem(helper.getLevel(), player, hit);
		if (result != InteractionResult.PASS) {
			helper.fail("cable useWithoutItem should be PASS (was " + result
					+ "); RMB is consumed and placement is blocked");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-039-CONTRACT-MENU — machines with a menu still return SUCCESS on right-click
	 *     (so their GUI keeps opening). Regression guard ensuring the useWithoutItem fix did not
	 *     silence interactive blocks. Battery box was chosen because its block entity implements
	 *     MenuProvider and it is the simplest GUI-bearing machine in the mod.
	 * @covers R-PLACE-02
	 */
	@GameTest
	public void mod039_machineUseReturnsSuccess(GameTestHelper helper) {
		helper.setBlock(CABLE, ModBlocks.BATTERY_BOX);
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CABLE));
		// makeMockServerPlayerInLevel(): openMenu needs a real ServerPlayer with a network handler.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockHitResult hit = hitOnCable(helper);
		InteractionResult result = state.useWithoutItem(helper.getLevel(), player, hit);
		if (result != InteractionResult.SUCCESS) {
			helper.fail("battery_box useWithoutItem should be SUCCESS (was " + result
					+ "); GUI-bearing blocks must stay interactive");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-039-FULL — the actual bug scenario: with a cable in hand, right-click an
	 *     existing cable → a new cable segment must be placed in the adjacent cell, without Shift
	 *     and without aiming at a different block. Replays the {@code ItemStack.useOn(ctx)} step
	 *     that vanilla reaches once the cable returns PASS.
	 * @covers R-PLACE-03
	 */
	@GameTest
	public void mod039_rmbOnCablePlacesAdjacent(GameTestHelper helper) {
		// Place the first cable (setBlock is fine here — it is only the *target* of the click).
		helper.setBlock(CABLE, ModBlocks.COPPER_CABLE);
		helper.assertBlockPresent(ModBlocks.COPPER_CABLE, CABLE);

		// Player aims at CABLE's EAST face (hit direction = EAST). Vanilla placement rule
		// (BlockPlaceContext): because a cable is not canBeReplaced, the new block lands in
		// hitPos.relative(EAST) = ADJACENT. (direction EAST ⇒ relative +X.)
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack cableStack = new ItemStack(ModItems.COPPER_CABLE_ITEM);
		BlockHitResult hit = hitOnCable(helper);

		// This is the exact call ServerPlayerGameMode.useItemOn makes once useWithoutItem returned
		// PASS (bytecode offset ~292 in 26.2): stack.useOn(ctx) → BlockItem.useOn → BlockItem.place.
		UseOnContext ctx = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
		cableStack.useOn(ctx);

		helper.assertBlockPresent(ModBlocks.COPPER_CABLE, ADJACENT);
		helper.succeed();
	}

	/**
	 * @implements MOD-038 — a copper cable must NOT draw a connection arm toward an iron chest. The
	 *     chest inherits {@code AbstractMachineBlock} (for {@code facing} + the right-click-to-open
	 *     hook), so before the fix {@code connectsTo} saw it as "a machine" and drew a misleading
	 *     "energy goes here" arm. The fix is the {@code AbstractMachineBlock#isCableConnectable()}
	 *     marker, which the chest overrides to {@code false}. Because the cable ghost preview (Fabric
	 *     {@code CablePlacementPreview} / NeoForge {@code NeoForgeCableGhost}) calls the same
	 *     {@code getStateForPlacement} -> {@code connectsTo} path, fixing the blockstate here fixes
	 *     the preview too — no client-side test is possible (GameTest is server-side).
	 *
	 * <p>Layout: cable at CABLE (1,2,1), chest one block west at CHEST (0,2,1), battery box one block
	 * east at BATTERY (2,2,1). The chest is placed LAST so vanilla's block-update notifies the cable
	 * and {@code updateShape} recomputes the WEST/EAST connection flags. Assertions check both sides
	 * of the cable: WEST (chest) must be {@code false}, EAST (battery box, an energy block) must be
	 * {@code true} — the EAST assertion is a positive control that proves {@code updateShape} did run
	 * (otherwise both would stay {@code false}, the default, and the EAST check would fail).
	 *
	 * <p>The battery box is oriented {@code FACING=WEST} so its input face ({@code FACING}) points
	 * toward the cable: the cable touches the box's WEST face, which is the box's front (input) face
	 * — a connectable face. With a default {@code FACING=NORTH} the touched WEST face would be a side
	 * (inert), and the arm would correctly be {@code false} (see
	 * {@code cableConnectsOnlyToBatteryBoxIOFaces}), which would defeat this test's positive control.
	 */
	@GameTest
	public void mod038_cableDoesNotConnectToIronChest(GameTestHelper helper) {
		BlockPos cablePos = new BlockPos(1, 2, 1);
		BlockPos chestPos = new BlockPos(0, 2, 1);   // WEST of the cable
		BlockPos batteryPos = new BlockPos(2, 2, 1); // EAST of the cable

		helper.setBlock(cablePos, ModBlocks.COPPER_CABLE);
		// FACING=WEST: the box's front (input) face points at the cable on its west side.
		helper.setBlock(batteryPos, ModBlocks.BATTERY_BOX.defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		helper.setBlock(chestPos, ModBlocks.IRON_CHEST); // placed last -> notifies the cable

		BlockState cable = helper.getLevel().getBlockState(helper.absolutePos(cablePos));
		boolean armToChest = cable.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST));
		boolean armToBattery = cable.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST));

		if (armToChest) {
			helper.fail("Cable must NOT connect to iron_chest (WEST=" + armToChest
					+ "); chest has no energy port — MOD-038 regression");
		}
		if (!armToBattery) {
			helper.fail("Cable SHOULD connect to battery_box (EAST=" + armToBattery
					+ "); positive control failed — updateShape did not run or energy blocks regressed");
		}
		helper.succeed();
	}

	/** A hit aimed at CABLE's +X (EAST) face: the click targets CABLE and would place at ADJACENT. */
	private static BlockHitResult hitOnCable(GameTestHelper helper) {
		return new BlockHitResult(
				Vec3.atCenterOf(helper.absolutePos(CABLE)), Direction.EAST, helper.absolutePos(CABLE), false);
	}

	/**
	 * @implements TC-WINDMILL-CONNECT — a copper cable must draw a connection arm <b>only</b> toward the
	 *     wind mill's back face ({@code FACING.getOpposite()}, the single energy output). Before the
	 *     fix, {@code CableBlock.connectsTo} decided by block type alone, so it drew a misleading arm
	 *     toward every face — even the inert front (rotor), the four sides and the top/bottom — even
	 *     though no EU can flow there. The fix is the face-aware
	 *     {@code AbstractMachineBlock#isCableConnectable(BlockState, Direction)}, which the wind mill
	 *     overrides to allow only the back face.
	 *
	 * <p>Layout: wind mill at WIND (2,2,2) with default {@code FACING=NORTH} (so back/output face is
	 * SOUTH, i.e. +Z). One cable touches each of the mill's six faces. The wind mill is placed LAST so
	 * vanilla's block-update notifies each cable and {@code updateShape} recomputes its connection flag
	 * (mirrors {@code mod038_cableDoesNotConnectToIronChest}). The cable on the mill's SOUTH (+Z) side
	 * touches the output face → its arm toward the mill must be {@code true} (also the positive control
	 * that proves {@code updateShape} ran); the other five cables' arms toward the mill must be
	 * {@code false}.
	 *
	 * <p>{@code dirToMill} is the direction <b>from the cable toward the mill</b>, i.e. the
	 * {@code PipeBlock.PROPERTY_BY_DIRECTION} key on the cable's own state — opposite of the mill face
	 * it touches (e.g. the +Z cable looks NORTH at the mill).
	 *
	 * @covers R-NRG-03 (wind mill single back-face output), docs/blocks/generators/wind_mill.md §Energy
	 */
	@GameTest
	public void cableConnectsOnlyToWindMillBackFace(GameTestHelper helper) {
		BlockPos wind = new BlockPos(2, 2, 2); // FACING=NORTH → back/output face is SOUTH (+Z)
		// One cable per wind mill face. dirToMill = direction FROM the cable TOWARD the mill (the cable's
		// own PipeBlock connection key). The +Z cable touches the SOUTH/output face → expect true.
		record CableFace(BlockPos cablePos, Direction dirToMill, boolean expectArm) {
		}
		List<CableFace> rig = List.of(
				new CableFace(new BlockPos(2, 2, 3), Direction.NORTH, true),  // +Z side → mill's SOUTH/output face
				new CableFace(new BlockPos(2, 2, 1), Direction.SOUTH, false), // −Z side → mill's NORTH/front (rotor)
				new CableFace(new BlockPos(3, 2, 2), Direction.WEST, false),  // +X side → mill's EAST side
				new CableFace(new BlockPos(1, 2, 2), Direction.EAST, false),  // −X side → mill's WEST side
				new CableFace(new BlockPos(2, 3, 2), Direction.DOWN, false),  // +Y side → mill's top
				new CableFace(new BlockPos(2, 1, 2), Direction.UP, false));   // −Y side → mill's bottom

		// Place cables first, then the mill last so each cable gets a neighbour-changed update.
		for (CableFace cf : rig) {
			helper.setBlock(cf.cablePos(), ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(wind, ModBlocks.WIND_MILL);

		for (CableFace cf : rig) {
			BlockState cableState = helper.getLevel().getBlockState(helper.absolutePos(cf.cablePos()));
			boolean arm = cableState.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(cf.dirToMill()));
			if (arm != cf.expectArm()) {
				helper.fail("Wind mill cable arm toward mill (" + cf.dirToMill() + ") = " + arm
						+ ", expected " + cf.expectArm() + " (only the +Z/output face connects); TC-WINDMILL-CONNECT");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-WATERMILL-CONNECT — the same single back-face contract as the wind mill, applied to
	 *     the water mill (MOD-194). {@code WaterMillBlockEntity#energyRoleForFace} emits EU only from the
	 *     face opposite {@code FACING}; the front carries the wheel and the four sides stand in the water,
	 *     all inert. The block, however, had no {@code isCableConnectable} override and inherited
	 *     {@link HorizontalMachineBlock}'s "everything except FACING connects", so a cable drew a
	 *     misleading arm toward the two sides, the top and the bottom — four faces through which no EU
	 *     can ever flow. Same defect class as MOD-038 (iron chest) and the wind mill fix above; MOD-179
	 *     corrected the block entity's face roles but left the block behind.
	 *
	 * <p>Layout mirrors {@code cableConnectsOnlyToWindMillBackFace} exactly: mill at (2,2,2) with default
	 * {@code FACING=NORTH}, so the output face is SOUTH (+Z). One cable per face, mill placed LAST so each
	 * cable gets a neighbour-changed update and recomputes its connection flag. The +Z cable is the
	 * positive control proving {@code updateShape} ran at all; the other five must stay unconnected.
	 *
	 * @covers R-NRG-03 (water mill single back-face output), docs/blocks/generators/water_mill.md §Energy
	 */
	@GameTest
	public void cableConnectsOnlyToWaterMillBackFace(GameTestHelper helper) {
		BlockPos mill = new BlockPos(2, 2, 2); // FACING=NORTH → back/output face is SOUTH (+Z)
		record CableFace(BlockPos cablePos, Direction dirToMill, boolean expectArm) {
		}
		List<CableFace> rig = List.of(
				new CableFace(new BlockPos(2, 2, 3), Direction.NORTH, true),  // +Z side → mill's SOUTH/output face
				new CableFace(new BlockPos(2, 2, 1), Direction.SOUTH, false), // −Z side → mill's NORTH/front (wheel)
				new CableFace(new BlockPos(3, 2, 2), Direction.WEST, false),  // +X side → mill's EAST side
				new CableFace(new BlockPos(1, 2, 2), Direction.EAST, false),  // −X side → mill's WEST side
				new CableFace(new BlockPos(2, 3, 2), Direction.DOWN, false),  // +Y side → mill's top
				new CableFace(new BlockPos(2, 1, 2), Direction.UP, false));   // −Y side → mill's bottom

		for (CableFace cf : rig) {
			helper.setBlock(cf.cablePos(), ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(mill, ModBlocks.WATER_MILL);

		for (CableFace cf : rig) {
			BlockState cableState = helper.getLevel().getBlockState(helper.absolutePos(cf.cablePos()));
			boolean arm = cableState.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(cf.dirToMill()));
			if (arm != cf.expectArm()) {
				helper.fail("Water mill cable arm toward mill (" + cf.dirToMill() + ") = " + arm
						+ ", expected " + cf.expectArm() + " (only the +Z/output face connects); TC-WATERMILL-CONNECT");
			}
		}
		helper.succeed();
	}

	/**
	 * @implements TC-BATTERYBOX-CONNECT — a copper cable must draw a connection arm <b>only</b> toward the
	 *     battery box's two IO faces: the front ({@code FACING}, charge input) and the back
	 *     ({@code FACING.getOpposite()}, discharge output). The four sides, top and bottom are inert —
	 *     no cable arm, matching {@code BatteryBoxBlockEntity#energyRoleForFace} and the MOD-006
	 *     single-axis IO layout. Before the face-aware {@code isCableConnectable} fix the cable drew a
	 *     misleading arm toward every face.
	 *
	 * <p>Layout: battery box at BOX (2,2,2) with default {@code FACING=NORTH}, so front/input = NORTH
	 * (−Z) and back/output = SOUTH (+Z). One cable touches each of its six faces; the box is placed
	 * LAST so vanilla's block-update notifies each cable and {@code updateShape} recomputes the flags.
	 * The two axial cables' arms toward the box must be {@code true} (positive control that proves
	 * {@code updateShape} ran), the other four {@code false}.
	 *
	 * @covers R-NRG-03 (battery box single-axis IO, front/back faces only)
	 */
	@GameTest
	public void cableConnectsOnlyToBatteryBoxIOFaces(GameTestHelper helper) {
		BlockPos box = new BlockPos(2, 2, 2); // FACING=NORTH → front/input = NORTH (−Z), back/output = SOUTH (+Z)
		// One cable per box face. dirToBox = direction FROM the cable TOWARD the box (the cable's own
		// PipeBlock connection key). The −Z and +Z cables touch the front/back IO faces → expect true.
		record CableFace(BlockPos cablePos, Direction dirToBox, boolean expectArm) {
		}
		List<CableFace> rig = List.of(
				new CableFace(new BlockPos(2, 2, 1), Direction.SOUTH, true),  // −Z side → box's NORTH/front (input)
				new CableFace(new BlockPos(2, 2, 3), Direction.NORTH, true),  // +Z side → box's SOUTH/back (output)
				new CableFace(new BlockPos(3, 2, 2), Direction.WEST, false),  // +X side → box's EAST side
				new CableFace(new BlockPos(1, 2, 2), Direction.EAST, false),  // −X side → box's WEST side
				new CableFace(new BlockPos(2, 3, 2), Direction.DOWN, false),  // +Y side → box's top
				new CableFace(new BlockPos(2, 1, 2), Direction.UP, false));   // −Y side → box's bottom

		// Place cables first, then the box last so each cable gets a neighbour-changed update.
		for (CableFace cf : rig) {
			helper.setBlock(cf.cablePos(), ModBlocks.COPPER_CABLE);
		}
		helper.setBlock(box, ModBlocks.BATTERY_BOX);

		for (CableFace cf : rig) {
			BlockState cableState = helper.getLevel().getBlockState(helper.absolutePos(cf.cablePos()));
			boolean arm = cableState.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(cf.dirToBox()));
			if (arm != cf.expectArm()) {
			helper.fail("Battery box cable arm toward box (" + cf.dirToBox() + ") = " + arm
					+ ", expected " + cf.expectArm() + " (only −Z/front + +Z/back IO faces connect); TC-BATTERYBOX-CONNECT");
			}
		}
		helper.succeed();
	}

	// --- MOD-061: cable must not arm toward a horizontal machine's FACING (front) face -----------

	/**
	 * Shared rig for the six MOD-061 target machines (generator, geothermal, macerator, electric
	 * furnace, extractor, compressor) and the pump exception. Each of a block's six faces gets a
	 * cable; the block is placed LAST so each cable gets a neighbour-changed update and
	 * {@code updateShape} recomputes its flag. {@code facingArm} is the expected arm on the FACING
	 * face: {@code false} for the six machines (front is energy-inert, R-NRG-03), {@code true} for
	 * the pump (its front is the fluid-intake + energy-IN face, so it cancels the default). The five
	 * non-FACING faces are always expected to arm — both the six machines and the pump expose energy
	 * on their sides — so only the FACING expectation varies.
	 *
	 * <p>Layout: machine at {@code (2,2,2)} with the given {@code facing} (default NORTH for most,
	 * EAST for the non-NORTH regression). One cable touches each of the six faces. With
	 * {@code FACING=NORTH}, the front face is the machine's NORTH (−Z) side, so the −Z cable's arm
	 * toward the machine (its {@code SOUTH} key) must be {@code facingArm}.
	 *
	 * <p>{@code dirToMachine} is the direction <b>from the cable toward the machine</b>, i.e. the
	 * {@code PipeBlock.PROPERTY_BY_DIRECTION} key on the cable's own state — mirrors
	 * {@code cableConnectsOnlyToWindMillBackFace}.
	 */
	private static void assertArmsAround(GameTestHelper helper, Block machine, Direction facing,
			boolean facingArm) {
		BlockPos machinePos = new BlockPos(2, 2, 2);
		// For each of the six faces: cable pos, the cable's direction-key toward the machine, and
		// which face of the machine that cable touches. Expected arm = (machineFace == facing) ? facingArm : true.
		record CableFace(BlockPos cablePos, Direction dirToMachine, Direction machineFace) {
		}
		java.util.List<CableFace> rig = List.of(
				new CableFace(new BlockPos(2, 2, 1), Direction.SOUTH, Direction.NORTH),
				new CableFace(new BlockPos(2, 2, 3), Direction.NORTH, Direction.SOUTH),
				new CableFace(new BlockPos(3, 2, 2), Direction.WEST, Direction.EAST),
				new CableFace(new BlockPos(1, 2, 2), Direction.EAST, Direction.WEST),
				new CableFace(new BlockPos(2, 3, 2), Direction.DOWN, Direction.UP),
				new CableFace(new BlockPos(2, 1, 2), Direction.UP, Direction.DOWN));
		// Place cables first, then the machine LAST so each cable gets a neighbour-changed update.
		for (CableFace cf : rig) {
			helper.setBlock(cf.cablePos(), ModBlocks.COPPER_CABLE);
		}
		BlockState machineState = machine.defaultBlockState();
		if (machineState.hasProperty(HorizontalMachineBlock.FACING)) {
			machineState = machineState.setValue(HorizontalMachineBlock.FACING, facing);
		}
		helper.setBlock(machinePos, machineState);

		for (CableFace cf : rig) {
			boolean expected = cf.machineFace() == facing ? facingArm : true;
			BlockState cableState = helper.getLevel().getBlockState(helper.absolutePos(cf.cablePos()));
			boolean arm = cableState.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(cf.dirToMachine()));
			if (arm != expected) {
				helper.fail(machine + " cable arm toward machine (" + cf.dirToMachine()
						+ ", machine face " + cf.machineFace() + ", FACING=" + facing + ") = " + arm
						+ ", expected " + expected + " — MOD-061 FACING-inert regression");
			}
		}
		helper.succeed();
	}

	/** @implements TC-GEN-001-CON01 — generator front (FACING) face is energy-inert; cable must not arm there. */
	@GameTest
	public void mod061_cableDoesNotArmToGeneratorFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.GENERATOR, Direction.NORTH, false);
	}

	/** @implements MOD-061 — geothermal generator front (FACING) face is energy-inert. */
	@GameTest
	public void mod061_cableDoesNotArmToGeothermalFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.GEOTHERMAL_GENERATOR, Direction.NORTH, false);
	}

	/** @implements TC-MACH-001-CON04 — macerator front (FACING) face is energy-inert. */
	@GameTest
	public void mod061_cableDoesNotArmToMaceratorFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.MACERATOR, Direction.NORTH, false);
	}

	/** @implements MOD-061 — electric furnace front (FACING) face is energy-inert. */
	@GameTest
	public void mod061_cableDoesNotArmToElectricFurnaceFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.ELECTRIC_FURNACE, Direction.NORTH, false);
	}

	/** @implements MOD-061 — extractor front (FACING) face is energy-inert. */
	@GameTest
	public void mod061_cableDoesNotArmToExtractorFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.EXTRACTOR, Direction.NORTH, false);
	}

	/** @implements MOD-061 — compressor front (FACING) face is energy-inert. */
	@GameTest
	public void mod061_cableDoesNotArmToCompressorFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.COMPRESSOR, Direction.NORTH, false);
	}

	/**
	 * Regression guard against the classic bug where the override compares {@code side == NORTH}
	 * (a hardcoded direction) instead of {@code side == state.getValue(FACING)}, or confuses
	 * {@code getOpposite()}. With {@code FACING=EAST}, the EAST cable's arm must be {@code false}
	 * (front), while the NORTH cable's arm must be {@code true} (side). Mirrors the all-four-facing
	 * sweep in {@code EnergyFaceGameTest.rNrg03_batteryBoxAllFacingDirections}; here one non-default
	 * facing is enough as a regression guard.
	 */
	@GameTest
	public void mod061_cableFacingInertOnNonNorthFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.GENERATOR, Direction.EAST, false);
	}

	/**
	 * @implements MOD-061 (pump) — the pump is no longer an exception: its front ({@code FACING}) face
	 *     is energy-inert just like the other six machines, so a cable arms toward the five working
	 *     faces only. Fluid intake is a separate subsystem and still reads {@code FACING} directly, so
	 *     this does not change which way the pump draws fluid from — only the energy/cable contract.
	 */
	@GameTest
	public void mod061_cableDoesNotArmToPumpFacing(GameTestHelper helper) {
		assertArmsAround(helper, ModBlocks.PUMP, Direction.NORTH, false);
	}

	/**
	 * @implements MOD-061 migration — the connection flags saved in chunk palette NBT go stale when
	 *     {@code isCableConnectable} semantics change. The once-per-load {@code validateShape} on
	 *     {@link dev.alaindustrial.block.entity.CableBlockEntity} must re-derive them on the first
	 *     server tick. This covers the LOGIC + once-semantics: a cable whose every flag was forced
	 *     to {@code true} against a machine with an inert front face must drop the FACING flag after
	 *     one {@code serverTick}; a second {@code serverTick} must be a no-op (the once-flag holds).
	 *
	 * <p>The on-load TRIGGER itself ({@code shapeValidated == false} after chunk reload) is not
	 *     testable here — the suite has no chunk save/reload infrastructure — so this drives
	 *     {@code serverTick} directly the way {@code MachineGameTest.drive} does. The trigger path
	 *     is covered by manual playtest (see task MOD-061 acceptance).
	 */
	@GameTest
	public void mod061_cableStaleFlagsReshapeOnFirstTick(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(2, 2, 2);
		BlockPos cablePos = new BlockPos(2, 2, 1); // −Z of the generator → touches its NORTH face
		// Place the generator first (FACING=NORTH → front/NORTH is inert), then the cable.
		helper.setBlock(genPos, ModBlocks.GENERATOR);
		helper.setBlock(cablePos, ModBlocks.COPPER_CABLE);
		// Force every connection flag of the cable to true, simulating a stale pre-MOD-061 state
		// saved in chunk palette NBT: the SOUTH key (toward the generator's inert NORTH face) is the
		// one we expect validateShape to flip back to false.
		BlockState stale = helper.getLevel().getBlockState(helper.absolutePos(cablePos));
		for (Direction dir : Direction.values()) {
			stale = stale.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), true);
		}
		helper.setBlock(cablePos, stale);

		dev.alaindustrial.block.entity.CableBlockEntity cable = helper.getBlockEntity(cablePos,
				dev.alaindustrial.block.entity.CableBlockEntity.class);
		if (cable == null) {
			helper.fail("cable block entity missing at " + cablePos);
			return;
		}
		// First serverTick triggers validateShape → stale flags reconciled with connectsTo.
		cable.serverTick(helper.getLevel(), cable.getBlockPos(),
				helper.getLevel().getBlockState(helper.absolutePos(cablePos)));
		BlockState afterFirst = helper.getLevel().getBlockState(helper.absolutePos(cablePos));
		boolean armToFront = afterFirst.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH));
		if (armToFront) {
			helper.fail("stale SOUTH arm toward generator's inert NORTH/FACING face was not cleared"
					+ " after first serverTick; validateShape did not run — MOD-061 migration");
			return;
		}
		// Second serverTick must be a no-op: once-flag holds, no further setBlock. If the flag never
		// set, validateShape would re-run every tick (silent per-tick perf regression) — catching
		// this requires comparing state before/after, since setBlock(updateClients) is otherwise silent.
		cable.serverTick(helper.getLevel(), cable.getBlockPos(), afterFirst);
		BlockState afterSecond = helper.getLevel().getBlockState(helper.absolutePos(cablePos));
		if (!afterFirst.equals(afterSecond)) {
			helper.fail("cable state changed on the SECOND serverTick — once-semantics broken"
					+ " (shapeValidated never set), per-tick revalidate perf regression — MOD-061");
			return;
		}
		helper.succeed();
	}

	// --- MOD-071: cable must not arm toward a solar panel's UP (working) face ---------------------

	/**
	 * Shared driver for the three MOD-071 panel cases. The panels are <b>not</b> {@code Horizontal}
	 * (no {@code FACING} property), so they reuse {@link #assertArmsAround} with the inert face passed
	 * as {@code facing}: passing {@code UP} as the "facing" makes {@code assertArmsAround} expect
	 * {@code false} on the {@code UP} face (inert daylight/moonlight surface) and {@code true} on the
	 * five other faces — exactly the MOD-071 contract. The helper's {@code hasProperty(FACING)} guard
	 * skips the orientation step, so the block is placed default (symmetric, no rotation).
	 *
	 * <p>See {@link dev.alaindustrial.block.AbstractSolarPanelBlock} for why the override lives on the
	 * shared base of the three panels (sibling to MOD-061, but per-block because panels are not
	 * Horizontal).
	 */
	private static void assertArmsAroundPanel(GameTestHelper helper, Block panel) {
		assertArmsAround(helper, panel, Direction.UP, false);
	}

	/**
	 * @implements MOD-071 — a copper cable must NOT draw a connection arm toward a solar panel's
	 *     {@code UP} face: {@code UP} is the daylight capture surface and is energy-inert
	 *     ({@code SolarPanelBlockEntity#energyRoleForFace(UP) == NONE}); EU flows from the other five
	 *     faces only. Before the {@code AbstractSolarPanelBlock#isCableConnectable} override, the cable
	 *     inherited the all-faces-true default and drew a misleading "energy goes here" arm toward
	 *     {@code UP}.
	 * @covers R-NRG-03 (solar panel 5-side output, UP = working surface)
	 */
	@GameTest
	public void mod071_cableDoesNotArmToSolarPanelUp(GameTestHelper helper) {
		assertArmsAroundPanel(helper, ModBlocks.SOLAR_PANEL);
	}

	/** @implements MOD-071 — Daylight Solar Panel T2, same UP-inert contract as the base panel. */
	@GameTest
	public void mod071_cableDoesNotArmToDaylightSolarPanelUp(GameTestHelper helper) {
		assertArmsAroundPanel(helper, ModBlocks.DAYLIGHT_SOLAR_PANEL);
	}

	/** @implements MOD-071 — Moonlit Solar Panel (night mirror), same UP-inert contract. */
	@GameTest
	public void mod071_cableDoesNotArmToMoonlitSolarPanelUp(GameTestHelper helper) {
		assertArmsAroundPanel(helper, ModBlocks.MOONLIT_SOLAR_PANEL);
	}

	/**
	 * @implements TC-CABLE-FACE-PARITY — the class-wide guard (MOD-199). Every per-block test above
	 *     was written by hand, which is exactly why the water mill slipped through until MOD-194:
	 *     a new block simply never got one. This sweeps the block registry instead, so coverage no
	 *     longer depends on anyone remembering. Body lives in {@code common} and runs on both
	 *     loaders.
	 * @covers R-NRG-03 (inert faces stay inert), MOD-038 / MOD-061 / MOD-194 defect class
	 */
	@GameTest
	public void mod199_inertFacesRejectCableArms(GameTestHelper helper) {
		CableFaceParityScenarios.inertFacesRejectCableArms(helper);
	}
}
