package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.EngravedPlateBlock;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * {@code alaindustrial:abandoned_lab} (MOD-513) — the abandoned lab of the lore: a camouflaged hatch
 * on the surface, a ladder shaft straight down, and one of the seven approved labs at the bottom.
 * Specs: {@code docs/structures/lab_entrance.md} (the surface and the shaft) and
 * {@code docs/structures/lab.md} (the lab).
 *
 * <p><b>One feature for all three parts,</b> for the reason {@link OilGeyserFeature} gives: they share
 * a column by construction, which only holds if one {@code place} call draws all of them. The lab
 * itself is a structure template placed from inside the feature, as vanilla's fossils are.
 *
 * <p><b>Everything is checked before the first block is written,</b> and a failed check drops the
 * whole placement: a flat patch of known ground with nothing standing on it, solid rock around the
 * shaft and around the whole lab, and every cell inside the ±1-chunk window this step may write to.
 * A rare find is allowed not to appear in a given spot; it is not allowed to appear half-built.
 */
public final class AbandonedLabFeature extends Feature<NoneFeatureConfiguration> {

	/** Registry id; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("abandoned_lab");

	/** Stateless; one shared instance, registered by each loader. */
	public static final AbandonedLabFeature INSTANCE = new AbandonedLabFeature();

	/**
	 * The seven approved labs. Their templates live at
	 * {@code data/alaindustrial/structure/abandoned_lab/<id>.nbt}, generated from
	 * {@code docs/structures/lab/<id>.txt} by {@code tools/gen_lab_templates.py}. Public for the
	 * gametest that checks every one of them reaches the game intact.
	 */
	public static final List<String> LABS = List.of("hermit", "reactor", "archive", "workshop", "flooded", "cave",
			"collapse");

	/** Hatch to lab floor, in blocks: deeper than the prototypes (11–15), and different every time. */
	static final int MIN_DEPTH = 24;
	static final int MAX_DEPTH = 48;

	/** Shaft levels that always stay above the lab's highest block (the entrance spec asks for six). */
	static final int MIN_SHAFT_ABOVE_LAB = 6;

	/** The lab floor never comes closer than this to the bottom of the world. */
	static final int FLOOR_ABOVE_MIN_Y = 8;

	/** Depths tried in solid rock before the site is given up. */
	static final int DEPTH_ATTEMPTS = 4;

	/** The largest ground-height difference allowed inside the entrance footprint. */
	static final int MAX_GROUND_STEP = 1;

	/** The entrance footprint around the hatch, before rotation: x -2..3, z -3..3. */
	private static final int[][] FOOTPRINT = footprint();

	/** Ground cells shown as {@link LabCamouflage#groundSub()} — the "disturbed" ground. */
	private static final int[][] DISTURBED = {{-2, -2}, {-2, 1}, {-1, -1}, {-1, 1}, {0, -1}, {0, 1}, {1, -1},
			{1, 0}, {1, 1}};
	private static final int[][] BOULDERS = {{-1, -1}, {-1, 1}, {0, -1}, {0, 1}, {1, 0}};
	private static final int[][] STUMPS = {{1, -1}, {1, 1}};
	private static final int[][] CROWN_LOW = {{-1, -2}, {0, -3}, {0, -2}, {0, 2}, {1, -3}, {1, -2}, {1, 2},
			{1, 3}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {3, -2}, {3, 2}, {3, 3}};
	private static final int[][] CROWN_HIGH = {{1, -1}, {1, 1}, {2, -1}, {2, 0}, {2, 2}};
	private static final int[][] SHAFT_WALLS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

	/** How far the entrance reaches from the hatch in any horizontal direction, after any rotation. */
	private static final int ENTRANCE_REACH = 3;

