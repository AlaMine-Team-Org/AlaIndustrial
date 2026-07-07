package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Geothermal generator — faces the player, lights up while burning lava. */
public class GeothermalGeneratorBlock extends LitMachineBlock implements MachineHumProvider {
	public static final MapCodec<GeothermalGeneratorBlock> CODEC = simpleCodec(GeothermalGeneratorBlock::new);

	public GeothermalGeneratorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GeothermalGeneratorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.GENERATOR_HUM;
	}

	@Override
	public float humVolume() {
		return 0.4f;
	}
}
