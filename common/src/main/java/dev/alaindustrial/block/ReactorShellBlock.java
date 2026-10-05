package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A plain cube of the reactor shell — casing, glass and the feedthrough (MOD-468, stage 1).
 *
 * <p><b>Why these carry a {@code formed} state.</b> A room that passed its scan should stop looking
 * like a stack of crates and start looking like a room. Minecraft has no connected-texture support in
 * the vanilla renderer, so the seam has to go away in the <em>art</em>: each shell block has a second
 * texture without the bright bezel, and the controller flips every cell of a sealed shell onto it. The
 * bezel is what draws the grid, so dropping it is what makes a wall read as one surface.
 *
 * <p>That makes the state a reward as well as a readout — the moment the last block goes in, the whole
 * structure visibly snaps together, which is the feedback a multiblock owes the player who built it.
 *
 * <p>The property is set by {@link dev.alaindustrial.block.entity.ReactorControllerBlockEntity}, never
 * by the block itself: only the controller knows whether the shell around this cell is complete.
 *
 * <p><b>The 26.2 block-codec seam (MOD-703).</b> Minecraft 26.2 requires every block to name a codec
 * ({@code BlockBehaviour.codec()} is abstract there); 26.3 removed block codecs. {@link #codec()} answers it
 * once for every subclass, so no subclass carries a {@code CODEC} of its own and their sources are the same
 * on both lines. It has no {@code @Override} on purpose: on 26.2 it overrides, on 26.3 it is a method of its
 * own that nothing calls.
 */
public class ReactorShellBlock extends Block {

	/** Whether this cell belongs to a shell that currently passes its scan. Cosmetic only. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	/**
	 * Whether this cell sits on an edge or corner of the sealed box. Edges keep their bezel while the
	 * faces go smooth, so a finished room reads as one object with a drawn outline rather than as a
	 * featureless slab — the outline is what gives a 14-block wall any shape at all.
	 */
	public static final BooleanProperty EDGE = BooleanProperty.create("edge");

	public ReactorShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FORMED, false).setValue(EDGE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FORMED, EDGE);
	}

	/**
	 * Two shell blocks of the same kind hide their shared face, so a wall is not drawn twice from the
	 * inside out. This is what stops a glass wall from showing a grid of internal panes — vanilla glass
	 * does exactly the same thing, and without it the "seamless when formed" effect stops at the first
	 * window.
	 */
	@Override
	protected boolean skipRendering(BlockState state, BlockState neighbourState, net.minecraft.core.Direction side) {
		return neighbourState.is(this) || super.skipRendering(state, neighbourState, side);
	}

	/**
	 * The codec Minecraft 26.2 asks every block for: the unit codec of this very instance. 26.2 reads block
	 * codecs only in the datagen block-list report ({@code BlockTypes.CODEC} → {@code BlockListReport},
	 * javap), so nothing at run time decodes a block through it.
	 */
	protected MapCodec<? extends Block> codec() {
		return MapCodec.unit(this);
	}
}
