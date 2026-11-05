package dev.alaindustrial.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain block of the quarry frame — a tower head, the cart (MOD-950): a full cube while loose, hidden under the
 * designer's model once the frame forms ({@link QuarryFramePart}).
 */
public class QuarryPartBlock extends Block {

	public QuarryPartBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(QuarryFramePart.FORMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(QuarryFramePart.FORMED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return QuarryFramePart.renderShape(state, super.getRenderShape(state));
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return QuarryFramePart.occlusion(state, super.getOcclusionShape(state));
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return QuarryFramePart.lightDampening(state, super.getLightDampening(state));
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return QuarryFramePart.skylight(state, super.propagatesSkylightDown(state));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return QuarryFramePart.shape(state, level, pos, super.getShape(state, level, pos, context));
	}

	/** A formed cell opens the frame's controller screen ({@link QuarryFramePart#openController}). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		return QuarryFramePart.openController(state, level, pos, player);
	}

	/** Metal underfoot once formed ({@link QuarryFramePart#sound}). */
	@Override
	protected SoundType getSoundType(BlockState state) {
		return QuarryFramePart.sound(state, super.getSoundType(state));
	}
}
