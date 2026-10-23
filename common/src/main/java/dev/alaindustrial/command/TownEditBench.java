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
 *
 * <p>The intact bench ({@link #planIntact}) is the same workbench for the building the drone restores a ruin to: the
 * intact template {@code alaindustrial:ala_town/<style>/intact/<piece>} ({@link #intact}), saved by its structure
 * block into {@code generated/alaindustrial/structure/ala_town/<style>/intact/<piece>.nbt} and taken into the mod by
 * {@code tools/import_town_templates.py --intact}. It starts from the ruin, or from the intact building saved so far,
 * and its box is as wide and long as the ruin with {@link #INTACT_HEADROOM} blocks more height for the lost roof. The
 * ruin itself is never saved over, and no town pool names an intact template, so world generation never places one.
 */
public final class TownEditBench {

	/** Folder of the mod's town templates under the {@code structure} data folder. */
	static final String TOWN = "ala_town/";

	/** Folder of a style's intact buildings: {@code ala_town/<style>/intact/<piece>}. */
	static final String INTACT = "intact";

	/**
	 * Folder of the intact-building candidates in the world's templates, beside {@link #TOWN} rather than in it so a
	 * candidate is never taken for a piece: {@code ala_town_candidates/<style>/<piece>_<variant>}, written into the
	 * world's {@code generated} folder by {@code tools/gen_town_house_candidates.py}.
	 */
	static final String CANDIDATES = "ala_town_candidates/";

	/** Blocks the intact bench's box rises above the ruin's: room for the roof the ruin lost. */
	public static final int INTACT_HEADROOM = 8;

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

	/**
	 * One piece laid out on the bench: the template placed, the name its structure block saves under, its corner
	 * relative to the bench origin, the size of its box and the structure block's mode (SAVE for a piece to edit, LOAD
	 * for a reference that pressing must not save over).
	 */
	public record Slot(Identifier template, Identifier saveAs, BlockPos offset, Vec3i size, StructureMode mode) {
		/** The piece's name in its style, {@code town_hall}. */
		public String piece() {
			return saveAs.getPath().substring(saveAs.getPath().lastIndexOf('/') + 1);
		}
	}

	/** The ruin template of {@code piece}: {@code alaindustrial:ala_town/<style>/<piece>}. */
	public static Identifier ruin(String style, String piece) {
		return Industrialization.id(TOWN + style + "/" + piece);
	}

	/** The intact building of {@code piece}: {@code alaindustrial:ala_town/<style>/intact/<piece>}. */
	public static Identifier intact(String style, String piece) {
		return Industrialization.id(TOWN + style + "/" + INTACT + "/" + piece);
	}

	/**
	 * The intact-building candidate {@code variant} of {@code piece}:
	 * {@code alaindustrial:ala_town_candidates/<style>/<piece>_<variant>}; a bare number {@code 2} means {@code v2}.
	 */
	public static Identifier candidate(String style, String piece, String variant) {
		String suffix = !variant.isEmpty() && variant.chars().allMatch(Character::isDigit) ? "v" + variant : variant;
		return Industrialization.id(CANDIDATES + style + "/" + piece + "_" + suffix);
	}

	/** Variants of {@code piece} that have a candidate, a leading {@code v} dropped: {@code 1}, {@code inlet}. */
	public static List<String> candidates(StructureTemplateManager templates, String style, String piece) {
		String prefix = CANDIDATES + style + "/" + piece + "_";
		TreeSet<String> variants = new TreeSet<>();
		templates.listTemplates()
				.filter(id -> id.getNamespace().equals(Industrialization.MOD_ID) && id.getPath().startsWith(prefix))
				.map(id -> id.getPath().substring(prefix.length()))
				.filter(rest -> !rest.isEmpty() && rest.indexOf('/') < 0)
				.forEach(rest -> variants.add(rest.matches("v[0-9]+") ? rest.substring(1) : rest));
		return List.copyOf(variants);
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

	/** Pieces of {@code style}, by name; the intact buildings under {@code <style>/intact/} are not pieces. */
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
			Identifier id = ruin(style, piece);
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
			slots.add(new Slot(id, id, new BlockPos(x, 0, z), size, StructureMode.SAVE));
			x += size.getX() + GAP;
			rowDepth = Math.max(rowDepth, size.getZ());
		}
		return slots;
	}

	/**
	 * The intact bench of {@code piece}: the intact building saved so far (the world's save first, then the mod's) or,
	 * before there is one, the ruin as the starting point; its structure block saves under {@link #intact}. The box is
	 * as wide and long as the ruin and {@link #INTACT_HEADROOM} blocks taller, never lower than the saved intact
	 * building and never over the structure block's limit. With {@code withRuin} the ruin stands first, to the west, as
	 * a reference whose structure block is in LOAD mode. Empty when the ruin does not load.
	 */
	public static List<Slot> planIntact(StructureTemplateManager templates, String style, String piece,
			boolean withRuin) {
		Identifier ruinId = ruin(style, piece);
		Optional<StructureTemplate> ruinTemplate = templates.get(ruinId);
		if (ruinTemplate.isEmpty()) {
			return List.of();
		}
		Vec3i size = ruinTemplate.get().getSize();
		Identifier intactId = intact(style, piece);
		Optional<StructureTemplate> saved = templates.get(intactId);
		int height = Math.min(StructureBlockEntity.MAX_SIZE_PER_AXIS,
				Math.max(size.getY() + INTACT_HEADROOM, saved.map(t -> t.getSize().getY()).orElse(0)));
		List<Slot> slots = new ArrayList<>();
		int x = 0;
		if (withRuin) {
			slots.add(new Slot(ruinId, ruinId, BlockPos.ZERO, size, StructureMode.LOAD));
			x = size.getX() + GAP;
		}
		slots.add(new Slot(saved.isPresent() ? intactId : ruinId, intactId, new BlockPos(x, 0, 0),
				new Vec3i(size.getX(), height, size.getZ()), StructureMode.SAVE));
		return slots;
	}

	/**
	 * The intact bench of {@code piece} started from a candidate instead of the ruin: the ruin to the west as a
	 * reference in LOAD mode, and the candidate in a SAVE box that saves under {@link #intact}, as wide and long as the
	 * ruin and {@link #INTACT_HEADROOM} blocks taller (never lower than the candidate). Empty when the ruin or the
	 * candidate does not load.
	 */
	public static List<Slot> planCandidate(StructureTemplateManager templates, String style, String piece,
			Identifier candidateId) {
		Identifier ruinId = ruin(style, piece);
		Optional<StructureTemplate> ruinTemplate = templates.get(ruinId);
		Optional<StructureTemplate> candidate = templates.get(candidateId);
		if (ruinTemplate.isEmpty() || candidate.isEmpty()) {
			return List.of();
		}
		Vec3i size = ruinTemplate.get().getSize();
		int height = Math.min(StructureBlockEntity.MAX_SIZE_PER_AXIS,
				Math.max(size.getY() + INTACT_HEADROOM, candidate.get().getSize().getY()));
		return List.of(new Slot(ruinId, ruinId, BlockPos.ZERO, size, StructureMode.LOAD),
				new Slot(candidateId, intact(style, piece), new BlockPos(size.getX() + GAP, 0, 0),
						new Vec3i(size.getX(), height, size.getZ()), StructureMode.SAVE));
	}

	/**
	 * Places {@code slot} with its corner at {@code origin + slot.offset()}, its box filled with structure voids first,
	 * and its structure block in the slot's mode next to it; returns the structure block's position.
	 */
	public static BlockPos place(ServerLevel level, BlockPos origin, Slot slot) {
		StructureTemplate template = LineStructures.templates(level).getOrCreate(slot.template());
		BlockPos corner = origin.offset(slot.offset());
		Vec3i size = slot.size();
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
		BlockState state = Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, slot.mode());
		level.setBlock(at, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof StructureBlockEntity block) {
			block.setStructureName(slot.saveAs());
			block.setStructurePos(BlockPos.ZERO.subtract(STRUCTURE_BLOCK_OFFSET));
			block.setStructureSize(size);
			block.setMode(slot.mode());
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
