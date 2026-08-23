package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Creative Energy Source (MOD-479): shape, state and ticker. All behaviour lives in
 * {@link CreativeEnergySourceBlockEntity}.
 *
 * <p>Rotation-symmetric on purpose — no {@code FACING}. Every face emits, so a front would be a face
 * that means something to the player and nothing to the energy code, and the two would drift the first
 * time someone rotated it expecting a difference.
 *
 * <p>{@code LIT} mirrors the on/off switch in the screen, so a stand of these can be read at a glance
 * without opening any of them.
 */
public class CreativeEnergySourceBlock extends AbstractMachineBlock {

	public static final MapCodec<CreativeEnergySourceBlock> CODEC = simpleCodec(CreativeEnergySourceBlock::new);

	public CreativeEnergySourceBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.LIT, true));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.LIT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CreativeEnergySourceBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
