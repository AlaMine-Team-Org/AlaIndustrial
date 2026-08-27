package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A cell of a greenhouse shell (MOD-505) — the deck underfoot and the glazing overhead are the same
 * class, differing only in their properties and their art. The reactor's shell does exactly this
 * for its casing and windows, and for the same reason: both answer identically to being part of a
 * sealed structure, so one class is the honest shape.
 *
 * <p><b>Why it carries a {@code formed} state.</b> A room that passed its scan should stop looking
 * like a stack of crates and start looking like a room. Minecraft's vanilla renderer has no
 * connected-texture support, so the seam has to go away in the <em>art</em>: the deck has a second
 * texture without the bright bezel, and the controller flips every cell of a sealed floor onto it.
 * The bezel is what draws the grid, so dropping it is what makes a floor read as one surface.
 *
 * <p>That makes the state a reward as well as a readout — the moment the last block goes in, the
 * structure visibly snaps together, which is the feedback a multiblock owes the player who built it.
 * The first playtest of this feature had the logic working and this missing, and the verdict was
 * that the greenhouse "did not become a multiblock": the scan is invisible, so the paint IS the
 * mechanic as far as the player can tell.
 *
 * <p>Both properties are written by {@link dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity},
 * never by the block itself: only the controller knows whether the shell around a given cell is
 * complete.
 */
public class CrystalFarmShellBlock extends Block {

	public static final MapCodec<CrystalFarmShellBlock> CODEC = simpleCodec(CrystalFarmShellBlock::new);

	/** Whether this cell belongs to a greenhouse that currently passes its scan. Cosmetic only. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/**
	 * Whether this cell sits on an edge or corner of the sealed box. Edges keep their bezel while the
	 * faces go smooth, so a finished room reads as one object with a drawn outline rather than as a
	 * featureless slab — the outline is what gives a large floor any shape at all.
	 */
	public static final BooleanProperty EDGE = BooleanProperty.create("edge");

	public CrystalFarmShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FORMED, false).setValue(EDGE, false));
	}

	@Override
	protected MapCodec<? extends CrystalFarmShellBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FORMED, EDGE);
	}

	/**
	 * Two shell blocks of the same kind hide their shared face, so a wall is not drawn twice from the
	 * inside out. This is what stops a glass dome from showing a grid of internal panes — vanilla glass
	 * does the same, and without it the "seamless when formed" effect would stop at the first window.
	 */
	@Override
	protected boolean skipRendering(BlockState state, BlockState neighbourState, Direction side) {
		return neighbourState.is(this) || super.skipRendering(state, neighbourState, side);
	}
}
