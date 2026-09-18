package dev.alaindustrial.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * What the lab entrance is made of in each biome (MOD-513): the table of
 * {@code docs/structures/lab_entrance.md}, the per-biome camouflage section, as approved by the owner in
 * the dev world on 2026-09-18 — forty land biomes of the Overworld.
 *
 * <p>Offsets in a {@link Decoration} are relative to the hatch before the entrance is rotated: x east,
 * y up, z south, the same frame as the entrance spec.
 *
 * @param groundTop the ground of the whole footprint
 * @param groundSub the "disturbed" ground in the cells around the hatch
 * @param boulder the five boulders in a cross around the hatch
 * @param accent what sits on each boulder, or {@code null}
 * @param canopy what shades the hatch
 * @param log the two stumps of a {@link Canopy#TREE} (the stem of a {@link Canopy#MUSHROOM}), or {@code null}
 * @param leaves the crown, or {@code null}
 * @param decorations one-off blocks particular to the biome
 */
public record LabCamouflage(Block groundTop, Block groundSub, Block boulder, @Nullable BlockState accent,
		Canopy canopy, @Nullable Block log, @Nullable Block leaves, List<Decoration> decorations) {

	/** What stands over the hatch. */
	public enum Canopy {
		/** Open ground: the boulders must match the ground, nothing else hides them. */
		NONE,
		/** Two stumps and a low crown of leaves. */
		TREE,
		/** The same shape in mushroom stem and cap, for the mushroom fields. */
		MUSHROOM
	}

	/** One block particular to a biome, at an offset from the hatch before rotation. */
	public record Decoration(int dx, int dy, int dz, BlockState state) {
	}

	private static final Map<ResourceKey<Biome>, LabCamouflage> BY_BIOME = new HashMap<>();

	static {
		Block redTerracotta = Blocks.DYED_TERRACOTTA.pick(DyeColor.RED);
		BlockState moss = Blocks.MOSS_CARPET.defaultBlockState();
		BlockState snow = Blocks.SNOW.defaultBlockState();
		BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();
		BlockState driftwood = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);

		tree(Biomes.PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.ANDESITE, moss, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		tree(Biomes.SUNFLOWER_PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.ANDESITE, moss, Blocks.OAK_LOG,
				Blocks.OAK_LEAVES,
				new Decoration(-2, 1, -2, Blocks.SUNFLOWER.defaultBlockState()
						.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER)),
				new Decoration(-2, 2, -2, Blocks.SUNFLOWER.defaultBlockState()
						.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)));
		open(Biomes.SNOWY_PLAINS, Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.STONE, snow);
		open(Biomes.ICE_SPIKES, Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, null);
		open(Biomes.DESERT, Blocks.SAND, Blocks.SAND, Blocks.SMOOTH_SANDSTONE, null,
				new Decoration(-2, 1, -1, deadBush));
		tree(Biomes.SWAMP, Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.MOSSY_COBBLESTONE, moss, Blocks.OAK_LOG,
				Blocks.OAK_LEAVES, new Decoration(-2, 1, -2, Blocks.LILY_PAD.defaultBlockState()));
		tree(Biomes.MANGROVE_SWAMP, Blocks.MUD, Blocks.MUD, Blocks.MOSSY_COBBLESTONE, moss, Blocks.MANGROVE_LOG,
				Blocks.MANGROVE_LEAVES, new Decoration(-2, 1, -2, Blocks.MANGROVE_ROOTS.defaultBlockState()));
		tree(Biomes.FOREST, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.ANDESITE, moss, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		tree(Biomes.FLOWER_FOREST, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.ANDESITE, moss, Blocks.OAK_LOG,
				Blocks.OAK_LEAVES,
				new Decoration(-2, 1, -2, Blocks.POPPY.defaultBlockState()),
				new Decoration(-2, 1, -1, Blocks.AZURE_BLUET.defaultBlockState()));
		tree(Biomes.BIRCH_FOREST, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, moss, Blocks.BIRCH_LOG,
				Blocks.BIRCH_LEAVES);
		tree(Biomes.OLD_GROWTH_BIRCH_FOREST, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MOSSY_COBBLESTONE, moss,
				Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
		tree(Biomes.DARK_FOREST, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE, moss,
				Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES,
				new Decoration(-2, 1, -2, Blocks.RED_MUSHROOM.defaultBlockState()));
		tree(Biomes.OLD_GROWTH_PINE_TAIGA, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE, moss,
				Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
		tree(Biomes.OLD_GROWTH_SPRUCE_TAIGA, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE, moss,
				Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
		tree(Biomes.TAIGA, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.STONE, moss, Blocks.SPRUCE_LOG,
				Blocks.SPRUCE_LEAVES);
		tree(Biomes.SNOWY_TAIGA, Blocks.SNOW_BLOCK, Blocks.PODZOL, Blocks.STONE, null, Blocks.SPRUCE_LOG,
				Blocks.SPRUCE_LEAVES);
		tree(Biomes.SAVANNA, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.TERRACOTTA, null, Blocks.ACACIA_LOG,
				Blocks.ACACIA_LEAVES);
		tree(Biomes.SAVANNA_PLATEAU, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.TERRACOTTA, null,
				Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES);
		tree(Biomes.WINDSWEPT_SAVANNA, Blocks.COARSE_DIRT, Blocks.STONE, Blocks.STONE, null, Blocks.ACACIA_LOG,
				Blocks.ACACIA_LEAVES);
		open(Biomes.BADLANDS, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, null,
				new Decoration(-2, 1, -1, deadBush));
		open(Biomes.ERODED_BADLANDS, Blocks.RED_SAND, redTerracotta, Blocks.RED_SANDSTONE, null,
				new Decoration(-2, 1, -1, deadBush));
		tree(Biomes.WOODED_BADLANDS, Blocks.COARSE_DIRT, Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, null,
				Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		open(Biomes.MEADOW, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, moss,
				new Decoration(-2, 1, -2, Blocks.CORNFLOWER.defaultBlockState()),
				new Decoration(-2, 1, -1, Blocks.ALLIUM.defaultBlockState()));
		tree(Biomes.GROVE, Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.STONE, snow, Blocks.SPRUCE_LOG,
				Blocks.SPRUCE_LEAVES);
		open(Biomes.SNOWY_SLOPES, Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.STONE, snow);
		open(Biomes.JAGGED_PEAKS, Blocks.STONE, Blocks.STONE, Blocks.STONE, null);
		open(Biomes.FROZEN_PEAKS, Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, null);
		open(Biomes.STONY_PEAKS, Blocks.STONE, Blocks.STONE, Blocks.STONE, null);
		// The vine hangs on the leaves at (-1, 1, -2), its east neighbour — that is what holds it up.
		tree(Biomes.JUNGLE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MOSSY_COBBLESTONE, moss, Blocks.JUNGLE_LOG,
				Blocks.JUNGLE_LEAVES,
				new Decoration(-2, 1, -2, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true)));
		tree(Biomes.SPARSE_JUNGLE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, moss, Blocks.JUNGLE_LOG,
				Blocks.JUNGLE_LEAVES);
		tree(Biomes.BAMBOO_JUNGLE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MOSSY_COBBLESTONE, moss,
				Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES,
				new Decoration(-2, 1, -2, Blocks.BAMBOO.defaultBlockState()
						.setValue(BambooStalkBlock.AGE, 1)
						.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE)
						.setValue(BambooStalkBlock.STAGE, 1)));
		open(Biomes.STONY_SHORE, Blocks.STONE, Blocks.GRAVEL, Blocks.STONE, null);
		// A flat beach needs an anchor the cross of boulders lacks there: driftwood, lying down.
		open(Biomes.BEACH, Blocks.SAND, Blocks.SAND, Blocks.SANDSTONE, null,
				new Decoration(-2, 1, -1, deadBush),
				new Decoration(-1, 1, 2, driftwood),
				new Decoration(0, 1, 2, driftwood));
		open(Biomes.SNOWY_BEACH, Blocks.SAND, Blocks.SAND, Blocks.SANDSTONE, snow,
				new Decoration(-1, 1, 2, driftwood),
				new Decoration(0, 1, 2, driftwood),
				new Decoration(-1, 2, 2, snow));
		put(Biomes.MUSHROOM_FIELDS, new LabCamouflage(Blocks.MYCELIUM, Blocks.MYCELIUM, Blocks.STONE, null,
				Canopy.MUSHROOM, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK, List.of()));
		tree(Biomes.WINDSWEPT_HILLS, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.STONE, moss, Blocks.OAK_LOG,
				Blocks.OAK_LEAVES);
		open(Biomes.WINDSWEPT_GRAVELLY_HILLS, Blocks.GRAVEL, Blocks.GRAVEL, Blocks.STONE, null);
		tree(Biomes.WINDSWEPT_FOREST, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, moss, Blocks.SPRUCE_LOG,
				Blocks.SPRUCE_LEAVES);
		tree(Biomes.CHERRY_GROVE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE,
				Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 4), Blocks.CHERRY_LOG,
				Blocks.CHERRY_LEAVES);
		tree(Biomes.PALE_GARDEN, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE,
				Blocks.PALE_MOSS_CARPET.defaultBlockState(), Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_LEAVES);
	}

	/**
	 * Every block the footprint may stand on: the grounds of the table, and nothing wider. Not
	 * {@code #minecraft:dirt} — in 26.2 that tag no longer holds grass, podzol, mycelium or mud, which
	 * are half of this table.
	 */
	public static final Set<Block> GROUND = BY_BIOME.values().stream()
			.flatMap(camouflage -> Stream.of(camouflage.groundTop(), camouflage.groundSub()))
			.collect(Collectors.toUnmodifiableSet());

	/** Plains: the materials for a biome the table does not know, such as a modded one added to the tag. */
	private static final LabCamouflage FALLBACK = BY_BIOME.get(Biomes.PLAINS);

	/** The materials for {@code biome}; a biome the table does not know gets the plains set. */
	public static LabCamouflage forBiome(Holder<Biome> biome) {
		return biome.unwrapKey().map(BY_BIOME::get).orElse(FALLBACK);
	}

	private static void tree(ResourceKey<Biome> biome, Block groundTop, Block groundSub, Block boulder,
			@Nullable BlockState accent, Block log, Block leaves, Decoration... decorations) {
		put(biome, new LabCamouflage(groundTop, groundSub, boulder, accent, Canopy.TREE, log, leaves,
				List.of(decorations)));
	}

	private static void open(ResourceKey<Biome> biome, Block groundTop, Block groundSub, Block boulder,
			@Nullable BlockState accent, Decoration... decorations) {
		put(biome, new LabCamouflage(groundTop, groundSub, boulder, accent, Canopy.NONE, null, null,
				List.of(decorations)));
	}

	private static void put(ResourceKey<Biome> biome, LabCamouflage camouflage) {
		if (BY_BIOME.put(biome, camouflage) != null) {
			throw new IllegalStateException("biome listed twice in the lab camouflage table: " + biome);
		}
	}
}
