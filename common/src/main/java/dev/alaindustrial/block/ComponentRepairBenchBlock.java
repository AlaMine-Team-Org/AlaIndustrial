package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
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

/**
 * LV Component Repair Bench (MOD-384) — restores worn wind-mill rotors and water-mill wheels. Plain
 * {@link LitMachineBlock}: it faces the player and lights up while repairing, and all the behaviour
 * lives in {@link ComponentRepairBenchBlockEntity}.
 *
 * <p>Audible while working since MOD-447: implements {@link MachineHumProvider} with the bench's own
 * anvil-ring loop (pattern A, the vanilla {@code lit} blockstate).
 */
public class ComponentRepairBenchBlock extends LitMachineBlock implements MachineHumProvider {
	public static final MapCodec<ComponentRepairBenchBlock> CODEC = simpleCodec(ComponentRepairBenchBlock::new);

	public ComponentRepairBenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ComponentRepairBenchBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: drives the client loop off the vanilla lit blockstate (pattern A). MOD-447.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.COMPONENT_REPAIR_BENCH_HUM;
	}
}
