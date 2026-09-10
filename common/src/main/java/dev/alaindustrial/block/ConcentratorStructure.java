package dev.alaindustrial.block;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Assembly and disassembly of the two-by-two-by-two Mirror Concentrator (MOD-603).
 *
 * <p><b>The player picks the direction by building, not by choosing.</b> The grown panel is always
 * a bottom corner of the structure, and there are four boxes it can be the corner of. Whichever one
 * the player fills is the one that assembles, so "which way does it face" needs no key, no packet
 * and nothing stored before assembly — the world already holds the answer in the sections the player
 * put down. The solar family carries no {@code FACING} of its own (it is deliberately facing-inert),
 * so there was nothing to rotate a preview with in the first place.
 *
 * <p><b>Disassembly is degradation, and it cascades.</b> Every cell checks, in the one direction
 * that changed, whether the neighbour that ought to be there still is; if not it falls back to its
 * loose form. The eight cells are face-connected, so losing any one of them reaches all the others
 * through ordinary neighbour updates — the pickaxe, an explosion, a piston and {@code /setblock} all
 * arrive by the same road, exactly as they do for the Workstation's pair. Nothing is dropped by this
 * class: a cell that degrades is still standing, and the player picks it back up normally.
 */
public final class ConcentratorStructure {

	private ConcentratorStructure() {
	}

	/** Facings tried when assembling, in a fixed order so the result never depends on timing. */
	private static final Direction[] FACINGS = {
			Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** True when the state is a grown concentrator standing on its own, not yet a structure. */
	public static boolean isLooseCore(BlockState state) {
		return state.is(ModContent.RADIANT_SOLAR_PANEL.get())
				&& !state.getValue(RadiantSolarPanelBlock.ASSEMBLED);
	}

	/** True when the state is a section the player placed and that no structure has claimed. */
	public static boolean isLooseSection(BlockState state) {
		return state.is(ModContent.CONCENTRATOR_SECTION.get())
				&& state.getValue(ConcentratorSectionBlock.PART) == ConcentratorPart.LOOSE;
	}

	/** The facing a cell belongs to, or {@code null} when the state is not an assembled cell. */
	@Nullable
	public static Direction assembledFacing(BlockState state) {
		if (state.is(ModContent.RADIANT_SOLAR_PANEL.get())
				&& state.getValue(RadiantSolarPanelBlock.ASSEMBLED)) {
			return state.getValue(RadiantSolarPanelBlock.FACING);
		}
		if (state.is(ModContent.CONCENTRATOR_SECTION.get())
				&& state.getValue(ConcentratorSectionBlock.PART).assembled()) {
			return state.getValue(ConcentratorSectionBlock.FACING);
		}
		return null;
	}

	/** Which cell an assembled state is, or {@code null} when it is not part of a structure. */
	@Nullable
	public static ConcentratorPart partOf(BlockState state) {
		if (state.is(ModContent.RADIANT_SOLAR_PANEL.get())) {
			return state.getValue(RadiantSolarPanelBlock.ASSEMBLED) ? ConcentratorPart.CORE : null;
		}
		if (state.is(ModContent.CONCENTRATOR_SECTION.get())) {
			ConcentratorPart part = state.getValue(ConcentratorSectionBlock.PART);
			return part.assembled() ? part : null;
		}
		return null;
	}

	/**
	 * Step from one cell to a face neighbour, expressed back in the canonical frame.
	 *
	 * <p>The inverse of {@link ConcentratorPart#worldOffset}: it asks which canonical axis the given
	 * world direction is, for a structure with this facing. Written as four comparisons against
	 * vanilla's own directions rather than an inverse matrix, for the same reason the forward
	 * direction is — there is no arithmetic here to get backwards.
	 */
	private static Vec3i canonicalStep(Direction facing, Direction toNeighbour) {
		if (toNeighbour.getAxis().isVertical()) {
			return new Vec3i(0, toNeighbour == Direction.UP ? 1 : -1, 0);
		}
		Direction right = facing.getClockWise();
		if (toNeighbour == right) {
			return new Vec3i(1, 0, 0);
		}
		if (toNeighbour == right.getOpposite()) {
			return new Vec3i(-1, 0, 0);
		}
		return new Vec3i(0, 0, toNeighbour == facing.getOpposite() ? 1 : -1);
	}

	/**
	 * The cell that must sit next to {@code part} in the given world direction, or {@code null} when
	 * that direction leaves the structure (and so says nothing about whether it is still whole).
	 */
	@Nullable
	public static ConcentratorPart neighbourPart(ConcentratorPart part, Direction facing,
			Direction toNeighbour) {
		Vec3i cell = part.canonicalOffset();
		if (cell == null) {
			return null;
		}
		Vec3i step = canonicalStep(facing, toNeighbour);
		int dx = cell.getX() + step.getX();
		int dy = cell.getY() + step.getY();
		int dz = cell.getZ() + step.getZ();
		return ConcentratorPart.atCanonical(dx, dy, dz);
	}

	/** True when {@code state} really is the given cell of a structure with the given facing. */
	public static boolean isMember(BlockState state, ConcentratorPart expected, Direction facing) {
		return partOf(state) == expected && assembledFacing(state) == facing;
	}

	/**
	 * Try to complete a structure that {@code pos} would belong to.
	 *
	 * <p>{@code public static} for the same reason the Workstation's is: a game test places blocks
	 * straight into the world, and programmatic placement never calls {@code setPlacedBy} (MOD-015).
	 *
	 * <p>The guard that stops this recursing through the neighbour updates assembly itself causes is
	 * the same one that makes it correct: it only ever starts from a LOOSE block, and assembly writes
	 * cells, not loose blocks.
	 */
	public static void tryAssemble(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (isLooseCore(state)) {
			for (Direction facing : FACINGS) {
				if (assembleAt(level, pos, facing)) {
					return;
				}
			}
			return;
		}
		if (!isLooseSection(state)) {
			return;
		}
		// A section does not know which cell it will become, so every cell it could be is tried; each
		// guess names exactly one position the core would have to stand in.
		for (Direction facing : FACINGS) {
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				if (part == ConcentratorPart.CORE) {
					continue;
				}
				BlockPos core = pos.subtract(part.worldOffset(facing));
				if (isLooseCore(level.getBlockState(core)) && assembleAt(level, core, facing)) {
					return;
				}
			}
		}
	}

