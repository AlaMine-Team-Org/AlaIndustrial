package dev.alaindustrial.worldgen.town;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.SettlementStyle;
import dev.alaindustrial.settlement.SettlementTerritory;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code alaindustrial:ala_town} — a dead ALA work town (MOD-830, R13 and R29 of MOD-824, ADR-050): a jigsaw
 * structure from data, with the abandoned lab under its town hall.
 *
 * <p><b>Data, the vanilla way.</b> The file of a town is a vanilla jigsaw structure's — {@code start_pool},
 * {@code size}, {@code start_height}, {@code project_start_to_heightmap}, {@code max_distance_from_center},
 * {@code use_expansion_hack} and the common {@code biomes}, {@code step}, {@code terrain_adaptation},
 * {@code spawn_overrides} — plus {@code style}, the id of its {@code alaindustrial:settlement_style}. The layout
 * is the vanilla {@link JigsawPlacement}: buildings and the ground between them are templates in pools, joined by
 * jigsaw blocks (tools/gen_ala_town_templates.py writes the built-in ones). A third style is files in a datapack,
 * no code.
 *
 * <p><b>What the type adds</b> to a jigsaw structure:
 * <ul>
 *   <li>the lab: a piece that holds a data structure block named {@value #LAB_HATCH} gets an
 *       {@link AlaTownLabPiece} under that cell. A town whose lab finds no rock is not placed (spec
 *       {@code ala_town.md}); a style with no such marker is a town without a lab;</li>
 *   <li>no town on water: a town whose corners or middle stand on water on the generator's own columns is not
 *       placed;</li>
 *   <li>no building out of the ground's reach: a town with a building whose floor is further than
 *       {@value #BEARD_REACH} blocks from the generator's ground at its corners, edges or middle is not placed —
 *       the terrain adjustment would leave it hanging over a valley or sunk into a hill;</li>
 *   <li>the plaques of the former residents ({@link TownPlaques}), engraved after the pieces of each chunk;</li>
 *   <li>the settlement of the core the town hall holds, registered with this town's style and the footprint of all
 *       its pieces, which the settlement's territory covers ({@link TownCore}, {@link TownFootprint}).</li>
 * </ul>
 *
 * <p><b>The style</b> is read by id when it is needed, not decoded into a holder (ADR-050 §3): a missing
 * style is logged once and the town still generates, since nothing of its layout comes from it.
 */
public final class AlaTownStructure extends Structure {

	/** The data marker under which the lab's hatch goes; written by the template generator. */
	public static final String LAB_HATCH = "lab_hatch";

	/** Inset of the water samples from the town's edge: the corners themselves are often bare ground or road. */
	private static final int WATER_SAMPLE_INSET = 4;

	/**
	 * How far the terrain adjustment reaches from a building's floor: the radius of the vanilla beard kernel
	 * ({@code Beardifier}), past which it neither fills under a floor nor cuts above it.
	 */
	public static final int BEARD_REACH = 12;

	/** Samples per side of the grid over a building's floor checked against {@link #BEARD_REACH}. */
	private static final int FLOOR_SAMPLES = 3;

	public static final MapCodec<AlaTownStructure> CODEC = RecordCodecBuilder.<AlaTownStructure>mapCodec(i -> i.group(
					settingsCodec(i),
					StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
					Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size").forGetter(s -> s.size),
					HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
					Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(s -> s.useExpansionHack),
					Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap")
							.forGetter(s -> s.projectStartToHeightmap),
					JigsawStructure.MaxDistance.CODEC.fieldOf("max_distance_from_center")
							.forGetter(s -> s.maxDistanceFromCenter),
					ResourceKey.codec(ModDataRegistries.SETTLEMENT_STYLE).fieldOf("style").forGetter(s -> s.style))
			.apply(i, AlaTownStructure::new))
			.validate(AlaTownStructure::verifyRange);

	/** Styles already reported missing, so the warning is written once per style, not once per chunk. */
	private static final Set<ResourceKey<SettlementStyle>> MISSING_STYLES = ConcurrentHashMap.newKeySet();

	private final Holder<StructureTemplatePool> startPool;
	private final int size;
	private final HeightProvider startHeight;
	private final boolean useExpansionHack;
	private final Optional<Heightmap.Types> projectStartToHeightmap;
	private final JigsawStructure.MaxDistance maxDistanceFromCenter;
	private final ResourceKey<SettlementStyle> style;

	public AlaTownStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size,
			HeightProvider startHeight, boolean useExpansionHack, Optional<Heightmap.Types> projectStartToHeightmap,
			JigsawStructure.MaxDistance maxDistanceFromCenter, ResourceKey<SettlementStyle> style) {
		super(settings);
		this.startPool = startPool;
		this.size = size;
		this.startHeight = startHeight;
		this.useExpansionHack = useExpansionHack;
		this.projectStartToHeightmap = projectStartToHeightmap;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.style = style;
	}

	/** The same limit vanilla puts on a jigsaw structure: pieces and their terrain margin within 128 blocks. */
	private static DataResult<AlaTownStructure> verifyRange(AlaTownStructure structure) {
		int margin = structure.terrainAdaptation() == TerrainAdjustment.NONE ? 0 : 12;
		return structure.maxDistanceFromCenter.horizontal() + margin > JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE
				? DataResult.error(() -> "Horizontal structure size including terrain adaptation must not exceed "
						+ JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE)
				: DataResult.success(structure);
	}

	/** The id of this town's settlement style. */
	public ResourceKey<SettlementStyle> style() {
		return style;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		noteStyle(context.registryAccess());
		ChunkPos chunk = context.chunkPos();
		int height = startHeight.sample(context.random(),
				new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
		BlockPos start = new BlockPos(chunk.getMinBlockX(), height, chunk.getMinBlockZ());
		Optional<GenerationStub> jigsaw = JigsawPlacement.addPieces(context, startPool, Optional.empty(), size, start,
				useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter, PoolAliasLookup.EMPTY,
				DimensionPadding.ZERO, LiquidSettings.APPLY_WATERLOGGING);
		if (jigsaw.isEmpty() || !LineStructures.isValidBiome(context, jigsaw.get())) {
			return Optional.empty();
		}
		StructurePiecesBuilder town = jigsaw.get().getPiecesBuilder();
		if (town.isEmpty() || standsInWater(context, town.getBoundingBox()) || floorOutOfReach(context, town)) {
			return Optional.empty();
		}
		Optional<BlockPos> hatch = labHatch(town, context.structureTemplateManager());
		if (hatch.isPresent()) {
			Optional<AlaTownLabPiece> lab = AlaTownLabPiece.under(hatch.get(), context.random(),
					context.structureTemplateManager(), context.chunkGenerator(), context.heightAccessor(),
					context.randomState());
			if (lab.isEmpty()) {
				return Optional.empty();
			}
			town.addPiece(lab.get());
		}
		return Optional.of(new GenerationStub(jigsaw.get().position(), Either.right(town)));
	}

	/** The cell of the first {@value #LAB_HATCH} marker among the laid-out pieces, if any. */
	static Optional<BlockPos> labHatch(StructurePiecesBuilder town, StructureTemplateManager templates) {
		for (StructurePiece piece : town.build().pieces()) {
			if (piece instanceof PoolElementStructurePiece pool
					&& pool.getElement() instanceof SinglePoolElement single) {
				for (StructureTemplate.StructureBlockInfo marker : single.getDataMarkers(templates, pool.getPosition(),
						pool.getRotation(), true)) {
					if (marker.nbt() != null && LAB_HATCH.equals(marker.nbt().getStringOr("metadata", ""))) {
						return Optional.of(marker.pos());
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Whether a building of {@code town} has its floor further than {@link #BEARD_REACH} from the generator's ground
	 * at a point of a {@value #FLOOR_SAMPLES} × {@value #FLOOR_SAMPLES} grid over it (corners, edge middles, middle).
	 * Only rigid pieces: the ground pieces follow the terrain by themselves.
	 */
	private static boolean floorOutOfReach(GenerationContext context, StructurePiecesBuilder town) {
		for (StructurePiece piece : town.build().pieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool)
					|| pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) {
				continue;
			}
			BoundingBox box = pool.getBoundingBox();
			for (int i = 0; i < FLOOR_SAMPLES; i++) {
				for (int j = 0; j < FLOOR_SAMPLES; j++) {
					int x = box.minX() + (box.maxX() - box.minX()) * i / (FLOOR_SAMPLES - 1);
					int z = box.minZ() + (box.maxZ() - box.minZ()) * j / (FLOOR_SAMPLES - 1);
					int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z,
							Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
					if (Math.abs(ground - box.minY()) > BEARD_REACH) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Water at the middle or near a corner of {@code box}, on the generator's own columns. */
	private static boolean standsInWater(GenerationContext context, BoundingBox box) {
		int inset = Math.min(WATER_SAMPLE_INSET, Math.min(box.getXSpan(), box.getZSpan()) / 2);
		int[] xs = {box.minX() + inset, box.maxX() - inset};
		int[] zs = {box.minZ() + inset, box.maxZ() - inset};
		if (isWater(context, box.getCenter().getX(), box.getCenter().getZ())) {
			return true;
		}
		for (int x : xs) {
			for (int z : zs) {
				if (isWater(context, x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether the topmost non-air block of the generator's column at ({@code x}, {@code z}) holds a fluid. */
	private static boolean isWater(GenerationContext context, int x, int z) {
		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(),
				context.randomState());
		for (int y = context.heightAccessor().getMaxY(); y >= context.heightAccessor().getMinY(); y--) {
			BlockState state = column.getBlock(y);
			if (!state.isAir()) {
				return !state.getFluidState().isEmpty();
			}
		}
		return false;
	}

	/** Logs once per style that this town's style is not in the registry; the town is placed either way. */
	private void noteStyle(RegistryAccess access) {
		boolean known = access.lookup(ModDataRegistries.SETTLEMENT_STYLE).flatMap(styles -> styles.get(style))
				.isPresent();
		if (!known && MISSING_STYLES.add(style)) {
			Industrialization.LOGGER.warn("[ala_town] settlement style {} of a town is not in the data; the town is "
					+ "placed, its settlement will live on defaults", style.identifier());
		}
	}

	/**
	 * After the pieces of this chunk are down: the plaques of the former residents ({@link TownPlaques}) and the
	 * settlement of the core ({@link TownCore}); and the block-entity placeholders the game left where no block
	 * entity lives, the reception capsule's cells above its lower one ({@link TownBlockEntities}).
	 */
	@Override
	public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, PiecesContainer pieces) {
		TownBlockEntities.dropEmptyPlaceholders(level.getChunk(chunkPos.x(), chunkPos.z()), chunkBox);
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		StructureTemplateManager templates = LineStructures.templates(server);
		// The whole town's, not this chunk's: every piece of the start is in the container (MOD-848).
		Optional<SettlementTerritory.Area> footprint = TownFootprint.of(pieces.pieces());
		for (StructurePiece piece : pieces.pieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getBoundingBox().intersects(chunkBox)) {
				TownPlaques.engrave(level, templates, pool, chunkBox);
				TownCore.register(level, templates, pool, chunkBox, style, footprint);
			}
		}
	}

	@Override
	public StructureType<?> type() {
		return AlaTownTypes.STRUCTURE_TYPE;
	}
}
