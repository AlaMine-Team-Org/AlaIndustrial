package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.WarehouseShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The warehouse shelf (MOD-885, epic MOD-824 R157): the storage of a town's restored warehouse, a cabinet of four
 * bins whose cards show what it holds the most of. A full cube that faces the player on placement; a right click opens
 * its chest window ({@link AbstractMachineBlock#useWithoutItem} opens the block entity's menu).
 *
 * <p>Breaking it spills the contents like a chest: {@code BlockEntity.preRemoveSideEffects} empties any
 * {@code Container}, so nothing here drops anything itself. It has no energy, so a cable draws no arm toward it.
 * Its bins stand back from the front, so it lets light in and hides none of its neighbours' faces, like the block
 * breaker.
 */
public class WarehouseShelfBlock extends HorizontalMachineBlock {

	public WarehouseShelfBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WarehouseShelfBlockEntity(pos, state);
	}

	/** A plain container: no energy port on any face. */
	@Override
	public boolean isCableConnectable() {
		return false;
	}

	/**
	 * What the shelf hides of its neighbours' faces: nothing. Its bins and cards are recessed into the front, so a
	 * full-cube occlusion shape would make the block in front stop drawing its face and the player would see through
	 * it. {@code noOcclusion()} is not the answer (R-PHY-05): a full cube still occludes. The block breaker's recipe.
	 */
	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	/**
	 * Light passes through, as through glass: the recessed front is lit by the light inside its own cell, and a cell
	 * that blocked light renders the bins black.
	 */
	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** No ambient-occlusion shade on the neighbours: on a block that lets light through it reads as a shadow. */
	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
