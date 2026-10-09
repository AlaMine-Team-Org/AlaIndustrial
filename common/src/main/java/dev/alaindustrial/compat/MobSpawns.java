package dev.alaindustrial.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Natural mob spawns, whose shape differs between the Minecraft lines (MOD-767, ADR-036). Every line has a
 * twin of this class with the same signatures; only the bodies differ.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where a biome's spawns became the environment attribute
 * {@code gameplay/natural_mob_spawns}: the chunk generator answers {@code getMobsAt} from the level, and a
 * spawner entry's group size is one {@code count} int provider.
 */
public final class MobSpawns {

	private MobSpawns() {
	}

	/** Every entity type that may spawn naturally in {@code category} at {@code pos}, in list order. */
	public static List<EntityType<?>> typesAt(ServerLevel level, MobCategory category, BlockPos pos) {
		List<EntityType<?>> types = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> entry : level.getChunkSource().getGenerator()
				.getMobsAt(level, level.structureManager(), category, pos).unwrap()) {
			types.add(entry.value().type());
		}
		return types;
	}

	/**
	 * One spawner entry as a NeoForge {@code add_spawns} biome modifier writes it: the entity type, its weight
	 * and its group size — here a constant {@code count} or a {@code minecraft:uniform} provider.
	 */
	public static String spawnerJson(Identifier type, int weight, int minGroup, int maxGroup) {
		String count = minGroup == maxGroup ? Integer.toString(minGroup)
				: "{\n\t\t\t\"type\": \"minecraft:uniform\",\n\t\t\t\"min_inclusive\": " + minGroup
						+ ",\n\t\t\t\"max_inclusive\": " + maxGroup + "\n\t\t}";
		return "{\n\t\t\"type\": \"" + type + "\",\n\t\t\"count\": " + count + ",\n\t\t\"weight\": " + weight + "\n\t}";
	}
}
