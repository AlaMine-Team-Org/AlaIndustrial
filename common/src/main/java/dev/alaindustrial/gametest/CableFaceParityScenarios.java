package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AbstractMachineBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MOD-199 — cross-loader guard for the whole "cable arm to a dead face" defect class.
 *
 * <p>A block declares its per-face energy roles in its block entity ({@code energyRoleForFace}) and
 * its cable attachment policy in the block class ({@code isCableConnectable}). Nothing tied the two
 * together, so they could drift: the cable drew a connection arm toward a face no EU can ever leave,
 * the player saw a joint that looked correct, and no energy arrived. This has now happened three
 * times — MOD-038 (iron chest), MOD-061, and MOD-194 (water mill, the only block in its family
 * without the override, missed because MOD-179 narrowed the face roles but left the block alone).
 *
 * <p>The reason it keeps recurring is that coverage was written by hand, one test per block:
 * {@code CablePlacementGameTest} pinned four blocks out of roughly fifteen candidates, and a new
 * block simply never got a test. This sweep covers whatever the mod registers, the moment it exists
 * — including a block nobody remembered to add to {@code ModContent}.
 *
 * <p><b>This COMPLEMENTS the per-block tests, it does not replace them.</b> The sweep calls the
 * {@code isCableConnectable} predicate directly; it never places a cable and never reads the cable's
 * own blockstate. The in-world path that actually draws the arm — {@code CableBlock.connectsTo} plus
 * the MOD-061 re-derivation in {@code CableBlockEntity.validateShape} — is only covered by the
 * hand-written tests, which place a cable, place the machine last so {@code updateShape} fires, and
 * assert the cable's connection flag with an explicit positive control. Deleting those in favour of
 * this sweep would be a net loss.
 *
 * <p><b>The invariant is one-directional on purpose.</b> It asserts only that an INERT face
 * ({@link EnergyRole#NONE}) must not accept a cable arm. Today strict equality would in fact pass on
 * every swept block (the chests, which do refuse cables while inheriting non-NONE roles, never reach
 * the comparison — their block entities are not {@link MachineBlockEntity} and the sweep filters
 * them out earlier). The weaker form is deliberate future-proofing: refusing a cable on a live face
 * is a legitimate design choice a block may make — the chests already make it — whereas inviting a
 * cable to a dead face is never anything but a bug. Guarding only the direction that misleads the
 * player keeps this test free of false alarms, which is what decides whether a guard survives.
 */
public final class CableFaceParityScenarios {
	private CableFaceParityScenarios() { }

	/** Where each candidate block is placed in turn. */
	private static final BlockPos PROBE = new BlockPos(1, 2, 1);

	/**
	 * Floor under the sweep size. Currently 21 blocks qualify; 10 leaves room for real removals while
	 * still catching a broken filter or registry scan.
	 */
	private static final int MIN_EXPECTED_BLOCKS = 10;

	/**
	 * Sweeps every block registered under the mod's namespace and fails if any face that reports
	 * {@link EnergyRole#NONE} still accepts a cable arm. Reports every offending block/face/orientation
	 * in one message rather than stopping at the first, so a single run tells the whole story.
	 */
	public static void inertFacesRejectCableArms(GameTestHelper helper) {
		List<String> violations = new ArrayList<>();
		int blocksChecked = 0;
		int facesChecked = 0;

		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (!(block instanceof AbstractMachineBlock machineBlock)) {
				continue; // ores, decoration, anything without the cable-attachment contract
			}

			boolean counted = false;
			for (BlockState probeState : orientations(block)) {
				helper.setBlock(PROBE, probeState);
				BlockState placed = helper.getLevel().getBlockState(helper.absolutePos(PROBE));
				// A block that silently failed to place would leave AIR, produce no block entity and be
				// skipped as "nothing to compare" — the same shape of green-because-it-never-looked
				// failure the floor check guards against, one level down. Catch it explicitly.
				if (placed.getBlock() != block) {
					helper.fail("MOD-199 could not place `" + id.getPath() + "` at the probe position;"
							+ " the sweep cannot vouch for that block; TC-CABLE-FACE-PARITY");
				}
				BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(PROBE));
				if (!(blockEntity instanceof MachineBlockEntity machineEntity)) {
					helper.setBlock(PROBE, Blocks.AIR);
					break; // no per-face roles to compare against (chests, plain containers)
				}

				if (!counted) {
					blocksChecked++;
					counted = true;
				}
				for (Direction face : Direction.values()) {
					facesChecked++;
					boolean acceptsArm = machineBlock.isCableConnectable(placed, face);
					boolean inert = machineEntity.energyRoleForFace(face) == EnergyRole.NONE;
					if (acceptsArm && inert) {
						violations.add(id.getPath() + "[" + facingOf(placed) + "]/" + face.getName());
					}
				}
				helper.setBlock(PROBE, Blocks.AIR);
			}
		}

		// Report violations before the floor: if a real violation coexists with a shrunken sweep, the
		// concrete offender is far more useful than "the sweep looks small".
		if (!violations.isEmpty()) {
			helper.fail("MOD-199: cable arm accepted on " + violations.size() + " inert face(s) — "
					+ String.join(", ", violations)
					+ ". Such a face draws a joint the player reads as working while no EU can pass;"
					+ " override isCableConnectable(state, face) on the block to match"
					+ " energyRoleForFace, as the wind mills and the water mill (MOD-194) do;"
					+ " TC-CABLE-FACE-PARITY");
		}
		// A sweep that silently matched nothing would be the perfect fake guard: green forever because
		// it never looked at anything. Pin a floor instead of trusting the loop ran.
		if (blocksChecked < MIN_EXPECTED_BLOCKS) {
			helper.fail("MOD-199 sweep inspected only " + blocksChecked + " blocks (" + facesChecked
					+ " faces) with per-face energy roles — expected at least " + MIN_EXPECTED_BLOCKS
					+ ". The registry scan or the block filter is broken, so this test proves nothing;"
					+ " TC-CABLE-FACE-PARITY");
		}
		helper.succeed();
	}

	/**
	 * States to probe for one block. A horizontally-orientable block is swept in all four facings:
	 * with the default state alone ({@code FACING=NORTH}) an override hardcoded to a compass direction
	 * — {@code side == Direction.SOUTH} instead of {@code state.getValue(FACING).getOpposite()} —
	 * would pass while every rotated copy in the world is broken.
	 */
	private static List<BlockState> orientations(Block block) {
		BlockState defaultState = block.defaultBlockState();
		List<BlockState> states = new ArrayList<>();
		if (defaultState.hasProperty(HorizontalMachineBlock.FACING)) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				states.add(defaultState.setValue(HorizontalMachineBlock.FACING, facing));
			}
		} else {
			states.add(defaultState);
		}
		return states;
	}

	/** Orientation label for the failure message, or {@code -} for blocks without a facing. */
	private static String facingOf(BlockState state) {
		return state.hasProperty(HorizontalMachineBlock.FACING)
				? state.getValue(HorizontalMachineBlock.FACING).getName()
				: "-";
	}
}
