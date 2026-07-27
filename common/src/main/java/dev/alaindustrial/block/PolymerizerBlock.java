package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Polymerizer block (MOD-019) — a full cube that faces the player and shows its "on" model while an
 * operation runs. Silent by design for now: it does not implement {@code MachineHumProvider}, the same
 * state the Compressor and the Pump are in (see {@code docs/SOUND_TRACKING.md}; a working hum is tracked
 * by MOD-143).
 */
public class PolymerizerBlock extends LitMachineBlock {
	public static final MapCodec<PolymerizerBlock> CODEC = simpleCodec(PolymerizerBlock::new);

	public PolymerizerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PolymerizerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
