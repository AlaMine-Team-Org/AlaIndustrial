package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The cable arm and the network's endpoint are one rule (MOD-715, batch 0; ADR-027). A cable draws an arm
 * toward a neighbour when {@code CableBlock.connectsTo} says so; the network counts that neighbour as an
 * endpoint when {@link EnergyLookup} finds a port on the face that can move energy either way. Where the
 * two disagree the player sees a joint that carries nothing, or energy flowing through a face with no arm.
 *
 * <p>Pinned before the arm rule moves from {@code instanceof ReactorOutletBlock} to an interface: the arm
 * toward the reactor outlet and toward a concentrator's bottom cell (neither a machine block), and a sweep
 * of every block of the mod in its default state, every face — with today's disagreements written down by
 * name in {@link #KNOWN_DISAGREEMENTS}, so the sweep fails on a new one and on one that went away alike.
 */
public final class CableArmEndpointParityScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CableArmEndpointParityScenarios::reactorOutletDrawsAnArmOnEveryFace,
						"mod715_cable_arm_reactor_outlet"),
				RosterEntry.of(CableArmEndpointParityScenarios::concentratorBottomCellDrawsAnArm,
						"mod715_cable_arm_concentrator_cell"),
				RosterEntry.of(CableArmEndpointParityScenarios::everyBlockArmMatchesItsEndpoint,
						"mod715_cable_arm_matches_endpoint_for_every_block"));

		private Roster() {}
	}

	private static final BlockPos PROBE = new BlockPos(3, 2, 3);

	/**
	 * Today's disagreements between the arm and the endpoint, one {@code id/face: arm=… endpoint=…} line
	 * each, sorted. Characterization, not approval: each is a block whose arm and port are decided by two
	 * different rules (MOD-715 research, findings). The list may only change by a fix that is its own
	 * decision.
	 *
	 * <ul>
	 *   <li>the crystal-farm controller publishes a port on its default front (north) that no cable arm
	 *       reaches;</li>
	 *   <li>the storage module draws an arm on every face but publishes no energy port at all.</li>
	 * </ul>
	 */
	static final List<String> KNOWN_DISAGREEMENTS = List.of(
			"crystal_farm_controller/north: arm=false endpoint=true",
			"storage_module/down: arm=true endpoint=false",
			"storage_module/east: arm=true endpoint=false",
			"storage_module/north: arm=true endpoint=false",
			"storage_module/south: arm=true endpoint=false",
			"storage_module/up: arm=true endpoint=false",
			"storage_module/west: arm=true endpoint=false");

	private CableArmEndpointParityScenarios() {
	}

	/** Does a cable at {@code abs.relative(face)} draw an arm toward the block at {@code abs}? */
	private static boolean arm(Level level, BlockPos abs, Direction face) {
		return CableBlock.shouldConnectTo(level, abs.relative(face), face.getOpposite());
	}

	/** Would the network count the block at {@code abs} as an endpoint through {@code face}? */
	private static boolean endpoint(Level level, BlockPos abs, Direction face) {
		EnergyPort port = EnergyLookup.get().find(level, abs, face);
		return port != null && (port.supportsInsertion() || port.supportsExtraction());
	}

	/**
	 * The outlet is no machine block, yet a cable joins it on every face — the one special case of the arm
	 * rule today.
	 *
	 * @implements MOD-715-CA01 — the cable arm is drawn toward the reactor outlet
	 */
	public static void reactorOutletDrawsAnArmOnEveryFace(GameTestHelper helper) {
		helper.setBlock(PROBE, ModContent.REACTOR_OUTLET.get());
		BlockPos abs = helper.absolutePos(PROBE);
		for (Direction face : Direction.values()) {
			if (!arm(helper.getLevel(), abs, face)) {
				helper.fail("no cable arm toward the reactor outlet's " + face + " face");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * An assembled concentrator's bottom cell lends the core's port, and the arm reads the same rule; the
	 * cell above it lends nothing and draws no arm.
	 *
	 * @implements MOD-715-CA02 — the cable arm is drawn toward a concentrator's bottom cell
	 */
	public static void concentratorBottomCellDrawsAnArm(GameTestHelper helper) {
		BlockPos core = PROBE;
		Direction facing = Direction.NORTH;
		helper.setBlock(core, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part != ConcentratorPart.CORE) {
				helper.setBlock(core.offset(part.worldOffset(facing)),
						ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
			}
		}
		ConcentratorStructure.tryAssemble(helper.getLevel(), helper.absolutePos(core));
		Level level = helper.getLevel();
		Direction out = facing.getOpposite();
		BlockPos bottom = helper.absolutePos(core.offset(ConcentratorPart.BACK.worldOffset(facing)));
		BlockPos top = helper.absolutePos(core.offset(ConcentratorPart.TOP_BACK.worldOffset(facing)));
		if (!arm(level, bottom, out) || !endpoint(level, bottom, out)) {
			helper.fail("the bottom back cell's " + out + " face: arm " + arm(level, bottom, out) + ", endpoint "
					+ endpoint(level, bottom, out) + " — both must be true");
			return;
		}
		if (arm(level, top, out) || endpoint(level, top, out)) {
			helper.fail("the top back cell's " + out + " face: arm " + arm(level, top, out) + ", endpoint "
					+ endpoint(level, top, out) + " — both must be false");
			return;
		}
		helper.succeed();
	}

	/**
	 * Every block of the mod, default state, every face: arm ⇔ endpoint, but for the disagreements written
	 * down in {@link #KNOWN_DISAGREEMENTS}.
	 *
	 * @implements MOD-715-CA03 — for every block, the cable arm and the network endpoint agree as today
	 */
	public static void everyBlockArmMatchesItsEndpoint(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos abs = helper.absolutePos(PROBE);
		List<String> disagreements = new ArrayList<>();
		int swept = 0;
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.id()));
			if (block instanceof CableBlock) {
				continue; // cable to cable is a link, not an endpoint
			}
			helper.setBlock(PROBE, block.defaultBlockState());
			if (level.getBlockState(abs).getBlock() != block) {
				helper.setBlock(PROBE, Blocks.AIR);
				continue;
			}
			swept++;
			for (Direction face : Direction.values()) {
				boolean arm = arm(level, abs, face);
				boolean endpoint = endpoint(level, abs, face);
				if (arm != endpoint) {
					disagreements.add(def.id() + "/" + face.getName() + ": arm=" + arm + " endpoint=" + endpoint);
				}
			}
			helper.setBlock(PROBE, Blocks.AIR);
		}
		disagreements.sort(null);
		if (swept < 100) {
			helper.fail("the sweep placed only " + swept + " blocks — it cannot vouch for the mod's content");
			return;
		}
		if (!disagreements.equals(KNOWN_DISAGREEMENTS)) {
			List<String> added = new ArrayList<>(disagreements);
			added.removeAll(KNOWN_DISAGREEMENTS);
			List<String> gone = new ArrayList<>(KNOWN_DISAGREEMENTS);
			gone.removeAll(disagreements);
			helper.fail("the cable arm and the network endpoint disagree differently than recorded.\n  new: "
					+ String.join("\n       ", added) + "\n  gone: " + String.join("\n        ", gone));
			return;
		}
		helper.succeed();
	}
}
