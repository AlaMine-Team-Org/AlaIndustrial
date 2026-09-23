package dev.alaindustrial.block.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Reads a BlockState this mod's block entities persisted through {@link BlockState#CODEC} on
 * Minecraft 26.2 — the cross-version read that MOD-645 was opened for.
 *
 * <p><b>Why this exists.</b> Between 26.2 and 26.3 Minecraft replaced {@code BlockState.CODEC}:
 * 26.2 wrote a map ({@code {Name: "…", Properties: {…}}}), 26.3 reads only a plain registry-name
 * string (a default state) or a {@code {id, properties}} map. The mod's own persistence never
 * changed, yet every world saved by a 26.2 build stopped loading these fields on 26.3 — the decode
 * failed, the reader's {@code orElse(…)} default kicked in, and an incubator silently forgot the
 * colour of its dome glass while a kok-sagyz root forgot it stood in sand (which is the plant's
 * growth bonus). Vanilla's datafixers do not touch mod-owned block-entity fields, so the legacy
 * shape stays this mod's problem for as long as 26.2 worlds exist.
 *
 * <p><b>Why a codec branch and not the {@link BlockEntityDataMigrations} ladder.</b> The mod's own
 * save layout did not change — the vanilla codec under it did — so there is no version bump to
 * record, and {@code KokSagyzRootBlockEntity} is not on the ladder's {@code EnergyBlockEntity}
 * type anyway. A tolerant read is also idempotent by construction: encoding always goes through
 * the <em>current</em> {@link BlockState#CODEC}, so a legacy tag turns into the new format the
 * moment its chunk is saved again and the branch then simply never fires.
 */
public final class LegacyBlockStates {

	private LegacyBlockStates() {}

	/**
	 * The exact shape {@code BlockState.CODEC} produced on 26.2: a {@code Name} registry id plus an
	 * optional {@code Properties} string map, absent for a default state. Reading it back needs no
	 * registry holder indirection — the plain by-name codec resolves the block and each property is
	 * re-applied by name.
	 */
	private static final Codec<BlockState> LEGACY_26_2 = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.BLOCK.byNameCodec().fieldOf("Name").forGetter(BlockState::getBlock),
			Codec.unboundedMap(Codec.STRING, Codec.STRING)
					.optionalFieldOf("Properties", Map.of())
					.forGetter(LegacyBlockStates::propertyStrings))
			.apply(instance, LegacyBlockStates::fromNameAndProperties));

	/**
	 * Reads both eras: the current {@link BlockState#CODEC} (26.3: a registry-name string for a
	 * default state, or an {@code {id, properties}} map) and the 26.2 {@code {Name[, Properties]}}
	 * map above. Use for every load of a persisted BlockState; keep writing through
	 * {@link BlockState#CODEC} — the encode side of this codec is exactly that, by way of the
	 * always-left branch below.
	 */
	public static final Codec<BlockState> TOLERANT_CODEC = Codec.either(BlockState.CODEC, LEGACY_26_2)
			.xmap(either -> either.map(state -> state, state -> state), Either::left);

	/** Default state of the named block, with every listed property applied where it exists. */
	private static BlockState fromNameAndProperties(Block block, Map<String, String> properties) {
		BlockState state = block.defaultBlockState();
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
			if (property != null) {
				state = apply(state, property, entry.getValue());
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/**
	 * Only reachable if the legacy branch is ever asked to encode, which the tolerant codec above
	 * never does — kept faithful rather than {@code Map.of()} so the codec stays honest if someone
	 * reuses it directly.
	 */
	private static Map<String, String> propertyStrings(BlockState state) {
		Map<String, String> out = new LinkedHashMap<>();
		state.getValues().forEach(value -> out.put(value.property().getName(), value.valueName()));
		return out;
	}
}
