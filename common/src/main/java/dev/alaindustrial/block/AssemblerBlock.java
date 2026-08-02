package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Assembler (MOD-275) — the mod's first MV machine, a full cube that faces the player.
 *
 * <p>Extends {@link HorizontalMachineBlock} rather than {@link LitMachineBlock}: the assembler's
 * front is a blueprint plate, not a furnace mouth, and it has no "on" model — its activity is read
 * from the GUI progress bar. Inheriting the horizontal base is what marks that front face and keeps
 * it out of automation on both the energy side ({@code facingAwareRole}) and the item side
 * ({@code MachineBlockEntity#getSlotsForFace}), which is exactly the contract the OKF spec states
 * ("the front face is marked and … excluded from automation").
 */
public class AssemblerBlock extends HorizontalMachineBlock {
	public static final MapCodec<AssemblerBlock> CODEC = simpleCodec(AssemblerBlock::new);

	public AssemblerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AssemblerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
