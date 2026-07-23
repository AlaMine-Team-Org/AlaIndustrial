package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
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
 * Water mill block — a full-cube LV generator that faces the player on placement. The {@code facing}
 * front is energy-inert (via {@link dev.alaindustrial.block.entity.AbstractGeneratorBlockEntity}); the
 * other five faces emit EU. No {@code lit} state (production is passive, not fuel-driven), so it extends
 * {@link HorizontalMachineBlock} rather than {@link LitMachineBlock}. The default full-cube shape is
 * inherited (no {@code getShape} override).
 */
public class WaterMillBlock extends HorizontalMachineBlock implements MachineHumProvider {
	public static final MapCodec<WaterMillBlock> CODEC = simpleCodec(WaterMillBlock::new);

	public WaterMillBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WaterMillBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		// Hum ticker: the client loop starts/stops with isWorking() below (pattern C — no lit state). MOD-143.
		return humMachineTicker(level);
	}

	@Override
	public Supplier<SoundEvent> humSound() {
		return ModSounds.WATER_MILL_HUM;
	}

	@Override
	public float humVolume() {
		// Quieter than a lone machine: mills line up along a channel and stack, like the solar farm case.
		return 0.22f;
	}

	/**
	 * Pattern C working predicate (MOD-143): the mill has no {@code lit} state, so derive "working" from
	 * the same client-synced production channel the wheel renderer spins on — slot 2 ({@code progress} =
	 * count of flowing-water faces). It is {@code > 0} only in {@code MODE_OK} with a wheel installed and
	 * a current present; obstruction / interference / dry all zero it (see {@code WaterMillBlockEntity}),
	 * so the loop is silent exactly when the wheel stands still. Synced via the block-entity update packet
	 * (no menu needed), so this read is valid client-side.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof WaterMillBlockEntity mill
				&& mill.getDataAccess().get(2) > 0;
	}
}
