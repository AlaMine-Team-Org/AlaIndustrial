package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fermenter block (MOD-146) — a full cube that faces the player and shows its "on" model while a
 * batch is brewing.
 *
 * <p>Silent on purpose for now: the mod's hum loops are authored per machine through
 * {@link MachineHumProvider}, and this block ships without one rather than borrowing another
 * machine's voice. Adding it later is a sound file plus the interface, with no change here.
 */
public class FermenterBlock extends LitMachineBlock {
	public static final MapCodec<FermenterBlock> CODEC = simpleCodec(FermenterBlock::new);

	public FermenterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FermenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
