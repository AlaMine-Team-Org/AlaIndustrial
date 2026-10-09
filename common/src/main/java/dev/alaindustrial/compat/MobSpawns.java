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
 * <p><b>This twin: Minecraft 26.2</b>, where a biome still carries its spawn lists: the chunk generator
 * answers {@code getMobsAt} from the biome at the position, and a spawner entry's group size is a
 * {@code minCount}/{@code maxCount} pair.
 */
public final class MobSpawns {

	private MobSpawns() {
	}

	/** Every entity type that may spawn naturally in {@code category} at {@code pos}, in list order. */
	public static List<EntityType<?>> typesAt(ServerLevel level, MobCategory category, BlockPos pos) {
		List<EntityType<?>> types = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> entry : level.getChunkSource().getGenerator()
				.getMobsAt(level.getBiome(pos), level.structureManager(), category, pos).unwrap()) {
			types.add(entry.value().type());
		}
		return types;
	}

	/**
	 * One spawner entry as a NeoForge {@code add_spawns} biome modifier writes it: the entity type, its weight
	 * and its group size — here the {@code minCount}/{@code maxCount} pair.
	 */
	public static String spawnerJson(Identifier type, int weight, int minGroup, int maxGroup) {
		return "{\n\t\t\"type\": \"" + type + "\",\n\t\t\"minCount\": " + minGroup + ",\n\t\t\"maxCount\": "
				+ maxGroup + ",\n\t\t\"weight\": " + weight + "\n\t}";
	}
}
