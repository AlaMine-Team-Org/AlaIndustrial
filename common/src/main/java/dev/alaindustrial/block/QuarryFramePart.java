package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.quarry.QuarryFrameShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What every block of a formed quarry frame becomes (MOD-950): invisible, because the controller's renderer draws
 * the designer's model over the whole frame in its place, the way the mob wheel's cells hide under its drive.
 *
 * <p>The foundations, scaffolding, rail beams, heads, bridge beams and the cart all carry {@link #FORMED}; the
 * controller sets it on every cell of the frame when the frame forms and clears it when it comes apart. A formed
 * cell takes the model's shape in its cell ({@link #shape}) for collision and outline, stops occluding its
 * neighbours and lets light through: the model is thinner than the cubes under it (the lattice, the path), so a cell
 * that still culled the faces next to it would show holes into the ground, and one that still blocked light would
 * draw the model it hides in the dark.
 */
public final class QuarryFramePart {

	/** Whether this block is part of a formed quarry frame and hidden under its model. */
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");

	private QuarryFramePart() {
	}

	public static boolean formed(BlockState state) {
		return state.hasProperty(FORMED) && state.getValue(FORMED);
	}

	/** {@link RenderShape#INVISIBLE} once formed, {@code loose} otherwise. */
	public static RenderShape renderShape(BlockState state, RenderShape loose) {
		return formed(state) ? RenderShape.INVISIBLE : loose;
	}

	/** Nothing once formed, {@code loose} otherwise. */
	public static VoxelShape occlusion(BlockState state, VoxelShape loose) {
		return formed(state) ? Shapes.empty() : loose;
	}

	/** No dampening once formed, as through glass; {@code loose} otherwise. */
	public static int lightDampening(BlockState state, int loose) {
		return formed(state) ? 0 : loose;
	}

	/**
	 * A formed frame is the designer's steel model, so it sounds like metal underfoot (owner, 2026-11-05) — a path of
	 * concrete rail beams too; a loose block keeps its own sound.
	 */
	public static SoundType sound(BlockState state, SoundType loose) {
		return formed(state) ? SoundType.METAL : loose;
	}

	/** Skylight passes once formed; {@code loose} otherwise. */
	public static boolean skylight(BlockState state, boolean loose) {
		return formed(state) || loose;
	}

	/**
	 * A right click on a formed cell opens the frame's controller screen (owner, 2026-11-05: the controller hides under
	 * the model like every other cell, and a player cannot tell which one it is). A loose block passes, so placing
	 * against it works as ever.
	 */
	public static InteractionResult openController(BlockState state, Level level, BlockPos pos, Player player) {
		if (!formed(state)) {
			return InteractionResult.PASS;
		}
		return openControllerOf(level, pos, player);
	}

	/** Opens the screen of the controller whose formed frame holds {@code pos}; passes outside any frame. */
	public static InteractionResult openControllerOf(Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockPos controller = QuarryFrameShapes.controllerOf(level, pos);
		if (controller == null || !(level.getBlockEntity(controller) instanceof MenuProvider menu)) {
			return InteractionResult.PASS;
		}
		player.openMenu(menu);
		return InteractionResult.SUCCESS;
	}

	/**
	 * The formed cell's shape — the designer's model cut by the cell, from the frame that registered it
	 * ({@link QuarryFrameShapes}) — or {@code loose} while loose or before the client has the frame.
	 */
	public static VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos, VoxelShape loose) {
		if (!formed(state)) {
			return loose;
		}
		VoxelShape cut = QuarryFrameShapes.shapeAt(level, pos);
		return cut != null ? cut : loose;
	}
}
