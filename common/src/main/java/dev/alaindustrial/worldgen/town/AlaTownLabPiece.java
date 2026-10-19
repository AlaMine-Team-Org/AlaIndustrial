package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * {@code alaindustrial:ala_town_lab} — the abandoned lab under a town hall (R29 of MOD-824, MOD-830): the hatch
 * in the hall's floor, a ladder shaft straight down and one of the seven approved labs at the bottom. The labs,
 * the depth, the plaque and the shaft are the single lab's ({@link AbandonedLabPlacer}); what differs is how the
 * pieces of a structure are placed.
 *
 * <p><b>Decided when the town is laid out, written chunk by chunk.</b> A structure piece is placed once per
 * chunk it crosses, each time only inside that chunk, so everything random — which lab, its turn, its depth, the
 * lost letter of its plaque — is drawn once when the town is laid out and saved with the piece. The plaque's
 * digits are {@code ArchiveRecord.labNumber} of the hatch, as for the single lab.
 *
 * <p><b>Rock.</b> The single lab looks for solid rock and gives the site up without it. A town cannot move its
 * hall, so the check is done before the town is laid out on the generator's own terrain columns — sparse, a
 * 3 × 3 grid over the lab — and a town whose lab finds no rock at any tried depth is not placed (spec
 * {@code ala_town.md}). What the grid misses, a cave from a carver or an aquifer pocket, is filled: before the
 * lab is placed, air and fluid in its box and one block around it become stone, so the lab always sits in rock
 * and nothing flows into it.
 *
 * <p><b>The lab's number reaches the record service</b> (R42): the chunk that holds the hatch hands it to
 * {@link RecordNumberLedger#noteLabNumber} on the server thread. Single labs are not noted.
 */
public final class AlaTownLabPiece extends StructurePiece {

	/** How far apart the rock samples stand: a 3 × 3 grid over the lab plus the hatch column. */
	private static final int ROCK_GRID = 3;

	private final String lab;
	private final Rotation rotation;
	private final BlockPos hatch;
	private final int depth;
	private final int letter;
	private final StructureTemplate template;
	private final AbandonedLabPlacer.Shaft shaft;
	private final BlockPos templateOrigin;
	private final BoundingBox labBox;

	private AlaTownLabPiece(StructureTemplateManager templates, String lab, Rotation rotation, BlockPos hatch,
			int depth, int letter, AbandonedLabPlacer.Shaft shaft) {
		super(AlaTownTypes.LAB_PIECE, 0, BoundingBox.fromCorners(hatch, hatch));
		this.lab = lab;
		this.rotation = rotation;
		this.hatch = hatch;
		this.depth = depth;
		this.letter = letter;
		this.template = templates.getOrCreate(templateId(lab));
		this.shaft = shaft;
		this.templateOrigin = hatch.below(depth).subtract(shaft.foot());
		this.labBox = template.getBoundingBox(settings(), templateOrigin);
		this.boundingBox = BoundingBox.encapsulating(labBox, new BoundingBox(hatch.getX() - 1, shaftFrom(),
				hatch.getZ() - 1, hatch.getX() + 1, hatch.getY(), hatch.getZ() + 1));
	}

	/** Read back from a saved chunk; registered as the piece type's loader. */
	public AlaTownLabPiece(StructureTemplateManager templates, CompoundTag tag) {
		super(AlaTownTypes.LAB_PIECE, tag);
		this.lab = tag.getStringOr("Lab", AbandonedLabPlacer.LABS.getFirst());
		this.rotation = tag.read("Rotation", Rotation.CODEC).orElse(Rotation.NONE);
		this.hatch = new BlockPos(tag.getIntOr("HatchX", 0), tag.getIntOr("HatchY", 0), tag.getIntOr("HatchZ", 0));
		this.depth = tag.getIntOr("Depth", AbandonedLabPlacer.MIN_DEPTH);
		this.letter = tag.getIntOr("Letter", 0);
		this.template = templates.getOrCreate(templateId(lab));
		AbandonedLabPlacer.Shaft read = AbandonedLabPlacer.Shaft.of(template);
		this.shaft = read != null ? read : new AbandonedLabPlacer.Shaft(BlockPos.ZERO, 0);
		this.templateOrigin = hatch.below(depth).subtract(shaft.foot());
		this.labBox = template.getBoundingBox(settings(), templateOrigin);
	}

	/**
	 * The lab under {@code hatch}, drawn from {@code random} — or empty when the lab template is broken, the world
	 * is too shallow there, or no tried depth finds rock on the generator's columns.
	 */
	static Optional<AlaTownLabPiece> under(BlockPos hatch, RandomSource random, StructureTemplateManager templates,
			ChunkGenerator generator, LevelHeightAccessor height, RandomState randomState) {
		String lab = AbandonedLabPlacer.LABS.get(random.nextInt(AbandonedLabPlacer.LABS.size()));
		Rotation rotation = Rotation.getRandom(random);
		int letter = random.nextInt(AbandonedLabPlacer.BROKEN_LETTERS.size());
		StructureTemplate template = templates.getOrCreate(templateId(lab));
		AbandonedLabPlacer.Shaft shaft = AbandonedLabPlacer.Shaft.of(template);
		if (shaft == null) {
			Industrialization.LOGGER.error("[ala_town] lab template '{}' is missing or has no entry ladder", lab);
			return Optional.empty();
		}
		int topAboveFoot = shaft.top() - shaft.foot().getY();
		int minDepth = Math.max(AbandonedLabPlacer.MIN_DEPTH,
				topAboveFoot + 1 + AbandonedLabPlacer.MIN_SHAFT_ABOVE_LAB);
		int maxDepth = Math.min(AbandonedLabPlacer.MAX_DEPTH,
				hatch.getY() - (height.getMinY() + AbandonedLabPlacer.FLOOR_ABOVE_MIN_Y));
		if (maxDepth < minDepth) {
			return Optional.empty();
		}
		RockColumns rock = new RockColumns(generator, height, randomState);
		for (int attempt = 0; attempt < AbandonedLabPlacer.DEPTH_ATTEMPTS; attempt++) {
			int depth = minDepth + random.nextInt(maxDepth - minDepth + 1);
			AlaTownLabPiece piece = new AlaTownLabPiece(templates, lab, rotation, hatch, depth, letter, shaft);
			if (rock.isSolid(piece.labBox.inflatedBy(AbandonedLabPlacer.LAB_READ_MARGIN))) {
				return Optional.of(piece);
			}
		}
		return Optional.empty();
	}

	/** Where the hatch is: the cell of the hall's {@code lab_hatch} marker. */
	public BlockPos hatch() {
		return hatch;
	}

	/** The lab's own box, without the shaft: what the town's other pieces must not cut. */
	public BoundingBox labBox() {
		return labBox;
	}

	/** What was drawn for this lab, for a golden layout line: which lab, its turn, depth and lost letter. */
	public String describe() {
		return "lab=" + lab + " rot=" + rotation.name() + " depth=" + depth + " letter=" + letter;
	}

	/** The plaque's digits, {@code ArchiveRecord.labNumber} of the hatch in this world. */
	public int labNumber(long worldSeed) {
		return ArchiveRecord.labNumber(worldSeed, hatch.getX(), hatch.getY(), hatch.getZ());
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Lab", lab);
		tag.store("Rotation", Rotation.CODEC, rotation);
		tag.putInt("HatchX", hatch.getX());
		tag.putInt("HatchY", hatch.getY());
		tag.putInt("HatchZ", hatch.getZ());
		tag.putInt("Depth", depth);
		tag.putInt("Letter", letter);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
		fillToRock(level, chunkBox);
		template.placeInWorld(level, templateOrigin, templateOrigin, settings().setBoundingBox(chunkBox), random,
				Block.UPDATE_CLIENTS);
		engravePlaque(level, chunkBox);
		placeShaft(level, chunkBox);
		if (chunkBox.isInside(hatch)) {
			noteLabNumber(level);
		}
	}

	/** Air and fluid in the lab's box and one block around it become rock: see the class comment. */
	private void fillToRock(WorldGenLevel level, BoundingBox chunkBox) {
		BoundingBox shell = labBox.inflatedBy(AbandonedLabPlacer.LAB_READ_MARGIN);
		int minX = Math.max(shell.minX(), chunkBox.minX());
		int maxX = Math.min(shell.maxX(), chunkBox.maxX());
		int minZ = Math.max(shell.minZ(), chunkBox.minZ());
		int maxZ = Math.min(shell.maxZ(), chunkBox.maxZ());
		int minY = Math.max(shell.minY(), level.getMinY());
		int maxY = Math.min(shell.maxY(), level.getMaxY());
		for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				level.setBlock(pos, (pos.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState(),
						Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * The lab's plaque: the four plates the template carries, found in the template itself (a plaque may cross a
	 * chunk border, and each chunk must engrave the same plate the same way), letter first.
	 */
	private void engravePlaque(WorldGenLevel level, BoundingBox chunkBox) {
		List<BlockPos> plates = new ArrayList<>();
		Map<BlockPos, Direction> facing = new HashMap<>();
		for (Supplier<Block> plate : plateBlocks()) {
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(templateOrigin, settings(),
					plate.get())) {
				plates.add(info.pos());
				facing.put(info.pos(), info.state().getValue(HorizontalDirectionalBlock.FACING));
			}
		}
		if (plates.size() != 4) {
			Industrialization.LOGGER.warn("[ala_town] lab '{}' has {} plaque plates, expected 4", lab, plates.size());
			return;
		}
		Direction face = facing.get(plates.getFirst());
		AbandonedLabPlacer.sortForReader(plates, face);
		List<Block> engraved = AbandonedLabPlacer.plaque(AbandonedLabPlacer.BROKEN_LETTERS.get(letter).get(),
				labNumber(level.getSeed()));
		for (int i = 0; i < plates.size(); i++) {
			if (chunkBox.isInside(plates.get(i))) {
				level.setBlock(plates.get(i),
						engraved.get(i).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face),
						Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Ladder and stone walls from the lab's top to the hall's floor, and the copper hatch in the floor. */
	private void placeShaft(WorldGenLevel level, BoundingBox chunkBox) {
		BlockState ladder = AbandonedLabPlacer.ladder(rotation);
		for (int y = shaftFrom(); y < hatch.getY(); y++) {
			BlockPos column = hatch.atY(y);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				setInside(level, chunkBox, column.relative(side), Blocks.STONE.defaultBlockState());
			}
			setInside(level, chunkBox, column, ladder);
		}
		setInside(level, chunkBox, hatch, AbandonedLabPlacer.trapdoor(rotation));
	}

	// WorldGenRegion#getServer is the server the chunk is generated for; only its task queue is used.
	private void noteLabNumber(WorldGenLevel level) {
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		int number = labNumber(level.getSeed());
		server.execute(() -> RecordNumberLedger.get(server).noteLabNumber(number));
	}

	private static void setInside(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
		if (chunkBox.isInside(pos)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/** The first shaft level above the lab: one above the template's top. */
	private int shaftFrom() {
		return hatch.getY() - depth + (shaft.top() - shaft.foot().getY()) + 1;
	}

	private StructurePlaceSettings settings() {
		return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(shaft.foot())
				.setIgnoreEntities(true);
	}

	private static List<Supplier<Block>> plateBlocks() {
		List<Supplier<Block>> plates = new ArrayList<>(AbandonedLabPlacer.DIGITS);
		plates.addAll(AbandonedLabPlacer.BROKEN_LETTERS);
		return plates;
	}

	private static net.minecraft.resources.Identifier templateId(String lab) {
		return Industrialization.id("abandoned_lab/" + lab);
	}

	/**
	 * The generator's own terrain columns around the lab, read once and kept: a column is a full noise column, and
	 * every tried depth asks the same ones.
	 */
	private static final class RockColumns {
		private final ChunkGenerator generator;
		private final LevelHeightAccessor height;
		private final RandomState randomState;
		private final Map<Long, NoiseColumn> columns = new HashMap<>();

		RockColumns(ChunkGenerator generator, LevelHeightAccessor height, RandomState randomState) {
			this.generator = generator;
			this.height = height;
			this.randomState = randomState;
		}

		/** Every sampled cell of {@code box} is rock: a 3 × 3 grid of columns over it, all of its height. */
		boolean isSolid(BoundingBox box) {
			for (int i = 0; i < ROCK_GRID; i++) {
				int x = box.minX() + (box.maxX() - box.minX()) * i / (ROCK_GRID - 1);
				for (int j = 0; j < ROCK_GRID; j++) {
					int z = box.minZ() + (box.maxZ() - box.minZ()) * j / (ROCK_GRID - 1);
					NoiseColumn column = columns.computeIfAbsent(BlockPos.asLong(x, 0, z),
							key -> generator.getBaseColumn(x, z, height, randomState));
					for (int y = box.minY(); y <= box.maxY(); y++) {
						if (!AbandonedLabPlacer.isRock(column.getBlock(y))) {
							return false;
						}
					}
				}
			}
			return true;
		}
	}
}
