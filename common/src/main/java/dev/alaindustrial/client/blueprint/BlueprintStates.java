package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Turns a blueprint {@link Placement} into the block state the scene draws (MOD-797): the block's default
 * state with the placement's properties laid over it. A property the block does not have, or a value it
 * does not accept, is skipped rather than thrown — the L1 catalogue test is where a typo is caught, the
 * screen only has to stay up. Results are cached: the sheet asks for the same few states every frame.
 */
final class BlueprintStates {

	private static final Map<Placement, Optional<BlockState>> CACHE = new HashMap<>();
	private static final Map<Placement, Optional<BlockState>> LATE_CACHE = new HashMap<>();

	private BlueprintStates() {
	}

	/**
	 * The state {@code placement} describes — with its late properties when {@code late} — or null when its
	 * block id is unknown.
	 */
	static @Nullable BlockState resolve(Placement placement, boolean late) {
		if (late && !placement.lateProps().isEmpty()) {
			return LATE_CACHE.computeIfAbsent(placement, p -> build(p, true)).orElse(null);
		}
		return CACHE.computeIfAbsent(placement, p -> build(p, false)).orElse(null);
	}

	private static Optional<BlockState> build(Placement placement, boolean late) {
		Identifier id = Identifier.tryParse(placement.blockId());
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return Optional.empty();
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		state = apply(block, state, placement.props());
		if (late) {
			state = apply(block, state, placement.lateProps());
		}
		return Optional.of(state);
	}

	private static BlockState apply(Block block, BlockState state, Map<String, String> props) {
		for (Map.Entry<String, String> entry : props.entrySet()) {
			Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
			if (property != null) {
				state = withValue(state, property, entry.getValue());
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property,
			String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}
}
