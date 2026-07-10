package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Energy storage block. Single-axis IO (MOD-006): the {@code FACING} (front) face is the energy
 * <b>input</b> (charge), the opposite (back) face is the <b>output</b> (discharge), and the other
 * four faces are inert — see {@link BatteryBoxBlockEntity#energyRoleForFace}. A cable therefore
 * draws an arm only toward the front and back faces, never toward the four sides (which would show
 * a misleading "energy goes here" arm without any EU ever flowing).
 */
public class BatteryBoxBlock extends HorizontalMachineBlock {
	public static final MapCodec<BatteryBoxBlock> CODEC = simpleCodec(BatteryBoxBlock::new);

	public BatteryBoxBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BatteryBoxBlockEntity(pos, state);
	}

	/**
	 * Restricts the cable arm to the single-axis IO layout, matching
	 * {@link BatteryBoxBlockEntity#energyRoleForFace}: front ({@code FACING}, input) and back
	 * ({@code FACING.getOpposite()}, output) connect; the four sides, top and bottom are inert. Same
	 * per-face approach as the wind mill block, but accepting both the input and the output face
	 * rather than the output alone. Decided from {@code FACING} (a blockstate property) so it is
	 * correct the instant the block is placed, with no block-entity load race.
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
