package dev.alaindustrial.command;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The workbench of {@code /ala town edit} (MOD-845): lays the pieces of an ALA town style out in rows next to the
 * player, each as its template stands in the mod's data, with a structure block in SAVE mode already set to the
 * piece's name, corner and size. The owner edits a piece and presses "Save"; the game writes it into the world's
 * {@code generated/alaindustrial/structure/ala_town/<style>/<piece>.nbt}, from where
 * {@code tools/import_town_templates.py} takes it into the mod's resources.
 *
 * <p>Three things make the saved file a faithful round trip of the template. The template is placed with a known
 * shape and no side effects ({@link #QUIET}), so bars and fences keep the connections the template gives them. It is
 * placed without processors, so the jigsaw blocks that link the town and the data markers ({@code lab_hatch}) stay
 * as blocks and are saved back. And every cell of the piece's box that the template leaves out is filled with a
 * structure void first: the structure block skips structure voids when it saves, so a cell the template did not
 * have (the terrain under a ground piece, natural ground in a building's floor) is again left out, instead of being
 * saved as air that would cut into the terrain of every town.
 */
public final class TownEditBench {

	/** Folder of the mod's town templates under the {@code structure} data folder. */
	static final String TOWN = "ala_town/";

	/** Empty blocks between two pieces: the structure block sits one cell off a piece's north-west corner. */
	static final int GAP = 4;

	/** A row of pieces ends once it is this long; the next row starts behind the deepest piece of this one. */
	static final int ROW_LENGTH = 128;

	/** Where the structure block of a piece stands, relative to the piece's corner, and its offset back to it. */
	static final BlockPos STRUCTURE_BLOCK_OFFSET = new BlockPos(-1, 0, -1);

	/**
	 * How the bench writes a piece: to the clients, but with no side effect on the neighbours or on placement, the way
	 * a strict structure block loads a template. A placed block does not reshape the bars beside it, and sand on a
	 * roof does not fall into the structure void under it.
	 */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

	private TownEditBench() {
	}

	/** One piece laid out on the bench: its template, its corner relative to the bench origin and its size. */
	public record Slot(Identifier template, BlockPos offset, Vec3i size) {
		/** The piece's name in its style, {@code town_hall}. */
		public String piece() {
			return template.getPath().substring(template.getPath().lastIndexOf('/') + 1);
		}
	}

	/** Styles that have templates: the folders under {@code alaindustrial:ala_town/}. */
	public static List<String> styles(StructureTemplateManager templates) {
		TreeSet<String> styles = new TreeSet<>();
		templates.listTemplates().filter(TownEditBench::isTown).forEach(id -> {
			String rest = id.getPath().substring(TOWN.length());
			if (rest.indexOf('/') > 0) {
				styles.add(rest.substring(0, rest.indexOf('/')));
			}
		});
		return List.copyOf(styles);
	}

	/** Pieces of {@code style}, by name. */
	public static List<String> pieces(StructureTemplateManager templates, String style) {
		String prefix = TOWN + style + "/";
		TreeSet<String> pieces = new TreeSet<>();
		templates.listTemplates().filter(TownEditBench::isTown)
				.filter(id -> id.getPath().startsWith(prefix) && id.getPath().indexOf('/', prefix.length()) < 0)
				.forEach(id -> pieces.add(id.getPath().substring(prefix.length())));
		return List.copyOf(pieces);
	}

	/**
	 * The bench: {@code pieces} of {@code style} in rows along +x, rows stepping along +z, {@link #GAP} blocks apart.
	 * A piece whose template does not load is left out.
	 */
	public static List<Slot> plan(StructureTemplateManager templates, String style, List<String> pieces) {
		List<Slot> slots = new ArrayList<>();
		int x = 0;
		int z = 0;
		int rowDepth = 0;
		for (String piece : pieces) {
			Identifier id = Industrialization.id(TOWN + style + "/" + piece);
			Optional<StructureTemplate> template = templates.get(id);
			if (template.isEmpty()) {
				continue;
			}
			Vec3i size = template.get().getSize();
			if (x > 0 && x + size.getX() > ROW_LENGTH) {
				x = 0;
				z += rowDepth + GAP;
				rowDepth = 0;
			}
			slots.add(new Slot(id, new BlockPos(x, 0, z), size));
			x += size.getX() + GAP;
			rowDepth = Math.max(rowDepth, size.getZ());
		}
		return slots;
	}

	/**
	 * Places {@code slot} with its corner at {@code origin + slot.offset()} and its structure block in SAVE mode next
	 * to it; returns the structure block's position.
	 */
	public static BlockPos place(ServerLevel level, BlockPos origin, Slot slot) {
		StructureTemplate template = LineStructures.templates(level).getOrCreate(slot.template());
		BlockPos corner = origin.offset(slot.offset());
		Vec3i size = template.getSize();
		BlockState voidState = Blocks.STRUCTURE_VOID.defaultBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(size.getX() - 1, size.getY() - 1,
				size.getZ() - 1))) {
			level.setBlock(pos, voidState, QUIET);
		}
		// Known shape, as the town's own pool elements place it (SinglePoolElement): a fence or bars keep the
		// connections the template has, instead of joining whatever stands next to them on the bench.
		template.placeInWorld(level, corner, corner, new StructurePlaceSettings().setKnownShape(true),
				level.getRandom(), QUIET);

		BlockPos at = corner.offset(STRUCTURE_BLOCK_OFFSET);
		BlockState state = Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.SAVE);
		level.setBlock(at, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof StructureBlockEntity block) {
			block.setStructureName(slot.template());
			block.setStructurePos(BlockPos.ZERO.subtract(STRUCTURE_BLOCK_OFFSET));
			block.setStructureSize(size);
			block.setMode(StructureMode.SAVE);
			block.setIgnoreEntities(true);
			block.setShowBoundingBox(true);
			block.setChanged();
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
		return at;
	}

	private static boolean isTown(Identifier id) {
		return id.getNamespace().equals(Industrialization.MOD_ID) && id.getPath().startsWith(TOWN);
	}
}
