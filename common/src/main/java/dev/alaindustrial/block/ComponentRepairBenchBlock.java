package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import net.minecraft.core.BlockPos;
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
 * <p>Deliberately not a {@code MachineHumProvider}: the bench has no ambient loop yet, and declaring
 * one without a registered sound event would be a silent machine that claims to be audible. Adding a
 * hum later is the {@code /alamod-sound} pipeline's job, tracked in {@code docs/SOUND_TRACKING.md}.
 */
public class ComponentRepairBenchBlock extends LitMachineBlock {
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
		return machineTicker(level);
	}
}
