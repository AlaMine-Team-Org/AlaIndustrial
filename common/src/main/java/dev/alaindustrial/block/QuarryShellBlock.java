package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.quarry.QuarryFrameShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Quarry Shell (MOD-950): the invisible block a formed quarry frame puts in the air cells its model reaches into —
 * the upper part of the heads and of the paths, the saddles over their ends. It has no item and drops nothing; it
 * exists so the drawn model is solid where it is drawn, and its shape is the model cut by its cell
 * ({@link QuarryFrameShapes}). The controller places it when the frame forms and takes it away when it comes apart.
 *
 * <p>Since the frame is as tall as the model (owner, 2026-11-04) only slivers of the model reach above its blocks — the
 * crown of a head, the tops of the saddles and the bridge. A player breaking one is breaking what they see, so it
 * breaks the frame block under it ({@link #playerWillDestroy}) and the frame comes apart as from any of its blocks.
 */
public class QuarryShellBlock extends Block {

	/** How far down a broken shell looks for the frame block it stands on: the model rises at most this far. */
	private static final int REACH_DOWN = 3;

	public QuarryShellBlock(Properties properties) {
		super(properties);
	}

	/** Breaks the frame block under this shell too, dropping it as the player would have. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			BlockPos part = framePartBelow(level, pos);
			if (part != null) {
				level.destroyBlock(part, !player.hasInfiniteMaterials(), player);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * The formed frame block a broken shell takes with it: the nearest one under it, at most {@value #REACH_DOWN}
	 * cells down, else one beside it — the model also reaches out sideways past its blocks, as the grey box at a
	 * tower's foot does, and a shell there stands on the ground beside the foundation — else one above; {@code null}
	 * when there is none.
	 */
	@Nullable
	private static BlockPos framePartBelow(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int step = 0; step < REACH_DOWN; step++) {
			cursor.move(0, -1, 0);
			if (QuarryFramePart.formed(level.getBlockState(cursor))) {
				return cursor.immutable();
			}
		}
		for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
				Direction.UP}) {
			BlockPos next = pos.relative(side);
			if (QuarryFramePart.formed(level.getBlockState(next))) {
				return next;
			}
		}
		return null;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/** The model in this cell, or nothing outside a registered frame (a shell the frame has not claimed yet). */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = QuarryFrameShapes.shapeAt(level, pos);
		return shape != null ? shape : Shapes.empty();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** The shell is a cell of a formed frame: a click opens its controller screen. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		return QuarryFramePart.openControllerOf(level, pos, player);
	}
}