	private static final List<Supplier<Block>> DIGITS = List.of(
			ModContent.ENGRAVED_PLATE_0, ModContent.ENGRAVED_PLATE_1, ModContent.ENGRAVED_PLATE_2,
			ModContent.ENGRAVED_PLATE_3, ModContent.ENGRAVED_PLATE_4, ModContent.ENGRAVED_PLATE_5,
			ModContent.ENGRAVED_PLATE_6, ModContent.ENGRAVED_PLATE_7, ModContent.ENGRAVED_PLATE_8,
			ModContent.ENGRAVED_PLATE_9);
	private static final List<Supplier<Block>> BROKEN_LETTERS = List.of(
			ModContent.BROKEN_ENGRAVED_PLATE_W, ModContent.BROKEN_ENGRAVED_PLATE_K,
			ModContent.BROKEN_ENGRAVED_PLATE_P, ModContent.BROKEN_ENGRAVED_PLATE_B,
			ModContent.BROKEN_ENGRAVED_PLATE_D, ModContent.BROKEN_ENGRAVED_PLATE_R,
			ModContent.BROKEN_ENGRAVED_PLATE_M);

	private AbandonedLabFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();

		String lab = LABS.get(random.nextInt(LABS.size()));
		Rotation rotation = Rotation.getRandom(random);
		StructureTemplate template = level.getLevel().getServer().getStructureManager()
				.getOrCreate(Industrialization.id("abandoned_lab/" + lab));
		Shaft shaft = Shaft.of(template);
		if (shaft == null) {
			Industrialization.LOGGER.error("[abandoned_lab] template '{}' is missing or has no entry ladder", lab);
			return false;
		}
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation)
				.setRotationPivot(shaft.foot())
				.setIgnoreEntities(true);

		// The lab's extent around its ladder foot once rotated, merged with the entrance's reach.
		BoundingBox labAroundFoot = template.getBoundingBox(settings, BlockPos.ZERO.subtract(shaft.foot()));
		ChunkPos chunk = ChunkPos.containing(origin);
		int hatchX = clampIntoWindow(origin.getX(), chunk.getMinBlockX(), chunk.getMaxBlockX(),
				Math.min(labAroundFoot.minX(), -ENTRANCE_REACH), Math.max(labAroundFoot.maxX(), ENTRANCE_REACH));
		int hatchZ = clampIntoWindow(origin.getZ(), chunk.getMinBlockZ(), chunk.getMaxBlockZ(),
				Math.min(labAroundFoot.minZ(), -ENTRANCE_REACH), Math.max(labAroundFoot.maxZ(), ENTRANCE_REACH));

		int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, hatchX, hatchZ) - 1;
		BlockPos hatch = new BlockPos(hatchX, groundY, hatchZ);
		if (!siteIsClear(level, hatch, rotation)) {
			return false;
		}

		int topAboveFoot = shaft.top() - shaft.foot().getY();
		int minDepth = Math.max(MIN_DEPTH, topAboveFoot + 1 + MIN_SHAFT_ABOVE_LAB);
		int maxDepth = Math.min(MAX_DEPTH, groundY - (level.getMinY() + FLOOR_ABOVE_MIN_Y));
		if (maxDepth < minDepth) {
			return false;
		}
		for (int attempt = 0; attempt < DEPTH_ATTEMPTS; attempt++) {
			int depth = minDepth + random.nextInt(maxDepth - minDepth + 1);
			BlockPos foot = hatch.below(depth);
			BlockPos templateOrigin = foot.subtract(shaft.foot());
			BoundingBox labBox = template.getBoundingBox(settings, templateOrigin);
			int shaftFrom = foot.getY() + topAboveFoot + 1;
			if (!rockIsSolid(level, labBox.inflatedBy(1)) || !shaftRockIsSolid(level, hatch, shaftFrom)) {
				continue;
			}
			settings.setBoundingBox(writeWindow(level, chunk));
			template.placeInWorld(level, templateOrigin, templateOrigin, settings, random, Block.UPDATE_CLIENTS);
			engravePlaque(level, labBox, hatch, random);
			placeShaft(level, hatch, shaftFrom, rotation);
			placeEntrance(level, hatch, rotation, LabCamouflage.forBiome(level.getBiome(hatch)));
			Industrialization.LOGGER.debug("[abandoned_lab] '{}' at {} {} {}, hatch Y {}, depth {}, rotation {}",
					lab, hatchX, foot.getY(), hatchZ, groundY, depth, rotation);
			return true;
		}
		return false;
	}

	/**
	 * Where the entrance ladder of a lab template stands: the foot (the floor block under the lowest
	 * rung, template-local — the point the template turns around) and the highest rung, which is the
	 * template's top. Read from the template itself, so no layout number is repeated in code.
	 */
	public record Shaft(BlockPos foot, int top) {

		/** The template's shaft, or {@code null} if the template is missing or does not end in one ladder. */
		public static @Nullable Shaft of(StructureTemplate template) {
			List<StructureTemplate.StructureBlockInfo> ladders =
					template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.LADDER);
			StructureTemplate.StructureBlockInfo highest = null;
			for (StructureTemplate.StructureBlockInfo info : ladders) {
				if (highest == null || info.pos().getY() > highest.pos().getY()) {
					highest = info;
				}
			}
			if (highest == null || highest.pos().getY() != template.getSize().getY() - 1) {
				return null;
			}
			int x = highest.pos().getX();
			int z = highest.pos().getZ();
			int lowest = highest.pos().getY();
			boolean descending = true;
			while (descending) {
				descending = false;
				for (StructureTemplate.StructureBlockInfo info : ladders) {
					if (info.pos().getX() == x && info.pos().getZ() == z && info.pos().getY() == lowest - 1) {
						lowest--;
						descending = true;
						break;
					}
				}
			}
			return new Shaft(new BlockPos(x, lowest - 1, z), highest.pos().getY());
		}
	}

	/**
	 * The hatch coordinate on one axis: the placement origin, moved just enough that everything from
	 * {@code reachMin} to {@code reachMax} around it stays inside the ±1-chunk window of this chunk.
	 */
	static int clampIntoWindow(int origin, int chunkMin, int chunkMax, int reachMin, int reachMax) {
		int lowest = chunkMin - 16 - reachMin;
		int highest = chunkMax + 16 - reachMax;
		return Math.max(lowest, Math.min(highest, origin));
	}

	/** The ±1-chunk window a feature of this chunk may write to, full height. */
	private static BoundingBox writeWindow(WorldGenLevel level, ChunkPos chunk) {
		return new BoundingBox(chunk.getMinBlockX() - 16, level.getMinY(), chunk.getMinBlockZ() - 16,
				chunk.getMaxBlockX() + 16, level.getMaxY(), chunk.getMaxBlockZ() + 16);
	}

	/**
	 * The surface test: every footprint cell is known ground within one block of the hatch's height, is
	 * not under water, and has nothing standing on it but plants the entrance may clear.
	 */
	private static boolean siteIsClear(WorldGenLevel level, BlockPos hatch, Rotation rotation) {
		if (!LabCamouflage.GROUND.contains(level.getBlockState(hatch).getBlock())) {
			return false;
		}
		for (int[] cell : FOOTPRINT) {
			BlockPos column = offset(hatch, cell[0], 0, cell[1], rotation);
			int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, column.getX(), column.getZ()) - 1;
			if (Math.abs(ground - hatch.getY()) > MAX_GROUND_STEP) {
				return false;
			}
			BlockPos top = column.atY(ground);
			if (!LabCamouflage.GROUND.contains(level.getBlockState(top).getBlock())) {
				return false;
			}
			for (int dy = 1; dy <= 3; dy++) {
				BlockPos pos = top.above(dy);
				BlockState above = level.getBlockState(pos);
				if (!above.isAir() && !isPlant(level, pos, above)) {
					return false;
				}
			}
		}
		return true;
	}

	/** No air, fluid or protected block anywhere in {@code box}: the lab sits in rock, not in a cave. */
	private static boolean rockIsSolid(WorldGenLevel level, BoundingBox box) {
		if (box.minY() < level.getMinY() || box.maxY() > level.getMaxY()) {
			return false;
		}
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
				box.maxZ())) {
			if (!isRock(level.getBlockState(pos))) {
				return false;
			}
		}
		return true;
	}

	/** The shaft column and its four walls, from the lab's top to the stone slab under the hatch. */
	private static boolean shaftRockIsSolid(WorldGenLevel level, BlockPos hatch, int fromY) {
		for (int y = fromY; y < hatch.getY(); y++) {
			if (!isRock(level.getBlockState(hatch.atY(y)))) {
				return false;
			}
			for (int[] wall : SHAFT_WALLS) {
				if (!isRock(level.getBlockState(hatch.offset(wall[0], y - hatch.getY(), wall[1])))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Grass, a flower, a mushroom, a snow layer: something with no collision and no fluid. The entrance
	 * clears these. "Replaceable" is the wrong test — a flower is not replaceable, and a site with a
	 * dandelion on it was being turned down as if a tree stood there.
	 */
	private static boolean isPlant(WorldGenLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean isRock(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.FEATURES_CANNOT_REPLACE);
	}

	/**
	 * The plaque of the previous record: the leftmost plate, as a reader facing the wall sees it, is
	 * the lost letter — one of the seven broken ones — and the three after it are this lab's historic
	 * number, derived from the world seed and the hatch position.
	 */
	private static void engravePlaque(WorldGenLevel level, BoundingBox labBox, BlockPos hatch, RandomSource random) {
		List<BlockPos> plates = new ArrayList<>();
		Direction facing = null;
		for (BlockPos pos : BlockPos.betweenClosed(labBox.minX(), labBox.minY(), labBox.minZ(), labBox.maxX(),
				labBox.maxY(), labBox.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof EngravedPlateBlock) {
				plates.add(pos.immutable());
				facing = state.getValue(HorizontalDirectionalBlock.FACING);
			}
		}
		if (plates.size() != 4 || facing == null) {
			Industrialization.LOGGER.warn("[abandoned_lab] expected a plaque of 4 plates near {}, found {}",
					hatch, plates.size());
			return;
		}
		Direction readerRight = facing.getOpposite().getClockWise();
		plates.sort(Comparator.comparingInt(p -> p.getX() * readerRight.getStepX() + p.getZ() * readerRight.getStepZ()));
		int number = ArchiveRecord.labNumber(level.getSeed(), hatch.getX(), hatch.getY(), hatch.getZ());
		List<Block> engraved = List.of(
				BROKEN_LETTERS.get(random.nextInt(BROKEN_LETTERS.size())).get(),
				DIGITS.get(number / 100).get(),
				DIGITS.get(number / 10 % 10).get(),
				DIGITS.get(number % 10).get());
		for (int i = 0; i < plates.size(); i++) {
			level.setBlock(plates.get(i),
					engraved.get(i).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing),
					Block.UPDATE_CLIENTS);
		}
	}

	/** Ladder and stone walls from the lab's top up to the slab under the hatch. */
	private static void placeShaft(WorldGenLevel level, BlockPos hatch, int fromY, Rotation rotation) {
		BlockState ladder = ladder(rotation);
		for (int y = fromY; y < hatch.getY() - 1; y++) {
			for (int[] wall : SHAFT_WALLS) {
				level.setBlock(offset(hatch, wall[0], y - hatch.getY(), wall[1], rotation),
						Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			level.setBlock(hatch.atY(y), ladder, Block.UPDATE_CLIENTS);
		}
	}

	/** The surface: stone slab, ground, hatch, boulders, canopy and the biome's own touches. */
	private static void placeEntrance(WorldGenLevel level, BlockPos hatch, Rotation rotation,
			LabCamouflage camouflage) {
		for (int[] cell : FOOTPRINT) {
			boolean isHatch = cell[0] == 0 && cell[1] == 0;
			set(level, hatch, cell[0], -1, cell[1], rotation,
					isHatch ? ladder(rotation) : Blocks.STONE.defaultBlockState());
			BlockState ground = isHatch ? trapdoor(rotation)
					: (contains(DISTURBED, cell) ? camouflage.groundSub() : camouflage.groundTop()).defaultBlockState();
			set(level, hatch, cell[0], 0, cell[1], rotation, ground);
			// Only plants are cleared: a neighbour one block higher keeps its own ground.
			for (int dy = 1; dy <= 3; dy++) {
				BlockPos pos = offset(hatch, cell[0], dy, cell[1], rotation);
				if (isPlant(level, pos, level.getBlockState(pos))) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		for (int[] cell : BOULDERS) {
			set(level, hatch, cell[0], 1, cell[1], rotation, camouflage.boulder().defaultBlockState());
			if (camouflage.accent() != null) {
				set(level, hatch, cell[0], 2, cell[1], rotation, turned(camouflage.accent(), rotation));
			}
		}
		if (camouflage.canopy() != LabCamouflage.Canopy.NONE && camouflage.log() != null
				&& camouflage.leaves() != null) {
			BlockState leaves = camouflage.canopy() == LabCamouflage.Canopy.TREE
					? camouflage.leaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
					: camouflage.leaves().defaultBlockState();
			for (int[] cell : STUMPS) {
				set(level, hatch, cell[0], 1, cell[1], rotation, camouflage.log().defaultBlockState());
			}
			for (int[] cell : CROWN_LOW) {
				set(level, hatch, cell[0], 1, cell[1], rotation, leaves);
			}
			for (int[] cell : CROWN_HIGH) {
				set(level, hatch, cell[0], 2, cell[1], rotation, leaves);
			}
		}
		for (LabCamouflage.Decoration decoration : camouflage.decorations()) {
			set(level, hatch, decoration.dx(), decoration.dy(), decoration.dz(), rotation,
					turned(decoration.state(), rotation));
		}
	}

	private static BlockState ladder(Rotation rotation) {
		return turned(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), rotation);
	}

	/** The one element that never changes with the biome: the oxidized copper hatch, shut, flush with the ground. */
	private static BlockState trapdoor(Rotation rotation) {
		return turned(Blocks.COPPER_TRAPDOOR.weathering().oxidized().defaultBlockState()
				.setValue(TrapDoorBlock.HALF, Half.TOP)
				.setValue(TrapDoorBlock.OPEN, false)
				.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), rotation);
	}

	/**
	 * {@code state} turned with the entrance. {@code BlockState#rotate(Rotation)} is deprecated only in
	 * NeoForge's patch, in favour of a positional overload Fabric does not have; this file is shared by
	 * both loaders, and nothing here depends on a position.
	 */
	@SuppressWarnings("deprecation")
	private static BlockState turned(BlockState state, Rotation rotation) {
		return state.rotate(rotation);
	}

	private static void set(WorldGenLevel level, BlockPos hatch, int dx, int dy, int dz, Rotation rotation,
			BlockState state) {
		level.setBlock(offset(hatch, dx, dy, dz, rotation), state, Block.UPDATE_CLIENTS);
	}

	/** {@code hatch} plus an entrance offset turned by {@code rotation} around the hatch. */
	private static BlockPos offset(BlockPos hatch, int dx, int dy, int dz, Rotation rotation) {
		BlockPos turned = new BlockPos(dx, dy, dz).rotate(rotation);
		return hatch.offset(turned.getX(), turned.getY(), turned.getZ());
	}

	private static boolean contains(int[][] cells, int[] cell) {
		for (int[] candidate : cells) {
			if (candidate[0] == cell[0] && candidate[1] == cell[1]) {
				return true;
			}
		}
		return false;
	}

	private static int[][] footprint() {
		List<int[]> cells = new ArrayList<>();
		for (int dx = -2; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				cells.add(new int[] {dx, dz});
			}
		}
		return cells.toArray(new int[0][]);
	}
}
