package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Redstone-started, externally heated LV centrifuge that doubles an ore dust into shavings.
 *
 * <p>The shape is inset rather than a full cube because the housing is an open frame: the mod's
 * block-standards gate refuses "full cube that does not occlude", and the rotor the renderer spins
 * inside does not exist for hit-testing — a shape cut to the frame's openings would let clicks pass
 * straight through the rotor into whatever is behind the machine. Same reasoning, same numbers, as the
 * Energy Condenser.
 */
public final class ThermalCentrifugeBlock extends LitMachineBlock {
	public static final MapCodec<ThermalCentrifugeBlock> CODEC = simpleCodec(ThermalCentrifugeBlock::new);

	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public ThermalCentrifugeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Full cube for support, even though the outline is inset — and this is not cosmetic.
	 *
	 * <p>A lever attaches only to a face the game considers sturdy, and sturdiness is read from THIS
	 * shape, not from {@link #getShape}. Inheriting the inset outline here made the one machine in the
	 * mod that <em>requires</em> a redstone signal the one machine you could not hang a lever on — found
	 * in playtesting, and about as sharp a contradiction as a block can ship with.
	 */
	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.block();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ThermalCentrifugeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * One callback, two signals. A neighbour change here can mean the heater below finished warming
	 * <em>or</em> that the player flipped the lever, and both must bypass the base class's 40-tick idle
	 * sleep — a machine that took two seconds to notice its own start button would read as broken.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide()
				&& level.getBlockEntity(pos) instanceof ThermalCentrifugeBlockEntity centrifuge) {
			centrifuge.onHeatNeighbourChanged();
		}
	}
}
