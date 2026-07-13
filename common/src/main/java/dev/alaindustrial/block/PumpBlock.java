package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric pump — faces the player, lights up while moving lava.
 *
 * <p>Inherits the {@link HorizontalMachineBlock} "front (FACING) face is inert" cable rule: a cable
 * draws an arm toward the five working faces but not toward FACING. Energy intake follows the same
 * shape — see {@link PumpBlockEntity#energyRoleForFace} ({@code IN} on every face <b>but</b>
 * FACING). Fluid intake is a separate subsystem and still reads FACING directly ({@code acquireFluid}
 * starts its BFS from the block in front of the pump), so this does <b>not</b> change which way the
 * pump draws lava/water from — only the energy/cable contract on that one face.
 */
public class PumpBlock extends LitMachineBlock {
	public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);

	public PumpBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PumpBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
