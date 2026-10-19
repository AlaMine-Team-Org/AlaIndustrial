package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.gametest.compat.TownTerrain;
import dev.alaindustrial.worldgen.town.AlaTownLabPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * A site for an ALA town in a gametest (MOD-830): a flat generator of a known surface and the same ground built in
 * the live level, so a town can be laid out the way world generation lays it out — by the structure's own
 * {@code generate}, against a chunk generator — and placed chunk by chunk the way {@code /place structure} does.
 *
 * <p>A gametest world has no terrain of its own to speak of, and the two lines' test worlds differ, so the
 * scenario brings both: the generator the layout asks for heights ({@link #SURFACE_Y}, grass on dirt on stone, one
 * biome) and the matching ground in the level the pieces are written into. The lab goes deeper than the built
 * ground; it fills its own box with rock (see {@link AlaTownLabPiece}).
 */
final class AlaTownSite {

	/** The flat generator's top block, grass: high enough under it for the deepest lab above the world's floor. */
	static final int SURFACE_Y = 11;

	/** Layers of ground built in the live level under the surface; the lab below builds its own rock. */
	private static final int GROUND_DEPTH = 6;

	/** Air cleared above the town's box, so nothing of the test world stands in the town. */
	private static final int AIR_MARGIN = 3;

	private AlaTownSite() {
	}

	/** A flat generator of one biome whose top block, grass, is at {@link #SURFACE_Y}. */
	static ChunkGenerator generator(ServerLevel level, ResourceKey<Biome> biome) {
		Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome);
		List<FlatLayerInfo> layers = List.of(new FlatLayerInfo(SURFACE_Y - level.getMinY() - 3, Blocks.STONE),
				new FlatLayerInfo(3, Blocks.DIRT), new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
		return new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), holder, List.of())
				.withBiomeAndLayers(layers, Optional.empty(), holder));
	}

	/** The structure {@code id} from the live registry. */
	static Holder.Reference<Structure> structure(ServerLevel level, Identifier id) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
	}

	/** The structure laid out in {@code chunk} as world generation would, with its own biome test. */
	static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkGenerator generator,
			ChunkPos chunk, long seed) {
		RandomState randomState = level.getChunkSource().randomState();
		return TownTerrain.generate(level, structure, generator, generator.getBiomeSource(),
				TownTerrain.climate(randomState), randomState, seed, chunk);
	}

	/** Builds the generator's ground under the town's box in the live level, then places the town chunk by chunk. */
	static void place(ServerLevel level, StructureStart start, ChunkGenerator generator, long seed) {
		BoundingBox box = start.getBoundingBox();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = SURFACE_Y - GROUND_DEPTH; y <= box.maxY() + AIR_MARGIN; y++) {
					level.setBlock(cursor.set(x, y, z), ground(y), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
						chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
				start.placeInChunk(level, level.structureManager(), generator,
						RandomSource.create(seed ^ chunk.pack()), chunkBox, chunk);
			}
		}
	}

	private static BlockState ground(int y) {
		if (y > SURFACE_Y) {
			return Blocks.AIR.defaultBlockState();
		}
		if (y == SURFACE_Y) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		return y >= SURFACE_Y - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** The town's pieces from pools, in order. */
	static List<PoolElementStructurePiece> poolPieces(StructureStart start) {
		List<PoolElementStructurePiece> out = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool) {
				out.add(pool);
			}
		}
		return out;
	}

	/** The town's lab pieces (one for a town with a lab). */
	static List<AlaTownLabPiece> labs(StructureStart start) {
		List<AlaTownLabPiece> out = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof AlaTownLabPiece lab) {
				out.add(lab);
			}
		}
		return out;
	}

	/** The template a pool piece placed, or {@code null} for an element that is not a single template. */
	static Identifier template(PoolElementStructurePiece piece) {
		return piece.getElement() instanceof SinglePoolElement single ? single.getTemplateLocation() : null;
	}

	/** Whether the piece follows its own Y (a building) rather than the terrain (the ground between). */
	static boolean rigid(PoolElementStructurePiece piece) {
		return piece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
	}

	/** Every template the pools {@code alaindustrial:ala_town/<style>/…} can place. */
	static List<Identifier> styleTemplates(ServerLevel level, String style) {
		List<Identifier> out = new ArrayList<>();
		String prefix = "ala_town/" + style + "/";
		level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).listElements().forEach(pool -> {
			Identifier id = pool.key().identifier();
			if (id.getNamespace().equals(Industrialization.MOD_ID) && id.getPath().startsWith(prefix)) {
				pool.value().getTemplates().forEach(entry -> {
					if (entry.getFirst() instanceof SinglePoolElement single) {
						out.add(single.getTemplateLocation());
					}
				});
			}
		});
		return out;
	}
}