	/** Assemble the box anchored at {@code core} with the given facing, if every cell is ready. */
	private static boolean assembleAt(Level level, BlockPos core, Direction facing) {
		BlockState coreState = level.getBlockState(core);
		if (!isLooseCore(coreState)) {
			return false;
		}
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			if (!isLooseSection(level.getBlockState(core.offset(part.worldOffset(facing))))) {
				return false;
			}
		}
		// The core goes first: setting it asks its NEIGHBOURS to re-check their shape, never itself,
		// and the loose sections around it have nothing to re-check. Each section that follows is
		// already correct by the time the cells next to it look at it.
		level.setBlockAndUpdate(core, coreState
				.setValue(RadiantSolarPanelBlock.ASSEMBLED, Boolean.TRUE)
				.setValue(RadiantSolarPanelBlock.FACING, facing));
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			BlockPos cell = core.offset(part.worldOffset(facing));
			level.setBlockAndUpdate(cell, level.getBlockState(cell)
					.setValue(ConcentratorSectionBlock.PART, part)
					.setValue(ConcentratorSectionBlock.FACING, facing));
		}
		level.playSound(null, core, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 0.9f);
		return true;
	}

	/**
	 * The state a cell falls back to when the structure around it is no longer whole.
	 *
	 * <p>Both blocks keep their own identity: the core is still a working one-block concentrator and
	 * a section is still a section, so the player is left holding exactly what they put in.
	 */
	public static BlockState degraded(BlockState state) {
		if (state.is(ModContent.RADIANT_SOLAR_PANEL.get())) {
			return state.setValue(RadiantSolarPanelBlock.ASSEMBLED, Boolean.FALSE);
		}
		return state.setValue(ConcentratorSectionBlock.PART, ConcentratorPart.LOOSE);
	}

	/**
	 * Answer for {@code updateShape}: the state this cell should now be, given what happened in one
	 * direction. Returns {@code state} unchanged when the change says nothing about this structure.
	 */
	public static BlockState afterNeighbourChange(BlockState state, Direction toNeighbour,
			BlockState neighbourState) {
		ConcentratorPart part = partOf(state);
		Direction facing = assembledFacing(state);
		if (part == null || facing == null) {
			return state;
		}
		ConcentratorPart expected = neighbourPart(part, facing, toNeighbour);
		if (expected == null || isMember(neighbourState, expected, facing)) {
			return state;
		}
		return degraded(state);
	}

	/** Position of the core of the structure {@code pos} belongs to, or {@code null}. */
	@Nullable
	public static BlockPos coreOf(BlockState state, BlockPos pos) {
		ConcentratorPart part = partOf(state);
		Direction facing = assembledFacing(state);
		if (part == null || facing == null) {
			return null;
		}
		return pos.subtract(part.worldOffset(facing));
	}

	/** Every cell position of the structure anchored at {@code core}, the core included. */
	public static BlockPos[] cells(BlockPos core, Direction facing) {
		BlockPos[] out = new BlockPos[ConcentratorPart.CELLS.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = core.offset(ConcentratorPart.CELLS[i].worldOffset(facing));
		}
		return out;
	}
}
