package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.CesuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MV Reinforced Energy Storage. Single-axis IO exactly as the Battery Box (MOD-006): the
 * {@code FACING} (front) face is the energy <b>input</b>, the opposite (back) face is the
 * <b>output</b>, and the other four faces are inert — see {@link CesuBlockEntity#energyRoleForFace}.
 * A cable therefore draws an arm only toward front and back, never toward the four sides.
 */
public class CesuBlock extends HorizontalMachineBlock {
	public static final MapCodec<CesuBlock> CODEC = simpleCodec(CesuBlock::new);

	public CesuBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CesuBlockEntity(pos, state);
	}

	/**
	 * Restricts the cable arm to the single-axis IO layout, matching
	 * {@link CesuBlockEntity#energyRoleForFace}. Decided from {@code FACING} (a blockstate property) so
	 * it is correct the instant the block is placed, with no block-entity load race.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		Direction facing = state.getValue(HorizontalMachineBlock.FACING);
		return side == facing || side == facing.getOpposite();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
