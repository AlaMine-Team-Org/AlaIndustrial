package dev.alaindustrial.settlement.works;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.worldgen.town.AlaTownStructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * What one building of a town should look like, cell by cell, in the world (MOD-857): the cells its comparison
 * counts, the block each should hold, and the key points each belongs to. Built once per building and load from its
 * intact template, its ruin template and its definition ({@link TownBuildingDef}); never saved.
 *
 * <p><b>Which cells count.</b> Every cell of the intact template that holds a block, and every cell the intact
 * template keeps empty where the ruin has a block — the ruin's debris. Cells the intact template leaves out
 * (structure void) do not count, nor do the template's structure blocks (the lab hatch's marker). An empty cell the
 * ruin also left empty does not count either: the ground the terrain adjustment pushes into a building is not the
 * building's to score. A jigsaw block counts as what it turns into in a town ({@code final_state}).
 *
 * <p><b>A cell matches</b> when the world holds the expected block, or air where air is expected; the block's state
 * (a door open, a lamp lit, stairs turned) does not count. The full rotated state is kept for the executor that puts
 * the block back ({@link #expected}).
 *
 * <p>This is the plan the builder drone (MOD-858) works from: {@link #size()} cells, each with its world position, the
 * state to put there, and whether it is debris to take away.
 */
public final class BuildingPlan {

	private final long[] cells;
	private final BlockState[] expected;
	private final BlockState[] ruin;
	private final boolean[] debris;
	private final int[] keyMask;
	private final List<String> keyNames;
	private final double restoredAt;
	/** The box around every cell, {minX, minY, minZ, maxX, maxY, maxZ}; empty plan: an empty box. */
	private final int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
			Integer.MIN_VALUE, Integer.MIN_VALUE};

	/** The block that marks a drone nest's spot on the town hall's roof (MOD-858): a yellow carpet. */
	public static final Identifier NEST_SPOT = Identifier.withDefaultNamespace("yellow_carpet");

	/** The lab hatches of the building in the world, packed: the drone keeps off their shafts (MOD-858, R93). */
	private final long[] hatches;
	/** The ring around the footprint a clearing frees of terrain, packed, top layer first ({@link #ringPos}). */
	private final long[] ring;

	private BuildingPlan(List<Cell> list, List<String> keyNames, double restoredAt, long[] hatches, int ringWidth,
			Predicate<BlockPos> foreign) {
		this.hatches = hatches;
		int size = list.size();
		this.cells = new long[size];
		this.expected = new BlockState[size];
		this.ruin = new BlockState[size];
		this.debris = new boolean[size];
		this.keyMask = new int[size];
		for (int i = 0; i < size; i++) {
			Cell cell = list.get(i);
			cells[i] = cell.pos;
			expected[i] = cell.state;
			ruin[i] = cell.ruin;
			debris[i] = cell.debris;
			keyMask[i] = cell.keys;
			int[] xyz = {BlockPos.getX(cell.pos), BlockPos.getY(cell.pos), BlockPos.getZ(cell.pos)};
			for (int axis = 0; axis < 3; axis++) {
				bounds[axis] = Math.min(bounds[axis], xyz[axis]);
				bounds[axis + 3] = Math.max(bounds[axis + 3], xyz[axis]);
			}
		}
		this.keyNames = List.copyOf(keyNames);
		this.restoredAt = restoredAt;
		this.ring = ring(ringWidth, foreign);
	}

	/**
	 * The ring of {@code width} columns around the footprint (every column a cell of the plan stands in), from the
	 * layer over the building's floor (its lowest layer) to its top, top layer first so sand over sand comes off before
	 * what holds it up; cells {@code foreign} claims — another building's — are left out.
	 */
	private long[] ring(int width, Predicate<BlockPos> foreign) {
		if (width <= 0 || cells.length == 0) {
			return new long[0];
		}
		Set<Long> footprint = new HashSet<>();
		for (long cell : cells) {
			footprint.add(BlockPos.asLong(BlockPos.getX(cell), 0, BlockPos.getZ(cell)));
		}
		Set<Long> columns = new TreeSet<>();
		for (long column : footprint) {
			int x = BlockPos.getX(column);
			int z = BlockPos.getZ(column);
			for (int dx = -width; dx <= width; dx++) {
				for (int dz = -width; dz <= width; dz++) {
					long next = BlockPos.asLong(x + dx, 0, z + dz);
					if (!footprint.contains(next)) {
						columns.add(next);
					}
				}
			}
		}
		List<Long> out = new ArrayList<>();
		for (int y = bounds[4]; y >= groundLevel(); y--) {
			for (long column : columns) {
				BlockPos pos = new BlockPos(BlockPos.getX(column), y, BlockPos.getZ(column));
				if (!foreign.test(pos)) {
					out.add(pos.asLong());
				}
			}
		}
		return out.stream().mapToLong(Long::longValue).toArray();
	}

	/** The building's ground level: the layer over its floor, its lowest layer. What stands there stands free. */
	public int groundLevel() {
		return bounds[1] + 1;
	}

	/**
	 * How many cells the ring around the building holds (owner's round 4 of stage 6): a clearing takes the terrain and
	 * rubble out of them after the building's own cells, so its walls stand free. Never counted by the comparison.
	 */
	public int ringSize() {
		return ring.length;
	}

	/** Where ring cell {@code index} is in the world, {@code 0 <= index < }{@link #ringSize()}. */
	public BlockPos ringPos(int index) {
		return BlockPos.of(ring[index]);
	}

	/**
	 * Where an executor's cell {@code index} is: a cell of the plan below {@link #size()}, a ring cell from there on —
	 * one numbering for a clearing's whole walk.
	 */
	public BlockPos target(int index) {
		return index < cells.length ? pos(index) : ringPos(index - cells.length);
	}

	private record Cell(long pos, BlockState state, BlockState ruin, boolean debris, int keys) {
	}

	/**
	 * The box around every cell of the building, or empty for a plan with no cells. The builder drone's route search
	 * reaches over it (owner's round 3): a hall's fittings lie inside, and the way in is its door.
	 */
	public Optional<BoundingBox> box() {
		if (cells.length == 0) {
			return Optional.empty();
		}
		return Optional.of(new BoundingBox(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]));
	}

	/**
	 * Whether {@code pos} lies in the box around the building's cells — its footprint and height (MOD-858: the
	 * dispatcher and the drone nests work only inside their town hall).
	 */
	public boolean contains(BlockPos pos) {
		return pos.getX() >= bounds[0] && pos.getY() >= bounds[1] && pos.getZ() >= bounds[2]
				&& pos.getX() <= bounds[3] && pos.getY() <= bounds[4] && pos.getZ() <= bounds[5];
	}

	/**
	 * Whether {@code pos} stands over the building's footprint (MOD-858, owner's round 1): inside the box around its
	 * cells seen from above, no lower than its lowest layer and at most {@code above} blocks over its highest. A drone
	 * nest anywhere on or over the town hall's roof answers yes.
	 */
	public boolean over(BlockPos pos, int above) {
		return pos.getX() >= bounds[0] && pos.getZ() >= bounds[2] && pos.getX() <= bounds[3]
				&& pos.getZ() <= bounds[5] && pos.getY() >= bounds[1] && pos.getY() <= bounds[4] + Math.max(0, above);
	}

	/**
	 * Whether cell {@code index} lies on the shaft of the lab under the building: in the column of a lab hatch (the
	 * ruin's {@code lab_hatch} marker), at the hatch or below it. A builder drone never touches such a cell (MOD-858,
	 * R93) — a floor block put there would seal the way down to the lab.
	 */
	public boolean onLabShaft(int index) {
		long cell = cells[index];
		for (long hatch : hatches) {
			if (BlockPos.getX(hatch) == BlockPos.getX(cell) && BlockPos.getZ(hatch) == BlockPos.getZ(cell)
					&& BlockPos.getY(cell) <= BlockPos.getY(hatch)) {
				return true;
			}
		}
		return false;
	}

	/** How many cells the comparison counts. */
	public int size() {
		return cells.length;
	}

	/** Where cell {@code index} is in the world. */
	public BlockPos pos(int index) {
		return BlockPos.of(cells[index]);
	}

	/** The world position of cell {@code index}, packed ({@link BlockPos#asLong()}). */
	public long packedPos(int index) {
		return cells[index];
	}

	/** The state cell {@code index} should hold — rotated with the building; air for a debris cell. */
	public BlockState expected(int index) {
		return expected[index];
	}

	/** Whether cell {@code index} is the ruin's debris: the intact template wants air where the ruin has a block. */
	public boolean debris(int index) {
		return debris[index];
	}

	/**
	 * The block the ruin's template has at cell {@code index}, rotated with the building — air where the ruin left the
	 * cell empty. The builder drone (MOD-858) takes the ruin's own block away where the intact template wants another
	 * one: it is the damage the ruin was made with, not something a player put there.
	 */
	public BlockState ruin(int index) {
		return ruin[index];
	}

	/**
	 * Whether {@code world} at cell {@code index} matches the plan: the same block, or air where air belongs. A drone
	 * nest standing where the plan wants a yellow carpet matches too: the carpets mark the drone pad's nest spots,
	 * and a nest is put on one in their place (MOD-858, owner decision 2026-10-21).
	 */
	public boolean matches(int index, BlockState world) {
		BlockState want = expected[index];
		if (want.isAir()) {
			return world.isAir();
		}
		return world.is(want.getBlock())
				|| world.is(ModContent.DRONE_NEST.get())
				&& BuiltInRegistries.BLOCK.getKey(want.getBlock()).equals(NEST_SPOT);
	}

	/** The key points of cell {@code index}, one bit per index into {@link #keyNames()}. */
	public int keyMask(int index) {
		return keyMask[index];
	}

	/** The translation keys of the key points, in the definition's order. */
	public List<String> keyNames() {
		return keyNames;
	}

	/** The share «restored» needs. */
	public double restoredAt() {
		return restoredAt;
	}

	/**
	 * The plan of {@code building}, or empty for a building with no intact template, or one whose templates the server
	 * cannot read.
	 */
	public static Optional<BuildingPlan> of(StructureTemplateManager templates, TownBuilding building,
			TownBuildingDef definition) {
		return of(templates, building, definition, pos -> false);
	}

	/**
	 * The plan of {@code building}, as {@link #of(StructureTemplateManager, TownBuilding, TownBuildingDef)}, its ring
	 * leaving out the cells {@code foreign} claims (the town's other buildings).
	 */
	public static Optional<BuildingPlan> of(StructureTemplateManager templates, TownBuilding building,
			TownBuildingDef definition, Predicate<BlockPos> foreign) {
		if (building.intact().isEmpty()) {
			return Optional.empty();
		}
		Optional<Map<BlockPos, BlockState>> intact = cellsOf(templates, building.intact().get());
		Optional<Map<BlockPos, BlockState>> ruin = cellsOf(templates, building.template());
		if (intact.isEmpty() || ruin.isEmpty()) {
			return Optional.empty();
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(building.rotation());
		List<TownBuildingDef.KeyPoint> keys = definition.keyPoints();
		List<Cell> list = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : intact.get().entrySet()) {
			BlockPos local = entry.getKey();
			BlockState want = entry.getValue();
			BlockState was = ruin.get().getOrDefault(local, Blocks.AIR.defaultBlockState());
			boolean isDebris = want.isAir() && !was.isAir();
			if (want.isAir() && !isDebris) {
				continue;
			}
			int mask = 0;
			for (int k = 0; k < keys.size(); k++) {
				if (keys.get(k).covers(local, want)) {
					mask |= 1 << k;
				}
			}
			BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(building.origin());
			list.add(new Cell(world.asLong(), want.rotate(building.rotation()), was.rotate(building.rotation()),
					isDebris, mask));
		}
		// The template's own order, bottom layer first: an executor that walks the plan builds upwards.
		list.sort((a, b) -> {
			int byY = Integer.compare(BlockPos.getY(a.pos), BlockPos.getY(b.pos));
			return byY != 0 ? byY : Long.compare(a.pos, b.pos);
		});
		List<String> names = new ArrayList<>();
		keys.forEach(key -> names.add(key.name()));
		return Optional.of(new BuildingPlan(list, names, definition.restoredAt(),
				hatches(templates, building.template(), building.origin(), settings), definition.clearRing(), foreign));
	}

	/**
	 * The cells of template {@code id} in its own coordinates, as a town shows them: a jigsaw block as what it turns
	 * into, structure blocks left out. Read from the template's saved form — the template keeps its palette private.
	 */
	static Optional<Map<BlockPos, BlockState>> cellsOf(StructureTemplateManager templates, Identifier id) {
		Optional<StructureTemplate> template = templates.get(id);
		if (template.isEmpty()) {
			Industrialization.LOGGER.warn("[ala-towns] building template {} is not in the data", id);
			return Optional.empty();
		}
		CompoundTag tag = template.get().save(new CompoundTag());
		ListTag paletteTag = tag.getListOrEmpty(StructureTemplate.PALETTE_TAG);
		if (paletteTag.isEmpty()) {
			paletteTag = tag.getListOrEmpty(StructureTemplate.PALETTE_LIST_TAG).getListOrEmpty(0);
		}
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletteTag.getCompoundOrEmpty(i)));
		}
		Map<BlockPos, BlockState> out = new HashMap<>();
		ListTag blocks = tag.getListOrEmpty(StructureTemplate.BLOCKS_TAG);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.getCompoundOrEmpty(i);
			int index = block.getIntOr(StructureTemplate.BLOCK_TAG_STATE, -1);
			ListTag pos = block.getListOrEmpty(StructureTemplate.BLOCK_TAG_POS);
			if (index < 0 || index >= palette.size() || pos.size() < 3) {
				continue;
			}
			BlockState state = palette.get(index);
			if (state.is(Blocks.STRUCTURE_BLOCK)) {
				continue;
			}
			if (state.is(Blocks.JIGSAW)) {
				state = finalState(block.getCompoundOrEmpty(StructureTemplate.BLOCK_TAG_NBT));
			}
			out.put(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), state);
		}
		return Optional.of(out);
	}

	/** The world cells of template {@code id}'s lab hatch markers, placed at {@code origin} with {@code settings}. */
	private static long[] hatches(StructureTemplateManager templates, Identifier id, BlockPos origin,
			StructurePlaceSettings settings) {
		List<Long> found = new ArrayList<>();
		templates.get(id).ifPresent(template -> {
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings,
					Blocks.STRUCTURE_BLOCK)) {
				if (info.nbt() != null
						&& AlaTownStructure.LAB_HATCH.equals(info.nbt().getStringOr("metadata", ""))) {
					found.add(info.pos().asLong());
				}
			}
		});
		return found.stream().mapToLong(Long::longValue).toArray();
	}

	/** What a jigsaw block turns into, from its data; air when it names nothing the game knows. */
	private static BlockState finalState(CompoundTag data) {
		String text = data.getStringOr("final_state", "minecraft:air");
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, true).blockState();
		} catch (CommandSyntaxException e) {
			return Blocks.AIR.defaultBlockState();
		}
	}
}
