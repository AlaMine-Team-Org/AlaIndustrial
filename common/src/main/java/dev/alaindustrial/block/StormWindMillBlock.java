package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storm wind mill block — the weather-focused T2 evolution of the wind mill. A full cube that faces
 * the player on placement; the front {@code facing} face is energy-inert, and EU is emitted
 * <b>only from the back face</b> ({@code facing.getOpposite()}) — mirroring the T1 wind mill's
 * single-axis output so a cable on the back keeps working after evolution (the other four sides,
 * top and bottom are also inert). Same physical block shape and placement rules as
 * {@link WindMillBlock}; only the entity (generation formula) and the front texture differ.
 */
public class StormWindMillBlock extends HorizontalMachineBlock {
	public static final MapCodec<StormWindMillBlock> CODEC = simpleCodec(StormWindMillBlock::new);

	public StormWindMillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StormWindMillBlockEntity(pos, state);
	}

	/**
	 * Restricts the cable arm to the back face alone — see {@link WindMillBlock#isCableConnectable}.
	 * Kept in sync with the T1 wind mill so a cable plugged into the back of an evolving mill keeps
	 * working after the transform, and so no misleading arm appears on the inert faces.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return side == state.getValue(HorizontalMachineBlock.FACING).getOpposite();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
