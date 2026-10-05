package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * L2 characterization of the energy port every powered block entity shows on each face (MOD-712,
 * batch 0, BE-10): the {@link EnergyRole} of {@code energyRoleForFace} on all six faces, for every
 * block the block entity serves and every state that block can be in.
 *
 * <p>Before BE-10 fourteen machines each wrote out "consumer on every face but the front"; after it
 * that is the machine base's default. A role is not visible in any screen, and a consumer that quietly
 * turns into a source, or a front face that starts accepting a cable, breaks a player's wiring rather
 * than a test of its own — hence this snapshot of all of them at once.
 *
 * <p>A block with more than {@link #MAX_ALL_STATES} states (the cables and pipes, whose states are
 * connection shapes) is sampled in its default state, turned through every value of its {@code facing}
 * property when it has one. The block entities are built level-free with the state under test
 * ({@link SaveFormatTestSupport}), so only the block entity's own answer is recorded.
 */
public final class EnergyRoleForFaceSnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(EnergyRoleForFaceSnapshotScenarios::energyRolesMatchSnapshot,
								"machine_char_energy_role_snapshot")
						.ticks(100));

		private Roster() {}
	}

	private EnergyRoleForFaceSnapshotScenarios() {}

	/** Above this many states a block is sampled by its {@code facing} only. */
	private static final int MAX_ALL_STATES = 48;

	/**
	 * @implements MOD-712-CH04 — every powered block entity exposes the reviewed energy role on each of
	 *     its six faces, in every state of every block it serves.
	 */
	public static void energyRolesMatchSnapshot(GameTestHelper helper) {
		ReferenceLines.compare(helper, "EnergyRoleForFaceSnapshot",
				"Reviewed energy role per face (order DUNSWE; I in, O out, B both, N none) of every powered"
						+ " block entity (MOD-712) — the reference {@link EnergyRoleForFaceSnapshotScenarios}"
						+ " compares against.",
				"*:machine_char_energy_role*", currentRoles(), EnergyRoleForFaceSnapshot.LINES);
	}

	/** One line per (block entity, block, state), in manifest order. */
	static List<String> currentRoles() {
		List<String> lines = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			for (String blockId : def.blocks()) {
				Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(blockId));
				String prefix = def.id().equals(blockId) ? blockId : def.id() + "@" + blockId;
				for (BlockState state : statesOf(block)) {
					BlockEntity be = def.factory().create(SaveFormatTestSupport.POS, state);
					if (be instanceof EnergyBlockEntity powered) {
						lines.add(prefix + "[" + describe(state) + "] DUNSWE=" + roles(powered));
					}
				}
			}
		}
		return lines;
	}

	private static List<BlockState> statesOf(Block block) {
		List<BlockState> all = block.getStateDefinition().getPossibleStates();
		if (all.size() <= MAX_ALL_STATES) {
			return all;
		}
		BlockState base = block.defaultBlockState();
		for (Property<?> property : base.getProperties()) {
			if ("facing".equals(property.getName())) {
				return turned(base, property);
			}
		}
		return List.of(base);
	}

	private static <T extends Comparable<T>> List<BlockState> turned(BlockState base, Property<T> property) {
		List<BlockState> states = new ArrayList<>();
		for (T value : property.getPossibleValues()) {
			states.add(base.setValue(property, value));
		}
		return states;
	}

	private static String roles(EnergyBlockEntity be) {
		StringBuilder code = new StringBuilder();
		for (Direction face : Direction.values()) {
			code.append(be.energyRoleForFace(face).name().charAt(0));
		}
		return code.toString();
	}

	/** {@code name=value} for every property, sorted by name. */
	private static String describe(BlockState state) {
		List<Property<?>> properties = new ArrayList<>(state.getProperties());
		properties.sort(Comparator.comparing(Property::getName));
		List<String> parts = new ArrayList<>();
		for (Property<?> property : properties) {
			parts.add(property.getName() + "=" + valueName(state, property));
		}
		return String.join(",", parts);
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}
}
